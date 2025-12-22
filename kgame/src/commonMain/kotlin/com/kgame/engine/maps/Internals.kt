package com.kgame.engine.maps

/**
 * Extracts the flags of the [Int].
 */
internal fun Int.gidFlags() = this and TiledMapData.FLIPPED_MASKS

/**
 * Extracts the real GID by clearing the flipping and rotation flags.
 */
internal fun Int.realGid() = this and TiledMapData.FLIPPED_MASKS.inv()

/**
 * Offsets the real GID part of an encoded GID while preserving its flipping flags.
 * Internal utility to avoid code duplication during normalization.
 */
internal fun Int.applyOffset(firstGid: Int): Int {
    if (this == 0) return 0
    val realGid = this.realGid()
    val flags = this.gidFlags()
    return (realGid + firstGid) or flags
}

/**
 * Scans the layer hierarchy to extract and optimize physical collision objects.
 * * This function performs a "refining" process to build a flat list of collidable shapes:
 * 1. **Filtering**: It only processes [TiledMapObjectLayer] instances and ignores non-shape
 * objects (like Points) which have no physical volume.
 * 2. **Type Selection**: It retains only objects explicitly marked as "solid" (full block)
 * or "platform" (one-way collision).
 * 3. **Spatial Optimization**: The resulting list is sorted by the absolute world X-position
 * (including shape offsets).
 * * This pre-sorting allows the physics engine to implement "early-exit" logic or
 * sweep-and-prune algorithms, significantly reducing CPU load on large maps.
 */
internal fun List<TiledMapLayer>.filterSolidObjects(): List<TiledMapShapeObject> {
    return this.asSequence()
        .filterIsInstance<TiledMapObjectLayer>()
        .flatMap { it.objects.asSequence() }
        .filterIsInstance<TiledMapShapeObject>()
        .filterNot { it.shape is TiledMapShape.Point }
        .filter { it.isSolid || it.isPlatform }
        .sortedBy { it.position.x + it.shape.offset.x }
        .toList()
}