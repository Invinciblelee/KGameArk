package com.kgame.plugins.systems

import com.kgame.ecs.Entity
import com.kgame.ecs.IteratingSystem
import com.kgame.ecs.World.Companion.family
import com.kgame.ecs.World.Companion.inject
import com.kgame.plugins.components.AlphaAnimation
import com.kgame.plugins.components.Renderable
import com.kgame.plugins.components.RotationAnimation
import com.kgame.plugins.components.ScaleAnimation
import com.kgame.plugins.components.SpriteAnimation
import com.kgame.plugins.components.SpriteVisual
import com.kgame.plugins.components.Transform
import com.kgame.plugins.components.TranslationAnimation
import com.kgame.plugins.components.applyAlpha
import com.kgame.plugins.components.applyRotation
import com.kgame.plugins.components.applyScale
import com.kgame.plugins.components.applyTranslation
import com.kgame.plugins.services.AnimationService
import com.kgame.plugins.services.CameraService

/**
 * The AnimationSystem is responsible for updating all entities with animations.
 * It uses a dedicated extension function `applyToRenderable` to handle all the complex
 * logic of timing, state calculation, and property application.
 */
class AnimationSystem(
    private val animationService: AnimationService = inject()
) : IteratingSystem(
    family = family {
        all(Transform, Renderable)
        any(
            TranslationAnimation,
            RotationAnimation,
            ScaleAnimation,
            AlphaAnimation,
            SpriteAnimation
        )
    }
) {

    override fun onTickEntity(entity: Entity, deltaTime: Float) {
        val transform = entity[Transform]

        val translationAnimation = entity.getOrNull(TranslationAnimation)
        if (translationAnimation != null) {
            val progress = animationService.getProgress(translationAnimation)
            transform.applyTranslation(
                fromPosition = translationAnimation.from,
                toPosition = translationAnimation.to,
                fraction = progress
            )
        }

        val rotationAnimation = entity.getOrNull(RotationAnimation)
        if (rotationAnimation != null) {
            val progress = animationService.getProgress(rotationAnimation)
            transform.applyRotation(
                fromDegrees = rotationAnimation.from,
                toDegrees = rotationAnimation.to,
                pivot = rotationAnimation.pivot,
                fraction = progress,
            )
        }

        val scaleAnimation = entity.getOrNull(ScaleAnimation)
        if (scaleAnimation != null) {
            val progress = animationService.getProgress(scaleAnimation)
            transform.applyScale(
                fromScale = scaleAnimation.from,
                toScale = scaleAnimation.to,
                pivot = scaleAnimation.pivot,
                fraction = progress,
            )
        }

        val renderable = entity.getOrNull(Renderable)
        if (renderable != null) {
            val spriteVisual = renderable.visual as? SpriteVisual
            if (spriteVisual != null) {
                val spriteAnimation = entity.getOrNull(SpriteAnimation)
                if (spriteAnimation != null) {
                    val frame = animationService.getCurrentFrame(spriteAnimation, spriteVisual.atlas)
                    spriteVisual.setFrame(frame.name)
                }
            }

            val alphaAnimation = entity.getOrNull(AlphaAnimation)
            if (alphaAnimation != null) {
                val progress = animationService.getProgress(alphaAnimation)
                renderable.applyAlpha(
                    fromAlpha = alphaAnimation.from,
                    toAlpha = alphaAnimation.to,
                    fraction = progress
                )
            }
        }
    }

}
