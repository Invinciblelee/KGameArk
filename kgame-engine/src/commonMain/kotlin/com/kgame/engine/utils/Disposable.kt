package com.kgame.engine.utils

internal class Disposable(
    private val onDispose: () -> Unit
) {

    operator fun invoke() {
        onDispose()
    }

}