package io.github.shoalapps.swordfish.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import io.github.shoalapps.swordfish.AppGraph
import io.github.shoalapps.swordfish.LocalisedText as Text
import io.github.shoalapps.swordfish.data.LocalPlaylistCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistTransferActions(val busy: Boolean, val export: (List<String>) -> Unit, val import: () -> Unit)

/** Both entry points use the system picker; no storage permission or persisted URI grant is needed. */
@Composable
fun rememberPlaylistTransfer(): PlaylistTransferActions {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    val codec = remember { LocalPlaylistCodec() }
    var busy by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var exportIds by rememberSaveable { mutableStateOf(arrayListOf<String>()) }

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                try {
                    val playlists = AppGraph.store.playlists.value.filter { it.id in exportIds }
                    require(playlists.size == exportIds.size) { "A selected playlist no longer exists. Please export again." }
                    val bytes = withContext(Dispatchers.Default) { codec.encode(playlists) }
                    withContext(Dispatchers.IO) {
                        resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                            ?: error("No output stream")
                    }
                    message = "Exported ${playlists.size} playlists and ${playlists.sumOf { it.items.size }} videos. This file contains playlist entries, not downloaded videos."
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: IllegalArgumentException) {
                    message = error.message ?: "Could not export playlists. Please try again."
                } catch (_: Exception) {
                    message = "Could not export playlists. Please try again."
                } finally { busy = false }
            }
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                try {
                    val decoded = withContext(Dispatchers.IO) {
                        resolver.openInputStream(uri)?.use { codec.read(it) } ?: error("No input stream")
                    }
                    val result = AppGraph.store.importPlaylists(decoded)
                    message = "Imported ${result.addedPlaylists} playlists and ${result.addedVideos} videos.\n" +
                        "${result.renamedPlaylists} playlists were given a number because the name was already used.\n" +
                        "Left out ${result.duplicatePlaylists} identical playlists and ${result.duplicateVideos} repeated video entries.\n" +
                        "Skipped ${result.skippedPlaylists} playlists and ${result.skippedVideos} video entries that were invalid or over the limits."
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: IllegalArgumentException) {
                    message = error.message ?: "Could not import playlists. Please choose a playlist JSON file."
                } catch (_: Exception) {
                    message = "Could not read this playlist file. Please try again."
                } finally { busy = false }
            }
        }
    }
    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("Playlist file") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }
    return PlaylistTransferActions(busy, export = { ids ->
        exportIds = ArrayList(ids)
        try { exportPicker.launch("angelfish-playlists.json") }
        catch (_: Exception) { message = "Could not open the file picker. Please try again." }
    }, import = {
        try { importPicker.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }
        catch (_: Exception) { message = "Could not open the file picker. Please try again." }
    })
}
