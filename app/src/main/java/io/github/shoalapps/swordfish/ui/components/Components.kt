@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.shoalapps.swordfish.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import io.github.shoalapps.swordfish.LocalisedIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import io.github.shoalapps.swordfish.LocalisedText as Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.github.shoalapps.swordfish.AppGraph
import io.github.shoalapps.swordfish.data.Branding
import io.github.shoalapps.swordfish.data.ChannelItem
import io.github.shoalapps.swordfish.data.Sub
import io.github.shoalapps.swordfish.data.VideoItem
import io.github.shoalapps.swordfish.data.formatCount
import io.github.shoalapps.swordfish.data.formatDuration
import io.github.shoalapps.swordfish.data.subtitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps about five full-size cards composed (and their images loaded) above and below
 * the screen, so scrolling back up never shows blank thumbnails.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Suppress("DEPRECATION")
@Composable
fun rememberCachedListState(): androidx.compose.foundation.lazy.LazyListState =
    androidx.compose.foundation.lazy.rememberLazyListState(
        cacheWindow = androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow(ahead = 1600.dp, behind = 1600.dp),
    )

/** Cards per row: one on phones, more as the window widens, like the official tablet layout. */
@Composable
fun feedColumns(): Int = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp / 340).coerceIn(1, 4)

/** True on tablets, foldables and desktop windows, where pages get a two-pane layout. */
@Composable
fun isWideScreen(): Boolean = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 840

/**
 * Videos as full-width cards on phones, or a grid of [columns] on wider screens.
 * [between] is called after each row (or card), e.g. to slot in a Shorts shelf.
 */
