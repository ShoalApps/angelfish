package io.github.shoalapps.swordfish.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
data class LocalPlaylist(val id: String, val name: String, val items: List<VideoItem> = emptyList())

/** Saved collections, independent of sign-in and the consumable playback queue. */
class LocalPlaylists(playlists: List<LocalPlaylist> = emptyList()) {
    val playlists = playlists.filter { it.id.isNotBlank() && it.name.isNotBlank() }
        .distinctBy { it.id }.map { it.copy(name = it.name.trim(), items = it.items.distinctBy { v -> v.id }) }

    fun create(id: String, name: String): LocalPlaylists =
        if (id.isBlank() || name.isBlank() || playlists.any { it.id == id }) this
        else LocalPlaylists(playlists + LocalPlaylist(id, name.trim()))

    fun rename(id: String, name: String): LocalPlaylists =
        if (name.isBlank()) this else edit(id) { it.copy(name = name.trim()) }

    fun delete(id: String) = LocalPlaylists(playlists.filterNot { it.id == id })
    fun add(id: String, video: VideoItem) = edit(id) { p ->
        if (p.items.any { it.id == video.id }) p else p.copy(items = p.items + video)
    }
    fun remove(id: String, videoId: String) = edit(id) { p -> p.copy(items = p.items.filterNot { it.id == videoId }) }
    fun move(id: String, videoId: String, offset: Int) = edit(id) { p ->
        val from = p.items.indexOfFirst { it.id == videoId }
        val to = from + offset
        if (from < 0 || to !in p.items.indices) p
        else p.copy(items = p.items.toMutableList().apply { add(to, removeAt(from)) })
    }
    private fun edit(id: String, change: (LocalPlaylist) -> LocalPlaylist) =
        LocalPlaylists(playlists.map { if (it.id == id) change(it) else it })

    companion object {
        /** A broken item doesn't hide the other videos or playlists. The key supplies the identity. */
        fun read(id: String, raw: String): LocalPlaylist? = runCatching {
            val record = json.parseToJsonElement(raw) as? JsonObject ?: return null
            val name = (record["name"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val items = (record["items"] as? JsonArray).orEmpty().mapNotNull { item ->
                runCatching { json.decodeFromJsonElement<VideoItem>(item) }.getOrNull()
            }.distinctBy { it.id }
            LocalPlaylist(id, name, items)
        }.getOrNull()
    }
}
