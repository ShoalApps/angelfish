@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.shoalapps.swordfish.ui.screens

import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import io.github.shoalapps.swordfish.LocalisedIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import io.github.shoalapps.swordfish.LocalisedText as Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.MaterialShapes
import io.github.shoalapps.swordfish.ui.components.CookieAvatar
import io.github.shoalapps.swordfish.ui.components.ShapeIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.shoalapps.swordfish.AppGraph
import io.github.shoalapps.swordfish.data.Entry
import io.github.shoalapps.swordfish.data.VideoItem
import io.github.shoalapps.swordfish.ui.components.Avatar
import io.github.shoalapps.swordfish.ui.components.EmptyState
import io.github.shoalapps.swordfish.ui.components.ErrorState
import io.github.shoalapps.swordfish.ui.components.LocalBottomPadding
import io.github.shoalapps.swordfish.ui.components.ShelfCard
import io.github.shoalapps.swordfish.ui.components.SkeletonCards
import io.github.shoalapps.swordfish.ui.components.VideoCard
import io.github.shoalapps.swordfish.ui.components.listPadding
import io.github.shoalapps.swordfish.ui.components.feedColumns
import io.github.shoalapps.swordfish.ui.components.videoGrid
import io.github.shoalapps.swordfish.ui.components.staggeredReveal
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.shoalapps.swordfish.data.FeedFilter
import io.github.shoalapps.swordfish.data.LearningTopics
import io.github.shoalapps.swordfish.ui.components.PolygonShape
import io.github.shoalapps.swordfish.ui.components.VideoRow
import io.github.shoalapps.swordfish.ui.components.rememberCachedListState
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// ---------------- Home ----------------

val homeChips = listOf("All", "Music", "Gaming", "Podcasts", "Live", "Films", "News", "Sport", "Tech", "Comedy")

/** Chips backed by YouTube's own charts rather than a search. */
private val chipKiosks = mapOf(
    "Music" to "trending_music",
    "Gaming" to "trending_gaming",
    "Podcasts" to "trending_podcasts_episodes",
    "Live" to "live",
    "Films" to "trending_movies_and_shows",
)

class HomeViewModel : ViewModel() {
    var chip by mutableStateOf("All")
        private set
    var items by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var shorts by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var generation by mutableStateOf(0)
        private set
    private var job: Job? = null
    private var loadedKey = feedKey()
    private var seed = kotlin.random.Random.nextInt(Int.MAX_VALUE)
    private var previous: List<VideoItem> = emptyList()

    /** Changes to any of these mean the feed must be rebuilt. */
    private fun feedKey(): String {
        val s = AppGraph.store.settings.value
        return listOf(AppGraph.store.signedIn, AppGraph.store.pageId.value, s.feedMode, s.learningTopics.sorted(), s.shortsDisabled).toString()
    }

    val learning get() = AppGraph.store.settings.value.feedMode == "learning"

    fun chips(): List<String> =
        if (learning) listOf("All") + AppGraph.store.settings.value.learningTopics.mapNotNull { LearningTopics.of(it)?.label }
        else homeChips

    fun select(c: String) {
        if (c == chip && items.isNotEmpty()) return
        chip = c
        load()
    }

    fun refresh() { seed = (seed + 1) and Int.MAX_VALUE; load(pull = true) }

    fun reloadIfChanged() {
        if (loadedKey != feedKey()) { chip = "All"; load() }
    }

