@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.example.kgame

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.kgame.games.aircraftwar.GameAircraftWarDemo
import com.example.kgame.games.common.GameDemo
import com.example.kgame.games.common.SimpleGameDemo
import kgameark.shared.generated.resources.Res
import kgameark.shared.generated.resources.stormplane_background

@Composable
expect fun AppTheme(content: @Composable () -> Unit)

@Composable
@Preview
fun App() {
    AppTheme {
        GameAircraftWarDemo()
    }
}
