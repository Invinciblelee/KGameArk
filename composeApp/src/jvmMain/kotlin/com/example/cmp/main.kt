package com.example.cmp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.cmp.games.GameEnvironment
import com.game.engine.core.PlatformContext

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        alwaysOnTop = true,
        title = "CMP",
    ) {
        App(GameEnvironment(PlatformContext))
    }
}

//./gradlew :composeApp:hotRunJvm --auto