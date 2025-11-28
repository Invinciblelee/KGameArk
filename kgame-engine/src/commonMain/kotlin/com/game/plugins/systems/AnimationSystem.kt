package com.game.plugins.systems

import com.game.ecs.Entity
import com.game.ecs.IteratingSystem
import com.game.ecs.World.Companion.family
import com.game.plugins.components.Alpha
import com.game.plugins.components.Animation
import com.game.plugins.components.PlaybackState
import com.game.plugins.components.Renderable
import com.game.plugins.components.Rotation
import com.game.plugins.components.Scale
import com.game.plugins.components.Transform
import com.game.plugins.components.Translation

/**
 * The AnimationSystem is responsible for updating all entities with animations.
 * It uses a dedicated extension function `applyToRenderable` to handle all the complex
 * logic of timing, state calculation, and property application.
 */
class AnimationSystem: IteratingSystem(
    // This system operates on any entity that has the necessary components
    // for an animation to be applied.
    family = family { all(Animation, Transform).any(Renderable) }
) {

    override fun onTickEntity(entity: Entity) {
        // Get all necessary components from the entity.
        val animation = entity[Animation]
        val transform = entity[Transform]

        val renderable = entity.getOrNull(Renderable)

        for (effect in animation.effects) {
            effect.update(deltaTime)

            when (effect) {
                is Translation -> {
                    transform.position = effect.currentValue
                }
                is Rotation -> {
                    transform.rotation = effect.currentValue
                    transform.rotationPivot = effect.pivot
                }
                is Scale -> {
                    val value = effect.currentValue
                    transform.scaleX = value.x
                    transform.scaleY = value.y
                    transform.scalePivot = effect.pivot
                }
                is Alpha -> {
                    renderable?.alpha = effect.currentValue
                }
            }

            if (!effect.isInfinite && effect.isFinished) {
                effect.state = PlaybackState.Stopped
            }
        }
    }
}
