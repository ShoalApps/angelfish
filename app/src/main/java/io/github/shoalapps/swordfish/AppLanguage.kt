package io.github.shoalapps.swordfish

import android.content.Context
import androidx.compose.runtime.Composable
import io.github.shoalapps.common.ui.AppLanguage as Shared

/**
 * Angelfish's app language, handled by the shared Shoal library. The catalogues stay in this app's
 * assets (i18n/), and the English phrases stay here so the catalogue extractor still finds them.
 */
object AppLanguage {
    fun init(context: Context) = Shared.init(context)
    fun chosen(context: Context): String = Shared.chosen(context)
    fun wrap(base: Context): Context = Shared.wrap(base)
    fun set(context: Context, tag: String) = Shared.set(context, tag)
    fun translate(text: String): String = Shared.translate(text)
    fun translate(context: Context, text: String, tag: String): String = Shared.translate(context, text, tag)

    @Composable
    fun text(source: String): String = Shared.text(source)

    @Composable
    fun SettingsRow() = Shared.SettingsRow(
        appLanguage = "App language",
        followSystemLanguage = "Follow system language",
        translationNotice = "Some translations are automatic. Untranslated text appears in English.",
        chooseAppLanguage = "Choose app language",
        close = "Close",
    )
}
