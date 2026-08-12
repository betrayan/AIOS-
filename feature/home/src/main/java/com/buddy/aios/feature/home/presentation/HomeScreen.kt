package com.buddy.aios.feature.home.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buddy.aios.core.ai.morning.MorningSummary
import com.buddy.aios.core.analytics.activity.MorningReadiness
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.Task
import com.buddy.aios.core.ui.animation.AIOSMotion
import com.buddy.aios.core.ui.animation.clickableWithScale
import com.buddy.aios.core.ui.components.BuddyOrb
import com.buddy.aios.core.ui.components.GlassCard
import com.buddy.aios.core.ui.components.OrbState
import com.buddy.aios.core.ui.shapes.BuddyShapes
import com.buddy.aios.core.ui.theme.BuddyColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AIOS Central Command Center Home Screen.
 *
 * Structure:
 * - TOP: Time, greeting, and Morning Readiness Insight Banner (non-medical, local-only).
 * - CENTER: Large 8-state AIOS Orb + Contextual Status Message below.
 * - TODAY: Clean task timeline/list.
 * - QUICK ACTIONS: Ask AIOS, Voice, New Task, Memory Vault.
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
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var newTaskTitle by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BuddyColors.BackgroundDeep),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ── TOP: Time & Contextual Greeting Header ───────────────────────
            item {
                HomeHeaderSection(
                    greeting = uiState.userGreeting,
                    buddyMode = uiState.buddyMode,
                )
            }

            // ── MORNING INSIGHT BANNER (when morning window is active) ───────
            uiState.morningSummary?.let { morning ->
                if (morning.isMorningWindowActive) {
                    item {
                        MorningInsightBanner(morning = morning)
                    }
                }
            }

            // ── CENTER: Primary AIOS Orb Command Visual ──────────────────────
            item {
                HomeCentralOrbSection(
                    buddyMode = uiState.buddyMode,
                    activeTaskCount = uiState.activeTasks.size,
                    morningSuggestion = uiState.morningSummary?.morningSuggestion,
                    onOrbClick = {
                        viewModel.onNewConversation { convId -> onNavigateToChat(convId) }
                    },
                )
            }

            // ── QUICK ACTIONS BAR ─────────────────────────────────────────────
            item {
                HomeQuickActionsSection(
                    onAskClick = { viewModel.onNewConversation { convId -> onNavigateToChat(convId) } },
                    onVoiceClick = { viewModel.onNewConversation { convId -> onNavigateToChat(convId) } },
                    onAddTaskClick = { showAddTaskDialog = true },
                    onMemoryClick = onNavigateToMemory,
                )
            }

            // ── TODAY: Clean Task Timeline ────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "TODAY",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = BuddyColors.TextMuted,
                        letterSpacing = 1.5.sp,
                    )
                    Text(
                        text = "${uiState.activeTasks.size} tasks remaining",
                        style = MaterialTheme.typography.labelSmall,
                        color = BuddyColors.TextMuted,
                    )
                }
            }

            if (uiState.activeTasks.isEmpty()) {
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = BuddyColors.SurfaceDark.copy(alpha = 0.6f),
                    ) {
                        Text(
                            text = "Nothing urgent right now. Tell AIOS what you want to accomplish today.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BuddyColors.TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
            } else {
                items(uiState.activeTasks, key = { it.id }) { task ->
                    HomeTimelineTaskItem(
                        task = task,
                        onComplete = { viewModel.onCompleteTask(task.id) },
                    )
                }
            }
        }

        // Add Quick Task Dialog
        if (showAddTaskDialog) {
            AlertDialog(
                onDismissRequest = { showAddTaskDialog = false },
                title = { Text("New Reminder", color = Color.White) },
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

// ── Header Section ────────────────────────────────────────────────────────────

@Composable
private fun HomeHeaderSection(
    greeting: String,
    buddyMode: BuddyMode,
) {
    val dateStr = SimpleDateFormat("EEEE, MMMM d", Locale.ENGLISH).format(Date())

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            Text(
                text = dateStr.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = BuddyColors.TextMuted,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }

        // Mode Status Badge
        val (modeColor, modeLabel) = when (buddyMode) {
            BuddyMode.ACTIVE -> BuddyColors.ActiveGreen to "ACTIVE"
            BuddyMode.QUIET  -> BuddyColors.QuietYellow to "QUIET"
            BuddyMode.SILENT -> BuddyColors.SilentBlue to "SILENT"
            BuddyMode.OFF    -> BuddyColors.OffGray to "OFF"
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(BuddyShapes.Pill)
                .background(modeColor.copy(alpha = 0.15f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(modeColor)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = modeLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = modeColor,
            )
        }
    }
}

// ── Morning Insight Banner ────────────────────────────────────────────────────

@Composable
private fun MorningInsightBanner(morning: MorningSummary) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = BuddyColors.SurfaceDark.copy(alpha = 0.85f),
        borderBrush = BuddyColors.GlassCardBorderGradient,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Nightlight,
                        contentDescription = null,
                        tint = BuddyColors.PurpleLight,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "OVERNIGHT INACTIVITY & READINESS",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = BuddyColors.PurpleLight,
                        letterSpacing = 1.0.sp,
                    )
                }

                val readinessBadgeColor = when (morning.sleepEstimate.morningReadiness) {
                    MorningReadiness.READY       -> BuddyColors.ActiveGreen
                    MorningReadiness.NORMAL      -> BuddyColors.Cyan
                    MorningReadiness.TAKE_IT_EASY -> BuddyColors.QuietYellow
                    else                         -> BuddyColors.TextMuted
                }
                Text(
                    text = morning.sleepEstimate.morningReadiness.name,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = readinessBadgeColor,
                    modifier = Modifier
                        .clip(BuddyShapes.Pill)
                        .background(readinessBadgeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            if (morning.sleepEstimate.hasSufficientData) {
                Text(
                    text = "Estimated sleep: ${morning.sleepEstimate.formattedDuration} (${morning.sleepEstimate.formattedWindow})",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = morning.sleepEstimate.comparisonToTargetText,
                    style = MaterialTheme.typography.bodySmall,
                    color = BuddyColors.TextSecondary,
                )
            } else {
                Text(
                    text = morning.sleepEstimate.comparisonToTargetText,
                    style = MaterialTheme.typography.bodySmall,
                    color = BuddyColors.TextMuted,
                )
            }
        }
    }
}

