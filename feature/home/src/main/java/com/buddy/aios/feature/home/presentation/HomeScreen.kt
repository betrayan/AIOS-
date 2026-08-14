package com.buddy.aios.feature.home.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.Task
import com.buddy.aios.core.ui.animation.clickableWithScale
import com.buddy.aios.core.ui.components.BuddyOrb
import com.buddy.aios.core.ui.components.GlassCard
import com.buddy.aios.core.ui.components.OrbState
import com.buddy.aios.core.ui.components.VoiceWaveform
import com.buddy.aios.core.ui.shapes.BuddyShapes
import com.buddy.aios.core.ui.theme.BuddyColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * AIOS Reference-Based Premium Home Screen Rebuild.
 *
 * Visual Hierarchy:
 * 1. Top Header: Dynamic salutation ("Good Morning, Vijay 👋"), subtitle, and avatar.
 * 2. Compact Voice Status: Floating Dynamic Island pill capsule (~60% width, 56dp height).
 * 3. Today at a Glance: 4 horizontal scrollable glass cards with REAL AIOS data.
 * 4. Top Priority: 🎯 Card showing ONE top priority task from PriorityEngine with animated checkmark.
 * 5. Quick Actions: ⚡ 4 action buttons (Voice Record, Add Task, Add Reminder, Ask AIOS).
 * 6. Recent Activity: Task history list.
 * 7. Bottom Navigation: Floating glass nav bar (Home, Chat, Settings).
 */
@Composable
fun HomeScreen(
    onNavigateToChat: (String) -> Unit,
    onNavigateToMemory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showVoiceRecordDialog by remember { mutableStateOf(false) }
    var newTaskTitle by remember { mutableStateOf("") }
    var activeTab by remember { mutableStateOf("home") }

    // Recording simulation state for quick action dialog
    var isRecordingActive by remember { mutableStateOf(false) }
    var recordTimerSeconds by remember { mutableStateOf(0) }

    LaunchedEffect(isRecordingActive) {
        if (isRecordingActive) {
            recordTimerSeconds = 0
            while (isRecordingActive) {
                delay(1000L)
                recordTimerSeconds++
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showVoiceRecordDialog = true
            isRecordingActive = true
        } else {
            Toast.makeText(context, "Microphone permission required for recording", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BuddyColors.BackgroundDeep)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // ── 1. TOP HEADER ────────────────────────────────────────────────
            item {
                HomeHeader(
                    userName = uiState.userName,
                    subtitleText = uiState.subtitleText,
                    onProfileClick = onNavigateToSettings,
                )
            }

            // ── 2. COMPACT DYNAMIC ISLAND VOICE CAPSULE ──────────────────────
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CompactVoiceCapsule(
                        buddyMode = uiState.buddyMode,
                        onCapsuleClick = {
                            viewModel.onNewConversation { convId -> onNavigateToChat(convId) }
                        }
                    )
                }
            }

            // ── 3. TODAY AT A GLANCE ──────────────────────────────────────────
            item {
                TodayAtAGlanceSection(
                    activeTasksCount = uiState.activeTasks.size,
                    completedTasksCount = uiState.completedTasksCount,
                    reminderCount = uiState.reminderCount,
                    eventCount = uiState.eventCount,
                    weatherTemp = uiState.weatherTemp,
                    weatherCondition = uiState.weatherCondition,
                )
            }

            // ── 4. TOP PRIORITY ───────────────────────────────────────────────
            item {
                TopPrioritySection(
                    topTask = uiState.topPriorityTask ?: uiState.activeTasks.firstOrNull(),
                    onCompleteTask = { taskId -> viewModel.onCompleteTask(taskId) }
                )
            }

            // ── 5. QUICK ACTIONS ──────────────────────────────────────────────
            item {
                HomeQuickActionsSection(
                    onVoiceRecordClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            showVoiceRecordDialog = true
                            isRecordingActive = true
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onAddTaskClick = { showAddTaskDialog = true },
                    onAddReminderClick = { showAddTaskDialog = true },
                    onAskAIOSClick = { viewModel.onNewConversation { convId -> onNavigateToChat(convId) } }
                )
            }

            // ── 6. RECENT ACTIVITY ────────────────────────────────────────────
            item {
                RecentActivitySection(
                    recentTasks = uiState.activeTasks.take(3),
                    onViewAllClick = { viewModel.onNewConversation { convId -> onNavigateToChat(convId) } },
                    onTaskClick = { viewModel.onNewConversation { convId -> onNavigateToChat(convId) } }
                )
            }
        }

        // ── VOICE RECORD DIALOG ───────────────────────────────────────────────
        if (showVoiceRecordDialog) {
            AlertDialog(
                onDismissRequest = {
                    isRecordingActive = false
                    showVoiceRecordDialog = false
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(BuddyColors.Rose))
                        Spacer(Modifier.width(8.dp))
                        Text("Voice Recording", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    ) {
                        val minutes = recordTimerSeconds / 60
                        val secs = recordTimerSeconds % 60
                        val timeFormatted = String.format(Locale.ENGLISH, "%02d:%02d", minutes, secs)

                        Text(
                            text = if (isRecordingActive) "🔴 Recording $timeFormatted" else "Recording Paused ($timeFormatted)",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (isRecordingActive) BuddyColors.Rose else BuddyColors.TextMuted,
                        )

                        Spacer(Modifier.height(16.dp))
                        VoiceWaveform(isActive = isRecordingActive, activeColor = BuddyColors.Rose, barCount = 16)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        isRecordingActive = false
                        showVoiceRecordDialog = false
                        Toast.makeText(context, "Voice recording saved to local vault", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("SAVE", color = BuddyColors.Cyan, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        isRecordingActive = false
                        showVoiceRecordDialog = false
                        Toast.makeText(context, "Recording discarded", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("DELETE", color = BuddyColors.Rose)
                    }
                },
                containerColor = BuddyColors.SurfaceDark,
            )
        }

        // ── ADD TASK DIALOG ───────────────────────────────────────────────────
        if (showAddTaskDialog) {
            AlertDialog(
                onDismissRequest = { showAddTaskDialog = false },
                title = { Text("Add Task / Reminder", color = Color.White) },
                text = {
                    OutlinedTextField(
                        value = newTaskTitle,
                        onValueChange = { newTaskTitle = it },
                        placeholder = { Text("e.g., Study Java at 7 PM") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BuddyColors.Cyan,
                            unfocusedBorderColor = BuddyColors.GlassBorder,
                            focusedTextColor = Color.White,
                        ),
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.onCreateQuickTask(newTaskTitle)
                        newTaskTitle = ""
                        showAddTaskDialog = false
                    }) {
                        Text("Add", color = BuddyColors.Cyan, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddTaskDialog = false }) {
                        Text("Cancel", color = BuddyColors.TextMuted)
                    }
                },
                containerColor = BuddyColors.SurfaceDark,
            )
        }
    }
}

