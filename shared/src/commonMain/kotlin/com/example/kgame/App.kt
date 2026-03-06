@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.example.kgame

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key.Companion.S
import com.example.kgame.games.catalyst.QuantumCatalystGame
import com.example.kgame.games.common.SampleGame
import com.example.kgame.games.gomoku.GomokuGame
import com.example.kgame.games.match3.Match3Game
import com.example.kgame.games.singularity.NeonSingularityGame
import com.example.kgame.games.survivor.SurvivorGameDemo
import com.example.kgame.games.td.GeometricTDGame
import com.example.kgame.games.weaver.AuraWeaverGame

@Composable
expect fun AppTheme(content: @Composable () -> Unit)

@Composable
fun App() {
    AppTheme {
        GeometricTDGame()
    }
}
