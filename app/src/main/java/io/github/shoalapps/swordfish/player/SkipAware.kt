package io.github.shoalapps.swordfish.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import io.github.shoalapps.swordfish.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.Stream
import java.io.File

/**
 * SponsorBlock-aware loading. The player doesn't download the parts SponsorBlock will skip: it
 * stops buffering at the start of the next segment, and the stretch straight after it (what will
 * actually play) is fetched ahead into a small cache, so the skip lands on video that's already
 * there. A segment is only loaded if you choose to watch it, by going back into it.
 */
object SkipAware {
    /** Don't buffer past here (microseconds into the video); no limit when there's nothing ahead to skip. */
    @Volatile var gateUs: Long = Long.MAX_VALUE
        private set

    /** Playback stops loading at the gate, but keeps a little of the segment so the skip can trigger. */
    private const val PAST_START_MS = 800L
    /** How much of what plays after a segment to fetch ahead. */
    private const val AHEAD_MS = 12_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefetch: Job? = null
    private var prefetchedFor: String? = null

    private val cache: SimpleCache by lazy {
        SimpleCache(File(AppGraph.app.cacheDir, "skip-ahead"), LeastRecentlyUsedCacheEvictor(150L * 1024 * 1024), StandaloneDatabaseProvider(AppGraph.app))
    }

    /** What playback reads through: the cache (read only, filled by [prefetchAfter]) over YouTube. */
    fun playbackFactory(upstream: DataSource.Factory): DataSource.Factory =
        CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(upstream)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** The player's buffering rules, with SponsorBlock's gate added on top of the defaults. */
    fun loadControl(): LoadControl = Gated(
        // The most it could ever hold. NetworkPace decides how much of that's worth having right now.
        DefaultLoadControl.Builder().setBufferDurationsMs(NetworkPace.MIN_SECONDS * 1000, NetworkPace.MAX_SECONDS * 1000, 1000, 2500).build()
    )

    // Kotlin's "by" doesn't forward Java's default methods, so each one ExoPlayer calls is passed on by hand.
    private class Gated(private val d: DefaultLoadControl) : LoadControl {
        /** Enough is in hand, so nothing's fetched until it has run down a way. */
        @Volatile private var resting = false
        override fun onPrepared(playerId: androidx.media3.exoplayer.analytics.PlayerId) = d.onPrepared(playerId)
        override fun onTracksSelected(parameters: LoadControl.Parameters, trackGroups: androidx.media3.exoplayer.source.TrackGroupArray, trackSelections: Array<out androidx.media3.exoplayer.trackselection.ExoTrackSelection?>) =
            d.onTracksSelected(parameters, trackGroups, trackSelections).also {
                val total = trackSelections.filterNotNull().sumOf { it.selectedFormat.bitrate.coerceAtLeast(0).toLong() }
                NetworkPace.mediaBps = if (total > 0) total else NetworkPace.TYPICAL_BPS
            }
        override fun onStopped(playerId: androidx.media3.exoplayer.analytics.PlayerId) = d.onStopped(playerId)
        override fun onReleased(playerId: androidx.media3.exoplayer.analytics.PlayerId) = d.onReleased(playerId)
        override fun getAllocator(playerId: androidx.media3.exoplayer.analytics.PlayerId) = d.getAllocator(playerId)
        override fun getBackBufferDurationUs(playerId: androidx.media3.exoplayer.analytics.PlayerId) = d.getBackBufferDurationUs(playerId)
        override fun retainBackBufferFromKeyframe(playerId: androidx.media3.exoplayer.analytics.PlayerId) = d.retainBackBufferFromKeyframe(playerId)
        override fun shouldStartPlayback(parameters: LoadControl.Parameters) = d.shouldStartPlayback(parameters)
        override fun shouldContinuePreloading(playerId: androidx.media3.exoplayer.analytics.PlayerId, timeline: androidx.media3.common.Timeline, mediaPeriodId: androidx.media3.exoplayer.source.MediaSource.MediaPeriodId, bufferedDurationUs: Long) =
            d.shouldContinuePreloading(playerId, timeline, mediaPeriodId, bufferedDurationUs)