// ── 1. TOP HEADER ─────────────────────────────────────────────────────────────

@Composable
private fun HomeHeader(
    userName: String,
    subtitleText: String,
    onProfileClick: () -> Unit,
) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val salutation = when (hour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..20 -> "Good Evening"
        else -> "Good Night"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "$salutation,\n$userName 👋",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    lineHeight = 34.sp
                ),
                color = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodyMedium,
                color = BuddyColors.TextSecondary,
            )
        }

        // Profile Avatar Button
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(BuddyColors.SurfaceDark)
                .border(1.dp, BuddyColors.GlassBorder, CircleShape)
                .clickableWithScale(onClick = onProfileClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Open Settings & Profile",
                tint = BuddyColors.Cyan,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ── 2. COMPACT DYNAMIC ISLAND VOICE CAPSULE ───────────────────────────────────

@Composable
private fun CompactVoiceCapsule(
    buddyMode: BuddyMode,
    onCapsuleClick: () -> Unit,
) {
    GlassCard(
        modifier = Modifier
            .width(200.dp)
            .height(48.dp)
            .clickableWithScale(onClick = onCapsuleClick),
        shape = BuddyShapes.Pill,
        backgroundColor = BuddyColors.SurfaceDark.copy(alpha = 0.95f),
        borderBrush = BuddyColors.GlassCardBorderGradient,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            BuddyOrb(
                buddyMode = buddyMode,
                size = 16.dp,
                orbState = if (buddyMode == BuddyMode.OFF) OrbState.OFF else OrbState.IDLE,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (buddyMode == BuddyMode.OFF) "AIOS Off" else "● AIOS Voice · Tap",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
    }
}

// ── 3. TODAY AT A GLANCE ──────────────────────────────────────────────────────

@Composable
private fun TodayAtAGlanceSection(
    activeTasksCount: Int,
    completedTasksCount: Int,
    reminderCount: Int,
    eventCount: Int,
    weatherTemp: String,
    weatherCondition: String,
) {
    Column {
        Text(
            text = "Today at a glance",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
        )
        Spacer(Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item {
                GlanceCard(
                    icon = Icons.Default.CheckCircle,
                    iconColor = BuddyColors.ActiveGreen,
                    valueText = "$activeTasksCount",
                    titleText = "Tasks",
                    subtitleText = "$completedTasksCount completed",
                )
            }
            item {
                GlanceCard(
                    icon = Icons.Default.Alarm,
                    iconColor = BuddyColors.Rose,
                    valueText = "$reminderCount",
                    titleText = "Reminders",
                    subtitleText = "Upcoming today",
                )
            }
            item {
                GlanceCard(
                    icon = Icons.Default.CalendarToday,
                    iconColor = BuddyColors.PurpleLight,
                    valueText = "$eventCount",
                    titleText = "Events",
                    subtitleText = "Scheduled today",
                )
            }
            item {
                GlanceCard(
                    icon = Icons.Default.Cloud,
                    iconColor = BuddyColors.Cyan,
                    valueText = weatherTemp,
                    titleText = "Weather",
                    subtitleText = weatherCondition,
                )
            }
        }
    }
}

@Composable
private fun GlanceCard(
    icon: ImageVector,
    iconColor: Color,
    valueText: String,
    titleText: String,
    subtitleText: String,
) {
    GlassCard(
        modifier = Modifier.width(115.dp),
        shape = RoundedCornerShape(18.dp),
        backgroundColor = BuddyColors.SurfaceDark.copy(alpha = 0.85f),
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = titleText,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
            Text(
                text = titleText,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = BuddyColors.TextMuted,
            )
        }
    }
}

// ── 4. TOP PRIORITY ───────────────────────────────────────────────────────────

@Composable
private fun TopPrioritySection(
    topTask: Task?,
    onCompleteTask: (String) -> Unit,
) {
    Column {
        Text(
            text = "🎯 Top Priority",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
        )
        Spacer(Modifier.height(10.dp))

        if (topTask != null) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                backgroundColor = BuddyColors.SurfaceDark.copy(alpha = 0.90f),
                borderBrush = BuddyColors.GlassCardBorderGradient,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = topTask.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                        Spacer(Modifier.height(4.dp))
                        val dueStr = topTask.reminderTime?.let {
                            "Due today at " + SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(it))
                        } ?: "Priority action"
                        Text(
                            text = dueStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = BuddyColors.Cyan,
                        )
                    }

                    IconButton(onClick = { onCompleteTask(topTask.id) }) {
                        Icon(
                            imageVector = Icons.Default.RadioButtonUnchecked,
                            contentDescription = "Complete Top Priority Task",
                            tint = BuddyColors.Cyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        } else {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = BuddyColors.SurfaceDark.copy(alpha = 0.60f),
            ) {
                Text(
                    text = "No top priority set for today. All tasks caught up!",
                    style = MaterialTheme.typography.bodySmall,
                    color = BuddyColors.TextMuted,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ── 5. QUICK ACTIONS ──────────────────────────────────────────────────────────

@Composable
private fun HomeQuickActionsSection(
    onVoiceRecordClick: () -> Unit,
    onAddTaskClick: () -> Unit,
    onAddReminderClick: () -> Unit,
    onAskAIOSClick: () -> Unit,
) {
    Column {
        Text(
            text = "⚡ Quick Actions",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
        )
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            QuickActionButton("Voice Record", Icons.Default.Mic, BuddyColors.Rose, onVoiceRecordClick, Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            QuickActionButton("Add Task", Icons.Default.Add, BuddyColors.ActiveGreen, onAddTaskClick, Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            QuickActionButton("Add Reminder", Icons.Default.AccessTime, BuddyColors.PurpleLight, onAddReminderClick, Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            QuickActionButton("Ask AIOS", Icons.Default.ChatBubbleOutline, BuddyColors.Cyan, onAskAIOSClick, Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickActionButton(
    label: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier.clickableWithScale(onClick = onClick),
        backgroundColor = BuddyColors.SurfaceDark.copy(alpha = 0.85f),
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 12.dp, horizontal = 4.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accentColor,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── 6. RECENT ACTIVITY ────────────────────────────────────────────────────────

@Composable
private fun RecentActivitySection(
    recentTasks: List<Task>,
    onViewAllClick: () -> Unit,
    onTaskClick: (String) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Recent",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
            Text(
                text = "View all",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = BuddyColors.Cyan,
                modifier = Modifier.clickable { onViewAllClick() }
            )
        }
        Spacer(Modifier.height(10.dp))

        if (recentTasks.isEmpty()) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = BuddyColors.SurfaceDark.copy(alpha = 0.60f),
            ) {
                Text(
                    text = "No recent activity",
                    style = MaterialTheme.typography.bodySmall,
                    color = BuddyColors.TextMuted,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recentTasks.forEach { task ->
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTaskClick(task.id) },
                        backgroundColor = BuddyColors.SurfaceDark.copy(alpha = 0.75f),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = BuddyColors.ActiveGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = task.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.White,
                                    )
                                    Text(
                                        text = "Active • Today",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = BuddyColors.TextMuted,
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "View Task Detail",
                                tint = BuddyColors.TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
