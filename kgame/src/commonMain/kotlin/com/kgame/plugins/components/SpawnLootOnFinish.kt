package com.kgame.plugins.components

import com.kgame.ecs.Component
import com.kgame.ecs.ComponentType

/**
 * A component that defines the loot-spawning behavior triggered upon animation completion.
 * This is considered a behavior-data component rather than a simple tag because it 
 * contains specific configuration for the spawning logic.
 *
 * @param lootIds A list of potential loot identifiers (e.g., item IDs or prefab names).
 * @param chance The probability (0.0 to 1.0) of the loot actually being spawned.
 */
data class SpawnLootOnFinish(
    val lootIds: List<String>,
    val chance: Float = 1.0f
) : Component<SpawnLootOnFinish> {
    override fun type() = SpawnLootOnFinish

    companion object : ComponentType<SpawnLootOnFinish>()
}