        override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
            val gate = gateUs
            val loadedTo = parameters.playbackPositionUs + parameters.bufferedDurationUs
            if (parameters.playbackPositionUs < gate && loadedTo >= gate) return false
            if (parameters.rebuffering) NetworkPace.stalled()
            val target = NetworkPace.targetSeconds()
            val held = parameters.bufferedDurationUs / 1_000_000
            if (held >= target) resting = true
            else if (held < NetworkPace.resumeSeconds(target)) resting = false
            if (resting) return false
            return d.shouldContinueLoading(parameters)
        }
    }

    /** Nothing to skip ahead (or SponsorBlock is off): load as normal. */
    fun clear() {
        gateUs = Long.MAX_VALUE
        prefetch?.cancel(); prefetchedFor = null
    }

    /**
     * Plans around the next segment to skip: stop loading at its start, and fetch the [AHEAD_MS]
     * after its end for each of the [streams] playing (video and audio are separate on YouTube).
     */
    fun plan(segmentStartMs: Long, segmentEndMs: Long, durationMs: Long, streams: List<Stream>, key: String) {
        gateUs = (segmentStartMs + PAST_START_MS) * 1000
        if (prefetchedFor == key) return
        prefetchedFor = key
        prefetch?.cancel()
        prefetch = scope.launch {
            for (s in streams) runCatching { fetchAhead(s, segmentEndMs, durationMs) }
                .onSuccess { android.util.Log.d("SkipAware", "fetched ahead from ${segmentEndMs / 1000.0}s (${s.itagItem?.mediaFormat})") }
                .onFailure { android.util.Log.w("SkipAware", "couldn't fetch ahead", it) }
        }
    }

    private fun fetchAhead(s: Stream, fromMs: Long, durationMs: Long) {
        val url = s.content
        val itag = s.itagItem
        val length = itag?.contentLength?.takeIf { it > 0 } ?: return
        val range = sidxRange(url, itag.indexStart, itag.indexEnd, fromMs)
            ?: estimate(length, itag.averageBitrate.takeIf { it > 0 } ?: itag.bitrate, fromMs, durationMs)
            ?: return
        val writer = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(YtDataSource.Factory()).createDataSource()
        CacheWriter(writer, DataSpec.Builder().setUri(Uri.parse(url)).setPosition(range.first).setLength(range.last - range.first + 1).build(), null, null).cache()
    }

    /** Without an index: where the time falls by average bitrate, with a few seconds' margin before. */
    private fun estimate(length: Long, bitrate: Int, fromMs: Long, durationMs: Long): LongRange? {
        if (durationMs <= 0 || bitrate <= 0) return null
        val bytesPerMs = bitrate / 8.0 / 1000
        val start = (length * fromMs / durationMs - bytesPerMs * 3000).toLong().coerceAtLeast(0)
        val end = (start + bytesPerMs * (AHEAD_MS + 3000)).toLong().coerceAtMost(length - 1)
        return if (end > start) start..end else null
    }

    /**
     * MP4 streams start with an index (sidx) of their fragments: read it to find exactly which
     * bytes hold [fromMs] onwards.
     */
    private fun sidxRange(url: String, indexStart: Int, indexEnd: Int, fromMs: Long): LongRange? {
        if (indexStart <= 0 || indexEnd <= indexStart) return null
        val src = YtDataSource.Factory().createDataSource()
        val bytes = try {
            src.open(DataSpec.Builder().setUri(Uri.parse(url)).setPosition(indexStart.toLong()).setLength((indexEnd - indexStart + 1).toLong()).build())
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (true) { val n = src.read(buf, 0, buf.size); if (n == C.RESULT_END_OF_INPUT) break; out.write(buf, 0, n) }
            out.toByteArray()
        } finally { src.close() }
        val refs = parseSidx(bytes, indexEnd.toLong() + 1) ?: return null
        val first = refs.indexOfLast { it.startMs <= fromMs }.coerceAtLeast(0)
        var last = first
        while (last + 1 < refs.size && refs[last].startMs + refs[last].durationMs < fromMs + AHEAD_MS) last++
        return refs[first].offset until (refs[last].offset + refs[last].size)
    }

    internal data class Ref(val startMs: Long, val durationMs: Long, val offset: Long, val size: Long)

    /** The fragments listed in a sidx box: when each starts and where its bytes are. */
    internal fun parseSidx(b: ByteArray, firstByteAfterIndex: Long): List<Ref>? {
        val buf = java.nio.ByteBuffer.wrap(b)
        if (b.size < 32 || String(b, 4, 4, Charsets.US_ASCII) != "sidx") return null
        buf.position(8)
        val version = buf.get().toInt(); buf.position(buf.position() + 3)
        buf.int // reference ID
        val timescale = buf.int.toLong() and 0xffffffffL
        if (timescale == 0L) return null
        val earliest: Long; val firstOffset: Long
        if (version == 0) { earliest = buf.int.toLong() and 0xffffffffL; firstOffset = buf.int.toLong() and 0xffffffffL }
        else { earliest = buf.long; firstOffset = buf.long }
        buf.short // reserved
        val count = buf.short.toInt() and 0xffff
        var time = earliest
        var offset = firstByteAfterIndex + firstOffset
        val refs = ArrayList<Ref>(count)
        repeat(count) {
            if (buf.remaining() < 12) return refs
            val size = (buf.int.toLong() and 0x7fffffffL)
            val dur = buf.int.toLong() and 0xffffffffL
            buf.int // SAP
            refs += Ref(time * 1000 / timescale, dur * 1000 / timescale, offset, size)
            time += dur; offset += size
        }
        return refs
    }
}
