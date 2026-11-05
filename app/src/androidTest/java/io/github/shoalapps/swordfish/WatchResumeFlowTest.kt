package io.github.shoalapps.swordfish

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.shoalapps.swordfish.data.OfflineVideo
import io.github.shoalapps.swordfish.data.OfflineVideos
import io.github.shoalapps.swordfish.data.VideoItem
import io.github.shoalapps.swordfish.player.PlayerHolder
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The real player and a local file, no network. Needs resume.m4a (two minutes) pushed to the app's external files. */
@RunWith(AndroidJUnit4::class)
class WatchResumeFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val video = VideoItem("https://www.youtube.com/watch?v=resumefixtr", "resumefixtr", "Fixture", "Local", thumbnail = "file:///none.png", durationSec = 120)
    private val vm get() = PlayerHolder.viewModel
    private fun <T> ui(block: () -> T): T { var r: T? = null; rule.runOnUiThread { r = block() }; return r!! }
    private fun ready() {
        runCatching { rule.waitUntil(15_000) { ui { vm.player.duration > 0 && vm.player.currentMediaItem?.mediaId == video.id } } }.onFailure {
            error(ui { "player not ready: state ${vm.player.playbackState} duration ${vm.player.duration} id ${vm.player.currentMediaItem?.mediaId} error ${vm.player.playerError} vm ${vm.error} loading ${vm.loading} local ${AppGraph.offline.local(video.id)}" })
        }
    }
    private val saved get() = AppGraph.store.watchPositions.value.firstOrNull { it.video.id == video.id }

    /** Registers the file as an offline copy in the running app, as a finished download would. */
    @Suppress("UNCHECKED_CAST")
    private fun register(entry: OfflineVideo?) {
        val copies = OfflineVideos::class.java.getDeclaredField("_videos").apply { isAccessible = true }.get(AppGraph.offline) as MutableStateFlow<List<OfflineVideo>>
        copies.value = copies.value.filterNot { it.video.id == video.id } + listOfNotNull(entry)
    }

    @Test fun aSavedPositionResumesInTheRealPlayer() {
        val source = File(context.getExternalFilesDir(null), "resume.m4a")
        assumeTrue(source.isFile)
        val copy = File(File(context.filesDir, "offline").apply { mkdirs() }, "resumefixtr.m4a")
        source.copyTo(copy, overwrite = true)
        register(OfflineVideo(video, kept = true, complete = true, audioOnly = true))
        assertNotNull(AppGraph.offline.local(video.id))
        AppGraph.store.clearWatchPositions()
        AppGraph.store.updateSettings { it.copy(rememberWatchPositions = true) }

        ui { vm.open(video) }
        ready()
        ui { vm.seekTo(60_000) }
        rule.waitUntil(8_000) { ui { vm.player.currentPosition >= 60_000 } }
        ui { vm.close() }
        val position = assertNotNull(saved).let { saved!!.positionMs }
        assertTrue("saved at $position", position in 60_000..70_000)

        // Opening it again carries on from there, in the real player.
        ui { vm.open(video) }
        ready()
        assertTrue("resumed at ${ui { vm.player.currentPosition }}", ui { vm.player.currentPosition } >= position - 500)
        assertEquals(video.id, ui { vm.resumeNotice }?.first)

        ui { vm.startOver(video.id) }
        assertEquals(null, saved)
        rule.waitUntil(8_000) { ui { vm.player.currentPosition } < 10_000 }
        ui { vm.player.pause() }

        // Past 95% counts as finished, so next time starts from the beginning.
        ui { vm.seekTo(117_000) }
        rule.waitUntil(8_000) { ui { vm.player.currentPosition } >= 117_000 }
        ui { vm.close() }
        assertEquals(null, saved)
        ui { vm.open(video) }
        ready()
        assertTrue(ui { vm.player.currentPosition } < 10_000)
        ui { vm.close() }
        register(null)
        copy.delete()
    }
}
