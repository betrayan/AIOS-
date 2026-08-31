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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.buddy.aios.core.domain.entity.TaskPriority
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
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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

    // ── Unified Enhanced Task/Reminder Sheet State ───────────────────────────
    var showCreationSheet by remember { mutableStateOf(false) }
    var initialSheetIsReminder by remember { mutableStateOf(false) }

    var showVoiceRecordDialog by remember { mutableStateOf(false) }
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
                    onTasksClick = {
                        initialSheetIsReminder = false
                        showCreationSheet = true
                    },
                    onRemindersClick = {
                        initialSheetIsReminder = true
                        showCreationSheet = true
                    },
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
                    onAddTaskClick = {
                        initialSheetIsReminder = false
                        showCreationSheet = true
                    },
                    onAddReminderClick = {
                        initialSheetIsReminder = true
                        showCreationSheet = true
                    },
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

        // ── ENHANCED TASK & REMINDER CREATION SHEET ──────────────────────────────
        if (showCreationSheet) {
            EnhancedTaskReminderSheet(
                initialIsReminder = initialSheetIsReminder,
                onDismiss = { showCreationSheet = false },
                onSave = { title, desc, dateMs, hour, min, priority, voiceEnabled, isReminder ->
                    if (isReminder) {
                        val cal = Calendar.getInstance().apply {
                            timeInMillis = dateMs
                            set(Calendar.HOUR_OF_DAY, hour)
                            set(Calendar.MINUTE, min)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        val triggerMs = cal.timeInMillis
                        if (triggerMs <= System.currentTimeMillis()) {
                            Toast.makeText(context, "Please select a future time", Toast.LENGTH_SHORT).show()
                            return@EnhancedTaskReminderSheet
                        }
                        viewModel.onCreateReminder(
                            title = title,
                            description = desc,
                            reminderTimeMs = triggerMs,
                            priority = priority,
                            voiceEnabled = voiceEnabled,
                        )
                        val formatted = SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.ENGLISH).format(Date(triggerMs))
                        Toast.makeText(context, "⏰ Reminder set for $formatted", Toast.LENGTH_LONG).show()
                    } else {
                        viewModel.onCreateQuickTask(title)
                        Toast.makeText(context, "📝 Task added: $title", Toast.LENGTH_SHORT).show()
                    }
                    showCreationSheet = false
                }
            )
        }
    }
}

// ── ENHANCED TASK & REMINDER CREATION SHEET ─────────────────────────────────────

/**
 * Enhanced Task & Reminder Creator Bottom Sheet.
 *
 * Features:
 * - Dual Mode Tab Bar: 📝 Task | ⏰ Reminder
 * - Live Day, Date & Time Preview Header with AM/PM pill
 * - Quick Date Chips (Today, Tomorrow, In 2 Days, Custom Calendar)
 * - Quick Time Chips (Morning 9:00 AM, Noon 1:00 PM, Evening 5:00 PM, Night 8:00 PM, Custom Wheel)
 * - Priority Selector Chips (Low, Medium, High)
 * - Voice Announcement Toggle
 * - Sleek dark glassmorphic styling with glowing accents
 */
