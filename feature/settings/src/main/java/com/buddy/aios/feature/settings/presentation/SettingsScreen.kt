package com.buddy.aios.feature.settings.presentation

import android.app.TimePickerDialog
import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.PrivacyLevel
import com.buddy.aios.core.ui.animation.clickableWithScale
import com.buddy.aios.core.ui.components.AIOSLoadingIndicator
import com.buddy.aios.core.ui.components.GlassCard
import java.util.Locale
import com.buddy.aios.core.ui.shapes.BuddyShapes
import com.buddy.aios.core.ui.theme.BuddyColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMemory: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showEditNameDialog by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf("") }
    var showInterventionDemo by remember { mutableStateOf(false) }

    var activityAwarenessEnabled by remember { mutableStateOf(true) }
    var targetSleepHours by remember { mutableStateOf(8) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BuddyColors.BackgroundDeep)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Control Center", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            Text("System preferences & AI behavior", style = MaterialTheme.typography.labelSmall, color = BuddyColors.TextSecondary)
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
            if (state.isLoading) {
                AIOSLoadingIndicator()
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
                ) {
                    // 1. Profile Section
                    item {
                        Text("USER PROFILE", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = BuddyColors.Cyan)
                        Spacer(Modifier.height(8.dp))
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = BuddyColors.PurpleLight, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(state.userProfile.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                        Text("Persona: ${state.userProfile.personaPreference}", style = MaterialTheme.typography.bodySmall, color = BuddyColors.TextSecondary)
                                    }
                                }
                                TextButton(
                                    onClick = {
                                        editedName = state.userProfile.name
                                        showEditNameDialog = true
                                    }
                                ) {
                                    Text("Edit Name", color = BuddyColors.Cyan, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // 1.5 AIOS Memories Section (Relocated from Home)
                    item {
                        Text("MEMORY & VAULT", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = BuddyColors.Cyan)
                        Spacer(Modifier.height(8.dp))
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickableWithScale(onClick = onNavigateToMemory),
                            backgroundColor = BuddyColors.SurfaceDark.copy(alpha = 0.85f),
                            borderBrush = BuddyColors.GlassCardBorderGradient,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🧠", style = MaterialTheme.typography.titleLarge)
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text("AIOS Memories", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                        Text("Manage what AIOS remembers about you", style = MaterialTheme.typography.bodySmall, color = BuddyColors.TextSecondary)
                                    }
                                }
                                Text("›", style = MaterialTheme.typography.titleLarge, color = BuddyColors.Cyan)
                            }
                        }
                    }

                    // 2. Buddy Operating Mode Selector
                    item {
                        Text("BUDDY OPERATING MODE", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = BuddyColors.Cyan)
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ModeCard(
                                title = "ACTIVE",
                                description = "Buddy can proactively help you, speak, and send reminders.",
                                isSelected = state.buddyMode == BuddyMode.ACTIVE,
                                activeColor = BuddyColors.ActiveGreen,
                                onClick = { viewModel.onSetBuddyMode(BuddyMode.ACTIVE) }
                            )
                            ModeCard(
                                title = "QUIET",
                                description = "Buddy speaks less and waits for explicit requests.",
                                isSelected = state.buddyMode == BuddyMode.QUIET,
                                activeColor = BuddyColors.QuietYellow,
                                onClick = { viewModel.onSetBuddyMode(BuddyMode.QUIET) }
                            )
                            ModeCard(
                                title = "SILENT",
                                description = "Voice output is disabled. Tasks, memory, and analytics continue.",
                                isSelected = state.buddyMode == BuddyMode.SILENT,
                                activeColor = BuddyColors.SilentBlue,
                                onClick = { viewModel.onSetBuddyMode(BuddyMode.SILENT) }
                            )
                            ModeCard(
                                title = "OFF",
                                description = "AIOS is completely inactive. No voice, reminders, or background activity.",
                                isSelected = state.buddyMode == BuddyMode.OFF,
                                activeColor = BuddyColors.OffGray,
                                onClick = { viewModel.onSetBuddyMode(BuddyMode.OFF) }
                            )
                        }
                    }

                    // 3. Privacy & AI Engine Switcher Card
                    item {
                        Text("PRIVACY & AI ENGINE", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = BuddyColors.Cyan)
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrivacyCard(
                                title = "LOCAL_ONLY (Default)",
                                statusBadge = "● LOCAL",
                                description = "100% On-Device. Zero bytes sent to cloud. Requires local model.",
                                isSelected = state.userProfile.privacyLevel == PrivacyLevel.LOCAL_ONLY,
                                onClick = { viewModel.onSetPrivacyLevel(PrivacyLevel.LOCAL_ONLY) }
                            )
                            PrivacyCard(
                                title = "CLOUD_OPT_IN (Gemini)",
                                statusBadge = "● CLOUD OPT-IN",
                                description = "Enables Cloud AI (Gemini 2.5 Flash) for complex reasoning.",
                                isSelected = state.userProfile.privacyLevel == PrivacyLevel.CLOUD_OPT_IN,
                                onClick = { viewModel.onSetPrivacyLevel(PrivacyLevel.CLOUD_OPT_IN) }
                            )
                        }
                    }

                    // 4. Activity Awareness & Sleep Target
                    item {
                        Text("ACTIVITY AWARENESS (LOCAL ONLY)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = BuddyColors.Cyan)
                        Spacer(Modifier.height(8.dp))
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.Nightlight, contentDescription = null, tint = BuddyColors.PurpleLight)
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text("Activity Awareness", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                            Text("Infers overnight inactivity window locally. Zero cloud data.", style = MaterialTheme.typography.bodySmall, color = BuddyColors.TextSecondary)
                                        }
                                    }
                                    Switch(
                                        checked = activityAwarenessEnabled,
                                        onCheckedChange = { activityAwarenessEnabled = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = BuddyColors.Cyan, checkedTrackColor = BuddyColors.PurpleGlow),
                                    )
                                }

                                if (activityAwarenessEnabled) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("Target Sleep Hours", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TextButton(onClick = { if (targetSleepHours > 5) targetSleepHours-- }) { Text("-", color = BuddyColors.Cyan) }
                                            Text("${targetSleepHours}h", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                            TextButton(onClick = { if (targetSleepHours < 12) targetSleepHours++ }) { Text("+", color = BuddyColors.Cyan) }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 5. Notifications & Sound
                    item {
                        Text("NOTIFICATIONS & SOUND", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = BuddyColors.Cyan)
                        Spacer(Modifier.height(8.dp))
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Notifications, contentDescription = null, tint = BuddyColors.ActiveGreen)
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text("AIOS Notifications", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                            Text("Morning summary and reminder notifications", style = MaterialTheme.typography.bodySmall, color = BuddyColors.TextSecondary)
                                        }
                                    }
                                    Text("AIOS Signature", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = BuddyColors.Cyan)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Test Reminder Notification", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                    TextButton(onClick = {
                                        viewModel.onScheduleTestReminder {
                                            Toast.makeText(context, "Test reminder scheduled in 60s! Close app to test.", Toast.LENGTH_LONG).show()
                                        }
                                    }) {
                                        Text("Test (60s)", color = BuddyColors.Cyan, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // 6. Morning Intelligence & Context
                    item {
                        Text("MORNING INTELLIGENCE", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = BuddyColors.Cyan)
                        Spacer(Modifier.height(8.dp))
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                val m = state.morningSettings

                                // ── Morning Wish ON/OFF + Time Picker ──────────────────────────
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column {
                                        Text("Morning Wish", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                        Text("Buddy wakes you with a voice greeting", style = MaterialTheme.typography.bodySmall, color = BuddyColors.TextSecondary)
                                    }
                                    Switch(
                                        checked = m.isMorningWishEnabled,
                                        onCheckedChange = { viewModel.onUpdateMorningSettings(m.copy(isMorningWishEnabled = it)) },
                                        colors = SwitchDefaults.colors(checkedThumbColor = BuddyColors.Cyan, checkedTrackColor = BuddyColors.PurpleGlow),
                                    )
                                }

                                if (m.isMorningWishEnabled) {
                                    // Time picker row — shows current scheduled time and opens system TimePickerDialog
                                    val morningWishTimeLabel = String.format(
                                        Locale.ENGLISH,
                                        "%02d:%02d %s",
                                        if (m.morningWishHour % 12 == 0) 12 else m.morningWishHour % 12,
                                        m.morningWishMinute,
                                        if (m.morningWishHour < 12) "AM" else "PM"
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column {
                                            Text("Wake-up Time", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                            Text(
                                                text = morningWishTimeLabel,
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                color = BuddyColors.Cyan
                                            )
                                        }
                                        TextButton(
                                            onClick = {
                                                // Use Android TimePickerDialog which is 12/24h aware and correct
                                                TimePickerDialog(
                                                    context,
                                                    { _, selectedHour, selectedMinute ->
                                                        // selectedHour is always 0..23 — no 12/24h conversion needed
                                                        viewModel.onUpdateMorningSettings(
                                                            m.copy(
                                                                morningWishHour = selectedHour,
                                                                morningWishMinute = selectedMinute
                                                            )
                                                        )
                                                        Toast.makeText(
                                                            context,
                                                            "Morning Wish set to ${String.format(Locale.ENGLISH, "%02d:%02d", selectedHour, selectedMinute)}",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    },
                                                    m.morningWishHour,   // initial hour (0..23)
                                                    m.morningWishMinute, // initial minute (0..59)
                                                    true                 // is24HourView — stored as 24h, displayed correctly
                                                ).show()
                                            }
                                        ) {
                                            Text("Change", color = BuddyColors.Cyan, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // ── Morning Briefing toggle ─────────────────────────────────
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column {
                                        Text("Morning Briefing", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                        Text("Contextual briefing at start of day", style = MaterialTheme.typography.bodySmall, color = BuddyColors.TextSecondary)
                                    }
                                    Switch(
                                        checked = m.isBriefingEnabled,
                                        onCheckedChange = { viewModel.onUpdateMorningSettings(m.copy(isBriefingEnabled = it)) },
                                        colors = SwitchDefaults.colors(checkedThumbColor = BuddyColors.Cyan, checkedTrackColor = BuddyColors.PurpleGlow),
                                    )
                                }

                                if (m.isBriefingEnabled) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("Voice Summary", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                        Switch(
                                            checked = m.isVoiceEnabled,
                                            onCheckedChange = { viewModel.onUpdateMorningSettings(m.copy(isVoiceEnabled = it)) },
                                            colors = SwitchDefaults.colors(checkedThumbColor = BuddyColors.Cyan, checkedTrackColor = BuddyColors.PurpleGlow),
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("Weather Relevance", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                        Switch(
                                            checked = m.includeWeather,
                                            onCheckedChange = { viewModel.onUpdateMorningSettings(m.copy(includeWeather = it)) },
                                            colors = SwitchDefaults.colors(checkedThumbColor = BuddyColors.Cyan, checkedTrackColor = BuddyColors.PurpleGlow),
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("Sleep Summary", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                        Switch(
                                            checked = m.includeSleep,
                                            onCheckedChange = { viewModel.onUpdateMorningSettings(m.copy(includeSleep = it)) },
                                            colors = SwitchDefaults.colors(checkedThumbColor = BuddyColors.Cyan, checkedTrackColor = BuddyColors.PurpleGlow),
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("Contextual Battery Alerts", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                        Switch(
                                            checked = m.contextualBatteryAlerts,
                                            onCheckedChange = { viewModel.onUpdateMorningSettings(m.copy(contextualBatteryAlerts = it)) },
                                            colors = SwitchDefaults.colors(checkedThumbColor = BuddyColors.Cyan, checkedTrackColor = BuddyColors.PurpleGlow),
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("Travel Context Alerts", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                        Switch(
                                            checked = m.contextualTravelAlerts,
                                            onCheckedChange = { viewModel.onUpdateMorningSettings(m.copy(contextualTravelAlerts = it)) },
                                            colors = SwitchDefaults.colors(checkedThumbColor = BuddyColors.Cyan, checkedTrackColor = BuddyColors.PurpleGlow),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 6. System Info
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = BuddyColors.PurpleLight)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("AIOS Companion Edition", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                    Text("Version 8.0.0 (Stage 8 Brain) • Clean Architecture", style = MaterialTheme.typography.labelSmall, color = BuddyColors.TextMuted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Name Dialog
    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Edit Preferred Name", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BuddyColors.Cyan,
                        unfocusedBorderColor = BuddyColors.GlassBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onUpdateUserName(editedName.trim())
                        showEditNameDialog = false
                    }
                ) {
                    Text("Save", color = BuddyColors.Cyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel", color = BuddyColors.TextMuted)
                }
            },
            containerColor = BuddyColors.SurfaceDark
        )
    }
}

@Composable
private fun ModeCard(
    title: String,
    description: String,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickableWithScale(onClick = onClick),
        backgroundColor = if (isSelected) activeColor.copy(alpha = 0.20f) else BuddyColors.SurfaceDark.copy(alpha = 0.70f),
        borderWidth = if (isSelected) 2.dp else 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(if (isSelected) activeColor else BuddyColors.TextMuted, shape = BuddyShapes.Pill)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                Spacer(Modifier.height(2.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = BuddyColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun PrivacyCard(
    title: String,
    statusBadge: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickableWithScale(onClick = onClick),
        backgroundColor = if (isSelected) BuddyColors.PurpleGlow.copy(alpha = 0.25f) else BuddyColors.SurfaceDark.copy(alpha = 0.70f),
        borderWidth = if (isSelected) 2.dp else 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = if (isSelected) BuddyColors.Cyan else BuddyColors.TextMuted,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    Text(
                        text = statusBadge,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isSelected) BuddyColors.Cyan else BuddyColors.TextMuted,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = BuddyColors.TextSecondary)
            }
        }
    }
}
