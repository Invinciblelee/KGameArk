package com.game.plugins.services

import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.game.ecs.Entity
import com.game.ecs.EntityComponentContext
import com.game.ecs.World
import com.game.ecs.World.Companion.family
import com.game.engine.geometry.ViewportTransform
import com.game.engine.math.radians
import com.game.plugins.components.Camera
import com.game.plugins.components.CameraDeadZone
import com.game.plugins.components.CameraShake
import com.game.plugins.components.CameraTarget
import com.game.plugins.components.CameraTransition
import com.game.plugins.components.Elasticity
import com.game.plugins.components.RigidBody
import com.game.plugins.components.Smooth
import com.game.plugins.components.Transform
import com.game.plugins.components.WorldBounds
import com.game.plugins.components.applyCameraTransition
import com.game.plugins.components.applyElasticityFollow
import com.game.plugins.components.applySmoothFollow
import com.game.plugins.components.clampInBounds
import com.game.plugins.components.getBounds
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class CameraService(
    private val viewportTransform: ViewportTransform,
    world: World = World.requireCurrentWorld()
) : EntityComponentContext(world.componentService) {

    internal val family = family {
        all(Camera, Transform)
    }

    val mainCameraEntity: Entity? get() = family.find { it[Camera].isMain }

    val transformer = CoordinateTransformer(this, viewportTransform)

    val culler = CameraFrustumCuller(this, viewportTransform)

    val director = CameraDirector(this, viewportTransform)

    fun getCameraEntity(cameraName: String): Entity {
        return family.single { it[Camera].name == cameraName }
    }

    internal fun getCameraEntityOrDefault(cameraName: String?): Entity? {
        return if (cameraName != null) {
            getCameraEntity(cameraName)
        } else {
            mainCameraEntity
        }
    }

    fun getWorldBounds(cameraName: String? = null): Rect {
        val cameraEntity = getCameraEntityOrDefault(cameraName)
        return cameraEntity?.getOrNull(WorldBounds)?.rect ?: viewportTransform.virtualSize.run {
            Rect(-width / 2f, -height / 2f, width / 2f, height / 2f)
        }
    }

    fun getCameraBounds(cameraName: String? = null): Rect {
        val cameraEntity = getCameraEntityOrDefault(cameraName)
        return cameraEntity?.getOrNull(Camera)?.bounds ?: Rect.Zero
    }

}

