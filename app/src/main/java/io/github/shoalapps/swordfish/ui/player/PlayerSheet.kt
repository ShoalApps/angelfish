@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.shoalapps.swordfish.ui.player

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import io.github.shoalapps.common.ui.LocalisedIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import io.github.shoalapps.common.ui.LocalisedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.shoalapps.swordfish.AppGraph
import io.github.shoalapps.swordfish.data.SbCategories
import io.github.shoalapps.swordfish.data.SbSegment
import io.github.shoalapps.swordfish.data.Sub
import io.github.shoalapps.swordfish.data.VideoItem
import io.github.shoalapps.swordfish.data.formatCount
import io.github.shoalapps.swordfish.data.formatDuration
import io.github.shoalapps.swordfish.player.CommentRatings
import io.github.shoalapps.swordfish.player.PlayerViewModel
import io.github.shoalapps.swordfish.ui.components.Avatar
import io.github.shoalapps.swordfish.ui.components.ErrorState
import io.github.shoalapps.swordfish.ui.components.SubscribeButton
import io.github.shoalapps.swordfish.ui.components.VideoCard
import io.github.shoalapps.swordfish.ui.components.rememberBranding
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import coil3.compose.AsyncImage
import io.github.shoalapps.swordfish.data.Comment
import io.github.shoalapps.swordfish.data.fullDate
import io.github.shoalapps.swordfish.data.timeAgo
import io.github.shoalapps.swordfish.data.uploadedText
import io.github.shoalapps.swordfish.player.Rating
import io.github.shoalapps.swordfish.ui.Links
import io.github.shoalapps.swordfish.ui.components.CenterLoading
import io.github.shoalapps.common.expressive.PolygonShape
import io.github.shoalapps.common.expressive.rememberMorphShape
import io.github.shoalapps.common.expressive.rememberSpin
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.foundation.interaction.collectIsPressedAsState
import io.github.shoalapps.swordfish.ui.components.LaunchOrigin
import io.github.shoalapps.swordfish.ui.components.pressScale
import io.github.shoalapps.swordfish.ui.components.shareVideo
import io.github.shoalapps.swordfish.ui.components.staggeredReveal
import io.github.shoalapps.swordfish.ui.components.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

private val MiniHeight = 64.dp
/** Height of Material 3 Expressive's short navigation bar. */
private val NavBarHeight = 64.dp

