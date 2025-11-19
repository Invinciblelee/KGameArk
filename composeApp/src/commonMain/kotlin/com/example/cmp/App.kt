@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.example.cmp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.tooling.preview.Preview


@Composable
@Preview
fun App() {
    MaterialExpressiveTheme {
        Surface(
            modifier = Modifier
            .fillMaxSize()
            .preferredFrameRate(FrameRateCategory.High)
        ) {
            MainScreen()
        }
    }
}