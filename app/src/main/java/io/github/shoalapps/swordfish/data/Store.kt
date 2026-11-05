package io.github.shoalapps.swordfish.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
data class Settings(
    // Appearance
    /** 0 = follow system, 1 = light, 2 = dark */
    val theme: Int = 0,
    val dynamicColor: Boolean = true,

    // Feed
    /** "recommended" or "learning" */
    val feedMode: String = "recommended",
    val learningTopics: Set<String> = setOf("science", "technology", "engineering", "maths"),

    // Shorts
    val shortsDisabled: Boolean = false,
    /** "youtube", "tiktok" or "reels" */
    val shortsLayout: String = "youtube",
    /** Max Shorts per visit to the Shorts tab; 0 = no limit */
    val shortsSessionLimit: Int = 0,
    /** Max minutes of Shorts per day; 0 = no limit */
    val shortsDailyMinutes: Int = 0,

    // Quality
    val qualityWifi: Int = 1080,
    val qualityMobile: Int = 720,

    // Offline videos. Automatic copies are disposable; explicit downloads are kept.
    val offlineMode: Boolean = false,
    val cacheCurrentVideo: Boolean = false,
    val autoDownloadCount: Int = 0,
    val downloadWifiOnly: Boolean = true,

    // New subscriptions inherit this bell setting. Existing bells stay unchanged.
    val defaultNotifications: NotificationMode = NotificationMode.ALL,
    val notificationDailyLimit: Int = 10,

    // Playback
    val backgroundPlay: Boolean = true,
    val autoPip: Boolean = true,
    val autoplayNext: Boolean = true,

    // SponsorBlock / community
    val sponsorBlock: Boolean = true,
    val sbSkip: Set<String> = setOf("sponsor", "selfpromo", "interaction"),
    val deArrow: Boolean = true,
    val ryd: Boolean = true,

    // ---- Developer settings (Morphe-style tweaks) ----
    val developerMode: Boolean = false,
    // Feed filters
    val hideLive: Boolean = false,
    val hideWatched: Boolean = false,
    val hideShortsShelf: Boolean = false,
    val keywordFilter: String = "",
    // Player
    val rememberSpeed: Boolean = false,
    val defaultSpeed: Float = 1f,
    val doubleTapSeconds: Int = 10,
    val swipeGestures: Boolean = true,
    val loopVideo: Boolean = false,
    // Layout
    val compactFeed: Boolean = false,
    val hideRelated: Boolean = false,
    val hideComments: Boolean = false,
    val navLabels: Boolean = true,
    val hideSubsAvatars: Boolean = false,
    // Misc
    val sanitizeShareLinks: Boolean = true,
    // Look
    val ambientMode: Boolean = true,

    // Shown once: the Shorts tab points people to Clownfish.
    val clipsIntroSeen: Boolean = false,

    // Updates
    /** Check GitHub once a day for a newer release. */
    val autoUpdate: Boolean = true,
)

/** Topics for the Learning feed: key, label, search phrases. */
object LearningTopics {
    data class Topic(val key: String, val label: String, val queries: List<String>)

    val all = listOf(
        Topic("science", "Science", listOf("science explained", "science documentary")),
        Topic("technology", "Technology", listOf("how technology works", "tech explained")),
        Topic("engineering", "Engineering", listOf("engineering explained", "how it's made engineering")),
        Topic("maths", "Maths", listOf("maths explained", "mathematics lecture")),
        Topic("computing", "Computing", listOf("computer science explained", "programming tutorial")),
        Topic("electronics", "Electronics", listOf("electronics explained", "circuit design tutorial")),
        Topic("physics", "Physics", listOf("physics explained", "physics lecture")),
        Topic("chemistry", "Chemistry", listOf("chemistry explained", "chemistry experiments")),
        Topic("biology", "Biology", listOf("biology explained", "nature documentary")),
        Topic("space", "Space", listOf("space and astronomy explained", "astrophysics")),
        Topic("history", "History", listOf("history documentary", "history explained")),
        Topic("geography", "Geography", listOf("geography explained", "how countries work")),
        Topic("languages", "Languages", listOf("learn a language lesson", "linguistics explained")),
        Topic("economics", "Economics", listOf("economics explained", "how money works")),
    )

    fun of(key: String) = all.firstOrNull { it.key == key }
}

@Serializable
data class Sub(val url: String, val name: String, val avatar: String? = null,
    val notifications: NotificationMode = NotificationMode.NONE)

@Serializable
enum class NotificationMode(val label: String) {
    ALL("All"), PERSONALISED("Personalised"), NONE("None")
}

@Serializable
data class Account(val name: String, val handle: String? = null, val avatar: String? = null)

