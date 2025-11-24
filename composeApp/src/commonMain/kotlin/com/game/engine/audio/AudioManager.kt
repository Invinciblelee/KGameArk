package com.game.engine.audio

import com.game.engine.asset.SourceUri
import com.game.engine.context.PlatformContext

expect class AudioManager(context: PlatformContext) {

    fun playSound(uri: SourceUri, volume: Float = 1f)
    fun playMusic(uri: SourceUri, loop: Boolean = false)
    fun pauseMusic()
    fun resumeMusic()
    fun stopMusic()
    fun setMusicVolume(volume: Float)
    fun clear()
    fun shutdown()

}