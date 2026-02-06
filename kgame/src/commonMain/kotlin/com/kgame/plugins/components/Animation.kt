package com.kgame.plugins.components

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ScaleFactor
import com.kgame.ecs.Component
import com.kgame.ecs.ComponentType

sealed interface AnimationSpec

/**
 * A AnimationSpec of Spring, used to control the spring effect of an entity.
 * @param stiffness Stiffness of the spring.
 * @param damping Damping of the spring.
 */
data class Spring(
    val stiffness: Float = 300f,
    val damping: Float = 15f
) : AnimationSpec

/**
 * A AnimationSpec of Tween, used to control the tween animation of an entity.
 * @param duration Duration of the tween animation.
 * @param easing Easing function of the tween animation.
 */
data class Tween(
    val duration: Float = 1f,
    val easing: Easing = LinearEasing
) : AnimationSpec

/**
 * A AnimationSpec of InfiniteRepeatable, used to control the infinite repeatable animation of an entity.
 * @param animation Animation to be repeated.
 * @param repeatMode Repeat mode of the animation.
 * @param iterations Number of times the animation should repeat.
 */
data class InfiniteRepeatable(
    val animation: Tween = Tween(),
    val repeatMode: RepeatMode = RepeatMode.Restart,
    val iterations: Int = Int.MAX_VALUE
) : AnimationSpec

/**
 * Describes a specific animation effect, such as translation or alpha fade.
 *
 * @param from The starting value of the animation.
 * @param to The ending value of the animation.
 * @param spec The `AnimationTiming` (e.g., `Tween`, `Spring`, `InfiniteRepeatable`) that defines the animation's behavior.
 */
sealed class Animation<T, C>(
    val from: T,
    val to: T,
    val spec: AnimationSpec
): Component<C>, Identifiable {

    override val id: Int = Identifiable.nextId()

    val isInfinite: Boolean
        get() = spec is InfiniteRepeatable

    val duration: Float
        get() = when (spec) {
            is Tween -> spec.duration
            is Spring -> 1f
            is InfiniteRepeatable -> spec.animation.duration
        }

}

class TranslationAnimation(
    from: Offset,
    to: Offset,
    spec: AnimationSpec = Tween(1f)
) : Animation<Offset, TranslationAnimation>(from, to, spec) {
    override fun type() = TranslationAnimation

    companion object : ComponentType<TranslationAnimation>()
}

class RotationAnimation(
    from: Float,
    to: Float,
    val pivot: TransformOrigin = TransformOrigin.Center,
    spec: AnimationSpec = Tween(1f)
) : Animation<Float, RotationAnimation>(from, to, spec) {
    override fun type() = RotationAnimation

    companion object : ComponentType<RotationAnimation>()
}

class ScaleAnimation(
    from: ScaleFactor,
    to: ScaleFactor,
    val pivot: TransformOrigin = TransformOrigin.Center,
    spec: AnimationSpec = Tween(1f)
) : Animation<ScaleFactor, ScaleAnimation>(from, to, spec) {
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

    companion object : ComponentType<ScaleAnimation>()
}

class AlphaAnimation(
    from: Float,
    to: Float,
    spec: AnimationSpec = Tween(1f)
) : Animation<Float, AlphaAnimation>(from, to, spec) {
    override fun type() = AlphaAnimation

    companion object : ComponentType<AlphaAnimation>()
}

/**
 * A AnimationSpec of Path, used to move an entity along a specific [Path].
 *
 * @param path The trajectory the entity will follow.
 * @param spec The `AnimationSpec` that defines the timing (usually [Tween] or [InfiniteRepeatable]).
 * @param orientToPath If true, the entity's rotation will automatically align with the path's tangent.
 * @param rotationOffset Additional rotation offset in degrees.
 */
class PathAnimation(
    val path: Path,
    spec: AnimationSpec = Tween(1f),
    val orientToPath: Boolean = false,
    val rotationOffset: Float = 0f
) : Animation<Float, PathAnimation>(from = 0f, to = 1f, spec = spec) {

    override fun type() = PathAnimation

    companion object : ComponentType<PathAnimation>()
}