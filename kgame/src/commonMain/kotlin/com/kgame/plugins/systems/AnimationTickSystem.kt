package com.kgame.plugins.systems

import com.kgame.ecs.IntervalSystem
import com.kgame.ecs.SystemPriority
import com.kgame.ecs.World.Companion.inject
import com.kgame.plugins.services.AnimationService

class AnimationTickSystem(
    private val animationService: AnimationService = inject(),
    priority: SystemPriority = SystemPriorityAnchors.Animation
): IntervalSystem(priority = priority) {

    override fun onTick(deltaTime: Float) {
        animationService.update(deltaTime)
    }

}