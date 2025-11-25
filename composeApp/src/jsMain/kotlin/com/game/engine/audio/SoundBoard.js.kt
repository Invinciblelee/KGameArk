package com.game.engine.audio

import com.game.engine.context.PlatformContext

actual class SoundBoard actual constructor(context: PlatformContext) {
    actual val mixer: MixerChannel
        get() = TODO("Not yet implemented")

    actual fun isRegistered(name: String): Boolean {
        TODO("Not yet implemented")
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