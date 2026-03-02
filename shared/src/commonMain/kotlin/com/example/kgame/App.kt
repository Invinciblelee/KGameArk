@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.example.kgame

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import com.example.kgame.games.common.SampleGame
import com.example.kgame.games.fruit.FruitSlicerGame
import com.example.kgame.games.match3.Match3Game

@Composable
expect fun AppTheme(content: @Composable () -> Unit)

@Composable
fun App() {
    AppTheme {
        SampleGame()
    }
}
