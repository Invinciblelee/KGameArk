package com.game.plugins.services

import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Offset
import com.game.ecs.Entity
import com.game.ecs.EntityComponentContext
import com.game.ecs.World
import com.game.ecs.World.Companion.family
import com.game.engine.geometry.ViewportTransform
import com.game.engine.geometry.clampInBounds
import com.game.engine.math.radians
import com.game.plugins.components.Camera
import com.game.plugins.components.CameraTarget
import com.game.plugins.components.CameraTransition
import com.game.plugins.components.RigidBody
import com.game.plugins.components.Elasticity
import com.game.plugins.components.Transform
import com.game.plugins.components.applyCameraTransition
import com.game.plugins.components.applySmoothFollow
import com.game.plugins.components.applyElasticityFollow
import com.game.plugins.components.getBounds
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class CameraService(
    viewportTransform: ViewportTransform,
    world: World = World.requireCurrentWorld()
): EntityComponentContext(world.componentService) {

    internal val family = family {
        all(Camera, CameraTarget, Transform)
    }

    val mainCameraEntity: Entity? get() = family.find { it[Camera].isMain }
    val activeCameraEntity: Entity? get() = family.find { it[Camera].isActive }

    val mainCamera: Camera? get() = mainCameraEntity?.get(Camera)

    val activeCamera: Camera? get() = activeCameraEntity?.get(Camera)

    val transformer = CoordinateTransformer(this, viewportTransform)

    val culler = CameraFrustumCuller(this, viewportTransform)

    val director = CameraDirector(this, viewportTransform)

}

