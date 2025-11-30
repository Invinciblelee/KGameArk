package com.game.plugins.components

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ScaleFactor
import com.game.ecs.Component
import com.game.ecs.ComponentType
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

sealed interface AnimationSpec {

    fun getFraction(elapsedTime: Float): Float

}

/**
 * A component of Spring, used to control the spring effect of an entity.
 * @param stiffness Stiffness of the spring.
 * @param damping Damping of the spring.
 */
data class Spring(
    val stiffness: Float = 300f,
    val damping: Float = 15f
): AnimationSpec {

    private val natFreq = sqrt(stiffness.toDouble())
    private val dampingRatio = (damping / (2f * sqrt(stiffness))).toDouble()

    override fun getFraction(elapsedTime: Float): Float {
        val t = elapsedTime.toDouble()
        val disp = 1.0
        val vel  = 0.0
        val newDisp: Double = when {
            dampingRatio > 1.0  -> overDamped(t, disp, vel)
            dampingRatio == 1.0 -> criticallyDamped(t, disp, vel)
            else                -> underDamped(t, disp, vel)
        }
        return (1.0 - newDisp).toFloat()
    }

    private fun overDamped(time: Double, d: Double, v: Double): Double {
        val r = -dampingRatio * natFreq
        val s = natFreq * sqrt(dampingRatio * dampingRatio - 1)
        val gp = r + s; val gm = r - s
        val b = (gm * d - v) / (gm - gp)
        val a = d - b
        return a * exp(gm * time) + b * exp(gp * time)
    }

    private fun criticallyDamped(time: Double, d: Double, v: Double): Double {
        val a = d
        val b = v + natFreq * d
        return (a + b * time) * exp(-natFreq * time)
    }

    private fun underDamped(time: Double, d: Double, v: Double): Double {
        val dampedFreq = natFreq * sqrt(1 - dampingRatio * dampingRatio)
        val r = -dampingRatio * natFreq
        val sinCoeff = (v + r * d) / dampedFreq
        return exp(r * time) * (d * cos(dampedFreq * time) + sinCoeff * sin(dampedFreq * time))
    }

}

/**
 * A component of Tween, used to control the tween animation of an entity.
 * @param duration Duration of the tween animation.
 * @param easing Easing function of the tween animation.
 */
data class Tween(
    val duration: Float = 1f,
    val easing: Easing = LinearEasing
): AnimationSpec {
    override fun getFraction(elapsedTime: Float): Float {
        val fraction = if (duration == 0f) 0f else elapsedTime / duration
        return easing.transform(fraction)
    }
}

/**
 * A component of InfiniteRepeatable, used to control the infinite repeatable animation of an entity.
 * @param animation Animation to be repeated.
 * @param repeatMode Repeat mode of the animation.
 */
data class InfiniteRepeatable(
    val animation: Tween,
    val repeatMode: RepeatMode = RepeatMode.Restart
) : AnimationSpec {

    override fun getFraction(elapsedTime: Float): Float {
        val t = if (animation.duration == 0f) 0f else elapsedTime % animation.duration
        val linear = t / animation.duration
        val easing = animation.easing
        return when (repeatMode) {
            RepeatMode.Restart -> easing.transform(linear)
            RepeatMode.Reverse -> {
                val half = 0.5f
                val mirrored = if (linear <= half) linear else 1f - linear
                easing.transform(mirrored * 2f)
            }
        }
    }

}

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

    /**
     * The current elapsed time of the animation in seconds.
     */
    private var elapsedTime: Float = 0f

    /**
     * Whether the animation is currently playing.
     */
    var isPlaying: Boolean = true
        private set

    /**
     * Whether the animation has been started.
     */
    var isStarted: Boolean = false
        private set

    /**
     * Whether the animation is currently in an infinite loop.
     */
    val isInfinite: Boolean
        get() = spec is InfiniteRepeatable

    /**
     * Whether the animation has completed its loop.
     */
    val isFinished: Boolean
        get() = !isInfinite && elapsedTime >= duration()

    /**
     * The current progress of the animation, ranging from 0.0 to 1.0.
     */
    val progress: Float
        get() = spec.getFraction(elapsedTime)

    /**
     * Updates the animation's progress based on the given delta time.
     * @param deltaTime The time elapsed since the last frame in seconds.
     * @return The current progress of the animation, ranging from 0.0 to 1.0.
     */
    internal fun update(deltaTime: Float): Float {
        if (!isPlaying) {
            return spec.getFraction(elapsedTime)
        }
        elapsedTime += deltaTime
        if (!isInfinite && elapsedTime >= duration()) {
            elapsedTime = duration()
            isPlaying = false
            isStarted = false
        }
        return spec.getFraction(elapsedTime)
    }

    /**
     * Starts the animation.
     */
    fun play() {
        if (!isStarted) {
            isStarted = true
            elapsedTime = 0f
        }
        isPlaying = true
    }

    /**
     * Pauses the animation.
     */
    fun pause() {
        isPlaying = false
    }

    /**
     * Stops the animation.
     */
    fun stop() {
        isPlaying = false
        isStarted = false
        elapsedTime = 0f
    }

    private fun duration(): Float = when (spec) {
        is Tween -> spec.duration
        is Spring -> 1f
        is InfiniteRepeatable -> spec.animation.duration
    }

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