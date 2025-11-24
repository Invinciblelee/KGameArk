package com.game.engine.audio

import com.game.engine.asset.SourceUri
import com.game.engine.context.PlatformContext

actual class AudioManager actual constructor(context: PlatformContext) {
    actual fun playSound(uri: SourceUri, volume: Float) {
    }

    actual fun playMusic(uri: SourceUri, loop: Boolean) {
    }

    actual fun pauseMusic() {
    }

    actual fun resumeMusic() {
    }

    actual fun stopMusic() {
    }

    actual fun setMusicVolume(volume: Float) {
    }
}