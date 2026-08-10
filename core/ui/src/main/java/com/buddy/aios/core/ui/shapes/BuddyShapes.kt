package com.buddy.aios.core.ui.shapes

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * AIOS Standard Corner Radius System
 * - Small: 12dp (badges, micro tags)
 * - Medium: 16dp (cards, text fields)
 * - Large: 24dp (sheets, featured containers)
 * - Extra Large / Pill: 28dp (floating bars, pill selectors)
 */
object BuddyShapes {
    val Small      = RoundedCornerShape(12.dp)
    val Medium     = RoundedCornerShape(16.dp)
    val Large      = RoundedCornerShape(24.dp)
    val ExtraLarge = RoundedCornerShape(28.dp)
    val Pill       = RoundedCornerShape(50)
}