@androidx.compose.runtime.Composable
@androidx.compose.material3.ExperimentalMaterial3Api
private fun EnhancedTaskReminderSheet(
    initialIsReminder: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        description: String,
        dateMs: Long,
        hour: Int,
        minute: Int,
        priority: TaskPriority,
        voiceEnabled: Boolean,
        isReminder: Boolean,
    ) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isReminderMode by remember { mutableStateOf(initialIsReminder) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    // Date state
    var selectedDateMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    // Time state
    val calNow = Calendar.getInstance()
    var selectedHour by remember { mutableIntStateOf(calNow.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableIntStateOf(calNow.get(Calendar.MINUTE)) }
    var showTimePickerDialog by remember { mutableStateOf(false) }

    // Options
    var priority by remember { mutableStateOf(TaskPriority.MEDIUM) }
    var voiceEnabled by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BuddyColors.BackgroundDeep,
        scrimColor = Color.Black.copy(alpha = 0.75f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(BuddyColors.GlassBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Mode Switcher Tab Header ──────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(BuddyShapes.Pill)
                    .background(BuddyColors.SurfaceDark)
                    .border(1.dp, BuddyColors.GlassBorder, BuddyShapes.Pill)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Task Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(BuddyShapes.Pill)
                        .background(if (!isReminderMode) BuddyColors.Cyan.copy(alpha = 0.25f) else Color.Transparent)
                        .clickableWithScale { isReminderMode = false },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (!isReminderMode) BuddyColors.Cyan else BuddyColors.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "📝 Add Task",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (!isReminderMode) Color.White else BuddyColors.TextMuted,
                        )
                    }
                }

                // Reminder Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(BuddyShapes.Pill)
                        .background(if (isReminderMode) BuddyColors.PurpleLight.copy(alpha = 0.30f) else Color.Transparent)
                        .clickableWithScale { isReminderMode = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            tint = if (isReminderMode) BuddyColors.PurpleLight else BuddyColors.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "⏰ Add Reminder",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (isReminderMode) Color.White else BuddyColors.TextMuted,
                        )
                    }
                }
            }

            // ── Inputs Card ──────────────────────────────────────────────────
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = BuddyColors.SurfaceDark.copy(alpha = 0.85f),
                borderBrush = BuddyColors.GlassCardBorderGradient,
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = {
                            Text(
                                if (isReminderMode) "e.g., Chennai interview" else "e.g., Complete Kotlin review",
                                color = BuddyColors.TextMuted
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isReminderMode) Icons.Default.Alarm else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isReminderMode) BuddyColors.Rose else BuddyColors.ActiveGreen,
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BuddyColors.Cyan,
                            unfocusedBorderColor = BuddyColors.GlassBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        placeholder = { Text("Description / notes (optional)", color = BuddyColors.TextMuted) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = BuddyColors.TextMuted,
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BuddyColors.Cyan,
                            unfocusedBorderColor = BuddyColors.GlassBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ── Date & Time Controls (Visible for Reminders) ────────────────
            if (isReminderMode) {
                // DATE SECTION
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "🗓️ DAY & DATE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                            color = BuddyColors.TextSecondary,
                        )
                        val dateFormatted = SimpleDateFormat("EEEE, MMM d", Locale.ENGLISH).format(Date(selectedDateMs))
                        Text(
                            dateFormatted,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = BuddyColors.Cyan,
                        )
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            val todayMs = System.currentTimeMillis()
                            ChipOption("Today", isSelected = isSameDay(selectedDateMs, todayMs)) {
                                selectedDateMs = todayMs
                            }
                        }
                        item {
                            val tomMs = System.currentTimeMillis() + (24 * 60 * 60 * 1000L)
                            ChipOption("Tomorrow", isSelected = isSameDay(selectedDateMs, tomMs)) {
                                selectedDateMs = tomMs
                            }
                        }
                        item {
                            val next2Ms = System.currentTimeMillis() + (2 * 24 * 60 * 60 * 1000L)
                            ChipOption("In 2 Days", isSelected = isSameDay(selectedDateMs, next2Ms)) {
                                selectedDateMs = next2Ms
                            }
                        }
                        item {
                            ChipOption("📅 Calendar Date", isSelected = false) {
                                showDatePickerDialog = true
                            }
                        }
                    }
                }

                // TIME SECTION WITH AM/PM DISPLAY
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "⏰ TIME & AM/PM",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                            color = BuddyColors.TextSecondary,
                        )
                        val timeCal = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, selectedHour)
                            set(Calendar.MINUTE, selectedMinute)
                        }
                        val timeFormatted = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(timeCal.time)
                        Text(
                            timeFormatted,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = BuddyColors.Rose,
                        )
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            ChipOption("🌅 9:00 AM", isSelected = selectedHour == 9 && selectedMinute == 0) {
                                selectedHour = 9
                                selectedMinute = 0
                            }
                        }
                        item {
                            ChipOption("☀️ 1:00 PM", isSelected = selectedHour == 13 && selectedMinute == 0) {
                                selectedHour = 13
                                selectedMinute = 0
                            }
                        }
                        item {
                            ChipOption("🌆 5:00 PM", isSelected = selectedHour == 17 && selectedMinute == 0) {
                                selectedHour = 17
                                selectedMinute = 0
                            }
                        }
                        item {
                            ChipOption("🌙 8:00 PM", isSelected = selectedHour == 20 && selectedMinute == 0) {
                                selectedHour = 20
                                selectedMinute = 0
                            }
                        }
                        item {
                            ChipOption("⏰ Custom Time", isSelected = false) {
                                showTimePickerDialog = true
                            }
                        }
                    }
                }
            }

            // ── Priority Section ──────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "🎯 PRIORITY",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                    color = BuddyColors.TextSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PriorityChip("🟢 Low", isSelected = priority == TaskPriority.LOW, color = BuddyColors.ActiveGreen) {
                        priority = TaskPriority.LOW
                    }
                    PriorityChip("🟡 Medium", isSelected = priority == TaskPriority.MEDIUM, color = BuddyColors.Cyan) {
                        priority = TaskPriority.MEDIUM
                    }
                    PriorityChip("🔴 High", isSelected = priority == TaskPriority.HIGH, color = BuddyColors.Rose) {
                        priority = TaskPriority.HIGH
                    }
                }
            }

            // ── Voice Announce Toggle ────────────────────────────────────────
            if (isReminderMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(BuddyColors.SurfaceDark)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (voiceEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = null,
                            tint = if (voiceEnabled) BuddyColors.Cyan else BuddyColors.TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Voice Announcement",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                if (voiceEnabled) "Buddy speaks out loud when alarm fires" else "Notification banner only",
                                style = MaterialTheme.typography.labelSmall,
                                color = BuddyColors.TextMuted
                            )
                        }
                    }
                    Switch(
                        checked = voiceEnabled,
                        onCheckedChange = { voiceEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = BuddyColors.Cyan,
                            uncheckedThumbColor = BuddyColors.TextMuted,
                            uncheckedTrackColor = BuddyColors.SurfaceDark,
                        )
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Primary Action Save Button ────────────────────────────────────
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickableWithScale(enabled = title.isNotBlank()) {
                        onSave(
                            title,
                            description,
                            selectedDateMs,
                            selectedHour,
                            selectedMinute,
                            priority,
                            voiceEnabled,
                            isReminderMode
                        )
                    },
                shape = BuddyShapes.Pill,
                backgroundColor = if (title.isNotBlank())
                    if (isReminderMode) BuddyColors.Rose.copy(alpha = 0.90f) else BuddyColors.Cyan.copy(alpha = 0.90f)
                else
                    BuddyColors.SurfaceDark,
                borderBrush = BuddyColors.GlassCardBorderGradient,
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val btnText = when {
                        title.isBlank() -> "Enter Title"
                        isReminderMode -> {
                            val timeCal = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, selectedHour)
                                set(Calendar.MINUTE, selectedMinute)
                            }
                            val tStr = SimpleDateFormat("h:mm a", Locale.ENGLISH).format(timeCal.time)
                            "⏰ SET REMINDER AT $tStr"
                        }
                        else -> "📝 CREATE TASK"
                    }
                    Text(
                        btnText,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (title.isNotBlank()) Color.White else BuddyColors.TextMuted,
                    )
                }
            }
        }
    }

    // ── DATE PICKER DIALOG ────────────────────────────────────────────────────
    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMs)
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedDateMs = it }
                    showDatePickerDialog = false
                }) {
                    Text("OK", color = BuddyColors.Cyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Cancel", color = BuddyColors.TextMuted)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // ── TIME PICKER DIALOG ────────────────────────────────────────────────────
    if (showTimePickerDialog) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePickerDialog = false },
            containerColor = BuddyColors.SurfaceDark,
            title = { Text("Select Time & AM/PM", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedHour = timePickerState.hour
                    selectedMinute = timePickerState.minute
                    showTimePickerDialog = false
                }) {
                    Text("OK", color = BuddyColors.Cyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerDialog = false }) {
                    Text("Cancel", color = BuddyColors.TextMuted)
                }
            }
        )
    }
}

