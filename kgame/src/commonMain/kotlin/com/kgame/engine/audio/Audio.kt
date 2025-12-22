@file:Suppress("ConvertSecondaryConstructorToPrimary")

package com.kgame.engine.audio

import com.kgame.engine.asset.SourceUri
import com.kgame.engine.audio.AudioState.Error
import com.kgame.engine.audio.AudioState.Loading
import com.kgame.engine.audio.AudioState.None
import com.kgame.engine.audio.AudioState.Paused
import com.kgame.engine.audio.AudioState.Playing
import com.kgame.engine.audio.AudioState.Ready
import com.kgame.platform.PlatformContext
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
     * Provides the state of [com.kgame.engine.audio.Audio] after initialization
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
     * Adjust volume of [com.kgame.engine.audio.Audio]
     * @param volume range from 0.0 to 1.0
     **
     * Example:
     * ```
     * val audio = Audio(context, audioUri) // AutoPlay defaults to "false"
     * audio.setVolume(0.5f)
     * ```
     */
    fun setVolume(volume: Float)

    /**
     * Used after [com.kgame.engine.audio.Audio] is initialized with [AudioState.Ready] to play the sound immediately.
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
     * Used when [com.kgame.engine.audio.Audio] is [AudioState.Playing] to pause the sound to be continued later.
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
     * Used to reset the [com.kgame.engine.audio.Audio] without reloading from the sound file.
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
     * Used when done to clear the [com.kgame.engine.audio.Audio] object from memory.
     * This instance of [com.kgame.engine.audio.Audio] can no longer be used.
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