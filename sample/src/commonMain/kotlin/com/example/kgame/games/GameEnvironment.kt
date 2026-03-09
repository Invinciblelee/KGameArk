package com.example.kgame.games

import com.kgame.engine.asset.AssetsReader
import com.kgame.platform.KGameHost
import kgameark.shared.generated.resources.Res

private val DefaultAssetsReader = object : AssetsReader {
    override suspend fun readBytes(path: String): ByteArray {
        return Res.readBytes(path)
    }

    override fun getUri(path: String): String {
        return Res.getUri(path)
    }
}

object GameHost: KGameHost(DefaultAssetsReader)
