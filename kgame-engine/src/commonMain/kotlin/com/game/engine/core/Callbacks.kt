package com.game.engine.core

internal fun interface TickCallback {
    operator fun invoke(value: Float)
}

internal fun interface EnableChangedCallback {
    operator fun invoke(value: Boolean)
}