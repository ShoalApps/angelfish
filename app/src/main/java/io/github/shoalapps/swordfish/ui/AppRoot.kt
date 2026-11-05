@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.shoalapps.swordfish.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.VideoLibrary
import io.github.shoalapps.swordfish.LocalisedIcon as Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.Scaffold
import io.github.shoalapps.swordfish.LocalisedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.shoalapps.swordfish.AppGraph
import io.github.shoalapps.swordfish.data.VideoItem
import io.github.shoalapps.swordfish.player.PlayerViewModel
import io.github.shoalapps.swordfish.ui.components.Avatar
import io.github.shoalapps.swordfish.ui.components.LocalBottomPadding
import io.github.shoalapps.swordfish.ui.player.PlayerSheet
import io.github.shoalapps.swordfish.ui.player.VideoSurface
import io.github.shoalapps.swordfish.ui.screens.ChannelScreen
import io.github.shoalapps.swordfish.ui.screens.AboutScreen
import io.github.shoalapps.swordfish.ui.screens.DeveloperSettingsScreen
import io.github.shoalapps.swordfish.ui.screens.ChannelPickerScreen
import io.github.shoalapps.swordfish.ui.screens.HomeScreen
import io.github.shoalapps.swordfish.ui.screens.LoginScreen
import io.github.shoalapps.swordfish.ui.screens.PlaylistScreen
import io.github.shoalapps.swordfish.ui.screens.SearchScreen
import io.github.shoalapps.swordfish.ui.screens.SettingsScreen
import io.github.shoalapps.swordfish.ui.screens.ShortsScreen
import io.github.shoalapps.swordfish.ui.screens.SubscriptionsScreen
import io.github.shoalapps.swordfish.ui.screens.YouScreen

private data class Tab(val route: String, val label: String, val selected: ImageVector, val unselected: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Rounded.Home, Icons.Outlined.Home),
    Tab("shorts", "Shorts", Icons.Rounded.VideoLibrary, Icons.Outlined.VideoLibrary),
    Tab("subs", "Subscriptions", Icons.Rounded.Subscriptions, Icons.Outlined.Subscriptions),
    Tab("you", "You", Icons.Rounded.AccountCircle, Icons.Outlined.AccountCircle),
)

private fun isTab(route: String?) = tabs.any { it.route == route }

/** "Update available" snackbar, and a wavy progress bar while the new version downloads. */
@Composable
private fun UpdatePrompt(modifier: Modifier) {
    val host = remember { androidx.compose.material3.SnackbarHostState() }
    val available = AppGraph.Update.available
    val message = AppGraph.Update.message
    LaunchedEffect(available) {
        if (available == null) return@LaunchedEffect
        val result = host.showSnackbar(
            "A new version of Angelfish is ready",
            actionLabel = "Update",
            withDismissAction = true,
            duration = androidx.compose.material3.SnackbarDuration.Indefinite,
        )
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) AppGraph.installUpdate()
        else AppGraph.Update.available = null
    }
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        host.showSnackbar(message)
        AppGraph.Update.message = null
    }
    Box(modifier.padding(horizontal = 12.dp)) {
        androidx.compose.material3.SnackbarHost(host)
        AppGraph.Update.progress?.let { p ->
            androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ) {
                androidx.compose.foundation.layout.Column(Modifier.padding(16.dp).widthIn(max = 480.dp)) {
                    Text("Downloading update… ${(p * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.material3.LinearWavyProgressIndicator(
                        progress = { p },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        color = MaterialTheme.colorScheme.inversePrimary,
                    )
                }
            }
        }
    }
}

