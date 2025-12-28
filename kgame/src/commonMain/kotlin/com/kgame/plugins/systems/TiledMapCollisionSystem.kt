@file:Suppress("ConvertTwoComparisonsToRangeCheck")

package com.kgame.plugins.systems

import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.kgame.ecs.IntervalSystem
import com.kgame.ecs.World.Companion.family
import com.kgame.engine.geometry.set
import com.kgame.engine.maps.TiledMapShape
import com.kgame.plugins.components.Hitbox
import com.kgame.plugins.components.TiledMap
import com.kgame.plugins.components.Transform
import com.kgame.plugins.components.offset
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * TiledMapCollisionSystem
 *
 * A standardized, 0-GC collision system for Tiled Map objects.
 * Features:
 * - Full 4-directional blocking (Top, Bottom, Left, Right).
 * - Concave polygon support via individual segment projection.
 * - Clean 'when' branching for improved maintainability.
 */
class TiledMapCollisionSystem : IntervalSystem() {
    private val tiledMapFamily = family { all(TiledMap) }
    private val entitiesFamily = family { all(Transform, Hitbox) }

    // Pre-allocated buffers to prevent heap allocations during updates
    private val entityBounds = MutableRect(0f, 0f, 0f, 0f)
    private val shapeBounds = MutableRect(0f, 0f, 0f, 0f)

    override fun onTick(deltaTime: Float) {
        val mapEntity = tiledMapFamily.firstOrNull() ?: return
        val solidObjects = mapEntity[TiledMap].data.solidObjects

        entitiesFamily.forEach { entity ->
            val transform = entity[Transform]
            val hitbox = entity[Hitbox]

            // Sync current world coordinates of the entity
            entityBounds.set(transform, hitbox.rect)

            val entRight = entityBounds.right
            val entLeft = entityBounds.left

            var index = 0
            while (index < solidObjects.size) {
                val obj = solidObjects[index++]
                obj.getBounds(shapeBounds)

                if (shapeBounds.left > entRight) break

                if (shapeBounds.right < entLeft) continue

                // Broad-phase check
                if (entityBounds.overlaps(shapeBounds)) {
                    // Use 'when' for clean shape-specific branching
                    when (val shape = obj.shape) {
                        is TiledMapShape.Polyline -> {
                            if (obj.isPlatform) {
                                // --- CASE 1: ONE-WAY PLATFORM ---
                                // Only snap if falling and above the line
                                resolvePlatformSegments(transform, hitbox, obj.position, shape.points)
                            } else {
                                // --- CASE 2: SOLID POLYLINE ---
                                // Block from all 4 directions
                                resolveSolidSegments(transform, hitbox, obj.position, shape.points, false)
                            }
                        }

                        is TiledMapShape.Polygon -> {
                            resolveSolidSegments(transform, hitbox, obj.position, shape.points, isClosed = true)
                        }

                        is TiledMapShape.Ellipse -> {
                            resolveEllipseCollision(transform, hitbox, shapeBounds)
                        }

                        else -> {
                            // Default handling for simple rectangular collision boxes
                            resolveRectCollision(transform, hitbox, shapeBounds)
                        }
                    }
                }
            }
        }
    }

