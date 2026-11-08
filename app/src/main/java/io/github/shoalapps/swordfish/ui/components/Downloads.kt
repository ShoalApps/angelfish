package io.github.shoalapps.swordfish.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import io.github.shoalapps.common.ui.LocalisedText as Text
import io.github.shoalapps.common.ui.LocalisedIcon as Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import io.github.shoalapps.swordfish.data.DownloadErrors
import androidx.compose.ui.unit.dp
import io.github.shoalapps.swordfish.AppGraph
import io.github.shoalapps.swordfish.data.VideoItem

@Composable
fun DownloadButton(video: VideoItem) {
    val downloads by AppGraph.offline.videos.collectAsState()
    val progress by AppGraph.offline.progress.collectAsState()
    val entry = downloads.firstOrNull { it.video.id == video.id }
    if (!video.isLive) FilledTonalButton(onClick = { AppGraph.offline.keep(video) }) {
        Text(when {
            entry?.audioOnly == true && entry.complete -> "Download video"
            entry?.kept == true && entry.complete -> "Downloaded"
            entry?.error != null -> "Retry download"
            entry?.kept == true -> progress[video.id]?.let { "Downloading $it%" } ?: "Download queued"
            else -> "Download"
        })
    }
}

/** Just the sound, for talks and music: far smaller than the video. */
@Composable
fun SaveAudioButton(video: VideoItem) {
    val downloads by AppGraph.offline.videos.collectAsState()
    val entry = downloads.firstOrNull { it.video.id == video.id }
    if (!video.isLive && entry?.audioOnly != true && entry?.kept != true) OutlinedButton(onClick = { AppGraph.offline.keep(video, audioOnly = true) }) { Text("Save audio") }
}

@Composable
fun Downloads(onVideo: (VideoItem) -> Unit) {
    val downloads by AppGraph.offline.videos.collectAsState()
    val progress by AppGraph.offline.progress.collectAsState()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Downloads", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp))
        Text("Saved videos stay until you remove them. Automatic copies come from your subscriptions and change as they post.", style = MaterialTheme.typography.bodySmall)
        if (downloads.isEmpty()) Text("Tap Download on a video to keep it offline.", Modifier.padding(vertical = 12.dp))
        downloads.forEach { entry ->
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage(
                        model = entry.video.thumbnail ?: "https://i.ytimg.com/vi/${entry.video.id}/hqdefault.jpg",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.width(120.dp).aspectRatio(16f / 9).clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(enabled = entry.complete) { onVideo(entry.video) },
                    )
                    Text(entry.video.title.ifBlank { entry.video.id }, localise = false, style = MaterialTheme.typography.titleSmall, maxLines = 3, modifier = Modifier.weight(1f))
                }
                Text(when {
                    entry.error != null -> DownloadErrors.message(entry.error)
                    entry.complete -> if (entry.audioOnly) "Audio only - ready offline" else if (entry.kept) "Saved - ready offline" else "Automatic - ready offline"
                    progress.containsKey(entry.video.id) -> "Downloading ${progress[entry.video.id]}%"
                    else -> "Queued - waiting for an allowed connection"
                }, style = MaterialTheme.typography.bodySmall)
                progress[entry.video.id]?.let { percent -> LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth()) }
                Row {
                    if (entry.complete) TextButton(onClick = { onVideo(entry.video) }) { Text("Play") }
                    if (!entry.kept || entry.error != null) TextButton(onClick = { AppGraph.offline.keep(entry.video) }) { Text(if (entry.error != null) "Retry" else "Keep") }
                    TextButton(onClick = { AppGraph.offline.remove(entry.video.id) }) { Text(if (entry.complete) "Remove" else "Cancel") }
                }
            }
        }
    }
}
