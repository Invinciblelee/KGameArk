@file:OptIn(ExperimentalForeignApi::class)

package com.game.engine.audio

import com.game.engine.context.PlatformContext
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
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

actual class SoundBoard actual constructor(context: PlatformContext) {

    private companion object {
        const val MAX_POLYPHONY = 4
    }

    private class PooledNode(val node: AVAudioPlayerNode, var inUse: Boolean = false) {
        fun reset() {
            dispatch_async(dispatch_get_main_queue()) {
                if (node.playing) {
                    this.node.stop()
                }
            }
            inUse = false
        }
    }

    private val soundBytes = mutableMapOf<String, SoundByte>()
    private val soundPools = mutableMapOf<String, ArrayDeque<PooledNode>>()
    private val soundBuffers = mutableMapOf<String, AVAudioPCMBuffer>()

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

    actual fun powerUp() = soundBytes.values.forEach { loadPool(it) }

    actual fun powerDown() {
        soundPools.values.forEach { pool ->
            pool.forEach { it.reset() }
            pool.clear()
        }
        soundPools.clear()
        soundBuffers.clear()
    }

    actual fun release() {
        scope.cancel()
        mixer.close()
        powerDown()
        engine.stop()
        engine.detachNode(mixerNode)
    }

    private fun decodeToBuffer(soundByte: SoundByte): AVAudioPCMBuffer =
        soundBuffers.getOrPut(soundByte.name) {
            val url = NSURL(string = soundByte.path)
            val file = AVAudioFile(forReading = url, error = null)
            AVAudioPCMBuffer(
                pCMFormat = file.processingFormat,
                frameCapacity = file.length.toUInt()
            ).apply { file.readIntoBuffer(this, error = null) }
        }

    private fun loadPool(soundByte: SoundByte): ArrayDeque<PooledNode> =
        soundPools.getOrPut(soundByte.name) {
            val buffer = decodeToBuffer(soundByte)
            ArrayDeque<PooledNode>(MAX_POLYPHONY).apply {
                repeat(MAX_POLYPHONY) {
                    val node = AVAudioPlayerNode()
                    engine.attachNode(node)
                    engine.connect(node, mixerNode, buffer.format)
                    addLast(PooledNode(node))
                }
            }
        }

    private fun obtainNode(name: String): PooledNode? {
        val pool = loadPool(requireNotNull(soundBytes[name]) { "Sound $name not registered" })
        pool.find { !it.inUse }?.let { it.inUse = true; return it }
        return null
    }

    private fun releaseNode(pooled: PooledNode) = pooled.reset()

    private fun startMixer() {
        scope.launch {
            while (isActive) {
                val sound = mixer.receiveCatching().getOrNull() ?: break
                val pooled = obtainNode(sound.name) ?: continue
                val buffer = decodeToBuffer(requireNotNull(soundBytes[sound.name]))
                pooled.node.volume = sound.volume
                pooled.node.scheduleBuffer(buffer, completionHandler = { releaseNode(pooled) })
                pooled.node.play()
            }
        }
    }
}