@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, UnstableApi::class)

package io.github.shoalapps.swordfish.ui.screens

import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Comment
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Reply
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import io.github.shoalapps.common.ui.LocalisedIcon as Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import io.github.shoalapps.common.ui.LocalisedText as Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.github.shoalapps.swordfish.AppGraph
import io.github.shoalapps.swordfish.data.Comment
import io.github.shoalapps.swordfish.data.FeedFilter
import io.github.shoalapps.swordfish.data.RydVotes
import io.github.shoalapps.swordfish.data.Sub
import io.github.shoalapps.swordfish.data.VideoItem
import io.github.shoalapps.swordfish.data.formatCount
import io.github.shoalapps.swordfish.player.NetworkInfo
import io.github.shoalapps.swordfish.player.PlayerHolder
import io.github.shoalapps.swordfish.player.StreamPicker
import io.github.shoalapps.swordfish.ui.ScreenAwake
import io.github.shoalapps.swordfish.ui.components.Avatar
import io.github.shoalapps.swordfish.ui.components.CenterLoading
import io.github.shoalapps.swordfish.ui.components.ErrorState
import io.github.shoalapps.swordfish.ui.components.ShapeIcon
import io.github.shoalapps.swordfish.ui.components.shareVideo
import io.github.shoalapps.swordfish.ui.components.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfo

/** A Short tapped elsewhere (e.g. the Home shelf) that the Shorts tab should open on. */
object ShortsLaunch {
    @Volatile var start: VideoItem? = null
}

/**
 * Shorts feed. Three players are kept warm (previous, current, next) and the three
 * Shorts above and below have their details fetched ahead. A Short that can't be
 * loaded is dropped, so the preloaded ones behind it move up into its place.
 */
class ShortsViewModel : ViewModel() {
    private val pool = List(3) {
        ExoPlayer.Builder(AppGraph.app)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply { repeatMode = Player.REPEAT_MODE_ONE }
    }
    /** slot -> video id it's holding */
    private val slotId = arrayOfNulls<String>(3)
    private val slotJobs = arrayOfNulls<Job>(3)

    var items by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var currentId by mutableStateOf<String?>(null)
        private set
    var paused by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var limitReached by mutableStateOf<String?>(null)
        private set
    var jumpTo by mutableStateOf<Int?>(null)
    var slotVersion by mutableIntStateOf(0)
        private set

    val infos = mutableStateMapOf<String, StreamInfo>()
    val ready = mutableStateMapOf<String, Boolean>()
    val aspects = mutableStateMapOf<String, Float>()
    val votes = mutableStateMapOf<String, RydVotes>()

    private var active = false
    private var viewedThisSession = mutableSetOf<String>()

