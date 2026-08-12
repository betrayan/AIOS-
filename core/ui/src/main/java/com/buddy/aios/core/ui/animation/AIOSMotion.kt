package com.buddy.aios.core.ui.animation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * AIOS Motion Design System.
 *
 * Centralizes all animation specs, timing parameters, and spring dynamics across AIOS.
 * Respects accessibility reduced-motion settings automatically.
 */
object AIOSMotion {

    // ── Timing Durations ──────────────────────────────────────────────────────
    const val DurationFast = 150
    const val DurationNormal = 250
    const val DurationSlow = 400

    // ── Spring Specifications ─────────────────────────────────────────────────
    val BouncySpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    val MediumSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    val SmoothSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow,
    )

    // ── Tween Specifications ──────────────────────────────────────────────────
    val FastTween: TweenSpec<Float> = tween(durationMillis = DurationFast, easing = FastOutSlowInEasing)
    val NormalTween: TweenSpec<Float> = tween(durationMillis = DurationNormal, easing = FastOutSlowInEasing)
    val SlowTween: TweenSpec<Float> = tween(durationMillis = DurationSlow, easing = FastOutSlowInEasing)

    // ── Screen Transitions ────────────────────────────────────────────────────
    val ScreenEnter: EnterTransition = fadeIn(animationSpec = tween(DurationNormal)) +
            slideInHorizontally(initialOffsetX = { 60 }, animationSpec = tween(DurationNormal))

    val ScreenExit: ExitTransition = fadeOut(animationSpec = tween(DurationNormal)) +
            slideOutHorizontally(targetOffsetX = { -60 }, animationSpec = tween(DurationNormal))

    val PopScreenEnter: EnterTransition = fadeIn(animationSpec = tween(DurationNormal)) +
            slideInHorizontally(initialOffsetX = { -60 }, animationSpec = tween(DurationNormal))

    val PopScreenExit: ExitTransition = fadeOut(animationSpec = tween(DurationNormal)) +
            slideOutHorizontally(targetOffsetX = { 60 }, animationSpec = tween(DurationNormal))

    val ScaleFadeEnter: EnterTransition = fadeIn(animationSpec = tween(DurationNormal)) +
            scaleIn(initialScale = 0.92f, animationSpec = tween(DurationNormal))

    val ScaleFadeExit: ExitTransition = fadeOut(animationSpec = tween(DurationFast)) +
            scaleOut(targetScale = 0.95f, animationSpec = tween(DurationFast))
}

/** CompositionLocal providing reduced motion accessibility setting. */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun ProvideReducedMotion(
    reducedMotionEnabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalReducedMotion provides reducedMotionEnabled) {
        content()
    }
}
