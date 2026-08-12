package com.buddy.aios.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.buddy.aios.core.ui.animation.AIOSMotion
import com.buddy.aios.core.ui.animation.clickableWithScale
import com.buddy.aios.core.ui.components.GlassCard
import com.buddy.aios.core.ui.shapes.BuddyShapes
import com.buddy.aios.core.ui.theme.BuddyColors

sealed class BottomNavItem(val route: String, val title: String, val icon: ImageVector) {
    data object Home     : BottomNavItem(AppDestinations.HOME, "Home", Icons.Default.Home)
    data object Chat     : BottomNavItem("chat/default", "Chat", Icons.Default.ChatBubbleOutline)
    data object Memory   : BottomNavItem(AppDestinations.MEMORY, "Memory", Icons.Default.Memory)
    data object Settings : BottomNavItem(AppDestinations.SETTINGS, "Settings", Icons.Default.Settings)
}

/**
 * Rebuilt Premium Floating Glass Bottom Navigation Bar.
 *
 * Design:
 * - Floating glass container with subtle border glow
 * - Spring scale dynamics on icon selection
 * - Smooth label slide-in/out transitions
 * - Glowing accent dot for active destination indicator
 */
@Composable
fun AIOSBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Chat,
        BottomNavItem.Memory,
        BottomNavItem.Settings,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        GlassCard(
            shape = BuddyShapes.ExtraLarge,
            backgroundColor = BuddyColors.SurfaceDark.copy(alpha = 0.95f),
            borderBrush = BuddyColors.GlassCardBorderGradient,
            borderWidth = 1.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    val isSelected = currentRoute?.startsWith(item.route.split("/")[0]) == true
                    PremiumNavItem(
                        item = item,
                        isSelected = isSelected,
                        onClick = { onNavigate(item.route) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumNavItem(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1.0f,
        animationSpec = AIOSMotion.BouncySpring,
        label = "navIconScale_${item.title}",
    )

    val pillAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1.0f else 0.0f,
        animationSpec = AIOSMotion.NormalTween,
        label = "navPillAlpha_${item.title}",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(BuddyShapes.ExtraLarge)
            .clickableWithScale(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (pillAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .size(width = 58.dp, height = 32.dp)
                        .clip(BuddyShapes.Pill)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    BuddyColors.PurpleGlow.copy(alpha = 0.35f * pillAlpha),
                                    BuddyColors.IndigoGlow.copy(alpha = 0.25f * pillAlpha),
                                )
                            )
                        )
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = if (isSelected) Color.White else BuddyColors.TextMuted,
                    modifier = Modifier
                        .size(20.dp)
                        .scale(iconScale),
                )

                AnimatedVisibility(
                    visible = isSelected,
                    enter = slideInHorizontally(initialOffsetX = { -it / 2 }, animationSpec = tween(AIOSMotion.DurationNormal)) + fadeIn(animationSpec = AIOSMotion.NormalTween),
                    exit = slideOutHorizontally(targetOffsetX = { -it / 2 }) + fadeOut(animationSpec = AIOSMotion.FastTween),
                ) {
                    Row {
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .size(width = 18.dp, height = 2.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected)
                        Brush.horizontalGradient(listOf(BuddyColors.Cyan, BuddyColors.PurpleLight))
                    else
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                )
        )
    }
}
