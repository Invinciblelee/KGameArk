package com.game.ecs.systems

import androidx.compose.ui.geometry.Offset
import com.game.ecs.Entity
import com.game.ecs.IteratingSystem
import com.game.ecs.World.Companion.family
import com.game.ecs.World.Companion.inject
import com.game.ecs.components.Camera
import com.game.ecs.components.CameraTarget
import com.game.ecs.components.CameraTransition
import com.game.ecs.components.SpringEffect
import com.game.ecs.components.Transform
import com.game.ecs.components.applyLerpFollow
import com.game.ecs.components.applySpringFollow
import com.game.ecs.injectables.ViewportTransform
import com.game.ecs.injectables.clampPositionInBounds
import kotlin.random.Random

/**
 * System responsible for updating camera position, applying follow logic,
 * handling smooth transitions, screen shake, and boundary constraints.
 */
class CameraSystem(
    val viewportTransform: ViewportTransform = inject()
) : IteratingSystem(
    family = family { all(Camera, CameraTarget, Transform) }
) {

    override fun onTickEntity(entity: Entity) {
        val cameraTransform = entity[Transform]
        val camera = entity[Camera]

        // 1. Check for active transition task. If present, run transition logic.
        val transition = entity.getOrNull(CameraTransition)
        if (transition != null) {
            handleTransition(entity, cameraTransform, transition)

            // Apply shake even during transition
            updateShake(deltaTime, camera)
            return
        }


        if (!camera.isActive) return

        // 2. Execute normal follow logic (Only if the camera is the active view)
        if (camera.isTracking) {
            val effect = entity.getOrNull(SpringEffect)
            val cameraTarget = entity[CameraTarget]

            updateFollow(deltaTime, camera, cameraTarget, cameraTransform, effect)
        }

        // 3. Execute shake logic
        updateShake(deltaTime, camera)

        // 4. Clamp camera within map boundaries
        clampToMapBounds(camera, cameraTransform)
    }

    private fun updateFollow(
        deltaTime: Float,
        camera: Camera,
        cameraTarget: CameraTarget,
        cameraTransform: Transform,
        effect: SpringEffect?
    ) {
        val targetTransform = cameraTarget.entity.getOrNull(Transform) ?: return
        val targetPosition = targetTransform.position

        // Apply Spring or Lerp based on the presence of SpringEffect
        if (effect != null) {
            cameraTransform.applySpringFollow(
                deltaTime = deltaTime,
                effect = effect,
                targetPosition = targetPosition
            )
        } else {
            cameraTransform.applyLerpFollow(
                deltaTime = deltaTime,
                targetPosition = targetPosition,
                lerpSpeed = camera.lerpSpeed
            )
        }

        // Clamp boundaries after movement calculation
        clampToMapBounds(camera, cameraTransform)
    }

    private fun handleTransition(entity: Entity, cameraTransform: Transform, transition: CameraTransition) {
        // 1. Lazy initialization: Lock the start position on the first frame.
        if (transition.startPosition == null) {
            transition.startPosition = cameraTransform.position
        }

        val startPos = transition.startPosition ?: return

        // 2. Update elapsed time and raw progress
        transition.elapsed += deltaTime
        val rawProgress = (transition.elapsed / transition.duration).coerceIn(0f, 1f)

        // 3. Calculate smooth curve using Smoothstep (Ease-In-Out)
        val easedProgress = rawProgress * rawProgress * (3f - 2f * rawProgress)

        // 4. Interpolate position: Start -> End
        val newX = startPos.x + (transition.targetPosition.x - startPos.x) * easedProgress
        val newY = startPos.y + (transition.targetPosition.y - startPos.y) * easedProgress

        cameraTransform.position = Offset(newX, newY)

        // Clamp boundaries during transition
        clampToMapBounds(entity[Camera], cameraTransform)

        // 5. Check for completion
        if (rawProgress >= 1f) {
            completeTransition(entity, transition)
        }
    }

    /**
     * Finalizes the transition, switches active camera, and resets physics state.
     */
    private fun completeTransition(entity: Entity, transition: CameraTransition) {
        // Enforce final position to eliminate float error
        entity[Transform].position = transition.targetPosition

        // 1. Remove transition component from the old camera
        entity.configure {
            it -= CameraTransition
        }

        // Check if this was a self-transition (panTo)
        if (entity[CameraTarget].name == transition.targetCamera) {
            // This was a PAN operation: The camera stays active and resumes updateFollowLogic next frame.
            entity[Camera].isTracking = !transition.finishTracking
            return
        }

        // 2. Deactivate the old camera
        entity[Camera].isActive = false

        // 3. Activate the new camera and reset its physics state
        family.forEach { newCameraEntity ->
            if (newCameraEntity[CameraTarget].name == transition.targetCamera) {
                newCameraEntity[Camera].isActive = true

                // Set the new camera's position to the end position for a seamless handoff
                newCameraEntity[Transform].position = transition.targetPosition

                // 🔥 CRITICAL FIX: Reset the SpringEffect's velocity to prevent bouncing
                val newEffect = newCameraEntity.getOrNull(SpringEffect)
                if (newEffect != null) {
                    newEffect.velocity = Offset.Zero
                }
            }
        }
    }

    private fun updateShake(dt: Float, camera: Camera) {
        // Updates camera shake based on trauma value
        if (camera.trauma > 0f) {
            camera.trauma = (camera.trauma - camera.traumaDecay * dt).coerceAtLeast(0f)
            val shakeFactor = camera.trauma * camera.trauma

            camera.shakeOffset = Offset(
                (Random.nextFloat() * 2 - 1) * camera.maxShakeOffset * shakeFactor,
                (Random.nextFloat() * 2 - 1) * camera.maxShakeOffset * shakeFactor
            )
            camera.shakeRotation =
                (Random.nextFloat() * 2 - 1) * camera.maxShakeAngle * shakeFactor
        } else {
            camera.shakeOffset = Offset.Zero
            camera.shakeRotation = 0f
        }
    }

    private fun clampToMapBounds(camera: Camera, cameraTransform: Transform) {
        // Clamps the camera position within the defined map boundaries
        val mapBounds = camera.mapBounds

        val cameraPosition = cameraTransform.position
        cameraTransform.position = viewportTransform.clampPositionInBounds(mapBounds, cameraPosition)
    }
}


