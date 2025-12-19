@file:Suppress("ConvertTwoComparisonsToRangeCheck", "ArrayInDataClass")

package com.kgame.engine.maps

import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path

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
    val tileSize: Size,
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

        fun getRealGid(gid: Int): Int {
            // Clear flags (flipped horizontally, vertically, diagonally)
            return gid and 0x1FFFFFFF
        }
    }

    val size: Size = Size(columns * tileSize.width, rows * tileSize.height)

    /**
     * Gets the [TiledMapSet] that a given Global ID (GID) belongs to.     * This follows a 'get' contract: it expects the GID to be valid.
     *
     * @return Returns null if no tileset contains the given GID.
     */
    fun findMapSet(gid: Int): TiledMapSet? {
        val realGid = getRealGid(gid)
        var index = tilesets.size - 1
        while (index >= 0) {
            val mapSet = tilesets[index--]
            if (realGid >= mapSet.firstGid) {
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
        val realGid = getRealGid(gid)
        if (realGid == 0) return null
        val mapSet = findMapSet(gid) ?: return null
        val localId = realGid - mapSet.firstGid
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
        val realGid = getRealGid(gid)
        if (realGid == 0) return null

        val mapSet = findMapSet(realGid) ?: return null

        mapSet.getClip(outRect, realGid)

        return mapSet
    }

    /**
     * Returns the **world-space** top-left corner of the tile at the given
     * flattened layer index, relative to the **map center**.
     *
     * @param index Zero-based position in the layer's `data` array.
     * @return Pixel offset relative to the map center.
     */
    fun getOffset(index: Int): Offset {
        val col = index % columns
        val row = index / columns
        return Offset(
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
            left   = left,
            top    = top,
            right  = left + tileWidth,
            bottom = top + tileHeight
        )
    }
    /**
     * Computes the visual bounding box of a tile in world space.
     * * If [gid] is 0 or no tileset is found, it defaults to the grid cell bounds.
     * This single method covers rendering, culling, and logical interaction.
     */
    fun getBounds(bounds: MutableRect, index: Int, gid: Int) {
        // Basic Grid Calculation (Internal logic)
        val gridX = (index % columns) * tileSize.width - size.width / 2f
        val gridY = (index / columns) * tileSize.height - size.height / 2f

        val mapSet = findMapSet(gid)
        if (mapSet == null) {
            // Fallback: Just return the grid square
            bounds.set(gridX, gridY, gridX + tileSize.width, gridY + tileSize.height)
            return
        }

        // Tiled alignment logic: Left-aligned and Bottom-aligned
        val (visW, visH) = mapSet.tileSize
        val drawX = gridX + mapSet.offset.x
        val drawY = (gridY + tileSize.height) - visH + mapSet.offset.y

        bounds.set(drawX, drawY, drawX + visW, drawY + visH)
    }
}

/**
 * Describes a tileset, which is a collection of tiles from a single texture atlas.
 *
 * @param firstGid The starting global ID (GID) of a tile in this tileset.
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
    val firstGid: Int,
    val name: String,
    val tileSize: Size,
    val spacing: Float,
    val margin: Float,
    val offset: Offset,
    val size: Size,
    val image: ImageBitmap,
    val terrains: Map<String, Int>,
    val tiles: Map<Int, TiledMapTile>,
) {
    // Calculate how many columns and rows fit in the image
    // Formula: (ImageDimension - 2 * margin + spacing) / (tileSize + spacing)
    val columns: Int = ((size.width - 2 * margin + spacing) / (tileSize.width + spacing)).toInt().coerceAtLeast(1)

    // Note: Usually tilesets provide a 'tilecount' attribute.
    // If you have 'tilecount', use: rows = ceil(tilecount / columns)
    // If not, calculate based on image height:
    val rows: Int = ((size.height - 2 * margin + spacing) / (tileSize.height + spacing)).toInt().coerceAtLeast(1)


    /**
     * Calculates the source rectangle using the Global ID (GID).
     * @param outRect The rectangle to be populated with source coordinates.
     * @param gid The raw GID from map data (should be pre-stripped of flags).
     */
    fun getClip(outRect: MutableRect, gid: Int) {
        // Convert Global ID to Local ID
        val localId = gid - firstGid

        val tw = tileSize.width
        val th = tileSize.height

        // Calculate grid position
        val col = localId % columns
        val row = localId / columns

        // Calculate source coordinates: margin + index * (size + spacing)
        val left = margin + col * (tw + spacing)
        val top = margin + row * (th + spacing)

        outRect.set(left, top, left + tw, top + th)
    }

}

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
    val offset: Offset,
    val size: Size,
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

    val offset: Offset
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
    override val offset: Offset,
    val size: Size,
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
    override val offset: Offset,
    val shape: TiledMapShape
) : TiledMapObject

sealed interface TiledMapShape {
    val size: Size

    data class Rectangle(override val size: Size) : TiledMapShape

    data class Ellipse(override val size: Size) : TiledMapShape

    data class Polygon(val points: List<Offset>) : TiledMapShape {
        override val size: Size = if (points.isEmpty()) {
            Size.Zero
        } else {
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE

            for (point in points) {
                minX = minOf(minX, point.x)
                minY = minOf(minY, point.y)
                maxX = maxOf(maxX, point.x)
                maxY = maxOf(maxY, point.y)
            }

            Size(maxX - minX, maxY - minY)
        }

        val path: Path by lazy {
            Path().apply {
                for ((index, point) in points.withIndex()) {
                    if (index == 0) moveTo(point.x, point.y)
                    else lineTo(point.x, point.y)
                }
                close()
            }
        }
    }

    object Point : TiledMapShape {
        override val size: Size = Size.Zero
    }
}