    init {
        pool.forEachIndexed { slot, p ->
            p.addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    slotId[slot]?.let { ready[it] = true }
                }
                override fun onVideoSizeChanged(size: VideoSize) {
                    val id = slotId[slot]
                    if (id != null && size.width > 0 && size.height > 0) aspects[id] = size.width.toFloat() / size.height
                }
                override fun onPlayerError(error: PlaybackException) {
                    slotId[slot]?.let { markFailed(it, error) }
                }
            })
        }
        viewModelScope.launch {
            var tick = 0
            while (true) {
                // No playback or progress updates are needed while Shorts is hidden.
                if (!active) { delay(500); continue }
                val p = currentId?.let { playerFor(it) }
                val awake = active && p?.isPlaying == true
                if (ScreenAwake.shorts != awake) ScreenAwake.shorts = awake
                if (p != null) {
                    val d = p.duration
                    progress = if (d > 0) (p.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
                }
                if (++tick % 10 == 0 && awake) {
                    AppGraph.store.addShortsMs(1000)
                    checkDailyLimit()
                }
                delay(100)
            }
        }
        viewModelScope.launch {
            io.github.shoalapps.swordfish.data.NetworkPolicy.changes.collect {
                val index = items.indexOfFirst { v -> v.id == currentId }
                if (index >= 0) {
                    val wasPaused = paused
                    retryNetwork()
                    paused = wasPaused
                    applyPlayState()
                }
            }
        }
        load()
    }

    fun retryNetwork() {
        networkError = null
        failStreak = 0
        for (slot in 0 until 3) { slotJobs[slot]?.cancel(); pool[slot].stop(); slotId[slot] = null }
        infos.clear()
        val cur = items.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        onPage(cur)
    }

    fun load() {
        loading = true; error = null
        viewModelScope.launch {
            try {
                val cached = AppGraph.shortsCache.also { AppGraph.shortsCache = null }
                val list = cached ?: run {
                    val fromAccount = if (AppGraph.store.signedIn) {
                        runCatching { AppGraph.account.home().shorts }.getOrDefault(emptyList())
                    } else emptyList()
                    val more = if (fromAccount.size >= 20) emptyList() else AppGraph.repo.shorts(AppGraph.store.subs.value)
                    fromAccount + more
                }
                var all = FeedFilter.apply(list)
                ShortsLaunch.start?.let { launch ->
                    ShortsLaunch.start = null
                    all = listOf(launch) + all.filter { it.id != launch.id }
                }
                items = all
                if (items.isEmpty()) error = "Couldn't find any Shorts right now"
                else onPage(0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = NetworkInfo.friendlyError(e)
            } finally {
                loading = false
            }
        }
    }

    fun playerFor(id: String): ExoPlayer? {
        slotVersion
        val slot = slotId.indexOf(id)
        return if (slot >= 0) pool[slot] else null
    }

    fun slotOf(id: String): Int {
        slotVersion
        return slotId.indexOf(id)
    }

    private suspend fun info(id: String): StreamInfo =
        infos[id] ?: AppGraph.repo.stream("https://www.youtube.com/watch?v=$id").also { infos[id] = it }

    /** Network-wide problem (bot check / VPN): shown over the feed instead of dropping Shorts. */
    var networkError by mutableStateOf<String?>(null)
        private set
    private var failStreak = 0

    /**
     * Drop a Short that won't load; everything after it moves up one place.
     * If YouTube is blocking the whole network, nothing is dropped; a message is shown instead.
     */
    fun markFailed(id: String, cause: Throwable? = null) {
        val msg = cause?.message.orEmpty()
        val networkWide = msg.contains("not a bot", true) || msg.contains("LOGIN_REQUIRED") ||
            cause is java.io.IOException || ++failStreak > 3
        if (networkWide) {
            networkError = NetworkInfo.friendlyError(msg.ifBlank { "YouTube isn't letting this network play Shorts right now." })
            return
        }
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return
        val curIndex = items.indexOfFirst { it.id == currentId }
        if (index < curIndex) return // already scrolled past; leave the history alone
        val slot = slotId.indexOf(id)
        if (slot >= 0) {
            slotJobs[slot]?.cancel()
            pool[slot].stop()
            slotId[slot] = null
        }
        items = items.filterNot { it.id == id }
        if (items.isEmpty()) {
            error = "Couldn't play any Shorts right now"
            return
        }
        onPage(curIndex.coerceIn(0, items.lastIndex))
    }

    fun onPage(index: Int) {
        val item = items.getOrNull(index) ?: return
        currentId = item.id
        paused = false
        viewedThisSession += item.id
        checkSessionLimit()

        val wanted = (if (io.github.shoalapps.swordfish.data.NetworkPolicy.savingData()) listOf(index) else listOf(index, index + 1, index - 1)).mapNotNull { items.getOrNull(it)?.id }
        for (slot in 0 until 3) {
            if (slotId[slot] !in wanted) {
                slotJobs[slot]?.cancel()
                pool[slot].stop()
                pool[slot].clearMediaItems()
                slotId[slot] = null
            }
        }
        for (id in wanted) {
            if (slotId.contains(id)) continue
            val slot = slotId.indexOf(null).takeIf { it >= 0 } ?: continue
            slotId[slot] = id
            ready.remove(id)
            slotJobs[slot] = viewModelScope.launch {
                try {
                    val inf = info(id)
                    val src = StreamPicker.buildSource(StreamPicker.pick(inf, io.github.shoalapps.swordfish.data.NetworkPolicy.quality()), inf)
                    if (src == null) { markFailed(id); return@launch }
                    if (slotId[slot] != id) return@launch
                    pool[slot].setMediaSource(src)
                    pool[slot].playWhenReady = false
                    pool[slot].prepare()
                    failStreak = 0
                    networkError = null
                    applyPlayState()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    markFailed(id, e)
                }
            }
        }
        slotVersion++
        applyPlayState()

        // Look further ahead: details, thumbnails and dislikes for three above and below.
        if (!io.github.shoalapps.swordfish.data.NetworkPolicy.backgroundAllowed()) return
        for (i in (index - 3)..(index + 3)) {
            val v = items.getOrNull(i) ?: continue
            if (!infos.containsKey(v.id)) viewModelScope.launch {
                try { info(v.id) } catch (e: CancellationException) { throw e } catch (e: Exception) { if (i > index) markFailed(v.id, e) }
            }
            if (!votes.containsKey(v.id)) viewModelScope.launch { AppGraph.ryd.votes(v.id)?.let { votes[v.id] = it } }
            SingletonImageLoader.get(AppGraph.app).enqueue(ImageRequest.Builder(AppGraph.app).data(v.thumbnail).build())
        }
    }

    private fun applyPlayState() {
        for (slot in 0 until 3) {
            val p = pool[slot]
            if (slotId[slot] != null && slotId[slot] == currentId) {
                p.playWhenReady = active && !paused && limitReached == null
            } else {
                p.playWhenReady = false
                if (p.currentPosition > 0) p.seekTo(0)
            }
        }
    }

    fun togglePause() {
        paused = !paused
        applyPlayState()
    }

    private fun takeLaunch() {
        val start = ShortsLaunch.start ?: return
        if (items.isEmpty()) return
        ShortsLaunch.start = null
        val existing = items.indexOfFirst { it.id == start.id }
        val cur = items.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        val target = if (existing >= 0) existing else {
            items = items.take(cur + 1) + start + items.drop(cur + 1)
            cur + 1
        }
        jumpTo = target
    }

    fun setActive(on: Boolean) {
        active = on
        if (!on) ScreenAwake.shorts = false
        if (on) {
            takeLaunch()
            PlayerHolder.player.pause()
            viewedThisSession = currentId?.let { mutableSetOf(it) } ?: mutableSetOf()
            checkDailyLimit()
        }
        applyPlayState()
    }

    private fun checkSessionLimit() {
        val limit = AppGraph.store.settings.value.shortsSessionLimit
        if (limit > 0 && viewedThisSession.size > limit) {
            limitReached = "You've watched $limit Shorts this time."
            applyPlayState()
        }
    }

    private fun checkDailyLimit() {
        val minutes = AppGraph.store.settings.value.shortsDailyMinutes
        if (minutes > 0 && AppGraph.store.shortsMsToday() >= minutes * 60_000L) {
            limitReached = "You've reached today's $minutes-minute Shorts limit."
            applyPlayState()
        }
    }

    override fun onCleared() {
        ScreenAwake.shorts = false
        pool.forEach { it.release() }
    }
}

