@file:Suppress("ConvertSecondaryConstructorToPrimary")

package com.game.engine.audio

import com.game.engine.asset.SourceUri
import com.game.engine.audio.AudioState.Error
import com.game.engine.audio.AudioState.Loading
import com.game.engine.audio.AudioState.None
import com.game.engine.audio.AudioState.Paused
import com.game.engine.audio.AudioState.Playing
import com.game.engine.audio.AudioState.Ready
import com.game.engine.context.PlatformContext
import kotlinx.coroutines.flow.StateFlow

/**
 * Play audio from a url ([String]).
 *
 * Example:
 * ```
 * val audioUri = HttpUri("https://dare.wisc.edu/wp-content/uploads/sites/1051/2008/11/MS072.mp3")
 * val audio = Audio(context, audioUri, true) // AutoPlay is marked "true"
 * ```
 */
expect class Audio(
    context: PlatformContext,
    uri: SourceUri,
    loop: Boolean = false,
    autoPlay: Boolean = false
) {

    /**
     * Provides the state of [com.game.engine.audio.Audio] after initialization
     *
     * @property[None] indicates merely an initial state where no audio has loaded
     * @property[Loading] an audio file is being loaded
     * @property[Ready] sound is ready to be played
     * @property[Playing] player is currently playing audio, even if not audible
     * @property[Paused] player has stopped but retained its progress / timecode
     * @property[Error] Something unexpected occurred. Error will be printed to logs
    */
    val audioState: StateFlow<AudioState>

    /**
     * Adjust volume of [com.game.engine.audio.Audio]
     * @param rate range from 0.0 to 1.0
     **
     * Example:
     * ```
     * setVolume(0.5f)
     * ```
     */
    fun setVolume(rate: Float)

    /**
     * Used after [com.game.engine.audio.Audio] is initialized with [AudioState.Ready] to play the sound immediately.
     *
     * Sets the [audioState] to [AudioState.Playing]
     *
     * Example:
     * ```
     * val audio = Audio(audioUrl) // AutoPlay defaults to "false"
     * audio.play() // plays the sound immediately
     * ```
     */
    fun play()

    /**
     * Used when [com.game.engine.audio.Audio] is [AudioState.Playing] to pause the sound to be continued later.
     *
     * Sets the [audioState] to [AudioState.Paused]
     *
     * Example:
     * ```
     * val audio = Audio(audioUrl) // AutoPlay defaults to "false"
     * audio.play() // plays the sound immediately
     * // more code
     * audio.pause() // paused until play is called again
     * // more code
     * audio.play() // sound resumes
     * ```
     */
    fun pause()

    /**
     * Used to reset the [com.game.engine.audio.Audio] without reloading from the sound file.
     *
     * Sets the [audioState] to [AudioState.Ready]
     *
     * Example:
     * ```
     * val audio = Audio(audioUrl) // AutoPlay defaults to "false"
     * audio.play() // plays the sound immediately
     * // more code
     * audio.stop() // now ready to play from the beginning again
     * ```
     */
    fun stop()

    /**
     * Used when done to clear the [com.game.engine.audio.Audio] object from memory.
     * This instance of [com.game.engine.audio.Audio] can no longer be used.
     **
     * Example:
     * ```
     * val audio = Audio(audioUrl) // AutoPlay defaults to "false"
     * audio.play() // plays the sound immediately
     * // more code
     * audio.release() // audio can no longer be called
     * ```
     */
    fun release()

}