package com.example.kgame.games

import com.kgame.engine.asset.AssetsReader
import com.kgame.engine.core.GameEnvironment
import com.kgame.engine.core.PlatformContext
import kgame.composeapp.generated.resources.Res


private val DefaultAssetsReader = object : AssetsReader {
    override suspend fun readBytes(path: String): ByteArray {
        return Res.readBytes(path)
    }

    override fun getUri(path: String): String {
        return Res.getUri(path)
    }
}

fun GameEnvironment(context: PlatformContext): GameEnvironment {
    return GameEnvironment(context, DefaultAssetsReader)
}
