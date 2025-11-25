@file:Suppress("UNCHECKED_CAST")

package com.game.engine.asset

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import cmp.composeapp.generated.resources.Res
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.jvm.JvmInline

sealed interface AssetKey<T> {
    val path: String
}

interface SourceUri {
    val path: String
}

@JvmInline
value class AssetUri(override val path: String): SourceUri

@JvmInline
value class HttpUri(override val path: String): SourceUri

@JvmInline
value class ImageKey(override val path: String) : AssetKey<ImageBitmap>

@JvmInline
value class TextKey(override val path: String) : AssetKey<String>

@JvmInline
value class VideoKey(override val path: String) : AssetKey<SourceUri>

@JvmInline
value class SoundKey(override val path: String) : AssetKey<SourceUri>

@JvmInline
value class MusicKey(override val path: String): AssetKey<SourceUri>

interface AssetsManager {

    suspend fun <T> load(key: AssetKey<T>)

    fun unload(key: AssetKey<*>)


    operator fun <T> get(key: AssetKey<T>): T

    fun clear()

}

class DefaultAssetsManager: AssetsManager {

    private val loadedAssets = HashMap<AssetKey<*>, Any>()
    private val lock = SynchronizedObject()

    override suspend fun <T> load(key: AssetKey<T>) = withContext(Dispatchers.Default) {
        val loaded = synchronized(lock) { loadedAssets.containsKey(key) }
        if (loaded) return@withContext

        val loadedObject = when (key) {
            is ImageKey -> {
                Res.readBytes(key.path).decodeToImageBitmap()
            }

            is TextKey -> {
                Res.readBytes(key.path).decodeToString()
            }

            is VideoKey, is SoundKey, is MusicKey -> {
                AssetUri(Res.getUri(key.path))
            }
        }

        synchronized(lock) { loadedAssets[key] = loadedObject }
    }

    @Suppress("UNCHECKED_CAST")
    override fun unload(key: AssetKey<*>) {
        synchronized(lock) { loadedAssets.remove(key) }
    }

    @Suppress("UNCHECKED_CAST")
    override operator fun <T> get(key: AssetKey<T>): T {
        val asset = synchronized(lock) { loadedAssets[key] }
            ?: throw IllegalStateException("Asset '${key.path}' not loaded. Call load() first.")

        return asset as T
    }

    override fun clear() {
        synchronized(lock) { loadedAssets.clear() }
    }

}
