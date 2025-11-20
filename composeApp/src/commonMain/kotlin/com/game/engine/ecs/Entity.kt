package com.game.engine.ecs

import kotlin.reflect.KClass

class Entity(val id: Int) {
    // 存储组件的 Map
    val components = mutableMapOf<KClass<out Component>, Component>()

    // 链式添加组件
    inline fun <reified T : Component> with(component: T): Entity {
        components[T::class] = component
        return this
    }

    // 获取组件
    inline fun <reified T : Component> get(): T {
        return components[T::class] as T
    }

    inline fun <reified T : Component> get(componentType: KClass<T>): T {
        return components[componentType] as T
    }

    inline fun <reified T : Component> Entity.getOrNull(): T? {
        return components[T::class] as? T
    }

    inline fun <reified T : Component> Entity.getOrNull(componentType: KClass<T>): T? {
        return components[componentType] as? T
    }

    inline fun <reified T : Component> Entity.getOrPut(factory: () -> T): T {
        return getOrNull<T>() ?: run {
            val newComp = factory()
            with(newComp)
            newComp
        }
    }

    inline fun <reified T : Component> has(): Boolean {
        return components.containsKey(T::class)
    }

    fun <T : Component> has(componentType: KClass<T>): Boolean {
        return components.containsKey(componentType)
    }

    inline fun <reified T: Component> remove(): Boolean {
        return components.remove(T::class) != null
    }

    fun <T: Component> remove(componentType: KClass<T>): Boolean {
        return components.remove(componentType) != null
    }

}