class CameraDirector(
    private val cameraService: CameraService,
    private val viewportTransform: ViewportTransform
) : EntityComponentContext(cameraService.componentService) {

    internal fun update(deltaTime: Float) {
        cameraService.family.forEach { entity ->
            val camera = entity[Camera]
            val transform = entity[Transform]

            val transition = entity.getOrNull(CameraTransition)
            if (transition != null) {
                updateTransition(entity, transform, transition, deltaTime)
            } else if (camera.isActive && camera.isTracking) {
                val target = entity.getOrNull(CameraTarget)
                if (target != null) {
                    updateFollow(entity, transform, target, deltaTime)
                    clampToWorldBounds(camera, transform)
                }
            }

            if (camera.isActive) {
                val shake = entity.getOrNull(CameraShake)
                if (shake != null) {
                    updateShake(shake, deltaTime)
                }
            }
        }
    }

    private fun updateFollow(
        entity: Entity,
        transform: Transform,
        target: CameraTarget,
        deltaTime: Float
    ) {
        val targetTransform = target.entity.getOrNull(Transform) ?: return
        val targetPos = targetTransform.position

        val deadZone = entity.getOrNull(CameraDeadZone)
        val newTargetPos = if (deadZone != null) {
            val cameraPos = transform.position

            val deadZoneCenter = cameraPos + deadZone.offset

            val delta = targetPos - deadZoneCenter

            val halfW = deadZone.size.width / 2f
            val halfH = deadZone.size.height / 2f

            var offsetX = 0f
            var offsetY = 0f

            if (delta.x > halfW) offsetX = delta.x - halfW
            else if (delta.x < -halfW) offsetX = delta.x + halfW

            if (delta.y > halfH) offsetY = delta.y - halfH
            else if (delta.y < -halfH) offsetY = delta.y + halfH

            if (offsetX == 0f && offsetY == 0f) return

            cameraPos + Offset(offsetX, offsetY)
        } else {
            targetPos
        }

        val smooth = entity.getOrNull(Smooth)
        if (smooth != null) {
            transform.applySmoothFollow(newTargetPos, smooth, deltaTime)
            return
        }

        val elasticity = entity.getOrNull(Elasticity)
        if (elasticity != null) {
            val rigidBody = entity.getOrNull(RigidBody)
            if (rigidBody != null) {
                transform.applyElasticityFollow(newTargetPos, elasticity, rigidBody, deltaTime)
                return
            }
        }

        transform.position = newTargetPos
    }

    private fun updateTransition(
        entity: Entity,
        transform: Transform,
        transition: CameraTransition,
        deltaTime: Float
    ) {
        if (transition.startPosition == null) {
            transition.startPosition = transform.position
        }
        val progress = transform.applyCameraTransition(transition, deltaTime)
        if (progress >= 1f) {
            transform.position = transition.targetPosition

            entity.configure { -CameraTransition }
            val cameraTargetName = entity[Camera].name
            if (cameraTargetName == transition.targetCamera) {
                entity[Camera].isTracking = !transition.finishTracking
                return
            }

            switchCamera(transition.targetCamera, newPosition = transition.targetPosition)
        }
    }

    private fun updateShake(
        shake: CameraShake,
        deltaTime: Float
    ) {
        // Updates camera shake based on trauma value
        if (shake.trauma > 0f) {
            shake.trauma =
                (shake.trauma - shake.trauma * shake.traumaDecay * deltaTime).coerceAtLeast(0f)
            val shakeFactor = shake.trauma * shake.trauma

            shake.shakeOffset = Offset(
                (Random.nextFloat() * 2 - 1) * shake.maxShakeOffset * shakeFactor,
                (Random.nextFloat() * 2 - 1) * shake.maxShakeOffset * shakeFactor
            )
            shake.shakeRotation =
                (Random.nextFloat() * 2 - 1) * shake.maxShakeAngle * shakeFactor
        } else {
            shake.shakeOffset = Offset.Zero
            shake.shakeRotation = 0f
        }
    }

    private fun clampToWorldBounds(camera: Camera, transform: Transform) {
        // Clamps the camera position within the defined map boundaries
        val cameraPosition = transform.position
        transform.position = camera.clampInBounds(viewportTransform.virtualSize, cameraPosition)
    }


    /**
     * Instantly switches the active camera view.
     * @param cameraName The name of the camera to switch to.
     */
    fun switchCamera(cameraName: String, newPosition: Offset? = null) {
        // Instantly switches the active camera view
        cameraService.family.forEach {
            val isTarget = it[Camera].name == cameraName
            it[Camera].isMain = isTarget
            if (isTarget && newPosition != null) {
                it[Transform].position = newPosition
            }
        }
    }

    /**
     * Smoothly switches the active camera view.
     * @param cameraName The name of the camera to switch to.
     */
    fun switchCameraSmoothly(cameraName: String, duration: Float = 0.5f) {
        // Initiates a smooth transition between the current active camera and the target camera.
        val targetCameraEntity = cameraService.getCameraEntity(cameraName)

        // Ensure the target entity has a Transform to get the position
        val targetPosition = targetCameraEntity[CameraTarget].entity.getOrNull(Transform)?.position
            ?: return // Target entity must have a Transform

        val mainCameraEntity = cameraService.mainCameraEntity

        if (mainCameraEntity != null) {
            // Start the transition on the currently active camera
            val camera = targetCameraEntity[Camera]
            mainCameraEntity.configure {
                val clampedTargetPos = camera.clampInBounds(
                    viewportSize = viewportTransform.virtualSize,
                    position = targetPosition
                )
                +CameraTransition(
                    targetCamera = cameraName,
                    targetPosition = clampedTargetPos,
                    duration = duration,
                )
            }
        } else {
            // If no camera is active, fall back to instant switch
            switchCamera(cameraName)
        }
    }

    /**
     * Pans the currently active camera smoothly to an absolute world position.
     * This function utilizes the existing CameraTransition logic.
     *
     * @param position The absolute world coordinates (Offset) to move the camera to.
     * @param duration The time in seconds the pan should last (default 1.0s).
     * @param finishTracking If true, the camera will STOP tracking (isTracking = false) and remain static at the final position (acting as fillAfter).
     * If false (default), tracking will resume/continue upon completion (isTracking = true).
     * @param cameraName The optional name of the camera whose bounds should be used.
     *                   If null, the currently main camera will be used.
     */
    fun panTo(
        position: Offset,
        duration: Float = 1.0f,
        finishTracking: Boolean = false,
        cameraName: String? = null
    ) {
        // 1. Find the currently active camera entity
        val cameraEntity = cameraService.getCameraEntityOrDefault(cameraName) ?: return
        val camera = cameraEntity[Camera]
        cameraEntity.configure {
            // 2. Clamp the raw target position to ensure the viewport remains in bounds.
            val clampedTargetPos = camera.clampInBounds(
                viewportSize = viewportTransform.virtualSize,
                position = position
            )

            // 3. Initiate the transition
            +CameraTransition(
                // Use the existing CameraTarget name as the 'next' name.
                // This signals 'completeTransition' to *not* switch active camera,
                // but simply remove the component and resume normal follow logic.
                targetCamera = camera.name,
                targetPosition = clampedTargetPos,
                duration = duration,
                finishTracking = finishTracking
            )
        }
    }

    /**
     * Shakes the camera view by adding trauma.
     *
     * @param trauma The trauma of shake (clamped between 0 and 1).
     * @param traumaDecay The rate at which the trauma decays per second.
     * @param maxOffset The maximum pixel offset for shake (optional, overrides default).
     * @param maxAngle The maximum angle (in degrees) for shake (optional, overrides default).
     * @param cameraName The optional name of the camera whose bounds should be used.
     *                   If null, the currently main camera will be used.
     */
    fun shake(
        trauma: Float,
        traumaDecay: Float? = null,
        maxOffset: Float? = null,
        maxAngle: Float? = null,
        cameraName: String? = null
    ) {
        val cameraEntity = cameraService.getCameraEntityOrDefault(cameraName) ?: return
        val shake = cameraEntity.getOrNull(CameraShake) ?: return

        shake.trauma = trauma.coerceIn(0f, 1f)

        if (traumaDecay != null) {
            shake.traumaDecay = traumaDecay.coerceAtLeast(0.01f)
        }
        if (maxOffset != null) {
            shake.maxShakeOffset = maxOffset.coerceAtLeast(0f)
        }
        if (maxAngle != null) {
            shake.maxShakeAngle = maxAngle.coerceAtLeast(0f)
        }
    }

}

