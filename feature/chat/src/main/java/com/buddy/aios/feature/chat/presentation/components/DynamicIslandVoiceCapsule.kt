package com.buddy.aios.feature.chat.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddy.aios.core.ui.components.GlassCard
import com.buddy.aios.core.ui.components.VoiceWaveform
import com.buddy.aios.core.ui.shapes.BuddyShapes
import com.buddy.aios.core.ui.theme.BuddyColors
import com.buddy.aios.feature.chat.voice.VoiceSessionState

/**
 * Dynamic Island Voice Capsule inspired floating UI control.
 * Smoothly morphs and animates state changes near the top of the Chat screen.
 */
@Composable
fun DynamicIslandVoiceCapsule(
    sessionState: VoiceSessionState,
    onCapsuleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isListening = sessionState is VoiceSessionState.Listening
    val isSpeaking = sessionState is VoiceSessionState.Speaking
    val isThinking = sessionState is VoiceSessionState.Thinking || sessionState is VoiceSessionState.Processing

    GlassCard(
        modifier = modifier
            .width(340.dp)
            .height(68.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
            .clickable { onCapsuleClick() },
        shape = BuddyShapes.Pill,
        backgroundColor = BuddyColors.SurfaceDark.copy(alpha = 0.95f),
        borderBrush = if (isListening) BuddyColors.GlassCardBorderGradient else BuddyColors.CardSurfaceGradient,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            // Status Dot / Mic Icon
            val dotColor = when {
                isListening -> BuddyColors.Rose
                isSpeaking -> BuddyColors.PurpleLight
                isThinking -> BuddyColors.Cyan
                sessionState is VoiceSessionState.Error -> BuddyColors.Rose
                else -> BuddyColors.Cyan
            }

            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )

            Spacer(Modifier.width(12.dp))

            // State Text & Waveform Morphing
            AnimatedContent(
                targetState = sessionState,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "DynamicIslandContent"
            ) { state ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (state) {
                        is VoiceSessionState.Listening -> {
                            Text("Listening...", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp), color = Color.White)
                            Spacer(Modifier.width(10.dp))
                            VoiceWaveform(isActive = true, activeColor = BuddyColors.Rose, barCount = 8)
                        }
                        is VoiceSessionState.Thinking, is VoiceSessionState.Processing -> {
                            Text("✦ Thinking...", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp), color = BuddyColors.Cyan)
                        }
                        is VoiceSessionState.Speaking -> {
                            Text("🔊 Speaking...", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp), color = Color.White)
                            Spacer(Modifier.width(10.dp))
                            VoiceWaveform(isActive = true, activeColor = BuddyColors.PurpleLight, barCount = 8)
                        }
                        is VoiceSessionState.WaitingForUser -> {
                            Text("Waiting...", style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), color = BuddyColors.TextSecondary)
                        }
                        is VoiceSessionState.Error -> {
                            Text("Error", style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), color = BuddyColors.Rose)
                        }
                        else -> {
                            Text("● AIOS Voice", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp), color = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("· Tap", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = BuddyColors.TextMuted)
                        }
                    }
                }
            }
        }
    }
}
