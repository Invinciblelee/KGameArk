package com.game.engine.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember

class GameSceneStack<T : Any>(initialScene: T) {

    internal val backStack = mutableStateListOf(initialScene)

    fun push(scene: T) = backStack.add(scene)

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