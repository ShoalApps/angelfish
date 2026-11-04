package io.github.shoalapps.swordfish.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import io.github.shoalapps.swordfish.LocalisedText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.shoalapps.swordfish.ui.components.toast

/** Shown after a crash, so the details can be copied and sent for fixing. */
@Composable
fun CrashDialog(report: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Angelfish closed unexpectedly") },
        text = {
            Column {
                Text("Copy this report and send it to get the problem fixed.", style = MaterialTheme.typography.bodyMedium)
                Text(
                    report,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp).heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Angelfish crash", report))
                toast(context, "Crash report copied")
                onDismiss()
            }) { Text("Copy report") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
