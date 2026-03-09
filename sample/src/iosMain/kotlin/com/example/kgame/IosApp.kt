@file:OptIn(ExperimentalComposeUiApi::class)

package com.example.kgame

import androidx.compose.ui.ExperimentalComposeUiApi
import com.example.kgame.games.GameHost

fun iosApp() = GameHost { App() }