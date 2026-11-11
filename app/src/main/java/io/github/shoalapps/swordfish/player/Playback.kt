@file:OptIn(UnstableApi::class)

package io.github.shoalapps.swordfish.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import io.github.shoalapps.swordfish.AppGraph
import io.github.shoalapps.swordfish.MainActivity
import io.github.shoalapps.swordfish.data.NewPipeDownloader
import io.github.shoalapps.swordfish.data.bestUrl
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

/** One ExoPlayer for the whole app, shared by the UI and the background service. */
object PlayerHolder {
    private val models = ViewModelStore()
    val viewModel: PlayerViewModel by lazy {
        ViewModelProvider(models, ViewModelProvider.NewInstanceFactory())[PlayerViewModel::class.java]
    }

    val player: ExoPlayer by lazy {
        ExoPlayer.Builder(AppGraph.app)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            // Doesn't download what SponsorBlock will skip (see SkipAware).
            .setLoadControl(SkipAware.loadControl())
            .setBandwidthMeter(NetworkPace.meter)
            .build()
    }

    /** YouTube's servers, read through the cache that holds what plays after each skipped segment. */
    val dataSourceFactory: DataSource.Factory by lazy { SkipAware.playbackFactory(YtDataSource.Factory()) }
}

/**
 * Wraps the normal HTTP source with the tricks YouTube's video servers expect:
 * byte ranges in the URL (avoids throttling) and the right request style per client.
 */
class YtDataSource(private val upstream: HttpDataSource) : DataSource by upstream {

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        val isPlayback = uri.host?.endsWith("googlevideo.com") == true &&
            uri.path?.startsWith("/videoplayback") == true
        if (!isPlayback) return upstream.open(dataSpec)

        val url = uri.toString()
        var builder = uri.buildUpon()
        if (!url.contains("&rn=")) builder = builder.appendQueryParameter("rn", (requestNumber++).toString())

        // Progressive streams take their byte range in the URL; HLS segments don't need one.
        val progressive = uri.getQueryParameter("itag") != null
        val spec = dataSpec.buildUpon()
        if (progressive) {
            val start = dataSpec.position
            val end = if (dataSpec.length != C.LENGTH_UNSET.toLong()) (start + dataSpec.length - 1).toString() else ""
            builder = builder.appendQueryParameter("range", "$start-$end")
            spec.setPosition(0).setLength(C.LENGTH_UNSET.toLong())
        }

        val headers = HashMap(dataSpec.httpRequestHeaders)
        headers["User-Agent"] = if (YoutubeParsingHelper.isVisionOsStreamingUrl(url)) {
            YoutubeParsingHelper.getVisionOsUserAgent(null)
        } else {
            NewPipeDownloader.USER_AGENT
        }
        if (YoutubeParsingHelper.isWebStreamingUrl(url)) {
            headers["Origin"] = "https://www.youtube.com"
            headers["Referer"] = "https://www.youtube.com"
            headers["Sec-Fetch-Dest"] = "empty"
            headers["Sec-Fetch-Mode"] = "cors"
            headers["Sec-Fetch-Site"] = "cross-site"
        }
        headers["TE"] = "trailers"

        // YouTube's video servers expect a POST with this tiny body, like the official apps send.
        spec.setUri(builder.build())
            .setHttpRequestHeaders(headers)
            .setHttpMethod(DataSpec.HTTP_METHOD_POST)
            .setHttpBody(byteArrayOf(0x78, 0x00))
        return upstream.open(spec.build())
    }

    class Factory : DataSource.Factory {
        private val http = DefaultHttpDataSource.Factory()
            .setUserAgent(null)
            // Some CDN redirects point to HTTP even when playback started over HTTPS.
            .setCrossProtocolRedirectsForceOriginal(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)

        override fun createDataSource(): DataSource = YtDataSource(http.createDataSource())
    }

    companion object {
        private var requestNumber = 0
    }
}

/** What we chose to play from a video's list of streams. */
data class Playable(
    val video: VideoStream?,
    val audio: AudioStream?,
    val hls: String?,
    val qualities: List<Int>,
)

data class AudioChoice(val id: String, val label: String, val language: String?, val original: Boolean = false)

object StreamPicker {
    private val resolutionHeight = Regex("""^(\d{3,4})p""")

    /** Pixel height, read from the resolution label ("1080p60") when YouTube didn't send one. */
    fun heightOf(v: VideoStream): Int = qualityOf(v.getResolution(), v.width, v.height)

    /**
     * The quality number YouTube shows ("1080p"), which is the frame's short side: a vertical
     * 1080×1920 Short is 1080p, not 1920p. Using the raw height picked 480p for 1080p Shorts.
     */
    fun qualityOf(resolution: String?, width: Int, height: Int): Int =
        resolutionHeight.find(resolution.orEmpty())?.groupValues?.get(1)?.toInt()
            ?: if (width > 0 && height > 0) minOf(width, height) else height.coerceAtLeast(0)

    fun audioId(stream: AudioStream): String = stream.audioTrackId?.takeIf { it.isNotBlank() }
        ?: "${stream.audioLocale?.toLanguageTag().orEmpty()}:${stream.audioTrackType ?: AudioTrackType.ORIGINAL}"

