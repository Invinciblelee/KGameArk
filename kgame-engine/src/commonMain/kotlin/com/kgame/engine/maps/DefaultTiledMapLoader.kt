package com.kgame.engine.maps

import com.kgame.engine.asset.AssetsReader

class DefaultTiledMapLoader(assetsReader: AssetsReader): TiledMapLoader {

    private val mapLoaders = mapOf(
        "tmx" to TmxMapLoader(assetsReader),
        "json" to JsonMapLoader(assetsReader)
    )

    override suspend fun load(path: String): TiledMapData {
        val extension = path.substringAfterLast('.')
        val loader = mapLoaders[extension] ?: error("Unsupported map format: $extension")
        return loader.load(path)
    }

}