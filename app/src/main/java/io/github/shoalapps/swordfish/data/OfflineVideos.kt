package io.github.shoalapps.swordfish.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import io.github.shoalapps.swordfish.AppGraph
import io.github.shoalapps.swordfish.player.NetworkInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File

@Serializable
data class OfflineVideo(
    val video: VideoItem,
    val kept: Boolean = false,
    val complete: Boolean = false,
    val error: String? = null,
)

/** Pure retention rules shared by scheduling and completion, including late worker results. */
object OfflinePolicy {
    fun recommendations(feed: List<VideoItem>, count: Int, current: String?): List<VideoItem> =
        feed.filter { !it.isLive && !it.isShort && it.id != current }.distinctBy { it.id }
            .take(count.coerceIn(0, 50))

    /** Subscription videos wait while anything's playing, so they never compete with it for the connection. */
    fun toDownload(current: VideoItem?, suggestions: List<VideoItem>, watching: Boolean): List<VideoItem> =
        listOfNotNull(current) + if (watching) emptyList() else suggestions

    fun retain(entry: OfflineVideo, current: String?, recommendations: Set<String>): Boolean =
        entry.kept || entry.video.id == current || entry.video.id in recommendations
}

/** Completed files and their metadata live in private storage, never in the evictable image cache. */
class OfflineVideos(private val context: Context) {
    private val prefs = context.getSharedPreferences("offline", Context.MODE_PRIVATE)
    private val work get() = WorkManager.getInstance(context)
    private val root = File(context.filesDir, "offline").apply { mkdirs() }
    private val _videos = MutableStateFlow(read().map { it.copy(complete = it.complete && file(it.video.id).isFile) })
    val videos: StateFlow<List<OfflineVideo>> = _videos
    val progress = MutableStateFlow<Map<String, Int>>(emptyMap())
    private var current: VideoItem? = prefs.getString("current", null)?.let {
        runCatching { json.decodeFromString<VideoItem>(it) }.getOrNull()
    }
    // Only ever your subscriptions. Anything saved from the old home feed's list is left behind.
    private var feed: List<VideoItem> = prefs.getString("subscription-feed", null)?.let {
        runCatching { json.decodeFromString<List<VideoItem>>(it) }.getOrNull()
    }.orEmpty()
    private var lastPlan: Set<String> = emptySet()
    private val dismissed = mutableSetOf<String>()
    private val deferredDeletes = mutableSetOf<String>()
    private var wifiOnly = AppGraph.store.settings.value.downloadWifiOnly
    /** A video is open right now, as opposed to [current], which outlives the app for offline use. */
    @Volatile var isWatching = false
        private set

    private fun read(): List<OfflineVideo> = prefs.getString("videos", null)?.let {
        runCatching { json.decodeFromString<List<OfflineVideo>>(it) }.getOrNull()
    }.orEmpty()

    private fun file(id: String) = File(root, "$id.mp4")

    @Synchronized
    fun local(id: String): File? = file(id).takeIf { f ->
        _videos.value.any { it.video.id == id && it.complete } && f.isFile && f.length() > 0
    }

    @Synchronized
    private fun save(list: List<OfflineVideo>) {
        _videos.value = list
        prefs.edit().putString("videos", json.encodeToString(list)).apply()
    }

    @Synchronized
    fun watching(video: VideoItem?) {
        isWatching = video != null
        if (current?.id != video?.id && !NetworkInfo.offline()) {
            // Leaving a video removes its temporary copy, even if it was also in the old feed.
            val previousId = current?.id
            feed = feed.filterNot { it.id == previousId }
            prefs.edit().putString("subscription-feed", json.encodeToString(feed)).apply()
            deferredDeletes.filter { it != video?.id }.forEach { file(it).delete() }
            deferredDeletes.removeAll { it != video?.id }
            dismissed.remove(video?.id)
        }
        current = video
        prefs.edit().putString("current", video?.let { json.encodeToString(it) }).apply()
        reconcile()
    }

