package com.example.cmp

import androidx.compose.ui.window.ComposeUIViewController
import com.example.cmp.games.GameEnvironment
import com.game.engine.core.PlatformContext

fun MainViewController() = ComposeUIViewController(
    configure = {

    }
) { App(GameEnvironment(PlatformContext)) }

