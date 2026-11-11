package dev.horizon.android.ui.animation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.cos
import kotlin.math.pow

/**
 * Windows Fluent / Apple iOS-style silky smooth deceleration easing.
 */
class EaseOutFluent(private val power: Int = 3) : Easing {
    override fun transform(fraction: Float): Float = 1f - (1f - fraction).coerceIn(0f, 1f).pow(power)
}

/**
 * Smooth spring back-easing with organic subtle overshoot.
 */
class EaseOutBack(private val power: Int = 2) : Easing {
    private val exponent = 3f - power * 0.4f
    override fun transform(fraction: Float): Float {
        val t = fraction.coerceIn(0f, 1f)
        return (1f - (1f - t).pow(exponent) * cos(1.5f * Math.PI.toFloat() * t)).coerceIn(0f, 1.15f)
    }
}

/**
 * Micro-interaction press bounce modifier: scales down smoothly to 0.96f on press
 * and snaps back with bouncy spring physics.
 */
@Composable
fun Modifier.pressBounce(
    enabled: Boolean = true,
    targetScale: Float = 0.96f,
    onClick: (() -> Unit)? = null
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) targetScale else 1f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pressBounceScale"
    )

    return this
        .graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
        }
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )
}

/**
 * Horizontal slide & fade transition spec for landscape launcher stages.
 */
fun horizontalSlideFadeTransition(forward: Boolean = true): ContentTransform {
    val distance = 70
    return (
        fadeIn(animationSpec = tween(220, easing = EaseOutFluent(3))) +
        slideInHorizontally(
            animationSpec = tween(240, easing = EaseOutFluent(3)),
            initialOffsetX = { if (forward) distance else -distance }
        )
    ) togetherWith (
        fadeOut(animationSpec = tween(150)) +
        slideOutHorizontally(
            animationSpec = tween(180),
            targetOffsetX = { if (forward) -distance else distance }
        )
    )
}

/**
 * Vertical slide & fade transition spec for sub-category screens.
 */
fun verticalSlideFadeTransition(): ContentTransform {
    return (
        fadeIn(animationSpec = tween(200, easing = EaseOutFluent(3))) +
        slideInVertically(
            animationSpec = tween(220, easing = EaseOutFluent(3)),
            initialOffsetY = { it / 6 }
        )
    ) togetherWith (
        fadeOut(animationSpec = tween(140)) +
        slideOutVertically(
            animationSpec = tween(160),
            targetOffsetY = { -it / 6 }
        )
    )
}
