package io.github.shoalapps.swordfish

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.memory.MemoryCache
import coil3.disk.DiskCache
import okio.Path.Companion.toOkioPath
import io.github.shoalapps.swordfish.data.DeArrow
import io.github.shoalapps.swordfish.data.NewPipeDownloader
import io.github.shoalapps.swordfish.data.Ryd
import io.github.shoalapps.swordfish.data.SponsorBlock
import io.github.shoalapps.swordfish.data.Store
import io.github.shoalapps.swordfish.data.YouTubeRepo
import io.github.shoalapps.swordfish.data.YouTubeAccount
import io.github.shoalapps.swordfish.data.Sub
import io.github.shoalapps.swordfish.data.channelIdOf
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale
import java.util.concurrent.TimeUnit

class TubeApp : Application(), SingletonImageLoader.Factory {
    override fun attachBaseContext(base: android.content.Context) { super.attachBaseContext(AppLanguage.wrap(base)) }
    override fun onCreate() {
        super.onCreate()
        AppLanguage.init(this)
        Telemetry.init(this)
        DataMover.run(this)
        CrashReporter.install(this)
        AppGraph.init(this)
    }

    /** Big memory + disk caches so thumbnails never reload when you scroll back up. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(coil3.intercept.Interceptor { chain ->
                    val request = if (io.github.shoalapps.swordfish.data.NetworkPolicy.savingData() || io.github.shoalapps.swordfish.player.NetworkInfo.offline())
                        chain.request.newBuilder().networkCachePolicy(coil3.request.CachePolicy.DISABLED).build()
                    else chain.request
                    val original = request.data as? String
                    val preferred = original?.let(io.github.shoalapps.swordfish.data.ThumbnailUrls::preferred)
                    if (preferred != null && preferred != original) {
                        val result = chain.withRequest(request.newBuilder().data(preferred).build()).proceed()
                        if (result is coil3.request.ErrorResult) chain.withRequest(request).proceed() else result
                    } else chain.withRequest(request).proceed()
                })
            }
            .crossfade(200)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.30)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("thumbnails").toOkioPath())
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build()
            }
            .build()
}

/** Tiny service locator: one instance of each piece for the whole app. */
object AppGraph {
    lateinit var app: Application
        private set

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    val store by lazy { Store(app) }
    val repo by lazy { YouTubeRepo() }
    val sponsorBlock by lazy { SponsorBlock(http) }
    val deArrow by lazy { DeArrow(http) }
    val ryd by lazy { Ryd(http) }
    val offline by lazy { io.github.shoalapps.swordfish.data.OfflineVideos(app) }
    val actions by lazy { io.github.shoalapps.swordfish.data.OfflineActions(app) }
    val account by lazy { YouTubeAccount(http, store) }

    /** For fire-and-forget work that should outlive a screen (e.g. syncing a subscribe). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Subscribe/unsubscribe locally, and on YouTube too when signed in. */
    fun toggleSubscribe(sub: Sub) {
        val now = store.toggleSub(sub)
        io.github.shoalapps.swordfish.data.SubscriptionNotifications.resetBaseline(app, sub.url)
        val id = channelIdOf(sub.url) ?: return
        if (store.signedIn) scope.launch {
            runCatching {
                account.setSubscribed(id, now)
                if (now) account.setNotifications(id, store.subs.value.first { it.url == sub.url }.notifications)
            }.onFailure { subscriptionMessage("Saved on this phone. Couldn't sync the subscription to YouTube.") }
        }
    }

    fun setNotifications(url: String, mode: io.github.shoalapps.swordfish.data.NotificationMode) {
        store.setNotifications(url, mode)
        io.github.shoalapps.swordfish.data.SubscriptionNotifications.resetBaseline(app, url)
        val id = channelIdOf(url) ?: return
        if (store.signedIn) scope.launch {
            runCatching { account.setNotifications(id, mode) }
                .onFailure { subscriptionMessage("Bell saved on this phone. Couldn't sync it to YouTube.") }
        }
    }

    private fun subscriptionMessage(message: String) {
        scope.launch(Dispatchers.Main) { android.widget.Toast.makeText(app, message, android.widget.Toast.LENGTH_LONG).show() }
    }

    /** After signing in: load the profile and pull in YouTube subscriptions. */
    /** Takes up accounts signed into in Shoal, when they've changed. */
    fun pullManagerAccounts() {
        val s = ManagerAccounts.read(app) ?: return
        if (!ManagerAccounts.changed(app, s)) return
        val yt = s.cookies["youtube"]
        if (yt != null) {
            ManagerAccounts.applyCookies("https://www.youtube.com", yt)
            store.setCookie(yt)
            store.setPageId(s.youtubePageId)
            syncAccount()
            scope.launch { actions.flush() }
        }
    }

    fun syncAccount(replaceSubs: Boolean = false) {
        if (!store.signedIn) return
        scope.launch {
            runCatching { account.refreshAccount() }
            runCatching {
                val subs = account.subscriptions()
                if (replaceSubs) store.replaceSubs(subs) else store.mergeSubs(subs)
            }
        }
    }

    /** Switch which channel on the Google account Tube acts as. */
    fun selectChannel(choice: io.github.shoalapps.swordfish.data.ChannelChoice) {
        store.setPageId(choice.pageId)
        store.setAccount(io.github.shoalapps.swordfish.data.Account(choice.name, choice.handle, choice.avatar))
        syncAccount(replaceSubs = true)
        scope.launch { actions.flush() }
    }

    /** A newer release found on GitHub, its download progress, and any message about it. */
    object Update {
        var available by androidx.compose.runtime.mutableStateOf<io.github.shoalapps.swordfish.data.Updater.Release?>(null)
        var progress by androidx.compose.runtime.mutableStateOf<Float?>(null)
        var message by androidx.compose.runtime.mutableStateOf<String?>(null)
    }

