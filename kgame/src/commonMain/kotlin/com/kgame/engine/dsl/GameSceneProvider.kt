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

class GameSceneProviderScope<T : Any>(
    private val engine: GameEngine,
    private val fallback: (unknownScene: T) -> NavEntry<T> = {
        throw IllegalStateException("Unknown screen $it")
    }
) {
    private val clazzProviders = mutableMapOf<KClass<out T>, SceneClassProvider<out T>>()
    private val providers = mutableMapOf<Any, SceneInstanceProvider<out T>>()

    /**
     * Registers a scene provider for a specific [clazz].
     */
    fun <K : T> addSceneProvider(
        clazz: KClass<K>,
        clazzContentKey: (key: @JvmSuppressWildcards K) -> Any = { defaultContentKey(it) },
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

    /**
     * Registers a scene provider for a specific [key] instance.
     */
    fun <K : T> addSceneProvider(
        key: K,
        contentKey: Any = defaultContentKey(key),
        metadata: Map<String, Any> = emptyMap(),
        sceneBuilder: SceneBuilderScope<K>.(K) -> Unit,
    ) {
        require(key !in providers) {
            "An `scene` with the key `$key` has already been added."
        }
        providers[key] = SceneInstanceProvider(key, contentKey, metadata) { k ->
            SceneBuilderScope(k, engine).apply { sceneBuilder(k) }.build()
        }
    }

    /**
     * Add a scene provider by class type.
     */
    inline fun <reified K : T> scene(
        noinline clazzContentKey: (key: @JvmSuppressWildcards K) -> Any = { defaultContentKey(it) },
        metadata: Map<String, Any> = emptyMap(),
        noinline sceneBuilder: SceneBuilderScope<K>.(K) -> Unit,
    ) {
        addSceneProvider(K::class, clazzContentKey, metadata, sceneBuilder)
    }

    /**
     * Add a scene provider by specific key instance.
     */
    fun <K : T> scene(
        key: K,
        contentKey: Any = defaultContentKey(key),
        metadata: Map<String, Any> = emptyMap(),
        sceneBuilder: SceneBuilderScope<K>.(K) -> Unit,
    ) {
        addSceneProvider(key, contentKey, metadata, sceneBuilder)
    }

    @Suppress("UNCHECKED_CAST")
    @PublishedApi
    internal fun build(): GameSceneProvider<T> = { key ->
        val classProvider = clazzProviders[key::class] as? SceneClassProvider<T>
        val instanceProvider = providers[key] as? SceneInstanceProvider<T>

        classProvider?.run {
            NavEntry(key, clazzContentKey(key), metadata) {
                val sceneInstance = remember(key) { scene(key) }
                sceneInstance.Content()
            }
        } ?: instanceProvider?.run {
            NavEntry(key, contentKey, metadata) {
                val sceneInstance = remember(key) { sceneBuilder(key) }
                sceneInstance.Content()
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

@Suppress("DataClassDefinition")
private data class SceneInstanceProvider<K : Any>(
    val key: K,
    val contentKey: Any,
    val metadata: Map<String, Any>,
    val sceneBuilder: (K) -> GameScene<K>,
)

@PublishedApi internal fun defaultContentKey(key: Any): Any = key.toString()