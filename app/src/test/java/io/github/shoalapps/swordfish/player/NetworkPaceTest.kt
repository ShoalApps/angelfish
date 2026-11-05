package io.github.shoalapps.swordfish.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPaceTest {
    private val hd = 2_500_000L
    private fun target(speed: Long, metered: Boolean = false, saving: Boolean = false, low: Boolean = false, stalled: Boolean = false) =
        NetworkPace.targetSeconds(speed, hd, metered, saving, low, stalled)

    @Test fun theFasterTheConnectionTheLessIsHeld() {
        assertEquals(15, target(hd * 10))
        assertEquals(25, target(hd * 4))
        assertEquals(50, target(hd * 2))
        assertEquals(120, target(hd))
    }

    @Test fun aRecentStallMeansHoldingMore() {
        assertEquals(60, target(hd * 10, stalled = true))
        assertEquals(120, target(hd, stalled = true))
    }

    @Test fun meteredDataIsOnlySpentAheadWhenNeeded() {
        assertEquals(30, target(hd * 2, metered = true))
        assertEquals(120, target(hd, metered = true))
        assertEquals(20, target(hd, metered = true, saving = true))
    }

    @Test fun lowMemoryCapsItAndItStaysInBounds() {
        assertEquals(30, target(hd, low = true))
        assertEquals(15, target(hd * 100, saving = true))
        assertEquals(120, NetworkPace.targetSeconds(0, 0, false, false, false, false))
    }

    @Test fun fetchingRestsThenResumesBeforeRunningLow() {
        assertEquals(10, NetworkPace.resumeSeconds(15))
        assertEquals(72, NetworkPace.resumeSeconds(120))
        listOf(15, 20, 25, 30, 50, 60, 120).forEach { assertTrue(NetworkPace.resumeSeconds(it) < it) }
    }

    @Test fun preloadingFollowsTheConnectionAndStopsDuringPlayback() {
        assertEquals(3, NetworkPace.preloadCount(hd * 5, false, false, false))
        assertEquals(2, NetworkPace.preloadCount(hd * 2, false, false, false))
        assertEquals(1, NetworkPace.preloadCount(hd * 5, true, false, false))
        assertEquals(0, NetworkPace.preloadCount(hd / 2, false, false, false))
        assertEquals(0, NetworkPace.preloadCount(hd * 5, false, true, false))
        assertEquals(0, NetworkPace.preloadCount(hd * 5, false, false, true))
    }

    @Test fun downloadsLeaveRoomForTheVideo() {
        assertTrue(NetworkPace.hasRoomForDownload(hd * 3, hd))
        assertTrue(!NetworkPace.hasRoomForDownload(hd * 2, hd))
        assertEquals(hd * 8, NetworkPace.downloadLimitBps(hd * 10, hd))
        assertEquals(2_000_000L, NetworkPace.downloadLimitBps(hd, hd))
    }
}
