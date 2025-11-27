package com.example.cmp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.game.engine.context.PlatformContext

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "CMP",
    ) {
        App(PlatformContext)
    }
}

//./gradlew :composeApp:hotRunJvm --auto