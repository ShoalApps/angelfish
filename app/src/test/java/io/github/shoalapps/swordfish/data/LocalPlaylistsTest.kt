package io.github.shoalapps.swordfish.data

import org.junit.Assert.*
import org.junit.Test

class LocalPlaylistsTest {
    private fun video(id: String) = VideoItem("https://www.youtube.com/watch?v=$id", id, id, "Channel")
    private val a = video("a")
    private val b = video("b")
    private val c = video("c")
    private fun saved() = LocalPlaylists().create("one", " First ").create("two", "Second")
        .add("one", a).add("one", b).add("one", c).add("two", a)

    @Test fun namedCollectionsTrimNamesAndRejectBlankNamesOrDuplicateIds() {
        val lists = saved().create("one", "Again").create("three", "  ").create("", "Invalid")
        assertEquals(listOf("First", "Second"), lists.playlists.map { it.name })
        assertEquals("First", lists.rename("one", " ").playlists.first().name)
        assertEquals("Renamed", lists.rename("one", " Renamed ").playlists.first().name)
        assertEquals(listOf("two"), lists.delete("one").playlists.map { it.id })
    }

    @Test fun duplicateVideoIdsAreIgnoredWithinEachPlaylist() {
        val lists = saved().add("one", a.copy(title = "Updated"))
        assertEquals(listOf(a, b, c), lists.playlists.first().items)
        assertEquals(listOf(a), lists.playlists.last().items)
        assertEquals(lists.playlists, lists.add("missing", a).playlists)
    }

    @Test fun moveAndRemoveKeepOtherVideosAndPlaylists() {
        val lists = saved().move("one", "c", -1)
        assertEquals(listOf(a, c, b), lists.playlists.first().items)
        assertEquals(listOf(c, a, b), lists.move("one", "a", 1).playlists.first().items)
        assertEquals(lists.playlists, lists.move("one", "a", -1).playlists)
        assertEquals(lists.playlists, lists.move("one", "b", 1).playlists)
        assertEquals(lists.playlists, lists.move("one", "missing", 1).playlists)
        assertEquals(listOf(a, b), lists.remove("one", "c").playlists.first().items)
        assertEquals(listOf(a), lists.remove("one", "a").playlists.last().items)
    }

    @Test fun restoredListsAreNormalisedAndDoNotShareMutableLists() {
        val videos = mutableListOf(a, a, b)
        val records = mutableListOf(LocalPlaylist("one", " First ", videos), LocalPlaylist("one", "Duplicate"))
        val lists = LocalPlaylists(records)
        videos.clear()
        records.clear()
        assertEquals(listOf(LocalPlaylist("one", "First", listOf(a, b))), lists.playlists)
    }

    @Test fun damagedItemsDoNotHideReadableVideos() {
        val raw = """{"name":"Saved","items":[{"id":"broken"},{"id":"a","url":"url","title":"A","channelName":"Channel"},null,{"id":"a","url":"url","title":"Duplicate","channelName":"Channel"}],"future":true}"""
        val recovered = LocalPlaylists.read("one", raw)!!
        assertEquals("Saved", recovered.name)
        assertEquals(listOf("a"), recovered.items.map { it.id })
        assertEquals("A", recovered.items.single().title)
        assertEquals(emptyList<VideoItem>(), LocalPlaylists.read("empty", """{"name":"Empty"}""")!!.items)
        assertNull(LocalPlaylists.read("broken", "{"))
        assertNull(LocalPlaylists.read("broken", """{"items":[]}"""))
    }
}
