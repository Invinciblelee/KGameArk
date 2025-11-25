package com.game.engine.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

data class Sound(
    val name: String,
    val volume: Float
)

/**
 * A version of [Channel] used to play sounds on a [SoundBoard]
 * @see MixerChannel.play
 */
class MixerChannel: AutoCloseable {

    private val delegate = Channel<Sound>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    internal suspend fun receiveCatching() = delegate.receiveCatching()

    /**
     * Used to play a [SoundByte] on a [SoundBoard],
     * but requires a [SoundBoard.powerUp] first
     * @param name a string matching the name of a [SoundByte] you wish to play
     * @param volume the volume of the sound
     * @see SoundBoard.mixer
     */
    fun play(name: String, volume: Float = 1f) {
        scope.launch {
            delegate.send(Sound(name, volume))
        }
    }

    /**
     * Used to play a [SoundByte] on a [SoundBoard],
     * but requires a [SoundBoard.powerUp] first
     * @param sound the [SoundByte] you wish to play (must already be loaded onto your [SoundBoard])
     * @param volume the volume of the sound
     * @see SoundBoard.mixer
     */
    fun play(sound: SoundByte, volume: Float = 1f) {
        play(sound.name, volume)
    }

    override fun close() {
        scope.cancel()
        delegate.close()
    }

}
