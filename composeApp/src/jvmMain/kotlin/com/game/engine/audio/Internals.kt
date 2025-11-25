package com.game.engine.audio

import com.game.engine.asset.AssetUri
import com.game.engine.asset.HttpUri
import com.game.engine.asset.SourceUri
import java.net.URL
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import kotlin.io.path.Path
import kotlin.math.log10

private fun AssetUri.toResourceURL(): URL {
    val entry = "composeResources/${path.substringAfterLast("/composeResources/")}"
    return Thread.currentThread().contextClassLoader.getResource(entry)
        ?: throw IllegalStateException("load:The path provided was invalid: $path")
}

internal fun getAudioInputStream(uri: SourceUri): AudioInputStream {
    val originalStream = when (uri) {
        is AssetUri -> {
            AudioSystem.getAudioInputStream(uri.toResourceURL())
        }

        is HttpUri -> {
            AudioSystem.getAudioInputStream(Path(uri.path).toUri().toURL())
                ?: throw IllegalStateException("load:The URL provided was invalid or failed to load")
        }

        else -> {
            throw IllegalStateException("load:unsupported uri: $uri")
        }
    }

    val originalFormat = originalStream.format
    val stream: AudioInputStream

    if (originalFormat.encoding == AudioFormat.Encoding.PCM_SIGNED) {
        stream = originalStream
    } else {
        val pcmFormat = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            originalFormat.sampleRate,
            16,
            originalFormat.channels,
            originalFormat.channels * 2,
            originalFormat.sampleRate,
            false
        )

        stream = AudioSystem.getAudioInputStream(pcmFormat, originalStream)
    }
    return stream
}

internal fun Clip.setVolume(rate: Float): Boolean {
    if (isControlSupported(FloatControl.Type.MASTER_GAIN)) {
        // 1. 获取控制权
        val volumeControl = getControl(FloatControl.Type.MASTER_GAIN) as FloatControl

        // 2. 计算分贝 (dB)
        // 人耳对音量的感知是对数的。
        // 公式: dB = 20 * log10(amplitude)
        // 如果 rate <= 0，直接设置为控件允许的最小值（静音）
        val newGain = if (rate <= 0f) {
            volumeControl.minimum
        } else {
            val db = 20f * log10(rate)
            // 限制范围，防止超出硬件允许的最大/最小值
            db.coerceIn(volumeControl.minimum, volumeControl.maximum)
        }

        // 4. 设置新值
        volumeControl.value = newGain

        return true
    }
    return false
}