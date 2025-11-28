package com.game.engine.audio

/**
 * @property[None] indicates merely an initial state where no audio has loaded
 * @property[Loading] an audio file is being loaded
 * @property[Ready] sound is ready to be played
 * @property[Playing] player is currently playing audio, even if not audible
 * @property[Paused] player has stopped but retained its progress / timecode
 * @property[Error] Something unexpected occurred. Error will be printed to logs
 * @property[Completed] playback completed
 */
sealed class AudioState {

    /**
     * @property[None] Indicates merely an initial state where no audio has loaded
     */
    data object None: AudioState()

    /**
     * @property[Loading] an audio file is being loaded
     */
    data object Loading : AudioState()

    /**
     * @property[Ready] sound is ready to be played
     */
    data object Ready : AudioState()

    /**
     * @property[Playing] player is currently playing audio, even if not audible
     */
    data object Playing : AudioState()

    /**
     * @property[Paused] player has stopped but retained its progress / timecode
     */
    data object Paused : AudioState()

    /**
     * @property[Error] Something unexpected occurred. Error will be printed to logs
     */
    data class Error(val message: String) : AudioState()

    data object Completed: AudioState()
}