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
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

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

/** The navigation host's scope for the screen being shown (or the mini player's AnimatedVisibility); null outside them. */
val LocalScreenAnim = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Container transform between screens: give the poster on a grid and the hero on its detail page the same [key]
 * (e.g. "poster-m42") and the one grows into the other. A no-op outside the navigation host.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedBounds(key: Any, clip: Shape? = null): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val anim = LocalScreenAnim.current ?: return this
    return with(shared) {
        if (clip == null) this@sharedBounds.sharedBounds(rememberSharedContentState(key), anim)
        else this@sharedBounds.sharedBounds(rememberSharedContentState(key), anim, clipInOverlayDuringTransition = OverlayClip(clip))
    }
}

/** False when the system's "Remove animations" (animator duration scale 0) is on: skip decorative motion then. */
@Composable
fun animationsEnabled(): Boolean {
    val ctx = LocalContext.current
    return remember { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
}

/**
 * First-appearance entrance for a screen's list: items fade in and rise 12dp, staggered 20ms (all done in 300ms).
 * Plays once per screen (saved with the screen's state), once [ready] (the data is there); later items, scrolling
 * and recompositions show at rest.
 */
@Composable
fun rememberEntrance(ready: Boolean = true): Animatable<Float, AnimationVector1D> {
    var done by rememberSaveable { mutableStateOf(false) }
    val on = animationsEnabled()
    val t = remember { Animatable(if (done || !on) 1f else 0f) }
    LaunchedEffect(ready) { if (ready && t.value < 1f) { t.animateTo(1f, tween(300, easing = LinearEasing)); done = true } }
    if (t.value >= 1f) done = true
    return t
}

/** The [index]th item of a list using [rememberEntrance]. Reads the clock in the layer only, so it never recomposes. */
fun Modifier.entrance(t: Animatable<Float, AnimationVector1D>, index: Int): Modifier = graphicsLayer {
    if (t.value >= 1f) return@graphicsLayer
    val p = FastOutSlowInEasing.transform(((t.value * 300f - index.coerceIn(0, 6) * 20f) / 180f).coerceIn(0f, 1f))
    alpha = p
    translationY = (1f - p) * 12.dp.toPx()
}

/** Skeleton placeholder: a soft light band sweeping across (static when animations are off). */
@Composable
fun Modifier.shimmer(): Modifier {
    if (!animationsEnabled()) return this
    val x by rememberInfiniteTransition(label = "shimmer").animateFloat(-1f, 2f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "x")
    return drawWithContent {
        drawContent()
        val w = size.width
        drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = .08f), Color.Transparent),
            start = Offset(w * x - w * .5f, 0f), end = Offset(w * x, size.height)))
    }
}

/**
 * Phone press feedback for custom clickable surfaces (cards, posters, rows): the [base] indication (ripple) plus a
 * slight shrink on press with the given spatial spring. Provided as LocalIndication, so every plain `clickable` gets it;
 * M3 components pass their own indication and keep their shape morphs.
 */
class PressScale(private val base: IndicationNodeFactory, private val spec: FiniteAnimationSpec<Float>, private val pressed: Float = 0.97f) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = Node(base.create(interactionSource), interactionSource)
    override fun equals(other: Any?) = other is PressScale && other.base == base && other.spec == spec && other.pressed == pressed
    override fun hashCode() = (base.hashCode() * 31 + spec.hashCode()) * 31 + pressed.hashCode()

    private inner class Node(ripple: DelegatableNode, private val source: InteractionSource) : DelegatingNode(), DrawModifierNode {
        private val s = Animatable(1f)
        init { delegate(ripple) }
        override fun onAttach() {
            coroutineScope.launch {
                source.interactions.collect { i ->
                    val to = when (i) { is PressInteraction.Press -> pressed; is PressInteraction.Release, is PressInteraction.Cancel -> 1f; else -> return@collect }
                    launch { s.animateTo(to, spec) }
                }
            }
        }
        override fun ContentDrawScope.draw() {
            val v = s.value
            if (v == 1f) drawContent() else scale(v) { this@draw.drawContent() }
        }
    }
}
