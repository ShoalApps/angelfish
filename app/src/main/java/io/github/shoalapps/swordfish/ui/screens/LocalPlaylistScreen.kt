@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.shoalapps.swordfish.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.shoalapps.swordfish.AppGraph
import io.github.shoalapps.swordfish.LocalisedIcon as Icon
import io.github.shoalapps.swordfish.LocalisedText as Text
import io.github.shoalapps.swordfish.data.VideoItem
import io.github.shoalapps.swordfish.ui.components.*

@Composable
fun LocalPlaylistScreen(id: String, onBack: () -> Unit, onPlay: (List<VideoItem>) -> Unit) {
    val playlists by AppGraph.store.playlists.collectAsState()
    val queue by AppGraph.store.queue.collectAsState()
    val playlist = playlists.firstOrNull { it.id == id }
    val context = LocalContext.current
    val transfer = rememberPlaylistTransfer()
    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<List<VideoItem>?>(null) }
    val play: (List<VideoItem>) -> Unit = { items ->
        if (items.isNotEmpty()) {
            if (queue.isNotEmpty()) pending = items else onPlay(items)
        }
    }
    if (rename && playlist != null) PlaylistNameDialog(playlist.name,
        onDismiss = { rename = false },
        onSave = { AppGraph.store.renamePlaylist(id, it); rename = false })
    if (delete) AlertDialog(
        onDismissRequest = { delete = false },
        title = { Text("Delete playlist?") },
        text = { Text("This removes the playlist from this phone. Downloaded videos stay saved.") },
        confirmButton = { TextButton(onClick = { AppGraph.store.deletePlaylist(id); onBack() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text("Cancel") } },
    )
    pending?.let { items ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Replace queue?") },
            text = { Text("Your queued videos will be replaced by this playlist.") },
            confirmButton = { TextButton(onClick = { pending = null; onPlay(items) }) { Text("Replace") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(playlist?.name ?: "Playlist", localise = playlist == null) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
            contentPadding = listPadding(LocalBottomPadding.current)) {
            if (playlist == null) item { Text("Playlist not found", modifier = Modifier.padding(16.dp)) }
            else {
                item { Text("${playlist.items.size} videos · Saved on this phone", modifier = Modifier.padding(16.dp)) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { Button(onClick = { play(playlist.items) }, enabled = playlist.items.isNotEmpty()) { Text("Play all") } }
                        item { OutlinedButton(onClick = { play(playlist.items.shuffled()) }, enabled = playlist.items.isNotEmpty()) { Text("Shuffle") } }
                        item { OutlinedButton(onClick = {
                            AppGraph.store.addAllToQueue(playlist.items)
                            toast(context, "Added to queue")
                        }, enabled = playlist.items.isNotEmpty()) { Text("Add all to queue") } }
                        item { OutlinedButton(onClick = { transfer.export(listOf(id)) }, enabled = !transfer.busy) { Text("Export playlist") } }
                        item { TextButton(onClick = { rename = true }) { Text("Rename") } }
                        item { TextButton(onClick = { delete = true }) { Text("Delete") } }
                    }
                }
                if (playlist.items.isEmpty()) item {
                    Text("Use Add to playlist on a video to save it here.", modifier = Modifier.padding(16.dp))
                }
                itemsIndexed(playlist.items, key = { _, video -> video.id }) { index, video ->
                    Column(Modifier.animateItem()) {
                        VideoRow(video, { play(playlist.items.drop(index)) })
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { AppGraph.store.moveInPlaylist(id, video.id, -1) }, enabled = index > 0) {
                                Icon(Icons.Rounded.ArrowUpward, "Move up")
                            }
                            IconButton(onClick = { AppGraph.store.moveInPlaylist(id, video.id, 1) }, enabled = index < playlist.items.lastIndex) {
                                Icon(Icons.Rounded.ArrowDownward, "Move down")
                            }
                            IconButton(onClick = { AppGraph.store.removeFromPlaylist(id, video.id) }) {
                                Icon(Icons.Rounded.Close, "Remove from playlist")
                            }
                        }
                    }
                }
            }
        }
    }
}
