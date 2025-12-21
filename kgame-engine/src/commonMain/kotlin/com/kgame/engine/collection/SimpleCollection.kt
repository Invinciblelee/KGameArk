package com.kgame.engine.collection

interface SimpleCollection<T> {

    val count: Int

    operator fun get(index: Int): T

}