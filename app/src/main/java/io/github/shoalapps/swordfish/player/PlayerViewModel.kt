package io.github.shoalapps.swordfish.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import io.github.shoalapps.swordfish.AppGraph
import io.github.shoalapps.swordfish.data.Comment
import io.github.shoalapps.swordfish.data.RydVotes
import io.github.shoalapps.swordfish.data.SbSegment
import io.github.shoalapps.swordfish.data.VideoItem
import io.github.shoalapps.swordfish.data.toVideo
import io.github.shoalapps.swordfish.data.videoIdOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** Your rating of the current video. */
enum class Rating { NONE, LIKE, DISLIKE }

class PlayerViewModel : ViewModel() {
    val player = PlayerHolder.player
    private val store = AppGraph.store

    // What's loaded
    var current by mutableStateOf<VideoItem?>(null)
        private set
    var info by mutableStateOf<StreamInfo?>(null)
        private set
    var related by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /**
     * YouTube refused the video to every client the app can use, even the Kids one (some
     * made-for-kids videos), so the error offers to open it in YouTube's own app.
     */
    var onlyYouTubeCanPlay by mutableStateOf(false)
        private set
    var segments by mutableStateOf<List<SbSegment>>(emptyList())
        private set
    var votes by mutableStateOf<RydVotes?>(null)
        private set
    var rating by mutableStateOf(Rating.NONE)
        private set
    var qualities by mutableStateOf<List<Int>>(emptyList())
        private set
    var quality by mutableIntStateOf(1080)
        private set
    var audioTracks by mutableStateOf<List<AudioChoice>>(emptyList())
        private set
    var audioTrackId by mutableStateOf<String?>(null)
        private set
    var speed by mutableFloatStateOf(AppGraph.store.settings.value.defaultSpeed)
        private set

    // Captions, chapters, sleep timer
    var captionTracks by mutableStateOf<List<CaptionTrack>>(emptyList())
        private set
    var captionTrack by mutableStateOf<CaptionTrack?>(null)
        private set
    private var cues: List<Cue> = emptyList()
    var captionText by mutableStateOf<String?>(null)
        private set
    var chapters by mutableStateOf<List<Chapter>>(emptyList())
        private set
    var chapterIndex by mutableIntStateOf(-1)
        private set
    /** Minutes chosen for the sleep timer: 0 off, -1 end of video. */
    var sleepMinutes by mutableIntStateOf(0)
        private set
    private var sleepAtMs = 0L

    // Comments
    var comments by mutableStateOf<List<Comment>>(emptyList())
        private set
    var commentsLoading by mutableStateOf(false)
        private set
    private var commentsNext: Page? = null

    // Live player state
    var isPlaying by mutableStateOf(false)
        private set
    var buffering by mutableStateOf(false)
        private set
    var firstFrame by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var bufferedMs by mutableLongStateOf(0L)
        private set
    var aspect by mutableFloatStateOf(16f / 9f)
        private set

    // SponsorBlock "Skipped" pill
    var skipped by mutableStateOf<SbSegment?>(null)
        private set
    private val skippedIds = mutableSetOf<String>()

    // Sheet: 0 = mini player, 1 = full screen watch page
    val expand = Animatable(0f)
    var sheetVisible by mutableStateOf(false)
        private set
    var sheetTarget by mutableFloatStateOf(1f)
        private set
    var sheetNonce by mutableIntStateOf(0)
        private set
    var fullscreen by mutableStateOf(false)

    private var loadJob: Job? = null
    private var usingLocal = false
    private var nextJob: Job? = null
    private var queuedPlayback = false
    private var queueFailures = 0
    private var endHandled = false
    var queueMessage by mutableStateOf<String?>(null)
        private set

    private fun queueNotice(message: String) {
        queueMessage = message
        android.widget.Toast.makeText(AppGraph.app, message, android.widget.Toast.LENGTH_LONG).show()
    }

