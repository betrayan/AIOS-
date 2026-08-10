package com.buddy.aios.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.ui.theme.BuddyColors

enum class OrbState {
    IDLE,
    THINKING,
    SPEAKING,
    LISTENING
}

/**
 * Animated futuristic AI Orb composable reflecting [BuddyMode] and [OrbState].
 */
@Composable
fun BuddyOrb(
    buddyMode: BuddyMode,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    orbState: OrbState = OrbState.IDLE,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbTransition")

    // Rotation animation
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (orbState == OrbState.THINKING) 2000 else 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )

    // Pulse animation
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = when (orbState) {
            OrbState.THINKING -> 1.15f
            OrbState.SPEAKING -> 1.20f
            OrbState.LISTENING -> 1.10f
            OrbState.IDLE -> 1.05f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (orbState) {
                    OrbState.THINKING -> 600
                    OrbState.SPEAKING -> 800
                    OrbState.LISTENING -> 1000
                    OrbState.IDLE -> 2500
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val (coreColors, auraColor) = when (buddyMode) {
        BuddyMode.ACTIVE -> listOf(BuddyColors.Cyan, BuddyColors.PurpleGlow, BuddyColors.PurpleDark) to BuddyColors.Cyan.copy(alpha = 0.35f)
        BuddyMode.QUIET -> listOf(BuddyColors.Teal, BuddyColors.PurpleDark, BuddyColors.SurfaceElevated) to BuddyColors.Teal.copy(alpha = 0.25f)
        BuddyMode.SILENT -> listOf(BuddyColors.SilentBlue, BuddyColors.SurfaceElevated) to BuddyColors.SilentBlue.copy(alpha = 0.20f)
        BuddyMode.OFF -> listOf(BuddyColors.OffGray, Color(0xFF1E293B)) to Color.Transparent
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val centerRadius = (this.size.minDimension / 2f) * if (buddyMode != BuddyMode.OFF) pulseScale else 0.85f

            // Outer Aura Glow
            if (buddyMode != BuddyMode.OFF) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(auraColor, Color.Transparent),
                        center = center,
                        radius = centerRadius * 1.5f
                    ),
                    radius = centerRadius * 1.4f,
                    center = center
                )
            }

            // Core Gradient Blob
            drawCircle(
                brush = Brush.radialGradient(
                    colors = coreColors,
                    center = center,
                    radius = centerRadius
                ),
                radius = centerRadius * 0.85f,
                center = center
            )

            // Orbital Ring for ACTIVE / LISTENING states
            if (buddyMode == BuddyMode.ACTIVE || orbState == OrbState.LISTENING) {
                drawCircle(
                    color = BuddyColors.Cyan.copy(alpha = 0.6f),
                    radius = centerRadius * 1.05f,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}
