@file:OptIn(ExperimentalWasmJsInterop::class)

package com.kgame.engine.audio

import com.kgame.engine.asset.SourceUri
import com.kgame.engine.core.PlatformContext
import com.kgame.engine.log.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.w3c.dom.Audio

actual class Audio actual constructor(
    context: PlatformContext,
    private val uri: SourceUri,
    private val loop: Boolean,
    private val autoPlay: Boolean
) {
    private val tag = "Audio"

    private val _audioState = MutableStateFlow<AudioState>(AudioState.None)
    actual val audioState: StateFlow<AudioState> = _audioState.asStateFlow()

    private var player: Audio? = null
    private var cursor: Double = 0.0

    init {
        load()
    }

    private fun load() {
        try {
            _audioState.value = AudioState.Loading
            player = Audio(uri.path)
            player?.loop = loop
            player?.onended = {
                if (!loop) {
                    _audioState.value = AudioState.Completed
                }
            }
            player?.oncanplaythrough = {
                _audioState.value = AudioState.Ready
                if (autoPlay) {
                    play()
                }
            }
        } catch (e: Exception) {
            Logger.error(tag, "load:failure", e)
            _audioState.value = AudioState.Error("load:failure: $e")
        }
    }

    actual fun setVolume(volume: Float) {
        try {
            player?.let {
                it.volume = volume.toDouble()
            }
        } catch (e: Exception) {
            Logger.error(tag, "setVolume:failure", e)
            _audioState.value = AudioState.Error("setVolume:failure: $e")
        }
    }

    actual fun play() {
        try {
            player?.let {
                when (audioState.value) {
                    is AudioState.Loading,
                    is AudioState.Playing -> Unit
                    is AudioState.None -> {
                        throw IllegalStateException("AudioState.NONE: mediaPlayer not initialized")
                    }
                    is AudioState.Ready,
                    is AudioState.Paused -> {
                        it.currentTime = cursor
                        it.play()
                        _audioState.value = AudioState.Playing
                    }
                    is AudioState.Error,
                    is AudioState.Completed -> {
                        it.currentTime = 0.0
                        it.play()
                        _audioState.value = AudioState.Playing
                    }
                }
            }
        } catch (e: Exception) {
            Logger.error(tag, "play:failure", e)
            _audioState.value = AudioState.Error("play:failure: $e")
        }
    }

    actual fun pause() {
        try {
            player?.let {
                if (audioState.value is AudioState.Playing) {
                    cursor = it.currentTime
                    it.pause()
                    _audioState.value = AudioState.Paused
                }
            }
        } catch (e: Exception) {
            Logger.error(tag, "pause:failure", e)
            _audioState.value = AudioState.Error("pause:failure: $e")
        }
    }

    actual fun stop() {
        try {
            player?.let {
                if (audioState.value is AudioState.Playing) {
                    it.pause()
                    it.currentTime = 0.0
                    _audioState.value = AudioState.Ready
                }
            }
        } catch (e: Exception) {
            Logger.error(tag, "stop:failure", e)
            _audioState.value = AudioState.Error("stop:failure: $e")
        }
    }

    actual fun release() {
        try {
            _audioState.value = AudioState.None
            player?.let {
                it.pause()
                it.src = ""
                it.load()
            }
            player = null
        } catch (e: Exception) {
            Logger.error(tag, "release:failure", e)
            _audioState.value = AudioState.Error("release:failure: $e")
        }
    }
}