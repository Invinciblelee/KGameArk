package com.example.cmp

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.game.engine.context.PlatformContext

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        App(PlatformContext)
    }
}