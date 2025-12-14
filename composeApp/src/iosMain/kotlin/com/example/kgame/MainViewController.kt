package com.example.kgame

import androidx.compose.ui.window.ComposeUIViewController
import com.example.kgame.games.GameEnvironment
import com.kgame.engine.core.PlatformContext

fun MainViewController() = ComposeUIViewController(
    configure = {

    }
) { App(GameEnvironment(PlatformContext)) }