    /**
     * Logic for One-Way Platforms.
     * Only blocks the entity's feet (bottom) when descending.
     */
    private fun resolvePlatformSegments(
        transform: Transform,
        hitbox: Hitbox,
        pos: Offset,
        points: List<Offset>
    ) {
        val count = points.size - 1
        val halfWidth = entityBounds.width * 0.5f

        val entCenterX = entityBounds.center.x
        val entCenterY = entityBounds.center.y
        val entBottom = entityBounds.bottom

        var index = 0
        while (index < count) {
            val p1 = points[index++]
            val p2 = points[index]

            val x1 = p1.x + pos.x
            val y1 = p1.y + pos.y
            val x2 = p2.x + pos.x
            val y2 = p2.y + pos.y

            val dx = x2 - x1
            val dy = y2 - y1
            val absDx = if (dx < 0f) -dx else dx
            val absDy = if (dy < 0f) -dy else dy

            // 1. Vertical Resolution (Floor Snapping)
            val minX = min(x1, x2); val maxX = max(x1, x2)

            if (entCenterX >= minX && entCenterX <= maxX) {
                val t = if (absDx < 0.001f) 0.5f else (entCenterX - x1) / dx
                val surfaceY = y1 + t * dy

                // Flattened nested if
                if (entBottom >= surfaceY && entBottom - 10f < surfaceY && absDx >= absDy) {
                    transform.offset(y = surfaceY - entBottom)
                    entityBounds.set(transform, hitbox.rect)
                }
            }

            // 2. Horizontal Resolution (Wall Blocking)
            val minY = min(y1, y2); val maxY = max(y1, y2)

            // Use Guard Clauses to flatten the structure
            if (absDy <= absDx) continue
            if (entCenterY < minY || entCenterY > maxY) continue

            val ty = (entCenterY - y1) / dy // absDy > absDx ensures dy != 0
            val surfaceX = x1 + ty * dx

            // Calculate currentXSurfaceY for the Gate check
            val txGate = if (absDx < 0.001f) 0.5f else (entCenterX - x1) / dx
            val currentXSurfaceY = y1 + txGate * dy

            if (entBottom > currentXSurfaceY + 5f) continue

            val dist = entCenterX - surfaceX
            val absDist = if (dist < 0f) -dist else dist

            if (absDist < halfWidth) {
                if (entCenterX < surfaceX) {
                    transform.offset(x = surfaceX - entityBounds.right)
                } else {
                    transform.offset(x = surfaceX - entityBounds.left)
                }
                entityBounds.set(transform, hitbox.rect)
            }
        }
    }

    /**
     * Logic for Solid Segments (Polylines or Polygons).
     * Prevents penetration from all 4 directions.
     */
    private fun resolveSolidSegments(
        transform: Transform,
        hitbox: Hitbox,
        pos: Offset,
        points: List<Offset>,
        isClosed: Boolean
    ) {
        val count = points.size
        if (count < 2) return

        val edgeCount = if (isClosed) count else count - 1
        val halfWidth = entityBounds.width * 0.5f
        val halfHeight = entityBounds.height * 0.5f

        val entCenterX = entityBounds.center.x
        val entCenterY = entityBounds.center.y

        var index = 0
        while (index < edgeCount) {
            val p1 = points[index++]
            val p2 = points[index % count]

            val x1 = p1.x + pos.x; val x2 = p2.x + pos.x
            val y1 = p1.y + pos.y; val y2 = p2.y + pos.y

            val dx = x2 - x1
            val dy = y2 - y1

            // --- 1. Vertical Axis (Floors/Ceilings) ---
            val minX = min(x1, x2); val maxX = max(x1, x2)

            if (entCenterX >= minX && entCenterX <= maxX) {
                val t = if (abs(dx) < 0.001f) 0.5f else (entCenterX - x1) / dx
                val surfaceY = y1 + t * dy

                val distY = entCenterY - surfaceY
                val absDistY = if (distY < 0f) -distY else distY

                if (absDistY < halfHeight) {
                    if (entCenterY < surfaceY) {
                        transform.offset(y = surfaceY - entityBounds.bottom)
                    } else {
                        transform.offset(y = surfaceY - entityBounds.top)
                    }

                    entityBounds.set(transform, hitbox.rect)
                }
            }

            // --- 2. Horizontal Axis (Walls) ---
            val minY = min(y1, y2); val maxY = max(y1, y2)

            // Guard clause to flatten the Horizontal section
            if (entCenterY < minY || entCenterY > maxY) continue

            val ty = if (abs(dy) < 0.001f) 0.5f else (entCenterY - y1) / dy
            val surfaceX = x1 + ty * dx

            val distX = entCenterX - surfaceX
            val absDistX = if (distX < 0f) -distX else distX

            if (absDistX < halfWidth) {
                if (entCenterX < surfaceX) {
                    transform.offset(x = surfaceX - entityBounds.right)
                } else {
                    transform.offset(x = surfaceX - entityBounds.left)
                }

                entityBounds.set(transform, hitbox.rect)
            }
        }
    }