@Composable
fun PlayerSheet(
    vm: PlayerViewModel,
    bottomBarVisible: Boolean,
    onChannel: (String) -> Unit,
    /** Width of a navigation rail on the left, which the mini player keeps clear of. */
    startInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    ResumeSnackbar(vm)
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val slowSpatial = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Tablets and desktop windows keep the watch page in landscape; only phones jump to full screen.
    val largeScreen = LocalConfiguration.current.smallestScreenWidthDp >= 600
    val wide = io.github.shoalapps.swordfish.ui.components.isWideScreen()

    // Container transform: the tapped thumbnail grows into the player.
    val launch = remember { Animatable(1f) }
    var launchFrom by remember { mutableStateOf<Rect?>(null) }
    // Predictive back: the page shrinks as you swipe back.
    val back = remember { Animatable(0f) }

    LaunchedEffect(vm.sheetNonce) {
        val origin = if (!wide && vm.sheetTarget == 1f && vm.expand.value < 0.5f) LaunchOrigin.take(vm.current?.id) else LaunchOrigin.take(null)
        if (origin != null) {
            launchFrom = origin
            vm.expand.snapTo(1f)
            launch.snapTo(0f)
            launch.animateTo(1f, slowSpatial)
            launchFrom = null
        } else {
            vm.expand.animateTo(vm.sheetTarget, spatial)
        }
    }

    // The spring animation can briefly overshoot 0..1; sizes and paddings must never go negative.
    val p = vm.expand.value.coerceIn(0f, 1f)
    val ambient = AppGraph.store.settings.collectAsState().value.ambientMode &&
        MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val isFullscreen = vm.fullscreen || (landscape && !largeScreen && p > 0.99f)
    FullscreenEffect(isFullscreen, vm)

    PredictiveBackHandler(enabled = p > 0.5f && !isFullscreen) { events ->
        try {
            events.collect { e -> back.snapTo(e.progress) }
            vm.collapse()
            scope.launch { back.animateTo(0f, spatial) }
        } catch (e: CancellationException) {
            scope.launch { back.animateTo(0f, spatial) }
            throw e
        }
    }

    if (isFullscreen) {
        BackHandler { vm.fullscreen = false }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VideoSurface(vm, Modifier.fillMaxSize())
            PlayerControls(vm, fullscreen = true, onCollapse = { vm.fullscreen = false })
        }
        return
    }

    if (wide) {
        WideSheet(vm, bottomBarVisible, onChannel, back.value)
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullH = constraints.maxHeight.toFloat()
        val fullW = constraints.maxWidth.toFloat()
        val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
        val navBottom = WindowInsets.navigationBars.getBottom(density).toFloat()
        val miniH = with(density) { MiniHeight.toPx() }
        val barH = if (bottomBarVisible) with(density) { NavBarHeight.toPx() } + navBottom else navBottom
        val miniTop = fullH - barH - miniH - with(density) { 8.dp.toPx() }
        val travel = miniTop.coerceAtLeast(1f)

        val dragState = rememberDraggableState { delta ->
            scope.launch { vm.expand.snapTo((vm.expand.value - delta / travel).coerceIn(0f, 1f)) }
        }
        val settle: suspend (Float) -> Unit = { velocity ->
            // Swiping up on an open video goes full screen, like the official app.
            if (vm.expand.value >= 0.99f && velocity < -900f) vm.fullscreen = true
            val target = when {
                velocity < -1200f -> 1f
                velocity > 1200f -> 0f
                vm.expand.value > 0.5f -> 1f
                else -> 0f
            }
            vm.expand.animateTo(target, spatial, initialVelocity = -velocity / travel)
        }

        val b = back.value
        val top = lerpF(miniTop, 0f, p)
        val height = lerpF(miniH, fullH, p)
        val sidePad = lerp(8.dp, 0.dp, p)
        val corner = maxOf(lerp(18.dp, 0.dp, p), lerp(0.dp, 32.dp, b))
        val bg = lerp(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surface, p)
        val videoWidthFull = maxWidth
        val videoW = lerp(MiniHeight * 16f / 9f, videoWidthFull, p)
        val videoH = lerp(MiniHeight, videoWidthFull * 9f / 16f, p)
        val statusPad = with(density) { (statusTop * p).toDp() }
        val launching = launchFrom != null
        val l = launch.value

        // Swipe the mini player sideways to close it.
        val dismissX = remember { Animatable(0f) }
        val fullWpx = fullW
        val dismissState = rememberDraggableState { d -> scope.launch { dismissX.snapTo(dismissX.value + d) } }
        LaunchedEffect(vm.current?.id) { dismissX.snapTo(0f) }
        Surface(
            color = bg,
            shape = RoundedCornerShape(corner),
            shadowElevation = lerp(6.dp, 0.dp, p),
            modifier = Modifier
                .offset { IntOffset(dismissX.value.roundToInt(), top.roundToInt()) }
                .graphicsLayer { alpha = 1f - (kotlin.math.abs(dismissX.value) / fullWpx).coerceIn(0f, 0.9f) }
                .draggable(
                    state = dismissState,
                    orientation = Orientation.Horizontal,
                    enabled = p < 0.05f,
                    onDragStopped = { v ->
                        if (kotlin.math.abs(dismissX.value) > fullWpx * 0.35f || kotlin.math.abs(v) > 1500f) {
                            dismissX.animateTo(if (dismissX.value + v * 0.1f > 0) fullWpx else -fullWpx, tween(160))
                            vm.close()
                        } else {
                            dismissX.animateTo(0f, spatial)
                        }
                    },
                )
                .graphicsLayer {
                    val s = 1f - 0.1f * b
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0.5f, 0.35f)
                    alpha = if (launching) l.coerceIn(0f, 1f) else 1f
                }
                .padding(start = lerp(startInset, 0.dp, p))
                .padding(horizontal = sidePad)
                .fillMaxWidth()
                .height(with(density) { height.toDp() }),
        ) {
          Box(Modifier.fillMaxSize()) {
            // Ambient mode: a soft glow of the video's colours behind the player.
            if (ambient && p > 0.05f && android.os.Build.VERSION.SDK_INT >= 31) {
                AsyncImage(
                    model = vm.current?.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(statusPad + videoH + 64.dp)
                        .graphicsLayer {
                            alpha = 0.32f * p
                            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.verticalGradient(0.55f to Color.Black, 1f to Color.Transparent),
                                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                            )
                        }
                        .blur(70.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded),
                )
            }
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(statusPad))
                Box {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(videoH)
                            .draggable(
                                state = dragState,
                                orientation = Orientation.Vertical,
                                onDragStopped = { v -> settle(v) },
                            )
                            .clickable(enabled = p < 0.05f) { vm.showSheet(1f) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .width(videoW)
                                .height(videoH)
                                .clip(RoundedCornerShape(lerp(12.dp, 0.dp, p)))
                                .background(Color.Black),
                        ) {
                            VideoSurface(vm, Modifier.fillMaxSize())
                            if (p > 0.6f) {
                                PlayerControls(
                                    vm,
                                    fullscreen = false,
                                    onCollapse = { vm.collapse() },
                                    modifier = Modifier.alpha(((p - 0.6f) / 0.4f).coerceIn(0f, 1f)),
                                )
                            }
                        }
                        if (p < 0.4f) MiniInfo(vm, Modifier.alpha(1f - p / 0.4f).weight(1f))
                    }
                    // Thin progress line on the mini player
                    if (p < 0.2f && vm.durationMs > 0) {
                        val frac = (vm.positionMs.toFloat() / vm.durationMs).coerceIn(0f, 1f)
                        val primary = MaterialTheme.colorScheme.primary
                        Canvas(Modifier.fillMaxWidth().height(2.dp).align(Alignment.BottomStart).alpha(1f - p / 0.2f)) {
                            drawRect(primary, size = Size(size.width * frac, size.height))
                        }
                    }
                }
                if (p > 0.02f) {
                    WatchDetails(
                        vm,
                        onChannel,
                        Modifier
                            .weight(1f)
                            .alpha(p)
                            .graphicsLayer {
                                translationY = if (launching) (1f - l) * 64.dp.toPx() else 0f
                            },
                    )
                }
            }
          }
        }

        // The thumbnail flying from the feed into the player.
        val from = launchFrom
        if (from != null) {
            val toTop = statusTop
            val toH = fullW * 9f / 16f
            val x = lerpF(from.left, 0f, l)
            val y = lerpF(from.top, toTop, l)
            val w = lerpF(from.width, fullW, l)
            val h = lerpF(from.height, toH, l)
            AsyncImage(
                model = vm.current?.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                    .clip(RoundedCornerShape(lerp(12.dp, 0.dp, l.coerceIn(0f, 1f))))
                    .background(Color.Black),
            )
        }
    }
}

/**
 * Tablet and desktop layout: a floating video card in the corner that opens into a two-pane
 * watch page, video and details on the left and related videos down the right.
 */
@Composable
private fun WideSheet(vm: PlayerViewModel, bottomBarVisible: Boolean, onChannel: (String) -> Unit, back: Float) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val p = vm.expand.value.coerceIn(0f, 1f)
        val fullW = constraints.maxWidth.toFloat()
        val fullH = constraints.maxHeight.toFloat()
        val margin = with(density) { 16.dp.toPx() }
        val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
        val navBottom = WindowInsets.navigationBars.getBottom(density).toFloat()
        val barH = if (bottomBarVisible) with(density) { NavBarHeight.toPx() } + navBottom else navBottom
        val cardW = with(density) { 400.dp.toPx() }.coerceAtMost(fullW * 0.45f)
        val cardH = cardW * 9f / 16f
        val miniLeft = fullW - cardW - margin
        val miniTop = fullH - barH - cardH - margin
        val travel = miniTop.coerceAtLeast(1f)
        val dragState = rememberDraggableState { delta ->
            scope.launch { vm.expand.snapTo((vm.expand.value - delta / travel).coerceIn(0f, 1f)) }
        }
        // The video column is the whole card when small, about two thirds of the window when open.
        val pad = lerp(0.dp, 16.dp, p)
        val padPx = with(density) { pad.toPx() }
        val paneW = lerpF(cardW, fullW * 0.66f, p)
        val videoH = (paneW - 2 * padPx) * 9f / 16f

        Surface(
            color = lerp(Color.Black, MaterialTheme.colorScheme.surface, p),
            shape = RoundedCornerShape(maxOf(lerp(16.dp, 0.dp, p), lerp(0.dp, 32.dp, back))),
            shadowElevation = lerp(8.dp, 0.dp, p),
            modifier = Modifier
                .offset { IntOffset(lerpF(miniLeft, 0f, p).roundToInt(), lerpF(miniTop, 0f, p).roundToInt()) }
                .graphicsLayer {
                    val s = 1f - 0.06f * back
                    scaleX = s
                    scaleY = s
                }
                .size(with(density) { lerpF(cardW, fullW, p).toDp() }, with(density) { lerpF(cardH, fullH, p).toDp() }),
        ) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(with(density) { paneW.toDp() }).fillMaxHeight()) {
                    Spacer(Modifier.height(with(density) { (statusTop * p).toDp() } + pad))
                    Box(
                        Modifier
                            .padding(horizontal = pad)
                            .fillMaxWidth()
                            .height(with(density) { videoH.toDp() })
                            .clip(RoundedCornerShape(lerp(0.dp, 16.dp, p)))
                            .background(Color.Black)
                            .draggable(dragState, Orientation.Vertical, onDragStopped = { v ->
                                val target = when {
                                    v < -1200f -> 1f
                                    v > 1200f -> 0f
                                    vm.expand.value > 0.5f -> 1f
                                    else -> 0f
                                }
                                vm.expand.animateTo(target, spatial, initialVelocity = -v / travel)
                            })
                            .clickable(enabled = p < 0.05f) { vm.showSheet(1f) },
                    ) {
                        VideoSurface(vm, Modifier.fillMaxSize())
                        if (p > 0.6f) {
                            PlayerControls(vm, fullscreen = false, onCollapse = { vm.collapse() }, modifier = Modifier.alpha(((p - 0.6f) / 0.4f).coerceIn(0f, 1f)))
                        }
                        if (p < 0.05f) MiniCardOverlay(vm)
                    }
                    if (p > 0.02f) WatchDetails(vm, onChannel, Modifier.weight(1f).alpha(p), includeRelated = false)
                }
                if (p > 0.02f) {
                    RelatedPane(
                        vm,
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .alpha(p)
                            .padding(top = with(density) { (statusTop * p).toDp() } + pad),
                    )
                }
            }
        }
    }
}