// ── Central Orb Section ───────────────────────────────────────────────────────

@Composable
private fun HomeCentralOrbSection(
    buddyMode: BuddyMode,
    activeTaskCount: Int,
    morningSuggestion: String?,
    onOrbClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.clickableWithScale(onClick = onOrbClick),
        ) {
            BuddyOrb(
                buddyMode = buddyMode,
                size = 150.dp,
                orbState = if (buddyMode == BuddyMode.OFF) OrbState.OFF else OrbState.IDLE,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Contextual AIOS Status Message below Orb
        val statusMessage = when {
            buddyMode == BuddyMode.OFF -> "AIOS is currently OFF"
            morningSuggestion != null  -> morningSuggestion
            activeTaskCount > 0        -> "$activeTaskCount task${if (activeTaskCount > 1) "s" else ""} waiting today"
            else                       -> "Ready when you are"
        }

        Text(
            text = statusMessage,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = BuddyColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

// ── Quick Actions Bar ─────────────────────────────────────────────────────────

@Composable
private fun HomeQuickActionsSection(
    onAskClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onAddTaskClick: () -> Unit,
    onMemoryClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        QuickActionButton("Ask AIOS", Icons.Default.ChatBubbleOutline, BuddyColors.Cyan, onAskClick, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        QuickActionButton("Voice", Icons.Default.Mic, BuddyColors.Rose, onVoiceClick, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        QuickActionButton("+ Task", Icons.Default.Add, BuddyColors.ActiveGreen, onAddTaskClick, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        QuickActionButton("Vault", Icons.Default.Memory, BuddyColors.PurpleLight, onMemoryClick, Modifier.weight(1f))
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
        backgroundColor = BuddyColors.SurfaceDark.copy(alpha = 0.80f),
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 10.dp, horizontal = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accentColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
    }
}

// ── Timeline Task Item ────────────────────────────────────────────────────────

@Composable
private fun HomeTimelineTaskItem(
    task: Task,
    onComplete: () -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = BuddyColors.SurfaceDark.copy(alpha = 0.70f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Default.RadioButtonUnchecked,
                    contentDescription = "Complete task",
                    tint = BuddyColors.TextMuted,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onComplete() },
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                    task.dueDate?.let { due ->
                        val dueStr = SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(due))
                        Text(
                            text = dueStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = BuddyColors.Cyan,
                        )
                    }
                }
            }
        }
    }
}
