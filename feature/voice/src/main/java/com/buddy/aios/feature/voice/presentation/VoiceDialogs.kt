package com.buddy.aios.feature.voice.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.buddy.aios.core.ui.theme.BuddyColors

/**
 * Post-recording dialog asking the user to SAVE or DELETE the temp recording.
 * NEVER auto-saves. The user must make an explicit choice.
 */
@Composable
fun SaveRecordingDialog(
    durationMs: Long,
    onSave: (title: String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text("🎙️", style = MaterialTheme.typography.headlineMedium) },
        title = {
            Text(
                "Save Voice Recording?",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Duration:", style = MaterialTheme.typography.bodySmall, color = BuddyColors.TextSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text(formatDuration(durationMs), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = BuddyColors.Cyan)
                }
                Spacer(Modifier.height(12.dp))
                Text("Title (optional):", style = MaterialTheme.typography.labelSmall, color = BuddyColors.TextSecondary)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Auto-generated if blank", color = BuddyColors.TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BuddyColors.Cyan,
                        unfocusedBorderColor = BuddyColors.GlassBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "AIOS can save this recording locally on your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BuddyColors.TextSecondary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title) }) {
                Text("SAVE", color = BuddyColors.Cyan, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text("DELETE", color = BuddyColors.TextMuted)
            }
        },
        containerColor = BuddyColors.SurfaceDark,
    )
}

@Composable
fun StoragePickerDialog(
    onSelectPrivate: () -> Unit,
    onSelectDevice: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Voice Recordings", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                Text("Where should AIOS store your voice recordings?", style = MaterialTheme.typography.bodyMedium, color = BuddyColors.TextSecondary)
                Spacer(Modifier.height(4.dp))
                StorageOptionCard(
                    title = "AIOS Private Storage",
                    description = "Only AIOS can access these recordings.",
                    onClick = onSelectPrivate,
                )
                StorageOptionCard(
                    title = "Device Storage",
                    description = "Save recordings in Music/AIOS/Voice Recordings.",
                    onClick = onSelectDevice,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = BuddyColors.TextMuted) }
        },
        containerColor = BuddyColors.SurfaceDark,
    )
}

@Composable
private fun StorageOptionCard(title: String, description: String, onClick: () -> Unit) {
    com.buddy.aios.core.ui.components.GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .let { it.then(Modifier) },
    ) {
        TextButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                Text(description, style = MaterialTheme.typography.bodySmall, color = BuddyColors.TextSecondary)
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val min = totalSeconds / 60
    val sec = totalSeconds % 60
    return "%02d:%02d".format(min, sec)
}
