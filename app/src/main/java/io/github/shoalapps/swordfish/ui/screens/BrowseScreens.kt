@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.shoalapps.swordfish.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import io.github.shoalapps.common.ui.LocalisedIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import io.github.shoalapps.common.ui.LocalisedText as Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import io.github.shoalapps.swordfish.AppGraph
import io.github.shoalapps.swordfish.data.ChannelDetails
import io.github.shoalapps.swordfish.data.Entry
import io.github.shoalapps.swordfish.data.Sub
import io.github.shoalapps.swordfish.data.VideoItem
import io.github.shoalapps.swordfish.data.formatCount
import io.github.shoalapps.swordfish.ui.components.Avatar
import io.github.shoalapps.swordfish.ui.components.CenterLoading
import io.github.shoalapps.swordfish.ui.components.ChannelRow
import io.github.shoalapps.swordfish.ui.components.ErrorState
import io.github.shoalapps.swordfish.ui.components.LocalBottomPadding
import io.github.shoalapps.swordfish.ui.components.SkeletonCards
import io.github.shoalapps.swordfish.ui.components.SubscribeButton
import io.github.shoalapps.swordfish.ui.components.VideoCard
import io.github.shoalapps.swordfish.ui.components.listPadding
import io.github.shoalapps.swordfish.ui.components.feedColumns
import io.github.shoalapps.swordfish.ui.components.videoGrid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

// ---------------- Search ----------------

class SearchViewModel : ViewModel() {
    var query by mutableStateOf("")
        private set
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    var submitted by mutableStateOf<String?>(null)
        private set
    var results by mutableStateOf<List<Entry>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var moreError by mutableStateOf(false)
        private set
    /** "", "videos" or "channels" */
    var filter by mutableStateOf("")
        private set
    private var next: Page? = null
    private var suggestJob: Job? = null
    private var searchJob: Job? = null
    private var moreJob: Job? = null
    private var generation = 0

    fun chooseFilter(f: String) {
        if (f == filter) return
        filter = f
        submitted?.let { submit(it) }
    }

    fun onQuery(q: String) {
        query = q
        submitted = null
        generation++
        searchJob?.cancel()
        moreJob?.cancel()
        loading = false; loadingMore = false; next = null
        suggestJob?.cancel()
        suggestions = emptyList()
        if (q.isBlank()) return
        if (io.github.shoalapps.swordfish.player.NetworkInfo.offline()) { suggestions = emptyList(); return }
        suggestJob = viewModelScope.launch {
            delay(200)
            val found = AppGraph.repo.suggestions(q)
            if (query == q && submitted == null) suggestions = found
        }
    }

    fun submit(q: String = query) {
        if (q.isBlank()) return
        query = q
        submitted = q
        AppGraph.store.addSearch(q)
        suggestJob?.cancel()
        generation++
        val request = generation
        val selectedFilter = filter
        searchJob?.cancel()
        moreJob?.cancel()
        loadingMore = false; moreError = false
        results = emptyList(); error = null; next = null; loading = true
        searchJob = viewModelScope.launch {
            try {
                if (io.github.shoalapps.swordfish.player.NetworkInfo.offline()) {
                    results = if (selectedFilter == "channels") emptyList() else io.github.shoalapps.swordfish.data.FeedFilter.apply(AppGraph.offline.videos.value.filter {
                        it.complete && (it.video.title.contains(q, true) || it.video.channelName.contains(q, true))
                    }.map { it.video }).map { io.github.shoalapps.swordfish.data.Entry.Video(it) }
                    return@launch
                }
                val page = AppGraph.repo.search(q, filter = selectedFilter)
                if (request != generation) return@launch
                results = io.github.shoalapps.swordfish.data.FeedFilter.entries(page.items)
                next = page.next
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (request == generation) error = io.github.shoalapps.swordfish.player.NetworkInfo.friendlyError(e)
            } finally {
                if (request == generation) loading = false
            }
        }
    }

