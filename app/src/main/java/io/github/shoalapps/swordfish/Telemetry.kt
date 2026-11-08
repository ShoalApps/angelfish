package io.github.shoalapps.swordfish

import android.app.Activity
import android.app.Application
import android.content.Context
import io.github.shoalapps.common.reports.ErrorReports

/**
 * Angelfish's error reports, handled by the shared Shoal library: only with your agreement, never your
 * accounts, history or anything identifying. See https://shoalapps.github.io/privacy.html
 */
object Telemetry {
    private val reports = ErrorReports(ErrorReports.Config(
        dsn = BuildConfig.SENTRY_DSN,
        tag = "swordfish",
        versionName = BuildConfig.VERSION_NAME,
        debug = BuildConfig.DEBUG,
        translate = { AppLanguage.translate(it) },
        askTitle = "Help fix bugs?",
        askMessage = "Angelfish can send anonymous crash and error reports to Shoal (through Sentry) so problems get fixed. They include the error, the app and Android version and the phone model, never your accounts, history or anything you've looked at. You can change this in Settings.",
        sendLabel = "Send reports",
        declineLabel = "No thanks",
        privacyLabel = "Privacy policy",
    ))

    /** Whether reports are on (for the Settings switch). */
    val enabled get() = reports.enabled
    val available get() = reports.available
    fun init(app: Application) = reports.init(app)
    fun setEnabled(context: Context, on: Boolean) = reports.setEnabled(context, on)
    fun report(e: Throwable) = reports.report(e)
    fun askOnce(activity: Activity) = reports.askOnce(activity)
}