    @Synchronized
    fun subscriptions(videos: List<VideoItem>) {
        feed = videos
        prefs.edit().putString("subscription-feed", json.encodeToString(videos.take(100)) ).apply()
        reconcile()
    }

    @Synchronized
    fun keep(video: VideoItem) {
        if (!validId(video.id) || video.isLive) return
        dismissed.remove(video.id)
        val previous = _videos.value.firstOrNull { it.video.id == video.id }
        save(_videos.value.filterNot { it.video.id == video.id } +
            (previous ?: OfflineVideo(video)).copy(kept = true, error = null))
        // Promotion replaces an automatic job so a later Wi-Fi change cannot cancel the saved job.
        work.cancelUniqueWork(workName(video.id))
        enqueue(video, automatic = false)
    }

    /** Remove a saved copy or cancel a pending download; current playback stays protected. */
    @Synchronized
    fun remove(id: String) {
        work.cancelUniqueWork(workName(id))
        feed = feed.filterNot { it.id == id }
        prefs.edit().putString("subscription-feed", json.encodeToString(feed)).apply()
        dismissed += id
        if (current?.id == id) deferredDeletes += id else file(id).delete()
        save(_videos.value.filterNot { it.video.id == id })
    }

    @Synchronized
    fun reconcile() {
        // Keep automatic copies usable throughout an offline session, including when switching videos.
        if (NetworkInfo.offline()) {
            work.cancelAllWorkByTag(AUTO_TAG)
            work.cancelAllWorkByTag("saved-download")
            return
        }
        val settings = AppGraph.store.settings.value
        if (wifiOnly != settings.downloadWifiOnly) {
            _videos.value.filterNot { it.complete }.forEach { work.cancelUniqueWork(workName(it.video.id)) }
            wifiOnly = settings.downloadWifiOnly
        }
        val suggestions = OfflinePolicy.recommendations(feed.filterNot { it.id in dismissed }, settings.autoDownloadCount, current?.id)
        val ids = suggestions.map { it.id }.toSet()
        val retained = _videos.value.filter { entry ->
            val keep = OfflinePolicy.retain(entry, current?.id, ids)
            if (!keep) {
                work.cancelUniqueWork(workName(entry.video.id))
                file(entry.video.id).delete()
            }
            keep
        }
        save(retained)
        val speed = io.github.shoalapps.swordfish.player.NetworkPace
        val roomForCopy = speed.hasRoomForDownload(speed.speedBps(), speed.mediaBps)
        val automatic = OfflinePolicy.toDownload(
            current?.takeIf { settings.cacheCurrentVideo && it.id !in dismissed && !it.isLive && roomForCopy },
            suggestions, isWatching,
        )
        val next = automatic.map { it.id }.toSet()
        (lastPlan - next).filter { id -> retained.none { it.video.id == id && it.kept } }
            .forEach { work.cancelUniqueWork(workName(it)) }
        lastPlan = next
        // Metered downloads require explicit permission, and Data Saver always takes precedence.
        if (NetworkPolicy.downloadsAllowed()) automatic.forEach { enqueue(it, automatic = true) }
        else {
            work.cancelAllWorkByTag(AUTO_TAG)
            work.cancelAllWorkByTag("saved-download")
        }
        if (NetworkPolicy.downloadsAllowed()) retained.filter { it.kept && !it.complete && it.error == null }.forEach { enqueue(it.video, automatic = false) }
    }

    @Synchronized
    private fun enqueue(video: VideoItem, automatic: Boolean) {
        if (!validId(video.id) || local(video.id) != null) return
        val entry = _videos.value.firstOrNull { it.video.id == video.id }
        if (automatic && entry?.kept == true) return
        if (entry == null) save(_videos.value + OfflineVideo(video))
        val request = OneTimeWorkRequestBuilder<VideoDownloadWorker>()
            .setInputData(workDataOf("video" to json.encodeToString(video), "automatic" to automatic))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(
                if (AppGraph.store.settings.value.downloadWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            ).setRequiresBatteryNotLow(automatic).build())
            .addTag(if (automatic) AUTO_TAG else "saved-download")
            .build()
        work.enqueueUniqueWork(workName(video.id), ExistingWorkPolicy.KEEP, request)
    }

