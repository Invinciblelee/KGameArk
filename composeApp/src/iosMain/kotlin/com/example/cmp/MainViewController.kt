package com.example.cmp

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ComposeUIViewController
import com.game.engine.context.PlatformContext

fun MainViewController() = ComposeUIViewController(
    configure = {

    }
) { App(PlatformContext) }

