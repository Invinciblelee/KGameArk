package com.example.kgame

import androidx.compose.ui.window.Window
import com.example.kgame.games.GameHost

fun jvmApp() = GameHost {
    Window(
        onCloseRequest = ::exitApplication,
        alwaysOnTop = true,
        title = "KGame",
    ) {
        App()
    }
}