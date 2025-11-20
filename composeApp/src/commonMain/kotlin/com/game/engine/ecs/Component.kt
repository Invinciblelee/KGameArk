package com.game.engine.ecs

import kotlin.reflect.KClass

// 所有组件的标记接口
interface Component

object ComponentKind {
    private val idMap = HashMap<KClass<out Component>, Int>()
    private var nextId = 0

    fun get(type: KClass<out Component>): Int {
        return idMap.getOrPut(type) { nextId++ }
    }
}