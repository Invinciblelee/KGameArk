@file:Suppress("ConvertTwoComparisonsToRangeCheck", "ArrayInDataClass")

package com.kgame.engine.maps

import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontVariation.width
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Represents a complete Tiled map. This is the generic, core data structure
 * used within the engine to represent a map.
 *
 * @param columns The total columns of the map in number of tiles.
 * @param rows The total rows of the map in number of tiles.
 * @param tileSize The render size of a single tile in pixels.
 * @param layers A list of all layers in the map, ordered by their rendering sequence.
 * @param tilesets A list of all tilesets used by this map.
 * @param properties Custom properties defined for the map.
 */
data class TiledMapData(
    val columns: Int,
    val rows: Int,
    val tileSize: IntSize,
    val layers: List<TiledMapLayer>,
    val tilesets: List<TiledMapSet>,
    val properties: Map<String, String> = emptyMap()
) {
    companion object {
        /**
         * A bitmask used to clear all flipping and rotation flags from a Tiled GID.
         * The Tiled editor uses the top 3 bits of the GID for these flags.
         * This constant is the binary inversion of those three bits.
         */
        const val FLIPPED_MASKS = (0x80000000 or 0x40000000 or 0x20000000).toInt()

        /** Flag for horizontal flipping. */
        const val FLIPPED_HORIZONTALLY_FLAG = 0x80000000.toInt()

        /** Flag for vertical flipping. */
        const val FLIPPED_VERTICALLY_FLAG = 0x40000000

        /** Flag for diagonal flipping (rotation). */
        const val FLIPPED_DIAGONALLY_FLAG = 0x20000000
    }

    val size: IntSize = IntSize(columns * tileSize.width, rows * tileSize.height)

    /**
     * Gets the [TiledMapSet] that a given Global ID (GID) belongs to.     * This follows a 'get' contract: it expects the GID to be valid.
     *
     * @return Returns null if no tileset contains the given GID.
     */
    fun findMapSet(gid: Int): TiledMapSet? {
        var index = tilesets.size - 1
        while (index >= 0) {
            val mapSet = tilesets[index--]
            if (gid >= mapSet.id) {
                return mapSet
            }
        }
        return null
    }

    /**
     * Gets the special tile metadata [TiledMapTile] for a given Global ID (GID).
     * This follows a 'get' contract: it expects a tile with special properties to exist for this GID.
     * If a GID is valid but simply has no special properties, the `TiledMapSet.tiles` map
     * will not contain its local ID, and this function will throw an exception.
     *
     */
    fun findTile(gid: Int): TiledMapTile? {
        val mapSet = findMapSet(gid) ?: return null
        val localId = gid - mapSet.id
        return mapSet.tiles.getOrElse(localId) { EmptyTiledMapTile }
    }

    /**
     * Calculates the source clipping rectangle for a given Global ID (GID) and stores the result
     * in the provided [outRect]. This avoids creating new Rect objects on each call, which is
     * crucial for performance during rendering.
     *
     * @param outRect A mutable [MutableRect] object where the calculated clipping rectangle will be stored.
     * @param gid The Global ID of the tile.
     * @return The [TiledMapSet] that the GID belongs to, or null if the GID is invalid.
     */
    fun getClip(outRect: MutableRect, gid: Int): TiledMapSet? {
        val mapSet = findMapSet(gid) ?: return null

        // Use standardized IntSize and IntOffset values
        val (tileW, tileH) = mapSet.tileSize
        val (imgW, imgH) = mapSet.size
        val spacing = mapSet.spacing
        val margin = mapSet.margin

        val localId = gid - mapSet.id

        // 1. Calculate columns based on image size and spacing
        // Formula: (TotalWidth - 2 * Margin + Spacing) / (TileWidth + Spacing)
        // Tiled standard: Usually only one margin is applied at the start.
        val columns = (imgW - margin + spacing) / (tileW + spacing)

        val col = localId % columns
        val row = localId / columns

        // 2. Calculate coordinates
        val left = margin + col * (tileW + spacing)
        val top = margin + row * (tileH + spacing)

        outRect.set(
            left = left.toFloat(),
            top = top.toFloat(),
            right = (left + tileW).toFloat(),
            bottom = (top + tileH).toFloat()
        )

        return mapSet
    }

    /**
     * Returns the **world-space** top-left corner of the tile at the given
     * flattened layer index, relative to the **map center**.
     *
     * @param index Zero-based position in the layer's `data` array.
     * @return Pixel offset relative to the map center.
     */
    fun getOffset(index: Int): IntOffset {
        val col = index % columns
        val row = index / columns
        return IntOffset(
            col * tileSize.width - size.width / 2,
            row * tileSize.height - size.height / 2
        )
    }

    /**
     * Computes the **world-space** bounding rectangle of the tile at
     * [index] and stores it in [bounds]. The rectangle is expressed
     * relative to the **map center**.
     *
     * @param bounds Reusable rectangle to be populated; caller-owned.
     * @param index  Zero-based position in the layer's `data` array.
     */
    fun getBounds(bounds: MutableRect, index: Int) {
        val (tileWidth, tileHeight) = tileSize
        val col = index % columns
        val row = index / columns
        val left = col * tileWidth - size.width / 2
        val top  = row * tileHeight - size.height / 2
        bounds.set(
            left   = left.toFloat(),
            top    = top.toFloat(),
            right  = (left + tileWidth).toFloat(),
            bottom = (top + tileHeight).toFloat()
        )
    }

}

