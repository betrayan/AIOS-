package com.buddy.aios.core.ui.island

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.buddy.aios.core.ui.theme.BuddyColors
import kotlinx.coroutines.delay

/**
 * AIOS Dynamic Island — Compact, animated status pill.
 *
 * Behavior:
 * - Hidden by default. Pushes down ONLY when AIOS is active (Listening, Speaking, Thinking, Reminders, etc.).
 * - Auto-retracts when idle.
 * - Tap expands briefly to reveal details/actions.
 */
@Composable
fun AIOSIsland(
    displayState: AIOSIslandDisplayState,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Auto collapse expansion after 3s
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            delay(3000L)
            isExpanded = false
        }
    }

    if (!displayState.isVisible && isExpanded) {
        isExpanded = false
    }

    val shouldBeVisible = displayState.isVisible && displayState.state != AIOSIslandState.IDLE

    AnimatedVisibility(
        visible = shouldBeVisible,
        enter = slideInVertically(
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            initialOffsetY = { -it }
        ) + fadeIn(animationSpec = tween(180)),
        exit = slideOutVertically(
            animationSpec = tween(220),
            targetOffsetY = { -it }
        ) + fadeOut(animationSpec = tween(180)),
        modifier = modifier
            .zIndex(100f)
            .fillMaxWidth()
            .padding(top = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxWidth()
        ) {
            val (stateColor, _) = displayState.state.toVisuals()
            val borderColor by animateColorAsState(
                targetValue = stateColor.copy(alpha = 0.35f),
                animationSpec = tween(300),
                label = "borderColor"
            )

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF131422).copy(alpha = 0.96f),
                                Color(0xFF090A12).copy(alpha = 0.98f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            colors = listOf(borderColor, borderColor.copy(alpha = 0.15f))
                        ),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .clickable { isExpanded = !isExpanded }
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        )
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Main Compact Pill Row ──────────────────────────────────
                IslandCompactRow(
                    state = displayState.state,
                    message = displayState.message
                )

                // ── Brief Tap Expansion Details ─────────────────────────────
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(2.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = displayState.message,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        if (displayState.actionLabel != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = displayState.actionLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = BuddyColors.Cyan,
                                modifier = Modifier.clickable {
                                    displayState.onAction?.invoke()
                                    isExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Compact Pill Row ─────────────────────────────────────────────────────────

@Composable
private fun IslandCompactRow(
    state: AIOSIslandState,
    message: String,
) {
    val (stateColor, stateIcon) = state.toVisuals()

    Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Dynamic Live Visualizer depending on State
        StateVisualizer(state = state, color = stateColor, icon = stateIcon)

        // Status Text
        Text(
            text = message,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = 0.2.sp
            ),
            color = Color.White,
        )
    }
}

// ── State Interactive Animations ─────────────────────────────────────────────

@Composable
private fun StateVisualizer(
    state: AIOSIslandState,
    color: Color,
    icon: ImageVector?
) {
    when (state) {
        AIOSIslandState.SPEAKING -> SoundBarsVisualizer(color = color)
        AIOSIslandState.LISTENING -> ListeningRippleVisualizer(color = color)
        AIOSIslandState.THINKING -> ThinkingDotsVisualizer(color = color)
        AIOSIslandState.TOOL_EXECUTION -> ToolExecutionArcVisualizer(color = color)
        AIOSIslandState.CONTINUOUS -> PulseDotVisualizer(color = color)
        else -> IconVisualizer(color = color, icon = icon ?: Icons.Default.Check)
    }
}

/** 5 Animated Equalizer Bars for SPEAKING */
@Composable
private fun SoundBarsVisualizer(color: Color) {
    val transition = rememberInfiniteTransition(label = "soundBars")
    
    val height1 by transition.animateFloat(0.3f, 1.0f, infiniteRepeatable(tween(350, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "h1")
    val height2 by transition.animateFloat(0.6f, 0.2f, infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "h2")
    val height3 by transition.animateFloat(0.2f, 0.9f, infiniteRepeatable(tween(300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "h3")
    val height4 by transition.animateFloat(0.8f, 0.4f, infiniteRepeatable(tween(480, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "h4")
    val height5 by transition.animateFloat(0.4f, 1.0f, infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "h5")

    val heights = listOf(height1, height2, height3, height4, height5)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        modifier = Modifier.height(16.dp)
    ) {
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((16 * h).dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/** Ripple pulse for LISTENING */
@Composable
private fun ListeningRippleVisualizer(color: Color) {
    val transition = rememberInfiniteTransition(label = "ripple")
    val scale by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(18.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.25f))
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

/** 3 Bouncing Dots for THINKING */
@Composable
private fun ThinkingDotsVisualizer(color: Color) {
    val transition = rememberInfiniteTransition(label = "dots")
    val scale1 by transition.animateFloat(0.4f, 1.0f, infiniteRepeatable(tween(400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "d1")
    val scale2 by transition.animateFloat(0.4f, 1.0f, infiniteRepeatable(tween(400, delayMillis = 130, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "d2")
    val scale3 by transition.animateFloat(0.4f, 1.0f, infiniteRepeatable(tween(400, delayMillis = 260, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "d3")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        listOf(scale1, scale2, scale3).forEach { s ->
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .scale(s)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/** Arc Spinner for TOOL_EXECUTION */
@Composable
private fun ToolExecutionArcVisualizer(color: Color) {
    val transition = rememberInfiniteTransition(label = "arc")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "rot"
    )

    Canvas(modifier = Modifier.size(16.dp)) {
        drawArc(
            color = color,
            startAngle = rotation,
            sweepAngle = 240f,
            useCenter = false,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/** Breathing dot for CONTINUOUS */
@Composable
private fun PulseDotVisualizer(color: Color) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

/** Standard Icon Visualizer with subtle pop scale */
@Composable
private fun IconVisualizer(color: Color, icon: ImageVector) {
    val scale by animateFloatAsState(
        targetValue = 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "iconPop"
    )

    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = color,
        modifier = Modifier
            .size(16.dp)
            .scale(scale)
    )
}

// ── State Visual Colors & Icons ───────────────────────────────────────────────

private fun AIOSIslandState.toVisuals(): Pair<Color, ImageVector?> = when (this) {
    AIOSIslandState.IDLE         -> BuddyColors.TextMuted to null
    AIOSIslandState.LISTENING    -> BuddyColors.Rose to null
    AIOSIslandState.THINKING     -> BuddyColors.Cyan to null
    AIOSIslandState.SPEAKING     -> BuddyColors.PurpleLight to null
    AIOSIslandState.TOOL_EXECUTION -> BuddyColors.Cyan to null
    AIOSIslandState.TASK_CREATED -> BuddyColors.ActiveGreen to Icons.Default.Check
    AIOSIslandState.REMINDER     -> BuddyColors.QuietYellow to Icons.Default.Notifications
    AIOSIslandState.MORNING_WISH -> Color(0xFFFFB74D) to null
    AIOSIslandState.CONTINUOUS   -> BuddyColors.ActiveGreen to null
    AIOSIslandState.STOPPING     -> BuddyColors.TextMuted to null
    AIOSIslandState.MEMORY_SAVED -> BuddyColors.Cyan to Icons.Default.Memory
    AIOSIslandState.AIOS_MESSAGE -> BuddyColors.PurpleLight to null
    AIOSIslandState.ERROR        -> BuddyColors.Rose to Icons.Default.Error
}