    private fun load(pull: Boolean = false) {
        job?.cancel()
        loadedKey = feedKey()
        if (pull) refreshing = true else { loading = true; items = emptyList() }
        error = null
        job = viewModelScope.launch {
            try {
                if (io.github.shoalapps.swordfish.player.NetworkInfo.offline()) {
                    items = FeedFilter.apply(AppGraph.offline.videos.value.filter { it.complete }.map { it.video })
                    shorts = emptyList()
                    return@launch
                }
                val s = AppGraph.store.settings.value
                val wantShorts = !s.shortsDisabled && !s.hideShortsShelf && !learning && chip == "All" && io.github.shoalapps.swordfish.data.NetworkPolicy.backgroundAllowed()
                val shortsJob = if (wantShorts && !AppGraph.store.signedIn) {
                    async { runCatching { AppGraph.repo.shorts(AppGraph.store.subs.value) }.getOrDefault(emptyList()) }
                } else null
                var list: List<VideoItem> = emptyList()
                // The phone's connection can take a moment after launch, so retry a couple of times.
                for (attempt in 0..2) {
                    try {
                        list = when {
                            learning && chip == "All" -> AppGraph.repo.learningFeed(s.learningTopics, seed)
                            learning -> LearningTopics.all.firstOrNull { it.label == chip }?.let { AppGraph.repo.topicFeed(it.key) } ?: emptyList()
                            chip == "All" -> allFeed(wantShorts)
                            chip in chipKiosks -> AppGraph.repo.kiosk(chipKiosks.getValue(chip)).ifEmpty { search(chip) }
                            else -> search(chip)
                        }
                        break
                    } catch (e: java.io.IOException) {
                        if (attempt == 2) throw e
                        delay(2500L * (attempt + 1))
                    }
                }
                items = io.github.shoalapps.swordfish.data.FreshFeed.order(FeedFilter.apply(list), previous, seed)
                previous = items
                // Preload the first few videos so tapping them starts instantly.
                AppGraph.scope.launch { AppGraph.repo.prefetch(items.take(io.github.shoalapps.swordfish.player.NetworkPace.preloadCount()).map { it.id }) }
                shortsJob?.let { shorts = FeedFilter.apply(it.await()).take(12) }
                if (!wantShorts) shorts = emptyList()
                generation++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = io.github.shoalapps.swordfish.player.NetworkInfo.friendlyError(e)
            } finally {
                loading = false
                refreshing = false
                io.github.shoalapps.swordfish.ui.StartupState.homeReady = true
            }
        }
    }

    private suspend fun allFeed(wantShorts: Boolean): List<VideoItem> {
        if (AppGraph.store.signedIn) {
            val feed = runCatching { AppGraph.account.home() }.getOrNull()
            if (feed != null && feed.videos.isNotEmpty()) {
                if (wantShorts) shorts = FeedFilter.apply(feed.shorts).take(12)
                return feed.videos
            }
        }
        val discover = AppGraph.repo.discoverFeed(AppGraph.store.subs.value)
        return discover.ifEmpty { search("popular videos") }
    }

    private suspend fun search(q: String) =
        AppGraph.repo.search(q).items.filterIsInstance<Entry.Video>().map { it.item }.filterNot { it.isShort }
}

