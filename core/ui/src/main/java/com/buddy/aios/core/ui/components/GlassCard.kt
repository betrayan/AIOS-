package com.buddy.aios.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.buddy.aios.core.ui.shapes.BuddyShapes
import com.buddy.aios.core.ui.theme.BuddyColors

/**
 * Reusable translucent Glass Container with subtle border glow.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = BuddyShapes.Medium,
    backgroundColor: Color = BuddyColors.GlassSurface,
    borderBrush: Brush = BuddyColors.GlassCardBorderGradient,
    borderWidth: Dp = 1.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val containerModifier = modifier
        .clip(shape)
        .background(backgroundColor)
        .border(BorderStroke(borderWidth, borderBrush), shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)

    Box(
        modifier = containerModifier,
        content = content
    )
}
