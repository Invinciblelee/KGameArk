package com.kgame.engine.core

import androidx.compose.ui.text.TextMeasurer
import com.kgame.engine.asset.AssetsManager
import com.kgame.engine.audio.AudioManager
import com.kgame.engine.geometry.ResolutionManager
import com.kgame.engine.input.InputManager

internal object GameGlobals {

    private var _currentEngine: GameEngine? = null
    val currentEngine: GameEngine get() = requireNotNull(_currentEngine) { "GameEngine not initialized" }

    fun setCurrentEngine(engine: GameEngine?) {
        _currentEngine = engine
    }

    val resolution: ResolutionManager get() = currentEngine.resolution
    val input: InputManager get() = currentEngine.input
    val audio: AudioManager get() = currentEngine.audio
    val assets: AssetsManager get() = currentEngine.assets
    val textMeasurer: TextMeasurer get() = currentEngine.textMeasurer
}