/** Play/pause and close on the floating card, plus a thin progress line. */
@Composable
private fun MiniCardOverlay(vm: PlayerViewModel) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f))) {
        IconButton(onClick = vm::close, modifier = Modifier.align(Alignment.TopEnd)) {
            Icon(Icons.Rounded.Close, "Close", tint = Color.White)
        }
        FilledTonalIconButton(
            onClick = vm::togglePlay,
            shape = rememberMorphShape(MaterialShapes.Circle, MaterialShapes.Cookie6Sided, if (vm.isPlaying) 1f else 0f, 60f),
            modifier = Modifier.align(Alignment.Center).size(52.dp),
        ) {
            Icon(if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (vm.isPlaying) "Pause" else "Play")
        }
        if (vm.durationMs > 0) {
            val frac = (vm.positionMs.toFloat() / vm.durationMs).coerceIn(0f, 1f)
            val primary = MaterialTheme.colorScheme.primary
            Canvas(Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomStart)) {
                drawRect(primary, size = Size(size.width * frac, size.height))
            }
        }
    }
}

/** Related videos as compact rows, for the right-hand column of the wide watch page. */
@Composable
private fun RelatedPane(vm: PlayerViewModel, modifier: Modifier) {
    val settings by AppGraph.store.settings.collectAsState()
    if (settings.hideRelated) return
    val navPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(modifier, contentPadding = PaddingValues(end = 8.dp, bottom = 24.dp + navPad)) {
        itemsIndexed(vm.related, key = { _, v -> "r" + v.id }) { i, v ->
            io.github.shoalapps.swordfish.ui.components.VideoRow(v, { vm.open(v) }, Modifier.animateItem().staggeredReveal(i))
        }
    }
}

@Composable
private fun MiniInfo(vm: PlayerViewModel, modifier: Modifier) {
    val item = vm.current
    Row(modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                item?.title.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item?.channelName.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FilledTonalIconButton(
            onClick = vm::togglePlay,
            shape = rememberMorphShape(MaterialShapes.Circle, MaterialShapes.Cookie6Sided, if (vm.isPlaying) 1f else 0f, 60f),
        ) {
            AnimatedContent(vm.isPlaying, transitionSpec = { scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut() }, label = "mini") {
                Icon(if (it) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (it) "Pause" else "Play")
            }
        }
        IconButton(onClick = vm::close) { Icon(Icons.Rounded.Close, "Close") }
    }
}

