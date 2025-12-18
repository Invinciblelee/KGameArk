@file:Suppress("ConvertTwoComparisonsToRangeCheck")

package com.kgame.engine.maps

import androidx.collection.SimpleArrayMap
import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import com.kgame.plugins.systems.TiledMapRenderSystem

/**
 * Represents a complete Tiled map. This is the generic, core data structure
 * used within the engine to represent a map.
 *
 * @param columns The total columns of the map in number of tiles.
 * @param rows The total rows of the map in number of tiles.
 * @param width The total width of the map in pixels.
 * @param height The total height of the map in pixels.
 * @param tileWidth The render width of a single tile in pixels.
 * @param tileHeight The render height of a single tile in pixels.
 * @param layers A list of all layers in the map, ordered by their rendering sequence.
 * @param tilesets A list of all tilesets used by this map.
 * @param properties Custom properties defined for the map.
 */
data class TiledMapData(
    val columns: Int,
    val rows: Int,
    val tileWidth: Int,
    val tileHeight: Int,
    val layers: List<TiledMapLayer>,
    val tilesets: List<TiledMapSet>,
    val properties: Map<String, String> = emptyMap()
) {

    val width: Int = columns * tileWidth
    val height: Int = rows * tileHeight

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
        const val FLIPPED_VERTICALLY_FLAG = 0x40000000.toInt()

        /** Flag for diagonal flipping (rotation). */
        const val FLIPPED_DIAGONALLY_FLAG = 0x20000000.toInt()
    }

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
        return mapSet.tiles[localId]
    }

    /**
     * Calculates the source clipping rectangle for a given Global ID (GID) and stores the result
     * in the provided [outRect]. This avoids creating new Rect objects on each call, which is
     * crucial for performance during rendering.
     *
     * @param gid The Global ID of the tile.
     * @param outRect A mutable [MutableRect] object where the calculated clipping rectangle will be stored.
     * @return The [TiledMapSet] that the GID belongs to, or null if the GID is invalid.
     */
    fun getClip(gid: Int, outRect: MutableRect): TiledMapSet? {
        // 1. Find the correct tileset for the GID.
        // We use a manual loop for performance, avoiding the overhead of `find` or `firstOrNull`.
        val mapSet = findMapSet(gid) ?: return null

        // 2. Calculate the local ID of the tile within this specific tileset.
        val localId = gid - mapSet.id

        // 4. Calculate the row and column index of the tile in the tileset grid.
        val columnsInTileset = mapSet.image.width / this.tileWidth
        val col = localId % columnsInTileset
        val row = localId / columnsInTileset

        // 5. Calculate the top-left pixel coordinates and set them on the outRect.
        outRect.set(
            left = (col * this.tileWidth).toFloat(),
            top = (row * this.tileHeight).toFloat(),
            right = ((col + 1) * this.tileWidth).toFloat(),
            bottom = ((row + 1) * this.tileHeight).toFloat()
        )

        // 6. Return the tileset, which contains the ImageBitmap needed for drawing.
        return mapSet
    }

    /**
     * Converts a flat tile index into the pixel offset (top-left corner) of that tile
     * within the entire map.
     *
     * @param index zero-based index in the layer's `data` array
     * @return pixel coordinates (x, y) relative to the map's top-left corner
     */
    fun getOffset(index: Int): IntOffset {
        val col = index % this.columns
        val row = index / this.columns
        return IntOffset(col * this.tileWidth, row * this.tileHeight)
    }

}

/**
 * Describes a tileset, which is a collection of tiles from a single texture atlas.
 *
 * @param id The starting global ID (GID) of a tile in this tileset.
 * @param name The name of the tileset.
 * @param image The tileset's source image.
 * @param spacing The spacing between tiles in the tileset image.
 * @param margin The margin around the tiles in the tileset image.
 * @param offset The offset to apply to tiles when rendering.
 * @param terrains A map of terrain names to their corresponding tile IDs.
 * @param tiles A map of tile IDs to their metadata.
 */
