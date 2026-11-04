@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.shoalapps.swordfish.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import io.github.shoalapps.swordfish.LocalisedIcon as Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import io.github.shoalapps.swordfish.LocalisedText as Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import io.github.shoalapps.swordfish.ui.components.MorphShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Launch animation: the app's scalloped play shape spins in and morphs while Home,
 * Shorts and your subscriptions load in the background, then zooms away.
 */
@Composable
fun IntroOverlay() {
    var shown by rememberSaveable { mutableStateOf(true) }
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Clover8Leaf) }
    val enter = remember { Animatable(0f) }
    val shape = remember { Animatable(0f) }
    val spin = remember { Animatable(-120f) }
    val spatial = MaterialTheme.motionScheme.slowSpatialSpec<Float>()

    LaunchedEffect(Unit) {
        if (!shown) return@LaunchedEffect
        launch { enter.animateTo(1f, spatial) }
        launch { spin.animateTo(0f, spatial) }
        val pulse = launch {
            while (true) {
                shape.animateTo(1f, tween(700))
                shape.animateTo(0f, tween(700))
            }
        }
        delay(900)
        // Wait for the first feed, but never longer than a few seconds.
        withTimeoutOrNull(3500) { while (!StartupState.homeReady) delay(50) }
        shown = false
        // Stop the morph once the intro has gone, or it keeps drawing frames for the whole session.
        delay(400)
        pulse.cancel()
    }

    AnimatedVisibility(
        visible = shown,
        exit = fadeOut(tween(350)) + scaleOut(tween(350), targetScale = 1.6f),
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(132.dp)
                        .graphicsLayer {
                            scaleX = 0.4f + 0.6f * enter.value
                            scaleY = 0.4f + 0.6f * enter.value
                            alpha = enter.value.coerceIn(0f, 1f)
                            rotationZ = spin.value
                        }
                        .clip(MorphShape(morph, shape.value.coerceIn(0f, 1f)))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
                Text(
                    "Angelfish",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .graphicsLayer {
                            alpha = ((enter.value - 0.4f) / 0.6f).coerceIn(0f, 1f)
                            translationY = (1f - enter.value) * 40f
                        },
                )
            }
        }
    }
}

