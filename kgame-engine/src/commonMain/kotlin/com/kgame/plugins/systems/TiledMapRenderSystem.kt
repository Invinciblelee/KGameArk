package com.kgame.plugins.systems

import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.roundToIntSize
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize
import com.kgame.ecs.Entity
import com.kgame.ecs.IteratingSystem
import com.kgame.ecs.World.Companion.family
import com.kgame.ecs.World.Companion.inject
import com.kgame.engine.geometry.roundToIntOffset
import com.kgame.engine.graphics.drawscope.withCameraTransform
import com.kgame.engine.maps.AnimatedTiledMapTile
import com.kgame.engine.maps.EmptyTiledMapTile
import com.kgame.engine.maps.StaticTiledMapTile
import com.kgame.engine.maps.TiledMapData
import com.kgame.engine.maps.TiledMapGroupLayer
import com.kgame.engine.maps.TiledMapImageLayer
import com.kgame.engine.maps.TiledMapLayer
import com.kgame.engine.maps.TiledMapObjectLayer
import com.kgame.engine.maps.TiledMapShape
import com.kgame.engine.maps.TiledMapShapeObject
import com.kgame.engine.maps.TiledMapTileLayer
import com.kgame.engine.maps.TiledMapTileObject
import com.kgame.plugins.components.Camera
import com.kgame.plugins.components.CameraShake
import com.kgame.plugins.components.TiledMap
import com.kgame.plugins.components.Transform
import com.kgame.plugins.components.WorldBounds
import com.kgame.plugins.services.AnimationService
import com.kgame.plugins.services.CameraService

/**
 * A system responsible for rendering Tiled Maps, with built-in handling for tile animations.
 */
class TiledMapRenderSystem(
    private val cameraService: CameraService = inject(),
    private val animationService: AnimationService = inject()
) : IteratingSystem(
    family = family { all(TiledMap) }
) {

    private val clipRect = MutableRect(0f, 0f, 0f, 0f)

    private val boundsRect = MutableRect(0f, 0f, 0f, 0f)

    private val frustumRect = MutableRect(0f, 0f, 0f, 0f)

    /**
     * The onTick method is now used to update the timers for all active animations.
     */
    override fun onTick(deltaTime: Float) {
        cameraService.culler.getBounds(frustumRect)
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
        val worldBounds = entity.getOrNull(WorldBounds)
        val worldCenter = worldBounds?.rect?.center ?: Offset.Zero

        drawScope.translate(worldCenter.x, worldCenter.y) {
            var layerIndex = 0
            while (layerIndex < tiledMap.layers.size) {
                val layer = tiledMap.layers[layerIndex++]

                if (!layer.visible) {
                    continue
                }

                drawTiledMapLayer(layer, tiledMap)
            }
        }
    }

    private fun DrawScope.drawTiledMapLayer(layer: TiledMapLayer, tiledMap: TiledMapData) {
        when (layer) {
            is TiledMapTileLayer -> drawTiledMapTileLayer(layer, tiledMap)
            is TiledMapGroupLayer -> drawTiledMapGroupLayer(layer, tiledMap)
            is TiledMapImageLayer -> drawTiledMapImageLayer(layer, tiledMap)
            is TiledMapObjectLayer -> drawTiledMapObjectLayer(layer, tiledMap)
        }
    }

    private fun DrawScope.drawTiledMapTileLayer(layer: TiledMapTileLayer, tiledMap: TiledMapData) {
        var tileIndex = 0
        while (tileIndex < layer.data.size) {
            val gid = layer.data[tileIndex++]
            if (gid == 0) continue

            tiledMap.getBounds(boundsRect, tileIndex - 1)
            if (!frustumRect.overlaps(boundsRect)) continue

            val tile = tiledMap.findTile(gid) ?: continue

            val finalGid = when (tile) {
                is StaticTiledMapTile, is EmptyTiledMapTile -> gid
                is AnimatedTiledMapTile -> {
                    val currentFrame = animationService.getCurrentFrame(tile)
                    currentFrame.id
                }
            }

            val tileset = tiledMap.getClip(clipRect, finalGid) ?: continue

            drawImage(
                image = tileset.image,
                srcOffset = clipRect.topLeft.roundToIntOffset(),
                srcSize = clipRect.size.roundToIntSize(),
                dstOffset = boundsRect.topLeft.roundToIntOffset()
            )
        }
    }

    private fun DrawScope.drawTiledMapGroupLayer(layer: TiledMapGroupLayer, tiledMap: TiledMapData) {
        var index = 0
        while (index < layer.layers.size) {
            val childLayer = layer.layers[index++]
            drawTiledMapLayer(childLayer, tiledMap)
        }
    }

    private fun DrawScope.drawTiledMapImageLayer(layer: TiledMapImageLayer, tiledMap: TiledMapData) {
        drawImage(
            image = layer.image,
            dstOffset = layer.offset,
            dstSize = layer.size,
            alpha = 1.0f
        )
    }

    private fun DrawScope.drawTiledMapObjectLayer(layer: TiledMapObjectLayer, tiledMap: TiledMapData) {
        var index = 0
        while (index < layer.objects.size) {
            when(val obj = layer.objects[index++]) {
                is TiledMapTileObject -> drawTiledMapTileObject(obj, tiledMap)
                is TiledMapShapeObject -> drawTiledMapShapeObject(obj, layer)
            }
        }
    }

    private fun DrawScope.drawTiledMapTileObject(obj: TiledMapTileObject, tiledMap: TiledMapData) {
        val tile = tiledMap.findTile(obj.gid) ?: return

        val finalGid = if (tile is AnimatedTiledMapTile) {
            animationService.getCurrentFrame(tile).id
        } else {
            obj.gid
        }

        val tileset = tiledMap.getClip(clipRect, finalGid) ?: return

        val width = (obj.shape as? TiledMapShape.Rectangle)?.width?.toFloat() ?: tileset.tileSize.width.toFloat()
        val height = (obj.shape as? TiledMapShape.Rectangle)?.height?.toFloat() ?: tileset.tileSize.height.toFloat()

        val dstOffset = Offset(
            x = obj.position.x.toFloat(),
            y = (obj.position.y - height) // Tiled 坐标转换
        )


        drawImage(
            image = tileset.image,
            srcOffset = clipRect.topLeft.roundToIntOffset(),
            srcSize = clipRect.size.roundToIntSize(),
            dstOffset = dstOffset.roundToIntOffset(),
            dstSize = IntSize(width.toInt(), height.toInt())
        )
    }

    private fun DrawScope.drawTiledMapShapeObject(obj: TiledMapShapeObject, layer: TiledMapObjectLayer) {
        when (val shape = obj.shape) {
            is TiledMapShape.Rectangle -> {
                drawRect(
                    color = layer.color,
                    topLeft = obj.offset.toOffset(),
                    size = shape.size.toSize(),
                )
            }

            is TiledMapShape.Ellipse -> {
                drawOval(
                    color = layer.color,
                    topLeft = obj.offset.toOffset(),
                    size = shape.size.toSize()
                )
            }

            is TiledMapShape.Polygon -> {
                drawPath(shape.path, layer.color)
            }

            is TiledMapShape.Point -> {
                drawCircle(layer.color, radius = 4f, center = obj.offset.toOffset())
            }
        }
    }

}


