package com.kgame.engine.maps

import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.jvm.JvmInline

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
     * Gets the special tile [TiledMapTile] for a given Global ID (GID).
     */
    fun findTile(gid: Int): TiledMapTile? {
        val realGid = gid.realGid()
        if (realGid < firstGid) return null
        return tiles[realGid] ?: SimpleTiledMapTile(gid)
    }

    /**
     * Calculates the source rectangle using the Global ID (GID).
     * @param outRect The rectangle to be populated with source coordinates.
     * @param gid The raw GID from map data (should be pre-stripped of flags).
     */
    fun getClip(outRect: MutableRect, gid: Int): Boolean {
        val realGid = gid.realGid()
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

}

/**
 * [Tile Metadata Abstraction]
 * A sealed interface representing the metadata for a single tile, distinguishing
 * between a static tile and an animated one. This provides strong type-safety.
 */
sealed interface TiledMapTile {
    /**
     * The ID of the tile within its tileset.
     *
     * Note: This is a local ID before normalization and becomes a
     * Global ID (GID) with flipping flags preserved after normalization.
     */
    val id: Int

    /**
     * Custom properties defined for this specific tile.
     */
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

    val duration: Int = frames.sumOf { it.duration }

}


/**
 * A lightweight, memory-efficient wrapper for tiles that only possess an ID
 * without any custom properties or animations.
 */
@JvmInline
value class SimpleTiledMapTile(
    private val _id: Int
) : TiledMapTile {
    override val id: Int get() = _id
    override val properties: Map<String, String> get() = emptyMap()
}

/**
 * [Animation Frame]
 * Describes a single frame within a tile animation.
 *
 * @param id The ID of the tile to be displayed.
 *
 * Note: This is a local ID before normalization and becomes a
 * Global ID (GID) with flipping flags preserved after normalization.
 * @param duration The duration of this frame in milliseconds.
 */
data class TiledMapAnimationFrame(
    val id: Int,
    val duration: Int
)