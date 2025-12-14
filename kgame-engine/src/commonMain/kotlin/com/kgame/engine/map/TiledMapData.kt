@file:Suppress("ConvertTwoComparisonsToRangeCheck")

package com.kgame.engine.map

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Represents a complete Tiled map. This is the generic, core data structure
 * used within the engine to represent a map.
 *
 * @param width The total width of the map in number of tiles.
 * @param height The total height of the map in number of tiles.
 * @param tileWidth The render width of a single tile in pixels.
 * @param tileHeight The render height of a single tile in pixels.
 * @param mapLayers A list of all layers in the map, ordered by their rendering sequence.
 * @param mapSets A list of all tilesets used by this map.
 * @param properties Custom properties defined for the map.
 */
data class TiledMapData(
    val width: Int,
    val height: Int,
    val tileWidth: Int,
    val tileHeight: Int,
    val mapLayers: List<TiledMapLayer>,
    val mapSets: List<TiledMapSet>,
    val properties: Map<String, String> = emptyMap()
) {

    /** The total number of columns in the map grid, calculated from the total width. */
    val columns: Int = if (tileWidth > 0) width / tileWidth else 0

    /** The total number of rows in the map grid, calculated from the total height. */
    val rows: Int = if (tileHeight > 0) height / tileHeight else 0

    /**
     * Gets the [TiledMapSet] that a given Global ID (GID) belongs to.     * This follows a 'get' contract: it expects the GID to be valid.
     *
     * @throws IllegalArgumentException if no tileset contains the given GID.
     */
    fun getMapSet(gid: Int): TiledMapSet {
        var index = mapSets.size - 1
        while (index >= 0) {
            val mapSet = mapSets[index--]
            if (gid >= mapSet.id) {
                return mapSet
            }
        }
        throw IllegalArgumentException("Invalid GID: $gid. No TiledMapSet contains this ID.")
    }

    /**
     * Gets the special tile metadata [TiledMapTile] for a given Global ID (GID).
     * This follows a 'get' contract: it expects a tile with special properties to exist for this GID.
     * If a GID is valid but simply has no special properties, the `TiledMapSet.tiles` map
     * will not contain its local ID, and this function will throw an exception.
     *
     * @throws NoSuchElementException if the tile for the given GID does not have special metadata.
     * @throws IllegalArgumentException if the GID itself is invalid.
     */
    fun getTile(gid: Int): TiledMapTile {
        val mapSet = getMapSet(gid)
        val localId = gid - mapSet.id
        return mapSet.tiles.getValue(localId)
    }

}

/**
 * Describes a tileset, which is a collection of tiles from a single texture atlas.
 *
 * @param id The starting global ID (GID) of a tile in this tileset.
 * @param name The name of the tileset.
 * @param image The tileset's source image.
 * @param tiles A Map containing special metadata for specific tiles, keyed by their local ID.
 */
data class TiledMapSet(
    val id: Int,
    val name: String,
    val image: ImageBitmap,
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
) : TiledMapTile

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
    override val visible: Boolean,
    override val properties: Map<String, String>,
    val width: Int,
    val height: Int,
    val data: IntArray
) : TiledMapLayer {
    /**
     * Safely retrieves the Global ID at the given grid coordinates. Returns 0 if out of bounds.
     */
    fun getGid(x: Int, y: Int): Int {
        if (x < 0 || x >= width || y < 0 || y >= height) return 0
        return data[y * width + x]
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as TiledMapTileLayer

        if (visible != other.visible) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (name != other.name) return false
        if (properties != other.properties) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = visible.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + name.hashCode()
        result = 31 * result + properties.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

/**
 * [Object Layer]
 * Stores free-floating game objects like spawn points, collision areas, etc.
 */
data class TiledMapObjectLayer(
    override val name: String,
    override val visible: Boolean,
    override val properties: Map<String, String>,
    val objects: List<TiledMapObject>
) : TiledMapLayer

/**
 * [Map Object]
 * Describes a single object's position, size, and properties within an object layer.
 */
data class TiledMapObject(
    val id: Int,
    val name: String,
    val type: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val properties: Map<String, String>
)
