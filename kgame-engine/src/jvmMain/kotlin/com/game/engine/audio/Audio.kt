package com.game.engine.audio

import com.game.engine.asset.SourceUri
import com.game.engine.core.PlatformContext
import com.game.engine.log.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.LineEvent
import kotlin.IllegalStateException

actual class Audio actual constructor(
    val context: PlatformContext,
    private val uri: SourceUri,
    private val loop: Boolean,
    private val autoPlay: Boolean
) {

    private val tag = "Audio"

    private val _audioState = MutableStateFlow<AudioState>(AudioState.None)
    actual val audioState: StateFlow<AudioState> = _audioState.asStateFlow()

    private var clip: Clip = AudioSystem.getClip()
    private var cursor: Long = 0L

    init {
        load()
    }

    private fun load() {
        try {
            _audioState.value = AudioState.Loading
            val stream = getAudioInputStream(uri)
            clip.open(stream)
            clip.loop(if (loop) Clip.LOOP_CONTINUOUSLY else 0)
            when (clip.isOpen) {
                true -> {
                    _audioState.value = AudioState.Ready
                    if (autoPlay) {
                        play()
                    }
                }

                false -> _audioState.value = AudioState.Loading
            }
            clip.addLineListener { e ->
                if (!loop && e.type == LineEvent.Type.STOP && clip.framePosition == clip.frameLength) {
                    _audioState.value = AudioState.Completed
                }
            }
        } catch (e: Exception) {
            Logger.error(tag, "load:failure", e)
            _audioState.value = AudioState.Error("load:failure: $e")
        }
    }

    actual fun setVolume(volume: Float) {
        try {
            if (!clip.setVolume(volume)) {
                Logger.warn(tag, "setVolume:unsupported")
            }
        } catch (e: Exception) {
            Logger.error(tag, "setVolume:failure", e)
            _audioState.value = AudioState.Error("setVolume:failure: $e")
        }
    }

    actual fun play() {
        try {
            when (audioState.value) {
                is AudioState.Loading,
                is AudioState.Playing -> return

                is AudioState.None -> {
                    throw IllegalStateException("AudioState.NONE: mediaPlayer not initialized")
                }

                is AudioState.Ready,
                is AudioState.Paused -> {
                    if (loop) {
                        clip.loop(Clip.LOOP_CONTINUOUSLY)
                    } else {
                        clip.microsecondPosition = cursor
                        clip.start()
                    }
                    _audioState.value = AudioState.Playing
                }

                is AudioState.Error,
                is AudioState.Completed -> {
                    if (loop) {
                        clip.loop(Clip.LOOP_CONTINUOUSLY)
                    } else {
                        clip.microsecondPosition = cursor
                        clip.start()
                    }
                    _audioState.value = AudioState.Playing
                }
            }
        } catch (e: Exception) {
            Logger.error(tag, "play:failure", e)
            _audioState.value = AudioState.Error("play:failure: $e")
        }
    }

    actual fun pause() {
        try {
            if (clip.isRunning) {
                cursor = clip.microsecondPosition
                clip.stop()
                _audioState.value = AudioState.Paused
            }
        } catch (e: Exception) {
            Logger.error(tag, "pause:failure", e)
            _audioState.value = AudioState.Error("pause:failure: $e")
        }
    }

    actual fun stop() {
        try {
            if (clip.isRunning) {
                clip.stop()
                cursor = 0L
                _audioState.value = AudioState.Ready
            }
        } catch (e: Exception) {
            Logger.error(tag, "stop:failure", e)
            _audioState.value = AudioState.Error("stop:failure: $e")
        }
    }

    actual fun release() {
        try {
            _audioState.value = AudioState.None
            clip.flush()
        } catch (e: Exception) {
            Logger.error(tag, "release:failure", e)
            _audioState.value = AudioState.Error("release:failure: $e")
        }
    }

}