package com.game.engine.audio

expect class AudioManager() {
    fun playSound(id: String, volume: Float = 1f)
    fun playMusic(id: String, loop: Boolean = true)
    fun stopMusic()
}