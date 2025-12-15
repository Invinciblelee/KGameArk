package com.example.kgame

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.example.kgame.games.GameEnvironment
import com.kgame.engine.core.PlatformContext

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        App(GameEnvironment(PlatformContext))
    }
}

