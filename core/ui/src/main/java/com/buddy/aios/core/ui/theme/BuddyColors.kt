package com.buddy.aios.core.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * AIOS Dark-First Futuristic Design System Palette
 */
object BuddyColors {
    // Primary Brand Accents
    val Purple        = Color(0xFF8B5CF6)
    val PurpleLight   = Color(0xFFA78BFA)
    val PurpleDark    = Color(0xFF6D28D9)
    val PurpleGlow    = Color(0xFF7C3AED)

    val Teal          = Color(0xFF06B6D4)
    val TealLight     = Color(0xFF67E8F9)
    val Cyan          = Color(0xFF22D3EE)
    val CyanGlow      = Color(0xFF06B6D4)
    val Rose          = Color(0xFFF43F5E)
    val IndigoGlow    = Color(0xFF6366F1)

    // Dark-First Background & Surfaces
    val BackgroundDeep   = Color(0xFF07070E)
    val BackgroundSurface= Color(0xFF0B0B16)
    val SurfaceDark      = Color(0xFF0F0F1B)
    val SurfaceElevated  = Color(0xFF161628)
    val GlassSurface     = Color(0x1F1E1E38)
    val GlassSurfaceHigh = Color(0x3325254A)
    val GlassBorder      = Color(0x33A78BFA)
    val GlassBorderLight = Color(0x4D22D3EE)

    // Functional State Colors
    val ActiveGreen      = Color(0xFF10B981)
    val QuietYellow      = Color(0xFFF59E0B)
    val SilentBlue       = Color(0xFF3B82F6)
    val OffGray          = Color(0xFF4B5563)

    // Text & Content
    val TextPrimary      = Color(0xFFF8FAFC)
    val TextSecondary    = Color(0xFF94A3B8)
    val TextMuted        = Color(0xFF64748B)

    // Brushes & Gradients
    val PrimaryGradient = Brush.linearGradient(
        colors = listOf(Purple, Cyan)
    )

    val PrimaryGradientReverse = Brush.linearGradient(
        colors = listOf(Cyan, PurpleGlow)
    )

    val ActiveGlowGradient = Brush.linearGradient(
        colors = listOf(Cyan, PurpleGlow, IndigoGlow)
    )

    val OrbActiveGradient = Brush.radialGradient(
        colors = listOf(Cyan, PurpleGlow, BackgroundDeep)
    )

    val BackgroundRadialGradient = Brush.radialGradient(
        colors = listOf(Color(0xFF1B113B), BackgroundDeep),
        radius = 1600f
    )

    val GlassCardBorderGradient = Brush.linearGradient(
        colors = listOf(PurpleLight.copy(alpha = 0.45f), Cyan.copy(alpha = 0.20f))
    )

    val CardSurfaceGradient = Brush.linearGradient(
        colors = listOf(SurfaceElevated.copy(alpha = 0.90f), SurfaceDark.copy(alpha = 0.95f))
    )
}

