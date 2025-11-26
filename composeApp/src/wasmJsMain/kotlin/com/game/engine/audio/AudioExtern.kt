@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalJsExport::class)

package com.game.engine.audio

import kotlinx.coroutines.await
import kotlin.js.Promise

fun createAudioContext(): JsAny =
    js("new AudioContext()")

external fun fetch(url: String): Promise<JsAny>

@JsFun("(response) => response.arrayBuffer()")
external fun getArrayBuffer(response: JsAny): Promise<JsAny>

@JsFun("(ctx, buf) => ctx.decodeAudioData(buf)")
external fun decodeAudioData(ctx: JsAny, arrayBuffer: JsAny): Promise<JsAny>

suspend fun fetchAndDecode(url: String): JsAny {
    val ctx = createAudioContext()
    val resp: JsAny = fetch(url).await()
    val buf: JsAny = getArrayBuffer(resp).await()
    val pcm: JsAny = decodeAudioData(ctx, buf).await()
    return pcm
}

fun createBufferSource(ctx: JsAny): JsAny =
    js("ctx.createBufferSource()")

fun createGain(ctx: JsAny): JsAny =
    js("ctx.createGain()")

fun connect(a: JsAny, b: JsAny): JsAny =
    js("a.connect(b)")

fun startSource(source: JsAny, at: Double): Unit =
    js("source.start(at)")

fun stopSource(source: JsAny, at: Double): Unit =
    js("source.stop(at)")

fun setGain(gain: JsAny, value: Float): Unit =
    js("gain.gain.value = value")

fun setBuffer(source: JsAny, buffer: JsAny): Unit =
    js("source.buffer = buffer")

fun getDestination(ctx: JsAny): JsAny =
    js("ctx.destination")

fun getBufferDuration(buffer: JsAny): Double =
    js("buffer.duration")