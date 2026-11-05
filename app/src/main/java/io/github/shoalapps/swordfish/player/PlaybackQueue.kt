package io.github.shoalapps.swordfish.player

import io.github.shoalapps.swordfish.data.VideoItem

/** Upcoming manual choices, in play order. A video appears at most once. */
class PlaybackQueue(items: List<VideoItem> = emptyList()) {
    val items: List<VideoItem> = items.distinctBy { it.id }.toList()

    fun add(item: VideoItem, currentId: String? = null): PlaybackQueue =
        if (item.id == currentId || items.any { it.id == item.id }) this
        else PlaybackQueue(items + item)

    fun playNext(item: VideoItem, currentId: String? = null): PlaybackQueue =
        if (item.id == currentId) this else PlaybackQueue(listOf(item) + items.filterNot { it.id == item.id })

    fun clear() = PlaybackQueue()

    fun remove(id: String) = PlaybackQueue(items.filterNot { it.id == id })

    fun move(id: String, offset: Int): PlaybackQueue {
        val from = items.indexOfFirst { it.id == id }
        val to = from + offset
        if (from < 0 || to !in items.indices) return this
        return PlaybackQueue(items.toMutableList().apply { add(to, removeAt(from)) })
    }

    /** Ignore any stale copy of the current video before taking the next choice. */
    fun advance(currentId: String? = null): Pair<VideoItem?, PlaybackQueue> {
        val upcoming = items.filterNot { it.id == currentId }
        return upcoming.firstOrNull() to PlaybackQueue(upcoming.drop(1))
    }
}