@Composable
fun ShortsScreen(onChannel: (String) -> Unit, onExit: () -> Unit, vm: ShortsViewModel = viewModel()) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        vm.setActive(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> vm.setActive(true)
                Lifecycle.Event.ON_PAUSE -> vm.setActive(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            vm.setActive(false)
        }
    }
    val settingsNow = AppGraph.store.settings.collectAsState().value
    val layout = settingsNow.shortsLayout
    var commentsFor by remember { mutableStateOf<VideoItem?>(null) }
    // First visit: let people know short videos have their own Shoal app. Shorts wait while it shows.
    if (!settingsNow.clipsIntroSeen) {
        ClipsIntro(
            onShown = { vm.setActive(false) },
            onDone = {
                AppGraph.store.updateSettings { it.copy(clipsIntroSeen = true) }
                vm.setActive(true)
            },
        )
    }

    // White status bar icons over the video, whatever the app theme; put back on the way out.
    val view = androidx.compose.ui.platform.LocalView.current
    val overVideo = vm.limitReached == null && vm.error == null
    DisposableEffect(overVideo) {
        val window = (view.context as? android.app.Activity)?.window
        val bars = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val wasLight = bars?.isAppearanceLightStatusBars ?: false
        if (overVideo) bars?.isAppearanceLightStatusBars = false
        onDispose { bars?.isAppearanceLightStatusBars = wasLight }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val limit = vm.limitReached
        when {
            limit != null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { LimitReached(limit, onExit) }
            vm.loading -> LoadingIndicator(Modifier.align(Alignment.Center).size(64.dp), color = Color.White)
            vm.error != null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                ErrorState(vm.error!!, vm::load, Modifier.align(Alignment.Center))
            }
            else -> {
                val pager = rememberPagerState { vm.items.size }
                LaunchedEffect(pager.settledPage, vm.items) { vm.onPage(pager.settledPage) }
                LaunchedEffect(vm.jumpTo) {
                    vm.jumpTo?.let { pager.scrollToPage(it); vm.jumpTo = null }
                }
                VerticalPager(
                    state = pager,
                    beyondViewportPageCount = 1,
                    key = { vm.items[it].id },
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val item = vm.items[page]
                    ShortPage(vm, item, isCurrent = item.id == vm.currentId, layout = layout, onChannel = onChannel, onComments = { commentsFor = item })
                }
                vm.networkError?.let { msg ->
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    ) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(msg, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                            Button(onClick = vm::retryNetwork, modifier = Modifier.padding(top = 12.dp)) { Text("Try again") }
                        }
                    }
                }
            }
        }
        // Header, no shading: the text carries its own soft shadow.
        if (vm.limitReached == null && vm.error == null && !vm.loading) {
            Text(
                when (layout) { "reels" -> "Reels"; "tiktok" -> "For You"; else -> "Shorts" },
                color = Color.White,
                style = overlayText(if (layout == "tiktok") 17.sp else 22.sp, FontWeight.Bold),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
                    .then(if (layout == "tiktok") Modifier.align(Alignment.TopCenter) else Modifier),
            )
        }
    }
    commentsFor?.let { v -> ShortComments(v) { commentsFor = null } }
}