/** Draws video onto a TextureView so it animates smoothly with the sheet. */
@Composable
fun VideoSurface(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black).testTag("video-surface"), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx -> TextureView(ctx).also { vm.player.setVideoTextureView(it) } },
            onRelease = { vm.player.clearVideoTextureView(it) },
            modifier = Modifier.fillMaxSize().wrapContentSize().aspectRatio(vm.aspect),
        )
        // Show the thumbnail until the first video frame arrives, like the official app.
        androidx.compose.animation.AnimatedVisibility(!vm.firstFrame, enter = fadeIn(), exit = fadeOut()) {
            AsyncImage(
                model = vm.current?.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Errors sit on the video, where you're looking, with what you can do about them.
        vm.error?.let { message ->
            val context = LocalContext.current
            Column(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(message, color = Color.White, style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = vm::retry) { Text("Try again") }
                    if (vm.onlyYouTubeCanPlay) {
                        androidx.compose.material3.Button(onClick = {
                            val id = vm.current?.id ?: return@Button
                            vm.close()
                            runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=$id"))) }
                        }) { Text("Open in YouTube") }
                    }
                }
            }
        }
        // Captions, YouTube style: white text on a dark box near the bottom.
        val caption = vm.captionText
        if (caption != null && vm.captionTrack != null) {
            Text(
                caption,
                color = Color.White,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 24.dp, end = 24.dp, bottom = 36.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun FullscreenEffect(fullscreen: Boolean, vm: PlayerViewModel) {
    val view = LocalView.current
    val activity = LocalContext.current as? Activity ?: return
    LaunchedEffect(vm.fullscreen) {
        if (vm.fullscreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else if (activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            delay(2500)
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    DisposableEffect(fullscreen) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        if (fullscreen) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { }
    }
}

// ---------------- Controls ----------------

@Composable
fun PlayerControls(
    vm: PlayerViewModel,
    fullscreen: Boolean,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Nothing to control while the video can't play; keep only the way out, clear of the error's buttons.
    if (vm.error != null) {
        Box(modifier.fillMaxSize()) {
            IconButton(onClick = onCollapse, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
                Icon(if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.KeyboardArrowDown, "Minimise", tint = Color.White)
            }
        }
        return
    }
    var visible by remember { mutableStateOf(true) }
    var poke by remember { mutableIntStateOf(0) }
    var seekHint by remember { mutableStateOf<String?>(null) }
    var showOptions by remember { mutableStateOf(false) }
    var scrubMs by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(visible, vm.isPlaying, poke) {
        if (visible && vm.isPlaying) {
            delay(3000)
            visible = false
        }
    }
    LaunchedEffect(seekHint) {
        if (seekHint != null) { delay(700); seekHint = null }
    }
    val settings by AppGraph.store.settings.collectAsState()
    val seekSec = settings.doubleTapSeconds
    val context = LocalContext.current
    val activity = context as? Activity
    val audio = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    var swipeHint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(swipeHint) { if (swipeHint != null) { delay(900); swipeHint = null } }
    // Give the screen its normal brightness back when leaving full screen.
    DisposableEffect(fullscreen) {
        onDispose {
            activity?.window?.let { w ->
                val lp = w.attributes
                lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                w.attributes = lp
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .pointerInputTaps(
                onTap = { visible = !visible; poke++ },
                onDoubleTap = { left ->
                    vm.seekBy(if (left) -seekSec * 1000L else seekSec * 1000L)
                    seekHint = if (left) "« ${seekSec}s" else "${seekSec}s »"
                },
            )
            .then(
                if (fullscreen && settings.swipeGestures) Modifier.pointerInput(Unit) {
                    var left = true
                    detectVerticalDragGestures(
                        onDragStart = { left = it.x < size.width / 2f },
                    ) { change, dy ->
                        change.consume()
                        val delta = -dy / size.height
                        if (left && activity != null) {
                            val lp = activity.window.attributes
                            val cur = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                            lp.screenBrightness = (cur + delta).coerceIn(0.01f, 1f)
                            activity.window.attributes = lp
                            swipeHint = "Brightness ${(lp.screenBrightness * 100).toInt()}%"
                        } else {
                            val max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                            val acc = (audioAcc + delta * max * 1.5f)
                            val steps = acc.toInt()
                            audioAcc = acc - steps
                            if (steps != 0) {
                                val now = (audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) + steps).coerceIn(0, max)
                                audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, now, 0)
                            }
                            val v = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                            swipeHint = "Volume ${v * 100 / max.coerceAtLeast(1)}%"
                        }
                    }
                } else Modifier,
            ),
    ) {
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f))) {
                // Top row
                Row(
                    Modifier.fillMaxWidth().align(Alignment.TopStart).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onCollapse) {
                        Icon(
                            if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.KeyboardArrowDown,
                            "Minimise",
                            tint = Color.White,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { showOptions = true; poke++ }) {
                        Icon(Icons.Rounded.Tune, "Quality and speed", tint = Color.White)
                    }
                }

                // Centre row
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { vm.seekBy(-10_000); poke++ }) {
                        Icon(Icons.Rounded.Replay10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    PlayPauseButton(vm.isPlaying, vm.buffering) { vm.togglePlay(); poke++ }
                    IconButton(onClick = { vm.seekBy(10_000); poke++ }) {
                        Icon(Icons.Rounded.Forward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }

                // Bottom row
                Column(Modifier.fillMaxWidth().align(Alignment.BottomStart).padding(horizontal = 12.dp)) {
                    // While dragging, the time and chapter follow your finger, before you let go.
                    val shownMs = scrubMs ?: vm.positionMs
                    val shownChapter = if (scrubMs != null) vm.chapters.lastOrNull { it.startMs <= scrubMs!! } else vm.chapters.getOrNull(vm.chapterIndex)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${formatDuration(shownMs / 1000)} / ${formatDuration(vm.durationMs / 1000)}" +
                                (shownChapter?.title?.takeIf { it.isNotBlank() }?.let { "  •  $it" } ?: ""),
                            color = Color.White,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            vm.selectCaptions(if (vm.captionTrack == null) vm.captionTracks.firstOrNull() else null)
                            poke++
                        }, enabled = vm.captionTracks.isNotEmpty()) {
                            Icon(
                                if (vm.captionTrack != null) Icons.Rounded.ClosedCaption else Icons.Outlined.ClosedCaption,
                                "Captions",
                                tint = if (vm.captionTracks.isEmpty()) Color.White.copy(alpha = 0.4f) else Color.White,
                            )
                        }
                        IconButton(onClick = { vm.fullscreen = !fullscreen; poke++ }) {
                            Icon(
                                if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                "Fullscreen",
                                tint = Color.White,
                            )
                        }
                    }
                    SeekBar(
                        positionMs = vm.positionMs,
                        durationMs = vm.durationMs,
                        bufferedMs = vm.bufferedMs,
                        segments = vm.segments,
                        chapters = vm.chapters,
                        onSeek = { vm.seekTo(it); poke++ },
                        onDragging = { poke++ },
                        onScrub = { scrubMs = it },
                    )
                }
            }
        }

        if (vm.buffering && !visible) {
            Box(
                Modifier.align(Alignment.Center).size(64.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator(Modifier.size(48.dp), color = Color.White)
            }
        }

        // Swipe (brightness / volume) hint
        AnimatedVisibility(
            swipeHint != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp),
        ) {
            Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.6f)) {
                Text(swipeHint.orEmpty(), color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }

        // Double-tap hint
        AnimatedVisibility(
            seekHint != null,
            enter = fadeIn() + scaleIn(initialScale = 0.7f),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.6f)) {
                Text(seekHint.orEmpty(), color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }

        // SponsorBlock "Skipped" pill
        AnimatedVisibility(
            vm.skipped != null,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 48.dp),
        ) {
            val seg = vm.skipped
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Skipped ${seg?.let { SbCategories.of(it.category)?.label?.lowercase() } ?: "segment"}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    TextButton(onClick = vm::undoSkip) { Text("Undo") }
                }
            }
        }
    }

    if (showOptions) PlayerOptionsSheet(vm) { showOptions = false }
}

/** Leftover fraction of a volume step between drag events. */
private var audioAcc = 0f

private fun Modifier.pointerInputTaps(onTap: () -> Unit, onDoubleTap: (left: Boolean) -> Unit): Modifier =
    this.then(
        Modifier.pointerInput(Unit) {
            detectTapGestures(
                onTap = { onTap() },
                onDoubleTap = { offset -> onDoubleTap(offset.x < size.width / 2f) },
            )
        },
    )

