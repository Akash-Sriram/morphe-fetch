package app.morphe.fetch

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import app.morphe.fetch.MorpheDefaults

// ---------------------------------------------------------------------------
// Motion — ported from morphe-manager's ui/screen/shared/Animations.kt
// ---------------------------------------------------------------------------

/**
 * Motion set shared by every transition in the helper, so dialogs, pushed screens, overlays
 * and the floating button all move like the manager's.
 */
internal object MorpheAnimations {
    private fun <T> defaultTween(
        duration: Int = MorpheDefaults.ANIMATION_DURATION,
        easing: Easing = LinearOutSlowInEasing
    ) = tween<T>(duration, easing = easing)

    val fade: EnterTransition = fadeIn(animationSpec = defaultTween())
    val fadeOut: ExitTransition = fadeOut(animationSpec = defaultTween())

    /** Dialog scale-fade, used by dialogs. */
    val dialogEnter: EnterTransition = fadeIn(animationSpec = defaultTween()) +
        scaleIn(
            initialScale = MorpheDefaults.DIALOG_SCALE,
            animationSpec = defaultTween(easing = FastOutSlowInEasing)
        )
    val dialogExit: ExitTransition = fadeOut(animationSpec = defaultTween()) +
        scaleOut(
            targetScale = MorpheDefaults.DIALOG_SCALE,
            animationSpec = defaultTween()
        )

    val overlayEnter: EnterTransition = fadeIn(animationSpec = defaultTween())
    val overlayExit: ExitTransition = fadeOut(animationSpec = defaultTween())

    val screenEnter: EnterTransition = fadeIn(defaultTween(MorpheDefaults.SCREEN_ENTER_DURATION)) +
        scaleIn(
            initialScale = MorpheDefaults.DIALOG_SCALE,
            animationSpec = defaultTween(MorpheDefaults.SCREEN_ENTER_DURATION, FastOutSlowInEasing)
        )
    val screenExit: ExitTransition = dialogExit

    val pushEnter: EnterTransition = slideInVertically(
        animationSpec = defaultTween(MorpheDefaults.SCREEN_ENTER_DURATION, FastOutSlowInEasing)
    ) { it } + fadeIn(defaultTween(MorpheDefaults.SCREEN_ENTER_DURATION))

    val pushExit: ExitTransition = slideOutVertically(
        animationSpec = defaultTween(MorpheDefaults.SCREEN_ENTER_DURATION, FastOutSlowInEasing)
    ) { it } + fadeOut(tween(MorpheDefaults.SCREEN_ENTER_DURATION, easing = LinearEasing))

    val expandFadeEnter: EnterTransition = expandVertically(defaultTween()) + fadeIn(defaultTween())
    val shrinkFadeExit: ExitTransition = shrinkVertically(defaultTween()) + fadeOut(defaultTween())

    val fabEnter: EnterTransition = fadeIn(defaultTween()) +
        scaleIn(defaultTween(), initialScale = 0.85f) +
        slideInVertically(defaultTween()) { it / 2 }
    val fabExit: ExitTransition = fadeOut(defaultTween()) +
        scaleOut(defaultTween(), targetScale = 0.85f) +
        slideOutVertically(defaultTween()) { it / 2 }

    val springSlideUpEnter: EnterTransition = slideInVertically(
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        initialOffsetY = { it }
    ) + fadeIn(tween(MorpheDefaults.ANIMATION_DURATION_SHORT))

    fun slideTransitionSpec(
        enterDuration: Int = 200,
        exitDuration: Int = 150,
        offset: (Int) -> Int = { -it / 2 }
    ): AnimatedContentTransitionScope<*>.() -> ContentTransform = {
        (fadeIn(tween(enterDuration)) + slideInVertically(tween(enterDuration)) { offset(it) })
            .togetherWith(fadeOut(tween(exitDuration)) + slideOutVertically(tween(exitDuration)) { -offset(it) })
    }

    fun fadeCrossfade(
        duration: Int = MorpheDefaults.ANIMATION_DURATION
    ): AnimatedContentTransitionScope<*>.() -> ContentTransform = {
        fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
    }
}

/** Placement and fade animation for a lazy list row. */
@Composable
internal fun Modifier.animatedListItem(itemScope: LazyItemScope): Modifier = with(itemScope) {
    this@animatedListItem.animateItem(
        fadeInSpec = tween(MorpheDefaults.ANIMATION_DURATION),
        fadeOutSpec = tween(MorpheDefaults.ANIMATION_DURATION_SHORT),
        placementSpec = spring(stiffness = 400f, dampingRatio = 0.8f)
    )
}

/** Scale-down-on-press modifier. */
@Composable
internal fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.96f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "press_scale"
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}
