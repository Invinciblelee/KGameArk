package com.game.engine.audio

import com.game.engine.asset.AssetUri
import com.game.engine.asset.HttpUri
import com.game.engine.asset.SourceUri
import com.game.engine.context.PlatformContext
import com.game.engine.log.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URL
import java.util.Map.entry
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import kotlin.io.path.Path
import kotlin.math.log10


actual class Audio actual constructor(
    val context: PlatformContext,
    private val uri: SourceUri,
    private val loop: Boolean,
    private val autoPlay: Boolean
) {

    private val tag = "Basic-Sound Audio"

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

            val stream = when (uri) {
                is AssetUri -> {
                    AudioSystem.getAudioInputStream(uri.toResourceURL())
                }

                is HttpUri -> {
                    AudioSystem.getAudioInputStream(Path(uri.path).toUri().toURL())
                        ?: throw IllegalStateException("load:The URL provided was invalid or failed to load")
                }

                else -> {
                    throw IllegalStateException("load:unsupported uri: $uri")
                }
            }

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
        } catch (e: Exception) {
            Logger.error(tag, "load:failure", e)
            _audioState.value = AudioState.Error("load:failure: $e")
        }
    }

    private fun AssetUri.toResourceURL(): URL {
        val entry = "composeResources/${path.substringAfterLast("/composeResources/")}"
        return Thread.currentThread().contextClassLoader.getResource(entry)
            ?: throw IllegalStateException("load:The path provided was invalid: $path")
    }

    actual fun setVolume(rate: Float) {
        try {
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                // 1. 获取控制权
                val volumeControl = clip.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl

                // 2. 计算分贝 (dB)
                // 人耳对音量的感知是对数的。
                // 公式: dB = 20 * log10(amplitude)
                // 如果 rate <= 0，直接设置为控件允许的最小值（静音）
                val newGain = if (rate <= 0f) {
                    volumeControl.minimum
                } else {
                    val db = 20f * log10(rate)
                    // 限制范围，防止超出硬件允许的最大/最小值
                    db.coerceIn(volumeControl.minimum, volumeControl.maximum)
                }

                // 4. 设置新值
                volumeControl.value = newGain
            } else {
                Logger.warn(tag, "setVolume:unsupported")
            }
        } catch (e: Exception) {
            Logger.error(tag, "setVolume:failure", e)
            _audioState.value = AudioState.Error("setVolume:failure: $e")
        }
    }

    actual fun play() {
        when (audioState.value) {
            is AudioState.Loading,
            is AudioState.Playing -> { /** do nothing **/ }
            is AudioState.None -> {
                throw Exception ("AudioState.NONE: load() not run yet")
            }
            is AudioState.Error -> {
                throw Exception("AudioState.ERROR: ${(audioState.value as AudioState.Error).message}")
            }
            is AudioState.Paused -> {
                clip.microsecondPosition = cursor
                clip.start()
                _audioState.value = AudioState.Playing
            }
            is AudioState.Ready -> {
                clip.start()
                _audioState.value = AudioState.Playing
            }
            is AudioState.Completed -> {
                clip.microsecondPosition = 0L
                clip.start()
                _audioState.value = AudioState.Playing
            }
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