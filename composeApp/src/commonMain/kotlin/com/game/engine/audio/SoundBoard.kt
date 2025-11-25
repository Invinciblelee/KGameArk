package com.game.engine.audio

import com.game.engine.context.PlatformContext
import kotlinx.coroutines.channels.Channel

/**
 * Creates a standard [com.game.engine.audio.SoundBoard] format to replicate across all platforms
 * @param context (Required for Android) provides [Context] for Composable Resources
 * @property mixer the [Channel] used to pass [MixerChannel.play] commands
 *
 * ```
 * // Creates SoundBoard instance
 * val soundBoard = SoundBoard(context)
 * // Creates SoundByte
 * val click = SoundByte("click", Res.getUri("files/click.mp3"))
 * // Loads SoundByte
 * soundBoard.load(click)
 * // Prepare to play sounds
 * soundBoard.powerUp()
 * // Play sounds
 * soundBoard.mixer.play("click")
 * soundBoard.mixer.play(click)
 * ```
 */
expect class SoundBoard(context: PlatformContext) {

    /** the [Channel] used to pass [MixerChannel.play] commands */
    val mixer: MixerChannel

    fun isRegistered(name: String): Boolean

    fun register(soundByte: SoundByte)

    fun register(soundBytes: List<SoundByte>)

    fun powerUp()

    fun powerDown()

    /**
     * Used to release resources used by [mixer],
     * and other platform-specific resources
     */
    fun release()
}



