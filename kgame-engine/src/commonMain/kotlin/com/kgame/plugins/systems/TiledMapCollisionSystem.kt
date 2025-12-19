package com.kgame.plugins.systems

import com.kgame.ecs.IteratingSystem
import com.kgame.ecs.World.Companion.family
import com.kgame.plugins.components.Renderable
import com.kgame.plugins.components.Transform

class TiledMapCollisionSystem(

): IteratingSystem(
    family = family { all(Transform, Renderable) }
) {

}