class CameraDirector(
    private val cameraService: CameraService,
    private val viewportTransform: ViewportTransform
): EntityComponentContext(cameraService.componentService) {

    fun update(deltaTime: Float) {
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
                }
            }

            updateShake(camera, deltaTime)

            clampToMapBounds(camera, transform)
        }
    }

    private fun updateFollow(
        entity: Entity,
        transform: Transform,
        target: CameraTarget,
        deltaTime: Float
    ) {
        val targetTransform = target.entity.getOrNull(Transform) ?: return
        val elasticity = entity.getOrNull(Elasticity)
        val rigidBody = entity.getOrNull(RigidBody)

        if (elasticity != null && rigidBody != null) {
            transform.applyElasticityFollow(targetTransform.position, elasticity, rigidBody, deltaTime)
        } else {
            transform.applySmoothFollow(targetTransform.position, entity[Camera].lerpSpeed, deltaTime)
        }
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

            entity.configure { it -= CameraTransition }
            val cameraTargetName = entity.getOrNull(CameraTarget)?.name
            if (cameraTargetName == transition.targetCamera) {
                entity[Camera].isTracking = !transition.finishTracking
                return
            }

            switchCamera(transition.targetCamera, newPosition = transition.targetPosition)
        }
    }

    private fun updateShake(camera: Camera, deltaTime: Float) {
        // Updates camera shake based on trauma value
        if (camera.trauma > 0f) {
            camera.trauma = (camera.trauma - camera.traumaDecay * deltaTime).coerceAtLeast(0f)
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

    private fun clampToMapBounds(camera: Camera, transform: Transform) {
        // Clamps the camera position within the defined map boundaries
        val mapBounds = camera.mapBounds
        val cameraPosition = transform.position
        transform.position = viewportTransform.clampInBounds(mapBounds, cameraPosition)
    }


    /**
     * Instantly switches the active camera view.
     * @param name The name of the camera target to switch to.
     */
    fun switchCamera(name: String, newPosition: Offset? = null) {
        // Instantly switches the active camera view
        if (cameraService.family.none { it[CameraTarget].name == name }) {
            return
        }
        cameraService.family.forEach {
            val isTarget = it.getOrNull(CameraTarget)?.name == name
            it[Camera].isActive = isTarget
            if (isTarget && newPosition != null) {
                it[Transform].position = newPosition
            }
        }
    }

    /**
     * Smoothly switches the active camera view.
     * @param name The name of the camera target to switch to.
     */
    fun switchCameraSmoothly(name: String, duration: Float = 0.5f) {
        // Initiates a smooth transition between the current active camera and the target camera.
        val targetCameraEntity = cameraService.family.find { it[CameraTarget].name == name } ?: return

        // Ensure the target entity has a Transform to get the position
        val targetPosition = targetCameraEntity[CameraTarget].entity.getOrNull(Transform)?.position
            ?: return // Target entity must have a Transform

        val activeCameraEntity = cameraService.activeCameraEntity

        if (activeCameraEntity != null) {
            // Start the transition on the currently active camera
            activeCameraEntity.configure {
                val camera = it[Camera]
                val clampedTargetPos = viewportTransform.clampInBounds(
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
    fun panTo(
        targetPosition: Offset,
        duration: Float = 1.0f,
        finishTracking: Boolean = false
    ) {
        // 1. Find the currently active camera entity
        val activeCameraEntity = cameraService.activeCameraEntity ?: return

        activeCameraEntity.configure {
            val camera = it[Camera]

            // 2. Clamp the raw target position to ensure the viewport remains in bounds.
            val clampedTargetPos = viewportTransform.clampInBounds(
                worldBounds = camera.mapBounds,
                position = targetPosition
            )

            // 3. Initiate the transition
            it += CameraTransition(
                // Use the existing CameraTarget name as the 'next' name.
                // This signals 'completeTransition' to *not* switch active camera,
                // but simply remove the component and resume normal follow logic.
                targetCamera = it[CameraTarget].name,
                targetPosition = clampedTargetPos,
                duration = duration,
                finishTracking = finishTracking
            )
        }
    }

}

class CoordinateTransformer(
    private val cameraService: CameraService,
    private val viewportTransform: ViewportTransform
): EntityComponentContext(cameraService.componentService) {
    private val defaultCamera = Camera()
    private val defaultTransform = Transform()

    /**
     * Converts virtual coordinates to world coordinates.
     * @param position The virtual coordinates (Offset) to be converted.
     * @return The corresponding world coordinates (Offset).
     */
    fun virtualToWorld(position: Offset): Offset {
        val camera: Camera
        val transform: Transform

        val activeCameraEntity = cameraService.activeCameraEntity
        if (activeCameraEntity != null) {
            camera = activeCameraEntity[Camera]
            transform = activeCameraEntity[Transform]
        } else {
            camera = defaultCamera
            transform = defaultTransform
        }

        val virtualSize = viewportTransform.virtualSize
        val cx = virtualSize.width / 2f
        val cy = virtualSize.height / 2f
        var x = position.x - cx
        var y = position.y - cy
        if (camera.zoom != 0f) {
            x /= camera.zoom
            y /= camera.zoom
        }
        val totalRotation = camera.rotation + camera.shakeRotation
        if (totalRotation != 0f) {
            val rad = radians(totalRotation.toDouble())
            val cos = cos(rad)
            val sin = sin(rad)
            val newX = x * cos - y * sin
            val newY = x * sin + y * cos
            x = newX.toFloat()
            y = newY.toFloat()
        }
        val worldX = x + transform.position.x + camera.shakeOffset.x
        val worldY = y + transform.position.y + camera.shakeOffset.y

        return Offset(worldX, worldY)
    }

    /**
     * Converts world coordinates to virtual coordinates.
     * @param position The world coordinates (Offset) to be converted.
     * @return The corresponding virtual
     */
    fun worldToVirtual(position: Offset): Offset {
        val camera: Camera
        val transform: Transform

        val activeCameraEntity = cameraService.activeCameraEntity
        if (activeCameraEntity != null) {
            camera = activeCameraEntity[Camera]
            transform = activeCameraEntity[Transform]
        } else {
            camera = defaultCamera
            transform = defaultTransform
        }

        val virtualSize = viewportTransform.virtualSize
        val cx = virtualSize.width / 2f
        val cy = virtualSize.height / 2f
        var x = position.x - (transform.position.x + camera.shakeOffset.x)
        var y = position.y - (transform.position.y + camera.shakeOffset.y)
        val totalRotation = camera.rotation + camera.shakeRotation
        if (totalRotation != 0f) {
            val rad = radians(-totalRotation.toDouble())
            val cos = cos(rad)
            val sin = sin(rad)
            val newX = x * cos - y * sin
            val newY = x * sin + y * cos
            x = newX.toFloat()
            y = newY.toFloat()
        }
        x *= camera.zoom
        y *= camera.zoom

        return Offset(x + cx, y + cy)
    }
}

class CameraFrustumCuller(
    private val cameraService: CameraService,
    private val viewportTransform: ViewportTransform
): EntityComponentContext(cameraService.componentService) {
    private val frustumRect = MutableRect(0f, 0f, 0f, 0f)
    private val entityBounds = MutableRect(0f, 0f, 0f, 0f)

    /**
     * Checks if a transform is within the camera's frustum.
     * @param transform The transform to check.
     * @return True if the transform is within the camera's frustum, false otherwise.
     */
    fun overlaps(transform: Transform): Boolean {
        val activeCameraEntity = cameraService.activeCameraEntity

        if (activeCameraEntity != null) {
            val camera = activeCameraEntity[Camera]
            val camTrans = activeCameraEntity[Transform]
            val camX = camTrans.position.x + camera.shakeOffset.x
            val camY = camTrans.position.y + camera.shakeOffset.y
            val zoom = camera.zoom

            val virtualSize = viewportTransform.virtualSize
            val halfVW = virtualSize.width / 2f / zoom
            val halfVH = virtualSize.height / 2f / zoom

            frustumRect.set(
                left = camX - halfVW,
                top = camY - halfVH,
                right = camX + halfVW,
                bottom = camY + halfVH
            )
        } else {
            frustumRect.set(
                left = 0f,
                top = 0f,
                right = viewportTransform.virtualSize.width,
                bottom = viewportTransform.virtualSize.height
            )
        }

        frustumRect.inflate(min(frustumRect.width, frustumRect.height) * 0.1f)

        transform.getBounds(entityBounds)

        return frustumRect.overlaps(entityBounds)
    }

}