    /** Looks for a newer release: at most once a day automatically, or right away when asked. */
    fun checkForUpdate(force: Boolean = false) {
        // Shoal handles updates when it is installed.
        if (!force && io.github.shoalapps.swordfish.ShoalManager.installed(app)) return
        val U = io.github.shoalapps.swordfish.data.Updater
        if (!U.canUpdate(app)) {
            if (force) Update.message = "Development builds don't update themselves"
            return
        }
        val now = System.currentTimeMillis()
        if (!force && !io.github.shoalapps.swordfish.data.NetworkPolicy.backgroundAllowed()) return
        if (!force && (!store.settings.value.autoUpdate || now - store.lastUpdateCheck < 20 * 3600_000L)) return
        scope.launch {
            val latest = runCatching { U.latest(http) }.getOrNull()
            if (latest != null) store.lastUpdateCheck = now
            when {
                latest == null -> if (force) Update.message = "Couldn't reach GitHub. Try again later."
                latest.build > U.currentBuild(app) -> Update.available = latest
                force -> Update.message = "You're on the latest version"
            }
        }
    }

    /** Downloads the update and hands it to Android's installer, which asks the user to confirm. */
    fun installUpdate() {
        if (!io.github.shoalapps.swordfish.data.NetworkPolicy.backgroundAllowed()) {
            Update.message = "Data Saver is on. Connect to an unmetered network to download the update."
            return
        }
        val release = Update.available ?: return
        Update.available = null
        Update.progress = 0f
        scope.launch {
            runCatching {
                val apk = io.github.shoalapps.swordfish.data.Updater.download(http, release, app.cacheDir) { Update.progress = it }
                kotlinx.coroutines.withContext(Dispatchers.Main) { io.github.shoalapps.swordfish.data.Updater.install(app, apk) }
            }.onFailure { Update.message = "Couldn't download the update. Try again later." }
            Update.progress = null
        }
    }

    /** True while the phone has a working internet connection; screens use it to recover by themselves. */
    val online = kotlinx.coroutines.flow.MutableStateFlow(true)

    private fun watchNetwork() {
        val cm = app.getSystemService(android.net.ConnectivityManager::class.java) ?: return
        online.value = !io.github.shoalapps.swordfish.player.NetworkInfo.offline()
        runCatching {
            cm.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) {
                    online.value = caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) && !store.settings.value.offlineMode
                    io.github.shoalapps.swordfish.data.NetworkPolicy.changed()
                    scope.launch {
                        offline.reconcile()
                        if (online.value) { actions.reconnect(); actions.flush() }
                    }
                }
                override fun onLost(network: android.net.Network) { online.value = false; scope.launch { offline.reconcile() } }
            })
        }
    }

    /** Shorts fetched at startup so the Shorts tab opens instantly. */
    @Volatile var shortsCache: List<io.github.shoalapps.swordfish.data.VideoItem>? = null

    /** Warm things up in the background after launch. */
    private fun warmUp() {
        scope.launch {
            kotlinx.coroutines.delay(2500) // let Home load first
            if (store.settings.value.shortsDisabled || !io.github.shoalapps.swordfish.data.NetworkPolicy.backgroundAllowed()) return@launch
            val fromAccount = if (store.signedIn) runCatching { account.home().shorts }.getOrDefault(emptyList()) else emptyList()
            val more = if (fromAccount.size >= 20) emptyList() else runCatching { repo.shorts(store.subs.value) }.getOrDefault(emptyList())
            val list = (fromAccount + more).distinctBy { it.id }
            shortsCache = list
            repo.prefetch(list.take(io.github.shoalapps.swordfish.player.NetworkPace.preloadCount()).map { it.id })
        }
    }

    /** Adds a video to Watch later and says whether it worked. */
    fun saveToWatchLater(context: android.content.Context, videoId: String) {
        val app = context.applicationContext
        scope.launch {
            val ok = runCatching { account.saveToWatchLater(videoId) }.isSuccess
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(app, if (ok) "Watch later saved; account changes sync when online" else "Couldn't save to Watch later", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun signOut() {
        store.setCookie(null)
        store.setAccount(null)
        store.setPageId(null)
        runCatching { android.webkit.CookieManager.getInstance().removeAllCookies(null) }
    }

    fun init(application: Application) {
        app = application
        val locale = Locale.getDefault()
        NewPipe.init(
            NewPipeDownloader(http),
            Localization.fromLocale(locale),
            ContentCountry(locale.country.ifBlank { "GB" }),
        )
        // YouTube increasingly wants a proof-of-origin token; data/potoken makes one the way NewPipe
        // does. Off until it's been tested on a connection YouTube isn't throttling; to try it:
        // YoutubeStreamExtractor.setPoTokenProvider(io.github.shoalapps.swordfish.data.potoken.PoTokenProviderImpl)
        if (store.signedIn && store.account.value == null) syncAccount()
        scope.launch { store.settings.collect {
            online.value = !io.github.shoalapps.swordfish.player.NetworkInfo.offline()
            offline.reconcile()
            if (online.value) { actions.schedule(); actions.flush() }
        } }
        androidx.core.content.ContextCompat.registerReceiver(app, object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                io.github.shoalapps.swordfish.data.NetworkPolicy.changed()
                scope.launch { offline.reconcile(); actions.flush() }
            }
        }, android.content.IntentFilter(android.net.ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        io.github.shoalapps.swordfish.data.SubscriptionNotifications.schedule(app)
        watchNetwork()
        scope.launch { actions.schedule(); actions.flush() }
        warmUp()
    }
}
