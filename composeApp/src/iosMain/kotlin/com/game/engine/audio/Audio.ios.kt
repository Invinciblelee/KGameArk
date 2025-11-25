@file:OptIn(ExperimentalForeignApi::class)

package com.game.engine.audio

import com.game.engine.asset.AssetUri
import com.game.engine.asset.HttpUri
import com.game.engine.asset.SourceUri
import com.game.engine.context.PlatformContext
import com.game.engine.log.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioFramePosition
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioPlayerNodeBufferLoops
import platform.AVFAudio.AVAudioPlayerNodeBufferOptions
import platform.Foundation.NSURL

actual class Audio actual constructor(
    private val context: PlatformContext,
    private val uri: SourceUri,
    private val loop: Boolean,
    private val autoPlay: Boolean
) {
    private val tag = "Audio"
    private val _audioState = MutableStateFlow<AudioState>(AudioState.None)
    actual val audioState: StateFlow<AudioState> = _audioState.asStateFlow()

    private val engine = AVAudioEngine()
    private val playerNode = AVAudioPlayerNode()
    private var audioFile: AVAudioFile? = null
    private var seekFrame: AVAudioFramePosition = 0
    private var audioSampleRate: Double = 0.0
    private var audioLengthFrames: AVAudioFramePosition = 0

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        load()
    }

    private fun load() {
        _audioState.value = AudioState.Loading
        try {
            val url = when (uri) {
                is HttpUri -> NSURL(string = uri.path)
                is AssetUri -> NSURL(string = uri.path)
                else -> throw IllegalStateException("unsupported uri: $uri")
            }

            val audioFile = AVAudioFile(forReading = url, error = null).also {
                this.audioFile = it
            }

            val procFormat = audioFile.processingFormat
            audioSampleRate = procFormat.sampleRate
            audioLengthFrames = audioFile.length

            engine.attachNode(playerNode)
            engine.connect(playerNode, engine.mainMixerNode, procFormat)
            engine.startAndReturnError(null)

            _audioState.value = AudioState.Ready
            if (autoPlay) play()
        } catch (e: Exception) {
            Logger.error(tag, "load:failure", e)
            _audioState.value = AudioState.Error("load:failure: $e")
        }
    }

    actual fun play() {
        try {
            when (audioState.value) {
                AudioState.Loading,
                AudioState.Playing -> return
                is AudioState.None -> {
                    throw Exception ("AudioState.NONE: load() not run yet")
                }
                is AudioState.Error -> {
                    throw Exception("AudioState.ERROR: ${(audioState.value as AudioState.Error).message}")
                }
                AudioState.Paused,
                AudioState.Ready,
                AudioState.Completed -> {
                    if (!playerNode.playing) {
                        scheduleNextSegment()
                        playerNode.play()
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
            if (playerNode.playing) {
                playerNode.pause()
                _audioState.value = AudioState.Paused
            }
        } catch (e: Exception) {
            Logger.error(tag, "pause:failure", e)
            _audioState.value = AudioState.Error("pause:failure: $e")
        }
    }

    actual fun stop() {
        try {
            playerNode.stop()
            seekFrame = 0
            _audioState.value = AudioState.Ready
        } catch (e: Exception) {
            Logger.error(tag, "stop:failure", e)
            _audioState.value = AudioState.Error("stop:failure: $e")
        }
    }

    actual fun setVolume(rate: Float) {
        playerNode.volume = rate.coerceIn(0f, 1f)
    }

    actual fun release() {
        try {
            _audioState.value = AudioState.None
            playerNode.stop()
            engine.stop()
            engine.detachNode(playerNode)
            scope.cancel()
        } catch (e: Exception) {
            Logger.error(tag, "release:failure", e)
            _audioState.value = AudioState.Error("release:failure: $e")
        }
    }

    private fun scheduleNextSegment() {
        audioFile?.let { file ->
            val buffer = AVAudioPCMBuffer(
                pCMFormat = file.processingFormat,
                frameCapacity = file.length.toUInt()
            )
            file.readIntoBuffer(buffer, error = null)
            seekFrame = file.length

            val options: AVAudioPlayerNodeBufferOptions = if (loop) {
                AVAudioPlayerNodeBufferLoops
            } else {
                0u
            }

            playerNode.scheduleBuffer(
                buffer = buffer,
                atTime = null,
                options = options,
                completionHandler = {
                    if (!loop) {
                        _audioState.value = AudioState.Completed
                    }
                }
            )
        }
    }
}