package com.game.engine.audio

import com.game.engine.asset.SourceUri
import com.game.engine.context.PlatformContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class Audio actual constructor(
    context: PlatformContext,
    uri: SourceUri,
    loop: Boolean,
    autoPlay: Boolean
) {

    private val _audioState = MutableStateFlow<AudioState>(AudioState.None)
    actual val audioState: StateFlow<AudioState> = _audioState.asStateFlow()

    actual fun setVolume(rate: Float) {
    }

    actual fun play() {
    }

    actual fun pause() {
    }

    actual fun stop() {
    }

    actual fun release() {
    }
}