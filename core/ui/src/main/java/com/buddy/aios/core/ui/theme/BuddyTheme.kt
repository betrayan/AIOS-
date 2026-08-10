package com.buddy.aios.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Export legacy aliases for backward compatibility across feature screens
val BuddyPurple       = BuddyColors.Purple
val BuddyPurpleLight  = BuddyColors.PurpleLight
val BuddyPurpleDark   = BuddyColors.PurpleDark
val BuddyTeal         = BuddyColors.Teal
val BuddyCyan         = BuddyColors.Cyan
val BuddyBackground   = BuddyColors.BackgroundDeep
val BuddySurface      = BuddyColors.SurfaceDark
val BuddySurfaceVar   = BuddyColors.SurfaceElevated
val BuddyOnSurface    = BuddyColors.TextPrimary
val BuddyOnBackground = BuddyColors.TextPrimary
val BuddyError        = BuddyColors.Rose
val BuddySuccess      = BuddyColors.ActiveGreen

private val AIOSDarkColorScheme = darkColorScheme(
    primary          = BuddyColors.Purple,
    onPrimary        = Color.White,
    primaryContainer = BuddyColors.PurpleDark,
    secondary        = BuddyColors.Teal,
    onSecondary      = Color.White,
    secondaryContainer = BuddyColors.Cyan.copy(alpha = 0.2f),
    background       = BuddyColors.BackgroundDeep,
    onBackground     = BuddyColors.TextPrimary,
    surface          = BuddyColors.SurfaceDark,
    onSurface        = BuddyColors.TextPrimary,
    surfaceVariant   = BuddyColors.SurfaceElevated,
    onSurfaceVariant = BuddyColors.TextSecondary,
    error            = BuddyColors.Rose,
    onError          = Color.White,
)

@Composable
fun BuddyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AIOSDarkColorScheme,
        typography  = BuddyTypography,
        content     = content,
    )
}
