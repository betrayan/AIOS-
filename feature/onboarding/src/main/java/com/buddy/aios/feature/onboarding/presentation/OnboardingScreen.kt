package com.buddy.aios.feature.onboarding.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.ui.components.AIOSButton
import com.buddy.aios.core.ui.components.BuddyOrb
import com.buddy.aios.core.ui.components.OrbState
import com.buddy.aios.core.ui.theme.BuddyColors
import kotlinx.coroutines.delay

@Composable
fun OnboardingScreen(onOnboardingComplete: () -> Unit) {
    var animationStarted by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0.5f,
        animationSpec = tween(durationMillis = 800),
        label = "orbScaleAnim",
    )
    val alpha by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "orbAlphaAnim",
    )

    LaunchedEffect(Unit) {
        delay(100)
        animationStarted = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BuddyColors.BackgroundRadialGradient),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .scale(scale)
                .alpha(alpha)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
        ) {
            // Futuristic Core Animated AI Orb
            BuddyOrb(
                buddyMode = BuddyMode.ACTIVE,
                size = 180.dp,
                orbState = OrbState.IDLE
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Welcome to AIOS",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    text = "Your private, intelligent personal AI companion.\nLiving on your phone. Always with you.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = BuddyColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(16.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                AIOSButton(
                    text = "Initialize AIOS",
                    onClick = onOnboardingComplete,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = onOnboardingComplete) {
                    Text("Skip to Command Center", color = BuddyColors.TextMuted)
                }
            }
        }
    }
}
