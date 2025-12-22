@file:Suppress("ConvertTwoComparisonsToRangeCheck", "ArrayInDataClass")

package com.kgame.engine.maps

import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.takeOrElse
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import com.kgame.engine.collection.SimpleCollection
import com.kgame.engine.geometry.set

/**
 * Represents a complete Tiled map. This is the generic, core data structure
 * used within the engine to represent a map.
 *
 * @param columns The total columns of the map in number of tiles.
 * @param rows The total rows of the map in number of tiles.
 * @param tileSize The render size of a single tile in pixels.
 * @param backgroundColor The background color of this map.
 * @param layers A list of all layers in the map, ordered by their rendering sequence.
 * @param tilesets A list of all tilesets used by this map.
 * @param properties Custom properties defined for the map.
 */
data class TiledMapData(
    val columns: Int,
    val rows: Int,
    val tileSize: Size,
    val backgroundColor: Color,
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
        const val FLIPPED_MASKS = 0xF0000000.toInt()

        /** Flag for horizontal flipping. */
        const val FLIPPED_HORIZONTALLY_FLAG = 0x80000000.toInt()

        /** Flag for vertical flipping. */
        const val FLIPPED_VERTICALLY_FLAG = 0x40000000

        /** Flag for diagonal flipping (rotation). */
        const val FLIPPED_DIAGONALLY_FLAG = 0x20000000

        /**
         * Extracts the flags of the [gid].
         */
        fun getGidFlags(gid: Int): Int = gid and FLIPPED_MASKS

        /**
         * Extracts the real GID by clearing the flipping and rotation flags.
         */
        fun getRealGid(gid: Int): Int = gid and FLIPPED_MASKS.inv()

        /**
         * Checks if the tile is flipped horizontally.
         */
        fun isFlippedHorizontally(gid: Int): Boolean = (gid and FLIPPED_HORIZONTALLY_FLAG) != 0

        /**
         * Checks if the tile is flipped vertically.
         */
        fun isFlippedVertically(gid: Int): Boolean = (gid and FLIPPED_VERTICALLY_FLAG) != 0

        /**
         * Checks if the tile is flipped diagonally (usually used for 90-degree rotations).
         */
        fun isFlippedDiagonally(gid: Int): Boolean = (gid and FLIPPED_DIAGONALLY_FLAG) != 0
    }

    var isNormalized = false
        internal set

    val size: Size = Size(columns * tileSize.width, rows * tileSize.height)

    val solidObjects: List<TiledMapShapeObject> = layers
        .filterIsInstance<TiledMapObjectLayer>()
        .flatMap { it.objects }
        .filterIsInstance<TiledMapShapeObject>()
        .filterNot { it.shape is TiledMapShape.Point }
        .filter { it.isSolid || it.isPlatform }
        .sortedBy { it.position.x + it.shape.offset.x }

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
        return mapSet.tiles[localId] ?: EmptyTiledMapTile
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
     * Computes the **world-space** bounding rectangle of the tile at
     * [index] and stores it in [outRect]. The rectangle is expressed
     * relative to the **map center**.
     *
     * @param outRect Reusable rectangle to be populated; caller-owned.
     * @param index  Zero-based position in the layer's `data` array.
     */
    fun getCell(outRect: MutableRect, index: Int): Boolean {
        if (index < 0 || index >= columns * rows) {
            return false
        }

        val (tileWidth, tileHeight) = tileSize
        val col = index % columns
        val row = index / columns
        val left = col * tileWidth - size.width / 2f
        val top  = row * tileHeight - size.height / 2f
        outRect.set(
            left   = left,
            top    = top,
            right  = left + tileWidth,
            bottom = top + tileHeight
        )

        return true
    }

    /**
     * Computes the visual bounding box of a tile in world space.
     * * If [gid] is 0 or no tileset is found, it defaults to the grid cell bounds.
     * This single method covers rendering, culling, and logical interaction.
     */
    fun getBounds(bounds: MutableRect, index: Int, gid: Int): Boolean {
        val realGid = getRealGid(gid)
        if (realGid == 0) return false

        val (tileWidth, tileHeight) = tileSize

        // Basic Grid Calculation (Internal logic)
        val gridX = (index % columns) * tileWidth - size.width / 2f
        val gridY = (index / columns) * tileHeight - size.height / 2f

        val mapSet = findMapSet(realGid)
        if (mapSet == null) {
            // Fallback: Just return the grid square
            bounds.set(gridX, gridY, gridX + tileWidth, gridY + tileHeight)
            return true
        }

        // Tiled alignment logic: Left-aligned and Bottom-aligned
        val (visW, visH) = mapSet.tileSize
        val drawX = gridX + mapSet.offset.x
        val drawY = (gridY + tileHeight) - visH + mapSet.offset.y

        bounds.set(drawX, drawY, drawX + visW, drawY + visH)

        return true
    }
}

/**
 * Normalizes all coordinates in the TiledMapData to World Space (origin at map center).
 * This flattens all layer offsets and object positions into a single coordinate system.
 */
fun TiledMapData.normalized(): TiledMapData {
    if (isNormalized) return this
    isNormalized = true

    val mapWidth = columns * tileSize.width
    val mapHeight = rows * tileSize.height

    // Offset to shift (0,0) from Top-Left to Map-Center
    val worldOriginX = -mapWidth / 2f
    val worldOriginY = -mapHeight / 2f

    return this.copy(
        layers = layers.map { it.normalize(worldOriginX, worldOriginY, this) }
    )
}