class CoordinateTransformer(
    private val cameraService: CameraService,
    private val viewportTransform: ViewportTransform
) : EntityComponentContext(cameraService.componentService) {

    /**
     * Converts virtual coordinates (e.g., mouse click on screen) to world coordinates.
     *
     * @param position The virtual coordinates (Offset) to be converted.
     * @param cameraName The optional name of the camera target to use for this transformation.
     *                   If null, the currently main camera will be used.
     * @return The corresponding world coordinates (Offset).
     */
    fun virtualToWorld(
        position: Offset,
        cameraName: String? = null
    ): Offset {
        // 1. Find the correct camera entity.
        val cameraEntity = cameraService.getCameraEntityOrDefault(cameraName) ?: return position

        // --- CRITICAL FIX: Get the camera's specific viewport ---
        val camera = cameraEntity.getOrNull(Camera) ?: return position
        val transform = cameraEntity.getOrNull(Transform) ?: return position
        val shake = cameraEntity.getOrNull(CameraShake)

        // --- Calculate the center of THIS camera's viewport, not the whole screen ---
        val virtualSize = viewportTransform.virtualSize
        val viewLeft = virtualSize.width * camera.viewport.left
        val viewTop = virtualSize.height * camera.viewport.top
        val viewWidth = virtualSize.width * camera.viewport.width
        val viewHeight = virtualSize.height * camera.viewport.height
        val viewCenterX = viewLeft + viewWidth / 2f
        val viewCenterY = viewTop + viewHeight / 2f

        // 2. Convert screen coordinates to coordinates relative to the VIEWPORT'S center.
        var x = position.x - viewCenterX
        var y = position.y - viewCenterY

        // 3. Apply inverse scaling (from Transform).
        val scaleX = transform.scale.scaleX
        val scaleY = transform.scale.scaleY
        if (scaleX != 0f) x /= scaleX
        if (scaleY != 0f) y /= scaleY

        // 4. Apply inverse rotation (from Transform and Shake).
        val totalRotation = transform.rotation + (shake?.shakeRotation ?: 0f)
        if (totalRotation != 0f) {
            val rad = radians(totalRotation.toDouble())
            val cos = cos(rad)
            val sin = sin(rad)
            val newX = x * cos - y * sin
            val newY = x * sin + y * cos
            x = newX.toFloat()
            y = newY.toFloat()
        }

        // 5. Apply inverse translation, adding the camera's world position and shake offset.
        val finalCameraX = transform.position.x + (shake?.shakeOffset?.x ?: 0f)
        val finalCameraY = transform.position.y + (shake?.shakeOffset?.y ?: 0f)

        return Offset(x + finalCameraX, y + finalCameraY)
    }


    /**
     * Converts world coordinates to virtual coordinates (e.g., where to draw an object on screen).
     *
     * @param position The world coordinates (Offset) to be converted.
     * @param cameraName The optional name of the camera target to use for this transformation.
     *                   If null, the currently main camera will be used.
     * @return The corresponding virtual coordinates (Offset).
     */
    fun worldToVirtual(
        position: Offset,
        cameraName: String? = null
    ): Offset {
        // 1. Find the correct camera entity.
        val cameraEntity = cameraService.getCameraEntityOrDefault(cameraName) ?: return position

        // --- CRITICAL FIX: Get the camera's specific viewport ---
        val camera = cameraEntity.getOrNull(Camera) ?: return position
        val transform = cameraEntity.getOrNull(Transform) ?: return position
        val shake = cameraEntity.getOrNull(CameraShake)

        // 2. Calculate the offset of the world position from the final camera position (including shake).
        val finalCameraX = transform.position.x + (shake?.shakeOffset?.x ?: 0f)
        val finalCameraY = transform.position.y + (shake?.shakeOffset?.y ?: 0f)
        var x = position.x - finalCameraX
        var y = position.y - finalCameraY

        // 3. Apply inverse rotation (from Transform and Shake).
        val totalRotation = transform.rotation + (shake?.shakeRotation ?: 0f)
        if (totalRotation != 0f) {
            val rad = radians(-totalRotation.toDouble()) // Note: Negative rotation here.
            val cos = cos(rad)
            val sin = sin(rad)
            val newX = x * cos - y * sin
            val newY = x * sin + y * cos
            x = newX.toFloat()
            y = newY.toFloat()
        }

        // 4. Apply forward scaling (from Transform).
        x *= transform.scale.scaleX
        y *= transform.scale.scaleY

        // 5. 【CRITICAL FIX】Convert from viewport-center-relative coordinates back to top-left screen coordinates.
        //    Calculate the center of THIS camera's viewport, not the whole screen.
        val virtualSize = viewportTransform.virtualSize
        val viewLeft = virtualSize.width * camera.viewport.left
        val viewTop = virtualSize.height * camera.viewport.top
        val viewWidth = virtualSize.width * camera.viewport.width
        val viewHeight = virtualSize.height * camera.viewport.height
        val viewCenterX = viewLeft + viewWidth / 2f
        val viewCenterY = viewTop + viewHeight / 2f

        return Offset(x + viewCenterX, y + viewCenterY)
    }


    /**
     * Clamps a position within the specified camera's world bounds.
     *
     * @param position The world position to be clamped.
     * @param cameraName The optional name of the camera whose bounds should be used.
     *                   If null, the currently main camera will be used.
     * @return The clamped position, or the original position if no valid bounds are found.
     */
    fun clampInBounds(
        position: Offset,
        cameraName: String? = null
    ): Offset {
        // 1. Find the correct camera entity, same logic as virtualToWorld.
        val cameraEntity = cameraService.getCameraEntityOrDefault(cameraName) ?: return position
        val camera = cameraEntity.getOrNull(Camera) ?: return position

        // 2. Use the clampInBounds helper function to perform the calculation.
        return camera.clampInBounds(viewportTransform.virtualSize, position)
    }

}


