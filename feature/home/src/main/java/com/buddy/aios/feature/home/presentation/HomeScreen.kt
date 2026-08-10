package com.buddy.aios.feature.home.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.Task
import com.buddy.aios.core.ui.components.AIOSButton
import com.buddy.aios.core.ui.components.AIOSChip
import com.buddy.aios.core.ui.components.AIOSLoadingIndicator
import com.buddy.aios.core.ui.components.BuddyOrb
import com.buddy.aios.core.ui.components.GlassCard
import com.buddy.aios.core.ui.components.OrbState
import com.buddy.aios.core.ui.shapes.BuddyShapes
import com.buddy.aios.core.ui.theme.BuddyColors
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenConversation: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMemory: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var newTaskTitle by remember { mutableStateOf("") }
    var showAddTaskDialog by remember { mutableStateOf(false) }

    val dynamicGreeting = remember { getDynamicGreeting() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BuddyColors.BackgroundRadialGradient)
    ) {
        if (state.isLoading) {
            AIOSLoadingIndicator()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp),
            ) {
                // 1. Header & Dynamic Greeting
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "$dynamicGreeting, ${state.userGreeting.removePrefix("Hey ").removeSuffix("!")}",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Ready to make today count?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = BuddyColors.TextSecondary
                            )
                        }

                        IconButton(onClick = onOpenMemory) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = "Memories",
                                tint = BuddyColors.Cyan,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                // 2. AI Core Animated Orb Centerpiece
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BuddyOrb(
                            buddyMode = state.buddyMode,
                            size = 150.dp,
                            orbState = OrbState.IDLE
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(BuddyShapes.Pill)
                                    .background(
                                        when (state.buddyMode) {
                                            BuddyMode.ACTIVE -> BuddyColors.ActiveGreen
                                            BuddyMode.QUIET -> BuddyColors.QuietYellow
                                            BuddyMode.SILENT -> BuddyColors.SilentBlue
                                            BuddyMode.OFF -> BuddyColors.OffGray
                                        }
                                    )
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "AIOS • ${state.buddyMode.name} MODE",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = BuddyColors.TextSecondary
                            )
                        }
                    }
                }

                // 3. Buddy Mode Selector Pills
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = BuddyShapes.ExtraLarge,
                        backgroundColor = BuddyColors.GlassSurface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            BuddyMode.entries.forEach { mode ->
                                val isSelected = state.buddyMode == mode
                                AIOSChip(
                                    text = mode.name,
                                    isSelected = isSelected,
                                    onClick = { viewModel.onSetBuddyMode(mode) }
                                )
                            }
                        }
                    }
                }

                // 4. Memory Insight Card
                item {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenMemory),
                        backgroundColor = BuddyColors.SurfaceDark.copy(alpha = 0.85f),
                        borderBrush = BuddyColors.GlassCardBorderGradient
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(BuddyShapes.Pill)
                                        .background(BuddyColors.PurpleGlow.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Memory,
                                        contentDescription = null,
                                        tint = BuddyColors.Cyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "MEMORY INSIGHT",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = BuddyColors.Cyan
                                    )
                                    Text(
                                        text = if (state.memoryCount > 0) "AIOS remembers ${state.memoryCount} things about you" else "No saved memories yet",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. Today Overview Dashboard Card
                item {
                    val completedCount = state.activeTasks.count { it.isCompleted }
                    val totalCount = state.activeTasks.size
                    val percent = if (totalCount > 0) (completedCount * 100 / totalCount) else 0

                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = BuddyColors.SurfaceDark.copy(alpha = 0.85f)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TODAY OVERVIEW",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = BuddyColors.Cyan
                                )
                                Text(
                                    text = "$percent% COMPLETE",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = BuddyColors.PurpleLight
                                )
                            }
                            Spacer(Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                DashboardMetric("Tasks", "$completedCount/$totalCount", Icons.Default.CheckCircle)
                                DashboardMetric("Focus", "2h 40m", Icons.Default.HourglassTop)
                                DashboardMetric("Screen", "3h 12m", Icons.Default.Smartphone)
                                DashboardMetric("Memories", "${state.memoryCount}", Icons.Default.Memory)
                            }
                        }
                    }
                }

                // 6. Featured Next Up Task Card
                item {
                    val nextTask = state.activeTasks.firstOrNull { !it.isCompleted }
                    if (nextTask != null) {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            borderBrush = BuddyColors.PrimaryGradient
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "NEXT UP",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = BuddyColors.PurpleLight
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = nextTask.title,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.onCompleteTask(nextTask.id) },
                                    modifier = Modifier
                                        .clip(BuddyShapes.Pill)
                                        .background(BuddyColors.PurpleGlow)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Complete", tint = Color.White)
                                }
                            }
                        }
                    }
                }

                // 6. Action Bar (New Task & Start Chat)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AIOSButton(
                            text = "New Chat",
                            onClick = { viewModel.onNewConversation(onOpenConversation) },
                            icon = Icons.Default.AutoAwesome,
                            modifier = Modifier.weight(1f)
                        )
                        AIOSButton(
                            text = "Add Task",
                            onClick = { showAddTaskDialog = true },
                            icon = Icons.Default.Add,
                            gradient = Brush.linearGradient(listOf(BuddyColors.SurfaceElevated, BuddyColors.SurfaceElevated)),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 7. Active Tasks List
                item {
                    Text(
                        text = "Active Tasks",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                if (state.activeTasks.isEmpty()) {
                    item {
                        Text(
                            text = "No active tasks. Tap 'Add Task' to create one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BuddyColors.TextMuted,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                } else {
                    items(state.activeTasks, key = { it.id }) { task ->
                        TaskItemCard(
                            task = task,
                            onComplete = { viewModel.onCompleteTask(task.id) }
                        )
                    }
                }
            }
        }
    }

    // Add Task Dialog
    if (showAddTaskDialog) {
        AlertDialog(
            onDismissRequest = { showAddTaskDialog = false },
            title = { Text("Create New Task", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newTaskTitle,
                    onValueChange = { newTaskTitle = it },
                    placeholder = { Text("Study Java, Workout, etc.") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BuddyColors.Cyan,
                        unfocusedBorderColor = BuddyColors.TextMuted,
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onCreateQuickTask(newTaskTitle)
                        newTaskTitle = ""
                        showAddTaskDialog = false
                    }
                ) {
                    Text("Add", color = BuddyColors.Cyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTaskDialog = false }) {
                    Text("Cancel", color = BuddyColors.TextMuted)
                }
            },
            containerColor = BuddyColors.SurfaceDark
        )
    }
}

@Composable
private fun DashboardMetric(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = BuddyColors.Cyan, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
        Text(label, style = MaterialTheme.typography.labelSmall, color = BuddyColors.TextMuted)
    }
}

@Composable
private fun TaskItemCard(
    task: Task,
    onComplete: () -> Unit,
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onComplete),
        backgroundColor = BuddyColors.SurfaceElevated.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (task.isCompleted) BuddyColors.ActiveGreen else BuddyColors.TextMuted
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (task.isCompleted) FontWeight.Normal else FontWeight.SemiBold
                    ),
                    color = if (task.isCompleted) BuddyColors.TextMuted else Color.White
                )
            }
        }
    }
}

private fun getDynamicGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..22 -> "Good evening"
        else -> "Good night"
    }
}

