package io.github.shoalapps.swordfish

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.shoalapps.swordfish.data.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A real download through yt-dlp, so it's only meaningful when YouTube lets this network in. */
@RunWith(AndroidJUnit4::class)
class AudioDownloadTest {
    @Test fun anAudioCopyIsFoundAndAVideoCopyReplacesIt() {
        val id = "abcdefghijk"
        val video = VideoItem("https://www.youtube.com/watch?v=$id", id, "Test", "Test")
        val offline = AppGraph.offline
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        fun made(name: String) = java.io.File(context.cacheDir, name).apply { writeBytes(ByteArray(2048)) }
        try {
            offline.keep(video, audioOnly = true)
            offline.finish(video, made("video.m4a"))
            assertEquals("m4a", offline.local(id)!!.extension)
            assertTrue(offline.videos.value.first { it.video.id == id }.audioOnly)

            offline.keep(video, audioOnly = false)
            assertEquals(null, offline.local(id))
            assertEquals(false, offline.videos.value.first { it.video.id == id }.audioOnly)
            offline.finish(video, made("video.mp4"))
            assertEquals("mp4", offline.local(id)!!.extension)
        } finally {
            offline.remove(id)
        }
        assertEquals(null, offline.local(id))
    }

    @NetworkTest @Test fun savingAudioGivesAPlayableFileAndNoVideo() {
        val id = "aqz-KE-bpKQ"
        val video = VideoItem("https://www.youtube.com/watch?v=$id", id, "Big Buck Bunny", "Blender")
        AppGraph.store.updateSettings { it.copy(downloadWifiOnly = false) }
        val offline = AppGraph.offline
        offline.remove(id)
        offline.keep(video, audioOnly = true)
        val deadline = System.currentTimeMillis() + 240_000
        while (System.currentTimeMillis() < deadline) {
            val entry = offline.videos.value.firstOrNull { it.video.id == id }
            if (entry?.complete == true || entry?.error != null) break
            Thread.sleep(2000)
        }
        val entry = offline.videos.value.first { it.video.id == id }
        android.util.Log.i("AudioDownload", "complete ${entry.complete} error ${entry.error} file ${offline.local(id)?.name} ${offline.local(id)?.length()}")
        try {
            assumeTrue("YouTube refused this network: ${entry.error}", entry.complete)
            assertEquals("m4a", offline.local(id)!!.extension)
            assertTrue(offline.local(id)!!.length() > 100_000)
        } finally {
            offline.remove(id)
        }
    }
}
