package com.example.cmp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.cmp.games.GameDemo

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        alwaysOnTop = true,
        title = "CMP",
    ) {
        GameDemo()
    }
}

//./gradlew :composeApp:hotRunJvm --auto