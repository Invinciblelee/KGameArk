package com.kgame.engine.maps

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.takeOrElse

/**
 * Normalizes all coordinates and IDs in the TiledMapData.
 * 1. Shifts coordinate system from Top-Left to Map-Center (World Space).
 * 2. Maps all Local IDs within Tilesets and Animation Frames to Global IDs (GIDs).
 * 3. Corrects Tiled's Bottom-Left object alignment to World Top-Left.
 */
fun TiledMapData.normalized(): TiledMapData {
    if (isNormalized) return this

    // 1. Pre-calculate normalized tilesets
    val normalizedTilesets = tilesets.map { mapSet ->
        val offset = mapSet.firstGid
        mapSet.copy(
            tiles = mapSet.tiles
                .mapKeys { (localId, _) ->
                    localId + offset
                }
                .mapValues { (_, tile) ->
                    when (tile) {
                        is StaticTiledMapTile -> tile.copy(id = tile.id.applyOffset(offset))
                        is AnimatedTiledMapTile -> tile.copy(
                            id = tile.id.applyOffset(offset),
                            frames = tile.frames.map { it.copy(id = it.id.applyOffset(offset)) }
                        )

                        else -> tile
                    }
                }
        )
    }

    // 2. Prepare coordinate transformation
    val worldOriginX = -size.width / 2f
    val worldOriginY = -size.height / 2f

    // 3. Create the final copy.
    // Note: We use this.copy to get the base structure,
    // but we pass normalizedTilesets to the layers' normalization process
    val result = this.copy(
        tilesets = normalizedTilesets,
        isNormalized = true
    )

    // Pass the 'result' (which has new tilesets) to normalize
    return result.copy(
        layers = layers.map { it.normalize(worldOriginX, worldOriginY, result) }
    )
}

/**
 * Recursively flattens layer offsets and object positions into World Space.
 */
private fun TiledMapLayer.normalize(
    parentX: Float,
    parentY: Float,
    mapData: TiledMapData
): TiledMapLayer {
    // Flatten Layer offsets (ImageLayer has position, others default to 0)
    val layerX = if (this is TiledMapImageLayer) this.position.x else 0f
    val layerY = if (this is TiledMapImageLayer) this.position.y else 0f

    val layerWorldX = parentX + layerX
    val layerWorldY = parentY + layerY

    return when (this) {
        is TiledMapObjectLayer -> this.copy(
            objects = objects.map { obj ->
                val worldX = layerWorldX + obj.position.x
                val worldY = layerWorldY + obj.position.y
                when (obj) {
                    is TiledMapTileObject -> {
                        val mapSet = mapData.findMapSet(obj.gid)
                        val vOffset = mapSet?.offset ?: Offset.Zero
                        // Use explicit size if defined, otherwise fallback to Tileset or Map default
                        val finalSize = obj.size.takeOrElse { mapSet?.tileSize ?: mapData.tileSize }

                        obj.copy(
                            position = Offset(
                                x = worldX + vOffset.x,
                                // Correcting Tiled's Bottom-Left to World Top-Left
                                y = worldY - finalSize.height + vOffset.y
                            ),
                            size = finalSize
                        )
                    }

                    is TiledMapShapeObject -> {
                        // Shapes are Top-Left based, just apply translation
                        obj.copy(position = Offset(worldX, worldY))
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

        // TileLayer grid is handled by getCell/getBounds based on MapCenter
        is TiledMapTileLayer -> this
    }
}