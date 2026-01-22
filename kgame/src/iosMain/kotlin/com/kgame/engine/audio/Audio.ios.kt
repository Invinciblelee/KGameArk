@file:OptIn(ExperimentalForeignApi::class)

package com.kgame.engine.audio

import com.kgame.platform.PlatformContext
import com.kgame.engine.asset.AssetUri
import com.kgame.engine.asset.HttpUri
import com.kgame.engine.asset.SourceUri
import com.kgame.engine.log.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFile
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

    private var cursor: Long = 0

    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        load()
    }

    private fun load() = coroutineScope.launch {
        _audioState.value = AudioState.Loading
        try {
            val url = when (uri) {
                is HttpUri -> NSURL(string = uri.path)
                is AssetUri -> NSURL(string = uri.path)
                else -> throw IllegalStateException("unsupported uri: $uri")
            }

            val audioFile = AVAudioFile(forReading = url, error = null).also {
                this@Audio.audioFile = it
            }

            val procFormat = audioFile.processingFormat
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
            if (playerNode.playing) {
                _audioState.value = AudioState.Playing
                return
            }

            when (audioState.value) {
                is AudioState.Loading,
                is AudioState.Playing -> return
                is AudioState.None -> {
                    throw IllegalStateException("AudioState.NONE: mediaPlayer not initialized")
                }
                is AudioState.Ready,
                is AudioState.Paused -> {
                    startPlay(cursor)
                    _audioState.value = AudioState.Playing
                }
                is AudioState.Error,
                is AudioState.Completed -> {
                    startPlay(0)
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
                val nodeTime = playerNode.lastRenderTime ?: return
                val playerTime = playerNode.playerTimeForNodeTime(nodeTime) ?: return
                cursor = playerTime.sampleTime

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
            _audioState.value = AudioState.Ready
        } catch (e: Exception) {
            Logger.error(tag, "stop:failure", e)
            _audioState.value = AudioState.Error("stop:failure: $e")
        }
    }

    actual fun setVolume(volume: Float) {
        playerNode.volume = volume.coerceIn(0f, 1f)
    }

    actual fun release() {
        try {
            _audioState.value = AudioState.None
            playerNode.stop()
            engine.stop()
            engine.detachNode(playerNode)
            coroutineScope.cancel()
        } catch (e: Exception) {
            Logger.error(tag, "release:failure", e)
            _audioState.value = AudioState.Error("release:failure: $e")
        }
    }

    private fun startPlay(fromFrame: Long = 0) {
        val file = audioFile ?: return
        val start = fromFrame.coerceIn(0, file.length)
        val remaining = file.length - start
        if (remaining <= 0) return

        val buffer = AVAudioPCMBuffer(
            pCMFormat = file.processingFormat,
            frameCapacity = remaining.toUInt()
        )

        file.framePosition = start
        file.readIntoBuffer(buffer, error = null)

        playerNode.stop()
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
        playerNode.play()
    }

}