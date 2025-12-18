package com.kgame.plugins.components

import kotlinx.atomicfu.atomic


interface Identifiable {

    val id: Int

    companion object {
        private var nextId by atomic(0)

        fun nextId(): Int = nextId++
    }

}