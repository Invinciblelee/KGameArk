package com.kgame.plugins.components // or your components package

import com.kgame.ecs.Component
import com.kgame.ecs.ComponentType
import com.kgame.engine.map.TiledMapData

/**
 * A Component that attaches a [TiledMapData] data object to an entity.
 * It follows the project's convention of not using a "Component" suffix.
 *
 * @param data The pure data object representing the entire Tiled map.
 */
data class TiledMap(
    val data: TiledMapData
) : Component<TiledMap> {
    override fun type() = TiledMap

    companion object Companion : ComponentType<TiledMap>()
}