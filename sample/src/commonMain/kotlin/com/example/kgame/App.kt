@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.example.kgame

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import com.example.kgame.games.aircraftwar.AircraftWarGame
import com.example.kgame.games.swarm.SwarmBattleGame
import com.example.kgame.games.td.GeometricTDGame

@Composable
expect fun AppTheme(content: @Composable () -> Unit)

@Composable
fun App() {
    AppTheme {
        AircraftWarGame()
    }
}