data class TiledMapSet(
    val id: Int,
    val name: String,
    val image: ImageBitmap,
    val spacing: Int,
    val margin: Int,
    val offset: IntOffset,
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
 * [Tiled Map Animation State Manager]
 * A dedicated class responsible for managing and updating the runtime state of all Tiled map animations.
 *
 * This class embodies the "State Manager" design pattern, effectively decoupling the animation's
 * definition data (stored in [AnimatedTiledMapTile]) from its runtime state (e.g., elapsed time).
 *
 * The [TiledMapRenderSystem] will hold an instance of this class, delegating all animation state
 * updates and queries to it, which simplifies the renderer's logic and centralizes state management.
 */
class TiledMapAnimationState {

    private data class AnimationRuntimeState(
        var elapsedTime: Float = 0f,
        var currentFrameIndex: Int = 0
    )

    private val animationStates = SimpleArrayMap<Int, AnimationRuntimeState>()


    fun update(deltaTime: Float) {
        var index = 0
        while (index < animationStates.size()) {
            val state = animationStates.valueAt(index++)
            state.elapsedTime += deltaTime * 1000 // Convert delta time (seconds) to milliseconds
        }
    }

    fun getCurrentFrame(tile: AnimatedTiledMapTile): TiledMapAnimationFrame {
        val animationState = getAnimationRuntimeState(tile.id)

        if (animationState.elapsedTime > tile.duration) {
            animationState.elapsedTime %= tile.duration
        }

        var currentFrame = tile.frames.last()
        var accumulatedTime = 0
        for (frame in tile.frames) {
            accumulatedTime += frame.duration
            if (animationState.elapsedTime <= accumulatedTime) {
                currentFrame = frame
                break
            }
        }
        return currentFrame
    }

    private fun getAnimationRuntimeState(key: Int): AnimationRuntimeState {
        var state = animationStates[key]
        if (state == null) {
            state = AnimationRuntimeState()
            animationStates.put(key, state)
        }
        return state
    }

}

/**
 * [Layer Abstraction]
 * A sealed interface to support different types of map layers.
 */
sealed interface TiledMapLayer {
    val name: String
    val color: Color
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as TiledMapTileLayer

        if (visible != other.visible) return false
        if (columns != other.columns) return false
        if (rows != other.rows) return false
        if (name != other.name) return false
        if (properties != other.properties) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = visible.hashCode()
        result = 31 * result + columns
        result = 31 * result + rows
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
    override val color: Color,
    override val visible: Boolean,
    override val properties: Map<String, String>,
    val objects: List<TiledMapObject>
) : TiledMapLayer

/**
 * [Image Layer]
 * Represents a layer that contains a single, standalone image, often used for backgrounds or foreground elements.
 * Unlike tile layers, it is not based on grid data from a tileset.
 */
data class TiledMapImageLayer(
    override val name: String,
    override val color: Color,
    override val visible: Boolean,
    override val properties: Map<String, String>,
    val image: ImageBitmap,
    val x: Float = 0f, // The horizontal offset of the image layer in pixels.
    val y: Float = 0f // The vertical offset of the image layer in pixels.
) : TiledMapLayer

/**
 * [Group Layer]
 * Represents a container for organizing multiple map layers (tile, object, or image layers).
 * This allows layers to be managed together (e.g., toggled visible/invisible).
 */
data class TiledMapGroupLayer(
    override val name: String,
    override val color: Color,
    override val visible: Boolean,
    override val properties: Map<String, String>,
    val layers: List<TiledMapLayer> // A list of all child layers contained within this group. (包含子图层)
) : TiledMapLayer

/**
 * [Map Object]
 * Describes a single object's position, size, and properties within an object layer.
 */
data class TiledMapObject(
    val id: Int,
    val gid: Int,
    val name: String,
    val type: String,
    val x: Float,
    val y: Float,
    val shape: TiledMapShape,
    val properties: Map<String, String>
)

sealed class TiledMapShape {
    data class Rectangle(val width: Float, val height: Float) : TiledMapShape()

    data class Ellipse(val width: Float, val height: Float) : TiledMapShape()

    data class Polygon(val points: List<Offset>) : TiledMapShape()

    object Point : TiledMapShape()
}