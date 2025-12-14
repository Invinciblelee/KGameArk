package com.kgame.plugins.systems

import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.size
import com.kgame.ecs.Entity
import com.kgame.ecs.IteratingSystem
import com.kgame.ecs.World.Companion.family
import com.kgame.ecs.collection.compareEntityBy
import com.kgame.engine.map.AnimatedTiledMapTile
import com.kgame.engine.map.StaticTiledMapTile
import com.kgame.engine.map.TiledMapTileLayer
import com.kgame.plugins.components.Camera
import com.kgame.plugins.components.CameraShake
import com.kgame.plugins.components.Renderable
import com.kgame.plugins.components.Transform
import com.kgame.engine.graphics.drawscope.withCameraTransform
import com.kgame.plugins.components.TiledMap
import com.kgame.plugins.services.CameraService
import kotlin.math.floor

/**
 * A system responsible for rendering Tiled Maps, with built-in handling for tile animations.
 */
class TiledMapRenderSystem(
    private val cameraService: CameraService
) : IteratingSystem(
    family = family { all(Transform, TiledMap) }
) {

    private val visibleWorldRect = MutableRect(0f, 0f, 0f, 0f)

     // A private data class to hold the runtime state of a specific tile's animation.
    private data class AnimationRuntimeState(
        var currentFrameIndex: Int = 0,
        var elapsedTime: Float = 0f
    )

    // A map to store the runtime state for each animated GID.
    // The key is the Global ID (GID) of the animated tile.
    private val animationStates = mutableMapOf<Int, AnimationRuntimeState>()

    /**
     * The onTick method is now used to update the timers for all active animations.
     */
    override fun onTick() {
        val dt = deltaTime // Get delta time from the system
        // Update every active animation state
        animationStates.values.forEach { state ->
            state.elapsedTime += dt * 1000 // Convert delta time (seconds) to milliseconds
        }
        // The IteratingSystem's onTick will then call onTickEntity, but we won't use it.

        cameraService.culler.getBounds(visibleWorldRect)
    }

    /**
     * The main onRender method sets up the camera and then calls the entity-specific rendering.
     */
    override fun onRender(drawScope: DrawScope) {
        val cameraEntity = cameraService.mainCameraEntity
        if (cameraEntity != null) {
            val camera = cameraEntity[Camera]
            val camTrans = cameraEntity[Transform]
            val camShake = cameraEntity.getOrNull(CameraShake)

            drawScope.withCameraTransform(camera, camTrans, camShake) {
                // Call super.onRender() which will trigger onRenderEntity for each map.
                super.onRender(this)
            }
        } else {
            super.onRender(drawScope)
        }
    }

    /**
     * Renders a single map entity, now with animation logic integrated.
     */
    override fun onRenderEntity(entity: Entity, drawScope: DrawScope) {
        val transform = entity[Transform]
        val tiledMap = entity[TiledMap].data

        val mapLeft = transform.position.x
        val mapTop = transform.position.y
        val tileWidth = tiledMap.tileWidth.toFloat()
        val tileHeight = tiledMap.tileHeight.toFloat()

        if (tileWidth <= 0f || tileHeight <= 0f) return

        val startCol = floor((visibleWorldRect.left - mapLeft) / tileWidth).toInt().coerceAtLeast(0)
        val endCol = floor((visibleWorldRect.right - mapLeft) / tileWidth).toInt().coerceAtMost(tiledMap.columns - 1)
        val startRow = floor((visibleWorldRect.top - mapTop) / tileHeight).toInt().coerceAtLeast(0)
        val endRow = floor((visibleWorldRect.bottom - mapTop) / tileHeight).toInt().coerceAtMost(tiledMap.rows - 1)

        tiledMap.mapLayers.forEach { layer ->
            if (!layer.visible || layer !is TiledMapTileLayer) return@forEach

            for (row in startRow..endRow) {
                for (col in startCol..endCol) {
                    val gid = layer.getGid(col, row)
                    if (gid == 0) continue

                    val mapSet = tiledMap.getMapSet(gid)
                    val tile = tiledMap.getTile(gid)

                    val tileToRenderId = when (tile) {
                        is AnimatedTiledMapTile -> {
                            // Get or create the runtime state for this animated tile.
                            val animState = animationStates.getOrPut(gid) { AnimationRuntimeState() }
                            val currentFrame = tile.frames[animState.currentFrameIndex]

                            // Check if it's time to switch to the next frame.
                            if (animState.elapsedTime >= currentFrame.duration) {
                                animState.elapsedTime -= currentFrame.duration
                                animState.currentFrameIndex = (animState.currentFrameIndex + 1) % tile.frames.size
                            }
                            // Return the localId of the frame that should be currently displayed.
                            tile.frames[animState.currentFrameIndex].id
                        }
                        is StaticTiledMapTile -> {
                            tile.id // tile.id should be the same as localId
                        }
                    }

                    val srcRect = mapSet.getTileSourceRect(tileToRenderId, tiledMap)
                    val dstLeft = mapLeft + col * tileWidth
                    val dstTop = mapTop + row * tileHeight
                    val dstOffset =
                        IntOffset(dstLeft.toInt(), dstTop.toInt())

                    drawScope.drawImage(
                        image = mapSet.image,
                        srcOffset = srcRect.topLeft,
                        srcSize = srcRect.size,
                        dstOffset = dstOffset,
                        dstSize = srcRect.size
                    )
                }
            }
        }
    }
}
