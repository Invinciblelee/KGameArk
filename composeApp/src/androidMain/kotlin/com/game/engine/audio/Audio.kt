@file:Suppress("ConvertSecondaryConstructorToPrimary")

package com.game.engine.audio

import android.media.MediaPlayer
import com.game.engine.asset.AssetUri
import com.game.engine.asset.HttpUri
import com.game.engine.asset.SourceUri
import com.game.engine.context.PlatformContext
import com.game.engine.log.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.IllegalStateException

/**
 * Play audio from a url ([String]).
 *
 * Example:
 * ```
 * val audioUri = HttpUri("https://dare.wisc.edu/wp-content/uploads/sites/1051/2008/11/MS072.mp3")
 * val audio = Audio(context, audioUri, autoPlay = true) // AutoPlay is marked "true"
 * ```
 */

actual class Audio actual constructor(
    val context: PlatformContext,
    private val uri: SourceUri,
    private val loop: Boolean,
    private val autoPlay: Boolean
) {

    private val tag = "Audio"

    private val _audioState = MutableStateFlow<AudioState>(AudioState.None)
    actual val audioState: StateFlow<AudioState> = _audioState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null

    init {
        load()
    }

    private fun load() {
        try {
            _audioState.value = AudioState.Loading
            mediaPlayer = MediaPlayer().apply {
                when (uri) {
                    is HttpUri -> {
                        setDataSource(uri.path)
                    }

                    is AssetUri -> {
                        setDataSource(
                            context.assets.openFd(
                                uri.path.removePrefix("file:///android_asset/")
                            )
                        )
                    }

                    else -> {
                        throw IllegalStateException("load:unsupported uri: $uri")
                    }
                }

                isLooping = loop

                prepareAsync()
                setOnPreparedListener {
                    // Ready to play
                    _audioState.value = AudioState.Ready
                    if (autoPlay) {
                        play()
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Logger.error(tag, "player:error: $what, $extra")
                    _audioState.value = AudioState.Error("player:error: $what, $extra")
                    false
                }
                setOnCompletionListener {
                    if (!loop) {
                        _audioState.value = AudioState.Completed
                    }
                }
            }
        } catch (e: Exception) {
            Logger.error(tag, "load:failure", e)
            _audioState.value = AudioState.Error("load:failure: $e")
        }
    }

    actual fun setVolume(volume: Float) {
        try {
            mediaPlayer?.setVolume(volume, volume)
        } catch (e: Exception) {
            Logger.error(tag, "setVolume:failure", e)
            _audioState.value = AudioState.Error("setVolume:failure: $e")
        }
    }

    actual fun play() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    _audioState.value = AudioState.Playing
                    return
                }

                when (audioState.value) {
                    is AudioState.Loading,
                    is AudioState.Playing -> Unit
                    is AudioState.None -> {
                        throw IllegalStateException("AudioState.NONE: mediaPlayer not initialized")
                    }
                    is AudioState.Paused,
                    is AudioState.Ready -> {
                        it.start()
                        _audioState.value = AudioState.Playing
                    }
                    is AudioState.Error,
                    is AudioState.Completed -> {
                        it.seekTo(0)
                        it.start()
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
            mediaPlayer?.let {
                if (it.isPlaying) {
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
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
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
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Logger.error(tag, "release:failure", e)
            _audioState.value = AudioState.Error("release:failure: $e")
        }
    }
}