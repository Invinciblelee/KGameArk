package com.kgame.plugins.systems

import com.kgame.ecs.SystemPriority

object SystemPriorityAnchors {
    val Input = SystemPriority(1000)
    val Physics = SystemPriority(2000)
    val Logic = SystemPriority(3000)
    val Animation = SystemPriority(4000)
    val Camera = SystemPriority(5000)
    val Render = SystemPriority(6000)
    val Cleanup = SystemPriority(9000)
}