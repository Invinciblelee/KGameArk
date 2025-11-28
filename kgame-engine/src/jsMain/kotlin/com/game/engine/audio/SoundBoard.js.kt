package com.game.engine.audio

import com.game.engine.context.PlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import web.audio.AudioBuffer
import web.audio.AudioContext
import web.audio.AudioContextState
import web.audio.decodeAudioData
import web.audio.resume
import web.audio.suspended
import web.events.EventHandler
import web.html.HtmlTagName.source
import web.http.arrayBuffer
import web.http.fetch

actual class SoundBoard actual constructor(context: PlatformContext, val maxStreams: Int) {

    private class PooledClip(val context: AudioContext, var buffer: AudioBuffer, var inUse: Boolean = false) {
        private val gainNode = context.createGain().apply {
            connect(context.destination)
        }

        suspend fun play(volume: Float) {
            if (context.state == AudioContextState.suspended) {
                context.resume()
            }

            val source = context.createBufferSource()
            source.buffer = buffer
            source.connect(gainNode)
            gainNode.gain.setValueAtTime(volume, context.currentTime)
            source.onended = EventHandler {
                source.disconnect(gainNode)
                reset()
            }
            source.start(0.0)
        }

        fun reset() {
            inUse = false
        }
    }

    private val soundBytes = mutableMapOf<String, SoundByte>()
    private val soundPool = mutableMapOf<String, ArrayDeque<PooledClip>>()

    actual val mixer: MixerChannel = MixerChannel()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val context = js("new (window.AudioContext || window.webkitAudioContext)()")

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
        context.close()
    }

    private suspend fun loadPool(soundByte: SoundByte): ArrayDeque<PooledClip> {
        return soundPool.getOrPut(soundByte.name) {
            val response = fetch(soundByte.path)
            val arrayBuffer = response.arrayBuffer()
            val buffer = context.unsafeCast<AudioContext>().decodeAudioData(arrayBuffer)
            ArrayDeque<PooledClip>(maxStreams).apply {
                repeat(maxStreams) { addLast(PooledClip(context, buffer)) }
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
            }
        }
    }
}