    fun audioTracks(info: StreamInfo): List<AudioChoice> = info.audioStreams
        .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        .distinctBy(::audioId)
        .map { stream ->
            val original = stream.audioTrackType == null || stream.audioTrackType == AudioTrackType.ORIGINAL
            val name = stream.audioTrackName?.takeIf { it.isNotBlank() }
                ?: stream.audioLocale?.getDisplayName(java.util.Locale.getDefault())?.takeIf { it.isNotBlank() }
                ?: if (original) "Original" else "Audio"
            AudioChoice(audioId(stream), name + when (stream.audioTrackType) {
                AudioTrackType.ORIGINAL -> " (original)"
                AudioTrackType.DESCRIPTIVE -> " (audio description)"
                else -> ""
            }, stream.audioLocale?.toLanguageTag(), original)
        }

    fun pick(info: StreamInfo, maxHeight: Int, audioTrackId: String? = null): Playable {
        val isLive = info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM
        if (isLive && !info.hlsUrl.isNullOrBlank()) {
            return Playable(null, null, info.hlsUrl, emptyList())
        }

        val progressive = { d: DeliveryMethod -> d == DeliveryMethod.PROGRESSIVE_HTTP }

        var videos = info.videoOnlyStreams.filter { it.isUrl && progressive(it.deliveryMethod) }
        val muxedFallback = videos.isEmpty()
        if (muxedFallback) videos = info.videoStreams.filter { it.isUrl && progressive(it.deliveryMethod) }

        val qualities = videos.map(::heightOf).filter { it > 0 }.distinct().sortedDescending()

        val video = videos.sortedWith(
            compareByDescending<VideoStream> { val h = heightOf(it); if (h <= maxHeight) h else -h }
                .thenByDescending { it.format == MediaFormat.MPEG_4 }
                .thenByDescending { it.fps },
        ).firstOrNull()

        val audio = if (muxedFallback) null else info.audioStreams
            .filter { it.isUrl && progressive(it.deliveryMethod) }
            .sortedWith(
                compareByDescending<AudioStream> { audioTrackId != null && audioId(it) == audioTrackId }
                    .thenByDescending {
                    it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL
                }
                    .thenByDescending { it.format == MediaFormat.M4A }
                    .thenByDescending { it.averageBitrate },
            ).firstOrNull()

        return Playable(video, audio, null, qualities)
    }

    fun buildSource(p: Playable, info: StreamInfo): MediaSource? {
        val factory = PlayerHolder.dataSourceFactory
        val metadata = MediaMetadata.Builder()
            .setTitle(info.name)
            .setArtist(info.uploaderName)
            .setArtworkUri(info.thumbnails.bestUrl()?.let(Uri::parse))
            .build()

        p.hls?.let { url ->
            val item = MediaItem.Builder()
                .setUri(url)
                .setMediaId(info.id)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .setMediaMetadata(metadata)
                .build()
            return HlsMediaSource.Factory(factory).createMediaSource(item)
        }

        val video = p.video ?: return null
        // The loader asks whether to carry on every 128 KB (rather than every megabyte), so it
        // stops close to a SponsorBlock segment even on low-bitrate streams.
        val progressive = ProgressiveMediaSource.Factory(factory).setContinueLoadingCheckIntervalBytes(128 * 1024)
        val videoSource = progressive.createMediaSource(
            MediaItem.Builder().setUri(video.content).setMediaId(info.id).setMediaMetadata(metadata).build(),
        )
        val audio = p.audio ?: return videoSource
        val audioSource = progressive.createMediaSource(MediaItem.fromUri(audio.content))
        return MergingMediaSource(true, videoSource, audioSource)
    }
}

/** Keeps playback going with the screen off and shows the media notification. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // There is one actual MediaItem at a time. Route Next through the manual queue
        // instead of asking ExoPlayer for a second timeline item.
        val sessionPlayer = QueueAwarePlayer(PlayerHolder.player) { PlayerHolder.viewModel.next() }
        // Install the playback listener even when only a media controller starts the service.
        PlayerHolder.viewModel
        session = MediaSession.Builder(this, sessionPlayer)
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        PlayerHolder.player.pause()
        stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}

/**
 * What the notification and media controls talk to. There's one real item at a time, so Next goes
 * through the manual queue instead of asking ExoPlayer for a second item in its timeline.
 */
class QueueAwarePlayer(player: Player, private val onNext: () -> Unit) : ForwardingPlayer(player) {
    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .build()

    override fun isCommandAvailable(command: Int): Boolean =
        command == Player.COMMAND_SEEK_TO_NEXT || command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM || super.isCommandAvailable(command)

    private val followers = mutableMapOf<Player.Listener, Player.Listener>()

    override fun addListener(listener: Player.Listener) {
        if (followers.containsKey(listener)) return
        super.addListener(listener)
        // Runs just after the real report, whose list lacks Next, and puts it right. Don't wrap the
        // listener with Kotlin's "by": it doesn't forward Java's default methods, so nothing would arrive.
        val follower = object : Player.Listener {
            override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
                listener.onAvailableCommandsChanged(this@QueueAwarePlayer.availableCommands)
            }
        }
        followers[listener] = follower
        wrappedPlayer.addListener(follower)
    }

    override fun removeListener(listener: Player.Listener) {
        followers.remove(listener)?.let { wrappedPlayer.removeListener(it) }
        super.removeListener(listener)
    }

    override fun seekToNext() = onNext()
    override fun seekToNextMediaItem() = onNext()
}
