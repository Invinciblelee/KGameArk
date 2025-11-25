package com.game.engine.audio

import com.game.engine.asset.SourceUri
import com.game.engine.context.PlatformContext
import kotlinx.coroutines.flow.StateFlow

actual class Audio actual constructor(
    context: PlatformContext,
    uri: SourceUri,
    loop: Boolean,
    autoPlay: Boolean
) {
    actual val audioState: StateFlow<AudioState>
        get() = TODO("Not yet implemented")

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