@Composable
fun HomeScreen(
    onVideo: (VideoItem) -> Unit,
    onChannel: (String) -> Unit,
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    onShorts: (VideoItem) -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val cookie by AppGraph.store.cookie.collectAsState()
    val pageId by AppGraph.store.pageId.collectAsState()
    val settings by AppGraph.store.settings.collectAsState()
    val watchPositions by AppGraph.store.watchPositions.collectAsState()
    val continuing = io.github.shoalapps.swordfish.data.WatchPositions(watchPositions).continuing
    LaunchedEffect(cookie, pageId, settings.feedMode, settings.learningTopics, settings.shortsDisabled) { vm.reloadIfChanged() }
    // Back online after a failed load: try again without making the user tap.
    val online by AppGraph.online.collectAsState()
    LaunchedEffect(online) { vm.refresh() }
    var showFeedSheet by remember { mutableStateOf(false) }
    val columns = feedColumns()
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val listState = rememberCachedListState()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                scrollBehavior = scroll,
                title = { AppLogo() },
                actions = {
                    IconButton(onClick = onNotifications) { Icon(Icons.Outlined.Notifications, "Notifications") }
                    IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Search") }
                },
            )
        },
    ) { pad ->
        val ptr = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = vm.refreshing,
            onRefresh = vm::refresh,
            state = ptr,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(state = ptr, isRefreshing = vm.refreshing, modifier = Modifier.align(Alignment.TopCenter))
            },
            modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
        ) {
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = listPadding(LocalBottomPadding.current)) {
                item(key = "offline-status") { io.github.shoalapps.swordfish.ui.components.OfflineStatus() }
                if (settings.rememberWatchPositions && continuing.isNotEmpty()) item(key = "continue-watching") {
                    ContinueWatchingRow(continuing, onVideo)
                }
                item(key = "chips") {
                    Chips(vm.chips(), vm.chip, vm::select, onExplore = { showFeedSheet = true }, learning = vm.learning)
                }
                when {
                    vm.loading -> item(key = "loading") { SkeletonCards() }
                    vm.error != null -> item(key = "error") { ErrorState(vm.error!!, vm::refresh) }
                    vm.items.isEmpty() -> item(key = "empty") {
                        EmptyState(Icons.Rounded.Search, "Nothing to show", "Try another topic, or pull down to refresh.")
                    }
                    else -> {
                        val gen = vm.generation
                        val cols = if (settings.compactFeed) 1 else columns
                        videoGrid(
                            vm.items,
                            cols,
                            keyPrefix = "$gen-",
                            // One Shorts shelf, after the second video (or the first row of a grid), like the official app.
                            between = { row ->
                                if (row == (if (cols > 1) 0 else 1) && vm.shorts.isNotEmpty()) {
                                    item(key = "$gen-shorts") { ShortsShelf(vm.shorts, onShorts) }
                                }
                            },
                        ) { v, _, mod ->
                            if (settings.compactFeed) VideoRow(v, { onVideo(v) }, mod)
                            else VideoCard(v, onClick = { onVideo(v) }, onChannel = onChannel, modifier = mod, inGrid = cols > 1)
                        }
                    }
                }
            }
        }
    }
    if (showFeedSheet) FeedSheet { showFeedSheet = false }
}

/** Small expressive logo: the shoal on the app's scalloped shape, and its name. */
@Composable
fun AppLogo() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val shape = remember { PolygonShape(MaterialShapes.Cookie9Sided) }
        Box(
            Modifier.size(30.dp).clip(shape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            // The launcher icon's fish, in one colour.
            Icon(androidx.compose.ui.res.painterResource(io.github.shoalapps.swordfish.R.drawable.ic_launcher_mono), null, Modifier.requiredSize(50.dp), tint = MaterialTheme.colorScheme.onPrimary)
        }
        Text(
            "Angelfish",
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun Chips(chips: List<String>, selected: String, onSelect: (String) -> Unit, onExplore: () -> Unit, learning: Boolean) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item {
            Surface(
                onClick = onExplore,
                shape = RoundedCornerShape(8.dp),
                color = if (learning) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.height(32.dp),
            ) {
                Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (learning) Icons.Rounded.School else Icons.Outlined.Explore, "Choose feed", Modifier.size(18.dp))
                    if (learning) Text("Learning", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        items(chips) { c ->
            FilterChip(
                selected = c == selected,
                onClick = { onSelect(c) },
                label = { Text(c) },
                shape = RoundedCornerShape(8.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                    selectedLabelColor = MaterialTheme.colorScheme.inverseOnSurface,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                border = null,
            )
        }
    }
}

/** Horizontal row of tall Shorts thumbnails. */
@Composable
private fun ShortsShelf(shorts: List<VideoItem>, onOpen: (VideoItem) -> Unit) {
    Column(Modifier.padding(bottom = 16.dp)) {
        Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.VideoLibrary, null, tint = MaterialTheme.colorScheme.primary)
            Text("Shorts", style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.padding(start = 8.dp))
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(shorts, key = { it.id }) { s ->
                Box(
                    Modifier
                        .width(168.dp)
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { onOpen(s) },
                ) {
                    AsyncImage(model = s.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(110.dp)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))),
                    )
                    Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                        Text(s.title, localise = false, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        s.viewsText?.let { Text(it, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                    }
                }
            }
        }
    }
}

