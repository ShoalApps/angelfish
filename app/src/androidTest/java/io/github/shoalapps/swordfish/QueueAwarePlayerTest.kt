package io.github.shoalapps.swordfish

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.shoalapps.swordfish.player.QueueAwarePlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class QueueAwarePlayerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /** The notification depends on hearing every change, not only the ones the wrapper cares about. */
    @Test fun listenersHearEverythingAndNextGoesToTheQueue() {
        val states = CopyOnWriteArrayList<Int>()
        val commands = CopyOnWriteArrayList<Player.Commands>()
        var nexts = 0
        lateinit var real: ExoPlayer
        lateinit var player: QueueAwarePlayer
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { states += state }
            override fun onAvailableCommandsChanged(available: Player.Commands) { commands += available }
        }
        instrumentation.runOnMainSync {
            real = ExoPlayer.Builder(instrumentation.targetContext).build()
            player = QueueAwarePlayer(real) { nexts++ }
            player.addListener(listener)
            player.setMediaItem(MediaItem.fromUri("file:///nothing-here.mp4"))
            player.prepare()
        }
        val until = System.currentTimeMillis() + 5000
        while (states.isEmpty() && System.currentTimeMillis() < until) Thread.sleep(50)
        assertTrue("no state changes reached the listener", states.isNotEmpty())
        instrumentation.runOnMainSync {
            assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
            player.seekToNext()
            player.seekToNextMediaItem()
        }
        assertEquals(2, nexts)
        commands.lastOrNull()?.let { assertTrue(it.contains(Player.COMMAND_SEEK_TO_NEXT)) }
        instrumentation.runOnMainSync {
            player.removeListener(listener)
            real.release()
        }
    }
}
