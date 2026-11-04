package io.github.shoalapps.swordfish.tv

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.shoalapps.swordfish.AppGraph
import org.json.JSONObject

/**
 * TV mode: YouTube's own TV app (youtube.com/tv) full screen, with VacuumTube's modules (ad
 * blocking, SponsorBlock, DeArrow, Return YouTube Dislike, controller support and more; MIT, see
 * assets/tv/vacuumtube.js) injected before YouTube's scripts run. Remotes, keyboards and
 * Bluetooth/USB controllers all drive it.
 */
class TvActivity : ComponentActivity() {
    override fun attachBaseContext(base: android.content.Context) { super.attachBaseContext(io.github.shoalapps.swordfish.AppLanguage.wrap(base)) }
    internal lateinit var web: WebView
    private var saverDialogShown = false
    private val pad = Gamepad { buttons, axes ->
        web.evaluateJavascript("window.__pufferfishPad&&window.__pufferfishPad([${buttons.joinToString(",")}],[${axes.joinToString(",")}])", null)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Debug builds can be inspected from a computer (chrome://inspect); release builds can't.
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) WebView.setWebContentsDebuggingEnabled(true)
        web = WebView(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        setContentView(web)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = TvIdentity.userAgent(packageManager.getPackageInfo(packageName, 0).versionName ?: "1")
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            WebSettingsCompat.setUserAgentMetadata(web.settings, TvIdentity.metadata())
        }

        web.addJavascriptInterface(Bridge(this), "PufferfishTV")
        val script = assets.open("tv/vacuumtube.js").bufferedReader().use { it.readText() }
        val exitHook = "window.close=function(){PufferfishTV.exit()};"
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, exitHook + script, setOf("https://www.youtube.com"))
        }

        web.webViewClient = object : WebViewClient() {
            // Older WebViews without document-start scripts: inject as early as we can.
            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) && url.orEmpty().startsWith("https://www.youtube.com/tv")) {
                    view.evaluateJavascript(exitHook + script, null)
                }
            }

            // Stay on YouTube's TV app; anything else (help pages, sign-in links) opens outside.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host.orEmpty()
                if (host == "www.youtube.com" || host == "youtube.com" || host.endsWith(".google.com") || host == "accounts.youtube.com") return false
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                return true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()
            override fun onCloseWindow(window: WebView?) = finish()
        }

        // Back goes to YouTube's TV app (its own back key), which asks before exiting from Home.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = sendKey(KeyEvent.KEYCODE_ESCAPE)
        })

        deepLink = deepLinkOf(intent)
        if (!io.github.shoalapps.swordfish.data.NetworkPolicy.savingData()) {
            if (savedInstanceState != null) web.restoreState(savedInstanceState) else web.loadUrl("https://www.youtube.com/tv")
        }
        lifecycleScope.launch {
            io.github.shoalapps.swordfish.data.NetworkPolicy.changes.collect {
                if (io.github.shoalapps.swordfish.data.NetworkPolicy.savingData() && !saverDialogShown) {
                    saverDialogShown = true
                    web.stopLoading()
                    web.loadUrl("about:blank")
                    android.app.AlertDialog.Builder(this@TvActivity)
                        .setTitle(io.github.shoalapps.swordfish.AppLanguage.translate("Data Saver is on"))
                        .setMessage(io.github.shoalapps.swordfish.AppLanguage.translate("Connect to unmetered Wi-Fi for TV mode, or use Angelfish's video player to keep watching with data limits."))
                        .setPositiveButton(io.github.shoalapps.swordfish.AppLanguage.translate("Back to Angelfish")) { _, _ -> finish() }
                        .setOnCancelListener { finish() }
                        .show()
                }
            }
        }
        web.requestFocus()
    }

    /** A video to open, handed to YouTube's TV app as a deep link (via VacuumTube's h5vcc module). */
    private var deepLink: String? = null

    private fun deepLinkOf(intent: Intent?): String? =
        intent?.getStringExtra(EXTRA_VIDEO_ID)?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }?.let { "https://www.youtube.com/watch?v=$it" }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val link = deepLinkOf(intent) ?: return
        web.evaluateJavascript("window.__pufferfishEmit&&window.__pufferfishEmit('deeplink',${JSONObject.quote(link)})", null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    private fun sendKey(code: Int) {
        web.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        web.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun isController(source: Int) =
        source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Controller buttons go to the virtual gamepad, so YouTube treats it as a real controller.
        if (isController(event.source) && pad.onKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (isController(event.source) && event.action == MotionEvent.ACTION_MOVE && pad.onMotion(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        web.evaluateJavascript("window.__pufferfishEmit&&window.__pufferfishEmit('focus')", null)
    }

    override fun onPause() {
        pad.release()
        web.evaluateJavascript("window.__pufferfishEmit&&window.__pufferfishEmit('blur')", null)
        super.onPause()
    }

    override fun onStop() {
        // Like the TV apps: leaving TV mode pauses the video.
        web.evaluateJavascript("document.querySelector('video')?.pause()", null)
        web.onPause()
        super.onStop()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    /** What VacuumTube's modules ask of their host. Settings live in the "tv" preferences. */
    private class Bridge(private val activity: TvActivity) {
        private val prefs = activity.getSharedPreferences("tv", Context.MODE_PRIVATE)

        @JavascriptInterface fun getConfig(): String = prefs.getString("config", "{}") ?: "{}"

        @JavascriptInterface fun setConfig(json: String) {
            prefs.edit().putString("config", json).apply()
        }

        /** First-run defaults follow the app's own settings. */
        @JavascriptInterface fun getAppDefaults(): String {
            val s = AppGraph.store.settings.value
            return JSONObject()
                .put("adblock", true)
                .put("sponsorblock", s.sponsorBlock)
                .put("sponsorblock_skip_sponsor", "sponsor" in s.sbSkip)
                .put("sponsorblock_skip_selfpromo", "selfpromo" in s.sbSkip)
                .put("sponsorblock_skip_interaction", "interaction" in s.sbSkip)
                .put("sponsorblock_skip_intro", "intro" in s.sbSkip)
                .put("sponsorblock_skip_outro", "outro" in s.sbSkip)
                .put("dearrow", s.deArrow)
                .put("dislikes", s.ryd)
                .put("hide_shorts", s.shortsDisabled)
                .toString()
        }

        @JavascriptInterface fun openUrl(url: String) {
            val uri = Uri.parse(url)
            if (uri.scheme != "https") return
            activity.runOnUiThread { runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, uri)) } }
        }

        @JavascriptInterface fun getDeepLink(): String? = activity.deepLink.also { activity.deepLink = null }

        @JavascriptInterface fun exit() {
            activity.runOnUiThread { activity.finish() }
        }
    }

    companion object {
        const val EXTRA_VIDEO_ID = "video_id"

        fun open(context: Context, videoId: String? = null) {
            context.startActivity(Intent(context, TvActivity::class.java).putExtra(EXTRA_VIDEO_ID, videoId))
        }
    }
}

/**
 * The identity shown to YouTube. Its TV app only opens for TV/console browsers, so like VacuumTube
 * this is a Cobalt (YouTube's TV browser) user agent in Cobalt's exact format, naming Shoal as
 * the device.
 */
object TvIdentity {
    private const val COBALT = "27.lts.3.1040976-gold"
    private const val V8 = "13.8.258.54"
    private const val STARBOARD = 18
    private const val BRAND = "Angelfish"

    private fun chipset(): String = when (Build.SUPPORTED_ABIS.firstOrNull()) {
        "arm64-v8a" -> "arm64"
        "armeabi-v7a" -> "arm"
        "x86_64" -> "x8664"
        "x86" -> "x86"
        else -> "Unknown"
    }

    fun userAgent(version: String): String {
        val year = java.time.Year.now().value
        return "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}) Cobalt/$COBALT (unlike Gecko) v8/$V8-jit gles " +
            "Starboard/$STARBOARD, ${BRAND}_DESKTOP_${chipset()}_$year/$version ($BRAND, Android) " +
            "io.github.shoalapps.swordfish/$version"
    }

    fun metadata(): UserAgentMetadata {
        val full = COBALT.substringBefore('-').split('.').take(3).joinToString(".")
        return UserAgentMetadata.Builder()
            .setBrandVersionList(listOf(UserAgentMetadata.BrandVersion.Builder().setBrand(BRAND).setMajorVersion(COBALT.substringBefore('.')).setFullVersion(full).build()))
            .setFullVersion(full)
            .setPlatform("Starboard")
            .setPlatformVersion("")
            .setArchitecture(if (chipset().startsWith("arm")) "arm" else "x86")
            .setModel("")
            .setMobile(false)
            .setBitness(if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) 64 else 32)
            .setWow64(false)
            .build()
    }
}
