package com.game.engine.audio

import com.game.engine.asset.SourceUri
import com.game.engine.context.PlatformContext

interface AudioManager {

    val isMusicPlaying: Boolean

    fun playSound(uri: SourceUri, volume: Float = 1f)
    fun playMusic(uri: SourceUri, loop: Boolean = false)
    fun pauseMusic()
    fun resumeMusic()
    fun stopMusic()
    fun setMusicVolume(volume: Float)
    fun clear()
    fun shutdown()

}

class DefaultAudioManager(private val context: PlatformContext): AudioManager {
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
    }

    override fun pauseMusic() {
        music?.pause()
    }

    override fun resumeMusic() {
        music?.play()
    }

    override fun stopMusic() {
        music?.stop()
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