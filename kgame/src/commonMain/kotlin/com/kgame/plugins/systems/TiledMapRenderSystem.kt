package com.kgame.plugins.systems

import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.roundToIntSize
import androidx.compose.ui.unit.toIntSize
import com.kgame.ecs.Entity
import com.kgame.ecs.IteratingSystem
import com.kgame.ecs.SystemPriority
import com.kgame.ecs.World.Companion.family
import com.kgame.ecs.World.Companion.inject
import com.kgame.engine.geometry.roundToIntOffset
import com.kgame.engine.geometry.toIntOffset
import com.kgame.engine.graphics.drawscope.withCameraTransform
import com.kgame.engine.graphics.drawscope.withCenteredTransform
import com.kgame.engine.maps.AnimatedTiledMapTile
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
    private val animationService: AnimationService = inject(),
    priority: SystemPriority = SystemPriorityAnchors.Render
) : IteratingSystem(
    family = family { all(TiledMap) },
    priority = priority
) {

    companion object {
        var isDebugging: Boolean = false

        private val DebugStroke = Stroke(2f)
    }

    private val frustumRect = MutableRect(0f, 0f, 0f, 0f)

    private val clipRect = MutableRect(0f, 0f, 0f, 0f)

    private val boundsRect = MutableRect(0f, 0f, 0f, 0f)


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
            drawScope.withCenteredTransform {
                super.onRender(this)
            }
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
            val backgroundColor = tiledMap.backgroundColor
            if (backgroundColor != Color.Transparent) {
                drawRect(
                    color = backgroundColor,
                    topLeft = -tiledMap.size.center,
                    size = tiledMap.size
                )
            }

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

            tiledMap.getBounds(boundsRect, tileIndex - 1, gid)
            if (!frustumRect.overlaps(boundsRect)) continue

            val tileset = tiledMap.findMapSet(gid) ?: continue
            val tile = tileset.findTile(gid) ?: continue

            val finalGid = if (tile is AnimatedTiledMapTile) {
                animationService.getCurrentFrame(tile).id
            } else {
                tile.id
            }

            tileset.getClip(clipRect, finalGid)

            drawTiledMapTileImage(tileset.image, layer.opacity, finalGid)
        }
    }

    private fun DrawScope.drawTiledMapGroupLayer(
        layer: TiledMapGroupLayer,
        tiledMap: TiledMapData
    ) {
        var index = 0
        while (index < layer.layers.size) {
            val childLayer = layer.layers[index++]
            drawTiledMapLayer(childLayer, tiledMap)
        }
    }

    private fun DrawScope.drawTiledMapImageLayer(
        layer: TiledMapImageLayer,
        tiledMap: TiledMapData
    ) {
        layer.getBounds(boundsRect)
        if (!frustumRect.overlaps(boundsRect)) {
            return
        }

        drawTiledMapImage(layer.image, srcRect = null, dstRect = boundsRect, alpha = layer.opacity)
    }

    private fun DrawScope.drawTiledMapObjectLayer(
        layer: TiledMapObjectLayer,
        tiledMap: TiledMapData
    ) {
        var index = 0
        while (index < layer.objects.size) {
            when (val obj = layer.objects[index++]) {
                is TiledMapTileObject -> drawTiledMapTileObject(obj, layer, tiledMap)
                is TiledMapShapeObject -> {
                    if (isDebugging) {
                        drawTiledMapShapeObject(obj)
                    }
                }
            }
        }
    }

    private fun DrawScope.drawTiledMapTileObject(
        obj: TiledMapTileObject,
        layer: TiledMapObjectLayer,
        tiledMap: TiledMapData
    ) {
        obj.getBounds(boundsRect)
        if (!frustumRect.overlaps(boundsRect)) {
            return
        }

        val tileset = tiledMap.findMapSet(obj.gid) ?: return
        val tile = tileset.findTile(obj.gid) ?: return

        val finalGid = if (tile is AnimatedTiledMapTile) {
            animationService.getCurrentFrame(tile).id
        } else {
            tile.id
        }

        tileset.getClip(clipRect, finalGid)

        drawTiledMapTileImage(tileset.image, layer.opacity, finalGid)
    }

    private fun DrawScope.drawTiledMapShapeObject(obj: TiledMapShapeObject) {
        obj.getBounds(boundsRect)
        if (!frustumRect.overlaps(boundsRect)) {
            return
        }

        drawTiledMapShapeObject(obj, Color.Magenta)
    }

    private fun DrawScope.drawTiledMapTileImage(
        image: ImageBitmap,
        alpha: Float,
        gid: Int
    ) {
        val hFlip = TiledMapData.isFlippedHorizontally(gid)
        val vFlip = TiledMapData.isFlippedVertically(gid)
        val dFlip = TiledMapData.isFlippedDiagonally(gid)

        if (hFlip || vFlip || dFlip) {
            withTransform({
                val center = Offset(
                    x = boundsRect.left + boundsRect.width / 2f,
                    y = boundsRect.top + boundsRect.height / 2f
                )

                // Tiled flip order：Diagonal -> Horizontal -> Vertical
                if (dFlip) {
                    rotate(90f, pivot = center)
                    scale(scaleX = -1f, scaleY = 1f, pivot = center)
                }
                if (hFlip) scale(scaleX = -1f, scaleY = 1f, pivot = center)
                if (vFlip) scale(scaleX = 1f, scaleY = -1f, pivot = center)
            }) {
                drawTiledMapImage(image, srcRect = clipRect, dstRect = boundsRect, alpha = alpha)
            }
        } else {
            drawTiledMapImage(image, srcRect = clipRect, dstRect = boundsRect, alpha = alpha)
        }
    }

    private fun DrawScope.drawTiledMapImage(
        image: ImageBitmap,
        srcRect: MutableRect?,
        dstRect: MutableRect,
        alpha: Float
    ) {
        drawImage(
            image = image,
            srcOffset = srcRect?.topLeft?.roundToIntOffset() ?: IntOffset.Zero,
            srcSize = srcRect?.size?.roundToIntSize() ?: IntSize(image.width, image.height),
            dstOffset = dstRect.topLeft.toIntOffset(),
            dstSize = IntSize(
                (dstRect.width + 1f).toInt(),
                (dstRect.height + 1f).toInt()
            ),
            alpha = alpha
        )
    }

    private fun DrawScope.drawTiledMapShapeObject(
        obj: TiledMapShapeObject,
        color: Color
    ) {
        translate(obj.position.x, obj.position.y) {
            when (val shape = obj.shape) {
                is TiledMapShape.Rectangle -> {
                    drawRect(
                        color = color,
                        topLeft = shape.offset,
                        size = shape.size,
                        style = if (obj.isSolid) Fill else DebugStroke
                    )
                }

                is TiledMapShape.Ellipse -> {
                    drawOval(
                        color = color,
                        topLeft = shape.offset,
                        size = shape.size,
                        style = if (obj.isSolid) Fill else DebugStroke
                    )
                }

                is TiledMapShape.Polygon -> {
                    drawPath(
                        path = shape.path,
                        color = color,
                        style = if (obj.isSolid) Fill else DebugStroke
                    )
                }

                is TiledMapShape.Polyline -> {
                    drawPath(
                        path = shape.path,
                        color = color,
                        style = DebugStroke
                    )
                }

                is TiledMapShape.Point -> {
                    drawCircle(
                        color = color,
                        radius = 4f,
                        center = shape.offset,
                        style = DebugStroke
                    )
                }
            }
        }
    }

}


