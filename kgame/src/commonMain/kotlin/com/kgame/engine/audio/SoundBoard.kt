package com.kgame.engine.audio

import com.kgame.platform.PlatformContext
import kotlinx.coroutines.channels.Channel

/**
 * Creates a standard [com.kgame.engine.audio.SoundBoard] format to replicate across all platforms
 * @param context (Required for Android) provides [PlatformContext] for Composable Resources
 * @param maxStreams (Optional) the maximum number of sounds that can be played simultaneously
 * @property mixer the [Channel] used to pass [MixerChannel.play] commands
 *
 * ```kotlin
 * // Creates SoundBoard instance, maxStreams defaults to 4
 * val soundBoard = SoundBoard(context)
 * // Creates SoundByte
 * val click = SoundByte("click", Res.getUri("files/click.mp3"))
 * // Registers SoundByte
 * soundBoard.register(click)
 * // Prepare to play sounds, Optional but recommended.
 * soundBoard.powerUp()
 * // Play sounds, this function can be called without powerUp, but latency will be higher.
 * soundBoard.mixer.play("click")
 * soundBoard.mixer.play(click)
 * // Clear sounds
 * soundBoard.powerDown()
 * // Release resources
 * soundBoard.release()
 * ```
 */
expect class SoundBoard(context: PlatformContext, maxStreams: Int = 4) {

    /** the [Channel] used to pass [MixerChannel.play] commands */
    val mixer: MixerChannel

    /**
     * Used to judge the [SoundByte] with the given [name] is registered onto the [SoundBoard]
     * @param name the name of the [SoundByte]
     * @return true if the [SoundByte] is registered, false otherwise
     */
    fun isRegistered(name: String): Boolean

    /**
     * Used to register a [SoundByte] onto the [SoundBoard]
     * @param soundByte the [SoundByte] to load
     */
    fun register(soundByte: SoundByte)

    /**
     * Used to register a list of [SoundByte]s onto the [SoundBoard]
     * @param soundBytes the list of [SoundByte]s to register
     */
    fun register(soundBytes: List<SoundByte>)

    /**
     * Used to load all registered [SoundByte]s onto the [SoundBoard]
     *
     * This function can preload all sounds onto the [SoundBoard]
     *
     * @see SoundBoard.register
     */
    fun powerUp()

    /**
     * Used to unload all registered [SoundByte]s from the [SoundBoard]
     *
     * This function can unload all sounds from the [SoundBoard]
     *
     * @see SoundBoard.register
     */
    fun powerDown()

    /**
     * Used to release resources used by [mixer],
     * and other platform-specific resources
     *
     * This [SoundBoard] can no longer be used after calling this function.
     */
    fun release()
}