class CameraFrustumCuller(
    private val cameraService: CameraService,
    private val viewportTransform: ViewportTransform
) : EntityComponentContext(cameraService.componentService) {
    private val frustumBounds = MutableRect(0f, 0f, 0f, 0f)
    private val entityBounds = MutableRect(0f, 0f, 0f, 0f)

    /**
     * Checks if a transform is potentially visible to a camera and should be rendered.
     *
     * This function performs an efficient two-stage culling process:
     * 1.  **Broad-phase:** It first performs a quick check against the camera's defined
     *      world bounds (`camera.bounds`). If the transform is completely outside these
     *      bounds, it is culled immediately. This is the most efficient culling path.
     * 2.  **Narrow-phase:** If the transform passes the broad-phase check (or if no
     *      world bounds are set), this function then calculates the camera's precise
     *      frustum (field of view) based on its position, scale, and viewport. It
     *      then performs a final overlap check against this precise frustum.
     *
     * @param transform The transform of the entity to be checked.
     * @param cameraName The optional name of the camera to be used for the check.
     *                   If null, the main camera is used.
     * @return `false` if the transform is definitively outside the camera's view and should be culled.
     *         `true` if the transform is potentially inside the view and should be rendered.
     */
    fun overlaps(transform: Transform, cameraName: String? = null): Boolean {
        // --- Setup ---
        // If the camera cannot be found, there is no basis for culling, so we don't cull.
        val cameraEntity = cameraService.getCameraEntityOrDefault(cameraName) ?: return true
        val camera = cameraEntity.getOrNull(Camera) ?: return true

        transform.getBounds(entityBounds)

        // --- 1. Broad-Phase Culling (World Bounds Check) ---
        val cameraBounds = camera.bounds
        // This check is only performed if world bounds have been defined for the camera.
        if (!cameraBounds.isEmpty) {
            if (!entityBounds.overlaps(cameraBounds)) {
                // The object is entirely outside the world map, so it's definitely not visible. Cull it.
                return false
            }
        }
        // If no world bounds are set, or if the object is within them, proceed to the narrow-phase.

        // --- 2. Narrow-Phase Culling (Precise Frustum Check) ---
        val camTrans = cameraEntity[Transform]
        val camShake = cameraEntity.getOrNull(CameraShake)

        // Get frustum bounds.
        getBounds(frustumBounds, camTrans, camShake)

        // (Optional but recommended) Inflate the frustum slightly to prevent flickering
        // for objects that are moving quickly at the edge of the screen.
        frustumBounds.inflate(min(frustumBounds.width, frustumBounds.height) * 0.1f)

        // The final check: does the entity's bounds overlap with the camera's precise frustum?
        return frustumBounds.overlaps(entityBounds)
    }

    private fun getBounds(bounds: MutableRect, camTrans: Transform, camShake: CameraShake?) {
        // Calculate the camera's final position in the world, including any shake offset.
        val camX = camTrans.position.x + (camShake?.shakeOffset?.x ?: 0f)
        val camY = camTrans.position.y + (camShake?.shakeOffset?.y ?: 0f)
        val scale = camTrans.scale

        // CRITICAL FIX: Calculate the frustum's size in world units based on the
        // virtual screen size, the camera's specific viewport, and the camera's scale.
        val virtualSize = viewportTransform.virtualSize
        val halfViewWidthInWorld = (virtualSize.width / 2f) / scale.scaleX
        val halfViewHeightInWorld = (virtualSize.height / 2f) / scale.scaleY

        bounds.set(
            left = camX - halfViewWidthInWorld,
            top = camY - halfViewHeightInWorld,
            right = camX + halfViewWidthInWorld,
            bottom = camY + halfViewHeightInWorld
        )
    }


    fun getBounds(bounds: MutableRect, cameraName: String? = null) {
        val cameraEntity = cameraService.getCameraEntityOrDefault(cameraName) ?: return
        val camTrans = cameraEntity[Transform]
        val camShake = cameraEntity.getOrNull(CameraShake)
        getBounds(bounds, camTrans, camShake)
    }

}
