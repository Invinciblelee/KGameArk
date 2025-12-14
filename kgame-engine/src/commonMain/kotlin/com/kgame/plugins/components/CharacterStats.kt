package com.kgame.plugins.components

import com.kgame.ecs.Component
import com.kgame.ecs.ComponentType

/**
 * A simple CharacterStats component.
 * @property hp The health points of the entity.
 * @property mp The magic points of the entity.
 * @property str The strength of the entity.
 * @property dex The dexterity of the entity.
 * @property con The constitution of the entity.
 * @property wis The wisdom of the entity.
 * @property intel The intelligence of the entity.
 */
data class CharacterStats(
    val maxHp: Float = 100f,
    var hp: Float = maxHp,

    val maxMp: Float = 100f,
    var mp: Float = maxMp,

    var str: Float = 10f,
    var dex: Float = 10f,
    var con: Float = 10f,
    var wis: Float = 10f,
    var intel: Float = 10f,
): Component<CharacterStats> {
    override fun type() = CharacterStats
    companion object Companion : ComponentType<CharacterStats>()
}

val CharacterStats.isAlive: Boolean
    get() = hp > 0

val CharacterStats.isFullyHealed: Boolean
    get() = hp == maxHp

val CharacterStats.isFullyMana: Boolean
    get() = mp == maxMp

/**
 * Gets the current HP percentage (a value between 0.0 and 1.0).
 */
val CharacterStats.hpPercentage: Float
    get() = if (maxHp > 0f) (hp / maxHp).coerceIn(0f, 1f) else 0f