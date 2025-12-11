package com.example.cmp.games

import com.game.engine.asset.AssetsReader
import com.game.engine.core.GameEnvironment
import com.game.engine.core.PlatformContext
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
