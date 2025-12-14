package com.example.kgame

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.kgame.games.GameEnvironment
import com.kgame.engine.core.PlatformContext

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        alwaysOnTop = true,
        title = "KGame",
    ) {
        App(GameEnvironment(PlatformContext))
    }
}

//./gradlew :composeApp:hotRunJvm --auto