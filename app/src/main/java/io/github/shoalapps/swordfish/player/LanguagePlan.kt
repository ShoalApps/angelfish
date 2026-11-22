package io.github.shoalapps.swordfish.player

/** What the viewer wants for a video in another language. [mine] is a BCP 47 tag. */
data class LanguagePrefs(
    val mine: String,
    val preferDubs: Boolean = true,
    val allowAutoDubs: Boolean = true,
    val captionsWhenNoDub: Boolean = true,
)

/**
 * Hearing a video in your language: a dub if there's one (a person's before YouTube's machine
 * one), otherwise the original with captions in your language. A video already in your
 * language is left alone.
 */
object LanguagePlan {
    private fun base(tag: String?) = tag?.substringBefore('-')?.lowercase()?.takeIf { it.isNotBlank() }

    /**
     * The spoken language: the original audio track's, else the auto-generated captions' (speech
     * recognition hears what's said), else the title's as a last guess.
     */
    fun originalLanguage(audio: List<AudioChoice>, captions: List<CaptionTrack>, titleLanguage: String? = null): String? =
        base(audio.firstOrNull { it.original }?.language)
            ?: base(captions.firstOrNull { it.auto && !it.translated }?.lang)
            ?: base(titleLanguage)

    /** The audio track to start on, or null for the default (the original). */
    fun audio(tracks: List<AudioChoice>, prefs: LanguagePrefs): String? {
        val mine = base(prefs.mine) ?: return null
        if (!prefs.preferDubs) return null
        if (base(tracks.firstOrNull { it.original }?.language) == mine) return null
        val inMine = tracks.filter { !it.original && base(it.language) == mine }
        return (inMine.firstOrNull { !it.auto } ?: inMine.firstOrNull { it.auto && prefs.allowAutoDubs })?.id
    }

    /** Captions are worth turning on: no dub playing and the video's spoken in another language. */
    fun wantsCaptions(
        audio: List<AudioChoice>,
        chosenAudio: String?,
        captions: List<CaptionTrack>,
        prefs: LanguagePrefs,
        titleLanguage: String? = null,
    ): Boolean {
        if (!prefs.captionsWhenNoDub) return false
        val mine = base(prefs.mine) ?: return false
        if (chosenAudio != null && base(audio.firstOrNull { it.id == chosenAudio }?.language) == mine) return false
        val spoken = originalLanguage(audio, captions, titleLanguage) ?: return false
        return spoken != mine
    }
}
