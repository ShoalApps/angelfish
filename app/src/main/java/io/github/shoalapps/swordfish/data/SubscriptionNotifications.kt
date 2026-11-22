package io.github.shoalapps.swordfish.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import io.github.shoalapps.swordfish.AppGraph
import io.github.shoalapps.swordfish.MainActivity
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

object SubscriptionNotifications {
    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("subscription-notifications",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SubscriptionWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }

    /** Encoding documented by YouTube.js's NotificationPreferences protobuf schema. */
    fun preferenceParams(channelId: String, mode: NotificationMode): String {
        require(channelId.matches(Regex("UC[A-Za-z0-9_-]{22}")))
        val channel = channelId.toByteArray(Charsets.UTF_8)
        val index = when (mode) { NotificationMode.ALL -> 2; NotificationMode.PERSONALISED -> 1; NotificationMode.NONE -> 3 }
        val bytes = byteArrayOf(10, channel.size.toByte()) + channel + byteArrayOf(18, 2, 8, index.toByte(), 24, 0, 32, 4)
        return java.net.URLEncoder.encode(java.util.Base64.getEncoder().encodeToString(bytes), "UTF-8")
    }

    fun resetBaseline(context: Context, url: String) {
        context.getSharedPreferences("subscription-notifications", Context.MODE_PRIVATE).edit().remove("seen:$url").apply()
    }
}

class SubscriptionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!NetworkPolicy.backgroundAllowed()) return Result.success()
        val nm = NotificationManagerCompat.from(applicationContext)
        if (!nm.areNotificationsEnabled() || Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val prefs = applicationContext.getSharedPreferences("subscription-notifications", Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        var count = if (prefs.getString("day", null) == today) prefs.getInt("count", 0) else 0
        val limit = AppGraph.store.settings.value.notificationDailyLimit.coerceIn(0, 100)
        val subs = AppGraph.store.subs.value.filter { it.notifications != NotificationMode.NONE }
        if (subs.isEmpty()) return Result.success()
        applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("subscriptions", io.github.shoalapps.swordfish.AppLanguage.translate("New videos"), NotificationManager.IMPORTANCE_DEFAULT))
        try {
            val videos = AppGraph.repo.feed(subs)
            for (sub in subs) {
                if (!NetworkPolicy.backgroundAllowed()) return Result.success()
                val mode = AppGraph.store.subs.value.firstOrNull { it.url == sub.url }?.notifications ?: continue
                if (mode == NotificationMode.NONE) continue
                val uploads = videos.filter { it.channelUrl == sub.url }.distinctBy { it.id }
                if (uploads.isEmpty()) continue
                val key = "seen:${sub.url}"
                val seen = prefs.getStringSet(key, null)
                // First check establishes a baseline, so enabling a bell never floods old uploads.
                val fresh = if (seen == null) emptyList() else uploads.filter { it.id !in seen }
                val selected = if (mode == NotificationMode.PERSONALISED) fresh.take(1) else fresh
                for (video in selected.take((limit - count).coerceAtLeast(0))) {
                    val intent = Intent(applicationContext, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                        .setData(Uri.parse(video.url)).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    val pending = PendingIntent.getActivity(applicationContext, video.id.hashCode(), intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    nm.notify(video.id.hashCode(), NotificationCompat.Builder(applicationContext, "subscriptions")
                        .setSmallIcon(android.R.drawable.ic_media_play)
                        .setContentTitle(sub.name).setContentText(video.title)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(video.title))
                        .setContentIntent(pending).setAutoCancel(true).build())
                    count++
                }
                prefs.edit().putStringSet(key, uploads.map { it.id }.toSet() + seen.orEmpty().take(100))
                    .putString("day", today).putInt("count", count).apply()
            }
            return Result.success()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { return Result.retry() }
    }
}