    @Synchronized
    fun wanted(video: VideoItem): Boolean {
        if (video.id in dismissed) return false
        val ids = OfflinePolicy.recommendations(feed, AppGraph.store.settings.value.autoDownloadCount, current?.id).map { it.id }.toSet()
        return _videos.value.firstOrNull { it.video.id == video.id }?.let {
            OfflinePolicy.retain(it, current?.id, ids)
        } == true
    }

    @Synchronized
    fun finish(video: VideoItem, completed: File) {
        if (!wanted(video)) return
        val target = file(video.id)
        check(completed.renameTo(target)) { "Couldn't save the downloaded video" }
        save(_videos.value.map { if (it.video.id == video.id) it.copy(video = video, complete = true, error = null) else it })
    }

    @Synchronized
    fun failed(id: String, message: String) {
        save(_videos.value.map { if (it.video.id == id) it.copy(error = message) else it })
    }

    companion object {
        const val AUTO_TAG = "automatic-video"
        fun workName(id: String) = "video-$id"
        fun validId(id: String) = id.matches(Regex("[A-Za-z0-9_-]{11}"))
        fun wifi(): Boolean {
            val cm = AppGraph.app.getSystemService(ConnectivityManager::class.java) ?: return false
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    }
}

class VideoDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = engineMutex.withLock { withContext(Dispatchers.IO) {
        val video = inputData.getString("video")?.let { json.decodeFromString<VideoItem>(it) } ?: return@withContext Result.failure()
        if (!OfflineVideos.validId(video.id) || !AppGraph.offline.wanted(video)) return@withContext Result.success()
        val automatic = inputData.getBoolean("automatic", true)
        if (!NetworkPolicy.downloadsAllowed()) return@withContext Result.retry()
        val dir = File(applicationContext.filesDir, "offline/tmp/$id").apply { mkdirs() }
        try {
            // Only a video you asked to save shows anything, as Android requires for a long download.
            // Automatic copies stay out of sight and simply pick up later if Android stops them.
            if (!automatic) setForeground(foreground(video))
            // Both libraries initialise once. No shell interpolation or remote backend is involved.
            synchronized(YoutubeDL.getInstance()) {
                YoutubeDL.getInstance().init(applicationContext)
                FFmpeg.getInstance().init(applicationContext)
            }
            // The APK's seed extractor is only a bootstrap. Update before the first download,
            // then daily, while holding the same lock as downloads so files cannot change mid-run.
            refreshExtractor()
            check(NetworkPolicy.downloadsAllowed()) { "Downloads are paused by your network settings" }
            check(dir.usableSpace > 256L * 1024 * 1024) { "Not enough free space to download this video" }
            val quality = NetworkPolicy.quality()
            val request = YoutubeDLRequest("https://www.youtube.com/watch?v=${video.id}").apply {
                addOption("--no-playlist")
                addOption("--match-filter", "!is_live & !is_upcoming")
                addOption("--no-mtime")
                addOption("--no-continue")
                addOption("--socket-timeout", 20)
                addOption("--retries", 3)
                addOption("--max-filesize", "2G")
                if (AppGraph.offline.isWatching) {
                    val pace = io.github.shoalapps.swordfish.player.NetworkPace
                    addOption("--limit-rate", "${pace.downloadLimitBps(pace.speedBps(), pace.mediaBps) / 8 / 1024}K")
                }
                addOption("-f", "bv*[height<=$quality][ext=mp4]+ba[ext=m4a]/b[height<=$quality][ext=mp4]")
                addOption("--merge-output-format", "mp4")
                addOption("-o", File(dir, "video.%(ext)s").absolutePath)
            }
            suspend fun execute() = runInterruptible {
                YoutubeDL.getInstance().execute(request, id.toString()) { value, _, _ ->
                    if (!NetworkPolicy.downloadsAllowed()) YoutubeDL.getInstance().destroyProcessById(id.toString())
                    AppGraph.offline.progress.value = AppGraph.offline.progress.value + (video.id to value.toInt().coerceIn(0, 100))
                }
            }
            try {
                execute()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                if (!DownloadErrors.rejectedByYouTube(e.message.orEmpty())) throw e
                // Refresh extraction once, using YouTube's Android client if the default CDN
                // formats were rejected. Never loop through failing clients indefinitely.
                refreshExtractor(force = true)
                ensureActive()
                check(NetworkPolicy.downloadsAllowed()) { "Downloads are paused by your network settings" }
                dir.listFiles()?.forEach { it.delete() }
                request.addOption("--extractor-args", "youtube:player_client=android")
                execute()
            }
            ensureActive()
            val completed = File(dir, "video.mp4")
            check(completed.isFile && completed.length() > 0) { "The download did not produce a complete video" }
            AppGraph.offline.finish(video, completed)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else {
                AppGraph.offline.failed(video.id, DownloadErrors.message(e.message.orEmpty()))
                Result.failure()
            }
        } finally {
            YoutubeDL.getInstance().destroyProcessById(id.toString())
            AppGraph.offline.progress.value = AppGraph.offline.progress.value - video.id
            dir.deleteRecursively()
        }
    }

    }

