package com.game.engine.audio

import com.game.engine.asset.SourceUri
import com.game.engine.context.PlatformContext
import javafx.application.Platform
import javafx.scene.media.AudioClip
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import java.net.URI
import java.util.concurrent.ConcurrentHashMap


private fun SourceUri.toResourceURI(): URI {
    val resourcePath = path
        .substringAfterLast("/composeResources/")
        .let { "composeResources/$it" }
    val url = Thread.currentThread().contextClassLoader.getResource(resourcePath)
    requireNotNull(url) { "Resource not found: $resourcePath" }
    return url.toURI()
}

actual class AudioManager actual constructor(context: PlatformContext) {

    private val soundCache = ConcurrentHashMap<String, AudioClip>()
    private var musicPlayer: MediaPlayer? = null
    private var musicUri: SourceUri? = null

    private fun isMusicPlayerActive(player: MediaPlayer?): Boolean {
        return when (player?.status) {
            MediaPlayer.Status.PLAYING,
            MediaPlayer.Status.PAUSED,
            MediaPlayer.Status.STALLED,
            MediaPlayer.Status.READY -> true
            else -> false
        }
    }

    init {
        if (!Platform.isFxApplicationThread()) {
            Platform.startup {}
        }
    }

    actual fun playSound(uri: SourceUri, volume: Float) {
        Platform.runLater {
            val clip = soundCache.getOrPut(uri.path) {
                try {
                    AudioClip(uri.toResourceURI().toString())
                } catch (e: Exception) {
                    println("Error loading AudioClip: ${uri.path}")
                    e.printStackTrace()
                    return@runLater
                }
            }

            clip.volume = volume.toDouble().coerceIn(0.0, 1.0)
            clip.play()
        }
    }

    actual fun playMusic(uri: SourceUri, loop: Boolean) {
        Platform.runLater {
            // 1. 在 FX 线程上安全地检查当前状态
            if (musicUri?.path == uri.path && isMusicPlayerActive(musicPlayer)) {
                return@runLater
            }

            // 2. 更新 URI 跟踪
            musicUri = uri

            try {
                // 3. 清理旧资源
                musicPlayer?.stop()
                musicPlayer?.dispose()

                val media = Media(uri.toResourceURI().toString())
                musicPlayer = MediaPlayer(media).apply {
                    if (loop) {
                        cycleCount = MediaPlayer.INDEFINITE
                    }
                    play()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                musicUri = null
            }
        }
    }

    actual fun pauseMusic() {
        Platform.runLater {
            musicPlayer?.pause()
        }
    }

    actual fun resumeMusic() {
        Platform.runLater {
            musicPlayer?.play()
        }
    }

    actual fun stopMusic() {
        Platform.runLater {
            musicPlayer?.stop()
            musicPlayer?.seek(Duration.ZERO)
        }
    }

    actual fun setMusicVolume(volume: Float) {
        Platform.runLater {
            musicPlayer?.volume = volume.toDouble().coerceIn(0.0, 1.0)
        }
    }

    actual fun clear() {
        Platform.runLater {
            soundCache.values.forEach { it.stop() }
            soundCache.clear()
        }
    }

    actual fun shutdown() {
        Platform.runLater {
            stopMusic()
            clear()
            musicPlayer?.dispose()
            musicPlayer = null
        }
    }
}