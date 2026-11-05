package io.github.shoalapps.swordfish.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.github.shoalapps.swordfish.AppGraph
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

object NetworkInfo {
    /** True when traffic is going through a VPN (or a VPN-style proxy app). */
    fun onVpn(): Boolean = runCatching {
        val cm = AppGraph.app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }.getOrDefault(false)

    /** True when the phone has no usable internet connection at all. */
    fun offline(): Boolean = AppGraph.store.settings.value.offlineMode || runCatching {
        val cm = AppGraph.app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)

    fun isBotCheck(msg: String?) = msg.orEmpty().let { it.contains("not a bot", true) || it.contains("LOGIN_REQUIRED") }

    internal fun isUnavailableVideo(error: Throwable?): Boolean =
        error is org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException &&
            error.message.orEmpty().let { it.contains("UNPLAYABLE") && it.contains("not available", ignoreCase = true) }

    fun friendlyError(raw: String?): String = describe(raw, null, onVpn(), offline())

    /** A sentence a person can act on, for any failure while talking to YouTube. */
    fun friendlyError(e: Throwable): String = describe(e.message, e, onVpn(), offline())

    /** Pure version of [friendlyError], so it can be unit tested. */
    fun describe(raw: String?, e: Throwable?, vpn: Boolean, offline: Boolean): String {
        val msg = raw.orEmpty()
        val cause = generateSequence(e) { it.cause }.toList()
        val bot = isBotCheck(msg) || cause.any { isBotCheck(it.message) }
        return when {
            bot && vpn -> "YouTube is blocking your VPN's address. Exclude Angelfish from the VPN with split tunnelling, then try again."
            bot -> "YouTube is asking this network to prove it isn't a bot. Try again, or switch between Wi-Fi and mobile data."
            offline -> "You're offline. Check your connection and try again."
            cause.any { it is UnknownHostException || it is ConnectException } ->
                "Couldn't reach YouTube. Check your connection and try again."
            cause.any { it is SocketTimeoutException || it is InterruptedIOException } ->
                "YouTube is taking too long to answer. Try again."
            cause.any { it is SSLException } -> "Couldn't make a secure connection. Check the date and time on your phone, then try again."
            msg.contains("age", true) && msg.contains("restrict", true) -> "This video is age-restricted."
            msg.contains("private", true) && msg.contains("video", true) -> "This video is private."
            isUnavailableVideo(e) -> "This video is unavailable. Try opening it on YouTube."
            msg.contains("unavailable", true) || msg.contains("removed", true) -> "This video isn't available."
            msg.contains("members", true) && msg.contains("only", true) -> "This video is for channel members only."
            cause.any { it is IOException } -> "Couldn't reach YouTube. Try again."
            msg.isBlank() -> "Something went wrong. Try again."
            else -> msg
        }
    }
}
