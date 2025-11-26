package com.game.engine.core

/**
 * Represents a scene config.
 *
 * Example:
 **
 * ```kotlin
 * KGame(context) {
 *    scene("first") {
 *       onEnter {
 *          presentScene("second", "key" to "Hello World")
 *       }
 *    }
 *
 *    scene("second") {
 *        onEnter {
 *          println(config.get<String>("key")) // Prints "Hello World"
 *       }
 *    }
 * }
 * ```
 */
class SceneConfig {

    @PublishedApi
    internal val data = mutableMapOf<String, Any>()

    internal fun update(newData: Map<String, Any>) {
        data.clear()
        data.putAll(newData)
    }

    inline fun <reified T> get(key: String): T = data[key] as T

}

inline fun <reified T> SceneConfig.getOrNull(key: String): T? = data[key] as? T