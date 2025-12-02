package com.game.plugins.systems

import com.game.ecs.Entity
import com.game.ecs.IteratingSystem
import com.game.ecs.World.Companion.family
import com.game.ecs.World.Companion.inject
import com.game.plugins.components.AlphaAnimation
import com.game.plugins.components.Renderable
import com.game.plugins.components.RotationAnimation
import com.game.plugins.components.ScaleAnimation
import com.game.plugins.components.Sprite
import com.game.plugins.components.SpriteAnimation
import com.game.plugins.components.Transform
import com.game.plugins.components.TranslationAnimation
import com.game.plugins.components.applyAlpha
import com.game.plugins.components.applyRotation
import com.game.plugins.components.applyScale
import com.game.plugins.components.applyTranslation
import com.game.plugins.components.getCurrentFrameName
import com.game.plugins.components.progress
import com.game.plugins.components.update
import com.game.plugins.services.CameraService

/**
 * The AnimationSystem is responsible for updating all entities with animations.
 * It uses a dedicated extension function `applyToRenderable` to handle all the complex
 * logic of timing, state calculation, and property application.
 */
class AnimationSystem(
    val cameraService: CameraService = inject()
) : IteratingSystem(
    family = family {
        all(Transform)
        any(
            TranslationAnimation,
            RotationAnimation,
            ScaleAnimation,
            AlphaAnimation,
            SpriteAnimation,
            Renderable
        )
    }
) {

    override fun onTickEntity(entity: Entity) {
        val transform = entity[Transform]

        if (!cameraService.culler.overlaps(transform)) {
            return
        }

        val translationAnimation = entity.getOrNull(TranslationAnimation)
        if (translationAnimation != null) {
            translationAnimation.update(deltaTime)
            transform.applyTranslation(
                fromPosition = translationAnimation.from,
                toPosition = translationAnimation.to,
                fraction = translationAnimation.progress
            )
        }

        val rotationAnimation = entity.getOrNull(RotationAnimation)
        if (rotationAnimation != null) {
            rotationAnimation.update(deltaTime)
            transform.applyRotation(
                fromDegrees = rotationAnimation.from,
                toDegrees = rotationAnimation.to,
                pivot = rotationAnimation.pivot,
                fraction = rotationAnimation.progress,
            )
        }

        val scaleAnimation = entity.getOrNull(ScaleAnimation)
        if (scaleAnimation != null) {
            scaleAnimation.update(deltaTime)
            transform.applyScale(
                fromScale = scaleAnimation.from,
                toScale = scaleAnimation.to,
                pivot = scaleAnimation.pivot,
                fraction = scaleAnimation.progress,
            )
        }

        val renderable = entity.getOrNull(Renderable)
        if (renderable != null) {
            val sprite = renderable.visual as? Sprite
            if (sprite != null) {
                val spriteAnimation = entity.getOrNull(SpriteAnimation)
                if (spriteAnimation != null) {
                    spriteAnimation.update(sprite.atlas, deltaTime)
                    sprite.setFrame(spriteAnimation.getCurrentFrameName(sprite.atlas))
                }
            }

            val alphaAnimation = entity.getOrNull(AlphaAnimation)
            if (alphaAnimation != null) {
                alphaAnimation.update(deltaTime)
                renderable.applyAlpha(
                    fromAlpha = alphaAnimation.from,
                    toAlpha = alphaAnimation.to,
                    fraction = alphaAnimation.progress
                )
            }
        }
    }
}
