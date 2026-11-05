package io.github.shoalapps.swordfish.player

import io.github.shoalapps.swordfish.data.VideoItem
import org.junit.Assert.*
import org.junit.Test

class PlaybackQueueTest {
    private fun video(id: String) = VideoItem("https://www.youtube.com/watch?v=$id", id, id, "Channel")
    private val a = video("a")
    private val b = video("b")
    private val c = video("c")

    @Test fun addKeepsOrderAndIgnoresDuplicatesAndCurrent() {
        val queue = PlaybackQueue().add(a).add(b).add(a).add(c, "c")
        assertEquals(listOf(a, b), queue.items)
    }

    @Test fun playNextMovesAnExistingChoiceToTheFront() {
        val queue = PlaybackQueue(listOf(a, b)).playNext(b).playNext(c)
        assertEquals(listOf(c, b, a), queue.items)
        assertEquals(queue.items, queue.playNext(a, "a").items)
    }

    @Test fun movePreservesTheOtherItemsAndIgnoresInvalidMoves() {
        val queue = PlaybackQueue(listOf(a, b, c)).move("c", -1)
        assertEquals(listOf(a, c, b), queue.items)
        assertEquals(listOf(c, a, b), queue.move("a", 1).items)
        assertEquals(queue.items, queue.move("a", -1).items)
        assertEquals(queue.items, queue.move("b", 1).items)
        assertEquals(queue.items, queue.move("missing", 1).items)
    }

    @Test fun removeAlsoSupportsJumpingWithoutLosingEarlierChoices() {
        assertEquals(listOf(a, c), PlaybackQueue(listOf(a, b, c)).remove("b").items)
        assertEquals(listOf(a), PlaybackQueue(listOf(a)).remove("missing").items)
    }

    @Test fun advanceConsumesOneChoiceAndDropsTheCurrentVideo() {
        val (next, remaining) = PlaybackQueue(listOf(a, b, c)).advance("a")
        assertEquals(b, next)
        assertEquals(listOf(c), remaining.items)
        val (last, empty) = remaining.advance()
        assertEquals(c, last)
        assertTrue(empty.items.isEmpty())
        assertNull(empty.advance().first)
        assertNull(PlaybackQueue(listOf(a)).advance("a").first)
    }

    @Test fun clearLeavesNoNextChoice() {
        val cleared = PlaybackQueue(listOf(a, b)).clear()
        assertTrue(cleared.items.isEmpty())
        assertNull(cleared.advance().first)
    }

    @Test fun restoredQueuesAreDeduplicatedAndDoNotShareMutableLists() {
        val saved = mutableListOf(a, a, b)
        val queue = PlaybackQueue(saved)
        saved.clear()
        assertEquals(listOf(a, b), queue.items)
        assertEquals(queue.items, PlaybackQueue(queue.items).items)
        assertTrue(PlaybackQueue().items.isEmpty())
    }
}
