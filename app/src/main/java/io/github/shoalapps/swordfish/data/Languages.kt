package io.github.shoalapps.swordfish.data

import io.github.shoalapps.swordfish.AppGraph
import io.github.shoalapps.swordfish.AppLanguage
import io.github.shoalapps.swordfish.player.LanguagePrefs

/** The language videos should reach you in: the one picked for videos, else the app's, else the system's. */
object Languages {
    fun mine(): String = AppGraph.store.settings.value.videoLanguage.ifBlank {
        runCatching { AppLanguage.chosen(AppGraph.app) }.getOrDefault("").ifBlank { java.util.Locale.getDefault().toLanguageTag() }
    }

    fun prefs(): LanguagePrefs = AppGraph.store.settings.value.let {
        LanguagePrefs(mine(), it.preferDubs, it.allowAutoDubs, it.captionsWhenNoDub)
    }

    /** Just the language, "English" not "English (United Kingdom)": a dialect never changes what's offered. */
    fun name(tag: String): String = java.util.Locale.forLanguageTag(tag.substringBefore('-')).let { l ->
        l.getDisplayName(java.util.Locale.getDefault()).replaceFirstChar { it.titlecase() }.ifBlank { tag }
    }

    /** Languages worth offering in a picker: one per language, by name. */
    val choices: List<String> by lazy {
        java.util.Locale.getAvailableLocales().map { it.language }.filter { it.length == 2 }.distinct().sortedBy { name(it).lowercase() }
    }
}
