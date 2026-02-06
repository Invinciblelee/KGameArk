package com.kgame.plugins.components

import com.kgame.ecs.Component
import com.kgame.ecs.ComponentType
import com.kgame.ecs.EntityTag

/**
 * A tag for the player's primary entity.
 * Used to identify the player for controls, collisions, and camera targeting.
 */
data object PlayerTag : EntityTag()

/**
 * A tag for projectiles fired by the player.
 * Used in collision detection to apply damage to enemies and to differentiate from enemy bullets.
 */
data class PlayerBulletTag(val damage: Float) : Component<PlayerBulletTag> {
    override fun type() = PlayerBulletTag

    companion object : ComponentType<PlayerBulletTag>()
}

/**
 * A tag for an enemy entity.
 * Used to identify enemies for AI behavior, targeting, and collision detection.
 */
data object EnemyTag : EntityTag()

/**
 * A tag for projectiles fired by an enemy.
 * Used in collision detection to apply damage to the player.
 */
data class EnemyBulletTag(val damage: Float) : Component<EnemyBulletTag> {
    override fun type() = EnemyBulletTag

    companion object : ComponentType<EnemyBulletTag>()
}

/**
 * A utility tag marking an entity to be removed from the world at the end of the frame.
 * This is a safe way to destroy entities without modifying collections during iteration.
 */
data object CleanupTag : EntityTag()

/**
 * A tag indicating the entity is self-destructive and should be
 * marked with [CleanupTag] once its animation ends.
 *
 * Using 'AutoCleanup' instead of 'OnFinish' makes it a descriptive
 * property rather than a callback-style name.
 */
data object AutoCleanupTag : EntityTag()

/**
 * A tag for entities that can be picked up by the player, such as power-ups or health packs.
 * Used to trigger pickup logic upon collision with the player.
 */
data object PickupTag : EntityTag()

/**
 * A tag for visual effects entities, such as explosions, smoke, or hit sparks.
 * These entities are typically short-lived and non-interactive.
 */
data object VfxTag : EntityTag()

/**
 * A tag for non-destructible environmental objects, like walls or asteroids.
 * Used to block movement or destroy projectiles upon collision.
 */
data object ObstacleTag : EntityTag()

/**
 * A tag for an area or zone that applies a continuous effect, usually damage, to entities within it.
 * Examples include lava fields, toxic clouds, or electrified floors.
 */
data object HazardZoneTag : EntityTag()

/**
 * A transient tag added by the AnimationSystem when a non-looping animation finishes.
 */
data object AnimationFinishedTag : EntityTag()