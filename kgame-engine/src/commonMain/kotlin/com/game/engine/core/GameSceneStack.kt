package com.game.engine.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

@Stable
class GameSceneStack<T : Any>(initialScene: T) {

    internal val backStack = mutableStateListOf(initialScene)
    private val lock = SynchronizedObject()

    val size: Int
        get() = backStack.size

    fun push(scene: T): Boolean = synchronized(lock) {
        // Avoid duplicate scenes
        if (backStack.none { it == scene }) {
            backStack.add(scene)
        } else {
            false
        }
    }

    fun pop(): T? = synchronized(lock) {
        if (backStack.size > 1) {
            //MutableList.removeLast will crash below Android 15
            backStack.removeLastOrNull()
        } else {
            null
        }
    }

    fun peek(): T? = synchronized(lock) {
        backStack.lastOrNull()
    }

}

@Composable
fun <T: Any> rememberGameSceneStack(initialScene: T): GameSceneStack<T> {
    return remember { GameSceneStack(initialScene) }
}