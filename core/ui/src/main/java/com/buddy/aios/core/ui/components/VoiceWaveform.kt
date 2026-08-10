package com.buddy.aios.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.buddy.aios.core.ui.theme.BuddyColors

/**
 * Animated audio waveform visualizer for Voice UI (listening & speaking states).
 */
@Composable
fun VoiceWaveform(
    modifier: Modifier = Modifier,
    activeColor: Color = BuddyColors.Cyan,
    barCount: Int = 7,
    maxBarHeight: Dp = 48.dp,
    isActive: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "waveformAnim")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(maxBarHeight),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(barCount) { index ->
            val animDuration = 400 + (index * 150) % 500
            val heightMultiplier by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = if (isActive) (0.6f + (index % 3) * 0.2f) else 0.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = animDuration, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "barHeight_$index"
            )

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(heightMultiplier)
                    .clip(CircleShape)
                    .background(activeColor)
            )
        }
    }
}
