package com.kgame.engine.audio

import androidx.compose.runtime.Stable
import com.kgame.engine.asset.SourceUri
import com.kgame.engine.log.Logger
import com.kgame.platform.PlatformContext

/**
 * Manages the audio of the kgame.
 */
interface AudioManager {

    /**
     * Returns true if music is playing
     */
    val isMusicPlaying: Boolean

    /**
     * Plays a sound with the given [uri] and [volume].
     * @param uri The uri of the sound to play.
     * @param volume The volume of the sound.
     */
    fun playSound(uri: SourceUri, volume: Float = 1f)

    /**
     * Plays music with the given [uri] and [loop].
     * @param uri The uri of the music to play.
     * @param loop Whether the music should loop.
     */
    fun playMusic(uri: SourceUri, loop: Boolean = false)

    /**
     * Pauses music.
     */
    fun pauseMusic()

    /**
     * Resumes music.
     */
    fun resumeMusic()

    /**
     * Stops music.
     */
    fun stopMusic()

    /**
     * Sets the volume of music.
     * @param volume The volume of the music.
     */
    fun setMusicVolume(volume: Float)

    /**
     * Clears all resources.
     * This [AudioManager] can be reused after calling this function.
     */
    fun clear()

    /**
     * Shuts down the audio manager.
     * This [AudioManager] can no longer be used after calling this function.
     */
    fun shutdown()

}

/**
 * Default implementation of [AudioManager].
 */
@Stable
class DefaultAudioManager(private val context: PlatformContext): AudioManager {

    companion object {
        private const val TAG = "AudioManger"
    }

    private val soundBoard = SoundBoard(context)
    private var music: Audio? = null
    private var musicUri: SourceUri? = null

    override val isMusicPlaying: Boolean
        get() = when (music?.audioState?.value) {
            AudioState.Playing -> true
            AudioState.Paused -> true
            AudioState.Loading -> true
            else -> false
        }

    override fun playSound(uri: SourceUri, volume: Float) {
        if (!soundBoard.isRegistered(uri.path)) {
            soundBoard.register(SoundByte(uri.path))
        }
        soundBoard.mixer.play(uri.path, volume)
    }

    override fun playMusic(uri: SourceUri, loop: Boolean) {
        if (musicUri?.path == uri.path && isMusicPlaying) {
            return
        }
        musicUri = uri

        music?.release()
        music = Audio(context, uri, loop, true)

        Logger.info(TAG, "Play music: ${uri.path}")
    }

    override fun pauseMusic() {
        music?.pause()

        musicUri?.let { Logger.info(TAG, "Pause music: ${it.path}") }
    }

    override fun resumeMusic() {
        music?.play()

        musicUri?.let { Logger.info(TAG, "Resume music: ${it.path}") }
    }

    override fun stopMusic() {
        music?.stop()

        musicUri?.let { Logger.info(TAG, "Stop music: ${it.path}") }
    }

    override fun setMusicVolume(volume: Float) {
        music?.setVolume(volume)
    }

    override fun clear() {
        soundBoard.powerDown()
    }

    override fun shutdown() {
        music?.stop()
        music?.release()
        musicUri = null
        soundBoard.release()
    }
}