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
class PlaylistPersistenceTest {
    private fun isolatedTest(test: (Context, String) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "playlist-test-${System.nanoTime()}"
        val isolated = object : ContextWrapper(context) {
            override fun getSharedPreferences(ignored: String, mode: Int) = context.getSharedPreferences(name, mode)
        }
        try { test(isolated, name) } finally { context.deleteSharedPreferences(name) }
    }
    private fun video(id: String) = VideoItem("https://www.youtube.com/watch?v=$id", id, id, "Channel")

    @Test fun playlistEditsSurviveReopeningAndQueueConsumption() = isolatedTest { context, _ ->
        val first = Store(context)
        assertTrue(first.playlists.value.isEmpty())
        assertFalse(first.signedIn)
        assertNull(first.createPlaylist("  "))
        val id = first.createPlaylist(" Favourites ")!!
        val other = first.createPlaylist("Other")!!
        val a = video("a")
        val b = video("b")
        val c = video("c")
        first.addToPlaylist(id, a)
        first.addToPlaylist(id, b)
        first.addToPlaylist(id, c)
        assertFalse(first.addToPlaylist(id, a))
        first.addToPlaylist(other, a)
        first.moveInPlaylist(id, c.id, -1)
        first.renamePlaylist(id, " Renamed ")
        var reopened = Store(context)
        assertEquals("Renamed", reopened.playlists.value.first { it.id == id }.name)
        assertEquals(listOf(a, c, b), reopened.playlists.value.first { it.id == id }.items)
        reopened.replaceQueue(reopened.playlists.value.first { it.id == id }.items)
        assertEquals(a, reopened.advanceQueue())
        reopened.queueCurrent(a.id)
        reopened.addAllToQueue(listOf(a, b, c))
        assertEquals(listOf(c, b), reopened.queue.value)
        reopened.clearQueue()
        reopened.removeFromPlaylist(id, c.id)
        reopened = Store(context)
        assertEquals(listOf(a, b), reopened.playlists.value.first { it.id == id }.items)
        assertEquals(listOf(a), reopened.playlists.value.first { it.id == other }.items)
        assertTrue(reopened.queue.value.isEmpty())
        reopened.deletePlaylist(id)
        assertEquals(listOf(other), Store(context).playlists.value.map { it.id })
    }

    @Test fun corruptRecordsAndItemsLeaveOtherPlaylistsReadable() = isolatedTest { context, name ->
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("local-playlist:broken", "{not json")
            .putInt("local-playlist:wrong-type", 42)
            .putString("local-playlist:partial", """{"name":"Partial","items":[{"id":"bad"},{"url":"url","id":"good","title":"Readable","channelName":"Channel"}]}""")
            .putString("local-playlist:empty", """{"name":"Empty"}""")
            .commit()
        val store = Store(context)
        assertEquals(setOf("partial", "empty"), store.playlists.value.map { it.id }.toSet())
        assertEquals(listOf("good"), store.playlists.value.first { it.id == "partial" }.items.map { it.id })
        store.renamePlaylist("partial", "Recovered")
        store.createPlaylist("New")
        val reopened = Store(context)
        assertEquals(listOf("good"), reopened.playlists.value.first { it.id == "partial" }.items.map { it.id })
        assertTrue(reopened.playlists.value.first { it.id == "empty" }.items.isEmpty())
        assertEquals("{not json", prefs.getString("local-playlist:broken", null))
    }
}
