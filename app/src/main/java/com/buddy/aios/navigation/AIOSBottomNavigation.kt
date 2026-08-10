package com.buddy.aios.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.buddy.aios.core.ui.animation.clickableWithScale
import com.buddy.aios.core.ui.components.GlassCard
import com.buddy.aios.core.ui.shapes.BuddyShapes
import com.buddy.aios.core.ui.theme.BuddyColors

sealed class BottomNavItem(val route: String, val title: String, val icon: ImageVector) {
    data object Home : BottomNavItem(AppDestinations.HOME, "Home", Icons.Default.Home)
    data object Chat : BottomNavItem("chat/default", "Chat", Icons.Default.ChatBubbleOutline)
    data object Memory : BottomNavItem(AppDestinations.MEMORY, "Memory", Icons.Default.Memory)
    data object Settings : BottomNavItem(AppDestinations.SETTINGS, "Settings", Icons.Default.Settings)
}

@Composable
fun AIOSBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Chat,
        BottomNavItem.Memory,
        BottomNavItem.Settings
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        GlassCard(
            shape = BuddyShapes.ExtraLarge,
            backgroundColor = BuddyColors.SurfaceDark.copy(alpha = 0.92f),
            borderBrush = BuddyColors.GlassCardBorderGradient,
            borderWidth = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val isSelected = currentRoute?.startsWith(item.route.split("/")[0]) == true
                    val activeBg = if (isSelected) BuddyColors.PurpleGlow.copy(alpha = 0.28f) else Color.Transparent

                    Box(
                        modifier = Modifier
                            .clip(BuddyShapes.Pill)
                            .background(activeBg)
                            .clickableWithScale(onClick = { onNavigate(item.route) })
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = if (isSelected) BuddyColors.Cyan else BuddyColors.TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            AnimatedVisibility(visible = isSelected, enter = fadeIn(), exit = fadeOut()) {
                                Row {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

