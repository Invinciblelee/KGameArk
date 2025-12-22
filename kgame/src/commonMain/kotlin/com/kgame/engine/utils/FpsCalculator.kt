package com.kgame.engine.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.time.Clock

/**
 * A simple FPS counter.
 * @property fps The current frames per second.
 * @property frameTimeMillis The time in milliseconds that took to render the last frame.
 */
class FpsCalculator {
    private var lastFrameTime = Clock.System.now()
    private var frameCount = 0
    private var accumulatedMicros = 0L

    var fps: Int by mutableIntStateOf(0); private set
    var frameTimeMillis: Double by mutableDoubleStateOf(0.0); private set

    fun advanceFrame() {
        val now   = Clock.System.now()
        val durationMicros = (now - lastFrameTime).inWholeMicroseconds

        frameTimeMillis = durationMicros / 1000.0
        frameCount++
        accumulatedMicros += durationMicros

        if (accumulatedMicros >= 1_000_000L) {
            fps = (frameCount / (accumulatedMicros / 1_000_000.0)).toInt()
            frameCount = 0
            accumulatedMicros = 0L
        }

        lastFrameTime = now
    }
}