/**
 * Helper function to find the active camera entity.
 * @return The active camera entity or null if not found.
 */
fun CameraSystem.findActiveCameraEntity(): Entity? {
    return family.firstOrNull { it[Camera].isActive }
}

/**
 * Helper function to switch the active camera view.
 * @param name The name of the camera target to switch to.
 */
fun CameraSystem.switchCamera(name: String) {
    // Instantly switches the active camera view
    if (family.none { it[CameraTarget].name == name }) {
        return
    }
    family.forEach {
        it[Camera].isActive = it[CameraTarget].name == name
    }
}

/**
 * Smoothly switches the active camera view.
 * @param name The name of the camera target to switch to.
 */
fun CameraSystem.switchCameraSmoothly(name: String, duration: Float = 0.5f) {
    // Initiates a smooth transition between the current active camera and the target camera.
    val targetCamera = family.firstOrNull { it[CameraTarget].name == name } ?: return

    // Ensure the target entity has a Transform to get the position
    val targetPosition = targetCamera[CameraTarget].entity.getOrNull(Transform)?.position
        ?: return // Target entity must have a Transform

    val activeCameraEntity = family.firstOrNull { it[Camera].isActive }

    if (activeCameraEntity != null) {
        // Start the transition on the currently active camera
        activeCameraEntity.configure {
            val camera = it[Camera]
            val clampedTargetPos = viewportTransform.clampPositionInBounds(
                worldBounds = camera.mapBounds,
                position = targetPosition
            )
            it += CameraTransition(
                targetCamera = name,
                targetPosition = clampedTargetPos,
                duration = duration,
            )
        }
    } else {
        // If no camera is active, fall back to instant switch
        switchCamera(name)
    }
}

/**
 * Pans the currently active camera smoothly to an absolute world position.
 * This function utilizes the existing CameraTransition logic.
 *
 * @param targetPosition The absolute world coordinates (Offset) to move the camera to.
 * @param duration The time in seconds the pan should last (default 1.0s).
 * @param finishTracking If true, the camera will STOP tracking (isTracking = false) and remain static at the final position (acting as fillAfter).
 * If false (default), tracking will resume/continue upon completion (isTracking = true).
 */
fun CameraSystem.panTo(
    targetPosition: Offset,
    duration: Float = 1.0f,
    finishTracking: Boolean = false
) {
    // 1. Find the currently active camera entity
    val activeCameraEntity = findActiveCameraEntity() ?: return

    activeCameraEntity.configure {
        val camera = it[Camera]

        // 2. Clamp the raw target position to ensure the viewport remains in bounds.
        val clampedTargetPos = viewportTransform.clampPositionInBounds(
            worldBounds = camera.mapBounds,
            position = targetPosition
        )

        // 3. Initiate the transition
        it += CameraTransition(
            // Use the existing CameraTarget name as the 'next' name.
            // This signals 'completeTransition' to *not* switch active camera,
            // but simply remove the component and resume normal follow logic.
            targetCamera = activeCameraEntity[CameraTarget].name,
            targetPosition = clampedTargetPos,
            duration = duration,
            finishTracking = finishTracking
        )
    }
}