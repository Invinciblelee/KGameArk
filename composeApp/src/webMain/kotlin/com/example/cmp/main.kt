package com.example.cmp

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.game.engine.context.PlatformContext

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        val context = remember { object : PlatformContext() {} }
        App(context)
    }
}