/** Settings, subscriptions, history and sign-in, saved privately on the phone. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("tube", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load<Settings>("settings") ?: Settings())
    val settings: StateFlow<Settings> = _settings

    init {
        // Automatic downloads used to start out on, and slowed playback. Everyone starts over with them off once.
        if (!prefs.getBoolean("automatic-downloads-reset", false)) {
            _settings.value = _settings.value.copy(cacheCurrentVideo = false, autoDownloadCount = 0)
            prefs.edit().putString("settings", json.encodeToString(_settings.value)).putBoolean("automatic-downloads-reset", true).apply()
        }
    }

    private val _subs = MutableStateFlow(load<List<Sub>>("subs") ?: emptyList())
    val subs: StateFlow<List<Sub>> = _subs

    private val _history = MutableStateFlow(load<List<VideoItem>>("history") ?: emptyList())
    val history: StateFlow<List<VideoItem>> = _history

    private val _searches = MutableStateFlow(load<List<String>>("searches") ?: emptyList())
    val searches: StateFlow<List<String>> = _searches

    fun addSearch(q: String) {
        val t = q.trim()
        if (t.isEmpty()) return
        _searches.update { (listOf(t) + it.filterNot { s -> s.equals(t, true) }).take(20) }
        save("searches", _searches.value)
    }

    fun removeSearch(q: String) {
        _searches.update { it - q }
        save("searches", _searches.value)
    }

    private val _cookie = MutableStateFlow(prefs.getString("cookie", null))
    val cookie: StateFlow<String?> = _cookie

    private val _pageId = MutableStateFlow(prefs.getString("pageId", null))
    /** Which Brand Account channel to act as; null means the main channel. */
    val pageId: StateFlow<String?> = _pageId

    private val _account = MutableStateFlow(load<Account>("account"))
    val account: StateFlow<Account?> = _account

    val signedIn: Boolean get() = _cookie.value?.contains("SAPISID") == true

    fun updateSettings(change: (Settings) -> Settings) {
        _settings.update(change)
        save("settings", _settings.value)
    }

    fun isSubscribed(url: String?) = url != null && _subs.value.any { it.url == url }

    /** Returns true if now subscribed. */
    fun toggleSub(sub: Sub): Boolean {
        val nowSubscribed = !isSubscribed(sub.url)
        _subs.update { list -> if (nowSubscribed) list + sub.copy(notifications = settings.value.defaultNotifications) else list.filterNot { it.url == sub.url } }
        save("subs", _subs.value)
        return nowSubscribed
    }

    fun setNotifications(url: String, mode: NotificationMode) {
        _subs.update { list -> list.map { if (it.url == url) it.copy(notifications = mode) else it } }
        save("subs", _subs.value)
    }

    fun mergeSubs(incoming: List<Sub>) {
        _subs.update { list ->
            val known = list.map { it.url }.toSet()
            list + incoming.filterNot { it.url in known }
        }
        save("subs", _subs.value)
    }

    fun replaceSubs(list: List<Sub>) {
        _subs.value = list
        save("subs", list)
    }

    fun setPageId(id: String?) {
        _pageId.value = id
        prefs.edit().putString("pageId", id).apply()
    }

    /** Minutes of Shorts watched today. Resets at midnight. */
    fun shortsMsToday(): Long {
        val today = java.time.LocalDate.now().toString()
        return if (prefs.getString("shortsDay", null) == today) prefs.getLong("shortsMs", 0) else 0
    }

    fun addShortsMs(ms: Long) {
        val today = java.time.LocalDate.now().toString()
        val base = shortsMsToday()
        prefs.edit().putString("shortsDay", today).putLong("shortsMs", base + ms).apply()
    }

    /** When the app last asked GitHub for a newer release (epoch millis). */
    var lastUpdateCheck: Long
        get() = prefs.getLong("lastUpdateCheck", 0)
        set(value) = prefs.edit().putLong("lastUpdateCheck", value).apply()

    fun addHistory(item: VideoItem) {
        _history.update { list -> (listOf(item) + list.filterNot { it.id == item.id }).take(300) }
        save("history", _history.value)
    }

    fun clearHistory() {
        _history.value = emptyList()
        save("history", _history.value)
    }

    fun setCookie(cookie: String?) {
        _cookie.value = cookie
        prefs.edit().putString("cookie", cookie).apply()
    }

    fun setAccount(account: Account?) {
        _account.value = account
        if (account == null) prefs.edit().remove("account").apply() else save("account", account)
    }

    private inline fun <reified T> load(key: String): T? =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

    private inline fun <reified T> save(key: String, value: T) {
        prefs.edit().putString(key, json.encodeToString(value)).apply()
    }
}
