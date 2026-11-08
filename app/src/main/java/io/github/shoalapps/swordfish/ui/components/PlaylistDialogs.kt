package io.github.shoalapps.swordfish.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.shoalapps.swordfish.AppGraph
import io.github.shoalapps.common.ui.LocalisedText as Text
import io.github.shoalapps.swordfish.data.VideoItem

@Composable
fun PlaylistNameDialog(initial: String = "", onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.isEmpty()) "New playlist" else "Rename playlist") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text("Name") })
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun AddToPlaylistDialog(video: VideoItem, onDismiss: () -> Unit) {
    val playlists by AppGraph.store.playlists.collectAsState()
    val context = LocalContext.current
    var creating by rememberSaveable { mutableStateOf(false) }
    if (creating) {
        PlaylistNameDialog(onDismiss = { creating = false }, onSave = { name ->
            AppGraph.store.createPlaylist(name)?.let { AppGraph.store.addToPlaylist(it, video) }
            toast(context, "Added to playlist")
            onDismiss()
        })
    } else AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            LazyColumn {
                item { Text("Saved on this phone") }
                items(playlists, key = { it.id }) { playlist ->
                    ListItem(
                        headlineContent = { Text(playlist.name, localise = false) },
                        supportingContent = { Text("${playlist.items.size} videos") },
                        modifier = Modifier.fillMaxWidth().clickable {
                            val added = AppGraph.store.addToPlaylist(playlist.id, video)
                            toast(context, if (added) "Added to playlist" else "Already in playlist")
                            onDismiss()
                        },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { creating = true }) { Text("New playlist") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
