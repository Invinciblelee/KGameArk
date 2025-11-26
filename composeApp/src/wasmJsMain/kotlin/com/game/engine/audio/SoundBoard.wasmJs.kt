@file:OptIn(ExperimentalWasmJsInterop::class)

package com.game.engine.audio

import com.game.engine.context.PlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

actual class SoundBoard actual constructor(context: PlatformContext, val maxStreams: Int) {

    private class PooledClip(val ctx: JsAny, val buffer: JsAny, var inUse: Boolean = false) {
        private var source: JsAny? = null
        fun play(volume: Float) {
            val src = createBufferSource(ctx)
            setBuffer(src, buffer)            // ← 显式函数
            val g = createGain(ctx)
            setGain(g, volume)
            connect(src, g)
            connect(g, getDestination(ctx))
            startSource(src, 0.0)
            source = src
        }
        fun reset() {
            source?.let { stopSource(it, 0.0) }
            source = null
            inUse = false
        }
    }

    private val soundBytes = mutableMapOf<String, SoundByte>()
    private val soundPool = mutableMapOf<String, ArrayDeque<PooledClip>>()

    actual val mixer: MixerChannel = MixerChannel()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val ctx = createAudioContext()

    init { startMixer() }

    actual fun isRegistered(name: String) = soundBytes.containsKey(name)
    actual fun register(soundByte: SoundByte) { soundBytes[soundByte.name] = soundByte }
    actual fun register(soundBytes: List<SoundByte>) { soundBytes.forEach { register(it) } }

    actual fun powerUp() {
        scope.launch {
            soundBytes.values.forEach { soundByte ->
                loadPool(soundByte)
            }
        }
    }

    actual fun powerDown() {
        soundPool.values.forEach { it.forEach { it.reset() } }
        soundPool.clear()
    }

    actual fun release() {
        scope.cancel(); mixer.close(); powerDown()
    }

    private suspend fun loadPool(soundByte: SoundByte): ArrayDeque<PooledClip> {
        return soundPool.getOrPut(soundByte.name) {
            val buffer = fetchAndDecode(soundByte.path)
            ArrayDeque<PooledClip>(maxStreams).apply {
                repeat(maxStreams) { addLast(PooledClip(ctx, buffer)) }
            }
        }
    }

    private suspend fun obtainClip(name: String): PooledClip? {
        val pool = loadPool(requireNotNull(soundBytes[name]) { "Sound $name not registered" })
        pool.find { !it.inUse }?.let { it.inUse = true; return it }
        return null
    }

    private fun startMixer() {
        scope.launch {
            while (isActive) {
                val sound = mixer.receiveCatching().getOrNull() ?: break
                val pooled = obtainClip(sound.name) ?: continue
                pooled.play(sound.volume)
                launch {
                    delay((getBufferDuration(pooled.buffer) * 1000).toLong())
                    pooled.reset()
                }
            }
        }
    }
}