    /** Shared by the player, playback end, and the media notification. */
    fun next() {
        nextJob?.cancel()
        var skippedOffline = false
        while (true) {
            val item = store.advanceQueue() ?: break
            if (NetworkInfo.offline() && AppGraph.offline.local(item.id) == null) {
                skippedOffline = true
                queueNotice("Skipped ${item.title.ifBlank { "video" }}: not saved for offline playback.")
                continue
            }
            open(item, expandSheet = false, fromQueue = true)
            return
        }
        if (skippedOffline) {
            loadJob?.cancel()
            loading = false
            player.stop()
            error = queueMessage
        }
        if (store.settings.value.autoplayNext && !NetworkInfo.offline()) autoplayNext(manual = true)
    }

    fun jumpToQueue(item: VideoItem) {
        store.removeFromQueue(item.id)
        if (NetworkInfo.offline() && AppGraph.offline.local(item.id) == null) {
            queueNotice("Skipped ${item.title.ifBlank { "video" }}: not saved for offline playback.")
            next()
        } else open(item, expandSheet = false, fromQueue = true)
    }

    private val listener = object : Player.Listener {
        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            if (!usingLocal && playing?.hls == null) return
            val choices = mutableListOf<AudioChoice>()
            tracks.groups.forEachIndexed { groupIndex, group ->
                if (group.type != androidx.media3.common.C.TRACK_TYPE_AUDIO) return@forEachIndexed
                for (trackIndex in 0 until group.length) {
                    if (!group.isTrackSupported(trackIndex)) continue
                    val format = group.getTrackFormat(trackIndex)
                    val id = "media3:$groupIndex:$trackIndex"
                    val language = format.language?.takeUnless { it == "und" }
                    choices += AudioChoice(id, format.label ?: language?.let {
                        java.util.Locale.forLanguageTag(it).getDisplayName(java.util.Locale.getDefault())
                    } ?: if (usingLocal) "Downloaded audio" else "Audio ${choices.size + 1}", language)
                    if (group.isTrackSelected(trackIndex)) audioTrackId = id
                }
            }
            audioTracks = choices
        }
        override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        override fun onPlaybackStateChanged(state: Int) {
            buffering = state == Player.STATE_BUFFERING
            if (state == Player.STATE_READY) { durationMs = player.duration.coerceAtLeast(0); queueFailures = 0 }
            if (state == Player.STATE_ENDED) onEnded()
        }
        override fun onVideoSizeChanged(size: VideoSize) {
            if (size.width > 0 && size.height > 0) {
                aspect = size.width * size.pixelWidthHeightRatio / size.height
            }
        }
        override fun onRenderedFirstFrame() { firstFrame = true }
        override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
            if (reason != Player.DISCONTINUITY_REASON_SEEK) return
            endHandled = false
            // Going back into a segment means you want to watch it: don't skip it, and load it.
            val pos = new.positionMs / 1000.0
            segments.firstOrNull { pos >= it.startSec && pos < it.endSec - 0.4 }?.let { skippedIds += it.uuid }
            planSkips()
        }
        override fun onPlayerError(e: PlaybackException) = onPlaybackFailed(e)
    }

    init {
        player.addListener(listener)
        viewModelScope.launch {
            io.github.shoalapps.swordfish.data.NetworkPolicy.changes.collect {
                val i = info
                val q = preferredQuality()
                if (!usingLocal && !loading && i != null && quality != q) {
                    val resume = player.currentPosition
                    val wantedPlay = player.playWhenReady
                    quality = q
                    play(i, resume)
                    player.playWhenReady = wantedPlay
                }
            }
        }
        viewModelScope.launch {
            AppGraph.offline.videos.collect {
                val video = current ?: return@collect
                if (!usingLocal && !loading && AppGraph.offline.local(video.id) != null) {
                    val resume = player.currentPosition.coerceAtLeast(0)
                    val playingNow = player.playWhenReady
                    playLocal(video.id, resume, playingNow)
                    error = null
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                // Nothing to track while no video is open (but keep skipping while it plays in the background).
                if (sheetVisible || player.playWhenReady) tick()
                delay(200)
            }
        }
    }

    private fun tick() {
        positionMs = player.currentPosition
        bufferedMs = player.bufferedPosition
        if (io.github.shoalapps.swordfish.BuildConfig.DEBUG && positionMs / 1000 != lastLoggedSec) {
            lastLoggedSec = positionMs / 1000
            android.util.Log.d("SkipAware", "at ${positionMs / 1000.0}s buffered to ${bufferedMs / 1000.0}s, gate ${SkipAware.gateUs.let { if (it == Long.MAX_VALUE) "none" else (it / 1_000_000.0).toString() }}s, state ${player.playbackState}")
        }
        if (player.duration > 0) durationMs = player.duration
        if (cues.isNotEmpty()) {
            val text = cues.lastOrNull { positionMs in it.startMs until it.endMs }?.text
            if (text != captionText) captionText = text
        }
        if (chapters.isNotEmpty()) {
            val idx = chapters.indexOfLast { it.startMs <= positionMs }
            if (idx != chapterIndex) chapterIndex = idx
        }
        if (sleepMinutes > 0 && System.currentTimeMillis() >= sleepAtMs) {
            player.pause()
            sleepMinutes = 0
        }
        // While wanting to play, not just playing: loading stops at a segment, so the player may be
        // waiting there for the skip.
        if (!player.playWhenReady) return
        val pos = positionMs / 1000.0
        val seg = segments.firstOrNull {
            it.uuid !in skippedIds && pos >= it.startSec && pos < it.endSec - 0.4
        } ?: return
        skippedIds += seg.uuid
        val dur = player.duration
        if (dur > 0 && seg.endSec * 1000 >= dur - 1500) {
            // It runs to the end (an outro): that's the end of the video, so finish as if it ended,
            // rather than jumping past the last frame (which YouTube has nothing to send for).
            player.pause()
            endedAtOutro = true
            val finishedId = current?.id
            onEnded()
            if (current?.id != finishedId) return
        } else player.seekTo((seg.endSec * 1000).toLong())
        planSkips()
        skipped = seg
        viewModelScope.launch {
            delay(4000)
            if (skipped == seg) skipped = null
        }
    }

    private fun onEnded() {
        if (endHandled || loading || current == null) return
        endHandled = true
        val s = store.settings.value
        if (sleepMinutes == -1) {
            player.pause()
            sleepMinutes = 0
            return
        }
        when {
            store.queue.value.isNotEmpty() -> next()
            s.loopVideo -> { endHandled = false; player.seekTo(0); player.play() }
            s.autoplayNext && !fullscreen && sheetVisible && current != null -> autoplayNext()
        }
    }

    /** A stream fetched while choosing what to autoplay, so opening it doesn't fetch it again. */
    private var prefetched: Pair<String, StreamInfo>? = null

    /**
     * Plays the first suggestion that will actually play. Some (often kids videos) only play in
     * YouTube's own apps; those are skipped quietly rather than ending the video on an error.
     * If none of the first few play, it stays on the finished video.
     */
    private fun autoplayNext(manual: Boolean = false) {
        if (NetworkInfo.offline() || io.github.shoalapps.swordfish.data.NetworkPolicy.savingData()) return
        val candidates = related.take(5)
        nextJob?.cancel()
        nextJob = viewModelScope.launch {
            for (c in candidates) {
                val i = try {
                    streamWithRetry(c.url)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    continue
                }
                // You picked something yourself (or pressed play again) while this was being chosen.
                if (!manual && player.playbackState != Player.STATE_ENDED && !(endedAtOutro && !player.playWhenReady)) return@launch
                if (store.queue.value.isNotEmpty()) { next(); return@launch }
                prefetched = c.url to i
                open(c, expandSheet = false)
                return@launch
            }
        }
    }

    fun undoSkip() {
        val seg = skipped ?: return
        player.seekTo((seg.startSec * 1000).toLong())
        skipped = null
    }

    fun open(item: VideoItem) = open(item, expandSheet = true)

    private fun open(item: VideoItem, expandSheet: Boolean, fromQueue: Boolean = false) {
        nextJob?.cancel()
        queuedPlayback = fromQueue
        current = item
        store.queueCurrent(item.id)
        if (expandSheet) showSheet(1f)
        if ((info?.url == item.url || usingLocal && player.currentMediaItem?.mediaId == item.id) && error == null) return
        load(item.url)
    }

    fun openUrl(url: String, startSec: Long = 0) {
        val id = videoIdOf(url)
        if (id == info?.id) {
            showSheet(1f)
            if (startSec > 0) player.seekTo(startSec * 1000)
            return
        }
        // Set before loading starts, so a cached video can't begin playing from 0.
        pendingStartMs = startSec * 1000
        open(
            VideoItem(
                url = "https://www.youtube.com/watch?v=$id",
                id = id,
                title = "",
                channelName = "",
                thumbnail = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            ),
        )
    }

    private var pendingStartMs = 0L

    fun retry() { current?.let { load(it.url) } }

    /** Whether the current video has already had its one silent reload after a playback error. */
    private var recovered = false

    /**
     * Stream links expire after a few hours and YouTube sometimes rejects one mid-video.
     * The first time that happens, fetch fresh links and carry on from the same spot.
     */
    private fun onPlaybackFailed(e: PlaybackException) {
        val localId = current?.id
        if (!usingLocal && localId != null && AppGraph.offline.local(localId) != null) {
            playLocal(localId, player.currentPosition.coerceAtLeast(0), player.playWhenReady)
            error = null
            return
        }
        if (queuedPlayback && (usingLocal || NetworkInfo.offline())) {
            queueNotice("Skipped ${current?.title.orEmpty().ifBlank { "video" }}: couldn't play this copy.")
            error = queueMessage
            player.stop()
            next()
            return
        }
        io.github.shoalapps.swordfish.Telemetry.report(e)
        val url = current?.url
        val http = e.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
            e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
            e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
        if (!recovered && http && url != null) {
            recovered = true
            pendingStartMs = player.currentPosition
            AppGraph.repo.forget(url)
            load(url, keepRecovered = true)
            return
        }
        error = when (e.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                if (NetworkInfo.offline()) "You're offline. Check your connection and try again."
                else "The connection dropped while playing. Try again."
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "YouTube stopped sending this video. Try again."
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
                "This phone can't play this video's format. Try a lower quality."
            else -> "Couldn't play this video (${e.errorCodeName}). Try again."
        }
        // One bad video shouldn't strand the queue, but if several fail in a row the problem is
        // probably the connection, so stop instead of burning through everything that's left.
        if (queuedPlayback && store.queue.value.isNotEmpty() && queueFailures < 2) {
            queueFailures++
            queueNotice("Skipped ${current?.title.orEmpty().ifBlank { "video" }}: ${error.orEmpty()}")
            player.stop()
            next()
        }
    }

    private fun preferredQuality(): Int {
        return io.github.shoalapps.swordfish.data.NetworkPolicy.quality()
    }

    private fun load(url: String, keepRecovered: Boolean = false) {
        loadJob?.cancel()
        endHandled = false
        if (!keepRecovered) recovered = false
        info = null; error = null; onlyYouTubeCanPlay = false; segments = emptyList(); votes = null; rating = Rating.NONE
        related = emptyList(); skipped = null; skippedIds.clear(); firstFrame = false
        comments = emptyList(); commentsNext = null; commentsError = null; replies.clear()
        captionTracks = emptyList(); captionTrack = null; cues = emptyList(); captionText = null
        chapters = emptyList(); chapterIndex = -1
        quality = preferredQuality()
        audioTracks = emptyList()
        audioTrackId = null
        speed = store.settings.value.defaultSpeed
        loading = true
        player.stop()
        player.clearMediaItems()
        usingLocal = false
        SkipAware.clear(); playing = null; endedAtOutro = false
        val videoId = videoIdOf(url)
        AppGraph.offline.watching(current)
        if (AppGraph.offline.local(videoId) != null) {
            playLocal(videoId, pendingStartMs, true)
            pendingStartMs = 0
            loading = false
            current?.let(store::addHistory)
            return
        }
        if (NetworkInfo.offline()) {
            pendingStartMs = 0
            loading = false
            error = "This video isn't saved for offline playback."
            if (queuedPlayback) {
                queueNotice("Skipped ${current?.title.orEmpty().ifBlank { "video" }}: not saved for offline playback.")
                next()
            }
            return
        }
        loadJob = viewModelScope.launch {
            var skipFailed = false
            // These don't depend on the stream, so start them straight away.
            val s = store.settings.value
            if (!io.github.shoalapps.swordfish.data.NetworkPolicy.savingData()) {
                if (s.sponsorBlock) launch { segments = AppGraph.sponsorBlock.segments(videoId, s.sbSkip); planSkips() }
                if (s.ryd) launch { votes = AppGraph.ryd.votes(videoId) }
                launch { loadComments(url) }
            }
            try {
                val i = prefetched?.takeIf { it.first == url }?.second ?: streamWithRetry(url)
                prefetched = null
                info = i
                audioTracks = if (i.videoOnlyStreams.isNotEmpty()) StreamPicker.audioTracks(i) else emptyList()
                val video = i.toVideo().let { v ->
                    // keep the avatar/thumbnail we already had if the page lacks them
                    val prev = current
                    v.copy(channelAvatar = v.channelAvatar ?: prev?.channelAvatar)
                }
                current = video
                AppGraph.offline.watching(video)
                related = io.github.shoalapps.swordfish.data.FeedFilter.apply(
                    i.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideo() }
                        .ifEmpty { io.github.shoalapps.swordfish.data.KidsClient.related[i.id].orEmpty() },
                )
                store.addHistory(video)
                if (AppGraph.offline.local(video.id) != null) playLocal(video.id, pendingStartMs, true)
                else play(i, pendingStartMs)
                skipFailed = queuedPlayback && error != null
                pendingStartMs = 0
                captionTracks = Captions.tracks(i)
                chapters = Chapters.of(i)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pendingStartMs = 0
                io.github.shoalapps.swordfish.Telemetry.report(e)
                error = NetworkInfo.friendlyError(e)
                onlyYouTubeCanPlay = io.github.shoalapps.swordfish.data.KidsClient.shouldTry(e)
                skipFailed = queuedPlayback
            } finally {
                loading = false
            }
            if (skipFailed) {
                queueNotice("Skipped ${current?.title.orEmpty().ifBlank { "video" }}: couldn't play this video.")
                next()
            }
        }
    }

    private fun isBotCheck(msg: String) = NetworkInfo.isBotCheck(msg)

    /** The bot check is sometimes a one-off, so give it one quiet second try. */
    private suspend fun streamWithRetry(url: String): StreamInfo =
        try {
            AppGraph.repo.stream(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!isBotCheck(e.message.orEmpty())) throw e
            delay(1500)
            AppGraph.repo.stream(url)
        }

    private fun play(i: StreamInfo, startMs: Long) {
        val choice = StreamPicker.pick(i, quality, audioTrackId)
        if (choice.audio != null) audioTrackId = StreamPicker.audioId(choice.audio)
        qualities = choice.qualities
        val source = StreamPicker.buildSource(choice, i)
        if (source == null) {
            error = "No playable streams found for this video"
            return
        }
        playing = choice
        player.setMediaSource(source, startMs)
        player.setPlaybackSpeed(speed)
        player.prepare()
        player.play()
        planSkips()
    }

    private fun playLocal(id: String, startMs: Long, playWhenReady: Boolean) {
        val file = AppGraph.offline.local(id) ?: return
        usingLocal = true
        playing = null
        SkipAware.clear()
        qualities = emptyList()
        player.setMediaItem(androidx.media3.common.MediaItem.Builder()
            .setMediaId(id).setUri(android.net.Uri.fromFile(file))
            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder()
                .setTitle(current?.title).setArtist(current?.channelName)
                .setArtworkUri(current?.thumbnail?.let(android.net.Uri::parse)).build())
            .build(), startMs)
        player.setPlaybackSpeed(speed)
        player.prepare()
        player.playWhenReady = playWhenReady
    }

    private var lastLoggedSec = -1L

    /** The video "ended" by skipping an outro that runs to the end (it's paused there, not ended). */
    private var endedAtOutro = false

    /** The streams playing now, for fetching ahead past segments. */
    private var playing: Playable? = null

    /**
     * Tells the loader about the next segment SponsorBlock will skip: stop at its start, fetch what
     * plays after it. Segments you've gone back into (or already skipped) are loaded normally.
     */
    private fun planSkips() {
        val choice = playing
        val pos = player.currentPosition
        val next = segments.filter { it.uuid !in skippedIds && it.endSec * 1000 > pos + 500 }.minByOrNull { it.startSec }
        if (choice == null || choice.hls != null || next == null) { SkipAware.clear(); return }
        val streams = listOfNotNull<org.schabi.newpipe.extractor.stream.Stream>(choice.video, choice.audio)
        val total = info?.duration?.times(1000) ?: durationMs
        // A segment running to the end has nothing after it to fetch; just don't load it.
        val toEnd = total > 0 && next.endSec * 1000 >= total - 1500
        SkipAware.plan((next.startSec * 1000).toLong(), if (toEnd) -1 else (next.endSec * 1000).toLong(), total, if (toEnd) emptyList() else streams, next.uuid + "@" + (choice.video?.content?.hashCode() ?: 0))
    }

    var commentsError by mutableStateOf<String?>(null)
        private set
    /** Loaded replies per comment id. */
    val replies = androidx.compose.runtime.mutableStateMapOf<String, List<Comment>>()
    val repliesLoading = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()

    fun loadComments(forUrl: String? = null) {
        val url = forUrl ?: info?.url ?: current?.url ?: return
        if (commentsLoading) return
        if (comments.isNotEmpty() && commentsNext == null) return
        commentsLoading = true
        commentsError = null
        viewModelScope.launch {
            runCatching { AppGraph.repo.comments(url, commentsNext) }
                .onSuccess {
                    comments = (comments + it.items).distinctBy { c -> c.id.ifEmpty { c.author + c.html } }
                    commentsNext = it.next
                }
                .onFailure { commentsError = NetworkInfo.friendlyError(it) }
            commentsLoading = false
        }
    }

    fun toggleReplies(c: Comment) {
        if (replies.containsKey(c.id)) { replies.remove(c.id); return }
        val page = c.repliesPage ?: return
        val url = info?.url ?: current?.url ?: return
        repliesLoading[c.id] = true
        viewModelScope.launch {
            runCatching { AppGraph.repo.replies(url, page) }.onSuccess { replies[c.id] = it.items }
            repliesLoading.remove(c.id)
        }
    }

    /** Posts a comment (or a reply when [to] is set). Returns an error message or null. */
    suspend fun post(text: String, to: Comment?): String? {
        val id = info?.id ?: current?.id ?: return "No video"
        if (!store.signedIn) return "Sign in to comment"
        return runCatching {
            if (to == null) AppGraph.account.postComment(id, text) else AppGraph.account.replyToComment(id, to.id, text)
        }.fold(
            onSuccess = {
                val mine = Comment(
                    id = "local-${System.currentTimeMillis()}",
                    author = store.account.value?.name ?: "You",
                    avatar = store.account.value?.avatar,
                    html = text.replace("<", "&lt;"),
                    likes = 0, date = "saved · waiting to sync", replies = 0, pinned = false, hearted = false,
                )
                if (to == null) comments = listOf(mine) + comments
                else replies[to.id] = (replies[to.id] ?: emptyList()) + mine
                null
            },
            onFailure = { it.message ?: "Couldn't post" },
        )
    }

    val hasMoreComments get() = commentsNext != null

    /** Your like/dislike on each comment, shown straight away. */

    /** Likes or dislikes a comment (tapping the same again clears it). Returns a message to show, or null. */

    /** Changes quality for this video only; the defaults live in Settings. */
    fun changeQuality(q: Int) {
        quality = if (io.github.shoalapps.swordfish.data.NetworkPolicy.savingData()) q.coerceAtMost(720) else q
        info?.let { play(it, player.currentPosition) }
    }

    fun changeAudioTrack(id: String) {
        if (audioTracks.none { it.id == id }) return
        if (id.startsWith("media3:")) {
            val parts = id.split(':')
            val group = player.currentTracks.groups.getOrNull(parts.getOrNull(1)?.toIntOrNull() ?: -1) ?: return
            val index = parts.getOrNull(2)?.toIntOrNull() ?: return
            if (index !in 0 until group.length) return
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setOverrideForType(androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, index))
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_AUDIO, false).build()
            audioTrackId = id
            return
        }
        val stream = info ?: return
        val position = player.currentPosition
        val keepPlaying = player.playWhenReady
        audioTrackId = id
        play(stream, position)
        player.playWhenReady = keepPlaying
    }

    fun selectCaptions(track: CaptionTrack?) {
        captionTrack = track
        cues = emptyList()
        captionText = null
        if (track == null) return
        viewModelScope.launch { cues = runCatching { Captions.load(track) }.getOrDefault(emptyList()) }
    }

    fun setSleepTimer(minutes: Int) {
        sleepMinutes = minutes
        if (minutes > 0) sleepAtMs = System.currentTimeMillis() + minutes * 60_000L
    }

    fun toggleLoop() = store.updateSettings { it.copy(loopVideo = !it.loopVideo) }
    fun toggleAutoplay() = store.updateSettings { it.copy(autoplayNext = !it.autoplayNext) }

    fun changeSpeed(s: Float) {
        speed = s
        player.setPlaybackSpeed(s)
        if (store.settings.value.rememberSpeed) store.updateSettings { it.copy(defaultSpeed = s) }
    }

    /** Returns false if the user needs to sign in first. */
    fun rate(target: Rating): Boolean {
        val id = info?.id ?: current?.id ?: return true
        val next = if (rating == target) Rating.NONE else target
        rating = next
        if (!store.signedIn) return false
        val action = when (next) {
            Rating.LIKE -> "like"
            Rating.DISLIKE -> "dislike"
            Rating.NONE -> "removelike"
        }
        AppGraph.scope.launch { runCatching { AppGraph.account.rate(id, action) } }
        return true
    }

    fun togglePlay() {
        endHandled = false
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekBy(ms: Long) = player.seekTo((player.currentPosition + ms).coerceAtLeast(0))
    fun seekTo(ms: Long) { endHandled = false; player.seekTo(ms) }

    fun showSheet(target: Float) {
        sheetVisible = true
        sheetTarget = target
        sheetNonce++
    }

    fun collapse() = showSheet(0f)

    fun close() {
        nextJob?.cancel()
        loadJob?.cancel()
        player.stop()
        player.clearMediaItems()
        sheetVisible = false
        fullscreen = false
        current = null
        store.queueCurrent(null)
        AppGraph.offline.watching(null)
        info = null
        error = null
        viewModelScope.launch { expand.snapTo(0f) }
    }

    override fun onCleared() {
        player.removeListener(listener)
    }
}