/**
 * Describes a tileset, which is a collection of tiles from a single texture atlas.
 *
 * @param id The starting global ID (GID) of a tile in this tileset.
 * @param name The name of the tileset.
 * @param tileSize The render size of a single tile in pixels.
 * @param spacing The spacing between tiles in the tileset image.
 * @param margin The margin around the tiles in the tileset image.
 * @param offset The offset to apply to tiles when rendering.
 * @param size The total size in pixels.
 * @param image The tileset's source image.
 * @param terrains A map of terrain names to their corresponding tile IDs.
 * @param tiles A map of tile IDs to their metadata.
 */
data class TiledMapSet(
    val id: Int,
    val name: String,
    val tileSize: IntSize,
    val spacing: Int,
    val margin: Int,
    val offset: IntOffset,
    val size: IntSize,
    val image: ImageBitmap,
    val terrains: Map<String, Int>,
    val tiles: Map<Int, TiledMapTile>,
)

/**
 * [Tile Metadata Abstraction]
 * A sealed interface representing the metadata for a single tile, distinguishing
 * between a static tile and an animated one. This provides strong type-safety.
 */
sealed interface TiledMapTile {
    /** The local ID of the tile within its tileset. */
    val id: Int

    /** Custom properties defined for this specific tile. */
    val properties: Map<String, String>
}

/**
 * Represents a [Static Tile]. It contains no animation information.
 */
data class StaticTiledMapTile(
    override val id: Int,
    override val properties: Map<String, String> = emptyMap()
) : TiledMapTile

/**
 * Represents an [Animated Tile]. It contains a list of animation frames.
 */
data class AnimatedTiledMapTile(
    override val id: Int,
    override val properties: Map<String, String> = emptyMap(),
    val frames: List<TiledMapAnimationFrame>
) : TiledMapTile {

    val duration: Int by lazy { frames.sumOf { it.duration } }

}

data object EmptyTiledMapTile : TiledMapTile {
    override val id: Int = -1
    override val properties: Map<String, String> = emptyMap()
}

/**
 * [Animation Frame]
 * Describes a single frame within a tile animation.
 *
 * @param id The local ID of the tile to be displayed for this frame.
 * @param duration The duration of this frame in milliseconds.
 */
data class TiledMapAnimationFrame(
    val id: Int,
    val duration: Int
)

/**
 * [Layer Abstraction]
 * A sealed interface to support different types of map layers.
 */
sealed interface TiledMapLayer {
    val name: String
    val color: Color
    val opacity: Float
    val visible: Boolean
    val properties: Map<String, String>
}

/**
 * [Tile Layer]
 * Stores grid-based tile data.
 *
 * @param data An IntArray storing the GID for each tile, which is the most performant option.
 */
data class TiledMapTileLayer(
    override val name: String,
    override val color: Color,
    override val opacity: Float,
    override val visible: Boolean,
    override val properties: Map<String, String>,
    val columns: Int,
    val rows: Int,
    val data: IntArray
) : TiledMapLayer {
    /**
     * Safely retrieves the Global ID at the given grid coordinates. Returns 0 if out of bounds.
     */
    fun getGid(x: Int, y: Int): Int {
        if (x < 0 || x >= columns || y < 0 || y >= rows) return 0
        return data[y * columns + x]
    }

}

/**
 * [Image Layer]
 * Represents a layer that contains a single, standalone image, often used for backgrounds or foreground elements.
 * Unlike tile layers, it is not based on grid data from a tileset.
 */
data class TiledMapImageLayer(
    override val name: String,
    override val color: Color,
    override val opacity: Float,
    override val visible: Boolean,
    override val properties: Map<String, String>,
    val offset: IntOffset,
    val size: IntSize,
    val image: ImageBitmap,
) : TiledMapLayer

/**
 * [Group Layer]
 * Represents a container for organizing multiple map layers (tile, object, or image layers).
 * This allows layers to be managed together (e.g., toggled visible/invisible).
 */
data class TiledMapGroupLayer(
    override val name: String,
    override val color: Color,
    override val opacity: Float,
    override val visible: Boolean,
    override val properties: Map<String, String>,
    val layers: List<TiledMapLayer> // A list of all child layers contained within this group. (包含子图层)
) : TiledMapLayer

/**
 * [Object Layer]
 * Stores free-floating game objects like spawn points, collision areas, etc.
 */
data class TiledMapObjectLayer(
    override val name: String,
    override val color: Color,
    override val opacity: Float,
    override val visible: Boolean,
    override val properties: Map<String, String>,
    val objects: List<TiledMapObject>
) : TiledMapLayer

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

    val offset: IntOffset
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
    override val offset: IntOffset,
    val size: IntSize,
    val gid: Int,
) : TiledMapObject

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
    override val offset: IntOffset,
    val shape: TiledMapShape
) : TiledMapObject

sealed interface TiledMapShape {
    val size: IntSize

    data class Rectangle(override val size: IntSize) : TiledMapShape

    data class Ellipse(override val size: IntSize) : TiledMapShape

    data class Polygon(val points: List<IntOffset>) : TiledMapShape {
        override val size: IntSize = if (points.isEmpty()) {
            IntSize.Zero
        } else {
            var minX = Int.MAX_VALUE
            var minY = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE
            var maxY = Int.MIN_VALUE

            for (point in points) {
                minX = minOf(minX, point.x)
                minY = minOf(minY, point.y)
                maxX = maxOf(maxX, point.x)
                maxY = maxOf(maxY, point.y)
            }

            IntSize(maxX - minX, maxY - minY)
        }

        val path: Path by lazy {
            Path().apply {
                for ((index, point) in points.withIndex()) {
                    if (index == 0) moveTo(point.x.toFloat(), point.y.toFloat())
                    else lineTo(point.x.toFloat(), point.y.toFloat())
                }
                close()
            }
        }
    }

    object Point : TiledMapShape {
        override val size: IntSize = IntSize.Zero
    }
}