    private suspend fun refreshExtractor(force: Boolean = false) {
        val prefs = applicationContext.getSharedPreferences("download-engine", Context.MODE_PRIVATE)
        val last = prefs.getLong("last-successful-update", 0)
        val now = System.currentTimeMillis()
        if (!force && now - last in 0 until 24 * 60 * 60_000L) return
        check(NetworkPolicy.downloadsAllowed()) { "Downloads are paused by your network settings" }
        try {
            withTimeout(60_000) {
                runInterruptible { YoutubeDL.getInstance().updateYoutubeDL(applicationContext, YoutubeDL.UpdateChannel.STABLE) }
            }
            prefs.edit().putLong("last-successful-update", System.currentTimeMillis()).apply()
        } catch (e: TimeoutCancellationException) {
            if (last == 0L) throw java.io.IOException("DOWNLOADER_UPDATE_FAILED", e)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            // A previously refreshed extractor can still work while GitHub is unavailable.
            // Never start with the stale seed extractor after its first update failed.
            if (last == 0L) throw java.io.IOException("DOWNLOADER_UPDATE_FAILED", e)
        }
    }

    companion object { private val engineMutex = Mutex() }

    private fun foreground(video: VideoItem): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        // Android won't quieten a channel that already exists, so the old one makes way for a silent one.
        nm.deleteNotificationChannel("downloads")
        nm.createNotificationChannel(NotificationChannel("saved-downloads", io.github.shoalapps.swordfish.AppLanguage.translate("Saved video downloads"), NotificationManager.IMPORTANCE_MIN))
        val notification = NotificationCompat.Builder(applicationContext, "saved-downloads")
            .setSilent(true)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(io.github.shoalapps.swordfish.AppLanguage.translate("Downloading video"))
            .setContentText(video.title)
            .setOngoing(true)
            .addAction(0, io.github.shoalapps.swordfish.AppLanguage.translate("Cancel"), PendingIntent.getBroadcast(applicationContext, video.id.hashCode(),
                Intent(applicationContext, CancelVideoDownloadReceiver::class.java).putExtra("videoId", video.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(id.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else ForegroundInfo(id.hashCode(), notification)
    }
}

class CancelVideoDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra("videoId")?.takeIf(OfflineVideos::validId)?.let(AppGraph.offline::remove)
    }
}
