package io.github.shoalapps.swordfish

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.shoalapps.swordfish.data.Store
import io.github.shoalapps.swordfish.data.VideoItem
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueuePersistenceTest {
    @Test fun queueEditsAndAdvancementSurviveReopeningTheStore() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "queue-test-${System.nanoTime()}"
        val isolated = object : ContextWrapper(context) {
            override fun getSharedPreferences(ignored: String, mode: Int) = context.getSharedPreferences(name, mode)
        }
        fun video(id: String) = VideoItem("https://www.youtube.com/watch?v=$id", id, id, "Channel")
        val a = video("a")
        val b = video("b")
        val c = video("c")
        try {
            val first = Store(isolated)
            first.addToQueue(a)
            first.addToQueue(b)
            first.addToQueue(c)
            first.playNext(c)
            first.moveInQueue(b.id, -1)
            assertEquals(listOf(c, b, a), Store(isolated).queue.value)

            val reopened = Store(isolated)
            reopened.queueCurrent(c.id)
            reopened.addToQueue(c)
            assertEquals(b, reopened.advanceQueue())
            assertEquals(listOf(a), Store(isolated).queue.value)
            reopened.removeFromQueue(a.id)
            assertTrue(Store(isolated).queue.value.isEmpty())
            reopened.addToQueue(a)
            reopened.clearQueue()
            assertTrue(Store(isolated).queue.value.isEmpty())
        } finally {
            context.deleteSharedPreferences(name)
        }
    }
}
