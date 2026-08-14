package com.buddy.aios.feature.voice.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buddy.aios.core.domain.entity.VoiceRecording
import com.buddy.aios.core.ui.components.GlassCard
import com.buddy.aios.core.ui.theme.BuddyColors
import com.buddy.aios.feature.voice.recorder.RecorderState
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRecordingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: VoiceRecordingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var deleteTarget by remember { mutableStateOf<VoiceRecording?>(null) }
    var playingId by remember { mutableStateOf<String?>(null) }

    // Show snackbar on message
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BuddyColors.BackgroundDeep)
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "Voice Recordings",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                            )
                            Text(
                                "${state.recordingCount} recordings · ${formatBytes(state.storageTotalBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = BuddyColors.TextSecondary,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BuddyColors.SurfaceDark.copy(alpha = 0.85f)),
                )
            },
            containerColor = Color.Transparent,
        ) { padding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Recording indicator
                if (state.recorderState is RecorderState.Recording) {
                    RecordingIndicator(
                        startTimeMs = (state.recorderState as RecorderState.Recording).startTimeMs,
                        onStop = { viewModel.onStopRecording() },
                    )
                }

                if (state.recordings.isEmpty() && state.recorderState !is RecorderState.Recording) {
                    EmptyVaultPlaceholder()
                } else {
                    val grouped = groupRecordingsByDate(state.recordings)
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        grouped.forEach { (section, recordings) ->
                            item {
                                Text(
                                    section,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = BuddyColors.Cyan,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                            items(recordings, key = { it.id }) { recording ->
                                RecordingItem(
                                    recording = recording,
                                    isPlaying = playingId == recording.id,
                                    onPlayPause = { playingId = if (playingId == recording.id) null else recording.id },
                                    onDelete = { deleteTarget = recording },
                                    onShare = {
                                        val uri = Uri.parse(recording.fileUri)
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "audio/mp4"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share Recording"))
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Save/Delete dialog after recording
        if (state.showSaveDialog) {
            SaveRecordingDialog(
                durationMs = state.pendingDurationMs,
                onSave = { title -> viewModel.onSaveRecording(title) },
                onDelete = { viewModel.onDeleteTempRecording() },
                onDismiss = { viewModel.onDismissSaveDialog() },
            )
        }

        // Storage picker dialog
        if (state.showStoragePicker) {
            StoragePickerDialog(
                onSelectPrivate = { viewModel.onStorageLocationSelected(com.buddy.aios.core.domain.entity.StorageLocation.PRIVATE) },
                onSelectDevice = { viewModel.onStorageLocationSelected(com.buddy.aios.core.domain.entity.StorageLocation.DEVICE) },
                onDismiss = { viewModel.onDismissStoragePicker() },
            )
        }

        // Delete confirmation dialog
        deleteTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("Delete Recording?", color = Color.White) },
                text = { Text("\"${target.title}\" will be permanently deleted.", color = BuddyColors.TextSecondary) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.onDeleteSavedRecording(target.id)
                        deleteTarget = null
                    }) {
                        Text("DELETE", color = BuddyColors.Cyan, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text("CANCEL", color = BuddyColors.TextMuted)
                    }
                },
                containerColor = BuddyColors.SurfaceDark,
            )
        }
    }
}

@Composable
private fun RecordingItem(
    recording: VoiceRecording,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPlayPause, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = BuddyColors.Cyan,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    recording.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 1,
                )
                Text(
                    "${formatDuration(recording.durationMs)} · ${formatBytes(recording.sizeBytes)} · ${formatDate(recording.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = BuddyColors.TextMuted,
                )
            }
            IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = BuddyColors.TextSecondary)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = BuddyColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun RecordingIndicator(startTimeMs: Long, onStop: () -> Unit) {
    var elapsed by remember { mutableLongStateOf(0L) }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.5f, targetValue = 1f, label = "alpha",
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
    )

    LaunchedEffect(startTimeMs) {
        while (true) {
            elapsed = System.currentTimeMillis() - startTimeMs
            delay(500)
        }
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize(),
        backgroundColor = Color.Red.copy(alpha = 0.15f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Mic, contentDescription = null, tint = Color.Red.copy(alpha = pulse), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("🔴 Recording", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
            Spacer(Modifier.width(8.dp))
            Text(formatDuration(elapsed), style = MaterialTheme.typography.bodyMedium, color = BuddyColors.Cyan)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onStop) {
                Icon(Icons.Default.Stop, contentDescription = "Stop recording", tint = Color.Red)
            }
        }
    }
}

@Composable
private fun EmptyVaultPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎙️", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(12.dp))
            Text("No recordings yet", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
            Text("Enable Voice Recording in Settings to start capturing.", style = MaterialTheme.typography.bodySmall, color = BuddyColors.TextSecondary)
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun groupRecordingsByDate(recordings: List<VoiceRecording>): Map<String, List<VoiceRecording>> {
    val todayStart = System.currentTimeMillis().let {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.timeInMillis
    }
    val yesterdayStart = todayStart - 86_400_000L

    return recordings.groupBy { r ->
        when {
            r.createdAt >= todayStart -> "Today"
            r.createdAt >= yesterdayStart -> "Yesterday"
            else -> "Earlier"
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val min = totalSeconds / 60
    val sec = totalSeconds % 60
    return "%02d:%02d".format(min, sec)
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "${bytes} B"
    bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(timestamp))
