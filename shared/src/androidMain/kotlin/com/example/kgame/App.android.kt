@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.example.kgame

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable

@Composable
actual fun AppTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(content = content)
}