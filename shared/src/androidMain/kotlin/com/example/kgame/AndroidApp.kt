package com.example.kgame

import androidx.activity.ComponentActivity
import com.example.kgame.games.GameHost

fun androidApp(activity: ComponentActivity) {
    GameHost(activity) {
        App()

        SkikoShaderVerifier()
    }
}