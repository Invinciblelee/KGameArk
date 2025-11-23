package com.game.engine.core

import kotlin.jvm.JvmName

class SceneConfig {

    @PublishedApi
    internal val data = mutableMapOf<String, Any>()

    internal fun update(newData: Map<String, Any>) {
        data.clear()
        data.putAll(newData)
    }

    operator fun get(key: String): Any? = data[key]
    @JvmName("getWithGeneric")
    inline fun <reified T> get(key: String): T? = data[key] as? T

}