/** One-time sheet about Clownfish, the dedicated app for short videos. */
@Composable
private fun ClipsIntro(onShown: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { onShown() }
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ShapeIcon(
                Icons.Rounded.VideoLibrary,
                112.dp,
                MaterialShapes.Cookie12Sided,
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                "Short videos have their own app",
                style = MaterialTheme.typography.headlineSmallEmphasized,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 20.dp),
            )
            Text(
                "Clownfish brings YouTube Shorts, TikTok and Reels together in one Material You feed, " +
                    "with the same daily limits and layouts. Shorts will always keep working here too.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
            Button(
                onClick = {
                    io.github.shoalapps.swordfish.ui.components.openExternal(context, "https://shoalapps.github.io/#apps")
                    onDone()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Find out about Clownfish") }
            androidx.compose.material3.TextButton(onClick = onDone, modifier = Modifier.padding(top = 8.dp)) {
                Text("Keep watching Shorts here")
            }
        }
    }
}

/** White text with a soft shadow, readable on any video without darkening it. */
private fun overlayText(size: TextUnit, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontSize = size,
    fontWeight = weight,
    shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1.5f), blurRadius = 8f),
)

@Composable
private fun LimitReached(message: String, onExit: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ShapeIcon(Icons.Rounded.HourglassBottom, 120.dp, MaterialShapes.Cookie12Sided, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        Text("Time for a break", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp))
        Text(
            "$message You can change this in Settings > Shorts.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        Button(onClick = onExit) { Text("Back to Home") }
    }
}

