package com.kgame.plugins.services

import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.kgame.ecs.Entity
import com.kgame.ecs.EntityComponentContext
import com.kgame.ecs.World
import com.kgame.ecs.World.Companion.family
import com.kgame.engine.geometry.ResolutionManager
import com.kgame.engine.geometry.set
import com.kgame.engine.math.radians
import com.kgame.plugins.components.Camera
import com.kgame.plugins.components.CameraDeadZone
import com.kgame.plugins.components.CameraShake
import com.kgame.plugins.components.CameraTarget
import com.kgame.plugins.components.CameraTransition
import com.kgame.plugins.components.Elasticity
import com.kgame.plugins.components.Movement
import com.kgame.plugins.components.Smooth
import com.kgame.plugins.components.Transform
import com.kgame.plugins.components.applyCameraTransition
import com.kgame.plugins.components.applyElasticityFollow
import com.kgame.plugins.components.applySmoothFollow
import com.kgame.plugins.components.clampToBounds
import com.kgame.plugins.components.clampToViewport
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class CameraService(
    resolution: ResolutionManager,
    world: World = World.requireCurrentWorld()
) : EntityComponentContext(world.componentService) {

    internal val family = family {
        all(Camera, Transform)
    }

    val mainCameraEntity: Entity? get() = family.find { it[Camera].isMain }

    val transformer = CoordinateTransformer(this, resolution)

    val culler = CameraFrustumCuller(this, resolution)

    val director = CameraDirector(this, resolution)

    fun getCameraEntity(cameraName: String): Entity {
        return family.single { it[Camera].name == cameraName }
    }

    internal fun getCameraEntityOrDefault(cameraName: String?): Entity? {
        val cameraEntity = cameraName?.let { name ->
            family.firstOrNull { it[Camera].name == name }
        }
        return cameraEntity ?: mainCameraEntity
    }

}

