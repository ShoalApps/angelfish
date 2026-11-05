@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.shoalapps.swordfish.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import io.github.shoalapps.swordfish.LocalisedIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import io.github.shoalapps.swordfish.LocalisedText as Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import io.github.shoalapps.swordfish.AppGraph
import io.github.shoalapps.swordfish.data.SbCategories
import io.github.shoalapps.swordfish.data.Settings
import io.github.shoalapps.swordfish.ui.components.LocalBottomPadding
import io.github.shoalapps.swordfish.ui.components.MorphShape
import io.github.shoalapps.swordfish.ui.components.PolygonShape
import io.github.shoalapps.swordfish.ui.components.listPadding
import io.github.shoalapps.swordfish.ui.components.openExternal
import io.github.shoalapps.swordfish.ui.components.toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val qualityOptions = listOf(2160, 1440, 1080, 720, 480, 360, 240, 144)

/** A pending "pick one of these" dialog. */
private data class Choice(val title: String, val options: List<Pair<String, String>>, val selected: String, val onPick: (String) -> Unit)

// ---------------- Main settings ----------------

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onSwitchChannel: () -> Unit,
    onDeveloper: () -> Unit,
    onAbout: () -> Unit,
) {
    val store = AppGraph.store
    val s by store.settings.collectAsState()
    val account by store.account.collectAsState()
    val cookie by store.cookie.collectAsState()
    val signedIn = cookie?.contains("SAPISID") == true
    var choice by remember { mutableStateOf<Choice?>(null) }
    var clearPositions by remember { mutableStateOf(false) }
    val update: ((Settings) -> Settings) -> Unit = { store.updateSettings(it) }

    SettingsScaffold("Settings", onBack) {
        item(key = "support") { val ctx = androidx.compose.ui.platform.LocalContext.current; SupportCard { openExternal(ctx, "https://liberapay.com/ShoalApps") } }
        group("Account") {
            if (signedIn) {
                row { sh -> Row2(account?.name ?: "Signed in", account?.handle ?: "YouTube account", sh) {
                    TextButton(onClick = { AppGraph.signOut() }) { Text("Sign out") }
                } }
                row { sh -> Link("Switch channel", "Use a different channel on this Google account", sh, onClick = onSwitchChannel) }
                row { sh -> Link("Import subscriptions from YouTube", "Adds any channels you follow on YouTube", sh) { AppGraph.syncAccount() } }
            } else {
                row { sh -> Link("Sign in to YouTube", "Your feed, subscriptions, likes and Watch later", sh, onClick = onSignIn) }
            }
        }

        item(key = "app-language") { io.github.shoalapps.swordfish.AppLanguage.SettingsRow() }

        group("Appearance") {
            row { sh -> Segmented("Theme", listOf("Auto", "Light", "Dark"), s.theme, sh) { i -> update { it.copy(theme = i) } } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                row { sh -> Toggle("Dynamic colour", "Match colours to your wallpaper", s.dynamicColor, sh) { v -> update { it.copy(dynamicColor = v) } } }
            }
        }

        group("Feed") {
            row { sh ->
                Segmented("Home feed", listOf("Recommended", "Learning"), if (s.feedMode == "learning") 1 else 0, sh) { i ->
                    update { it.copy(feedMode = if (i == 1) "learning" else "recommended") }
                }
            }
            if (s.feedMode == "learning") {
                row { sh ->
                    Tile(sh) {
                        Text("Learning topics", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
                        TopicPicker(s.learningTopics) { t -> update { it.copy(learningTopics = t) } }
                    }
                }
            }
        }

        group("Shorts") {
            row { sh ->
                Toggle("Turn off Shorts", "Hides the Shorts tab, shelves and Shorts in every feed", s.shortsDisabled, sh) { v ->
                    update { it.copy(shortsDisabled = v) }
                }
            }
            if (!s.shortsDisabled) {
                row { sh ->
                    val keys = listOf("youtube", "tiktok", "reels")
                    Segmented("Shorts layout", listOf("YouTube", "TikTok", "Reels"), keys.indexOf(s.shortsLayout).coerceAtLeast(0), sh) { i ->
                        update { it.copy(shortsLayout = keys[i]) }
                    }
                }
                row { sh ->
                    val opts = listOf(0, 5, 10, 20, 50).map { (if (it == 0) "No limit" else "$it Shorts") to it.toString() }
                    Link("Limit per visit", if (s.shortsSessionLimit == 0) "No limit" else "${s.shortsSessionLimit} Shorts, then a break screen", sh) {
                        choice = Choice("Shorts per visit", opts, s.shortsSessionLimit.toString()) { v -> update { it.copy(shortsSessionLimit = v.toInt()) } }
                    }
                }
                row { sh ->
                    val today = store.shortsMsToday() / 60_000
                    val opts = listOf(0, 10, 15, 30, 60, 90).map { (if (it == 0) "No limit" else "$it minutes") to it.toString() }
                    Link(
                        "Daily time limit",
                        (if (s.shortsDailyMinutes == 0) "No limit" else "${s.shortsDailyMinutes} minutes a day") + " · $today min today",
                        sh,
                    ) {
                        choice = Choice("Shorts per day", opts, s.shortsDailyMinutes.toString()) { v -> update { it.copy(shortsDailyMinutes = v.toInt()) } }
                    }
                }
            }
        }

        group("Video quality") {
            row { sh ->
                Link("On unmetered networks", "${s.qualityWifi}p", sh) {
                    choice = Choice("Quality on unmetered networks", qualityChoices(), s.qualityWifi.toString()) { v -> update { it.copy(qualityWifi = v.toInt()) } }
                }
            }
            row { sh ->
                Link("On metered networks", "${s.qualityMobile}p", sh) {
                    choice = Choice("Quality on metered networks", qualityChoices(), s.qualityMobile.toString()) { v -> update { it.copy(qualityMobile = v.toInt()) } }
                }
            }
        }

        group("Offline videos") {
            row { sh -> Toggle("Offline mode", "Show downloads and save account actions for later", s.offlineMode, sh) { v -> update { it.copy(offlineMode = v) } } }
            row { sh -> Toggle("Keep the current video offline", "Off by default. Downloads the whole video as you watch, when your connection has room, and removes it when you change videos", s.cacheCurrentVideo, sh) { v -> update { it.copy(cacheCurrentVideo = v) } } }
            row { sh ->
                Tile(sh) {
                    var count by remember(s.autoDownloadCount) { mutableStateOf(s.autoDownloadCount.toString()) }
                    OutlinedTextField(value = count, onValueChange = { value ->
                        if (value.isEmpty() || value.all { it.isDigit() } && value.length <= 2) {
                            count = value
                            value.toIntOrNull()?.let { n -> if (n in 0..50) update { it.copy(autoDownloadCount = n) } }
                        }
                    }, label = { Text("Subscription videos to keep offline (0-50)") },
                        supportingText = { Text("Off (0) by default. The newest from your subscriptions download quietly, when you're not watching anything.") }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                }
            }
            row { sh -> Toggle("Allow downloads on mobile data", "Off by default. Applies to saved and automatic videos. Data Saver always pauses downloads on metered networks.", !s.downloadWifiOnly, sh) { v -> update { it.copy(downloadWifiOnly = !v) } } }
            row { sh -> Row2("Your downloads", "Open You to play, keep or remove offline videos", sh) {} }
        }

        group("Notifications") {
            row { sh -> Link("Default bell for new subscriptions", s.defaultNotifications.label, sh) {
                choice = Choice("Default notification bell", io.github.shoalapps.swordfish.data.NotificationMode.entries.map { it.label to it.name }, s.defaultNotifications.name) { v -> update { it.copy(defaultNotifications = io.github.shoalapps.swordfish.data.NotificationMode.valueOf(v)) } }
            } }
            row { sh -> Link("Daily notification limit", "${s.notificationDailyLimit} notifications per day", sh) {
                choice = Choice("Daily notification limit", listOf(0, 1, 3, 5, 10, 20, 50).map { (if (it == 0) "Off" else "$it per day") to it.toString() }, s.notificationDailyLimit.toString()) { v -> update { it.copy(notificationDailyLimit = v.toInt()) } }
            } }
            row { sh -> Row2("Bell options", "All: new uploads. Personalised: the latest new upload per channel per check. None: no alerts. Checks run about every 30 minutes when Android allows; Data Saver pauses checks on metered networks.", sh) {} }
        }

        group("Playback") {
            row { sh -> Toggle("Remember where I stopped", "Save watch positions on this device only", s.rememberWatchPositions, sh) { enabled ->
                update { it.copy(rememberWatchPositions = enabled) }
                if (!enabled) clearPositions = true
            } }
            row { sh -> Toggle("Autoplay", "Play the next video when one ends", s.autoplayNext, sh) { v -> update { it.copy(autoplayNext = v) } } }
            row { sh -> Toggle("Background play", "Keep audio playing with the screen off or app closed", s.backgroundPlay, sh) { v -> update { it.copy(backgroundPlay = v) } } }
            row { sh -> Toggle("Picture-in-picture", "Shrink to a floating window when you leave the app", s.autoPip, sh) { v -> update { it.copy(autoPip = v) } } }
            row { sh -> Toggle("Ambient mode", "A soft glow of the video's colours around the player", s.ambientMode, sh) { v -> update { it.copy(ambientMode = v) } } }
        }

        group("SponsorBlock") {
            row { sh -> Toggle("Skip segments", "Crowd-sourced skipping of sponsors and other filler", s.sponsorBlock, sh) { v -> update { it.copy(sponsorBlock = v) } } }
            if (s.sponsorBlock) {
                SbCategories.all.forEach { cat ->
                    row { sh ->
                        Toggle(cat.label, null, cat.key in s.sbSkip, sh, leading = {
                            Box(Modifier.size(14.dp).clip(CircleShape).background(cat.color))
                        }) { on -> update { it.copy(sbSkip = if (on) it.sbSkip + cat.key else it.sbSkip - cat.key) } }
                    }
                }
            }
        }

        group("Community") {
            row { sh -> Toggle("DeArrow", "Replace clickbait titles and thumbnails", s.deArrow, sh) { v -> update { it.copy(deArrow = v) } } }
            row { sh -> Toggle("Return YouTube Dislike", "Show dislike counts", s.ryd, sh) { v -> update { it.copy(ryd = v) } } }
        }

        group("Updates") {
            row { sh ->
                val context = androidx.compose.ui.platform.LocalContext.current
                if (io.github.shoalapps.swordfish.ShoalManager.installed(context)) {
                    Link("Managed by Shoal", "Updates are checked and installed by Shoal", sh) { io.github.shoalapps.swordfish.ShoalManager.checkForUpdates(context) }
                } else {
                    Toggle("Check for updates", "Look for a new version on GitHub once a day", s.autoUpdate, sh) { v -> update { it.copy(autoUpdate = v) } }
                }
            }
            row { sh ->
                val context = androidx.compose.ui.platform.LocalContext.current
                Link("Check for updates now", "Opens Shoal", sh) { io.github.shoalapps.swordfish.ShoalManager.checkForUpdates(context) }
            }
        }

        group("Data") {
            row { sh -> Link("Clear watch positions", "Remove all saved playback positions on this device", sh) { clearPositions = true } }
        }

        group("More") {
            row { sh -> Link("What's new", "Changes in this version", sh) { io.github.shoalapps.swordfish.WhatsNew.open() } }
            row { sh ->
                val context = androidx.compose.ui.platform.LocalContext.current
                Link("TV mode", "YouTube's TV interface for big screens, remotes and controllers", sh) {
                    io.github.shoalapps.swordfish.tv.TvActivity.open(context)
                }
            }
            if (s.developerMode) row { sh -> Link("Developer settings", "Advanced tweaks, filters and debugging", sh, onClick = onDeveloper) }
            row { sh -> Link("About Angelfish", "Version, credits and licence", sh, onClick = onAbout) }
        }
    }

    choice?.let { c -> ChoiceDialog(c) { choice = null } }
    if (clearPositions) AlertDialog(
        onDismissRequest = { clearPositions = false },
        title = { Text("Clear watch positions?") },
        text = { Text("Remove saved positions and Continue watching from this device?") },
        confirmButton = { TextButton(onClick = { store.clearWatchPositions(); clearPositions = false }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { clearPositions = false }) { Text("Keep") } },
    )
}

private fun qualityChoices() = qualityOptions.map { (if (it == 1080) "${it}p (default)" else "${it}p") to it.toString() }

// ---------------- Developer settings (Morphe-style) ----------------

@Composable
fun DeveloperSettingsScreen(onBack: () -> Unit) {
    val store = AppGraph.store
    val s by store.settings.collectAsState()
    val context = LocalContext.current
    var choice by remember { mutableStateOf<Choice?>(null) }
    var keywordDialog by remember { mutableStateOf(false) }
    var debugText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val update: ((Settings) -> Settings) -> Unit = { store.updateSettings(it) }

    SettingsScaffold("Developer settings", onBack) {
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text(
                    "Extra tweaks in the style of Morphe. Some change how the app behaves in ways that may surprise you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        group("Feed filters") {
            row { sh -> Toggle("Hide live streams", null, s.hideLive, sh) { v -> update { it.copy(hideLive = v) } } }
            row { sh -> Toggle("Hide watched videos", "Anything in your history", s.hideWatched, sh) { v -> update { it.copy(hideWatched = v) } } }
            row { sh -> Toggle("Hide Shorts shelves", null, s.hideShortsShelf, sh) { v -> update { it.copy(hideShortsShelf = v) } } }
            row { sh -> Link("Keyword filter", s.keywordFilter.ifBlank { "Hide videos whose title or channel contains these words" }, sh) { keywordDialog = true } }
        }

        group("Player") {
            row { sh -> Toggle("Remember playback speed", "Keep the last speed you picked", s.rememberSpeed, sh) { v -> update { it.copy(rememberSpeed = v) } } }
            row { sh ->
                val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
                Link("Default playback speed", "${s.defaultSpeed}x", sh) {
                    choice = Choice("Default speed", speeds.map { (if (it == 1f) "Normal" else "${it}x") to it.toString() }, s.defaultSpeed.toString()) { v ->
                        update { it.copy(defaultSpeed = v.toFloat()) }
                    }
                }
            }
            row { sh ->
                Link("Double-tap seek", "${s.doubleTapSeconds} seconds", sh) {
                    choice = Choice("Double-tap seek", listOf(5, 10, 15, 20, 30).map { "$it seconds" to it.toString() }, s.doubleTapSeconds.toString()) { v ->
                        update { it.copy(doubleTapSeconds = v.toInt()) }
                    }
                }
            }
            row { sh -> Toggle("Swipe controls", "Swipe up/down in full screen: left for brightness, right for volume", s.swipeGestures, sh) { v -> update { it.copy(swipeGestures = v) } } }
            row { sh -> Toggle("Loop videos", "Replay instead of ending", s.loopVideo, sh) { v -> update { it.copy(loopVideo = v) } } }
        }

        group("Layout") {
            row { sh -> Toggle("Compact feed", "Small thumbnails in lists instead of full-width cards", s.compactFeed, sh) { v -> update { it.copy(compactFeed = v) } } }
            row { sh -> Toggle("Hide related videos", "No 'up next' list under the player", s.hideRelated, sh) { v -> update { it.copy(hideRelated = v) } } }
            row { sh -> Toggle("Hide comments", null, s.hideComments, sh) { v -> update { it.copy(hideComments = v) } } }
            row { sh -> Toggle("Navigation labels", "Show text under the navigation bar icons", s.navLabels, sh) { v -> update { it.copy(navLabels = v) } } }
            row { sh -> Toggle("Hide channel row in Subscriptions", null, s.hideSubsAvatars, sh) { v -> update { it.copy(hideSubsAvatars = v) } } }
        }

        group("Sharing") {
            row { sh -> Toggle("Clean share links", "Remove tracking (si=…) and use short youtu.be links", s.sanitizeShareLinks, sh) { v -> update { it.copy(sanitizeShareLinks = v) } } }
        }

        group("Debug") {
            row { sh ->
                Link("Channel switcher response", "What YouTube sent when listing your channels", sh) {
                    debugText = "Loading…"
                    scope.launch {
                        runCatching { AppGraph.account.channels() }
                        debugText = AppGraph.account.lastChannelDebug.ifBlank { "Nothing yet. Sign in first." }
                    }
                }
            }
            row { sh ->
                Link("Clear image cache", "Thumbnails will download again", sh) {
                    val loader = SingletonImageLoader.get(context)
                    loader.memoryCache?.clear()
                    loader.diskCache?.clear()
                    toast(context, "Image cache cleared")
                }
            }
            row { sh -> Link("Clear watch positions", "Remove all saved playback positions on this device", sh) {
                store.clearWatchPositions()
                toast(context, "Watch positions cleared")
            } }
            row { sh ->
                Link("Reset all settings", "Keeps your sign-in, subscriptions and history", sh) {
                    store.updateSettings { Settings() }
                    toast(context, "Settings reset")
                    onBack()
                }
            }
            row { sh ->
                Link("Turn off developer settings", null, sh) {
                    update { it.copy(developerMode = false) }
                    onBack()
                }
            }
        }
    }

    choice?.let { c -> ChoiceDialog(c) { choice = null } }

    if (keywordDialog) {
        var text by remember { mutableStateOf(s.keywordFilter) }
        AlertDialog(
            onDismissRequest = { keywordDialog = false },
            title = { Text("Keyword filter") },
            text = {
                Column {
                    Text("Separate words with commas.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().padding(top = 12.dp), placeholder = { Text("e.g. reaction, prank") })
                }
            },
            confirmButton = {
                TextButton(onClick = { update { it.copy(keywordFilter = text.trim()) }; keywordDialog = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { keywordDialog = false }) { Text("Cancel") } },
        )
    }

    debugText?.let { text ->
        AlertDialog(
            onDismissRequest = { debugText = null },
            title = { Text("Channel switcher") },
            text = {
                Text(
                    text,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("debug", text))
                    toast(context, "Copied")
                }) { Text("Copy") }
            },
            dismissButton = { TextButton(onClick = { debugText = null }) { Text("Close") } },
        )
    }
}

// ---------------- About ----------------

private const val GITHUB_USER = "calamityCocoa"

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val s by AppGraph.store.settings.collectAsState()
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    var taps by remember { mutableIntStateOf(0) }

    SettingsScaffold("About", onBack) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                MorphingAvatar("https://github.com/$GITHUB_USER.png?size=460", 200.dp)
                Text("Angelfish", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 20.dp))
                Text(
                    "Made by @$GITHUB_USER",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { openExternal(context, "https://github.com/$GITHUB_USER") }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
        group("App", showTitle = false) {
            row { sh ->
                Link("Version", version, sh, trailingArrow = false) {
                    if (s.developerMode) {
                        toast(context, "Developer settings are already on")
                        return@Link
                    }
                    taps++
                    when {
                        taps >= 7 -> {
                            AppGraph.store.updateSettings { it.copy(developerMode = true) }
                            toast(context, "Developer settings unlocked. Find them in Settings.")
                        }
                        taps >= 3 -> toast(context, "${7 - taps} more taps to unlock developer settings")
                    }
                }
            }
            row { sh -> Link("Check for updates", "Opens Shoal", sh) { io.github.shoalapps.swordfish.ShoalManager.checkForUpdates(context) } }
            if (io.github.shoalapps.swordfish.Telemetry.available) row { sh -> Toggle("Send error reports", "Anonymous crash and error reports help fix bugs. Never your accounts or history.", io.github.shoalapps.swordfish.Telemetry.enabled, sh) { v -> io.github.shoalapps.swordfish.Telemetry.setEnabled(context, v) } }
            row { sh -> Link("Privacy policy", "What Angelfish sends, and to whom", sh) { openExternal(context, "https://shoalapps.github.io/privacy.html") } }
            row { sh -> Link("Legal", "Licences, trademarks and notices", sh) { openExternal(context, "https://shoalapps.github.io/legal.html") } }
            row { sh -> Link("Source code", "github.com/ShoalApps/angelfish", sh) { openExternal(context, "https://github.com/ShoalApps/angelfish") } }
            row { sh -> Link("Website", "shoalapps.github.io", sh) { openExternal(context, "https://shoalapps.github.io") } }
        }
        group("Licence") {
            row { sh ->
                Link(
                    "GNU GPL v3 or later",
                    "Free software: use, study, share and change it; anything built from it stays under the same licence. No warranty.",
                    sh,
                ) { openExternal(context, "https://www.gnu.org/licenses/gpl-3.0.html") }
            }
        }
        group("Built with") {
            // MIT licence: its notice must travel with the code (also at the top of assets/tv/vacuumtube.js).
            row { sh ->
                Link("VacuumTube", "TV mode's modules. MIT License, Copyright (c) 2025-2026 shy", sh) {
                    openExternal(context, "https://github.com/shy1132/VacuumTube/blob/main/LICENSE")
                }
            }
            listOf(
                "NewPipeExtractor" to "https://github.com/TeamNewPipe/NewPipeExtractor",
                "SponsorBlock" to "https://sponsor.ajay.app",
                "DeArrow" to "https://dearrow.ajay.app",
                "Return YouTube Dislike" to "https://returnyoutubedislike.com",
                "Jetpack Compose & Material 3" to "https://developer.android.com/compose",
                "Media3 ExoPlayer" to "https://developer.android.com/media/media3",
                "Coil" to "https://coil-kt.github.io/coil/",
            ).forEach { (name, url) -> row { sh -> Link(name, null, sh) { openExternal(context, url) } } }
        }
        item {
            Text(
                "Not affiliated with YouTube or Google.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
        }
    }
}

/** A picture that keeps morphing through Material 3 Expressive shapes while slowly turning. */
@Composable
fun MorphingAvatar(url: String, size: Dp) {
    val shapes = remember {
        listOf(
            MaterialShapes.Cookie12Sided, MaterialShapes.Clover8Leaf, MaterialShapes.Sunny, MaterialShapes.SoftBurst,
            MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Cookie6Sided, MaterialShapes.Circle,
        )
    }
    val morphs = remember { shapes.indices.map { i -> Morph(shapes[i], shapes[(i + 1) % shapes.size]) } }
    var index by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val spatial = MaterialTheme.motionScheme.slowSpatialSpec<Float>()

    LaunchedEffect(Unit) {
        while (true) {
            delay(1400)
            progress.animateTo(1f, spatial)
            index = (index + 1) % morphs.size
            progress.snapTo(0f)
        }
    }
    val t = rememberInfiniteTransition(label = "about")
    val turn by t.animateFloat(0f, 360f, infiniteRepeatable(tween(20_000, easing = LinearEasing)), label = "turn")
    val breath by t.animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "breath")
    val haloTurn by t.animateFloat(360f, 0f, infiniteRepeatable(tween(32_000, easing = LinearEasing)), label = "halo")

    val shape = MorphShape(morphs[index], progress.value, turn + spin.value)
    val halo = remember(haloTurn) { PolygonShape(MaterialShapes.Cookie12Sided, haloTurn) }

    Box(Modifier.size(size * 1.18f), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size * 1.16f)
                .graphicsLayer { scaleX = 2f - breath; scaleY = 2f - breath }
                .clip(halo)
                .background(MaterialTheme.colorScheme.primaryContainer),
        )
        Box(
            Modifier
                .size(size)
                .graphicsLayer { scaleX = breath; scaleY = breath }
                .clip(shape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable { scope.launch { spin.animateTo(spin.value + 360f, spatial) } },
        ) {
            AsyncImage(model = url, contentDescription = "Profile picture", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

// ---------------- Shared pieces ----------------

@Composable
private fun SettingsScaffold(title: String, onBack: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        // A readable column on tablets and Chromebooks rather than rows stretched edge to edge.
        LazyColumn(
            Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()).wrapContentWidth().widthIn(max = 720.dp),
            contentPadding = listPadding(LocalBottomPadding.current),
            content = content,
        )
    }
}

/** Rows of one settings group; each row is handed the Expressive shape for its place in the group. */
private class GroupBuilder {
    val rows = mutableListOf<@Composable (ListItemShapes) -> Unit>()
    fun row(content: @Composable (ListItemShapes) -> Unit) { rows += content }
}

/**
 * A titled group of Material 3 Expressive segmented list items: rounded tiles with small gaps,
 * the outer corners rounder than the inner ones, morphing when pressed.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.group(title: String, showTitle: Boolean = true, build: GroupBuilder.() -> Unit) {
    val rows = GroupBuilder().apply(build).rows
    if (rows.isEmpty()) return
    if (showTitle) item(key = "title-$title") { Section(title) }
    rows.forEachIndexed { i, row ->
        item(key = "$title-$i") {
            Box(Modifier.padding(horizontal = 16.dp, vertical = ListItemDefaults.SegmentedGap / 2)) {
                row(ListItemDefaults.segmentedShapes(i, rows.size))
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 32.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun Toggle(
    title: String,
    body: String?,
    checked: Boolean,
    shapes: ListItemShapes,
    leading: (@Composable () -> Unit)? = null,
    onChange: (Boolean) -> Unit,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onChange,
        shapes = shapes,
        leadingContent = leading,
        supportingContent = body?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = tileColors(),
    ) { Text(title) }
}

@Composable
private fun Link(title: String, body: String?, shapes: ListItemShapes, trailingArrow: Boolean = true, onClick: () -> Unit) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        supportingContent = body?.let { { Text(it) } },
        trailingContent = if (trailingArrow) ({ Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) }) else null,
        colors = tileColors(),
    ) { Text(title) }
}

@Composable
private fun Row2(title: String, body: String, shapes: ListItemShapes, trailing: @Composable () -> Unit) {
    SegmentedListItem(
        shapes = shapes,
        supportingContent = { Text(body) },
        trailingContent = trailing,
        colors = tileColors(),
    ) { Text(title) }
}

/** Every tile sits on the same tonal container, whether its switch is on or off. */
@Composable
private fun tileColors() = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    selectedContentColor = MaterialTheme.colorScheme.onSurface,
    selectedSupportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

/** Free-form content in a group tile. */
@Composable
private fun Tile(shapes: ListItemShapes, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(shape = shapes.shape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun Segmented(title: String, labels: List<String>, selected: Int, shapes: ListItemShapes, onSelect: (Int) -> Unit) {
    Tile(shapes) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = selected == i,
                    onClick = { onSelect(i) },
                    shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                ) { Text(label) }
            }
        }
    }
}

@Composable
private fun ChoiceDialog(c: Choice, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(c.title, localise = false) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                c.options.forEach { (label, value) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = { RadioButton(selected = c.selected == value, onClick = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            c.onPick(value)
                            onDismiss()
                        },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
