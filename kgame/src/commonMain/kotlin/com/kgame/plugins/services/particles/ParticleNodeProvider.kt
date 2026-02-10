package com.kgame.plugins.services.particles

/**
 * A capability interface that provides shorthand methods to create ParticleNodes.
 * Implemented by scopes to enable a clean, math-like DSL.
 */
interface ParticleNodeProvider {
    // Basic Constants
    fun scalar(value: Float): ParticleNode = ParticleNode.Scalar(value)
    fun vec2(x: Float, y: Float): ParticleNode = ParticleNode.Vector2(x, y)
    
    // Randomness & Logic
    fun random(min: Float, max: Float, exp: Float = 1.0f): ParticleNode = 
        ParticleNode.RandomRange(min, max, exp)

    fun select(condition: SelectCondition, onTrue: ParticleNode, onFalse: ParticleNode): ParticleNode =
        ParticleNode.Select(condition, onTrue, onFalse)

    fun color(argb: Int): ParticleNode = ParticleNode.Color(argb)

    fun color(argb: Long): ParticleNode = ParticleNode.Color(argb.toInt())

    fun color(red: Float, green: Float, blue: Float, alpha: Float = 1f): ParticleNode {
        val a = (alpha * 255f + 0.5f).toInt().coerceIn(0, 255)
        val r = (red * 255f + 0.5f).toInt().coerceIn(0, 255)
        val g = (green * 255f + 0.5f).toInt().coerceIn(0, 255)
        val b = (blue * 255f + 0.5f).toInt().coerceIn(0, 255)

        val argb = (a shl 24) or (r shl 16) or (g shl 8) or b
        return ParticleNode.Color(argb)
    }

    fun Ratio(value: Float) = SelectCondition.Ratio(value)
    fun Threshold(value: Int) = SelectCondition.Threshold(value)
    fun Modulo(value: Int) = SelectCondition.Modulo(value)

    val time: ParticleNode get() = ParticleNode.Time
    val index: ParticleNode get() = ParticleNode.Index
    val progress: ParticleNode get() = ParticleNode.Progress
}