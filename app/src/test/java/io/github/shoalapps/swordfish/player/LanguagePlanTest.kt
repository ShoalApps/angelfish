package io.github.shoalapps.swordfish.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguagePlanTest {
    private val original = AudioChoice("en.0", "English original", "en-US", original = true)
    private val frDub = AudioChoice("fr.1", "French (dubbed)", "fr-FR", dubbed = true)
    private val frAuto = AudioChoice("fr.2", "French (auto-dubbed)", "fr", dubbed = true, auto = true)
    private val deAuto = AudioChoice("de.2", "German (auto-dubbed)", "de", dubbed = true, auto = true)
    private val prefs = LanguagePrefs(mine = "fr-FR", preferDubs = true, allowAutoDubs = true, captionsWhenNoDub = true)

    @Test fun humanDubBeatsAutoDub() =
        assertEquals("fr.1", LanguagePlan.audio(listOf(original, frAuto, frDub), prefs))

    @Test fun autoDubWhenNoHumanOne() =
        assertEquals("fr.2", LanguagePlan.audio(listOf(original, frAuto), prefs))

    @Test fun autoDubsCanBeTurnedOff() =
        assertNull(LanguagePlan.audio(listOf(original, frAuto), prefs.copy(allowAutoDubs = false)))

    @Test fun dubsCanBeTurnedOff() =
        assertNull(LanguagePlan.audio(listOf(original, frDub), prefs.copy(preferDubs = false)))

    @Test fun originalAlreadyInMyLanguageStays() =
        assertNull(LanguagePlan.audio(listOf(original, frDub), prefs.copy(mine = "en-GB")))

    @Test fun captionsOnlyWhenNoDubAndOriginalIsForeign() {
        val subs = listOf(CaptionTrack("English (auto-generated)", "u", true, "en"))
        assertTrue(LanguagePlan.wantsCaptions(listOf(original, deAuto), chosenAudio = null, subs, prefs))
        assertFalse(LanguagePlan.wantsCaptions(listOf(original, frDub), chosenAudio = "fr.1", subs, prefs))
        assertFalse(LanguagePlan.wantsCaptions(listOf(original), null, subs, prefs.copy(mine = "en")))
        assertFalse(LanguagePlan.wantsCaptions(listOf(original), null, subs, prefs.copy(captionsWhenNoDub = false)))
    }

    @Test fun originalLanguageFallsBackToAutoCaptions() {
        // One unlabelled audio track: the auto-generated captions say what's spoken.
        val plain = AudioChoice("x", "Original", null, original = true)
        assertEquals("ja", LanguagePlan.originalLanguage(listOf(plain), listOf(CaptionTrack("Japanese (auto-generated)", "u", true, "ja"))))
        // Nothing to go on: don't guess, so captions aren't forced on.
        assertNull(LanguagePlan.originalLanguage(listOf(plain), emptyList()))
        assertFalse(LanguagePlan.wantsCaptions(listOf(plain), null, emptyList(), prefs))
        // The title's detected language is the last guess.
        assertTrue(LanguagePlan.wantsCaptions(listOf(plain), null, emptyList(), prefs, titleLanguage = "en"))
        assertFalse(LanguagePlan.wantsCaptions(listOf(plain), null, emptyList(), prefs, titleLanguage = "fr"))
    }

    @Test fun translatedTrackAddedWhenMyLanguageMissing() {
        val tracks = listOf(CaptionTrack("English", "https://x/timedtext?v=1&fmt=ttml", false, "en"), CaptionTrack("English (auto-generated)", "https://x/a", true, "en"))
        val out = Captions.withTranslation(tracks, "fr-FR")
        val added = out.last()
        assertEquals("fr", added.lang)
        assertTrue(added.translated)
        assertEquals("https://x/timedtext?v=1&fmt=ttml&tlang=fr", added.url)
        // Already there: nothing added.
        assertEquals(tracks, Captions.withTranslation(tracks, "en-US"))
    }
}
