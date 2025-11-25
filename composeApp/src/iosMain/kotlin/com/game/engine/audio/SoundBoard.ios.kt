package com.game.engine.audio

import com.game.engine.context.PlatformContext

actual class SoundBoard actual constructor(context: PlatformContext) {
    actual val mixer: MixerChannel = MixerChannel()

    actual fun isRegistered(name: String): Boolean {
        return true
    }

    actual fun register(soundByte: SoundByte) {
    }

    actual fun register(soundBytes: List<SoundByte>) {
    }

    actual fun powerUp() {
    }

    actual fun powerDown() {
    }

    actual fun release() {
    }
}