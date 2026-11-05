package io.github.shoalapps.swordfish.data

import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class LocalPlaylistCodecTest {
    private val codec = LocalPlaylistCodec()
    private val a = VideoItem("https://untrusted.invalid/private", "abcdefghijk", "Video A", "Channel",
        channelUrl = "private-channel", channelAvatar = "private-avatar", thumbnail = "private-thumbnail", viewsText = "private-views")
    private val b = a.copy(id = "ABCDEFGHIJK", title = "Video B")
    private fun playlist(name: String = "Saved", items: List<VideoItem> = listOf(a, b)) = LocalPlaylist("local-id", name, items)
    private fun decode(text: String) = codec.decode(text.toByteArray(Charsets.UTF_8))
    private fun document(items: String, name: String = "Saved") =
        """{"version":1,"playlists":[{"name":"$name","items":[$items]}]}"""
    private val entry = """{"id":"abcdefghijk","title":"Video A","channelName":"Channel"}"""
    private var id = 0
    private fun merge(existing: List<LocalPlaylist>, incoming: LocalPlaylistCodec.Decoded) =
        codec.merge(existing, incoming) { "new-${id++}" }
    private fun rejects(action: () -> Unit) {
        try {
            action()
            fail("Expected an invalid playlist file to be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun roundTripKeepsNamesOrderAndOnlyPortableFields() {
        val encoded = codec.encode(listOf(playlist("Café 🎵"), playlist("Empty", emptyList())))
        val text = encoded.toString(Charsets.UTF_8)
        assertTrue(text.contains("\"version\":1"))
        listOf("local-id", "private", "url", "thumbnail", "cookie", "account", "pageId", "viewsText").forEach {
            assertFalse("Unexpected exported field: $it", text.contains(it))
        }
        val decoded = codec.decode(encoded)
        assertEquals(listOf("Café 🎵", "Empty"), decoded.playlists.map { it.name })
        assertEquals(listOf(a.id, b.id), decoded.playlists.first().items.map { it.id })
        val restored = decoded.playlists.first().items.first()
        assertEquals(a.title, restored.title)
        assertEquals(a.channelName, restored.channelName)
        assertEquals("https://www.youtube.com/watch?v=${a.id}", restored.url)
        assertEquals("https://i.ytimg.com/vi/${a.id}/hqdefault.jpg", restored.thumbnail)
        assertNull(restored.channelUrl)
        assertNull(restored.channelAvatar)
        assertTrue(decoded.playlists.last().items.isEmpty())
    }

    @Test fun unknownFieldsAndUntrustedUrlsAreIgnored() {
        val decoded = decode("""{"version":1,"cookie":"secret","future":{},"playlists":[{"name":" Saved ","future":true,"items":[{"id":"abcdefghijk","title":"A","channelName":"C","url":"file:///secret","thumbnail":"content://private","extra":[]}]}]}""")
        assertEquals("Saved", decoded.playlists.single().name)
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk", decoded.playlists.single().items.single().url)
    }

    @Test fun brokenEntriesDoNotLoseNeighboursAndAreCounted() {
        val decoded = decode(document("""$entry,null,{},42,{"id":"../../etc/x","title":"Bad","channelName":"C"},{"id":12345678901,"title":"Bad","channelName":"C"},{"id":"ABCDEFGHIJK","title":false,"channelName":"C"},$entry,{"id":"ABCDEFGHIJK","title":"B","channelName":"C"}"""))
        assertEquals(listOf(a.id, b.id), decoded.playlists.single().items.map { it.id })
        assertEquals(6, decoded.skippedVideos)
        assertEquals(1, decoded.duplicateVideos)
    }

    @Test fun brokenPlaylistsDoNotLoseOtherPlaylists() {
        val decoded = decode("""{"version":1,"playlists":[null,{}, {"name":" ","items":[$entry]}, {"name":42,"items":[]},{"name":"Missing items"},{"name":"Good","items":[$entry]}]}""")
        assertEquals(listOf("Good"), decoded.playlists.map { it.name })
        assertEquals(5, decoded.skippedPlaylists)
        assertEquals(1, decoded.skippedVideos)
    }

    @Test fun invalidIdsAreSkipped() {
        listOf("", "short", "abcdefghijkl", "abcdefghij!", "abcdefghij/", "abcdefghijé", "abcdefghij\n").forEach { bad ->
            val escaped = bad.replace("\n", "\\n")
            assertEquals(1, decode(document(entry.replace("abcdefghijk", escaped))).skippedVideos)
        }
        assertEquals(0, decode(document(entry.replace("abcdefghijk", "abcd_1234-Z"))).skippedVideos)
    }

    @Test fun textLengthsAndTypesAreBoundedWithoutTruncatingNames() {
        assertEquals(1, decode(document(entry, "n".repeat(LocalPlaylistCodec.MAX_NAME + 1))).skippedPlaylists)
        assertEquals(1, decode(document(entry.replace("Video A", "t".repeat(LocalPlaylistCodec.MAX_TITLE + 1)))).skippedVideos)
        assertEquals(1, decode(document(entry.replace("Channel", "c".repeat(LocalPlaylistCodec.MAX_CHANNEL + 1)))).skippedVideos)
        assertEquals(0, decode(document(entry, "n".repeat(LocalPlaylistCodec.MAX_NAME))).skippedPlaylists)
        assertEquals(0, decode(document(entry.replace("Video A", ""))).skippedVideos)
    }

    @Test fun playlistAndItemCountsAreBoundedAndExcessIsCounted() {
        val records = (1..LocalPlaylistCodec.MAX_PLAYLISTS + 2).joinToString(",") { """{"name":"P$it","items":[]}""" }
        val decoded = decode("""{"version":1,"playlists":[$records]}""")
        assertEquals(LocalPlaylistCodec.MAX_PLAYLISTS, decoded.playlists.size)
        assertEquals(2, decoded.skippedPlaylists)
        val manyItems = decode(document((1..LocalPlaylistCodec.MAX_ITEMS + 3).joinToString(",") { entry }))
        assertEquals(3, manyItems.skippedVideos)
        assertEquals(LocalPlaylistCodec.MAX_ITEMS - 1, manyItems.duplicateVideos)
    }

    @Test fun totalVideoCountIsBoundedAcrossPlaylists() {
        val entries = (1..LocalPlaylistCodec.MAX_ITEMS).joinToString(",") { entry }
        val records = (1..6).joinToString(",") { """{"name":"P$it","items":[$entries]}""" }
        val decoded = decode("""{"version":1,"playlists":[$records]}""")
        assertEquals(1000, decoded.skippedVideos)
        assertTrue(decoded.playlists.last().items.isEmpty())
    }

    @Test fun emptyFilesUnsupportedVersionsAndMalformedDocumentsAreRejected() {
        listOf("", "{", "[]", "null", "{}", """{"version":2,"playlists":[]}""",
            """{"version":"1","playlists":[]}""", """{"version":1,"playlists":{}}""",
            """{"version":1,"playlists":[]} trailing""").forEach { text -> rejects { decode(text) } }
        assertTrue(decode("""{"version":1,"playlists":[]}""").playlists.isEmpty())
    }

    @Test fun byteLimitUtf8AndParserDepthAreEnforced() {
        rejects { codec.decode(ByteArray(LocalPlaylistCodec.MAX_BYTES + 1)) }
        rejects { codec.decode(byteArrayOf(0xc3.toByte(), 0x28)) }
        rejects { decode("""{"version":1,"playlists":[],"unknown":${"[".repeat(40)}0${"]".repeat(40)}}""") }
        // Brackets and escaped quotes inside strings do not affect the depth check.
        assertEquals(0, decode(document(entry.replace("Video A", "[".repeat(100) + "\\\""))).skippedVideos)
    }

    @Test fun streamReadStopsAtByteLimitEvenWithoutProviderMetadata() {
        var consumed = 0
        val endless = object : java.io.InputStream() {
            override fun read(): Int { consumed++; return 32 }
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                consumed += length
                bytes.fill(32, offset, offset + length)
                return length
            }
        }
        rejects { codec.read(endless) }
        assertEquals(LocalPlaylistCodec.MAX_BYTES + 1, consumed)
        assertEquals(1, codec.read(ByteArrayInputStream(codec.encode(listOf(playlist())))).playlists.size)
    }

    @Test fun importingAddsWithoutChangingExistingListsAndNumbersCollisions() {
        val original = playlist(items = listOf(b))
        val incoming = codec.decode(codec.encode(listOf(playlist())))
        val imported = merge(listOf(original, playlist("Saved (2)", emptyList()).copy(id = "other")), incoming)
        assertEquals(original, imported.playlists.first())
        assertEquals("Saved (3)", imported.playlists.last().name)
        assertEquals(1, imported.addedPlaylists)
        assertEquals(2, imported.addedVideos)
        assertEquals(1, imported.renamedPlaylists)
        assertNotEquals(original.id, imported.playlists.last().id)
        val repeated = merge(imported.playlists, incoming)
        assertEquals(imported.playlists, repeated.playlists)
        assertEquals(0, repeated.addedPlaylists)
        assertEquals(1, repeated.duplicatePlaylists)
    }

    @Test fun identicalContentsIgnoreMetadataButRespectOrderAndDifferentNames() {
        val incoming = codec.decode(codec.encode(listOf(playlist(items = listOf(a.copy(title = "New"), b)))))
        assertEquals(1, merge(listOf(playlist()), incoming).duplicatePlaylists)
        assertEquals(1, merge(listOf(playlist(items = listOf(b, a))), incoming).addedPlaylists)
        assertEquals(1, merge(listOf(playlist("Other")), incoming).addedPlaylists)
    }

    @Test fun duplicatesInsideTheFileAndEmptyPlaylistsAreIdempotent() {
        val incoming = codec.decode(codec.encode(listOf(playlist(), playlist(), playlist("Empty", emptyList()))))
        val first = merge(emptyList(), incoming)
        assertEquals(2, first.addedPlaylists)
        assertEquals(1, first.duplicatePlaylists)
        val second = merge(first.playlists, incoming)
        assertEquals(0, second.addedPlaylists)
        assertEquals(3, second.duplicatePlaylists)
    }

    @Test fun numberedLongNamesStayBoundedAndAreRecognisedOnRepeat() {
        val name = "n".repeat(LocalPlaylistCodec.MAX_NAME)
        val incoming = codec.decode(codec.encode(listOf(playlist(name))))
        val first = merge(listOf(playlist(name, emptyList())), incoming)
        assertEquals(LocalPlaylistCodec.MAX_NAME, first.playlists.last().name.length)
        assertTrue(first.playlists.last().name.endsWith(" (2)"))
        assertEquals(1, merge(first.playlists, incoming).duplicatePlaylists)
    }

    @Test fun exportRejectsInvalidOrOversizedDataInsteadOfSilentlyLosingIt() {
        rejects { codec.encode(listOf(playlist(items = listOf(a.copy(id = "bad"))))) }
        rejects { codec.encode(listOf(playlist("n".repeat(LocalPlaylistCodec.MAX_NAME + 1)))) }
        rejects { codec.encode(listOf(playlist(items = List(LocalPlaylistCodec.MAX_ITEMS + 1) { a }))) }
        rejects { codec.encode(List(LocalPlaylistCodec.MAX_PLAYLISTS + 1) { playlist() }) }
        val large = a.copy(title = "t".repeat(LocalPlaylistCodec.MAX_TITLE), channelName = "c".repeat(LocalPlaylistCodec.MAX_CHANNEL))
        rejects { codec.encode(List(5) { playlist(items = List(1000) { large }) }) }
    }
}
