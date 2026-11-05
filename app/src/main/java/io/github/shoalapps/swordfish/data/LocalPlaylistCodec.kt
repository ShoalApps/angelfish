package io.github.shoalapps.swordfish.data

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.serialization.json.*

/** Portable, account-free playlist files. No Android I/O or store access. */
class LocalPlaylistCodec {
    data class Playlist(val name: String, val items: List<VideoItem>)
    data class Decoded(
        val playlists: List<Playlist>,
        val skippedPlaylists: Int = 0,
        val skippedVideos: Int = 0,
        val duplicateVideos: Int = 0,
    )
    data class Imported(
        val playlists: List<LocalPlaylist>,
        val addedPlaylists: Int,
        val addedVideos: Int,
        val renamedPlaylists: Int,
        val duplicatePlaylists: Int,
        val skippedPlaylists: Int,
        val skippedVideos: Int,
        val duplicateVideos: Int,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** Read at most the limit plus one byte, even when the provider reports no size. */
    fun read(input: InputStream): Decoded {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (output.size() <= MAX_BYTES) {
            val count = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - output.size()))
            if (count < 0) break
            if (count == 0) {
                val next = input.read()
                if (next < 0) break
                output.write(next)
            } else output.write(buffer, 0, count)
        }
        return decode(output.toByteArray())
    }

    fun decode(bytes: ByteArray): Decoded {
        require(bytes.size <= MAX_BYTES) { "This playlist file is too large. The limit is 2 MB." }
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            throw IllegalArgumentException("This playlist file is not valid UTF-8 text.")
        }
        // Bound parser recursion before parsing, including inside unknown fields.
        var depth = 0
        var quoted = false
        var escaped = false
        for (c in text) {
            if (quoted) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 32) { "This playlist file is nested too deeply." } }
                '}', ']' -> depth--
            }
        }
        val root = try { json.parseToJsonElement(text) as? JsonObject } catch (_: Exception) { null }
        require(root != null) { "This is not a valid playlist file." }
        val version = root["version"] as? JsonPrimitive
        require(version != null && !version.isString && version.intOrNull == VERSION) {
            "This playlist file version is not supported."
        }
        val records = root["playlists"] as? JsonArray
        require(records != null) { "This playlist file has no playlist list." }
        var skippedPlaylists = 0
        var skippedVideos = 0
        var duplicateVideos = 0
        var visitedVideos = 0
        val playlists = mutableListOf<Playlist>()
        records.forEachIndexed { index, element ->
            val record = element as? JsonObject
            val name = string(record?.get("name"), MAX_NAME)?.trim()?.takeIf { it.isNotEmpty() }
            val entries = record?.get("items") as? JsonArray
            if (index >= MAX_PLAYLISTS || name == null || entries == null) {
                skippedPlaylists++
                skippedVideos += entries?.size ?: 0
            } else {
                val seen = mutableSetOf<String>()
                val items = mutableListOf<VideoItem>()
                entries.forEachIndexed { itemIndex, entry ->
                    visitedVideos++
                    val obj = entry as? JsonObject
                    val id = string(obj?.get("id"), 11)
                    val title = string(obj?.get("title"), MAX_TITLE)
                    val channel = string(obj?.get("channelName"), MAX_CHANNEL)
                    if (itemIndex >= MAX_ITEMS || visitedVideos > MAX_TOTAL_ITEMS || id == null ||
                        !OfflineVideos.validId(id) || title == null || channel == null) {
                        skippedVideos++
                    } else if (!seen.add(id)) {
                        duplicateVideos++
                    } else {
                        items += VideoItem("https://www.youtube.com/watch?v=$id", id, title, channel,
                            thumbnail = "https://i.ytimg.com/vi/$id/hqdefault.jpg")
                    }
                }
                playlists += Playlist(name, items)
            }
        }
        return Decoded(playlists, skippedPlaylists, skippedVideos, duplicateVideos)
    }

    /** Export an explicit allowlist, never serialising Store, account data, or arbitrary URLs. */
    fun encode(playlists: List<LocalPlaylist>): ByteArray {
        require(playlists.size <= MAX_PLAYLISTS && playlists.sumOf { it.items.size.toLong() } <= MAX_TOTAL_ITEMS) {
            "There are too many playlists or videos to export in one file. Export smaller playlists separately."
        }
        val root = buildJsonObject {
            put("version", VERSION)
            putJsonArray("playlists") {
                playlists.forEach { playlist ->
                    require(playlist.name.isNotBlank() && playlist.name.length <= MAX_NAME && playlist.items.size <= MAX_ITEMS) {
                        "A playlist name is too long or a playlist has too many videos to export."
                    }
                    add(buildJsonObject {
                        put("name", playlist.name)
                        putJsonArray("items") {
                            playlist.items.forEach { item ->
                                require(OfflineVideos.validId(item.id) && item.title.length <= MAX_TITLE && item.channelName.length <= MAX_CHANNEL) {
                                    "A video has an invalid ID or text that is too long to export."
                                }
                                add(buildJsonObject {
                                    put("id", item.id)
                                    put("title", item.title)
                                    put("channelName", item.channelName)
                                })
                            }
                        }
                    })
                }
            }
        }
        val bytes = root.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "This playlist file is too large. The limit is 2 MB." }
        return bytes
    }

    /** Ordered video IDs define identical contents; display metadata can change between exports. */
    fun merge(existing: List<LocalPlaylist>, incoming: Decoded, newId: () -> String): Imported {
        val result = existing.toMutableList()
        var added = 0
        var videos = 0
        var renamed = 0
        var duplicates = 0
        incoming.playlists.forEach { playlist ->
            val ids = playlist.items.map { it.id }
            if (result.any { saved ->
                (saved.name == playlist.name || numberedName(saved.name, playlist.name)) && saved.items.map { it.id } == ids
            }) {
                duplicates++
            } else {
                var name = playlist.name
                var number = 2
                while (result.any { it.name == name }) {
                    val suffix = " (${number++})"
                    name = playlist.name.take(MAX_NAME - suffix.length) + suffix
                }
                if (name != playlist.name) renamed++
                result += LocalPlaylist(newId(), name, playlist.items)
                added++
                videos += playlist.items.size
            }
        }
        return Imported(result, added, videos, renamed, duplicates, incoming.skippedPlaylists,
            incoming.skippedVideos, incoming.duplicateVideos)
    }

    private fun numberedName(name: String, original: String): Boolean {
        val number = Regex(" \\(([2-9]|[1-9][0-9]+)\\)$").find(name) ?: return false
        return name.substring(0, number.range.first) == original.take(MAX_NAME - number.value.length)
    }

    private fun string(value: JsonElement?, limit: Int): String? =
        (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.length <= limit }

    companion object {
        const val VERSION = 1
        const val MAX_BYTES = 2 * 1024 * 1024
        const val MAX_NAME = 200
        const val MAX_TITLE = 500
        const val MAX_CHANNEL = 200
        const val MAX_PLAYLISTS = 200
        const val MAX_ITEMS = 1000
        const val MAX_TOTAL_ITEMS = 5000
    }
}
