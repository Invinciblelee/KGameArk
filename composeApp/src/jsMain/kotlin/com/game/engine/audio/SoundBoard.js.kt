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

    private class PooledClip(val ctx: dynamic, var buffer: dynamic, var inUse: Boolean = false) {
        private var source: dynamic? = null
        fun play(volume: Float) {
            source = ctx.createBufferSource()
            source.buffer = buffer
            val gain = ctx.createGain()
            gain.gain.value = volume
            source.connect(gain)
            gain.connect(ctx.destination)
            source.start(0.0)
        }

        fun reset() {
            source?.stop(0.0)
            source = null
            inUse = false
        }
    }

    private val soundBytes = mutableMapOf<String, SoundByte>()
    private val soundPool = mutableMapOf<String, ArrayDeque<PooledClip>>()

    actual val mixer: MixerChannel = MixerChannel()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val ctx = js("new (window.AudioContext || window.webkitAudioContext)()")

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
        soundPool.values.forEach {
            it.forEach { pooled -> pooled.reset() }
        }
        soundPool.clear()
    }

    actual fun release() {
        scope.cancel()
        mixer.close()
        powerDown()
        ctx.close()
    }

    private fun loadPool(soundByte: SoundByte): ArrayDeque<PooledClip> {
        return soundPool.getOrPut(soundByte.name) {
            val buffer = ctx.decodeAudioData(
                js("fetch(url)").await().arrayBuffer().await()
            ).await()
            ArrayDeque<PooledClip>(maxStreams).apply {
                repeat(maxStreams) { addLast(PooledClip(ctx, buffer)) }
            }
        }
    }

    private fun obtainClip(name: String): PooledClip? {
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
                    delay(timeMillis = (pooled.buffer.duration * 1000L).toLong())
                    pooled.reset()
                }
            }
        }
    }
}