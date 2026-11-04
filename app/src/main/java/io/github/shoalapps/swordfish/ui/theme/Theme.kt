@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.shoalapps.swordfish.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.github.shoalapps.swordfish.data.Settings

// Fallback palette: Angelfish's colour, generated with Material's colour utilities.
// Used on Android 11 and below, or with dynamic colour turned off.
private val Light = lightColorScheme(
    primary = Color(0xFFAB3336), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD8), onPrimaryContainer = Color(0xFF8A1A21),
    secondary = Color(0xFF775654), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFED3D0), onSecondaryContainer = Color(0xFF5D3F3E),
    tertiary = Color(0xFF8B7042), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF8B7042), onTertiaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFFFFF8F7), onBackground = Color(0xFF231919),
    surface = Color(0xFFFFF8F7), onSurface = Color(0xFF231919), onSurfaceVariant = Color(0xFF534342),
    surfaceContainerLow = Color(0xFFFFF0EF), surfaceContainer = Color(0xFFFCEAE8),
    surfaceContainerHigh = Color(0xFFF6E4E3), surfaceContainerHighest = Color(0xFFF0DEDD),
    outline = Color(0xFF82706F), outlineVariant = Color(0xFFD7C1C0), inversePrimary = Color(0xFFFFB3AF),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB3AF), onPrimary = Color(0xFF620F14),
    primaryContainer = Color(0xFF8A1A21), onPrimaryContainer = Color(0xFFFFDAD8),
    secondary = Color(0xFFE7BDBA), onSecondary = Color(0xFF442928),
    secondaryContainer = Color(0xFF5D3F3E), onSecondaryContainer = Color(0xFFFFDAD8),
    tertiary = Color(0xFFE3C28C), onTertiary = Color(0xFF412D05),
    tertiaryContainer = Color(0xFFA98C5B), onTertiaryContainer = Color(0xFF000000),
    background = Color(0xFF1A1111), onBackground = Color(0xFFF0DEDD),
    surface = Color(0xFF1A1111), onSurface = Color(0xFFF0DEDD), onSurfaceVariant = Color(0xFFD7C1C0),
    surfaceContainerLow = Color(0xFF231919), surfaceContainer = Color(0xFF271D1D),
    surfaceContainerHigh = Color(0xFF322827), surfaceContainerHighest = Color(0xFF3D3231),
    outline = Color(0xFFA08C8B), outlineVariant = Color(0xFF534342), inversePrimary = Color(0xFFAB3336),
)

@Composable
fun TubeTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val scheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
