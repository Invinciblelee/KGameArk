package com.kgame.plugins.systems

import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kgame.ecs.Entity
import com.kgame.ecs.IteratingSystem
import com.kgame.ecs.World.Companion.family
import com.kgame.ecs.World.Companion.inject
import com.kgame.engine.graphics.drawscope.withCameraTransform
import com.kgame.engine.maps.AnimatedTiledMapTile
import com.kgame.engine.maps.StaticTiledMapTile
import com.kgame.engine.maps.TiledMapAnimationState
import com.kgame.engine.maps.TiledMapTileLayer
import com.kgame.plugins.components.Camera
import com.kgame.plugins.components.CameraShake
import com.kgame.plugins.components.TiledMap
import com.kgame.plugins.components.Transform
import com.kgame.plugins.services.CameraService
import kotlin.math.roundToInt

/**
 * A system responsible for rendering Tiled Maps, with built-in handling for tile animations.
 */
class TiledMapRenderSystem(
    private val cameraService: CameraService = inject()
) : IteratingSystem(
    family = family { all(TiledMap) }
) {

    private val animationState = TiledMapAnimationState()

    private val clipRect = MutableRect(0f, 0f, 0f, 0f)

    init {
        family.forEach { entity ->
            entity.configure {
               val data = entity[TiledMap].data
               val transform = addIfAbsent(Transform) { Transform() }
               transform.position
            }
        }
    }

    /**
     * The onTick method is now used to update the timers for all active animations.
     */
    override fun onTick() {
        // Update every active animation state
        animationState.update(deltaTime)
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
     * Renders a single map entity. This version is optimized to use `while` loops
     * instead of `forEach` to minimize object allocations in the render loop.
     */
    override fun onRenderEntity(entity: Entity, drawScope: DrawScope) {
        val tiledMap = entity[TiledMap].data

        var layerIndex = 0
        while (layerIndex < tiledMap.layers.size) {
            val layer = tiledMap.layers[layerIndex++]

            if (!layer.visible) {
                continue
            }

            if (layer is TiledMapTileLayer) {
                var tileIndex = 0
                while (tileIndex < layer.data.size) {
                    val gid = layer.data[tileIndex++]

                    if (gid == 0) {
                        continue
                    }

                    val tile = tiledMap.findTile(gid) ?: continue

                    val finalGid: Int = when (tile) {
                        is StaticTiledMapTile -> gid
                        is AnimatedTiledMapTile -> {
                            val currentFrame = animationState.getCurrentFrame(tile)
                            currentFrame.id
                        }
                    }

                    val tileset = tiledMap.getClip(finalGid, clipRect) ?: continue

                    val index = tileIndex - 1
                    val tileX = index % layer.width
                    val tileY = index / layer.width

                    drawScope.drawImage(
                        image = tileset.image,
                        srcOffset = IntOffset(
                            clipRect.left.roundToInt(),
                            clipRect.top.roundToInt()
                        ),
                        srcSize = IntSize(
                            clipRect.width.roundToInt(),
                            clipRect.height.roundToInt()
                        ),
                        dstOffset = IntOffset(
                            tileX * tiledMap.tileWidth,
                            tileY * tiledMap.tileHeight
                        )
                    )
                }
            }
        }
    }


}