    fun loadMore() {
        val q = submitted ?: return
        val page = next ?: return
        if (loadingMore || loading) return
        val request = generation
        val selectedFilter = filter
        loadingMore = true; moreError = false
        moreJob = viewModelScope.launch {
            try {
                val result = AppGraph.repo.search(q, page, selectedFilter)
                if (request != generation) return@launch
                results = io.github.shoalapps.swordfish.data.FeedFilter.entries(results + result.items)
                next = result.next
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (request == generation) moreError = true
            } finally {
                if (request == generation) loadingMore = false
            }
        }
    }
}

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onVideo: (VideoItem) -> Unit,
    onChannel: (String) -> Unit,
    vm: SearchViewModel = viewModel(),
) {
    val focus = remember { FocusRequester() }
    val columns = feedColumns()
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { if (vm.submitted == null) focus.requestFocus() }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            // Material 3's search bar field, kept in step with the view model's query.
            val text = rememberTextFieldState(vm.query)
            val barState = androidx.compose.material3.rememberSearchBarState()
            LaunchedEffect(text) {
                snapshotFlow { text.text.toString() }.collect { if (it != vm.query) vm.onQuery(it) }
            }
            LaunchedEffect(vm.query) {
                if (text.text.toString() != vm.query) text.setTextAndPlaceCursorAtEnd(vm.query)
            }
            Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                androidx.compose.material3.SearchBarDefaults.InputField(
                    textFieldState = text,
                    searchBarState = barState,
                    onSearch = { vm.submit(it); focusManager.clearFocus() },
                    placeholder = { Text("Search YouTube") },
                    leadingIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                    trailingIcon = {
                        if (vm.query.isNotEmpty()) {
                            IconButton(onClick = { vm.onQuery(""); focus.requestFocus() }) { Icon(Icons.Rounded.Close, "Clear") }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        },
    ) { pad ->
        AnimatedContent(
            targetState = vm.submitted == null,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
            label = "search",
        ) { typing ->
            if (typing) {
                val history by AppGraph.store.searches.collectAsState()
                LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(LocalBottomPadding.current)) {
                    // Recent searches show first, like the official app.
                    val recent = if (vm.query.isBlank()) history else history.filter { it.contains(vm.query.trim(), true) }.take(3)
                    items(recent, key = { "h:$it" }) { s ->
                        ListItem(
                            headlineContent = { Text(s, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Icon(Icons.Rounded.History, null) },
                            trailingContent = {
                                IconButton(onClick = { AppGraph.store.removeSearch(s) }) { Icon(Icons.Rounded.Close, "Remove") }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { vm.submit(s); focusManager.clearFocus() },
                        )
                    }
                    items(vm.suggestions.filterNot { it in recent }) { s ->
                        ListItem(
                            headlineContent = { Text(s, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Icon(Icons.Rounded.Search, null) },
                            trailingContent = {
                                IconButton(onClick = { vm.onQuery("$s ") }) { Icon(Icons.Rounded.NorthWest, "Fill") }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { vm.submit(s); focusManager.clearFocus() },
                        )
                    }
                }
            } else {
                val listState = io.github.shoalapps.swordfish.ui.components.rememberCachedListState()
                LaunchedEffect(listState) {
                    snapshotFlow {
                        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        last >= listState.layoutInfo.totalItemsCount - 4
                    }.distinctUntilChanged().filter { it }.collect { vm.loadMore() }
                }
                // Wide screens get a readable list (thumbnail beside the details), like the official tablet search.
                LazyColumn(
                    Modifier.fillMaxSize().then(if (columns > 1) Modifier.wrapContentWidth().widthIn(max = 1000.dp) else Modifier),
                    state = listState,
                    contentPadding = listPadding(LocalBottomPadding.current),
                ) {
                    item(key = "filters") {
                        androidx.compose.foundation.lazy.LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(listOf("" to "All", "videos" to "Videos", "channels" to "Channels")) { (key, label) ->
                                androidx.compose.material3.FilterChip(
                                    selected = vm.filter == key,
                                    onClick = { vm.chooseFilter(key) },
                                    label = { Text(label) },
                                    shape = RoundedCornerShape(8.dp),
                                )
                            }
                        }
                    }
                    when {
                        vm.loading -> item { SkeletonCards() }
                        vm.error != null -> item { ErrorState(vm.error!!, { vm.submit() }) }
                        vm.results.isEmpty() -> item {
                            Text(if (io.github.shoalapps.swordfish.player.NetworkInfo.offline()) "No downloaded videos match this search." else "No results found.", modifier = Modifier.padding(24.dp))
                        }
                        else -> {
                            var run = mutableListOf<VideoItem>()
                            val flush = {
                                val videos = run
                                if (videos.isNotEmpty()) {
                                    if (columns > 1) {
                                        videos.forEach { v ->
                                            item(key = "v" + v.id) {
                                                io.github.shoalapps.swordfish.ui.components.VideoRow(v, { onVideo(v) }, Modifier.animateItem(), large = true)
                                            }
                                        }
                                    } else {
                                        videoGrid(videos, 1, keyPrefix = "v") { v, _, mod -> VideoCard(v, { onVideo(v) }, onChannel, mod) }
                                    }
                                }
                                run = mutableListOf()
                            }
                            vm.results.forEach { e ->
                                when (e) {
                                    is Entry.Video -> run += e.item
                                    is Entry.Channel -> {
                                        flush()
                                        item(key = "c" + e.item.url) { ChannelRow(e.item) { onChannel(e.item.url) } }
                                    }
                                }
                            }
                            flush()
                            if (vm.loadingMore) item { CenterLoading() }
                            if (vm.moreError) item { ErrorState("Couldn't load more results.", vm::loadMore) }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Channel ----------------

class ChannelViewModel(private val url: String) : ViewModel() {
    var details by mutableStateOf<ChannelDetails?>(null)
        private set
    var videos by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var next: Page? = null

    init { load() }

    fun load() {
        loading = true; error = null
        viewModelScope.launch {
            try {
                if (io.github.shoalapps.swordfish.player.NetworkInfo.offline()) {
                    videos = io.github.shoalapps.swordfish.data.FeedFilter.apply(AppGraph.offline.videos.value.filter {
                        it.complete && it.video.channelUrl == url
                    }.map { it.video })
                    val first = videos.firstOrNull()
                    details = io.github.shoalapps.swordfish.data.ChannelDetails(url, first?.channelName ?: "Downloaded channel",
                        first?.channelAvatar, null, -1, "Downloaded videos available offline", null)
                    return@launch
                }
                val d = AppGraph.repo.channel(url)
                details = d
                d.videosTab?.let { tab ->
                    val page = AppGraph.repo.channelVideos(tab)
                    videos = page.items.distinctBy { v -> v.id }
                    next = page.next
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = io.github.shoalapps.swordfish.player.NetworkInfo.friendlyError(e)
            } finally {
                loading = false
            }
        }
    }

    fun loadMore() {
        val tab = details?.videosTab ?: return
        val page = next ?: return
        if (loadingMore) return
        loadingMore = true
        viewModelScope.launch {
            runCatching { AppGraph.repo.channelVideos(tab, page) }.onSuccess {
                videos = (videos + it.items).distinctBy { v -> v.id }
                next = it.next
            }
            loadingMore = false
        }
    }
}

@Composable
fun ChannelScreen(
    url: String,
    onBack: () -> Unit,
    onVideo: (VideoItem) -> Unit,
    vm: ChannelViewModel = viewModel(key = url) { ChannelViewModel(url) },
) {
    val subs by AppGraph.store.subs.collectAsState()
    val columns = feedColumns()
    val d = vm.details
    val listState = io.github.shoalapps.swordfish.ui.components.rememberCachedListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd) { if (nearEnd) vm.loadMore() }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(d?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
            state = listState,
            contentPadding = listPadding(LocalBottomPadding.current),
        ) {
            if (d != null) {
                item(key = "header") {
                    Column {
                        if (d.banner != null) {
                            AsyncImage(
                                model = d.banner,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .fillMaxWidth()
                                    .aspectRatio(4.2f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            io.github.shoalapps.swordfish.ui.components.CookieAvatar(d.avatar, 80.dp)
                            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                                Text(d.name, localise = false, style = MaterialTheme.typography.headlineSmall, maxLines = 2)
                                if (d.subscribers >= 0) {
                                    Text(
                                        "${formatCount(d.subscribers)} subscribers",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (!d.description.isNullOrBlank()) {
                            Text(
                                d.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        Box(Modifier.fillMaxWidth().padding(16.dp)) {
                            io.github.shoalapps.swordfish.ui.components.ChannelSubscribe(Sub(d.url, d.name, d.avatar))
                        }
                    }
                }
            }
            when {
                vm.loading -> item { SkeletonCards() }
                vm.error != null -> item { ErrorState(vm.error!!, vm::load) }
                else -> {
                    videoGrid(vm.videos, columns) { v, _, mod ->
                        VideoCard(v, { onVideo(v) }, null, mod, inGrid = columns > 1)
                    }
                    if (vm.loadingMore) item { CenterLoading() }
                }
            }
        }
    }
}
