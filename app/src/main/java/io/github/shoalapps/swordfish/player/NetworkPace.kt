package io.github.shoalapps.swordfish.player

import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import io.github.shoalapps.swordfish.AppGraph
import io.github.shoalapps.swordfish.data.NetworkPolicy

/**
 * Watches how fast video is really arriving and decides how much is worth having in hand. A bit like
 * culling in a game: what you aren't about to see doesn't get fetched. A fast connection can fetch
 * the next part any time, so it holds little; a slow one stocks up to ride out the dips.
 */
object NetworkPace {
    /** Measures every video transfer as it happens, and starts again when the network changes. */
    val meter: DefaultBandwidthMeter by lazy { DefaultBandwidthMeter.getSingletonInstance(AppGraph.app) }

    /** The bitrate of what's playing (video plus audio); a typical HD figure until it's known. */
    @Volatile var mediaBps: Long = TYPICAL_BPS
    /** When playback last ran out of video. */
    @Volatile private var lastStallMs = 0L

    const val TYPICAL_BPS = 2_500_000L
    const val MIN_SECONDS = 15
    const val MAX_SECONDS = 120
    private const val STALL_MEMORY_MS = 60_000L

    fun stalled() { lastStallMs = android.os.SystemClock.elapsedRealtime() }
    fun speedBps(): Long = meter.bitrateEstimate

    /** Seconds of video to hold ahead of where you're watching. */
    fun targetSeconds(speedBps: Long, mediaBps: Long, metered: Boolean, saving: Boolean, lowMemory: Boolean, stalledRecently: Boolean): Int {
        val ratio = speedBps.toDouble() / mediaBps.coerceAtLeast(1)
        var target = when {
            ratio >= 6 -> MIN_SECONDS
            ratio >= 3 -> 25
            ratio >= 1.5 -> 50
            else -> MAX_SECONDS
        }
        if (stalledRecently) target = maxOf(target, 60)
        // Paid-for data is only spent ahead of time when the connection really needs it.
        if (metered && ratio >= 1.5) target = minOf(target, 30)
        if (saving) target = minOf(target, 20)
        if (lowMemory) target = minOf(target, 30)
        return target.coerceIn(MIN_SECONDS, MAX_SECONDS)
    }

    /** Stops at the target, then waits until it has run down a way before fetching again, so the radio can rest. */
    fun resumeSeconds(target: Int): Int = (target * 3 / 5).coerceAtLeast(10)

    fun targetSeconds(): Int = targetSeconds(
        speedBps(), mediaBps, NetworkPolicy.metered(), NetworkPolicy.savingData(),
        AppGraph.app.getSystemService(android.app.ActivityManager::class.java)?.isLowRamDevice == true,
        lastStallMs > 0 && android.os.SystemClock.elapsedRealtime() - lastStallMs < STALL_MEMORY_MS,
    )

    /** How many videos in a list to look up ahead of a tap. */
    fun preloadCount(speedBps: Long, metered: Boolean, saving: Boolean, watching: Boolean): Int = when {
        watching || saving -> 0
        speedBps < TYPICAL_BPS -> 0
        metered -> 1
        speedBps < TYPICAL_BPS * 4 -> 2
        else -> 3
    }

    fun preloadCount(): Int = preloadCount(speedBps(), NetworkPolicy.metered(), NetworkPolicy.savingData(), AppGraph.offline.isWatching)

    /** True when the connection can carry a whole extra copy of the video without troubling playback. */
    fun hasRoomForDownload(speedBps: Long, mediaBps: Long) = speedBps >= mediaBps * 3

    /** The speed a download may use during playback: what the video leaves spare, and only a trickle on a slow line. */
    fun downloadLimitBps(speedBps: Long, mediaBps: Long): Long = (speedBps - mediaBps * 2).coerceAtLeast(speedBps / 4).coerceAtLeast(250_000L)
}
