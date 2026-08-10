package com.buddy.aios.feature.chat.presentation

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.Message
import com.buddy.aios.core.domain.entity.MessageRole
import com.buddy.aios.core.ui.animation.clickableWithScale
import com.buddy.aios.core.ui.components.AIOSButton
import com.buddy.aios.core.ui.components.AIOSChip
import com.buddy.aios.core.ui.components.BuddyOrb
import com.buddy.aios.core.ui.components.GlassCard
import com.buddy.aios.core.ui.components.OrbState
import com.buddy.aios.core.ui.components.VoiceWaveform
import com.buddy.aios.core.ui.shapes.BuddyShapes
import com.buddy.aios.core.ui.theme.BuddyColors
import com.buddy.aios.feature.chat.voice.VoiceState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()
    val capabilities by viewModel.currentCapabilities.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Audio record permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleVoiceInput()
        } else {
            Toast.makeText(context, "Microphone permission required for voice input", Toast.LENGTH_SHORT).show()
        }
    }

    val messages = (uiState as? ChatUiState.Active)?.messages ?: emptyList()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BuddyColors.BackgroundRadialGradient)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BuddyOrb(buddyMode = BuddyMode.ACTIVE, size = 32.dp, orbState = if (isStreaming || uiState is ChatUiState.Thinking) OrbState.THINKING else OrbState.IDLE)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = (uiState as? ChatUiState.Active)?.conversationTitle ?: "AIOS Companion",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (isStreaming || uiState is ChatUiState.Thinking) BuddyColors.Cyan else BuddyColors.ActiveGreen)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (isStreaming || uiState is ChatUiState.Thinking) "Thinking..." else "Active",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = BuddyColors.TextSecondary
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Chat", tint = BuddyColors.TextSecondary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BuddyColors.SurfaceDark.copy(alpha = 0.85f)),
                )
            },
            containerColor = Color.Transparent,
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (uiState is ChatUiState.Error) {
                    val errState = uiState as ChatUiState.Error
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .align(Alignment.Center),
                        backgroundColor = BuddyColors.SurfaceDark.copy(alpha = 0.95f),
                        borderWidth = 1.5.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = errState.message,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = BuddyColors.Rose
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = errState.secondaryMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = BuddyColors.TextSecondary
                            )
                            Spacer(Modifier.height(20.dp))
                            AIOSButton(
                                text = "Retry",
                                onClick = viewModel::onRetry,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else if (messages.isEmpty() && uiState !is ChatUiState.Thinking) {
                    // Ambient Empty State
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        BuddyOrb(buddyMode = BuddyMode.ACTIVE, size = 120.dp)
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = "Hey, I'm Buddy.",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Your personal AI companion.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BuddyColors.TextSecondary
                        )
                        Spacer(Modifier.height(32.dp))

                        // Suggested Prompts
                        val promptSuggestions = listOf("Plan my day", "Help me study", "Create a task", "Let's talk")
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            promptSuggestions.take(2).forEach { prompt ->
                                AIOSChip(
                                    text = prompt,
                                    isSelected = false,
                                    onClick = {
                                        viewModel.onInputChanged(prompt)
                                        viewModel.onSendMessage()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            promptSuggestions.takeLast(2).forEach { prompt ->
                                AIOSChip(
                                    text = prompt,
                                    isSelected = false,
                                    onClick = {
                                        viewModel.onInputChanged(prompt)
                                        viewModel.onSendMessage()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            ChatMessageBubble(message = message, context = context)
                        }

                        if (uiState is ChatUiState.Thinking || isStreaming) {
                            item {
                                ThinkingAnimationBubble()
                            }
                        }
                    }
                }

                // Floating Input Area
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    if (capabilities.allowTextInteraction) {
                        GlassCard(
                            shape = BuddyShapes.ExtraLarge,
                            backgroundColor = BuddyColors.SurfaceDark.copy(alpha = 0.95f),
                            borderWidth = 1.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                            viewModel.toggleVoiceInput()
                                        } else {
                                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (voiceState is VoiceState.Listening) Icons.Default.Stop else Icons.Default.Mic,
                                        contentDescription = "Voice Input",
                                        tint = if (voiceState is VoiceState.Listening) BuddyColors.Rose else BuddyColors.Cyan
                                    )
                                }

                                OutlinedTextField(
                                    value = inputText,
                                    onValueChange = viewModel::onInputChanged,
                                    placeholder = { Text("Ask Buddy anything...", color = BuddyColors.TextMuted) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = false,
                                    maxLines = 4,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                    )
                                )

                                IconButton(
                                    onClick = { viewModel.onSendMessage() },
                                    enabled = inputText.isNotBlank() && !isStreaming && uiState !is ChatUiState.Thinking,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(
                                            if (inputText.isNotBlank() && !isStreaming && uiState !is ChatUiState.Thinking) BuddyColors.PurpleGlow
                                            else BuddyColors.SurfaceElevated
                                        )
                                        .clickableWithScale(
                                            enabled = inputText.isNotBlank() && !isStreaming && uiState !is ChatUiState.Thinking,
                                            onClick = { viewModel.onSendMessage() }
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
                                        tint = if (inputText.isNotBlank() && !isStreaming) Color.White else BuddyColors.TextMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Voice Listening Waveform Modal Overlay
                AnimatedVisibility(
                    visible = voiceState is VoiceState.Listening,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        backgroundColor = BuddyColors.SurfaceElevated.copy(alpha = 0.98f)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            BuddyOrb(buddyMode = BuddyMode.ACTIVE, size = 80.dp, orbState = OrbState.LISTENING)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Listening to you...",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Spacer(Modifier.height(16.dp))
                            VoiceWaveform(isActive = true)
                            Spacer(Modifier.height(16.dp))
                            TextButton(onClick = { viewModel.toggleVoiceInput() }) {
                                Text("Stop Listening", color = BuddyColors.Rose, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Clear Conversation?", color = Color.White) },
            text = { Text("This will remove all messages in this conversation from your device.", color = BuddyColors.TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onClearConversation(onCleared = onNavigateBack)
                        showDeleteConfirm = false
                    }
                ) {
                    Text("Clear", color = BuddyColors.Rose, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = BuddyColors.TextMuted)
                }
            },
            containerColor = BuddyColors.SurfaceDark
        )
    }
}

@Composable
private fun ThinkingAnimationBubble() {
    val transition = rememberInfiniteTransition(label = "thinkingAnim")
    val dot1Alpha by transition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "d1"
    )
    val dot2Alpha by transition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "d2"
    )
    val dot3Alpha by transition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "d3"
    )

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        BuddyOrb(buddyMode = BuddyMode.ACTIVE, size = 28.dp, orbState = OrbState.THINKING)
        Spacer(Modifier.width(10.dp))
        GlassCard(
            shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
            backgroundColor = BuddyColors.SurfaceElevated.copy(alpha = 0.75f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Buddy is thinking ", style = MaterialTheme.typography.bodyMedium, color = BuddyColors.TextSecondary)
                Box(Modifier.size(6.dp).clip(CircleShape).background(BuddyColors.Cyan.copy(alpha = dot1Alpha)))
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(6.dp).clip(CircleShape).background(BuddyColors.Cyan.copy(alpha = dot2Alpha)))
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(6.dp).clip(CircleShape).background(BuddyColors.Cyan.copy(alpha = dot3Alpha)))
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(message: Message, context: Context) {
    val isUser = message.role == MessageRole.USER
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart

    val timeStr = remember(message.timestamp) {
        SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(message.timestamp))
    }

    val isCodeBlock = message.content.startsWith("```") && message.content.endsWith("```")
    val cleanedCode = if (isCodeBlock) {
        message.content.removeSurrounding("```").trim()
    } else message.content

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            if (!isUser) {
                BuddyOrb(buddyMode = BuddyMode.ACTIVE, size = 28.dp)
                Spacer(Modifier.width(8.dp))
            }

            GlassCard(
                shape = if (isUser) RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
                backgroundColor = if (isUser) BuddyColors.PurpleGlow.copy(alpha = 0.85f) else BuddyColors.SurfaceElevated.copy(alpha = 0.85f),
                borderWidth = 1.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (isCodeBlock) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Code Snippet", style = MaterialTheme.typography.labelSmall, color = BuddyColors.Cyan)
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("code", cleanedCode))
                                    Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.White.copy(alpha = 0.7f))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = cleanedCode,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = BuddyColors.TextPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(BuddyShapes.Small)
                                .background(Color.Black.copy(alpha = 0.4f))
                                .padding(10.dp)
                        )
                    } else {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}
