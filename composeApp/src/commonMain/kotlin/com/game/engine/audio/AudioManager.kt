@file:OptIn(ExperimentalBasicSound::class)

package com.game.engine.audio

import app.lexilabs.basic.sound.Audio
import app.lexilabs.basic.sound.ExperimentalBasicSound
import app.lexilabs.basic.sound.SoundBoard
import app.lexilabs.basic.sound.SoundByte
import app.lexilabs.basic.sound.play
import com.game.engine.asset.DataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AudioManager(val context: Any?) {

    private val soundBoard: SoundBoard = SoundBoard(context)

    private var currentAudio: Audio? = null

    private val soundByteCache = HashMap<String, SoundByte>()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var loopJob: Job? = null

    fun playSound(source: DataSource) {
        val soundByte = soundByteCache.getOrPut(source.uri) {
            SoundByte(
                name = source.uri,
                localPath = source.uri
            ).also {
                soundBoard.load(it)
                soundBoard.powerUp()
            }
        }
        soundBoard.mixer.play(soundByte)
    }

    fun playMusic(source: DataSource, loop: Boolean = true) {
        stopMusic()

        val newAudio = Audio(source.uri, true)
        newAudio.load()

        currentAudio = newAudio

        if (loop) {
            loopJob?.cancel()
            loopJob = scope.launch {
                newAudio.audioState.collect {
                   println("Audio State: $it")
                }
            }
        }
    }

    fun stopMusic() {
        currentAudio?.stop()
        currentAudio = null
    }


    fun shutdown() {
        scope.cancel()
        stopMusic()
        soundBoard.powerDown()
    }

}