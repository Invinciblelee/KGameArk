package com.kgame.engine.dsl

import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import com.kgame.engine.core.GameEngine
import com.kgame.engine.core.GameScene
import kotlin.jvm.JvmSuppressWildcards
import kotlin.reflect.KClass

typealias GameSceneProvider<T> = (T) -> NavEntry<T>

inline fun <T : Any> GameSceneProvider(
    engine: GameEngine,
    noinline fallback: (unknownScreen: T) -> NavEntry<T> = {
        throw IllegalStateException("Unknown screen $it")
    },
    builder: GameSceneProviderScope<T>.() -> Unit,
): GameSceneProvider<T> = GameSceneProviderScope(engine, fallback).apply(builder).build()

@GameDslMarker
class GameSceneProviderScope<T : Any>(
    private val engine: GameEngine,
    private val fallback: (unknownScene: T) -> NavEntry<T> = {
        throw IllegalStateException("Unknown scene $it")
    }
) {
    private val clazzProviders = mutableMapOf<KClass<out T>, SceneClassProvider<out T>>()

    fun <K : T> addSceneProvider(
        clazz: KClass<K>,
        clazzContentKey: (key: @JvmSuppressWildcards K) -> Any = {
            defaultContentKey(it)
        },
        metadata: Map<String, Any> = emptyMap(),
        sceneBuilder: SceneBuilderScope<K>.(K) -> Unit,
    ) {
        require(clazz !in clazzProviders) {
            "An `scene` with the same `clazz` has already been added: ${clazz.simpleName}."
        }

        clazzProviders[clazz] = SceneClassProvider(clazz, clazzContentKey, metadata) { key ->
            SceneBuilderScope(key, engine).apply { sceneBuilder(key) }.build()
        }
    }

    inline fun <reified K : T> scene(
        noinline clazzContentKey: (key: @JvmSuppressWildcards K) -> Any = { defaultContentKey(it) },
        metadata: Map<String, Any> = emptyMap(),
        noinline sceneBuilder: SceneBuilderScope<K>.(K) -> Unit,
    ) {
        addSceneProvider(K::class, clazzContentKey, metadata, sceneBuilder)
    }

    @Suppress("UNCHECKED_CAST")
    @PublishedApi
    internal fun build(): GameSceneProvider<T> = { key ->
        val entryClassProvider = clazzProviders[key::class] as? SceneClassProvider<T>
        entryClassProvider?.run {
            NavEntry(key, clazzContentKey(key), metadata) {
                val scene = remember(key) { scene(key) }
                scene.Content()
            }
        } ?: fallback.invoke(key)
    }

}

@Suppress("DataClassDefinition")
private data class SceneClassProvider<K : Any>(
    val clazz: KClass<K>,
    val clazzContentKey: (key: K) -> Any,
    val metadata: Map<String, Any>,
    val scene: (K) -> GameScene<K>,
)

@PublishedApi internal fun defaultContentKey(key: Any): Any = key.toString()