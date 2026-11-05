package io.github.shoalapps.swordfish.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
data class WatchPosition(
    val video: VideoItem,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtMs: Long,
)

/** Local playback memory. No Android, clock, disk or network dependencies. */
class WatchPositions(entries: List<WatchPosition> = emptyList()) {
    val entries = entries.filter { eligible(it.video, it.durationMs) && it.positionMs > 0 &&
        !finished(it.positionMs, it.durationMs) && it.updatedAtMs >= 0 }
        .sortedByDescending { it.updatedAtMs }.distinctBy { it.video.id }.take(CAP)

    fun resumeMs(id: String): Long = entries.firstOrNull { it.video.id == id }
        ?.takeIf { shouldResume(it.positionMs, it.durationMs) }?.positionMs ?: 0L

    val continuing: List<WatchPosition> get() = entries.filter { shouldResume(it.positionMs, it.durationMs) }.take(20)

    fun record(video: VideoItem, positionMs: Long, durationMs: Long, nowMs: Long): WatchPositions {
        if (!eligible(video, durationMs)) return remove(video.id)
        val remaining = entries.filterNot { it.video.id == video.id }
        return WatchPositions(remaining + WatchPosition(video.copy(durationSec = durationMs / 1000), positionMs, durationMs, nowMs))
    }

    fun remove(id: String) = WatchPositions(entries.filterNot { it.video.id == id })

    fun encode(): String = codec.encodeToString(entries)

    companion object {
        const val CAP = 500
        private val codec = Json { ignoreUnknownKeys = true }
        fun eligible(video: VideoItem, durationMs: Long) =
            video.id.isNotBlank() && !video.isLive && !video.isShort && durationMs > 0
        fun finished(positionMs: Long, durationMs: Long) =
            durationMs > 0 && positionMs.toDouble() / durationMs >= 0.95
        fun shouldResume(positionMs: Long, durationMs: Long) =
            positionMs > 30_000 && durationMs > 0 && !finished(positionMs, durationMs)

        /** A corrupt entry doesn't discard its neighbours; a broken document is simply empty. */
        fun decode(raw: String?): WatchPositions = WatchPositions(runCatching {
            (codec.parseToJsonElement(raw ?: "[]") as? JsonArray).orEmpty().mapNotNull {
                runCatching { codec.decodeFromJsonElement<WatchPosition>(it) }.getOrNull()
            }
        }.getOrDefault(emptyList()))
    }
}
