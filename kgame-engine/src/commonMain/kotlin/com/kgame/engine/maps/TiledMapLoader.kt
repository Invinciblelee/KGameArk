package com.kgame.engine.maps

interface TiledMapLoader {

    suspend fun load(path: String): TiledMapData

}