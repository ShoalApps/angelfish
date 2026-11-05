package io.github.shoalapps.swordfish

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.shoalapps.swordfish.data.Store
import io.github.shoalapps.swordfish.data.VideoItem
import io.github.shoalapps.swordfish.data.WatchPositions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Local fixtures only: doesn't start the app's feeds or contact YouTube. */
@RunWith(AndroidJUnit4::class)
class WatchPositionPersistenceTest {
    @Test fun savesRemovalOptOutAndClearSurviveReopening() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefix = "watch-test-${System.nanoTime()}-"
        val isolated = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int) = context.getSharedPreferences(prefix + name, mode)
        }
        val video = VideoItem("fixture:a", "a", "Local title", "Local channel", thumbnail = "file:///fixture.png", durationSec = 100)
        try {
            val store = Store(isolated)
            assertTrue(store.settings.value.rememberWatchPositions)
            store.recordWatchPosition(video, 40_000, 100_000, 1)
            store.awaitWatchPositionWrites()
            val reopened = Store(isolated)
            assertEquals(video, reopened.watchPositions.value.single().video)
            assertEquals(40_000L, WatchPositions(reopened.watchPositions.value).resumeMs("a"))

            store.updateSettings { it.copy(rememberWatchPositions = false) }
            store.recordWatchPosition(video, 60_000, 100_000, 2)
            store.awaitWatchPositionWrites()
            assertEquals(40_000L, store.watchPositions.value.single().positionMs)
            assertFalse(Store(isolated).settings.value.rememberWatchPositions)
            store.clearWatchPositions()
            store.awaitWatchPositionWrites()
            assertTrue(Store(isolated).watchPositions.value.isEmpty())

            store.updateSettings { it.copy(rememberWatchPositions = true) }
            store.recordWatchPosition(video, 50_000, 100_000, 3)
            store.removeWatchPosition(video.id)
            store.awaitWatchPositionWrites()
            assertTrue(Store(isolated).watchPositions.value.isEmpty())
            store.recordWatchPosition(video, 50_000, 100_000, 4)
            store.recordWatchPosition(video, 95_000, 100_000, 5)
            store.awaitWatchPositionWrites()
            assertTrue(Store(isolated).watchPositions.value.isEmpty())
        } finally {
            context.deleteSharedPreferences(prefix + "tube")
            context.deleteSharedPreferences(prefix + "watch-positions")
        }
    }
}
