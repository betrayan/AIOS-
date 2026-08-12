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
import com.buddy.aios.core.ui.animation.LocalReducedMotion
import com.buddy.aios.core.ui.theme.BuddyColors

/**
 * 8 Visual States of the AIOS Orb.
 */
enum class OrbState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    WORKING,
    SUCCESS,
    ERROR,
    OFF,
}

/**
 * Rebuilt high-quality animated AIOS Orb component.
 *
 * Visual design principles:
 * - IDLE     : Slow organic breathing pulse
 * - LISTENING: Reactive audio ripple and dynamic amplitude pulse
 * - THINKING : Smooth organic dual-ring rotation (no generic spinners)
 * - SPEAKING : Dynamic wave pulse driven by TTS output
 * - WORKING  : Subtle active processing pulse
 * - SUCCESS  : Positive green-cyan aura burst
 * - ERROR    : Controlled red pulse
 * - OFF      : Static dim, zero animation resources consumed
 */
@Composable
fun BuddyOrb(
    buddyMode: BuddyMode,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    orbState: OrbState = OrbState.IDLE,
    audioAmplitude: Float = 0.5f,
) {
    val isReducedMotion = LocalReducedMotion.current

    // OFF state: render static dim orb, avoiding animation memory/power overhead
    if (orbState == OrbState.OFF || buddyMode == BuddyMode.OFF) {
        Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(size)) {
                val radius = this.size.minDimension / 2f * 0.75f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(BuddyColors.OffGray.copy(alpha = 0.4f), Color(0xFF090912)),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }
        }
        return
    }

    val transition = rememberInfiniteTransition(label = "orbTransition")

    // 1. Organic Rotation (Thinking / Working)
    val rotationAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (orbState) {
                    OrbState.THINKING -> 2000
                    OrbState.WORKING  -> 2800
                    OrbState.LISTENING -> 4500
                    else -> 9000
                },
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle",
    )

    // 2. Pulse Scale
    val pulseScale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = when (orbState) {
            OrbState.THINKING  -> 1.15f
            OrbState.SPEAKING  -> 1.18f + (audioAmplitude * 0.12f)
            OrbState.LISTENING -> 1.12f + (audioAmplitude * 0.15f)
            OrbState.WORKING   -> 1.10f
            OrbState.SUCCESS   -> 1.20f
            OrbState.ERROR     -> 1.22f
            OrbState.IDLE      -> 1.05f
            else               -> 1.05f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when {
                    isReducedMotion -> 3000
                    orbState == OrbState.THINKING  -> 600
                    orbState == OrbState.SPEAKING  -> 450
                    orbState == OrbState.LISTENING -> 500
                    orbState == OrbState.WORKING   -> 800
                    orbState == OrbState.ERROR     -> 350
                    else -> 2200
                },
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    // 3. Ripple expansion for LISTENING state
    val rippleScale by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rippleScale",
    )

    // Color definitions based on State & BuddyMode
    val (coreColors, auraColor, ringColor) = when {
        orbState == OrbState.ERROR -> Triple(
            listOf(BuddyColors.Rose, Color(0xFFFF6B6B), Color(0xFF3F0B18)),
            BuddyColors.Rose.copy(alpha = 0.45f),
            BuddyColors.Rose.copy(alpha = 0.70f)
        )
        orbState == OrbState.SUCCESS -> Triple(
            listOf(BuddyColors.ActiveGreen, BuddyColors.Cyan, Color(0xFF063523)),
            BuddyColors.ActiveGreen.copy(alpha = 0.40f),
            BuddyColors.ActiveGreen.copy(alpha = 0.65f)
        )
        orbState == OrbState.LISTENING -> Triple(
            listOf(BuddyColors.Rose, BuddyColors.PurpleGlow, BuddyColors.SurfaceDark),
            BuddyColors.Rose.copy(alpha = 0.50f),
            BuddyColors.Rose.copy(alpha = 0.80f)
        )
        orbState == OrbState.SPEAKING -> Triple(
            listOf(BuddyColors.PurpleLight, BuddyColors.Cyan, BuddyColors.PurpleDark),
            BuddyColors.PurpleGlow.copy(alpha = 0.45f),
            BuddyColors.PurpleLight.copy(alpha = 0.75f)
        )
        buddyMode == BuddyMode.ACTIVE -> Triple(
            listOf(BuddyColors.Cyan, BuddyColors.PurpleGlow, BuddyColors.SurfaceDark),
            BuddyColors.Cyan.copy(alpha = 0.35f),
            BuddyColors.Cyan.copy(alpha = 0.60f)
        )
        buddyMode == BuddyMode.QUIET -> Triple(
            listOf(BuddyColors.Teal, BuddyColors.PurpleDark, BuddyColors.SurfaceElevated),
            BuddyColors.Teal.copy(alpha = 0.25f),
            BuddyColors.Teal.copy(alpha = 0.45f)
        )
        buddyMode == BuddyMode.SILENT -> Triple(
            listOf(BuddyColors.SilentBlue, BuddyColors.SurfaceElevated),
            BuddyColors.SilentBlue.copy(alpha = 0.20f),
            BuddyColors.SilentBlue.copy(alpha = 0.40f)
        )
        else -> Triple(
            listOf(BuddyColors.OffGray, Color(0xFF1E293B)),
            Color.Transparent,
            Color.Transparent
        )
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val centerRadius = (this.size.minDimension / 2f) * pulseScale

            // 1. Listening Ripple Effect
            if (orbState == OrbState.LISTENING && !isReducedMotion) {
                drawCircle(
                    color = ringColor.copy(alpha = (1.45f - rippleScale).coerceIn(0f, 0.4f)),
                    radius = centerRadius * rippleScale,
                    center = center,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }

            // 2. Outer Glow Aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(auraColor, Color.Transparent),
                    center = center,
                    radius = centerRadius * 1.5f,
                ),
                radius = centerRadius * 1.35f,
                center = center,
            )

            // 3. Core Gradient Blob
            drawCircle(
                brush = Brush.radialGradient(
                    colors = coreColors,
                    center = center,
                    radius = centerRadius,
                ),
                radius = centerRadius * 0.85f,
                center = center,
            )

            // 4. Dual Rotating Ring Overlay (THINKING / WORKING / LISTENING)
            if (orbState == OrbState.THINKING || orbState == OrbState.WORKING || orbState == OrbState.LISTENING) {
                drawCircle(
                    color = ringColor,
                    radius = centerRadius * 1.05f,
                    center = center,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }

            // 5. Success / Error Outline Ring
            if (orbState == OrbState.SUCCESS || orbState == OrbState.ERROR) {
                drawCircle(
                    color = ringColor,
                    radius = centerRadius * 1.08f,
                    center = center,
                    style = Stroke(width = 2.5.dp.toPx()),
                )
            }
        }
    }
}
