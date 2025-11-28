@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalJsExport::class)

package com.game.engine.audio

fun createAudioContext(): JsAny =
    js("new (window.AudioContext || window.webkitAudioContext)()")

fun closeAudioContext(context: JsAny): JsAny =
    js("context.close()")