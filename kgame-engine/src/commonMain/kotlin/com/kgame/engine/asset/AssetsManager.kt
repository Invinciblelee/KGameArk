@file:Suppress("UNCHECKED_CAST")

package com.kgame.engine.asset

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import com.kgame.engine.image.ImageAtlas
import com.kgame.engine.image.loadImageAtlas
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.jvm.JvmInline

/**
 * Represents a key for an asset.
 * @param source The source of the asset.
 */
sealed interface AssetKey<S, T> {
    val source: S
}

/**
 * Represents a URI for a source.
 * @param path The path to the source.
 *
 * Sample code for a URI:
 **
 * ```kotlin
 * val uri = HttpUri("https://example.com/audio.mp3")
 * val uri = AssetUri("files/audio.mp3")
 * ```
 */
interface SourceUri {
    val path: String
}

@JvmInline
value class AssetUri(override val path: String) : SourceUri

@JvmInline
value class HttpUri(override val path: String) : SourceUri

@JvmInline
value class ImageKey(override val source: String) : AssetKey<String, ImageBitmap>

@JvmInline
value class AtlasKey(override val source: String) : AssetKey<String, ImageAtlas>

@JvmInline
value class TextKey(override val source: String) : AssetKey<String, String>

@JvmInline
value class VideoKey(override val source: String) : AssetKey<String, SourceUri>

@JvmInline
value class SoundKey(override val source: String) : AssetKey<String, SourceUri>

@JvmInline
value class MusicKey(override val source: String) : AssetKey<String, SourceUri>

/**
 * Manages the loading and unloading of assets.
 */
interface AssetsManager {

    /**
     * Loads an asset with the given [key].
     * @param key The key of the asset to load.
     */
    suspend fun <S, T> load(key: AssetKey<S, T>)

    /**
     * Unloads an asset with the given [key].
     * @param key The key of the asset to unload.
     */
    fun unload(key: AssetKey<*, *>)


    /**
     * Gets an asset with the given [key].
     * @param key The key of the asset to get.
     * @return The asset
     */
    operator fun <S, T> get(key: AssetKey<S, T>): T

    /**
     * Clears all loaded assets.
     */
    fun clear()

}

interface AssetsReader {
    suspend fun readBytes(path: String): ByteArray
    fun getUri(path: String): String
}

/**
 * Default implementation of [AssetsManager].
 */
@Stable
class DefaultAssetsManager(private val assetsReader: AssetsReader) : AssetsManager {

    private val cache = HashMap<AssetKey<*, *>, Any>()
    private val lock = SynchronizedObject()

    override suspend fun <S, T> load(key: AssetKey<S, T>) {
        if (synchronized(lock) { cache.containsKey(key) }) return

        val loadedObject = when (key) {
            is ImageKey -> {
                assetsReader.readBytes(key.source).decodeToImageBitmap()
            }

            is TextKey -> {
                assetsReader.readBytes(key.source).decodeToString()
            }

            is VideoKey, is SoundKey, is MusicKey -> {
                AssetUri(assetsReader.getUri(key.source))
            }

            is AtlasKey -> {
                loadImageAtlas(assetsReader, key.source)
            }
        }

        synchronized(lock) { cache[key] = loadedObject }
    }

    @Suppress("UNCHECKED_CAST")
    override fun unload(key: AssetKey<*, *>) {
        synchronized(lock) { cache.remove(key) }
    }

    @Suppress("UNCHECKED_CAST")
    override operator fun <S, T> get(key: AssetKey<S, T>): T {
        val asset = synchronized(lock) { cache[key] }
            ?: throw IllegalStateException("Asset '${key.source}' not loaded. Call load() first.")

        return asset as T
    }

    override fun clear() {
        synchronized(lock) { cache.clear() }
    }

}
