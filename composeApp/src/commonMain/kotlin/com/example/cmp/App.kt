@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.example.cmp

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.cmp.games.common.GameDemo
import com.game.engine.core.GameEnvironment


@Composable
@Preview
fun App(environment: GameEnvironment) {
    MaterialExpressiveTheme {
        GameDemo(environment)
    }
}
