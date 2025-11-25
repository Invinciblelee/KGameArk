package com.game.engine.audio

import com.game.engine.asset.AssetUri
import com.game.engine.context.PlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

actual class SoundBoard actual constructor(context: PlatformContext) {

    private companion object {
        const val MAX_POLYPHONY = 4
    }

    private class PooledClip(val clip: Clip, var inUse: Boolean = false) {
        fun reset() {
            if (clip.isRunning) clip.stop()
            clip.microsecondPosition = 0
            inUse = false
        }
    }

    private val soundBytes = ConcurrentHashMap<String, SoundByte>()
    private val soundPool = ConcurrentHashMap<String, ArrayDeque<PooledClip>>()

    actual val mixer: MixerChannel = MixerChannel()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        startMixer()
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
        soundPool.values.forEach { pool ->
            pool.forEach { clip -> clip.reset() }
        }
        soundPool.clear()
    }

    actual fun release() {
        scope.cancel()
        mixer.close()
        powerDown()
    }

    private fun loadPool(soundByte: SoundByte): ArrayDeque<PooledClip> =
        soundPool.getOrPut(soundByte.name) {
            ArrayDeque<PooledClip>(MAX_POLYPHONY).apply {
                getAudioInputStream(AssetUri(soundByte.path)).use { sourceIn ->
                    val format   = sourceIn.format
                    val pcmBytes = sourceIn.readAllBytes()
                    repeat(MAX_POLYPHONY) {
                        val clip = AudioSystem.getClip()
                        clip.open(format, pcmBytes, 0, pcmBytes.size)
                        addLast(PooledClip(clip))
                    }
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
                pooled.clip.setVolume(sound.volume)
                pooled.clip.start()
                launch {
                    pooled.clip.drain()
                    pooled.reset()
                }
            }
        }
    }
}