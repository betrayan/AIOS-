package com.buddy.aios.core.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.buddy.aios.core.ui.theme.BuddyColors

/**
 * Adds press scale feedback to interactive elements.
 */
fun Modifier.clickableWithScale(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            scale.animateTo(pressedScale, spring(dampingRatio = 0.6f, stiffness = 400f))
        } else {
            scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 300f))
        }
    }

    this
        .scale(scale.value)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * Animated shimmer brush modifier for skeleton loading states.
 */
@Composable
fun Modifier.shimmerBackground(
    shimmerColors: List<Color> = listOf(
        BuddyColors.SurfaceDark,
        BuddyColors.GlassSurfaceHigh,
        BuddyColors.SurfaceDark
    )
): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 200f, translateAnim - 200f),
        end = Offset(translateAnim, translateAnim)
    )

    return this.background(brush)
}

/**
 * Standard animation specs for AIOS screen transitions.
 */
object AIOSAnimationSpecs {
    val FastOutSlowIn: AnimationSpec<Float> = tween(durationMillis = 300, easing = FastOutSlowInEasing)
    val PulseEase: AnimationSpec<Float> = infiniteRepeatable(
        animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
    )
}