@Composable
private fun ShortPage(
    vm: ShortsViewModel,
    item: VideoItem,
    isCurrent: Boolean,
    layout: String,
    onChannel: (String) -> Unit,
    onComments: () -> Unit,
) {
    val context = LocalContext.current
    val info = vm.infos[item.id]
    val player = vm.playerFor(item.id)
    var liked by remember(item.id) { mutableStateOf<Boolean?>(null) }
    val subs by AppGraph.store.subs.collectAsState()
    val heart = remember(item.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val channelUrl = info?.uploaderUrl ?: item.channelUrl
    val channelName = (info?.uploaderName ?: item.channelName).let { if (it.isBlank() || it.startsWith("@")) it else "@$it" }
    val avatar = info?.uploaderAvatars?.maxByOrNull { it.height }?.url ?: item.channelAvatar
    val subscribed = channelUrl != null && subs.any { it.url == channelUrl }
    val likes = info?.likeCount?.takeIf { it >= 0 } ?: vm.votes[item.id]?.likes
    val dislikes = vm.votes[item.id]?.dislikes
    val onLike = {
        liked = if (liked == true) null else true
        rateShort(context, item.id, liked)
    }
    val onDislike = {
        liked = if (liked == false) null else false
        rateShort(context, item.id, liked)
    }
    val onSubscribe = {
        if (channelUrl != null) AppGraph.toggleSubscribe(Sub(channelUrl, channelName.removePrefix("@"), item.channelAvatar))
    }
    val onShare = { shareVideo(context, FeedFilter.shareUrl(item.id, item.url)) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(item.id) {
                detectTapGestures(
                    onTap = { if (isCurrent) vm.togglePause() },
                    onDoubleTap = {
                        // Double-tap to like, with a heart that pops, like the other apps.
                        if (liked != true) onLike()
                        scope.launch { heart.snapTo(0.01f); heart.animateTo(1f, tween(650)); heart.snapTo(0f) }
                    },
                )
            },
    ) {
        val videoRatio = vm.aspects[item.id] ?: (9f / 16f)
        val boxRatio = maxWidth / maxHeight
        // Fill the screen when that trims only a little (phones); otherwise show the whole video,
        // as on tablets and in landscape, where filling would blow up a thin middle slice.
        val cropped = if (videoRatio > boxRatio) 1f - boxRatio / videoRatio else 1f - videoRatio / boxRatio
        val fit = cropped > 0.2f
        val w: androidx.compose.ui.unit.Dp
        val h: androidx.compose.ui.unit.Dp
        if (fit == (videoRatio > boxRatio)) { w = maxWidth; h = maxWidth / videoRatio } else { w = maxHeight * videoRatio; h = maxHeight }
        Box(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
            if (fit) {
                // A blurred, dimmed copy of the thumbnail fills the sides.
                AsyncImage(
                    model = item.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(48.dp).graphicsLayer { alpha = 0.55f },
                )
            }
            AsyncImage(model = item.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.requiredSize(w, h))
            if (player != null) {
                val isReady = vm.ready[item.id] == true
                key(vm.slotOf(item.id)) {
                    AndroidView(
                        factory = { ctx -> TextureView(ctx).also { player.setVideoTextureView(it) } },
                        onRelease = { player.clearVideoTextureView(it) },
                        modifier = Modifier.requiredSize(w, h).graphicsLayer { alpha = if (isReady) 1f else 0f },
                    )
                }
            }
        }

        AnimatedVisibility(
            isCurrent && vm.paused,
            enter = fadeIn() + scaleIn(initialScale = 1.4f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Icon(Icons.Rounded.PlayArrow, "Play", Modifier.size(84.dp), tint = Color.White.copy(alpha = 0.85f))
        }
        if (heart.value > 0f) {
            val v = heart.value
            Icon(
                Icons.Rounded.Favorite,
                null,
                tint = Color(0xFFFF3B5C),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(110.dp)
                    .graphicsLayer {
                        val sc = if (v < 0.3f) 0.4f + v * 2.5f else 1.15f - (v - 0.3f) * 0.2f
                        scaleX = sc; scaleY = sc
                        alpha = if (v < 0.7f) 1f else (1f - (v - 0.7f) / 0.3f)
                        translationY = -v * 60f
                    },
            )
        }

        // Details (bottom-left) and actions (right), lined up along one baseline.
        // When the whole video is shown (tablets), they hug the video instead of the screen's edges.
        Row(
            Modifier.align(Alignment.BottomCenter)
                .then(
                    // Room for the buttons on each side keeps the row centred on the video: details
                    // start at the video's left edge, buttons sit just outside its right edge.
                    if (fit && w + 144.dp < maxWidth) Modifier.width(w + 144.dp).padding(start = 72.dp + 14.dp, end = 4.dp, bottom = 18.dp)
                    else Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, bottom = 18.dp),
                ),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                when (layout) {
                    "tiktok" -> {
                        Text(
                            channelName, color = Color.White, style = overlayText(16.sp, FontWeight.Bold), maxLines = 1,
                            modifier = Modifier.clickable(enabled = channelUrl != null) { channelUrl?.let(onChannel) },
                        )
                        Text(info?.name ?: item.title, color = Color.White, style = overlayText(14.sp), maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    }
                    "reels" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(avatar, 32.dp, Modifier.clickable(enabled = channelUrl != null) { channelUrl?.let(onChannel) })
                            Text(
                                channelName.removePrefix("@"), color = Color.White, style = overlayText(14.sp, FontWeight.Bold), maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp).weight(1f, fill = false),
                            )
                            OverlayPill(if (subscribed) "Following" else "Follow", outlined = true, onClick = onSubscribe)
                        }
                        Text(info?.name ?: item.title, color = Color.White, style = overlayText(14.sp), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
                    }
                    else -> {
                        Text(info?.name ?: item.title, color = Color.White, style = overlayText(15.sp, FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                            Avatar(avatar, 32.dp, Modifier.clickable(enabled = channelUrl != null) { channelUrl?.let(onChannel) })
                            Text(
                                channelName, color = Color.White, style = overlayText(14.sp, FontWeight.Medium), maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp).weight(1f, fill = false),
                            )
                            OverlayPill(if (subscribed) "Subscribed" else "Subscribe", outlined = subscribed, onClick = onSubscribe)
                        }
                    }
                }
            }
            // Action column: fixed width so icons and labels sit exactly in line.
            Column(
                Modifier.width(64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (layout == "youtube") 14.dp else 18.dp),
            ) {
                when (layout) {
                    "tiktok" -> {
                        Box(Modifier.padding(bottom = 6.dp), contentAlignment = Alignment.BottomCenter) {
                            Avatar(avatar, 46.dp, Modifier.border(1.5.dp, Color.White, CircleShape).clickable(enabled = channelUrl != null) { channelUrl?.let(onChannel) })
                            if (!subscribed && channelUrl != null) {
                                Box(
                                    Modifier.offset(y = 10.dp).size(20.dp).clip(CircleShape).background(Color(0xFFFE2C55)).clickable(onClick = onSubscribe),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Rounded.Add, "Follow", Modifier.size(14.dp), tint = Color.White) }
                            }
                        }
                        PlainAction(Icons.Rounded.Favorite, likes?.let(::formatCount) ?: "0", if (liked == true) Color(0xFFFE2C55) else Color.White, 34.dp, onClick = onLike)
                        PlainAction(Icons.Rounded.Comment, "Comments", Color.White, 32.dp, onClick = onComments)
                        PlainAction(Icons.Rounded.BookmarkBorder, "Save", Color.White, 32.dp) {
                            if (AppGraph.store.signedIn) {
                                AppGraph.saveToWatchLater(context, item.id)
                            } else toast(context, "Sign in to save")
                        }
                        PlainAction(Icons.Rounded.Reply, "Share", Color.White, 32.dp, mirror = true, onClick = onShare)
                    }
                    "reels" -> {
                        PlainAction(if (liked == true) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder, likes?.let(::formatCount) ?: "", if (liked == true) Color(0xFFFF3040) else Color.White, 28.dp, onClick = onLike)
                        PlainAction(Icons.Outlined.ChatBubbleOutline, "", Color.White, 28.dp, onClick = onComments)
                        PlainAction(Icons.AutoMirrored.Rounded.Send, "", Color.White, 28.dp, onClick = onShare)
                        PlainAction(Icons.Outlined.ThumbDown, dislikes?.let(::formatCount) ?: "", if (liked == false) MaterialTheme.colorScheme.primary else Color.White, 26.dp, onClick = onDislike)
                    }
                    else -> {
                        CircleAction(if (liked == true) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp, likes?.let(::formatCount) ?: "Like", liked == true, onLike)
                        CircleAction(if (liked == false) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown, dislikes?.let(::formatCount) ?: "Dislike", liked == false, onDislike)
                        CircleAction(Icons.Outlined.ChatBubbleOutline, "Comments", false, onComments)
                        CircleAction(Icons.Rounded.Share, "Share", false, onShare)
                    }
                }
                io.github.shoalapps.swordfish.ui.components.VideoMenu(item, onChannel)
            }
        }
        if (isCurrent && player != null) {
            LinearProgressIndicator(
                progress = { vm.progress },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp),
                color = if (layout == "youtube") Color(0xFFFF0033) else Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
        }
    }
}

