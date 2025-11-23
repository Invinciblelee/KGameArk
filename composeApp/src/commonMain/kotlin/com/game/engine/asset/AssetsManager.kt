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

val AssetKey<*>.uri: String
    get() = Res.getUri(path)

interface DataSource {
    val uri: String
}

@JvmInline
internal value class LocalDataSource(override val uri: String): DataSource

@JvmInline
value class ImageKey(override val path: String) : AssetKey<ImageBitmap>

@JvmInline
value class TextKey(override val path: String) : AssetKey<String>

@JvmInline
value class VideoKey(override val path: String) : AssetKey<DataSource>

@JvmInline
value class SoundKey(override val path: String) : AssetKey<DataSource>

@JvmInline
value class MusicKey(override val path: String): AssetKey<DataSource>

class AssetsManager() {

    private val loadedAssets = HashMap<AssetKey<*>, Any>()
    private val lock = SynchronizedObject()

    suspend fun load(key: AssetKey<*>) = withContext(Dispatchers.Default) {
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
                LocalDataSource(Res.getUri(key.path))
            }
        }

        synchronized(lock) { loadedAssets.put(key, loadedObject) }
    }

    @Suppress("UNCHECKED_CAST")
    fun unload(key: AssetKey<*>) {
        synchronized(lock) { loadedAssets.remove(key) }
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: AssetKey<T>): T {
        val asset = synchronized(lock) { loadedAssets[key] }
            ?: throw IllegalStateException("Asset '${key.path}' not loaded. Call load() first.")

        return asset as T
    }

    fun clear() {
        synchronized(lock) { loadedAssets.clear() }
    }

}
