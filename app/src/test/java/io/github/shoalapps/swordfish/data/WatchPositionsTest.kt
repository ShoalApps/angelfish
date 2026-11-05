package io.github.shoalapps.swordfish.data

import org.junit.Assert.*
import org.junit.Test

class WatchPositionsTest {
    private fun video(id: String = "a") = VideoItem("https://www.youtube.com/watch?v=$id", id, "Title $id", "Channel",
        thumbnail = "file:///thumbnail.png", durationSec = 100)
    private fun saved(position: Long) = WatchPositions().record(video(), position, 100_000, 1)

    @Test fun resumeRequiresMoreThanThirtySecondsAndLessThanNinetyFivePercent() {
        assertEquals(0L, saved(30_000).resumeMs("a"))
        assertEquals(30_001L, saved(30_001).resumeMs("a"))
        assertEquals(94_999L, saved(94_999).resumeMs("a"))
        assertEquals(0L, saved(95_000).resumeMs("a"))
        assertTrue(saved(95_000).entries.isEmpty())
        assertTrue(saved(100_000).entries.isEmpty())
        assertFalse(WatchPositions.finished(100, 0))
        assertTrue(WatchPositions.finished(Long.MAX_VALUE, Long.MAX_VALUE))
    }

    @Test fun liveShortUnknownDurationAndInvalidRecordsAreExcluded() {
        val start = saved(40_000)
        assertTrue(start.record(video().copy(isLive = true), 40_000, 100_000, 2).entries.isEmpty())
        assertTrue(start.record(video().copy(isShort = true), 40_000, 100_000, 2).entries.isEmpty())
        assertTrue(start.record(video(), 40_000, -1, 2).entries.isEmpty())
        assertTrue(start.record(video(), 40_000, 0, 2).entries.isEmpty())
        assertTrue(saved(-1).entries.isEmpty())
        assertTrue(saved(0).entries.isEmpty())
        assertTrue(WatchPositions().record(video(""), 40_000, 100_000, 2).entries.isEmpty())
        assertTrue(WatchPositions().record(video(), 40_000, 100_000, -1).entries.isEmpty())
    }

    @Test fun cappedNewestFirstWithUpdatesReplacingOldEntriesAndEvictingOldest() {
        var positions = WatchPositions()
        for (i in 0..500) positions = positions.record(video("$i"), 40_000, 100_000, i.toLong())
        assertEquals(500, positions.entries.size)
        assertFalse(positions.entries.any { it.video.id == "0" })
        assertEquals("500", positions.entries.first().video.id)
        positions = positions.record(video("1").copy(title = "Updated"), 45_000, 100_000, 1000)
        assertEquals(500, positions.entries.size)
        assertEquals("Updated", positions.entries.first().video.title)
        assertEquals(45_000L, positions.resumeMs("1"))
        assertEquals(20, positions.continuing.size)
        assertEquals("1", positions.continuing.first().video.id)
        assertEquals(499, positions.remove("1").entries.size)
    }

    @Test fun finishedThenRepeatedVideoStartsAtZero() {
        val completed = saved(40_000).record(video(), 100_000, 100_000, 2)
        assertEquals(0L, completed.resumeMs("a"))
        val repeated = completed.record(video(), 10_000, 100_000, 3)
        assertEquals(0L, repeated.resumeMs("a"))
        assertTrue(repeated.continuing.isEmpty())
        assertEquals(40_000L, repeated.record(video(), 40_000, 100_000, 4).resumeMs("a"))
    }

    @Test fun codecRoundTripsMetadataAndToleratesMalformedNeighboursAndFutureFields() {
        val positions = saved(40_000)
        assertEquals(positions.entries, WatchPositions.decode(positions.encode()).entries)
        val entry = positions.encode().removePrefix("[").removeSuffix("]")
        val withFutureField = entry.dropLast(1) + ",\"future\":true}"
        val mixed = "[null,{},$withFutureField,{\"positionMs\":\"bad\"}]"
        assertEquals(positions.entries, WatchPositions.decode(mixed).entries)
        for (raw in listOf(null, "", "{", "{}", "null", "[true]")) {
            assertTrue(WatchPositions.decode(raw).entries.isEmpty())
        }
        val staleDuplicate = positions.entries.single().copy(positionMs = 45_000, updatedAtMs = 0)
        assertEquals(positions.entries, WatchPositions(listOf(staleDuplicate) + positions.entries).entries)
    }
}