/** YouTube Shorts style: icon in a soft circle that follows the system theme, label underneath. */
@Composable
private fun CircleAction(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(active) { if (active) { pop.animateTo(1.25f, tween(90)); pop.animateTo(1f, spring(0.4f, 600f)) } }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
            contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(48.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value },
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(26.dp)) }
        }
        Text(label, color = Color.White, style = overlayText(12.sp, FontWeight.Medium), maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}

/** TikTok / Reels style: bare icon with a shadow, count underneath. */
@Composable
private fun PlainAction(icon: ImageVector, label: String, tint: Color, size: Dp, mirror: Boolean = false, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(4.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(size).offset(y = 1.dp).graphicsLayer { scaleX = if (mirror) -1f else 1f }, tint = Color.Black.copy(alpha = 0.35f))
            Icon(icon, label.ifEmpty { null }, Modifier.size(size).graphicsLayer { scaleX = if (mirror) -1f else 1f }, tint = tint)
        }
        if (label.isNotEmpty()) {
            Text(label, color = Color.White, style = overlayText(12.sp, FontWeight.SemiBold), maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun OverlayPill(text: String, outlined: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(if (outlined) 8.dp else 50.dp),
        color = if (outlined) Color.Transparent else MaterialTheme.colorScheme.inverseSurface,
        contentColor = if (outlined) Color.White else MaterialTheme.colorScheme.inverseOnSurface,
        border = if (outlined) androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)) else null,
        modifier = Modifier.height(30.dp),
    ) {
        Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

private fun rateShort(context: android.content.Context, id: String, liked: Boolean?) {
    if (!AppGraph.store.signedIn) {
        toast(context, "Sign in to save likes to YouTube")
        return
    }
    val action = when (liked) {
        true -> "like"
        false -> "dislike"
        null -> "removelike"
    }
    AppGraph.scope.launch { runCatching { AppGraph.account.rate(id, action) } }
}

/** Comments for a Short, in a sheet over the video. */
@Composable
private fun ShortComments(item: VideoItem, onDismiss: () -> Unit) {
    val state by produceState<Pair<List<Comment>, String?>?>(null, item.id) {
        value = runCatching { AppGraph.repo.comments("https://www.youtube.com/watch?v=${item.id}").items }
            .fold({ it to null }, { emptyList<Comment>() to NetworkInfo.friendlyError(it) })
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)) {
        Text("Comments", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
        HorizontalDivider(Modifier.padding(top = 12.dp))
        LazyColumn(Modifier.fillMaxHeight(0.7f)) {
            val s = state
            when {
                s == null -> item { CenterLoading() }
                s.second != null -> item { Text(s.second!!, modifier = Modifier.padding(16.dp)) }
                s.first.isEmpty() -> item { Text("No comments yet", modifier = Modifier.padding(16.dp)) }
                else -> itemsIndexed(s.first) { _, c ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Avatar(c.avatar, 28.dp)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(listOfNotNull(c.author, c.date).joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val text = remember(c.html) { runCatching { AnnotatedString.fromHtml(c.html) }.getOrElse { AnnotatedString(c.html) } }
                            Text(text, style = MaterialTheme.typography.bodyMedium)
                            io.github.shoalapps.swordfish.ui.player.CommentThumbs(item.id, c)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
