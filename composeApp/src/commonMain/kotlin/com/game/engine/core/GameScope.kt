package com.game.engine.core

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextMeasurer
import com.game.engine.audio.AudioManager
import com.game.engine.input.InputManager

interface GameScope {
    val input: InputManager
    val audio: AudioManager
    val size: Size
    val textMeasurer: TextMeasurer

    val fps: Int

    fun switchScene(id: String)
}