@Composable
private fun ChipOption(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(BuddyShapes.Pill)
            .background(if (isSelected) BuddyColors.Cyan.copy(alpha = 0.25f) else BuddyColors.SurfaceDark)
            .border(1.dp, if (isSelected) BuddyColors.Cyan else BuddyColors.GlassBorder, BuddyShapes.Pill)
            .clickableWithScale(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (isSelected) Color.White else BuddyColors.TextMuted,
        )
    }
}

@Composable
private fun PriorityChip(label: String, isSelected: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(BuddyShapes.Pill)
            .background(if (isSelected) color.copy(alpha = 0.25f) else BuddyColors.SurfaceDark)
            .border(1.dp, if (isSelected) color else BuddyColors.GlassBorder, BuddyShapes.Pill)
            .clickableWithScale(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (isSelected) Color.White else BuddyColors.TextMuted,
        )
    }
}

private fun isSameDay(ms1: Long, ms2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = ms1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = ms2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
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
            .width(220.dp)
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
                text = if (buddyMode == BuddyMode.OFF) "AIOS Off" else "● Voice · Tap",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
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
    onTasksClick: () -> Unit = {},
    onRemindersClick: () -> Unit = {},
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
                    onClick = onTasksClick,
                )
            }
            item {
                GlanceCard(
                    icon = Icons.Default.Alarm,
                    iconColor = BuddyColors.Rose,
                    valueText = "$reminderCount",
                    titleText = "Reminders",
                    subtitleText = "Upcoming today",
                    onClick = onRemindersClick,
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
    onClick: (() -> Unit)? = null,
) {
    GlassCard(
        modifier = Modifier
            .width(115.dp)
            .then(
                if (onClick != null) Modifier.clickableWithScale(onClick = onClick)
                else Modifier
            ),
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
                        val taskTimestamp = topTask.reminderTime ?: topTask.dueDate
                        val dueStr = if (taskTimestamp != null && taskTimestamp > 0L) {
                            com.buddy.aios.core.common.time.ReminderDateFormatter.formatDueDateTime(taskTimestamp)
                        } else "Priority action"
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
