package io.github.shoalapps.swordfish.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import io.github.shoalapps.common.ui.LocalisedText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.shoalapps.swordfish.AppGraph
import kotlinx.coroutines.launch

@Composable
fun OfflineStatus() {
    val online by AppGraph.online.collectAsState()
    val actions by AppGraph.actions.actions.collectAsState()
    val cookie by AppGraph.store.cookie.collectAsState()
    val channel by AppGraph.store.pageId.collectAsState()
    val owner = remember(cookie, channel) { AppGraph.actions.identity() }
    var expanded by remember { mutableStateOf(false) }
    if (!online || actions.isNotEmpty()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (!online) Text("Offline · showing downloaded videos")
            if (actions.isNotEmpty()) {
                TextButton(onClick = { expanded = !expanded }) { Text("Saved actions (" + actions.size + ")") }
                if (expanded) {
                    Text("Account actions sync when online and Data Saver allows. Other sign-ins stay paused. History stays on this phone.",
                        style = MaterialTheme.typography.bodySmall)
                    actions.forEach { action ->
                        val ours = action.account == owner
                        Text(action.kind + " · " + when {
                            !ours -> "Paused for a different sign-in or channel"
                            action.state == "review" -> "Check YouTube before sending again; it may already have been sent"
                            action.state == "failed" -> "Could not sync; retry when ready"
                            action.state == "sending" -> "Syncing"
                            else -> "Waiting to sync"
                        }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        if (ours && action.kind in setOf("comment", "reply")) Text(action.value)
                        Row {
                            if (ours && action.state in setOf("review", "failed", "pending")) {
                                TextButton(onClick = {
                                    AppGraph.scope.launch { AppGraph.actions.retry(action.id); AppGraph.actions.schedule(); AppGraph.actions.flush() }
                                }) { Text(if (action.state == "review") "Send again" else "Retry") }
                            }
                            if (action.state != "sending") TextButton(onClick = {
                                AppGraph.scope.launch { AppGraph.actions.discard(action.id) }
                            }) { Text("Discard") }
                        }
                    }
                }
            }
        }
    }
}