/** Big play button whose shape morphs between circle (paused) and rounded square (playing). */
@Composable
private fun PlayPauseButton(playing: Boolean, buffering: Boolean, onClick: () -> Unit) {
    val spin = if (buffering) rememberSpin(3_000) else 0f
    val shape = rememberMorphShape(
        from = MaterialShapes.Cookie9Sided,
        to = MaterialShapes.Square,
        target = if (playing) 1f else 0f,
        rotateBy = 90f,
    )
    val source = remember { MutableInteractionSource() }
    FilledIconButton(
        onClick = onClick,
        interactionSource = source,
        shape = shape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.size(76.dp).pressScale(source, 0.88f).graphicsLayer { rotationZ = spin },
    ) {
        if (buffering) {
            LoadingIndicator(Modifier.size(40.dp).graphicsLayer { rotationZ = -spin })
        } else {
            AnimatedContent(
                playing,
                transitionSpec = { (scaleIn(initialScale = 0.5f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.5f) + fadeOut()) },
                label = "playIcon",
            ) {
                Icon(if (it) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (it) "Pause" else "Play", Modifier.size(36.dp))
            }
        }
    }
}

@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    segments: List<SbSegment>,
    chapters: List<io.github.shoalapps.swordfish.player.Chapter>,
    onSeek: (Long) -> Unit,
    onDragging: () -> Unit,
    onScrub: (Long?) -> Unit = {},
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(drag, durationMs) { onScrub(drag?.let { (it * durationMs).toLong() }) }
    val frac = drag ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val bufFrac = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val thumb by animateDpAsState(if (drag != null) 9.dp else 6.dp, label = "thumb")
    val track by animateDpAsState(if (drag != null) 6.dp else 4.dp, label = "track")
    val primary = MaterialTheme.colorScheme.primary

    BoxWithConstraints(Modifier.fillMaxWidth()) {
    // The chapter you'd land on, above your finger, like the official player.
    val dragFrac = drag
    val dragChapter = dragFrac?.let { f -> chapters.lastOrNull { it.startMs <= f * durationMs } }
    if (dragFrac != null && dragChapter != null && dragChapter.title.isNotBlank()) {
        val bubbleWidth = 240.dp
        val x = (maxWidth * dragFrac - bubbleWidth / 2).coerceIn(0.dp, (maxWidth - bubbleWidth).coerceAtLeast(0.dp))
        Text(
            dragChapter.title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .offset(x = x, y = (-30).dp)
                .width(bubbleWidth)
                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = (it.x / size.width).coerceIn(0f, 1f); onDragging() },
                    onDragEnd = { drag?.let { onSeek((it * durationMs).toLong()) }; drag = null },
                    onDragCancel = { drag = null },
                ) { change, _ ->
                    drag = (change.position.x / size.width).coerceIn(0f, 1f)
                    onDragging()
                }
            }
            .pointerInput(durationMs) {
                detectTapGestures { onSeek(((it.x / size.width).coerceIn(0f, 1f) * durationMs).toLong()) }
            },
    ) {
        val h = track.toPx()
        val y = size.height / 2f - h / 2f
        val r = CornerRadius(h / 2f)
        drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(0f, y), Size(size.width, h), r)
        drawRoundRect(Color.White.copy(alpha = 0.45f), Offset(0f, y), Size(size.width * bufFrac, h), r)
        if (durationMs > 0) {
            val durSec = durationMs / 1000f
            segments.forEach { s ->
                val color = SbCategories.of(s.category)?.color ?: Color.Green
                val x0 = (s.startSec.toFloat() / durSec).coerceIn(0f, 1f) * size.width
                val x1 = (s.endSec.toFloat() / durSec).coerceIn(0f, 1f) * size.width
                drawRect(color, Offset(x0, y), Size((x1 - x0).coerceAtLeast(2f), h))
            }
        }
        drawRoundRect(primary, Offset(0f, y), Size(size.width * frac, h), r)
        // Little gaps where chapters start, like the official seek bar.
        if (durationMs > 0) {
            chapters.drop(1).forEach { c ->
                val x = c.startMs.toFloat() / durationMs * size.width
                drawRect(Color.Black.copy(alpha = 0.6f), Offset(x - 1.5f, y), Size(3f, h))
            }
        }
        drawCircle(primary, thumb.toPx(), Offset(size.width * frac, size.height / 2f))
    }
    }
}

/** YouTube's player settings: a list that slides into each option. */
@Composable
private fun PlayerOptionsSheet(vm: PlayerViewModel, onDismiss: () -> Unit) {
    val settings by AppGraph.store.settings.collectAsState()
    var page by remember { mutableStateOf("main") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState != "main"
                (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally { if (forward) -it / 3 else it / 3 } + fadeOut())
            },
            label = "options",
        ) { current ->
            Column(Modifier.padding(bottom = 24.dp)) {
                when (current) {
                    "main" -> {
                        OptionRow(Icons.Rounded.Tune, "Quality", "${vm.quality}p") { page = "quality" }
                        OptionRow(Icons.Rounded.Speed, "Playback speed", if (vm.speed == 1f) "Normal" else "${vm.speed}x") { page = "speed" }
                        OptionRow(Icons.Rounded.Tune, "Audio track",
                            vm.audioTracks.firstOrNull { it.id == vm.audioTrackId }?.label ?: "Unavailable") {
                            if (vm.audioTracks.isNotEmpty()) page = "audio"
                        }
                        OptionRow(
                            Icons.Rounded.ClosedCaption,
                            "Captions",
                            vm.captionTrack?.label ?: if (vm.captionTracks.isEmpty()) "Unavailable" else "Off",
                        ) { if (vm.captionTracks.isNotEmpty()) page = "captions" }
                        OptionRow(
                            Icons.Rounded.Bedtime,
                            "Sleep timer",
                            when (vm.sleepMinutes) { 0 -> "Off"; -1 -> "End of video"; else -> "${vm.sleepMinutes} minutes" },
                        ) { page = "sleep" }
                        SwitchRow(Icons.Rounded.Repeat, "Loop video", settings.loopVideo) { vm.toggleLoop() }
                        SwitchRow(Icons.Rounded.SlowMotionVideo, "Autoplay", settings.autoplayNext) { vm.toggleAutoplay() }
                    }
                    "quality" -> {
                        SheetTitle("Quality for current video") { page = "main" }
                        vm.qualities.ifEmpty { listOf(vm.quality) }.forEach { q ->
                            PickRow("${q}p" + if (q >= 2160) " 4K" else if (q >= 1440) " HD" else if (q >= 720) " HD" else "", q == vm.quality) {
                                vm.changeQuality(q); onDismiss()
                            }
                        }
                    }
                    "audio" -> {
                        SheetTitle("Audio track") { page = "main" }
                        vm.audioTracks.forEach { track ->
                            PickRow(track.label, track.id == vm.audioTrackId) { vm.changeAudioTrack(track.id); onDismiss() }
                        }
                    }
                    "speed" -> {
                        SheetTitle("Playback speed") { page = "main" }
                        listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f).forEach { sp ->
                            PickRow(if (sp == 1f) "Normal" else "${sp}x", abs(sp - vm.speed) < 0.01f) { vm.changeSpeed(sp); onDismiss() }
                        }
                    }
                    "captions" -> {
                        SheetTitle("Captions") { page = "main" }
                        PickRow("Off", vm.captionTrack == null) { vm.selectCaptions(null); onDismiss() }
                        vm.captionTracks.forEach { t ->
                            PickRow(t.label, vm.captionTrack == t) { vm.selectCaptions(t); onDismiss() }
                        }
                    }
                    "sleep" -> {
                        SheetTitle("Sleep timer") { page = "main" }
                        io.github.shoalapps.swordfish.player.sleepChoices.forEach { m ->
                            val label = when (m) { 0 -> "Off"; -1 -> "End of video"; else -> "$m minutes" }
                            PickRow(label, vm.sleepMinutes == m) { vm.setSleepTimer(m); onDismiss() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    androidx.compose.material3.ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, null) },
        trailingContent = {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        },
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, checked: Boolean, onToggle: () -> Unit) {
    androidx.compose.material3.ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { androidx.compose.material3.Switch(checked = checked, onCheckedChange = { onToggle() }) },
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onToggle),
    )
}

