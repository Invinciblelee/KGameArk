package com.kgame.plugins.systems

import com.kgame.ecs.Entity
import com.kgame.ecs.IteratingSystem
import com.kgame.ecs.SystemPriority
import com.kgame.ecs.World.Companion.family
import com.kgame.ecs.World.Companion.inject
import com.kgame.plugins.components.AlphaAnimation
import com.kgame.plugins.components.AnimationFinishedTag
import com.kgame.plugins.components.AutoCleanupTag
import com.kgame.plugins.components.CleanupTag
import com.kgame.plugins.components.PathAnimation
import com.kgame.plugins.components.Renderable
import com.kgame.plugins.components.RotationAnimation
import com.kgame.plugins.components.ScaleAnimation
import com.kgame.plugins.components.SpriteAnimation
import com.kgame.plugins.components.Transform
import com.kgame.plugins.components.TranslationAnimation
import com.kgame.plugins.components.applyAlpha
import com.kgame.plugins.components.applyRotation
import com.kgame.plugins.components.applyScale
import com.kgame.plugins.components.applyTranslation
import com.kgame.plugins.services.AnimationService
import com.kgame.plugins.visuals.images.SpriteVisual

/**
 * The AnimationSystem is responsible for updating all entities with animations.
 * It uses a dedicated extension function `applyToRenderable` to handle all the complex
 * logic of timing, state calculation, and property application.
 */
class AnimationSystem(
    private val animationService: AnimationService = inject(),
    priority: SystemPriority = SystemPriorityAnchors.Animation
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
    },
    priority = priority
) {

    override fun onTickEntity(entity: Entity, deltaTime: Float) {
        val transform = entity[Transform]

        var isFinished = true

        val translationAnimation = entity.getOrNull(TranslationAnimation)
        if (translationAnimation != null) {
            val progress = animationService.getProgress(translationAnimation)
            transform.applyTranslation(
                fromPosition = translationAnimation.from,
                toPosition = translationAnimation.to,
                fraction = progress
            )

            if (!animationService.isStopped(translationAnimation.id, translationAnimation.name) || translationAnimation.isInfinite) {
                isFinished = false
            }
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

            if (!animationService.isStopped(rotationAnimation.id, rotationAnimation.name) || rotationAnimation.isInfinite) {
                isFinished = false
            }
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

            if (!animationService.isStopped(scaleAnimation.id, scaleAnimation.name) || scaleAnimation.isInfinite) {
                isFinished = false
            }
        }

        val pathAnimation = entity.getOrNull(PathAnimation)
        if (pathAnimation != null) {
            // Service handles everything: timing, easing, and path sampling
            transform.position = animationService.getPathPosition(pathAnimation)

            if (pathAnimation.orientToPath) {
                transform.rotation = animationService.getPathRotation(pathAnimation)
            }

            if (!animationService.isStopped(pathAnimation.id, pathAnimation.name) || pathAnimation.isInfinite) {
                isFinished = false
            }
        }

        val renderable = entity.getOrNull(Renderable)
        if (renderable != null) {
            val spriteVisual = renderable.visual as? SpriteVisual
            if (spriteVisual != null) {
                val spriteAnimation = entity.getOrNull(SpriteAnimation)
                if (spriteAnimation != null) {
                    val frame = animationService.getCurrentFrame(spriteAnimation, spriteVisual.atlas)
                    spriteVisual.setFrame(frame.name)

                    if (!animationService.isStopped(spriteAnimation.id, spriteAnimation.name) || spriteAnimation.loop) {
                        isFinished = false
                    }
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

                if (!animationService.isStopped(alphaAnimation.id, alphaAnimation.name) || alphaAnimation.isInfinite) {
                    isFinished = false
                }
            }
        }

        if (isFinished) {
            if (entity has AutoCleanupTag) {
                entity.configure { +CleanupTag }
            } else {
                entity.configure { +AnimationFinishedTag }
            }
        }
    }

}
