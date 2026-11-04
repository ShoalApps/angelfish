package io.github.shoalapps.swordfish.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.layout.ContentScale
import androidx.glance.appwidget.updateAll
import io.github.shoalapps.swordfish.R

/** Carry on watching your last video, and jump to search, subscriptions or Shorts. */
class SwordfishWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val last = runCatching { io.github.shoalapps.swordfish.AppGraph.store.history.value.firstOrNull() }.getOrNull()
        val thumb = loadBitmap(context, last?.thumbnail)
        val main = io.github.shoalapps.swordfish.MainActivity::class.java
        provideContent {
            GlanceTheme {
                WidgetSurface {
                    WidgetHeader("Angelfish", actionStartActivity(Intent(context, io.github.shoalapps.swordfish.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))
                    if (last != null) {
                        Row(GlanceModifier.fillMaxWidth().padding(top = 10.dp).clickable(open(context, last.url, main)), verticalAlignment = Alignment.CenterVertically) {
                            if (thumb != null) Image(ImageProvider(thumb), null, GlanceModifier.width(96.dp).height(54.dp).cornerRadius(12.dp), contentScale = ContentScale.Crop)
                            Column(GlanceModifier.defaultWeight().padding(start = 10.dp)) {
                                Text(io.github.shoalapps.swordfish.AppLanguage.translate("Carry on watching"), style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Medium))
                                Text(last.title, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 2)
                                Text(last.channelName, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp), maxLines = 1)
                            }
                        }
                    }
                    ButtonRow(
                        Triple(R.drawable.ic_w_search, "Search", open(context, "materialtube://search", main)),
                        Triple(R.drawable.ic_w_subs, "Subscriptions", open(context, "materialtube://subs", main)),
                        Triple(R.drawable.ic_w_shorts, "Shorts", open(context, "materialtube://shorts", main)),
                    )
                }
            }
        }
    }
}

class SwordfishWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SwordfishWidget()
}

/** Refreshes the widget (after watching something). */
suspend fun refreshWidget(context: Context) = runCatching { SwordfishWidget().updateAll(context) }


/** A rounded tonal button with an icon and a label, like the apps' own. */
@Composable
internal fun WidgetButton(icon: Int, label: String, action: Action, modifier: GlanceModifier = GlanceModifier) {
    Column(modifier.clickable(action), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            GlanceModifier.size(48.dp).cornerRadius(16.dp).background(GlanceTheme.colors.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(icon), label, GlanceModifier.size(24.dp), colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer))
        }
        Text(io.github.shoalapps.swordfish.AppLanguage.translate(label), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1, modifier = GlanceModifier.padding(top = 4.dp))
    }
}

/** The app's fish and name along the top. */
@Composable
internal fun WidgetHeader(name: String, open: Action) {
    Row(GlanceModifier.fillMaxWidth().clickable(open), verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(R.drawable.ic_widget_badge), null, GlanceModifier.size(26.dp), colorFilter = ColorFilter.tint(GlanceTheme.colors.primary))
        Text(name, style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp), modifier = GlanceModifier.padding(start = 6.dp))
    }
}

/** The widget's rounded surface. */
@Composable
internal fun WidgetSurface(content: @Composable ColumnScope.() -> Unit) {
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(28.dp).padding(14.dp),
    ) { content() }
}

/** A row of buttons spread across the widget. */
@Composable
internal fun ButtonRow(vararg buttons: Triple<Int, String, Action>) {
    Row(GlanceModifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        buttons.forEach { (icon, label, action) -> WidgetButton(icon, label, action, GlanceModifier.defaultWeight()) }
    }
}

internal suspend fun loadBitmap(context: Context, url: String?): android.graphics.Bitmap? {
    if (url == null) return null
    return runCatching {
        val req = coil3.request.ImageRequest.Builder(context).data(url).size(320).build()
        (coil3.SingletonImageLoader.get(context).execute(req).image as? coil3.BitmapImage)?.bitmap
            ?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
    }.getOrNull()
}

internal fun open(context: Context, uri: String, cls: Class<*>): Action =
    actionStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setClass(context, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

/**
 * A widget of buttons: a slim strip (the fish, then the buttons) when short, or the app's
 * name above bigger buttons when stretched taller.
 */
@Composable
internal fun QuickWidget(name: String, launch: Action, vararg buttons: Triple<Int, String, Action>) {
    val short = androidx.glance.LocalSize.current.height < 120.dp
    WidgetSurface {
        if (short) {
            Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(R.drawable.ic_widget_badge), name, GlanceModifier.size(32.dp).clickable(launch), colorFilter = ColorFilter.tint(GlanceTheme.colors.primary))
                buttons.forEach { (icon, label, action) -> WidgetButton(icon, label, action, GlanceModifier.defaultWeight()) }
            }
        } else {
            WidgetHeader(name, launch)
            Spacer(GlanceModifier.defaultWeight())
            ButtonRow(*buttons)
            Spacer(GlanceModifier.defaultWeight())
        }
    }
}