/** A tab's icon; the You tab shows your profile picture once signed in, like the official app. */
@Composable
private fun TabIcon(tab: Tab, selected: Boolean, avatar: String?) {
    if (tab.route == "you" && avatar != null) {
        Avatar(avatar, 26.dp, if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
    } else {
        Icon(if (selected) tab.selected else tab.unselected, tab.label)
    }
}

/** Set once the first Home feed has loaded, so the intro can step aside. */
object StartupState {
    var homeReady by androidx.compose.runtime.mutableStateOf(false)
}

/** Lets the Shorts screen ask for the screen to stay on. */
object ScreenAwake {
    var shorts by androidx.compose.runtime.mutableStateOf(false)
}

// Material motion: fade-through between tabs, shared X-axis for going deeper.
private fun AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs() =
    isTab(initialState.destination.route) && isTab(targetState.destination.route)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(): EnterTransition =
    if (betweenTabs()) fadeIn(tween(210, delayMillis = 90)) + scaleIn(tween(210, delayMillis = 90), initialScale = 0.92f)
    else slideInHorizontally(tween(300)) { it / 6 } + fadeIn(tween(300))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(): ExitTransition =
    if (betweenTabs()) fadeOut(tween(90))
    else slideOutHorizontally(tween(300)) { -it / 6 } + fadeOut(tween(200))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition =
    if (betweenTabs()) fadeIn(tween(210, delayMillis = 90)) + scaleIn(tween(210, delayMillis = 90), initialScale = 0.92f)
    else slideInHorizontally(tween(300)) { -it / 6 } + fadeIn(tween(300))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
    if (betweenTabs()) fadeOut(tween(90))
    else slideOutHorizontally(tween(300)) { it / 6 } + fadeOut(tween(200))

@Composable
fun AppRoot(vm: PlayerViewModel, inPip: Boolean, pendingRoute: String? = null, onRouteHandled: () -> Unit = {}) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = route == null || isTab(route)
    val account by AppGraph.store.account.collectAsState()
    val settings by AppGraph.store.settings.collectAsState()
    val visibleTabs = if (settings.shortsDisabled) tabs.filter { it.route != "shorts" } else tabs
    val goTab: (String) -> Unit = { r ->
        nav.navigate(r) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Launcher shortcuts (long-press the app icon).
    LaunchedEffect(pendingRoute) {
        when (pendingRoute) {
            "search" -> nav.navigate("search")
            "subs", "shorts", "you", "home" -> goTab(pendingRoute)
        }
        if (pendingRoute != null) onRouteHandled()
    }

    // Keep the screen on during playback, including when the player is minimized.
    // Android still lets the user turn the screen off with the power button.
    val view = LocalView.current
    val awake = vm.isPlaying || ScreenAwake.shorts
    DisposableEffect(awake) {
        view.keepScreenOn = awake
        onDispose { view.keepScreenOn = false }
    }

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Like the official app, the mini player steps aside while you're in Shorts.
    val miniHidden = route == "shorts" || route == "login"
    val useRail = LocalConfiguration.current.screenWidthDp >= 600
    val bottomExtra = (if (showBar && !useRail) 0.dp else navInset) +
        (if (vm.sheetVisible && !miniHidden && !io.github.shoalapps.swordfish.ui.components.isWideScreen()) 76.dp else 0.dp)

    val openVideo: (VideoItem) -> Unit = { vm.open(it) }
    val openChannel: (String) -> Unit = { url ->
        if (vm.sheetVisible) vm.collapse()
        nav.navigate("channel/${Uri.encode(url)}")
    }
    val openShort: (VideoItem) -> Unit = { short ->
        io.github.shoalapps.swordfish.ui.screens.ShortsLaunch.start = short
        vm.collapse()
        goTab("shorts")
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
      Row(Modifier.fillMaxSize()) {
        // Material adaptive navigation: a rail beside the content from medium widths up.
        if (useRail) {
            WideNavigationRail {
                visibleTabs.forEach { tab ->
                    val selected = route == tab.route
                    WideNavigationRailItem(
                        selected = selected,
                        onClick = { goTab(tab.route) },
                        icon = { TabIcon(tab, selected, account?.avatar) },
                        label = { Text(tab.label) },
                        railExpanded = false,
                    )
                }
            }
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                AnimatedVisibility(
                    visible = showBar && !useRail,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    ShortNavigationBar(
                        modifier = Modifier.graphicsLayer {
                            translationY = if (vm.sheetVisible && !miniHidden) size.height * vm.expand.value else 0f
                        },
                    ) {
                        visibleTabs.forEach { tab ->
                            val selected = route == tab.route
                            ShortNavigationBarItem(
                                selected = selected,
                                onClick = { goTab(tab.route) },
                                icon = { TabIcon(tab, selected, account?.avatar) },
                                label = { if (settings.navLabels) Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { pad ->
            CompositionLocalProvider(LocalBottomPadding provides bottomExtra) {
                NavHost(
                    navController = nav,
                    startDestination = "home",
                    modifier = Modifier.padding(bottom = pad.calculateBottomPadding()),
                    enterTransition = { enter() },
                    exitTransition = { exit() },
                    popEnterTransition = { popEnter() },
                    popExitTransition = { popExit() },
                ) {
                    composable("home") {
                        HomeScreen(
                            onVideo = openVideo,
                            onChannel = openChannel,
                            onSearch = { nav.navigate("search") },
                            onNotifications = { goTab("subs") },
                            onShorts = openShort,
                        )
                    }
                    composable("shorts") {
                        ShortsScreen(onChannel = openChannel, onExit = { goTab("home") })
                    }
                    composable("subs") {
                        SubscriptionsScreen(onVideo = openVideo, onChannel = openChannel, onShorts = openShort)
                    }
                    composable("you") {
                        YouScreen(
                            onVideo = openVideo,
                            onSettings = { nav.navigate("settings") },
                            onSignIn = { nav.navigate("login") },
                            onPlaylist = { id -> nav.navigate("playlist/$id") },
                            onLocalPlaylist = { id -> nav.navigate("local-playlist/$id") },
                            onSwitchChannel = { nav.navigate("channels") },
                            onAbout = { nav.navigate("about") },
                        )
                    }
                    composable("search") {
                        SearchScreen(onBack = { nav.popBackStack() }, onVideo = openVideo, onChannel = openChannel)
                    }
                    composable("channel/{url}") { e ->
                        val url = Uri.decode(e.arguments?.getString("url").orEmpty())
                        ChannelScreen(url = url, onBack = { nav.popBackStack() }, onVideo = openVideo)
                    }
                    composable("local-playlist/{id}") { e ->
                        io.github.shoalapps.swordfish.ui.screens.LocalPlaylistScreen(
                            id = e.arguments?.getString("id").orEmpty(),
                            onBack = { nav.popBackStack() },
                            onPlay = { items ->
                                AppGraph.store.replaceQueue(items)
                                items.firstOrNull()?.let { vm.jumpToQueue(it); vm.showSheet(1f) }
                            },
                        )
                    }
                    composable("playlist/{id}") { e ->
                        PlaylistScreen(
                            browseId = e.arguments?.getString("id").orEmpty(),
                            onBack = { nav.popBackStack() },
                            onVideo = openVideo,
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            onBack = { nav.popBackStack() },
                            onSignIn = { nav.navigate("login") },
                            onSwitchChannel = { nav.navigate("channels") },
                            onDeveloper = { nav.navigate("developer") },
                            onAbout = { nav.navigate("about") },
                        )
                    }
                    composable("developer") { DeveloperSettingsScreen(onBack = { nav.popBackStack() }) }
                    composable("about") { AboutScreen(onBack = { nav.popBackStack() }) }
                    composable("login") {
                        LoginScreen(
                            onDone = { nav.navigate("channels") { popUpTo("login") { inclusive = true } } },
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable("channels") {
                        ChannelPickerScreen(onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() })
                    }
                }
            }
        }

      }

        if (vm.sheetVisible && !inPip && !miniHidden) {
            PlayerSheet(vm, bottomBarVisible = showBar && !useRail, onChannel = openChannel, startInset = if (useRail) 96.dp else 0.dp)
        }

        UpdatePrompt(Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = if (showBar && !useRail) 80.dp + navInset else navInset + 16.dp))

        IntroOverlay()

        // Picture-in-picture: just the video, on black.
        if (inPip) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                VideoSurface(vm, Modifier.fillMaxSize())
            }
        }
    }
}
