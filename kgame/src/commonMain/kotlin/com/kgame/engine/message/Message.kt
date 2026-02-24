package com.kgame.engine.message

import kotlin.reflect.KClass

/**
 * Marker interface for any message object.
 * Pure data classes implement this.
 */
interface Message

/**
 * The token assigned to the companion object.
 */
interface MessageToken<T : Any> {
    val id: Int
}

/**
 * The internal ID assigner, safe for all platforms.
 */
internal object MessageRegistry {
    private var counter = 0
    // Internal mapping of KClass to the track id
    private val classToId = mutableMapOf<KClass<*>, Int>()

    /**
     * Registers a class and returns its unique id.
     */
    fun register(clazz: KClass<*>): Int {
        return classToId.getOrPut(clazz) { counter++ }
    }

    /**
     * Retrieves the id for a class. Throws if the class was never registered.
     */
    operator fun get(clazz: KClass<*>): Int = requireNotNull(classToId[clazz]) {
        "Message ${clazz.simpleName} was not registered. Did you forget the companion object token()?"
    }
}

/**
 * The delegate used in companion objects.
 */
class MessageTokenDelegate<T : Any>(clazz: KClass<T>) : MessageToken<T> {
    override val id: Int = MessageRegistry.register(clazz)
}

/**
 * The ultimate one-liner for KMP.
 */
inline fun <reified T : Any> token() = MessageTokenDelegate(T::class)
