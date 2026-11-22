package io.github.shoalapps.swordfish.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionsTtmlTest {
    @Test fun readsYouTubeTtml() {
        val ttml = """<?xml version="1.0" encoding="utf-8" ?><tt xml:lang="en" xmlns="http://www.w3.org/ns/ttml"><head></head><body><div>
            <p begin="00:00:00.160" end="00:00:02.879" style="s2">Hello &amp; welcome<br />to the <span style="s1">show</span></p>
            <p begin="00:01:02.500" end="00:01:04.000">it&#39;s &quot;fine&quot;</p>
            </div></body></tt>"""
        val cues = Captions.parse(ttml)
        assertEquals(2, cues.size)
        assertEquals(Cue(160, 2879, "Hello & welcome\nto the show"), cues[0])
        assertEquals(Cue(62_500, 64_000, "it's \"fine\""), cues[1])
    }

    @Test fun readsOffsetAndTickTimes() {
        val ttml = """<tt ttp:tickRate="10000000"><body><div>
            <p begin="1.5s" end="2500ms">a</p>
            <p begin="30000000t" dur="10000000t">b</p>
            </div></body></tt>"""
        val cues = Captions.parse(ttml)
        assertEquals(Cue(1500, 2500, "a"), cues[0])
        assertEquals(Cue(3000, 4000, "b"), cues[1])
    }

    @Test fun skipsEmptyParagraphs() {
        assertTrue(Captions.parse("""<tt><body><p begin="00:00:01.000" end="00:00:02.000"> </p></body></tt>""").isEmpty())
    }

    @Test fun stillReadsVtt() {
        assertEquals(listOf(Cue(1000, 2000, "Hi")), Captions.parse("WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nHi\n"))
    }

    private fun t(lang: String, auto: Boolean = false) = CaptionTrack(lang + if (auto) " (auto)" else "", "u", auto, lang)

    @Test fun prefersRememberedThenOwnLanguageThenOriginalAuto() {
        val tracks = listOf(t("ar"), t("de"), t("en", auto = true), t("fr"))
        assertEquals("fr", Captions.preferred(tracks, listOf("de"), remembered = "fr")?.lang)
        assertEquals("de", Captions.preferred(tracks, listOf("de-DE"), remembered = null)?.lang)
        // No match for the viewer's languages: the auto-generated one, which is in the video's own language.
        assertEquals(t("en", true), Captions.preferred(tracks, listOf("ja"), remembered = null))
    }

    @Test fun handWrittenBeatsAutoInTheSameLanguage() {
        val tracks = listOf(t("en", auto = true), t("en-GB"))
        assertEquals(t("en-GB"), Captions.preferred(tracks, listOf("en-US"), remembered = null))
        assertNull(Captions.preferred(emptyList(), listOf("en"), null))
    }
}

class CaptionsUntranslatedTest {
    @Test fun dropsTlangWherever_itSits() {
        val t = CaptionTrack("French (auto-translated)", "https://x/t?v=1&fmt=ttml&tlang=fr", true, "fr", translated = true)
        assertEquals("https://x/t?v=1&fmt=ttml", Captions.untranslated(t).url)
        assertEquals("https://x/t?v=1", Captions.untranslated(t.copy(url = "https://x/t?v=1&tlang=fr")).url)
    }
}

class CaptionsEscapedTagsTest {
    @Test fun escapedStylingIsStripped() {
        val vtt = "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\n&lt;i&gt;I must dance&lt;/i&gt; &amp; sing\n"
        assertEquals("I must dance & sing", Captions.parse(vtt)[0].text)
        val ttml = """<tt><body><p begin="00:00:01.000" end="00:00:02.000">&lt;i&gt;hey&lt;/i&gt;</p></body></tt>"""
        assertEquals("hey", Captions.parse(ttml)[0].text)
    }
}
