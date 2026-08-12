package com.buddy.aios.core.ui.island

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.buddy.aios.core.ui.components.BuddyOrb
import com.buddy.aios.core.ui.components.OrbState
import com.buddy.aios.core.ui.theme.BuddyColors

/**
 * AIOS Dynamic Island — Premium in-app notification overlay.
 *
 * This is a Compose-native component drawn directly inside the app's content tree
 * (not a system window overlay). It requires zero extra Android permissions.
 *
 * Visual behavior:
 * - COMPACT (default): Small dark pill near the top of the screen
 * - EXPANDED (on tap): Larger card with details and action button
 * - AUTO-DISMISS: Slides up and fades out after [AIOSIslandDisplayState.autoDismissMs]
 *
 * State transitions are driven by [AIOSIslandStateManager].
 */
@Composable
fun AIOSIsland(
    displayState: AIOSIslandDisplayState,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Collapse when the island hides
    if (!displayState.isVisible && isExpanded) {
        isExpanded = false
    }

    AnimatedVisibility(
        visible = displayState.isVisible,
        enter = slideInVertically(
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            initialOffsetY = { -it }
        ) + fadeIn(animationSpec = tween(200)),
        exit = slideOutVertically(
            animationSpec = tween(250),
            targetOffsetY = { -it }
        ) + fadeOut(animationSpec = tween(200)),
        modifier = modifier
            .zIndex(100f)
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Box(contentAlignment = Alignment.TopCenter, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(if (isExpanded) 20.dp else 28.dp))
                    .background(Color(0xFF0D0D1A).copy(alpha = 0.97f))
                    .clickable { isExpanded = !isExpanded }
                    .animateContentSize(animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    )),
            ) {
                // ── Compact Row ────────────────────────────────────────────
                IslandCompactRow(state = displayState.state, message = displayState.message)

                // ── Expanded Content ───────────────────────────────────────
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    IslandExpandedContent(
                        state = displayState.state,
                        message = displayState.message,
                        actionLabel = displayState.actionLabel,
                        onAction = {
                            displayState.onAction?.invoke()
                            isExpanded = false
                        },
                        onDismiss = { isExpanded = false },
                    )
                }
            }
        }
    }
}

// ── Compact Row ───────────────────────────────────────────────────────────────

@Composable
private fun IslandCompactRow(
    state: AIOSIslandState,
    message: String,
) {
    val orbState = state.toOrbState()
    val (stateColor, stateIcon) = state.toVisuals()
    val orbScale by animateFloatAsState(
        targetValue = when (state) {
            AIOSIslandState.LISTENING, AIOSIslandState.THINKING, AIOSIslandState.SPEAKING -> 1.1f
            else -> 1.0f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "orbScale",
    )

    Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // AIOS orb indicator
        Box(modifier = Modifier.scale(orbScale)) {
            BuddyOrb(
                buddyMode = com.buddy.aios.core.domain.entity.BuddyMode.ACTIVE,
                size = 22.dp,
                orbState = orbState,
            )
        }

        // State icon for non-orb states
        if (stateIcon != null) {
            Icon(
                imageVector = stateIcon,
                contentDescription = null,
                tint = stateColor,
                modifier = Modifier.size(14.dp),
            )
        }

        // Message text
        Text(
            text = message,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
    }
}

// ── Expanded Content ──────────────────────────────────────────────────────────

@Composable
private fun IslandExpandedContent(
    state: AIOSIslandState,
    message: String,
    actionLabel: String?,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (stateColor, _) = state.toVisuals()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    ) {
        // Divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color.White.copy(alpha = 0.1f))
        )
        Spacer(Modifier.height(12.dp))

        // State label + close
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.toDisplayLabel(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = stateColor,
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(20.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = BuddyColors.TextMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Full message
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = BuddyColors.TextSecondary,
        )

        // Action button
        if (actionLabel != null) {
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onAction) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = BuddyColors.Cyan,
                    )
                }
            }
        }
    }
}

// ── State Extensions ──────────────────────────────────────────────────────────

private fun AIOSIslandState.toOrbState(): OrbState = when (this) {
    AIOSIslandState.IDLE         -> OrbState.IDLE
    AIOSIslandState.LISTENING    -> OrbState.LISTENING
    AIOSIslandState.THINKING     -> OrbState.THINKING
    AIOSIslandState.SPEAKING     -> OrbState.SPEAKING
    AIOSIslandState.ERROR        -> OrbState.ERROR
    else                         -> OrbState.IDLE
}

private fun AIOSIslandState.toVisuals(): Pair<Color, ImageVector?> = when (this) {
    AIOSIslandState.IDLE         -> BuddyColors.TextMuted to null
    AIOSIslandState.LISTENING    -> BuddyColors.Rose to null
    AIOSIslandState.THINKING     -> BuddyColors.Cyan to null
    AIOSIslandState.SPEAKING     -> BuddyColors.PurpleLight to null
    AIOSIslandState.TASK_CREATED -> BuddyColors.ActiveGreen to Icons.Default.Check
    AIOSIslandState.REMINDER     -> BuddyColors.QuietYellow to Icons.Default.Notifications
    AIOSIslandState.MEMORY_SAVED -> BuddyColors.Cyan to Icons.Default.Memory
    AIOSIslandState.AIOS_MESSAGE -> BuddyColors.PurpleLight to null
    AIOSIslandState.ERROR        -> BuddyColors.Rose to Icons.Default.Error
}

private fun AIOSIslandState.toDisplayLabel(): String = when (this) {
    AIOSIslandState.IDLE         -> "AIOS"
    AIOSIslandState.LISTENING    -> "LISTENING"
    AIOSIslandState.THINKING     -> "THINKING"
    AIOSIslandState.SPEAKING     -> "SPEAKING"
    AIOSIslandState.TASK_CREATED -> "DONE"
    AIOSIslandState.REMINDER     -> "REMINDER"
    AIOSIslandState.MEMORY_SAVED -> "REMEMBERED"
    AIOSIslandState.AIOS_MESSAGE -> "AIOS"
    AIOSIslandState.ERROR        -> "ERROR"
}
