@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.shoalapps.swordfish.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.SwitchAccount
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import io.github.shoalapps.common.ui.LocalisedIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import io.github.shoalapps.common.ui.LocalisedText as Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.shoalapps.swordfish.AppGraph
import io.github.shoalapps.swordfish.data.ChannelChoice
import io.github.shoalapps.swordfish.ui.components.Avatar
import io.github.shoalapps.swordfish.ui.components.CenterLoading
import io.github.shoalapps.common.expressive.CookieAvatar
import io.github.shoalapps.swordfish.ui.components.EmptyState
import io.github.shoalapps.swordfish.ui.components.ErrorState
import kotlinx.coroutines.launch

// ---------------- Sign in ----------------

private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?service=youtube&passive=true" +
        "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Dm%26next%3Dhttps%253A%252F%252Fm.youtube.com%252F"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onDone: () -> Unit, onBack: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var finished by remember { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Sign in to YouTube")
                        Text(
                            "Uses an unofficial app, so a spare account is safest",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
            AndroidView(
                factory = { ctx ->
                    CookieManager.getInstance().setAcceptCookie(true)
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                loading = true
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                loading = false
                                if (finished) return
                                if (url != null && (url.startsWith("https://m.youtube.com") || url.startsWith("https://www.youtube.com"))) {
                                    val cookie = CookieManager.getInstance().getCookie("https://www.youtube.com")
                                    if (cookie != null && cookie.contains("SAPISID")) {
                                        finished = true
                                        CookieManager.getInstance().flush()
                                        AppGraph.store.setCookie(cookie)
                                        AppGraph.store.setPageId(null)
                                        AppGraph.syncAccount()
                                        onDone()
                                    }
                                }
                            }
                        }
                        loadUrl(LOGIN_URL)
                    }
                },
                onRelease = { it.stopLoading(); it.destroy() },
                modifier = Modifier.fillMaxSize(),
            )
            AnimatedVisibility(loading, enter = fadeIn(tween(150)), exit = fadeOut(tween(300)), modifier = Modifier.align(Alignment.TopCenter)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}

// ---------------- Choose a channel ----------------

class ChannelPickerViewModel : ViewModel() {
    var channels by mutableStateOf<List<ChannelChoice>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init { load() }

    fun load() {
        loading = true; error = null
        viewModelScope.launch {
            runCatching { AppGraph.account.channels() }
                .onSuccess { channels = it }
                .onFailure { error = it.message ?: "Couldn't load your channels" }
            loading = false
        }
    }
}

@Composable
fun ChannelPickerScreen(onDone: () -> Unit, onBack: () -> Unit, vm: ChannelPickerViewModel = viewModel()) {
    val current by AppGraph.store.pageId.collectAsState()
    LaunchedEffect(vm.channels) {
        // Only one channel on this account: nothing to choose.
        if (!vm.loading && vm.channels.size == 1) {
            AppGraph.selectChannel(vm.channels.first())
            onDone()
        }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Choose a channel") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
            when {
                vm.loading -> item { CenterLoading() }
                vm.error != null -> item { ErrorState(vm.error!!, vm::load) }
                vm.channels.isEmpty() -> item {
                    EmptyState(
                        Icons.Rounded.SwitchAccount,
                        "No other channels found",
                        "YouTube didn't return a channel list. You can carry on with your main channel, or try again.",
                    ) {
                        Button(onClick = {
                            AppGraph.store.setPageId(null)
                            AppGraph.syncAccount(replaceSubs = true)
                            onDone()
                        }) { Text("Use main channel") }
                    }
                }
                else -> {
                    item {
                        Text(
                            "Pick which of your channels Angelfish uses for your feed, subscriptions and likes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(vm.channels, key = { it.pageId ?: "main:${it.name}" }) { c ->
                        val isCurrent = c.pageId == current
                        ListItem(
                            headlineContent = { Text(c.name, localise = false, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal) },
                            supportingContent = {
                                Text(listOfNotNull(c.handle, if (c.pageId == null) "Main channel" else null).joinToString(" · "))
                            },
                            leadingContent = {
                                if (isCurrent) CookieAvatar(c.avatar, 52.dp) else Avatar(c.avatar, 48.dp)
                            },
                            trailingContent = { RadioButton(selected = isCurrent, onClick = null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                AppGraph.selectChannel(c)
                                onDone()
                            },
                        )
                    }
                }
            }
        }
    }
}
