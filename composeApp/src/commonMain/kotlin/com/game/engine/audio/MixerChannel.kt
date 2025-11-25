package com.game.engine.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

data class Sound(
    val name: String,
    val volume: Float
)

/**
 * A version of [Channel] used to play sounds on a [SoundBoard]
 * @see MixerChannel.play
 */
class MixerChannel: AutoCloseable {

    companion object {
        private const val MIN_PLAY_INTERVAL_MS = 100L
    }

    private val delegate = Channel<Sound>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val lastPlayed = mutableMapOf<String, Long>()
    private val rateLimitMutex = Mutex()

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
            rateLimitMutex.withLock {
                val currentTime = Clock.System.now().toEpochMilliseconds()
                val lastPlayedTime = lastPlayed[name] ?: 0L

                if (currentTime - lastPlayedTime >= MIN_PLAY_INTERVAL_MS) {
                    lastPlayed[name] = currentTime
                    delegate.send(Sound(name, volume))
                }
            }
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
        lastPlayed.clear()
    }

}
