package com.kgame.engine.maps

import com.kgame.engine.asset.AssetsReader

class JsonMapLoader(
    private val assetsReader: AssetsReader
): TiledMapLoader {

    override suspend fun load(path: String): TiledMapData {
        TODO("Not yet implemented")
    }

}