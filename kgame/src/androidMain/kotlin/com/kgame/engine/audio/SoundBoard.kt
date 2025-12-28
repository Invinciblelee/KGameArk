package com.kgame.engine.audio

import android.media.AudioAttributes
import android.media.SoundPool
import com.kgame.engine.log.Logger
import com.kgame.platform.PlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

actual class SoundBoard actual constructor(val context: PlatformContext, maxStreams: Int) {

    private val tag: String = "SoundBoard"
    private val soundPool: SoundPool
    private val soundBytes: MutableMap<String, SoundByte> = mutableMapOf()
    private val audioIds: MutableMap<String, Int> = mutableMapOf()
    private val loadingIds: MutableMap<String, Int> = mutableMapOf()
    private val pendingSounds: MutableMap<Int, Sound> = mutableMapOf()

    actual val mixer: MixerChannel = MixerChannel()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(maxStreams)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool.setOnLoadCompleteListener { soundPool, sampleId, status ->
            loadingIds.values.remove(sampleId)
            val sound = pendingSounds.remove(sampleId)
            if (status == 0 && sound != null) {
                soundPool.play(sampleId, sound.volume, sound.volume, 1, 0, 1f)
            }
            Logger.info(tag, "Sound loaded: $sampleId, status: $status")
        }

        startMixer()
    }

    private fun startMixer() {
        Logger.info(tag, "startMixer:starting")
        scope.launch {
            while (isActive) {
                val sound = mixer.receiveCatching().getOrNull() ?: break
                val id = audioIds[sound.name]
                if (id != null) {
                    soundPool.play(id, sound.volume, sound.volume, 1, 0, 1f)
                } else {
                    val soundByte = soundBytes[sound.name]
                    val sampleId = loadSound(requireNotNull(soundByte) {
                        "SoundByte not registered: ${sound.name}"
                    })
                    pendingSounds[sampleId] = sound
                }
            }
        }
    }

    private fun loadSound(soundByte: SoundByte): Int {
        Logger.info(tag, "loadSound:loading ${soundByte.name}")
        loadingIds[soundByte.name]?.let { return it }

        val path = soundByte.path.removePrefix("file:///android_asset/")
        val fd = context.assets.openFd(path)
        val sampleId = soundPool.load(fd, 1)
        loadingIds[soundByte.name] = sampleId
        audioIds[soundByte.name] = sampleId
        return sampleId
    }

    actual fun isRegistered(name: String): Boolean {
        return soundBytes.containsKey(name)
    }

    actual fun register(soundByte: SoundByte) {
        soundBytes[soundByte.name] = soundByte
    }

    actual fun register(soundBytes: List<SoundByte>) {
        for (soundByte in soundBytes) {
            register(soundByte)
        }
    }

    actual fun powerUp() {
        Logger.info(tag, "powerUp:starting")
        for (soundByte in soundBytes.values) {
            loadSound(soundByte)
        }
    }

    actual fun powerDown() {
        Logger.info(tag, "powerDown:stopping")
    }

    actual fun release() {
        Logger.info(tag, "release:stopping")
        scope.cancel()
        mixer.close()
        soundPool.release()
        audioIds.clear()
        soundBytes.clear()
    }
}