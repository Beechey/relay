package com.avetero.relay.ui.motion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.AnimatedContentScope

/**
 * Motion tokens from design-spec.md, matching MaterialTheme.motionScheme (expressive). Use the theme's
 * `MaterialTheme.motionScheme.*Spec()` inside composables; these are for places without a composable scope.
 */
object Springs {
    /** Spatial (position, size, shape): slight overshoot is intended. */
    fun <T> spatialFast(): FiniteAnimationSpec<T> = spring(0.6f, 800f)
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(0.8f, 380f)
    fun <T> spatialSlow(): FiniteAnimationSpec<T> = spring(0.8f, 200f)
    /** Effects (alpha, colour): no overshoot. */
    fun <T> effectsFast(): FiniteAnimationSpec<T> = spring(Spring.DampingRatioNoBouncy, 3800f)
    fun <T> effects(): FiniteAnimationSpec<T> = spring(Spring.DampingRatioNoBouncy, 1600f)
    fun <T> effectsSlow(): FiniteAnimationSpec<T> = spring(Spring.DampingRatioNoBouncy, 800f)
}

/** Tab switch: fade through (old out quickly, new in with a small scale-up). */
fun <S> AnimatedContentTransitionScope<S>.fadeThrough(): ContentTransform =
    (fadeIn(tween(210, delayMillis = 90)) + scaleIn(tween(210, delayMillis = 90), initialScale = 0.92f)) togetherWith fadeOut(tween(90))

/** Push: shared axis X, the new screen comes in 30% from the right. */
fun <S> AnimatedContentTransitionScope<S>.sharedAxisIn(): ContentTransform =
    (slideInHorizontally(spring<IntOffset>(1f, 380f)) { (it * 0.3f).toInt() } + fadeIn(tween(210, delayMillis = 60))) togetherWith
        (slideOutHorizontally(spring<IntOffset>(1f, 380f)) { -(it * 0.3f).toInt() } + fadeOut(tween(90)))

/** Pop: shared axis X reversed. */
fun <S> AnimatedContentTransitionScope<S>.sharedAxisOut(): ContentTransform =
    (slideInHorizontally(spring<IntOffset>(1f, 380f)) { -(it * 0.3f).toInt() } + fadeIn(tween(210, delayMillis = 60))) togetherWith
        (slideOutHorizontally(spring<IntOffset>(1f, 380f)) { (it * 0.3f).toInt() } + fadeOut(tween(90)))

/** TV: every screen change is a quick fade through; remote users never wait on motion. */
fun <S> AnimatedContentTransitionScope<S>.tvFade(): ContentTransform = fadeIn(tween(200, delayMillis = 60)) togetherWith fadeOut(tween(60))

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The navigation host's AnimatedContentScope for the screen being shown (null outside it). */
val LocalScreenAnim = compositionLocalOf<AnimatedContentScope?> { null }

/**
 * Container transform between screens: give the poster on a grid and the hero on its detail page the same [key]
 * (e.g. "poster-m42") and the one grows into the other. A no-op outside the navigation host.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedBounds(key: Any): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val anim = LocalScreenAnim.current ?: return this
    return with(shared) { this@sharedBounds.sharedBounds(rememberSharedContentState(key), anim) }
}