fun androidx.compose.foundation.lazy.LazyListScope.videoGrid(
    videos: List<VideoItem>,
    columns: Int,
    keyPrefix: String = "",
    between: androidx.compose.foundation.lazy.LazyListScope.(row: Int) -> Unit = {},
    card: @Composable (video: VideoItem, index: Int, modifier: Modifier) -> Unit,
) {
    if (columns <= 1) {
        videos.forEachIndexed { i, v ->
            item(key = keyPrefix + v.id) { card(v, i, Modifier.animateItem().staggeredReveal(i)) }
            between(i)
        }
        return
    }
    videos.chunked(columns).forEachIndexed { r, row ->
        item(key = keyPrefix + "row-" + row.first().id) {
            Row(
                Modifier.animateItem().staggeredReveal(r).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEachIndexed { c, v -> card(v, r * columns + c, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        between(r)
    }
}

/** Extra space lists need at the bottom (nav bar / mini player). */
val LocalBottomPadding = staticCompositionLocalOf { 0.dp }

fun listPadding(bottom: Dp, top: Dp = 0.dp) = PaddingValues(top = top, bottom = bottom + 8.dp)

/** Where a tapped thumbnail was on screen, so the player can zoom out of it. */
object LaunchOrigin {
    private var id: String? = null
    private var rect: Rect? = null

    fun set(videoId: String, bounds: Rect) { id = videoId; rect = bounds }

    fun take(videoId: String?): Rect? {
        val r = if (videoId != null && videoId == id) rect else null
        id = null; rect = null
        return r
    }
}

/** Expressive "squish" when pressed. */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource, pressed: Float = 0.97f): Modifier {
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressed else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "press",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Items rise and fade in one after another the first time a list appears. */
@Composable
fun Modifier.staggeredReveal(index: Int): Modifier {
    val anim = remember { Animatable(if (index < 10) 0f else 1f) }
    val density = LocalDensity.current
    LaunchedEffect(Unit) {
        if (anim.value < 1f) {
            delay(index * 45L)
            anim.animateTo(1f, tween(380))
        }
    }
    return graphicsLayer {
        alpha = anim.value
        translationY = with(density) { 24.dp.toPx() } * (1f - anim.value)
    }
}

/** DeArrow title + thumbnail for a video, if enabled. */
@Composable
fun rememberBranding(item: VideoItem): Branding? {
    val enabled = AppGraph.store.settings.collectAsState().value.deArrow
    val state by produceState(
        initialValue = if (enabled) AppGraph.deArrow.cached(item.id) else null,
        key1 = item.id,
        key2 = enabled,
    ) {
        value = if (enabled) AppGraph.deArrow.branding(item.id) else null
    }
    return state
}

fun shareVideo(context: Context, url: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    context.startActivity(Intent.createChooser(send, "Share"))
}

fun openExternal(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

@Composable
fun Thumbnail(
    item: VideoItem,
    branding: Branding?,
    modifier: Modifier = Modifier,
    corner: Dp = 0.dp,
    showBadge: Boolean = true,
) {
    var dearrowFailed by remember(item.id) { mutableStateOf(false) }
    val url = branding?.thumbnail?.takeIf { !dearrowFailed } ?: item.thumbnail
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        val context = LocalContext.current
        val request = remember(url) {
            ImageRequest.Builder(context)
                .data(url)
                .memoryCacheKey(url)
                .placeholderMemoryCacheKey(item.thumbnail)
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onError = { if (url != item.thumbnail) dearrowFailed = true },
        )
        if (showBadge) {
            val label = if (item.isLive) "LIVE" else formatDuration(item.durationSec)
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (item.isLive) Color(0xFFCC0000) else Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
fun Avatar(url: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (url == null) {
            Icon(Icons.Rounded.AccountCircle, null, Modifier.fillMaxSize(), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Looks up a channel picture for videos that came without one (charts, some search results). */
@Composable
fun rememberChannelAvatar(channelUrl: String?): String? {
    val url by produceState<String?>(null, channelUrl) {
        value = channelUrl?.let { AppGraph.repo.channelAvatar(it) }
    }
    return url
}

/** The ⋮ menu on every video. */
@Composable
fun VideoMenu(item: VideoItem, onChannel: ((String) -> Unit)?) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.MoreVert, "More", Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(16.dp)) {
            if (AppGraph.store.signedIn) {
                DropdownMenuItem(
                    text = { Text("Save to Watch later") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                    onClick = {
                        open = false
                        AppGraph.saveToWatchLater(context, item.id)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Share") },
                leadingIcon = { Icon(Icons.Rounded.Share, null) },
                onClick = { open = false; shareVideo(context, io.github.shoalapps.swordfish.data.FeedFilter.shareUrl(item.id, item.url)) },
            )
            DropdownMenuItem(
                text = { Text("Play next") },
                onClick = { open = false; toast(context, if (AppGraph.store.playNext(item)) "Playing next" else "Already playing or next") },
            )
            DropdownMenuItem(
                text = { Text("Add to queue") },
                onClick = { open = false; toast(context, if (AppGraph.store.addToQueue(item)) "Added to queue" else "Already playing or queued") },
            )
            if (onChannel != null && item.channelUrl != null) {
                DropdownMenuItem(
                    text = { Text("Go to channel") },
                    leadingIcon = { Icon(Icons.Rounded.AccountCircle, null) },
                    onClick = { open = false; onChannel(item.channelUrl) },
                )
            }
        }
    }
}

/** Full-width card, laid out like the official app's home feed. */
@Composable
fun VideoCard(
    item: VideoItem,
    onClick: () -> Unit,
    onChannel: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    inGrid: Boolean = false,
) {
    val branding = rememberBranding(item)
    val source = remember { MutableInteractionSource() }
    var bounds by remember { mutableStateOf<Rect?>(null) }
    Column(
        modifier
            .fillMaxWidth()
            .pressScale(source, 0.98f)
            .clickable(interactionSource = source, indication = ripple()) {
                bounds?.let { LaunchOrigin.set(item.id, it) }
                onClick()
            }
            .padding(bottom = if (inGrid) 20.dp else 16.dp),
    ) {
        Thumbnail(
            item,
            branding,
            Modifier.fillMaxWidth().onGloballyPositioned { bounds = it.boundsInRoot() },
            corner = if (inGrid) 12.dp else 0.dp,
        )
        Row(Modifier.padding(start = if (inGrid) 0.dp else 12.dp, top = 12.dp)) {
            Avatar(
                url = item.channelAvatar ?: rememberChannelAvatar(item.channelUrl),
                size = 36.dp,
                modifier = Modifier.clickable(enabled = onChannel != null && item.channelUrl != null) {
                    item.channelUrl?.let { onChannel?.invoke(it) }
                },
            )
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                AnimatedContent(
                    targetState = branding?.title ?: item.title,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                    label = "title",
                ) { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp),
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = item.subtitle(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            VideoMenu(item, onChannel)
        }
    }
}

/** Compact row: small thumbnail on the left (history, playlists). */
@Composable
fun VideoRow(item: VideoItem, onClick: () -> Unit, modifier: Modifier = Modifier, large: Boolean = false) {
    val branding = rememberBranding(item)
    val source = remember { MutableInteractionSource() }
    var bounds by remember { mutableStateOf<Rect?>(null) }
    Row(
        modifier
            .fillMaxWidth()
            .pressScale(source)
            .clickable(interactionSource = source, indication = ripple()) {
                bounds?.let { LaunchOrigin.set(item.id, it) }
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Thumbnail(
            item,
            branding,
            Modifier.width(if (large) 360.dp else 164.dp).onGloballyPositioned { bounds = it.boundsInRoot() },
            corner = if (large) 12.dp else 8.dp,
        )
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                text = branding?.title ?: item.title,
                style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.subtitle(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        VideoMenu(item, null)
    }
}

/** Small card for horizontal shelves (You tab). */
@Composable
fun ShelfCard(item: VideoItem, onClick: () -> Unit) {
    val branding = rememberBranding(item)
    val source = remember { MutableInteractionSource() }
    var bounds by remember { mutableStateOf<Rect?>(null) }
    Column(
        Modifier
            .width(160.dp)
            .pressScale(source)
            .clip(RoundedCornerShape(10.dp))
            .clickable(interactionSource = source, indication = ripple()) {
                bounds?.let { LaunchOrigin.set(item.id, it) }
                onClick()
            },
    ) {
        Thumbnail(item, branding, Modifier.fillMaxWidth().onGloballyPositioned { bounds = it.boundsInRoot() }, corner = 10.dp)
        Text(
            branding?.title ?: item.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            item.channelName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ChannelRow(item: ChannelItem, onClick: () -> Unit) {
    val subs by AppGraph.store.subs.collectAsState()
    val subscribed = subs.any { it.url == item.url }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(96.dp), contentAlignment = Alignment.Center) { Avatar(item.avatar, 80.dp) }
        Column(Modifier.padding(horizontal = 16.dp).weight(1f)) {
            Text(item.name, localise = false, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.subscribers >= 0) {
                Text(
                    "${formatCount(item.subscribers)} subscribers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            ChannelSubscribe(Sub(item.url, item.name, item.avatar))
        }
    }
}

/** YouTube-style pill: inverse colours, then a softer tonal shape once subscribed. */
@Composable
fun ChannelSubscribe(channel: Sub) {
    val subs by AppGraph.store.subs.collectAsState()
    val sub = subs.firstOrNull { it.url == channel.url }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SubscribeButton(sub != null) { AppGraph.toggleSubscribe(channel) }
        if (sub != null) SubscriptionBell(sub)
    }
}

@Composable
fun SubscriptionBell(sub: Sub) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        androidx.compose.material3.IconButton(onClick = { expanded = true }) {
            Icon(if (sub.notifications == io.github.shoalapps.swordfish.data.NotificationMode.NONE)
                Icons.Rounded.NotificationsNone else Icons.Rounded.Notifications,
                "Notifications: ${sub.notifications.label}")
        }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            io.github.shoalapps.swordfish.data.NotificationMode.entries.forEach { mode ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text((if (sub.notifications == mode) "✓ " else "") + mode.label) },
                    onClick = { AppGraph.setNotifications(sub.url, mode); expanded = false },
                )
            }
        }
    }
}

/** YouTube-style pill: inverse colours, then a softer tonal shape once subscribed. */
@Composable
fun SubscribeButton(subscribed: Boolean, onClick: () -> Unit) {
    val corner by animateDpAsState(
        if (subscribed) 14.dp else 20.dp,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "corner",
    )
    val container by animateColorAsState(
        if (subscribed) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.inverseSurface,
        label = "container",
    )
    val contentColor by animateColorAsState(
        if (subscribed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.inverseOnSurface,
        label = "content",
    )
    val source = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        interactionSource = source,
        modifier = Modifier.height(36.dp).pressScale(source, 0.92f),
    ) {
        AnimatedContent(
            targetState = subscribed,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.8f)) togetherWith fadeOut() },
            label = "sub",
        ) { isSub ->
            if (isSub) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Subscribed")
                }
            } else {
                Text("Subscribe")
            }
        }
    }
}

@Composable
fun CenterLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        LoadingIndicator(Modifier.size(56.dp))
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier, extra: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onRetry) { Text("Try again") }
            extra?.invoke()
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ShapeIcon(icon, 96.dp)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) {
            Spacer(Modifier.height(8.dp))
            action()
        }
    }
}

/** Pulsing placeholder cards while a feed loads. */
@Composable
fun SkeletonCards(count: Int = 3) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alpha",
    )
    val c1 = MaterialTheme.colorScheme.surfaceContainerHighest
    val c2 = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(Modifier.alpha(alpha)) {
        repeat(count) {
            Column(Modifier.padding(bottom = 16.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(c1))
                Row(Modifier.padding(start = 12.dp, top = 12.dp)) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(c1))
                    Column(Modifier.padding(start = 12.dp)) {
                        Box(Modifier.fillMaxWidth(0.85f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(c1))
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth(0.5f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(c2))
                    }
                }
            }
        }
    }
}
