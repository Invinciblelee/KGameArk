package com.example.kgame

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.kgame.games.GameEnvironment
import com.kgame.engine.asset.AssetsReader
import com.kgame.engine.core.PlatformContext
import com.kgame.engine.maps.TmxMapLoader
import kgame.composeapp.generated.resources.Res
import kotlinx.coroutines.runBlocking

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        alwaysOnTop = true,
        title = "KGame",
    ) {
        App(GameEnvironment(PlatformContext))
    }
}

fun main2() = runBlocking {
    val loader = TmxMapLoader(object : AssetsReader {
        override suspend fun readBytes(path: String): ByteArray {
            return Res.readBytes(path)
        }

        override fun getUri(path: String): String {
            TODO("Not yet implemented")
        }
    })

    val data = loader.load("files/maps/example.tmx")
    println(data)
}

//./gradlew :composeApp:hotRunJvm --auto