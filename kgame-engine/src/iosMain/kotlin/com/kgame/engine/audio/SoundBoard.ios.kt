@file:OptIn(ExperimentalForeignApi::class)

package com.kgame.engine.audio

import com.kgame.engine.core.PlatformContext
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioMixerNode
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPlayerNode
import platform.Foundation.NSURL

actual class SoundBoard actual constructor(context: PlatformContext, val maxStreams: Int) {

    private class PooledNode(
        val node: AVAudioPlayerNode,
        val buffer: AVAudioPCMBuffer,
        var inUse: Boolean = false
    ) {
        fun play(volume: Float) {
            node.volume = volume
            node.scheduleBuffer(buffer, completionHandler = { reset() })
            node.play()
        }

        fun reset() {
            inUse = false
        }
    }

    private val soundBytes = mutableMapOf<String, SoundByte>()
    private val soundPools = mutableMapOf<String, ArrayDeque<PooledNode>>()

    actual val mixer: MixerChannel = MixerChannel()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val engine = AVAudioEngine()
    private val mixerNode = AVAudioMixerNode()

    init {
        setupEngine()
        startMixer()
    }

    private fun setupEngine() {
        val fmt = engine.mainMixerNode.outputFormatForBus(0u)
        engine.attachNode(mixerNode)
        engine.connect(mixerNode, engine.mainMixerNode, fmt)
        engine.startAndReturnError(null)
    }

    actual fun isRegistered(name: String): Boolean = soundBytes.containsKey(name)

    actual fun register(soundByte: SoundByte) {
        soundBytes[soundByte.name] = soundByte
    }

    actual fun register(soundBytes: List<SoundByte>) {
        soundBytes.forEach { register(it) }
    }

    actual fun powerUp() {
        soundBytes.values.forEach { loadPool(it) }
    }

    actual fun powerDown() {
        soundPools.values.forEach { pool ->
            pool.forEach { it.reset() }
            pool.clear()
        }
        soundPools.clear()
    }

    actual fun release() {
        scope.cancel()
        mixer.close()
        powerDown()
        engine.stop()
        engine.detachNode(mixerNode)
    }

    private fun decodeToBuffer(soundByte: SoundByte): AVAudioPCMBuffer {
        val url = NSURL(string = soundByte.path)
        val file = AVAudioFile(forReading = url, error = null)
        return AVAudioPCMBuffer(
            pCMFormat = file.processingFormat,
            frameCapacity = file.length.toUInt()
        ).apply { file.readIntoBuffer(this, error = null) }
    }

    private fun loadPool(soundByte: SoundByte): ArrayDeque<PooledNode> =
        soundPools.getOrPut(soundByte.name) {
            val buffer = decodeToBuffer(soundByte)
            ArrayDeque<PooledNode>(maxStreams).apply {
                repeat(maxStreams) {
                    val node = AVAudioPlayerNode()
                    engine.attachNode(node)
                    engine.connect(node, mixerNode, buffer.format)
                    addLast(PooledNode(node, buffer))
                }
            }
        }

    private fun obtainNode(name: String): PooledNode? {
        val pool = loadPool(requireNotNull(soundBytes[name]) { "Sound $name not registered" })
        pool.find { !it.inUse }?.let { it.inUse = true; return it }
        return null
    }

    private fun startMixer() {
        scope.launch {
            while (isActive) {
                val sound = mixer.receiveCatching().getOrNull() ?: break
                val pooled = obtainNode(sound.name) ?: continue
                pooled.play(sound.volume)
            }
        }
    }
}