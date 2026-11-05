package io.github.shoalapps.swordfish.data

import org.junit.Assert.*
import org.junit.Test

class DownloadErrorsTest {
    @Test fun rawExtractorWarningsNeverReachDownloadRows() {
        val raw = "WARNING: Your yt-dlp version is older than 90 days!\nWARNING: YouTube is forcing SABR streaming\nERROR: HTTP Error 403: Forbidden"
        val shown = DownloadErrors.message(raw)
        assertTrue(DownloadErrors.rejectedByYouTube(raw))
        assertFalse(shown.contains("WARNING"))
        assertFalse(shown.contains("SABR"))
        assertTrue(shown.length < 150)
        assertEquals(shown, DownloadErrors.message(shown))
    }
    @Test fun downloaderUpdateAndStorageFailuresHaveUsefulMessages() {
        assertTrue(DownloadErrors.message("DOWNLOADER_UPDATE_FAILED").contains("update the downloader"))
        assertTrue(DownloadErrors.message("ENOSPC").contains("free space"))
        assertEquals("Couldn't download this video. Try again.", DownloadErrors.message("private internal diagnostics without a useful user message"))
    }

    @Test fun aBotCheckSaysSoInsteadOfBlamingTheVideo() {
        assertTrue(DownloadErrors.message("ERROR: [youtube] x: Sign in to confirm you're not a bot. This helps protect").contains("VPN"))
    }
}
