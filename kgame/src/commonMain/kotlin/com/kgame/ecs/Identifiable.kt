package com.kgame.ecs

import kotlinx.atomicfu.atomic

interface Identifiable {

    val id: Int

    /**
     * Using an internal companion object to restrict who can trigger ID generation.
     * Since the generator is private to this file or module, external code cannot
     * manually manipulate the ID sequence.
     */
    companion object {
        // Internal generator invisible to the outside world
        private val generator = atomic(0)

        /** Only used by components during initialization. */
        internal fun nextId(): Int = generator.getAndIncrement()
    }

}