private fun TiledMapLayer.normalize(
    parentX: Float,
    parentY: Float,
    mapData: TiledMapData
): TiledMapLayer {
    // Current layer absolute world position (ImageLayer has position, others default to 0)
    val layerX = if (this is TiledMapImageLayer) this.position.x else 0f
    val layerY = if (this is TiledMapImageLayer) this.position.y else 0f

    val layerWorldX = parentX + layerX
    val layerWorldY = parentY + layerY

    return when (this) {
        is TiledMapObjectLayer -> this.copy(
            // Flatten Layer offset into each Object's position
            objects = objects.map { obj ->
                val worldX = layerWorldX + obj.position.x
                val worldY = layerWorldY + obj.position.y
                when (obj) {
                    is TiledMapTileObject -> {
                        val mapSet = mapData.findMapSet(obj.gid)
                        // Use TileSet offset for visual alignment
                        val vOffset = mapSet?.offset ?: Offset.Zero
                        val tileSize = obj.size.takeOrElse { mapSet?.tileSize ?: mapData.tileSize }

                        obj.copy(
                            position = Offset(
                                x = worldX + vOffset.x,
                                // Correcting Tiled's Bottom-Left to World Top-Left
                                y = worldY - tileSize.height + vOffset.y
                            ),
                            size = tileSize
                        )
                    }
                    is TiledMapShapeObject -> {
                        // Shapes are already Top-Left, just apply world translation
                        obj.copy(
                            position = Offset(x = worldX, y = worldY)
                        )
                    }
                    else -> obj
                }
            }
        )

        is TiledMapGroupLayer -> this.copy(
            layers = layers.map { it.normalize(layerWorldX, layerWorldY, mapData) }
        )

        is TiledMapImageLayer -> this.copy(
            position = Offset(layerWorldX, layerWorldY)
        )

        // TileLayer is index-based, keep as is (will be handled in getCell/getBounds)
        is TiledMapTileLayer -> this
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
): SimpleCollection<TiledMapTile> {
    // Calculate how many columns and rows fit in the image
    // Formula: (ImageDimension - 2 * margin + spacing) / (tileSize + spacing)
    val columns: Int = ((size.width - 2 * margin + spacing) / (tileSize.width + spacing)).toInt().coerceAtLeast(1)

    // Note: Usually tilesets provide a 'tilecount' attribute.
    // If you have 'tilecount', use: rows = ceil(tilecount / columns)
    // If not, calculate based on image height:
    val rows: Int = ((size.height - 2 * margin + spacing) / (tileSize.height + spacing)).toInt().coerceAtLeast(1)

    private val tileList: List<TiledMapTile> = tiles.values.toList()

    override val count: Int
        get() = tileList.size

    override fun get(index: Int): TiledMapTile {
        return tileList[index]
    }

    /**
     * Calculates the source rectangle using the Global ID (GID).
     * @param outRect The rectangle to be populated with source coordinates.
     * @param gid The raw GID from map data (should be pre-stripped of flags).
     */
    fun getClip(outRect: MutableRect, gid: Int): Boolean {
        val realGid = TiledMapData.getRealGid(gid)
        if (realGid < firstGid) return false

        // Convert Global ID to Local ID
        val localId = realGid - firstGid

        val tw = tileSize.width
        val th = tileSize.height

        // Calculate grid position
        val col = localId % columns
        val row = localId / columns

        // Calculate source coordinates: margin + index * (size + spacing)
        val left = margin + col * (tw + spacing)
        val top = margin + row * (th + spacing)

        outRect.set(left, top, left + tw, top + th)

        return true
    }


    /**
     * Resolves the given GID to its final static tile GID by recursively processing
     * tile animations.
     *
     * This function extracts the real GID (stripping flip flags), locates the corresponding
     * tile in the current Tileset, and if it's an animated tile, follows the frame provided
     * by the [frameProvider] until a static tile is reached.
     *
     * @param gid The raw GID from the map layer, potentially including flip flags.
     * @param frameProvider The provider responsible for returning the current frame of an animated tile.
     * @return The final static GID within this Tileset, or 0 if the GID is empty.
     */
    inline fun resolveGid(gid: Int, frameProvider: (AnimatedTiledMapTile) -> TiledMapAnimationFrame): Int {
        val realGid = TiledMapData.getRealGid(gid)
        if (realGid == 0) return 0

        val tile = tiles[realGid - firstGid]

        return if (tile is AnimatedTiledMapTile) {
            val frame = frameProvider(tile)
            val flags = TiledMapData.getGidFlags(gid)
            (firstGid + frame.id) or flags
        } else {
            gid
        }
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
) : TiledMapTile, SimpleCollection<TiledMapAnimationFrame> {

    val duration: Int = frames.sumOf { it.duration }

    override val count: Int get() = frames.size

    override fun get(index: Int): TiledMapAnimationFrame {
        return frames[index]
    }

}

/**
 * Represents an [Empty Tile]. This is a placeholder or null-object pattern used for
 * empty grid cells (where GID is 0) or uninitialized tile references.
 */
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
    val position: Offset,
    val size: Size,
    val image: ImageBitmap,
) : TiledMapLayer {

    fun getBounds(bounds: MutableRect) {
        bounds.set(position, size)
    }

}

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
                for ((index, point) in points.withIndex()) {
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

    companion object {
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
    }
}