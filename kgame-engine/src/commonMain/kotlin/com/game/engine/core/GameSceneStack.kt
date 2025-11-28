package com.game.engine.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember

class GameSceneStack<T : Any>(initialScene: T) {

    internal val backStack = mutableStateListOf(initialScene)

    val size: Int
        get() = backStack.size

    fun push(scene: T): Boolean {
        if (backStack.lastOrNull() != scene) {
            return backStack.add(scene)
        }
        return false
    }

    fun pop(): T? = if (backStack.size > 1) {
        backStack.removeLast()
    } else {
        null
    }

    fun peek(): T? = backStack.lastOrNull()

}

@Composable
fun <T: Any> rememberGameSceneStack(initialScene: T): GameSceneStack<T> {
    return remember { GameSceneStack(initialScene) }
}