class CameraDirector(
    private val cameraService: CameraService,
    private val resolution: ResolutionManager
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
                    updateFollow(entity, camera, transform, target, deltaTime)
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
        camera: Camera,
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
            val movement = entity.getOrNull(Movement)
            if (movement != null) {
                transform.applyElasticityFollow(newTargetPos, elasticity, movement, deltaTime)
                return
            }
        }

        transform.position = newTargetPos

        clampToWorldBounds(camera, transform)
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

    /**
     * Updates camera shake state based on deterministic time-based sampling.
     * Designed for zero-GC execution within the game loop.
     */
    /**
     * Updates camera shake state with zero-allocation and non-synchronous jitter.
     * Aligned with Zen Edition's high-performance requirements.
     */
    private fun updateShake(
        shake: CameraShake,
        deltaTime: Float
    ) {
        if (shake.trauma <= 0f) {
            // Strict reset to ensure clean state after shake completion
            if (shake.shakeOffset != Offset.Zero || shake.shakeRotation != 0f) {
                shake.shakeOffset = Offset.Zero
                shake.shakeRotation = 0f
                shake.trauma = 0f
            }
            return
        }

        // Linear decay provides a predictable and "crisp" impact dissipation
        shake.trauma = (shake.trauma - shake.traumaDecay * deltaTime).coerceAtLeast(0f)

        // Performance threshold to avoid unnecessary calculations at imperceptible levels
        if (shake.trauma < 0.005f) return

        // Apply power curve for a more dynamic and punchy intensity response
        val shakeFactor = shake.trauma * shake.trauma
        val baseSeed = (shake.trauma * shake.frequency).toDouble()

        // Adding phase constants to prevent axial synchronization, ensuring organic motion
        val noiseX = sin(baseSeed + 1.12).toFloat()
        val noiseY = sin(baseSeed + 2.84).toFloat()
        val noiseR = sin(baseSeed + 5.37).toFloat()

        // Final transformation results to be consumed by the rendering pipeline
        shake.shakeOffset = Offset(
            x = noiseX * shake.maxShakeOffset * shakeFactor,
            y = noiseY * shake.maxShakeOffset * shakeFactor
        )
        shake.shakeRotation = noiseR * shake.maxShakeAngle * shakeFactor
    }

    private fun clampToWorldBounds(camera: Camera, transform: Transform) {
        // Clamps the camera position within the defined map boundaries
        val cameraPosition = transform.position
        transform.position = camera.clampToBounds(cameraPosition, resolution.virtualSize)
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
                val clampedTargetPos = camera.clampToBounds(
                    viewportSize = resolution.virtualSize,
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
            val clampedTargetPos = camera.clampToBounds(
                viewportSize = resolution.virtualSize,
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
     * Triggers a camera shake by injecting trauma into the camera system.
     *
     * This implementation uses "Max-Energy" logic: the strongest impact currently
     * active will take precedence, preventing minor jitters from overriding
     * major explosive feedback.
     *
     * @param trauma The intensity of the impact. Recommended range [0.0, 1.0].
     * @param traumaDecay The rate (units per second) at which the trauma dissipates.
     * @param maxOffset Maximum pixel displacement for the translation shake.
     * @param maxAngle Maximum rotation angle (in degrees) for the rotational shake.
     * @param frequency The oscillation speed. Higher values feel "harder".
     * @param cameraName Optional target camera name; defaults to the main camera.
     */
    fun shake(
        trauma: Float,
        traumaDecay: Float? = null,
        maxOffset: Float? = null,
        maxAngle: Float? = null,
        frequency: Float? = null,
        cameraName: String? = null
    ) {
        val cameraEntity = cameraService.getCameraEntityOrDefault(cameraName) ?: return
        val shake = cameraEntity.getOrNull(CameraShake) ?: return

        // Maintain the highest intensity to ensure impact integrity
        shake.trauma = max(shake.trauma, trauma.coerceAtLeast(0f))

        // Optional parameter overrides
        traumaDecay?.let { shake.traumaDecay = it.coerceAtLeast(0.01f) }
        maxOffset?.let { shake.maxShakeOffset = it.coerceAtLeast(0f) }
        maxAngle?.let { shake.maxShakeAngle = it.coerceAtLeast(0f) }
        frequency?.let { shake.frequency = it.coerceAtLeast(0.1f) }
    }

}

class CoordinateTransformer(
    private val cameraService: CameraService,
    private val resolution: ResolutionManager
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
        val virtualSize = resolution.virtualSize
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
        val virtualSize = resolution.virtualSize
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
    fun clampToBounds(
        position: Offset,
        cameraName: String? = null
    ): Offset {
        // 1. Find the correct camera entity, same logic as virtualToWorld.
        val cameraEntity = cameraService.getCameraEntityOrDefault(cameraName) ?: return position
        val camera = cameraEntity.getOrNull(Camera) ?: return position

        // 2. Use the clampToBounds helper function to perform the calculation.
        return camera.clampToBounds(position, resolution.virtualSize)
    }

    /**
     * Clamps the desired position of an object to keep it fully within the viewport of a specified camera.
     *
     * This is a high-level convenience function that retrieves the necessary camera and its state
     * from the CameraService and then performs the clamping calculation. It acts as a simplified
     * entry point to the more detailed `Camera.clampToViewport` extension function.
     *
     * @param position The desired top-left world-space position of the object to be clamped.
     * @param size The size of the object.
     * @param cameraName An optional name of the camera whose viewport will be used for clamping.
     *                   If null, the main or default camera from the CameraService will be used.
     * @return The new, clamped world-space position. If the specified camera cannot be found,
     *         it returns the original, unclamped position.
     */
    fun clampToViewport(
        position: Offset,
        size: Size,
        cameraName: String? = null
    ): Offset {
        // 1. Find the correct camera entity, same logic as virtualToWorld.
        val cameraEntity = cameraService.getCameraEntityOrDefault(cameraName) ?: return position
        val camera = cameraEntity.getOrNull(Camera) ?: return position
        val transform = cameraEntity.getOrNull(Transform) ?: return position

        // 2. Use the clampToViewport helper function to perform the calculation.
        return camera.clampToViewport(position, size, transform.position, resolution.virtualSize)
    }

}


class CameraFrustumCuller(
    private val cameraService: CameraService,
    private val resolution: ResolutionManager
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
     * @param bounds The local bounds of the entity to be checked.
     * @param cameraName The optional name of the camera to be used for the check.
     *                   If null, the main camera is used.
     * @return `false` if the transform is definitively outside the camera's view and should be culled.
     *         `true` if the transform is potentially inside the view and should be rendered.
     */
    fun overlaps(transform: Transform, bounds: Rect, cameraName: String? = null): Boolean {
        if (bounds.isInfinite) return true
        if (bounds.isEmpty) return false

        // --- Setup ---
        // If the camera cannot be found, there is no basis for culling, so we don't cull.
        val cameraEntity = cameraService.getCameraEntityOrDefault(cameraName) ?: return true
        val camera = cameraEntity.getOrNull(Camera) ?: return true

        entityBounds.set(transform, bounds)

        // --- 1. Broad-Phase Culling (World Bounds Check) ---
        val cameraBounds = camera.bounds
        // This check is only performed if world bounds have been defined for the camera.
        if (!cameraBounds.isEmpty && !entityBounds.overlaps(cameraBounds)) {
            // The object is entirely outside the world map, so it's definitely not visible. Cull it.
            return false
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


    /**
     * Calculates the bounding box of the camera's view frustum and writes the result to the
     * provided [bounds] object.
     *
     * This function determines the region of the 3D world that is visible to the specified camera.
     * If [cameraName] is not provided, the default camera entity is used.
     *
     * @param bounds The [MutableRect] object where the calculated frustum bounds will be
     * written. (NOTE: This object is modified in place).
     * @param cameraName Optional parameter specifying the name of the camera entity to use.
     * If null, the default camera provided by the service is used.
     *
     */
    fun getBounds(bounds: MutableRect, cameraName: String? = null) {
        // Retrieve the specified camera entity, falling back to the default if the name is null.
        // Return immediately if no camera entity is found.
        val cameraEntity = cameraService.getCameraEntityOrDefault(cameraName) ?: return

        // Get the Transform component, which contains the camera's position, rotation, and scale.
        val camTrans = cameraEntity[Transform]

        // Optionally retrieve the CameraShake component, which may affect the frustum calculation.
        val camShake = cameraEntity.getOrNull(CameraShake)

        // Delegate the actual bounds computation to a private, overloaded function.
        getBounds(bounds, camTrans, camShake)
    }


    private fun getBounds(bounds: MutableRect, camTrans: Transform, camShake: CameraShake?) {
        // Calculate the camera's final position in the world, including any shake offset.
        val camX = camTrans.position.x + (camShake?.shakeOffset?.x ?: 0f)
        val camY = camTrans.position.y + (camShake?.shakeOffset?.y ?: 0f)
        val scale = camTrans.scale

        // CRITICAL FIX: Calculate the frustum's size in world units based on the
        // virtual screen size, the camera's specific viewport, and the camera's scale.
        val virtualSize = resolution.virtualSize
        val halfViewWidthInWorld = (virtualSize.width / 2f) / scale.scaleX
        val halfViewHeightInWorld = (virtualSize.height / 2f) / scale.scaleY

        bounds.set(
            left = camX - halfViewWidthInWorld,
            top = camY - halfViewHeightInWorld,
            right = camX + halfViewWidthInWorld,
            bottom = camY + halfViewHeightInWorld
        )
    }

}