/** Quick switch between the normal feed and the Learning feed, with topic picks. */
@Composable
private fun FeedSheet(onDismiss: () -> Unit) {
    val s by AppGraph.store.settings.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text("Your feed", style = MaterialTheme.typography.titleLargeEmphasized)
            Spacer(Modifier.height(12.dp))
            val modes = listOf("recommended" to "Recommended", "learning" to "Learning")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { i, (key, label) ->
                    SegmentedButton(
                        selected = s.feedMode == key,
                        onClick = { AppGraph.store.updateSettings { it.copy(feedMode = key) } },
                        shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                    ) { Text(label) }
                }
            }
            AnimatedVisibility(s.feedMode == "learning") {
                Column {
                    Text(
                        "Topics",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                    )
                    TopicPicker(s.learningTopics) { t -> AppGraph.store.updateSettings { it.copy(learningTopics = t) } }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopicPicker(selected: Set<String>, onChange: (Set<String>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LearningTopics.all.forEach { t ->
            val on = t.key in selected
            FilterChip(
                selected = on,
                onClick = {
                    val next = if (on) selected - t.key else selected + t.key
                    if (next.isNotEmpty()) onChange(next)
                },
                label = { Text(t.label) },
                leadingIcon = if (on) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null,
            )
        }
    }
}

// ---------------- Subscriptions ----------------

class SubsViewModel : ViewModel() {
    var items by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var loadedFor: Int = -1
    private var job: Job? = null

    fun loadIfNeeded() {
        val key = AppGraph.store.subs.value.hashCode() * 31 + AppGraph.store.signedIn.hashCode()
        if (key != loadedFor) load(false)
    }

    fun load(pull: Boolean) {
        val subs = AppGraph.store.subs.value
        loadedFor = subs.hashCode() * 31 + AppGraph.store.signedIn.hashCode()
        if (pull) refreshing = true else loading = true
        error = null
        job?.cancel()
        job = viewModelScope.launch {
            try {
                if (io.github.shoalapps.swordfish.player.NetworkInfo.offline()) {
                    val urls = subs.map { it.url }.toSet()
                    items = FeedFilter.apply(AppGraph.offline.videos.value.filter { it.complete && it.video.channelUrl in urls }.map { it.video })
                    return@launch
                }
                val fromAccount = if (AppGraph.store.signedIn) {
                    runCatching { AppGraph.account.subscriptionFeed() }.getOrDefault(emptyList())
                } else emptyList()
                val list = fromAccount.ifEmpty { if (subs.isEmpty()) emptyList() else AppGraph.repo.feed(subs) }
                items = FeedFilter.apply(list)
                AppGraph.offline.subscriptions(items)
                // Every channel failing at once means the connection is the problem, not the channels.
                if (items.isEmpty() && subs.isNotEmpty() && io.github.shoalapps.swordfish.player.NetworkInfo.offline()) {
                    error = io.github.shoalapps.swordfish.player.NetworkInfo.friendlyError(java.net.UnknownHostException())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = io.github.shoalapps.swordfish.player.NetworkInfo.friendlyError(e)
            } finally {
                loading = false
                refreshing = false
            }
        }
    }
}

@Composable
fun SubscriptionsScreen(
    onVideo: (VideoItem) -> Unit,
    onChannel: (String) -> Unit,
    onShorts: (VideoItem) -> Unit = {},
    vm: SubsViewModel = viewModel(),
) {
    val subs by AppGraph.store.subs.collectAsState()
    val cookie by AppGraph.store.cookie.collectAsState()
    LaunchedEffect(subs, cookie) { vm.loadIfNeeded() }
    val columns = feedColumns()
    val online by AppGraph.online.collectAsState()
    LaunchedEffect(online) { vm.load(false) }
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Subscriptions") }, scrollBehavior = scroll) },
    ) { pad ->
        val ptr = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = vm.refreshing,
            onRefresh = { vm.load(true) },
            state = ptr,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = ptr,
                    isRefreshing = vm.refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
            modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
        ) {
            LazyColumn(Modifier.fillMaxSize(), state = rememberCachedListState(), contentPadding = listPadding(LocalBottomPadding.current)) {
                if (subs.isEmpty() && !AppGraph.store.signedIn) {
                    item {
                        EmptyState(
                            Icons.Rounded.Subscriptions,
                            "No subscriptions yet",
                            "Sign in from the You tab, or search for creators and tap Subscribe.",
                        )
                    }
                    return@LazyColumn
                }
                if (!AppGraph.store.settings.value.hideSubsAvatars) item(key = "avatars") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(subs, key = { it.url }) { s ->
                            Column(
                                Modifier.width(64.dp).clickable { onChannel(s.url) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Avatar(s.avatar, 56.dp)
                                Text(
                                    s.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
                if (vm.loading) item(key = "loading") { SkeletonCards() }
                else if (vm.error != null) item(key = "error") { ErrorState(vm.error!!, { vm.load(false) }) }
                else if (vm.items.isEmpty()) item(key = "empty") {
                    EmptyState(Icons.Rounded.Subscriptions, "No new videos", "Pull down to check again.")
                }
                else {
                    // Shorts go in their own row, as on Home, rather than as tall thumbnails in the grid.
                    val (shorts, videos) = vm.items.partition { it.isShort }
                    val s = AppGraph.store.settings.value
                    if (shorts.isNotEmpty() && !s.shortsDisabled && !s.hideShortsShelf) {
                        item(key = "shorts") { ShortsShelf(shorts.take(20), onShorts) }
                    }
                    videoGrid(videos, columns) { v, _, mod ->
                        VideoCard(v, onClick = { onVideo(v) }, onChannel = onChannel, modifier = mod, inGrid = columns > 1)
                    }
                }
            }
        }
    }
}

// ---------------- You ----------------

class YouViewModel : ViewModel() {
    var liked by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var watchLater by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var ytHistory by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    private var loadedFor: String? = "none"

    fun load() {
        val cookie = AppGraph.store.cookie.value
        if (cookie == loadedFor) return
        loadedFor = cookie
        if (!AppGraph.store.signedIn) {
            liked = emptyList(); watchLater = emptyList(); ytHistory = emptyList()
            return
        }
        viewModelScope.launch { ytHistory = runCatching { AppGraph.account.history() }.getOrDefault(emptyList()) }
        viewModelScope.launch { liked = runCatching { AppGraph.account.likedVideos() }.getOrDefault(emptyList()) }
        viewModelScope.launch { watchLater = runCatching { AppGraph.account.watchLater() }.getOrDefault(emptyList()) }
    }
}

@Composable
fun YouScreen(
    onVideo: (VideoItem) -> Unit,
    onSettings: () -> Unit,
    onSignIn: () -> Unit,
    onPlaylist: (String) -> Unit,
    onSwitchChannel: () -> Unit,
    onAbout: () -> Unit,
    onLocalPlaylist: (String) -> Unit = {},
    vm: YouViewModel = viewModel(),
) {
    val account by AppGraph.store.account.collectAsState()
    val cookie by AppGraph.store.cookie.collectAsState()
    val localHistory by AppGraph.store.history.collectAsState()
    val localPlaylists by AppGraph.store.playlists.collectAsState()
    val transfer = io.github.shoalapps.swordfish.ui.components.rememberPlaylistTransfer()
    var newPlaylist by remember { mutableStateOf(false) }
    if (newPlaylist) io.github.shoalapps.swordfish.ui.components.PlaylistNameDialog(
        onDismiss = { newPlaylist = false },
        onSave = { name ->
            newPlaylist = false
            AppGraph.store.createPlaylist(name)?.let(onLocalPlaylist)
        },
    )
    LaunchedEffect(cookie) { vm.load() }
    val signedIn = cookie?.contains("SAPISID") == true
    val online by AppGraph.online.collectAsState()
    val downloads by AppGraph.offline.videos.collectAsState()
    val sourceHistory = if (signedIn && vm.ytHistory.isNotEmpty()) vm.ytHistory else localHistory
    val downloadedIds = downloads.filter { it.complete }.map { it.video.id }.toSet()
    val history = if (online) sourceHistory else sourceHistory.filter { it.id in downloadedIds }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings") } },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()).testTag("you-list"),
            contentPadding = listPadding(LocalBottomPadding.current),
        ) {
            item(key = "header") {
                if (signedIn) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CookieAvatar(account?.avatar, 80.dp)
                        Column(Modifier.padding(start = 16.dp).weight(1f)) {
                            Text(account?.name ?: "Signed in", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            account?.handle?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = onSwitchChannel, contentPadding = PaddingValues(0.dp)) {
                                Text("Switch channel")
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        ShapeIcon(Icons.Rounded.AccountCircle, 112.dp, MaterialShapes.Clover8Leaf, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Enjoy your favourite videos", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                        Text(
                            "Sign in to see your feed, subscriptions, likes and Watch later.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                        )
                        Button(onClick = onSignIn) { Text("Sign in") }
                    }
                }
            }

            item(key = "offline-status") { io.github.shoalapps.swordfish.ui.components.OfflineStatus() }
            item(key = "downloads") { io.github.shoalapps.swordfish.ui.components.Downloads(onVideo) }

            item(key = "history-head") {
                SectionHeader("History", if (history.isNotEmpty() && !signedIn) "Clear" else null) { AppGraph.store.clearHistory() }
            }
            item(key = "history") {
                if (history.isEmpty()) {
                    Text(
                        "Videos you watch will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                } else {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(history.take(30), key = { "h" + it.id }) { v -> ShelfCard(v) { onVideo(v) } }
                    }
                }
            }

            item(key = "local-pl-head") { SectionHeader("Playlists", "New playlist") { newPlaylist = true } }
            item(key = "local-pl-note") {
                Text("Saved on this phone", modifier = Modifier.padding(horizontal = 16.dp))
            }
            item(key = "local-pl-files") {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { OutlinedButton(onClick = { transfer.import() }, enabled = !transfer.busy) { Text("Import playlists") } }
                    item { OutlinedButton(onClick = { transfer.export(localPlaylists.map { it.id }) },
                        enabled = !transfer.busy && localPlaylists.isNotEmpty()) { Text("Export all playlists") } }
                }
            }
            items(localPlaylists, key = { "local-pl-${it.id}" }) { playlist ->
                ListItem(
                    headlineContent = { Text(playlist.name, localise = false) },
                    supportingContent = { Text("${playlist.items.size} videos") },
                    leadingContent = { Icon(Icons.Rounded.VideoLibrary, null) },
                    modifier = Modifier.clickable { onLocalPlaylist(playlist.id) },
                )
            }
            if (signedIn) {
                item(key = "pl-head") { SectionHeader("YouTube playlists", null) {} }
                item(key = "pl") {
                    Column {
                        PlaylistEntry(Icons.Rounded.WatchLater, "Watch later", vm.watchLater.size) { onPlaylist("VLWL") }
                        PlaylistEntry(Icons.Rounded.ThumbUp, "Liked videos", vm.liked.size) { onPlaylist("VLLL") }
                    }
                }
            }

            item(key = "links") {
                Column(Modifier.padding(top = 16.dp)) {
                    ListItem(
                        headlineContent = { Text("Settings") },
                        leadingContent = { Icon(Icons.Rounded.Settings, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(onClick = onSettings),
                    )
                    val context = androidx.compose.ui.platform.LocalContext.current
                    ListItem(
                        headlineContent = { Text("TV mode") },
                        supportingContent = { Text("For big screens, remotes and controllers") },
                        leadingContent = { Icon(Icons.Rounded.Tv, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { io.github.shoalapps.swordfish.tv.TvActivity.open(context) },
                    )
                    ListItem(
                        headlineContent = { Text("About Angelfish") },
                        leadingContent = { Icon(Icons.Outlined.Info, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(onClick = onAbout),
                    )
                    if (signedIn) {
                        ListItem(
                            headlineContent = { Text("Sign out") },
                            leadingContent = { Icon(Icons.AutoMirrored.Rounded.Logout, null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { AppGraph.signOut() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String?, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
        if (action != null) OutlinedButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun PlaylistEntry(icon: ImageVector, title: String, count: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { if (count > 0) Text("$count videos") },
        leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

// ---------------- A signed-in playlist (Watch later, Liked) ----------------

class PlaylistViewModel(private val browseId: String) : ViewModel() {
    var items by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun load() {
        loading = true; error = null
        viewModelScope.launch {
            runCatching { AppGraph.account.playlist(browseId) }
                .onSuccess { items = it }
                .onFailure { error = io.github.shoalapps.swordfish.player.NetworkInfo.friendlyError(it) }
            loading = false
        }
    }
}

@Composable
fun PlaylistScreen(
    browseId: String,
    onBack: () -> Unit,
    onVideo: (VideoItem) -> Unit,
    vm: PlaylistViewModel = viewModel(key = browseId) { PlaylistViewModel(browseId) },
) {
    val online by AppGraph.online.collectAsState()
    LaunchedEffect(online) { vm.load() }
    val title = when (browseId) {
        "VLWL" -> "Watch later"
        "VLLL" -> "Liked videos"
        else -> "Playlist"
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
            contentPadding = listPadding(LocalBottomPadding.current),
        ) {
            when {
                vm.loading -> item { SkeletonCards() }
                vm.error != null -> item { ErrorState(vm.error!!, vm::load) }
                vm.items.isEmpty() -> item { EmptyState(Icons.Rounded.History, "Nothing here yet", "Videos you add will show up here.") }
                else -> itemsIndexed(vm.items, key = { _, v -> v.id }) { i, v ->
                    io.github.shoalapps.swordfish.ui.components.VideoRow(v, { onVideo(v) }, Modifier.animateItem().staggeredReveal(i))
                }
            }
        }
    }
}

/** Uses Coil's ordinary thumbnail cache directly, without fetching DeArrow branding. */
@Composable
internal fun ContinueWatchingRow(
    positions: List<io.github.shoalapps.swordfish.data.WatchPosition>,
    onVideo: (VideoItem) -> Unit,
) {
    Column(Modifier.testTag("continue-watching")) {
        SectionHeader("Continue watching", null) {}
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(positions, key = { it.video.id }) { entry ->
                val video = entry.video
                var menu by remember { mutableStateOf(false) }
                Column(Modifier.width(180.dp)) {
                    Column(Modifier.clickable { onVideo(video) }) {
                        AsyncImage(
                            model = video.thumbnail,
                            contentDescription = video.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)),
                        )
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { (entry.positionMs.toFloat() / entry.durationMs).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                        Text(video.title, localise = false, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(video.channelName, localise = false, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(androidx.compose.material.icons.Icons.Rounded.MoreVert, "Watch position options")
                            }
                            androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Remove from Continue watching") },
                                    onClick = { AppGraph.store.removeWatchPosition(video.id); menu = false },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
