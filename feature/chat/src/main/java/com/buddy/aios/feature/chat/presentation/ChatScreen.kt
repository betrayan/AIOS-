package com.buddy.aios.feature.chat.presentation

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buddy.aios.core.domain.agent.AgentStatus
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.Message
import com.buddy.aios.core.domain.entity.MessageRole
import com.buddy.aios.core.ui.animation.AIOSMotion
import com.buddy.aios.core.ui.animation.clickableWithScale
import com.buddy.aios.core.ui.components.BuddyOrb
import com.buddy.aios.core.ui.components.GlassCard
import com.buddy.aios.core.ui.components.OrbState
import com.buddy.aios.core.ui.components.VoiceWaveform
import com.buddy.aios.core.ui.shapes.BuddyShapes
import com.buddy.aios.core.ui.theme.BuddyColors
import com.buddy.aios.feature.chat.voice.TextToSpeechState
import com.buddy.aios.feature.chat.voice.VoiceInputState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceInputState.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    val agentStatus by viewModel.agentStatus.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }

    val isListening = voiceState is VoiceInputState.Listening || voiceState is VoiceInputState.PartialResult
    val isSpeaking = ttsState is TextToSpeechState.Speaking

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleVoiceInput()
        } else {
            Toast.makeText(context, "Microphone permission required for voice interaction", Toast.LENGTH_SHORT).show()
        }
    }

    val listState = rememberLazyListState()

    // Auto-scroll on new message
    val activeState = uiState as? ChatUiState.Active
    val messageCount = activeState?.messages?.size ?: 0
    LaunchedEffect(messageCount, activeState?.streamingPartialText) {
        if (messageCount > 0) {
            listState.animateScrollToItem(messageCount - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val orbStatus = when {
                            agentStatus == AgentStatus.FAILED -> OrbState.ERROR
                            isListening -> OrbState.LISTENING
                            isSpeaking -> OrbState.SPEAKING
                            isStreaming || uiState is ChatUiState.Thinking || agentStatus != AgentStatus.IDLE -> OrbState.THINKING
                            else -> OrbState.IDLE
                        }
                        BuddyOrb(buddyMode = BuddyMode.ACTIVE, size = 32.dp, orbState = orbStatus)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "AIOS Companion",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val statusDotColor = when {
                                    agentStatus == AgentStatus.FAILED -> BuddyColors.Rose
                                    isListening -> BuddyColors.Rose
                                    isSpeaking -> BuddyColors.PurpleLight
                                    isStreaming || agentStatus != AgentStatus.IDLE -> BuddyColors.Cyan
                                    else -> BuddyColors.ActiveGreen
                                }
                                val statusText = when {
                                    isListening -> "Listening..."
                                    isSpeaking -> "Speaking..."
                                    agentStatus == AgentStatus.UNDERSTANDING -> "Understanding..."
                                    agentStatus == AgentStatus.PLANNING -> "Planning..."
                                    agentStatus == AgentStatus.EXECUTING -> "Working..."
                                    agentStatus == AgentStatus.VERIFYING -> "Checking..."
                                    agentStatus == AgentStatus.WAITING_CONFIRMATION -> "Confirmation needed"
                                    isStreaming -> "Thinking..."
                                    else -> "Active"
                                }
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(statusDotColor))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BuddyColors.TextMuted
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
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Chat", tint = BuddyColors.TextMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BuddyColors.BackgroundDeep),
            )
        },
        containerColor = BuddyColors.BackgroundDeep,
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Messages List ──────────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                when (val state = uiState) {
                    is ChatUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            BuddyOrb(buddyMode = BuddyMode.ACTIVE, size = 80.dp, orbState = OrbState.THINKING)
                        }
                    }

                    is ChatUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            GlassCard(modifier = Modifier.fillMaxWidth(), backgroundColor = BuddyColors.SurfaceDark) {
                                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(state.message, style = MaterialTheme.typography.titleMedium, color = BuddyColors.Rose)
                                    Spacer(Modifier.height(8.dp))
                                    Text(state.secondaryMessage, style = MaterialTheme.typography.bodySmall, color = BuddyColors.TextMuted)
                                    Spacer(Modifier.height(16.dp))
                                    TextButton(onClick = { viewModel.onRetry() }) {
                                        Text("Retry", color = BuddyColors.Cyan, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    is ChatUiState.Active -> {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(state.messages, key = { it.id }) { message ->
                                ChatMessageBubble(message = message, onCopy = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("AIOS Message", message.content))
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                })
                            }

                            // Streaming Chunk Preview Bubble
                            state.streamingPartialText?.let { streaming ->
                                if (streaming.isNotBlank()) {
                                    item {
                                        ChatMessageBubble(
                                            message = Message(
                                                id = "streaming",
                                                conversationId = conversationId,
                                                role = MessageRole.ASSISTANT,
                                                content = streaming,
                                                timestamp = System.currentTimeMillis(),
                                            ),
                                            onCopy = {},
                                        )
                                    }
                                }
                            }
                        }
                    }

                    ChatUiState.Thinking -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            BuddyOrb(buddyMode = BuddyMode.ACTIVE, size = 70.dp, orbState = OrbState.THINKING)
                        }
                    }
                }
            }

            // ── Animated Voice Waveform Overlay (when listening/speaking) ──────
            AnimatedVisibility(
                visible = isListening || isSpeaking,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BuddyColors.SurfaceDark)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    VoiceWaveform(
                        isActive = isListening || isSpeaking,
                        activeColor = if (isListening) BuddyColors.Rose else BuddyColors.PurpleLight,
                        barCount = 18,
                    )
                }
            }

            // ── Bottom Input Bar ───────────────────────────────────────────────
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = BuddyShapes.Pill,
                backgroundColor = BuddyColors.SurfaceDark.copy(alpha = 0.95f),
                borderBrush = BuddyColors.GlassCardBorderGradient,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Voice Mic Toggle Button
                    IconButton(onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            viewModel.toggleVoiceInput()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Voice input",
                            tint = if (isListening) BuddyColors.Rose else BuddyColors.Cyan,
                        )
                    }

                    // Text Field Input
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { viewModel.onInputChanged(it) },
                        placeholder = { Text("Ask AIOS...", color = BuddyColors.TextMuted) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                        ),
                        singleLine = true,
                    )

                    // Send Button
                    IconButton(
                        onClick = { viewModel.onSendMessage() },
                        enabled = inputText.isNotBlank() && !isStreaming,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank()) BuddyColors.Cyan else BuddyColors.TextMuted,
                        )
                    }
                }
            }
        }

        // Clear Chat Confirmation Dialog
        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("Clear Conversation", color = Color.White) },
                text = { Text("Are you sure you want to delete this conversation history?", color = BuddyColors.TextSecondary) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.onClearConversation { onNavigateBack() }
                        showClearDialog = false
                    }) {
                        Text("Clear", color = BuddyColors.Rose, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("Cancel", color = BuddyColors.TextMuted)
                    }
                },
                containerColor = BuddyColors.SurfaceDark,
            )
        }
    }
}

// ── Message Bubble ────────────────────────────────────────────────────────────

@Composable
private fun ChatMessageBubble(
    message: Message,
    onCopy: () -> Unit,
) {
    val isUser = message.role == MessageRole.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        GlassCard(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp,
            ),
            backgroundColor = if (isUser) BuddyColors.PurpleGlow.copy(alpha = 0.30f) else BuddyColors.SurfaceDark.copy(alpha = 0.90f),
            borderBrush = if (isUser) BuddyColors.GlassCardBorderGradient else BuddyColors.CardSurfaceGradient,
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )

                // Tool confirmation metadata indicator if present
                message.metadata["tool_label"]?.let { label ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "✓ $label",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = BuddyColors.Cyan,
                    )
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val timeStr = SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(message.timestamp))
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = BuddyColors.TextMuted,
                    )
                    if (!isUser) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy message",
                            tint = BuddyColors.TextMuted,
                            modifier = Modifier
                                .size(12.dp)
                                .clickable { onCopy() },
                        )
                    }
                }
            }
        }
    }
}
