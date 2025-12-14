package com.kgame.plugins.components

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ScaleFactor
import com.kgame.ecs.Component
import com.kgame.ecs.ComponentType
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

sealed interface AnimationSpec

/**
 * A AnimationSpec of Spring, used to control the spring effect of an entity.
 * @param stiffness Stiffness of the spring.
 * @param damping Damping of the spring.
 */
data class Spring(
    val stiffness: Float = 300f,
    val damping: Float = 15f
): AnimationSpec

/**
 * A AnimationSpec of Tween, used to control the tween animation of an entity.
 * @param duration Duration of the tween animation.
 * @param easing Easing function of the tween animation.
 */
data class Tween(
    val duration: Float = 1f,
    val easing: Easing = LinearEasing
): AnimationSpec

/**
 * A AnimationSpec of InfiniteRepeatable, used to control the infinite repeatable animation of an entity.
 * @param animation Animation to be repeated.
 * @param repeatMode Repeat mode of the animation.
 */
data class InfiniteRepeatable(
    val animation: Tween,
    val repeatMode: RepeatMode = RepeatMode.Restart
) : AnimationSpec


enum class AnimationState { Playing, Paused, Stopped }

/**
 * Describes a specific animation effect, such as translation or alpha fade.
 *
 * @param from The starting value of the animation.
 * @param to The ending value of the animation.
 * @param spec The `AnimationTiming` (e.g., `Tween`, `Spring`, `InfiniteRepeatable`) that defines the animation's behavior.
 */
sealed class Animation<T>(
    val from: T,
    val to: T,
    val spec: AnimationSpec
) {
    internal var elapsedTime: Float = 0f
    internal var state: AnimationState = AnimationState.Playing
}

class TranslationAnimation(
    from: Offset,
    to: Offset,
    spec: AnimationSpec = Tween(1f)
) : Animation<Offset>(from, to, spec), Component<TranslationAnimation> {
    override fun type() = TranslationAnimation
    companion object: ComponentType<TranslationAnimation>()
}

class RotationAnimation(
    from: Float,
    to: Float,
    val pivot: TransformOrigin = TransformOrigin.Center,
    spec: AnimationSpec = Tween(1f)
) : Animation<Float>(from, to, spec), Component<RotationAnimation> {
    override fun type() = RotationAnimation
    companion object: ComponentType<RotationAnimation>()
}

class ScaleAnimation(
    from: ScaleFactor,
    to: ScaleFactor,
    val pivot: TransformOrigin = TransformOrigin.Center,
    spec: AnimationSpec = Tween(1f)
) : Animation<ScaleFactor>(from, to, spec), Component<ScaleAnimation> {
    constructor(
        from: Float,
        to: Float,
        pivot: TransformOrigin = TransformOrigin.Center,
        spec: AnimationSpec = Tween(1f)
    ) : this(
        from = ScaleFactor(from, from),
        to = ScaleFactor(to, to),
        pivot = pivot,
        spec = spec
    )

    override fun type() = ScaleAnimation
    companion object: ComponentType<ScaleAnimation>()
}

class AlphaAnimation(
    from: Float,
    to: Float,
    spec: AnimationSpec = Tween(1f)
) : Animation<Float>(from, to, spec), Component<AlphaAnimation> {
    override fun type() = AlphaAnimation
    companion object: ComponentType<AlphaAnimation>()
}

/**
 * Updates the animation's progress based on the given delta time.
 * @param deltaTime The time elapsed since the last frame in seconds.
 * @return Whether the animation has updated its loop.
 */
fun Animation<*>.update(deltaTime: Float): Boolean {
    if (this.state != AnimationState.Playing) return false
    this.elapsedTime += deltaTime
    if (!this.isInfinite && this.elapsedTime >= this.duration()) {
        this.elapsedTime = this.duration()
        this.state = AnimationState.Stopped
    }
    return true
}

/**
 * Starts the animation.
 */
fun Animation<*>.play() {
    if (this.state == AnimationState.Stopped) {
        this.elapsedTime = 0f
    }
    this.state = AnimationState.Playing
}

/**
 * Pauses the animation.
 */
fun Animation<*>.pause() {
    this.state = AnimationState.Paused
}

/**
 * Stops the animation.
 */
fun Animation<*>.stop() {
    this.state = AnimationState.Stopped
    elapsedTime = 0f
}

/**
 * Whether the animation is currently in an infinite loop.
 */
val Animation<*>.isInfinite: Boolean
    get() = spec is InfiniteRepeatable

/**
 * Whether the animation has completed its loop.
 */
val Animation<*>.isFinished: Boolean
    get() = !isInfinite && elapsedTime >= duration()

/**
 * The current progress of the animation, ranging from 0.0 to 1.0.
 */
val Animation<*>.progress: Float
    get() {
        val duration = duration()
        if (duration <= 0f) return if (elapsedTime > 0f) 1f else 0f

        val fraction = if (isInfinite) {
            (elapsedTime / duration) % 1.0f
        } else {
            (elapsedTime / duration).coerceIn(0f, 1f)
        }
        return when (spec) {
            is Tween -> spec.easing.transform(fraction)
            is InfiniteRepeatable -> {
                val linear = (elapsedTime / spec.animation.duration) % 1.0f
                when (spec.repeatMode) {
                    RepeatMode.Restart -> spec.animation.easing.transform(linear)
                    RepeatMode.Reverse -> {
                        val mirrored = if (linear <= 0.5f) linear * 2f else (1f - linear) * 2f
                        spec.animation.easing.transform(mirrored)
                    }
                }
            }
            is Spring -> SpringRatioSimulation.getFraction(elapsedTime, spec)
        }
    }

private fun Animation<*>.duration(): Float = when (spec) {
    is Tween -> spec.duration
    is Spring -> 1f
    is InfiniteRepeatable -> spec.animation.duration
}

private object SpringRatioSimulation {
    fun getFraction(elapsedTime: Float, spring: Spring): Float {
        val natFreq = sqrt(spring.stiffness.toDouble())
        val dampingRatio = (spring.damping / (2f * sqrt(spring.stiffness))).toDouble()
        val t = elapsedTime.toDouble()
        val newDisp: Double = when {
            dampingRatio > 1.0 -> overDamped(t, natFreq, dampingRatio)
            dampingRatio == 1.0 -> criticallyDamped(t, natFreq)
            else -> underDamped(t, natFreq, dampingRatio)
        }
        return (1.0 - newDisp).toFloat()
    }

    private fun overDamped(t: Double, natFreq: Double, dampingRatio: Double): Double {
        val r = -dampingRatio * natFreq; val s = natFreq * sqrt(dampingRatio * dampingRatio - 1); val gp = r + s; val gm = r - s; val b = (gm - 0.0) / (gm - gp); val a = 1.0 - b; return a * exp(gm * t) + b * exp(gp * t)
    }

    private fun criticallyDamped(t: Double, natFreq: Double): Double {
        val a = 1.0; val b = natFreq; return (a + b * t) * exp(-natFreq * t)
    }

    private fun underDamped(t: Double, natFreq: Double, dampingRatio: Double): Double {
        val dampedFreq = natFreq * sqrt(1 - dampingRatio * dampingRatio); val r = -dampingRatio * natFreq; val sinCoeff = r / dampedFreq; return exp(r * t) * (cos(dampedFreq * t) + sinCoeff * sin(dampedFreq * t))
    }
}