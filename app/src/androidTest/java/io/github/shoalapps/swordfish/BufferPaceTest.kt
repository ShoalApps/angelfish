package io.github.shoalapps.swordfish

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.shoalapps.swordfish.player.NetworkPace
import io.github.shoalapps.swordfish.player.SkipAware
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Plays a long local video through the real buffering rules. Needs pace.mp4 pushed to the app's external files. */
@RunWith(AndroidJUnit4::class)
class BufferPaceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun <T> main(block: () -> T): T { var r: T? = null; instrumentation.runOnMainSync { r = block() }; return r!! }

    @Test fun loadingStopsAtTheTargetThenCarriesOnAsItPlays() {
        val context = instrumentation.targetContext
        val file = File(context.getExternalFilesDir(null), "pace.mp4")
        assumeTrue(file.isFile)
        val player = main {
            ExoPlayer.Builder(context).setLoadControl(SkipAware.loadControl()).setBandwidthMeter(NetworkPace.meter).build().apply {
                setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
                volume = 0f
                // The whole file still loads; this only spares test devices whose video decoders can't cope.
                trackSelectionParameters = trackSelectionParameters.buildUpon().setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true).build()
                prepare()
            }
        }
        fun ahead() = main { (player.bufferedPosition - player.currentPosition) / 1000 }
        try {
            Thread.sleep(10_000)
            val target = main { NetworkPace.targetSeconds() }
            val paused = ahead()
            android.util.Log.i("BufferPace", "target ${target}s, holding ${paused}s of a ${main { player.duration } / 1000}s video, speed ${NetworkPace.speedBps()}, media ${NetworkPace.mediaBps}")
            assertEquals(Player.STATE_READY, main { player.playbackState })
            assertTrue("held $paused for target $target", paused >= NetworkPace.MIN_SECONDS - 1 && paused <= target + 15)

            // Watching through more than the target proves loading wakes up again and never stalls.
            main { player.setPlaybackSpeed(4f); player.play() }
            var lowest = Long.MAX_VALUE
            repeat(20) {
                Thread.sleep(1000)
                lowest = minOf(lowest, ahead())
                assertEquals(Player.STATE_READY, main { player.playbackState })
            }
            val at = main { player.currentPosition } / 1000
            android.util.Log.i("BufferPace", "after playing: at ${at}s, holding ${ahead()}s, lowest ${lowest}s")
            assertTrue("reached $at", at > target + 20)
            assertTrue("ran down to $lowest", lowest >= 5)
            assertTrue(ahead() <= target + 15)

            // A seek far ahead starts fresh and fills up again.
            main { player.pause(); player.setPlaybackSpeed(1f); player.seekTo(400_000) }
            Thread.sleep(6000)
            assertEquals(Player.STATE_READY, main { player.playbackState })
            assertTrue(ahead() >= NetworkPace.MIN_SECONDS - 1)
        } finally {
            main { player.release() }
        }
    }
}
