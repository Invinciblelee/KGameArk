package com.kgame.engine.maps

import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

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
 * @param isNormalized True if the [TiledMapData] is normalized.
 */
data class TiledMapData(
    val columns: Int,
    val rows: Int,
    val tileSize: Size,
    val backgroundColor: Color,
    val layers: List<TiledMapLayer>,
    val tilesets: List<TiledMapSet>,
    val properties: Map<String, String> = emptyMap(),
    val isNormalized: Boolean = false
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

    val size: Size = Size(columns * tileSize.width, rows * tileSize.height)

    val solidObjects: List<TiledMapShapeObject> = layers.filterSolidObjects()

    /**
     * Gets the [TiledMapSet] that a given Global ID (GID) belongs to.     * This follows a 'get' contract: it expects the GID to be valid.
     *
     * @return Returns null if no tileset contains the given GID.
     */
    fun findMapSet(gid: Int): TiledMapSet? {
        val realGid = gid.realGid()
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
     * Gets the special tile [TiledMapTile] for a given Global ID (GID).
     * This follows a 'get' contract: it expects a tile with special properties to exist for this GID.
     * If a GID is valid but simply has no special properties, the `TiledMapSet.tiles` map
     * will not contain its local ID, and this function will throw an exception.
     *
     */
    fun findTile(gid: Int): TiledMapTile? {
        val realGid = gid.realGid()
        if (realGid == 0) return null
        val mapSet = findMapSet(gid) ?: return null
        return mapSet.findTile(gid)
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
        val realGid = gid.realGid()
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
        val top = row * tileHeight - size.height / 2f
        outRect.set(
            left = left,
            top = top,
            right = left + tileWidth,
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
        val realGid = gid.realGid()
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