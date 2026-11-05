package io.github.shoalapps.swordfish

import io.github.shoalapps.swordfish.data.*
import org.junit.Assert.*
import org.junit.Test

class OfflinePolicyTest {
    private fun video(id: String) = VideoItem("https://www.youtube.com/watch?v=$id", id, id, "Channel")

    @Test fun automaticSelectionExcludesLiveShortsDuplicatesAndCurrent() {
        val current = video("current0001")
        val first = video("first000001")
        val second = video("second00001")
        val feed = listOf(current, first.copy(isLive = true), first.copy(isShort = true), first, first, second)
        assertEquals(listOf(first, second), OfflinePolicy.recommendations(feed, 4, current.id))
        assertTrue(OfflinePolicy.recommendations(feed, 0, current.id).isEmpty())
        assertEquals(listOf(first), OfflinePolicy.recommendations(feed, 1, current.id))
    }

    @Test fun automaticDownloadsAreOffByDefaultAndWaitDuringPlayback() {
        assertFalse(Settings().cacheCurrentVideo)
        assertEquals(0, Settings().autoDownloadCount)
        val current = video("current0001")
        val next = video("next0000001")
        assertEquals(listOf(next), OfflinePolicy.toDownload(null, listOf(next), watching = false))
        assertTrue(OfflinePolicy.toDownload(null, listOf(next), watching = true).isEmpty())
        assertEquals(listOf(current), OfflinePolicy.toDownload(current, listOf(next), watching = true))
    }

    @Test fun audioAsksForSoundAloneAndVideoForBoth() {
        assertFalse(OfflinePolicy.format(720, true).contains("bv"))
        assertTrue(OfflinePolicy.format(720, true).startsWith("ba"))
        assertTrue(OfflinePolicy.format(720, false).contains("height<=720"))
        assertFalse(OfflineVideo(video("old00000001")).audioOnly)
    }

    @Test fun cleanupKeepsManualDownloadsAndCurrentButDropsStaleAutomaticCopies() {
        val saved = OfflineVideo(video("saved000001"), kept = true)
        val playing = OfflineVideo(video("current0001"))
        val stale = OfflineVideo(video("stale000001"))
        assertTrue(OfflinePolicy.retain(saved, playing.video.id, emptySet()))
        assertTrue(OfflinePolicy.retain(playing, playing.video.id, emptySet()))
        assertFalse(OfflinePolicy.retain(stale, playing.video.id, emptySet()))
        assertTrue(OfflinePolicy.retain(stale, playing.video.id, setOf(stale.video.id)))
    }

    @Test fun feedRefreshPrioritisesNewVideosAndCannotRepeatAnIdenticalOrder() {
        val old = listOf(video("old00000001"), video("old00000002"))
        val fresh = video("fresh000001")
        assertEquals(fresh, FreshFeed.order(old + fresh, old, 1).first())
        val repeated = FreshFeed.order(old + old, old, 0)
        assertEquals(2, repeated.size)
        assertNotEquals(old, repeated)
    }

    @Test fun dataSaverCapsBothPlayersAndMobileDefaultIs720() {
        val settings = Settings()
        assertEquals(720, settings.qualityMobile)
        assertEquals(720, NetworkPolicy.quality(settings, metered = true, saving = false))
        assertEquals(1080, NetworkPolicy.quality(settings, metered = false, saving = false))
        assertEquals(720, NetworkPolicy.quality(settings.copy(qualityMobile = 2160), metered = true, saving = true))
        assertEquals(360, NetworkPolicy.quality(settings.copy(qualityMobile = 360), metered = true, saving = true))
        assertTrue(settings.downloadWifiOnly)
    }

    @Test fun notificationParamsUseChannelAndRequestedMode() {
        val channel = "UC" + "a".repeat(22)
        for ((mode, index) in listOf(NotificationMode.ALL to 2, NotificationMode.PERSONALISED to 1, NotificationMode.NONE to 3)) {
            val decoded = java.util.Base64.getDecoder().decode(java.net.URLDecoder.decode(SubscriptionNotifications.preferenceParams(channel, mode), "UTF-8"))
            assertEquals(channel, decoded.copyOfRange(2, 26).toString(Charsets.UTF_8))
            assertEquals(index.toByte(), decoded[29])
        }
    }
}