@Composable
private fun SheetTitle(title: String, onBack: () -> Unit) {
    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun PickRow(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.ListItem(
        headlineContent = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
        leadingContent = {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
            }
        },
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

// ---------------- Watch page details (laid out like the official app) ----------------

@Composable
private fun rememberHtml(html: String?, onLink: (String) -> Unit): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    return remember(html, linkColor) {
        if (html.isNullOrBlank()) return@remember AnnotatedString("")
        val prepared = if (html.contains("<br", ignoreCase = true)) html else html.replace("\n", "<br>")
        runCatching {
            AnnotatedString.fromHtml(
                prepared,
                linkStyles = TextLinkStyles(SpanStyle(color = linkColor)),
                linkInteractionListener = { link -> (link as? LinkAnnotation.Url)?.url?.let(onLink) },
            )
        }.getOrElse { AnnotatedString(html) }
    }
}

@Composable
private fun WatchDetails(vm: PlayerViewModel, onChannel: (String) -> Unit, modifier: Modifier, includeRelated: Boolean = true) {
    val item = vm.current
    val info = vm.info
    val subs by AppGraph.store.subs.collectAsState()
    val context = LocalContext.current
    var showDescription by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showPlaylist by remember { mutableStateOf(false) }
    if (showPlaylist && item != null) io.github.shoalapps.swordfish.ui.components.AddToPlaylistDialog(item) { showPlaylist = false }
    val onLink: (String) -> Unit = { url ->
        Links.route(
            context = context,
            url = url,
            currentVideoId = info?.id ?: item?.id,
            seekToMs = { ms ->
                vm.seekTo(ms)
                showDescription = false
                showComments = false
            },
            openVideo = { u, start ->
                showDescription = false
                showComments = false
                vm.openUrl(u, start)
            },
            openChannel = { url2 ->
                showDescription = false
                showComments = false
                onChannel(url2)
            },
        )
    }

    val settings by AppGraph.store.settings.collectAsState()
    // Comments and the description open in place of the details, under the video, so it stays in view.
    if (showQueue) {
        BackHandler { showQueue = false }
        QueuePanel(vm, modifier) { showQueue = false }
        return
    }
    if (showComments) {
        BackHandler { showComments = false }
        CommentsPanel(vm, onLink, modifier) { showComments = false }
        return
    }
    if (showDescription && item != null) {
        BackHandler { showDescription = false }
        DescriptionPanel(vm, onLink, modifier) { showDescription = false }
        return
    }
    val navPad = if (includeRelated) 0.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(modifier.fillMaxWidth(), state = io.github.shoalapps.swordfish.ui.components.rememberCachedListState(), contentPadding = PaddingValues(bottom = 24.dp + navPad)) {
        if (item != null) {
            item(key = "head") {
                val branding = rememberBranding(item)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showDescription = true }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    Text(
                        branding?.title ?: item.title,
                        style = MaterialTheme.typography.titleLargeEmphasized.copy(fontSize = 18.sp, lineHeight = 24.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = listOfNotNull(
                        info?.viewCount?.takeIf { it >= 0 }?.let { "${formatCount(it)} views" } ?: item.viewsText,
                        info?.let { i -> runCatching { i.uploadDate?.offsetDateTime()?.toEpochSecond() }.getOrNull()?.let(::timeAgo) }
                            ?: item.uploadedText(),
                    ).joinToString("  ")
                    Text(
                        "$meta  ...more".trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            item(key = "channel") {
                val channelUrl = info?.uploaderUrl ?: item.channelUrl
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = channelUrl != null) { channelUrl?.let(onChannel) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(item.channelAvatar ?: info?.uploaderAvatars?.firstOrNull()?.url ?: io.github.shoalapps.swordfish.ui.components.rememberChannelAvatar(channelUrl), 36.dp)
                    // Name and count share the space left of the button; a long name ellipsizes first.
                    Row(Modifier.weight(1f).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.channelName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 12.dp).weight(1f, fill = false),
                        )
                        val subCount = info?.uploaderSubscriberCount ?: -1L
                        if (subCount >= 0) {
                            Text(
                                formatCount(subCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    if (channelUrl != null) {
                        io.github.shoalapps.swordfish.ui.components.ChannelSubscribe(Sub(channelUrl, item.channelName, item.channelAvatar))
                    }
                }
            }
            item(key = "actions") {
              CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item { LikePill(vm) }
                    item { io.github.shoalapps.swordfish.ui.components.DownloadButton(item) }
                    item { ActionChip(Icons.Rounded.Share, "Share") { shareVideo(context, io.github.shoalapps.swordfish.data.FeedFilter.shareUrl(item.id, item.url)) } }
                    if (AppGraph.store.signedIn) {
                        item {
                            ActionChip(Icons.AutoMirrored.Rounded.PlaylistAdd, "Save") {
                                AppGraph.saveToWatchLater(context, item.id)
                            }
                        }
                    }
                    item { io.github.shoalapps.swordfish.ui.components.SaveAudioButton(item) }
                    item { ActionChip(Icons.Rounded.PlayArrow, "Play next") { AppGraph.store.playNext(item); toast(context, "This video is already playing") } }
                    item { ActionChip(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to queue") { AppGraph.store.addToQueue(item); toast(context, "This video is already playing") } }
                    item { ActionChip(Icons.AutoMirrored.Rounded.PlaylistAdd, "Queue") { showQueue = true } }
                    item { ActionChip(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist") { showPlaylist = true } }
                }
              }
            }
            if (!settings.hideComments) item(key = "comments") {
                CommentsTeaser(vm) { showComments = true }
            }
        }
        when {
            // Errors show on the video itself (VideoSurface).
            vm.error != null -> Unit
            vm.loading -> item(key = "load") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(56.dp))
                }
            }
        }
        if (includeRelated && !settings.hideRelated) itemsIndexed(vm.related, key = { _, v -> "r" + v.id }) { i, v ->
            VideoCard(
                v,
                onClick = { vm.open(v) },
                onChannel = onChannel,
                modifier = Modifier.animateItem().staggeredReveal(i),
            )
        }
    }

}

@Composable
private fun QueuePanel(vm: PlayerViewModel, modifier: Modifier, onDismiss: () -> Unit) {
    val queue by AppGraph.store.queue.collectAsState()
    Column(modifier.fillMaxWidth()) {
        PanelHeader("Queue", onDismiss)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Coming up (${queue.size})", modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.next() }, enabled = queue.isNotEmpty()) { Text("Next") }
            TextButton(onClick = { AppGraph.store.clearQueue() }, enabled = queue.isNotEmpty()) { Text("Clear") }
        }
        vm.queueMessage?.let { Text(it, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall) }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (queue.isEmpty()) item { Text("Your queue is empty", modifier = Modifier.padding(16.dp)) }
            itemsIndexed(queue, key = { _, video -> video.id }) { index, video ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Column(Modifier.fillMaxWidth().clickable { vm.jumpToQueue(video) }.padding(vertical = 8.dp)) {
                        Text(video.title.ifBlank { video.id }, localise = false, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(video.channelName, localise = false, style = MaterialTheme.typography.bodySmall)
                    }
                    Row {
                        IconButton(onClick = { AppGraph.store.moveInQueue(video.id, -1) }, enabled = index > 0) {
                            Icon(Icons.Rounded.KeyboardArrowUp, "Move up")
                        }
                        IconButton(onClick = { AppGraph.store.moveInQueue(video.id, 1) }, enabled = index < queue.lastIndex) {
                            Icon(Icons.Rounded.KeyboardArrowDown, "Move down")
                        }
                        TextButton(onClick = { AppGraph.store.removeFromQueue(video.id) }) { Text("Remove") }
                    }
                }
            }
        }
    }
}

/** Like and dislike as a Material 3 Expressive connected toggle-button group. */
@Composable
private fun LikePill(vm: PlayerViewModel) {
    val context = LocalContext.current
    val likes = vm.info?.likeCount?.takeIf { it >= 0 } ?: vm.votes?.likes
    val dislikes = vm.votes?.dislikes
    Row(horizontalArrangement = Arrangement.spacedBy(androidx.compose.material3.ButtonGroupDefaults.ConnectedSpaceBetween)) {
        RateToggle(
            checked = vm.rating == Rating.LIKE,
            icon = if (vm.rating == Rating.LIKE) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
            label = likes?.let(::formatCount).orEmpty(),
            description = "Like",
            shapes = androidx.compose.material3.ButtonGroupDefaults.connectedLeadingButtonShapes(),
        ) { if (!vm.rate(Rating.LIKE)) toast(context, "Sign in to save likes to YouTube") }
        RateToggle(
            checked = vm.rating == Rating.DISLIKE,
            icon = if (vm.rating == Rating.DISLIKE) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown,
            label = dislikes?.let(::formatCount).orEmpty(),
            description = "Dislike",
            shapes = androidx.compose.material3.ButtonGroupDefaults.connectedTrailingButtonShapes(),
        ) { if (!vm.rate(Rating.DISLIKE)) toast(context, "Sign in to save dislikes to YouTube") }
    }
}

/** One half of the like/dislike group: the thumb pops when you tap it. */
@Composable
private fun RateToggle(
    checked: Boolean,
    icon: ImageVector,
    label: String,
    description: String,
    shapes: androidx.compose.material3.ToggleButtonShapes,
    onClick: () -> Unit,
) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(checked) {
        if (checked) {
            pop.animateTo(1.35f, tween(110))
            pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
        }
    }
    androidx.compose.material3.ToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        shapes = shapes,
        colors = androidx.compose.material3.ToggleButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 14.dp),
        modifier = Modifier.height(36.dp),
    ) {
        Icon(icon, description, Modifier.size(20.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value })
        AnimatedContent(label, label = "count") { text ->
            if (text.isNotEmpty()) {
                Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun ActionChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val corner by animateDpAsState(if (pressed) 10.dp else 20.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "chip")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        interactionSource = source,
        modifier = Modifier.height(36.dp).pressScale(source, 0.94f),
    ) {
        Row(Modifier.fillMaxHeight().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun CommentsTeaser(vm: PlayerViewModel, onOpen: () -> Unit) {
    val first = vm.comments.firstOrNull()
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(12.dp).animateContentSize()) {
            Text("Comments", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            when {
                first != null -> Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(first.avatar, 24.dp)
                    val plain = remember(first.html) {
                        runCatching { AnnotatedString.fromHtml(first.html).text }.getOrDefault(first.html)
                    }
                    Text(
                        plain,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
                vm.commentsLoading -> Text(
                    "Loading comments…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                vm.commentsError != null -> Text(
                    vm.commentsError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                else -> Text(
                    "No comments available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun DescriptionPanel(vm: PlayerViewModel, onLink: (String) -> Unit, modifier: Modifier, onDismiss: () -> Unit) {
    val item = vm.current ?: return
    val info = vm.info
    val text = rememberHtml(info?.description?.content, onLink)
    Column(modifier.fillMaxWidth()) {
        PanelHeader("Description", onDismiss)
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Text(
                    rememberBranding(item)?.title ?: item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Stat(info?.likeCount?.takeIf { it >= 0 }?.let(::formatCount) ?: "–", "Likes")
                    Stat(info?.viewCount?.takeIf { it >= 0 }?.let(::formatCount) ?: "–", "Views")
                    val epoch = info?.let { i -> runCatching { i.uploadDate?.offsetDateTime()?.toEpochSecond() }.getOrNull() } ?: item.uploadedEpoch
                    Stat(epoch?.let(::fullDate) ?: item.uploadedText() ?: "–", "Uploaded")
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
                }
                if (vm.chapters.isNotEmpty()) {
                    Text(
                        "Chapters",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
                    )
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(vm.chapters) { i, c ->
                            Column(
                                Modifier
                                    .width(150.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { vm.seekTo(c.startMs) }
                                    .background(
                                        if (i == vm.chapterIndex) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                    )
                                    .padding(4.dp),
                            ) {
                                AsyncImage(
                                    model = c.thumbnail ?: item.thumbnail,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)),
                                )
                                Text(c.title, localise = false, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                                Text(formatDuration(c.startMs / 1000), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Title and close button at the top of the comments and description panels. */
@Composable
private fun PanelHeader(title: String, onClose: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close $title".lowercase().replaceFirstChar { it.uppercase() }) }
        }
        HorizontalDivider()
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CommentsPanel(vm: PlayerViewModel, onLink: (String) -> Unit, modifier: Modifier, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var replyTo by remember { mutableStateOf<Comment?>(null) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val signedIn = AppGraph.store.signedIn
    run {
        Column(modifier.fillMaxWidth().imePadding()) {
            PanelHeader("Comments", onDismiss)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                itemsIndexed(vm.comments, key = { i, c -> c.id.ifEmpty { "c$i" } }) { i, c ->
                    CommentRow(c, vm, onLink, onReply = if (signedIn) ({ replyTo = c }) else null)
                    if (c.repliesPage != null || vm.replies.containsKey(c.id)) {
                        val open = vm.replies.containsKey(c.id)
                        TextButton(
                            onClick = { vm.toggleReplies(c) },
                            modifier = Modifier.padding(start = 52.dp),
                        ) {
                            Icon(if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, Modifier.size(18.dp))
                            Text(
                                if (open) "Hide replies" else "${c.replies.coerceAtLeast(1)} ${if (c.replies == 1) "reply" else "replies"}",
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                        if (vm.repliesLoading[c.id] == true) {
                            LoadingIndicator(Modifier.padding(start = 60.dp).size(28.dp))
                        }
                        androidx.compose.animation.AnimatedVisibility(open) {
                            Column(Modifier.padding(start = 40.dp)) {
                                vm.replies[c.id].orEmpty().forEach { r ->
                                    CommentRow(r, vm, onLink, onReply = if (signedIn) ({ replyTo = c }) else null, small = true)
                                }
                            }
                        }
                    }
                    if (i >= vm.comments.size - 4 && vm.hasMoreComments) {
                        LaunchedEffect(i) { vm.loadComments() }
                    }
                }
                if (vm.commentsLoading) item { CenterLoading() }
                if (!vm.commentsLoading && vm.comments.isEmpty() && vm.commentsError == null) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No comments available")
                            TextButton(onClick = { vm.loadComments() }) { Text("Try again") }
                        }
                    }
                }
                vm.commentsError?.let { err ->
                    item { ErrorState(err, { vm.loadComments() }) }
                }
            }
            HorizontalDivider()
            // Compose box, like the official app.
            if (signedIn) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding()) {
                    replyTo?.let { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Replying to ${r.author}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { replyTo = null }) { Icon(Icons.Rounded.Close, "Cancel reply", Modifier.size(18.dp)) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(AppGraph.store.account.value?.avatar, 32.dp)
                        androidx.compose.material3.OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            placeholder = { Text(if (replyTo != null) "Add a reply…" else "Add a comment…") },
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                        androidx.compose.material3.FilledIconButton(
                            enabled = draft.isNotBlank() && !sending,
                            onClick = {
                                sending = true
                                scope.launch {
                                    val err = vm.post(draft.trim(), replyTo)
                                    sending = false
                                    if (err == null) {
                                        draft = ""; replyTo = null
                                        toast(context, "Posted")
                                    } else toast(context, err)
                                }
                            },
                        ) {
                            if (sending) LoadingIndicator(Modifier.size(24.dp)) else Icon(Icons.AutoMirrored.Rounded.Send, "Post")
                        }
                    }
                }
            } else {
                Text(
                    "Sign in to comment and reply",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp).navigationBarsPadding(),
                )
            }
        }
    }
}

/** Like and dislike for a comment; the like count goes up by one while you like it. */
@Composable
fun CommentThumbs(videoId: String, c: Comment) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rating = CommentRatings[c.id]
    val rate: (Rating) -> Unit = { target -> scope.launch { CommentRatings.rate(videoId, c, target)?.let { toast(context, it) } } }
    val liked = rating == Rating.LIKE
    val disliked = rating == Rating.DISLIKE
    val likes = c.likes + if (liked) 1 else 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).clickable { rate(Rating.LIKE) }.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (liked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
                if (liked) "Remove like" else "Like comment",
                Modifier.size(18.dp),
                tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (likes > 0) {
                Text(
                    formatCount(likes.toLong()),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        Icon(
            if (disliked) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown,
            if (disliked) "Remove dislike" else "Dislike comment",
            Modifier.padding(start = 4.dp).clip(RoundedCornerShape(50)).clickable { rate(Rating.DISLIKE) }.padding(6.dp).size(18.dp),
            tint = if (disliked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CommentRow(c: Comment, vm: PlayerViewModel, onLink: (String) -> Unit, onReply: (() -> Unit)?, small: Boolean = false) {
    val text = rememberHtml(c.html, onLink)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (small) 6.dp else 10.dp)) {
        Avatar(c.avatar, if (small) 24.dp else 28.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (c.pinned) Icon(Icons.Rounded.PushPin, "Pinned", Modifier.size(14.dp).padding(end = 2.dp))
                Text(
                    listOfNotNull(c.author, c.date).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                (vm.info?.id ?: vm.current?.id)?.let { CommentThumbs(it, c) }
                if (c.hearted) {
                    Icon(Icons.Rounded.Favorite, "Hearted by creator", Modifier.padding(start = 16.dp).size(16.dp), tint = Color(0xFFE53935))
                }
                if (onReply != null) {
                    Text(
                        "Reply",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onReply)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/** A non-modal snackbar shared by the phone, wide and full-screen player layouts. */
@Composable
private fun ResumeSnackbar(vm: PlayerViewModel) {
    val host = remember { androidx.compose.material3.SnackbarHostState() }
    val notice = vm.resumeNotice
    LaunchedEffect(notice) {
        if (notice == null) return@LaunchedEffect
        val result = host.showSnackbar(
            message = "Resumed at ${formatDuration(notice.second / 1000)}",
            actionLabel = "Start over",
            duration = androidx.compose.material3.SnackbarDuration.Short,
        )
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) vm.startOver(notice.first)
        vm.dismissResumeNotice(notice)
    }
    if (notice != null) androidx.compose.ui.window.Popup(alignment = Alignment.BottomCenter) {
        androidx.compose.material3.SnackbarHost(host, Modifier.padding(16.dp).navigationBarsPadding())
    }
}
