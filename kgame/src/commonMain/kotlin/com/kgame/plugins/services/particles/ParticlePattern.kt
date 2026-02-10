package com.kgame.plugins.services.particles

fun interface ParticlePattern {
    fun onPopulate(buffer: ParticleBuffer)
}
