package com.game.plugins.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VectorizedAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import com.game.ecs.Component
import com.game.ecs.ComponentType

/**
 * Defines the playback state of an animation.
 */
enum class PlaybackState {
    Playing,
    Paused,
    Stopped
}

internal typealias InternalAnimation<T, V> = androidx.compose.animation.core.Animation<T, V>

/**
 * Describes a specific animation effect, such as translation or alpha fade.
 *
 * @param from The starting value of the animation.
 * @param to The ending value of the animation.
 * @param spec The `AnimationSpec` (e.g., `tween`, `spring`, `infiniteRepeatable`) that defines the animation's behavior.
 */
sealed class AnimationEffect<T, V: AnimationVector>(
    val from: T,
    val to: T,
    val spec: AnimationSpec<T>
) {

    /**
     * The current elapsed time of the animation in nanoseconds.
     */
    var elapsedTime: Long = 0
        internal set

    /**
     * The current playback state of the animation.
     */
    var state: PlaybackState = PlaybackState.Stopped
        internal set

    /**
     * The real implementation of the animation.
     */
    abstract val animation: InternalAnimation<T, V>

    /**
     * Updates the animation's progress based on the given delta time.
     */
    fun update(deltaTime: Float) {
        val deltaTimeNanos = (deltaTime * 1_000_000_000).toLong()
        elapsedTime = (elapsedTime + deltaTimeNanos).coerceAtMost(animation.durationNanos)
    }

    /**
     * Whether the animation is currently playing.
     */
    val isPlaying: Boolean
        get() = state == PlaybackState.Playing

    /**
     * Whether the animation is currently paused.
     */
    val isPaused: Boolean
        get() = state == PlaybackState.Paused

    /**
     * Whether the animation is currently stopped.
     */
    val isStopped: Boolean
        get() = state == PlaybackState.Stopped

    /**
     * Whether the animation is currently in an infinite loop.
     */
    val isInfinite: Boolean
        get() = animation.isInfinite

    /**
     * Whether the animation has completed its loop.
     */
    val isFinished: Boolean
        get() = animation.isFinishedFromNanos(this.elapsedTime)

    /**
     * The current value of the animation.
     */
    val currentValue: T
        get() = animation.getValueFromNanos(this.elapsedTime)

    /**
     * The current velocity of the animation.
     */
    val currentVelocity: V
        get() = animation.getVelocityVectorFromNanos(this.elapsedTime)

}

class Translation(
    from: Offset,
    to: Offset,
    spec: AnimationSpec<Offset> = tween(1000) // Default to a 1-second tween
) : AnimationEffect<Offset, AnimationVector2D>(from, to, spec) {

    override val animation: InternalAnimation<Offset, AnimationVector2D> by lazy {
        TargetBasedAnimation(
            animationSpec = spec,
            typeConverter = Offset.VectorConverter,
            initialValue = from,
            targetValue = to
        )
    }

}

class Rotation(
    from: Float,
    to: Float,
    val pivot: TransformOrigin = TransformOrigin.Center,
    spec: AnimationSpec<Float> = tween(1000)
) : AnimationEffect<Float, AnimationVector1D>(from, to, spec) {

    override val animation: InternalAnimation<Float, AnimationVector1D> by lazy {
        TargetBasedAnimation(
            animationSpec = spec,
            typeConverter = Float.VectorConverter,
            initialValue = from,
            targetValue = to
        )
    }

}

class Scale(
    from: Offset,
    to: Offset,
    val pivot: TransformOrigin = TransformOrigin.Center,
    spec: AnimationSpec<Offset> = tween(1000)
)  : AnimationEffect<Offset, AnimationVector2D>(from, to, spec) {

    override val animation: InternalAnimation<Offset, AnimationVector2D> by lazy {
        TargetBasedAnimation(
            animationSpec = spec,
            typeConverter = Offset.VectorConverter,
            initialValue = from,
            targetValue = to
        )
    }

    constructor(
        from: Float,
        to: Float,
        pivot: TransformOrigin = TransformOrigin.Center,
        spec: AnimationSpec<Offset> = tween(1000)
    ) : this(
        from = Offset(from, from),
        to = Offset(to, to),
        pivot = pivot,
        spec = spec
    )
}

class Alpha(
    from: Float,
    to: Float,
    spec: AnimationSpec<Float> = tween(1000)
) : AnimationEffect<Float, AnimationVector1D>(from, to, spec) {

    override val animation: InternalAnimation<Float, AnimationVector1D> by lazy {
        TargetBasedAnimation(
            animationSpec = spec,
            typeConverter = Float.VectorConverter,
            initialValue = from,
            targetValue = to
        )
    }

}

/**
 * A component that holds and controls a list of animation effects for an entity.
 *
 * @property effects A mutable list of [AnimationEffect] to be applied to the entity.
 */
data class Animation(
    val effects: List<AnimationEffect<*, *>>,
    val autoPlay: Boolean = true
) : Component<Animation> {
    override fun type() = Animation

    companion object : ComponentType<Animation>()

    constructor(effect: AnimationEffect<*, *>, autoPlay: Boolean = true): this(listOf(effect), autoPlay)

    constructor(vararg effects: AnimationEffect<*, *>, autoPlay: Boolean = true): this(effects.toList(), autoPlay)

    init {
        if (autoPlay) play()
    }

    /**
     * Starts playing all animation effects from the beginning or resumes from a paused state.
     */
    fun play() {
        effects.forEach {
            if (it.state == PlaybackState.Stopped) {
                // If stopped, reset and play from the beginning.
                it.elapsedTime = 0L
            }

            // For both Stopped and Paused states, set to Playing.
            it.state = PlaybackState.Playing
        }
    }

    /**
     * Pauses all currently playing animation effects, preserving their current progress.
     * Calling `play()` again will resume the animation from this point.
     */
    fun pause() {
        effects.forEach {
            if (it.state == PlaybackState.Playing) {
                it.state = PlaybackState.Paused
            }
        }
    }

    /**
     * Stops all animation effects and resets their progress to the beginning.
     * The animated properties will be reset to their initial state on the next frame.
     */
    fun stop() {
        effects.forEach {
            it.state = PlaybackState.Stopped
            it.elapsedTime = 0
        }
    }
}
