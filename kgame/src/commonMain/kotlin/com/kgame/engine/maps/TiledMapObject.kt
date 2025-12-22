package com.kgame.engine.maps

import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import kotlin.getValue

/**
 * [Map Object]
 * Describes a single object's position, size, and properties within an object layer.
 */
interface TiledMapObject {
    val id: Int
    val name: String
    val type: String
    val visible: Boolean
    val properties: Map<String, String>

    val position: Offset

    fun getBounds(bounds: MutableRect)

}

/**
 * [Tile Object]
 * An object that references a specific tile from a tileset.
 * Note: In Tiled, the offset usually points to the BOTTOM-LEFT of the tile.
 */
data class TiledMapTileObject(
    override val id: Int,
    override val name: String,
    override val type: String,
    override val visible: Boolean,
    override val properties: Map<String, String>,
    override val position: Offset,
    val size: Size,
    val gid: Int,
) : TiledMapObject {

    override fun getBounds(bounds: MutableRect) {
        bounds.set(position.x, position.y, position.x + size.width, position.y + size.height)
    }

}

/**
 * [Shape Object]
 * A geometric object such as a Rectangle, Ellipse, or Polygon.
 * Note: The offset usually points to the TOP-LEFT of the bounding box.
 */
data class TiledMapShapeObject(
    override val id: Int,
    override val name: String,
    override val type: String,
    override val visible: Boolean,
    override val properties: Map<String, String>,
    override val position: Offset,
    val shape: TiledMapShape
) : TiledMapObject {

    val size: Size get() = shape.size

    val isSolid: Boolean get() = type == "solid"

    val isPlatform: Boolean get() = type == "platform"

    override fun getBounds(bounds: MutableRect) {
        val x = position.x + shape.offset.x
        val y = position.y + shape.offset.y
        bounds.set(x, y, x + shape.size.width, y + shape.size.height)
    }

}

sealed interface TiledMapShape {
    val offset: Offset
        get() = Offset.Zero
    val size: Size

    data class Rectangle(override val size: Size) : TiledMapShape

    data class Ellipse(override val size: Size) : TiledMapShape

    data class Polyline(val points: List<Offset>) : TiledMapShape {
        override val offset: Offset
        override val size: Size

        init {
            val bounds = calculateBounds(points)
            offset = bounds.topLeft
            size = bounds.size
        }

        val path: Path by lazy {
            Path().apply {
                points.forEachIndexed { index, point ->
                    if (index == 0) moveTo(point.x, point.y)
                    else lineTo(point.x, point.y)
                }
            }
        }
    }

    data class Polygon(val points: List<Offset>) : TiledMapShape {
        override val offset: Offset
        override val size: Size

        init {
            val bounds = calculateBounds(points)
            offset = bounds.topLeft
            size = bounds.size
        }

        val path: Path by lazy {
            Path().apply {
                points.forEachIndexed { index, point ->
                    if (index == 0) moveTo(point.x, point.y)
                    else lineTo(point.x, point.y)
                }
                close()
            }
        }
    }

    data object Point : TiledMapShape {
        override val size: Size = Size.Zero
    }
}

private fun calculateBounds(points: List<Offset>): Rect {
    if (points.isEmpty()) return Rect.Zero
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY

    for (p in points) {
        if (p.x < minX) minX = p.x
        if (p.y < minY) minY = p.y
        if (p.x > maxX) maxX = p.x
        if (p.y > maxY) maxY = p.y
    }
    return Rect(Offset(minX, minY), Size(maxX - minX, maxY - minY))
}