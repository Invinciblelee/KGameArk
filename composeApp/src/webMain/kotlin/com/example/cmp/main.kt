package com.example.cmp

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.example.cmp.games.GameEnvironment
import com.game.engine.core.PlatformContext

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        App(GameEnvironment(PlatformContext))
    }
}