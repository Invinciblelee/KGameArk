package com.game.engine.utils

import kotlin.uuid.Uuid

internal class Disposable(
    private val onDispose: () -> Unit
) {

    private val id: String = Uuid.random().toString()


    operator fun invoke() {
        onDispose()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Disposable

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

}