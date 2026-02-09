package com.kgame.ecs

/**
 * Defines the execution order of an ECS system.
 * Higher values are executed later in the frame.
 */
class SystemPriority(val value: Int) : Comparable<SystemPriority> {

    override fun compareTo(other: SystemPriority): Int = value.compareTo(other.value)

    infix fun after(offset: Int): SystemPriority = SystemPriority(value + offset)

    infix fun before(offset: Int): SystemPriority = SystemPriority(value - offset)

    /**
     * Named companion object following PascalCase conventions.
     * These anchors define the standard stages of the game loop.
     */
    companion object {
        val Min = SystemPriority(0)
        val Default = SystemPriority(5000)
        val Max = SystemPriority(10000)
    }
}