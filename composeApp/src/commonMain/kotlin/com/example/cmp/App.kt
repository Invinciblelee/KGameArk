@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.example.cmp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.cmp.games.aircraftwar.AircraftWarDemo
import com.game.engine.context.PlatformContext


@Composable
@Preview
fun App(context: PlatformContext) {
    MaterialExpressiveTheme {
        AircraftWarDemo(context)
    }
}

@Composable
private fun AppDemo() {
    Surface(
        modifier = Modifier
            .fillMaxSize()
    ) {
        MainScreen()
    }
}