    /**
     * Resolves solid collisions for elliptical shapes using axis-aligned projection.
     * * This method treats the ellipse as a mathematical function to derive precise
     * surface coordinates (surfaceX, surfaceY) based on the entity's center point.
     *
     * Characteristics:
     * 1. 0 GC: Operates entirely on primitive types without object allocation.
     * 2. Axis-Aligned: Independently resolves vertical (floor/ceiling) and
     * horizontal (wall) penetrations.
     * 3. Reactive: Immediately corrects position using the provided [transform].
     */
    private fun resolveEllipseCollision(
        transform: Transform,
        hitbox: Hitbox,
        ellipseRect: MutableRect // The bounding box of the ellipse from Tiled
    ) {
        val a = ellipseRect.width * 0.5f  // Horizontal radius
        val b = ellipseRect.height * 0.5f // Vertical radius
        val h = ellipseRect.left + a      // Center X
        val k = ellipseRect.top + b       // Center Y

        val entCenterX = entityBounds.center.x
        val entCenterY = entityBounds.center.y

        // 1. Vertical Axis (Floor/Ceiling of Ellipse)
        // Check if X is within ellipse range
        val minX = h - a
        val maxX = h + a

        if (entCenterX >= minX && entCenterX <= maxX) {
            // Equation of ellipse: (x-h)^2/a^2 + (y-k)^2/b^2 = 1
            // Solve for y: y = k +/- b * sqrt(1 - ((x-h)/a)^2)
            val dx = (entCenterX - h) / a
            val dyLocal = b * sqrt(1f - dx * dx)

            // Find top and bottom surface Y at this specific X
            val topY = k - dyLocal
            val bottomY = k + dyLocal

            val halfHeight = entityBounds.height * 0.5f

            // Check Bottom Surface (Floor)
            if (abs(entCenterY - bottomY) < halfHeight) {
                if (entCenterY < bottomY) transform.offset(y = bottomY - entityBounds.bottom)
                else transform.offset(y = bottomY - entityBounds.top)
                entityBounds.set(transform, hitbox.rect)
            }
            // Check Top Surface (Ceiling)
            else if (abs(entCenterY - topY) < halfHeight) {
                if (entCenterY < topY) transform.offset(y = topY - entityBounds.bottom)
                else transform.offset(y = topY - entityBounds.top)
                entityBounds.set(transform, hitbox.rect)
            }
        }

        // 2. Horizontal Axis (Walls of Ellipse)
        val minY = k - b
        val maxY = k + b

        if (entCenterY >= minY && entCenterY <= maxY) {
            val dy = (entCenterY - k) / b
            val dxLocal = a * sqrt(1f - dy * dy)

            val leftX = h - dxLocal
            val rightX = h + dxLocal

            val halfWidth = entityBounds.width * 0.5f

            // Use your style: Determine which side to offset based on center position
            // Check Right Wall of Ellipse
            if (abs(entCenterX - rightX) < halfWidth) {
                if (entCenterX < rightX) transform.offset(x = rightX - entityBounds.right)
                else transform.offset(x = rightX - entityBounds.left)
                entityBounds.set(transform, hitbox.rect)
            }
            // Check Left Wall of Ellipse
            else if (abs(entCenterX - leftX) < halfWidth) {
                if (entCenterX < leftX) transform.offset(x = leftX - entityBounds.right)
                else transform.offset(x = leftX - entityBounds.left)
                entityBounds.set(transform, hitbox.rect)
            }
        }
    }

    /**
     * Standard AABB resolution for basic rectangular shapes.
     */
    private fun resolveRectCollision(transform: Transform, hitbox: Hitbox, target: MutableRect) {
        val dx = entityBounds.center.x - target.center.x
        val dy = entityBounds.center.y - target.center.y
        val ox = (entityBounds.width + target.width) * 0.5f - abs(dx)
        val oy = (entityBounds.height + target.height) * 0.5f - abs(dy)

        if (ox > 0 && oy > 0) {
            if (ox < oy) {
                transform.offset(x = if (dx > 0) ox else -ox)
            } else {
                transform.offset(y = if (dy > 0) oy else -oy)
            }
            entityBounds.set(transform, hitbox.rect)
        }
    }
}