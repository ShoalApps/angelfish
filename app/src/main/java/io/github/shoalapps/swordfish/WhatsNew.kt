@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.shoalapps.swordfish

import android.content.Context
import android.content.res.Resources
import android.icu.text.DateFormat
import android.icu.util.TimeZone
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.pm.PackageInfoCompat
import androidx.graphics.shapes.Morph
import io.github.shoalapps.common.expressive.MorphShape
import io.github.shoalapps.common.ui.LocalisedText as Text
import io.github.shoalapps.swordfish.ui.components.pressScale
import io.github.shoalapps.swordfish.ui.components.staggeredReveal
import org.json.JSONArray
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date

/** The release workflow packages the same notes it publishes on GitHub. */
object WhatsNew {
    private var requested by mutableStateOf(false)

    fun open() { requested = true }

    @Composable
    fun Host() {
        val context = LocalContext.current
        val prefs = remember(context) { context.getSharedPreferences("whats-new", Context.MODE_PRIVATE) }
        val packageInfo = remember(context) {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        val version = PackageInfoCompat.getLongVersionCode(packageInfo)
        val versionName = packageInfo.versionName ?: version.toString()
        val seenKey = "${packageInfo.versionName} ($version)"
        var pending by rememberSaveable(seenKey) { mutableStateOf(prefs.getString("seen", null) != seenKey) }
        val showing = pending || requested
        val visibility = remember { MutableTransitionState(false) }
        LaunchedEffect(showing) { visibility.targetState = showing }
        if (!showing && !visibility.currentState && visibility.isIdle) return

        val notes = remember(context) { readNotes(context) }
        fun dismiss() {
            prefs.edit().putString("seen", seenKey).apply()
            pending = false
            requested = false
        }

        val motion = MaterialTheme.motionScheme
        Dialog(onDismissRequest = ::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                AnimatedVisibility(
                    visibleState = visibility,
                    enter = fadeIn(tween(220)) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.9f) +
                        slideInVertically(motion.defaultSpatialSpec()) { it / 12 },
                    exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.94f),
                ) {
                    Changelog(versionName, releaseDate(), notes, ::dismiss)
                }
            }
        }
    }

    private fun readNotes(context: Context): List<ReleaseNote> = runCatching {
        val entries = JSONArray(context.assets.open("whats-new.json").bufferedReader().use { it.readText() })
        List(entries.length()) { index ->
            val entry = entries.getJSONObject(index)
            ReleaseNote(entry.getString("title"), entry.getString("body"))
        }
    }.getOrDefault(emptyList())
}

private data class ReleaseNote(val title: String, val body: String)

@Composable
private fun releaseDate(): String {
    // Observe configuration changes while using the device language even if Angelfish has an override.
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        val locale = Resources.getSystem().configuration.locales[0]
        val date = LocalDate.parse(BuildConfig.RELEASE_DATE).atStartOfDay(ZoneOffset.UTC).toInstant()
        DateFormat.getDateInstance(DateFormat.FULL, locale).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date.from(date))
    }
}

@Composable
private fun Changelog(version: String, releaseDate: String, notes: List<ReleaseNote>, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val buttonCorner by animateDpAsState(
        targetValue = if (pressed) 16.dp else 32.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "Done button shape",
    )

    Surface(
        modifier = Modifier.widthIn(max = 500.dp).fillMaxWidth().heightIn(max = 700.dp),
        shape = RoundedCornerShape(36.dp),
        color = colors.surfaceContainer,
    ) {
        Column {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(shape = RoundedCornerShape(28.dp), color = colors.primaryContainer) {
                    Row(
                        Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "What's new",
                                modifier = Modifier.semantics { heading() },
                                style = type.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(version, style = type.labelLarge)
                            Text(releaseDate, style = type.bodySmall)
                        }
                        ReleaseBadge()
                    }
                }

                notes.ifEmpty { listOf(ReleaseNote("Release notes are unavailable for this build.", "")) }
                    .forEachIndexed { index, note ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().staggeredReveal(index + 1),
                            shape = RoundedCornerShape(22.dp),
                            color = colors.surfaceContainerLowest,
                        ) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(note.title, style = type.titleMedium, color = colors.primary, fontWeight = FontWeight.SemiBold)
                                if (note.body.isNotBlank()) {
                                    Text(note.body, style = type.bodyMedium, color = colors.onSurfaceVariant)
                                }
                            }
                        }
                    }
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                    .heightIn(min = 52.dp).pressScale(interaction),
                shape = RoundedCornerShape(buttonCorner),
                interactionSource = interaction,
            ) {
                Text("Done", style = type.labelLarge)
            }
        }
    }
}

@Composable
private fun ReleaseBadge() {
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Clover8Leaf) }
    val progress = remember { Animatable(0f) }
    val spatial = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(Unit) { progress.animateTo(1f, spatial) }

    Box(
        modifier = Modifier.size(64.dp)
            .graphicsLayer {
                val reveal = progress.value
                scaleX = 0.68f + 0.32f * reveal
                scaleY = scaleX
                rotationZ = -48f * (1f - reveal)
            }
            .clip(MorphShape(morph, progress.value.coerceIn(0f, 1f)))
            .background(colors.tertiary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, Modifier.size(29.dp), tint = colors.onTertiary)
    }
}
