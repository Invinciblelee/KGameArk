@file:Suppress("ArrayInDataClass")

package com.kgame.engine.maps

import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.kgame.engine.geometry.set

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
        if (x !in 0..<columns || y < 0 || y >= rows) return 0
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
