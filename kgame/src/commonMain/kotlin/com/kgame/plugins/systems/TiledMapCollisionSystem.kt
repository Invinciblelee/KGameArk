@file:Suppress("ConvertTwoComparisonsToRangeCheck")

package com.kgame.plugins.systems

import androidx.collection.SimpleArrayMap
import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Offset
import com.kgame.ecs.IntervalSystem
import com.kgame.ecs.SystemPriority
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
import kotlin.math.pow
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
class TiledMapCollisionSystem(priority: SystemPriority = SystemPriorityAnchors.Physics) :
    IntervalSystem(priority = priority) {
    private val tiledMapFamily = family { all(TiledMap) }
    private val entitiesFamily = family { all(Transform, Hitbox) }

    private val entityBounds = MutableRect(0f, 0f, 0f, 0f)
    private val shapeBounds = MutableRect(0f, 0f, 0f, 0f)

    // Manually track position history to derive delta
    private val positionHistory = SimpleArrayMap<Int, Offset>()

    override fun onTick(deltaTime: Float) {
        val mapEntity = tiledMapFamily.firstOrNull() ?: return
        val solidObjects = mapEntity[TiledMap].data.solidObjects

        entitiesFamily.forEach { entity ->
            val transform = entity[Transform]
            val hitbox = entity[Hitbox]

            // Calculate movement delta since last frame
            val lastPos = positionHistory[entity.id]
            val delta = if (lastPos != null) {
                Offset(transform.x - lastPos.x, transform.y - lastPos.y)
            } else {
                Offset.Zero
            }

            entityBounds.set(transform, hitbox.rect)

            var index = 0
            while (index < solidObjects.size) {
                val obj = solidObjects[index++]
                obj.getBounds(shapeBounds)

                if (shapeBounds.left > entityBounds.right) break
                if (shapeBounds.right < entityBounds.left) continue

                if (entityBounds.overlaps(shapeBounds)) {
                    when (val shape = obj.shape) {
                        is TiledMapShape.Polyline -> {
                            if (obj.isPlatform) {
                                resolvePlatformSegments(
                                    transform,
                                    hitbox,
                                    obj.position,
                                    shape.points,
                                    delta
                                )
                            } else {
                                resolveSolidSegments(
                                    transform,
                                    hitbox,
                                    obj.position,
                                    shape.points,
                                    false
                                )
                            }
                        }

                        is TiledMapShape.Polygon -> {
                            resolveSolidSegments(
                                transform,
                                hitbox,
                                obj.position,
                                shape.points,
                                true
                            )
                        }

                        is TiledMapShape.Ellipse -> resolveEllipseCollision(
                            transform,
                            hitbox,
                            shapeBounds
                        )

                        else -> resolveRectCollision(transform, hitbox, shapeBounds)
                    }
                }
            }

            positionHistory.put(entity.id, Offset(transform.x, transform.y))
        }
    }

    private fun resolvePlatformSegments(
        transform: Transform,
        hitbox: Hitbox,
        pos: Offset,
        points: List<Offset>,
        delta: Offset
    ) {
        val count = points.size - 1
        if (count < 1) return

        val eb = entityBounds
        val cx = eb.center.x
        val bottomY = eb.bottom

        // 关键点 1：获取上一帧的脚底位置
        val prevBottomY = bottomY - delta.y

        var bestSurfaceY = Float.MAX_VALUE
        var foundFloor = false

        var i = 0
        while (i < count) {
            val p1 = points[i];
            val p2 = points[i + 1]; i++
            val x1 = p1.x + pos.x;
            val y1 = p1.y + pos.y
            val x2 = p2.x + pos.x;
            val y2 = p2.y + pos.y

            val minX = if (x1 < x2) x1 else x2
            val maxX = if (x1 > x2) x1 else x2

            // 判定条件 A：玩家中心在水平范围内
            if (cx >= minX - 0.1f && cx <= maxX + 0.1f) {
                val lDx = x2 - x1
                if (abs(lDx) > 0.001f) {
                    val t = (cx - x1) / lDx
                    val surfaceY = y1 + t * (y2 - y1)

                    // 关键点 2：单向通行逻辑
                    // 只有当：上一帧脚底在表面之上，且这一帧脚底穿过了表面
                    // 这样向上跳时，prevBottomY 在 surfaceY 之下，不满足条件，直接穿透
                    if (prevBottomY <= surfaceY + 1f && bottomY >= surfaceY) {
                        // 关键点 3：距离判定
                        // 防止角色从极高处掉落瞬间吸附到半空的平台（可选，通常设为 15-20）
                        if (abs(bottomY - surfaceY) < 20f) {
                            if (surfaceY < bestSurfaceY) {
                                bestSurfaceY = surfaceY
                                foundFloor = true
                            }
                        }
                    }
                }
            }
        }

        // --- 只进行垂直位置约束，彻底删除所有水平 Block 逻辑 ---
        if (foundFloor) {
            transform.offset(y = bestSurfaceY - bottomY)
            eb.set(transform, hitbox.rect)
        }
    }

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

        var idx = 0
        while (idx < edgeCount) {
            val p1 = points[idx++];
            val p2 = points[idx % count]
            val x1 = p1.x + pos.x;
            val x2 = p2.x + pos.x
            val y1 = p1.y + pos.y;
            val y2 = p2.y + pos.y
            val dx = x2 - x1;
            val dy = y2 - y1

            // Vertical resolve
            if (entityBounds.center.x in min(x1, x2)..max(x1, x2)) {
                val t = if (abs(dx) < 0.001f) 0.5f else (entityBounds.center.x - x1) / dx
                val surfaceY = y1 + t * dy
                if (abs(entityBounds.center.y - surfaceY) < entityBounds.height * 0.5f) {
                    transform.offset(y = if (entityBounds.center.y < surfaceY) surfaceY - entityBounds.bottom else surfaceY - entityBounds.top)
                    entityBounds.set(transform, hitbox.rect)
                }
            }

            // Horizontal resolve
            if (entityBounds.center.y in min(y1, y2)..max(y1, y2)) {
                val ty = if (abs(dy) < 0.001f) 0.5f else (entityBounds.center.y - y1) / dy
                val surfaceX = x1 + ty * dx
                if (abs(entityBounds.center.x - surfaceX) < entityBounds.width * 0.5f) {
                    transform.offset(x = if (entityBounds.center.x < surfaceX) surfaceX - entityBounds.right else surfaceX - entityBounds.left)
                    entityBounds.set(transform, hitbox.rect)
                }
            }
        }
    }

    private fun resolveEllipseCollision(transform: Transform, hitbox: Hitbox, rect: MutableRect) {
        val a = rect.width * 0.5f;
        val b = rect.height * 0.5f
        val h = rect.left + a;
        val k = rect.top + b
        val cx = entityBounds.center.x;
        val cy = entityBounds.center.y

        if (cx in (h - a)..(h + a)) {
            val dy = b * sqrt(1f - ((cx - h) / a).pow(2))
            val topY = k - dy;
            val botY = k + dy
            val halfH = entityBounds.height * 0.5f
            if (abs(cy - botY) < halfH) {
                transform.offset(y = if (cy < botY) botY - entityBounds.bottom else botY - entityBounds.top)
                entityBounds.set(transform, hitbox.rect)
            } else if (abs(cy - topY) < halfH) {
                transform.offset(y = if (cy < topY) topY - entityBounds.bottom else topY - entityBounds.top)
                entityBounds.set(transform, hitbox.rect)
            }
        }

        if (cy in (k - b)..(k + b)) {
            val dx = a * sqrt(1f - ((cy - k) / b).pow(2))
            val leftX = h - dx;
            val rightX = h + dx
            val halfW = entityBounds.width * 0.5f
            if (abs(cx - rightX) < halfW) {
                transform.offset(x = if (cx < rightX) rightX - entityBounds.right else rightX - entityBounds.left)
                entityBounds.set(transform, hitbox.rect)
            } else if (abs(cx - leftX) < halfW) {
                transform.offset(x = if (cx < leftX) leftX - entityBounds.right else leftX - entityBounds.left)
                entityBounds.set(transform, hitbox.rect)
            }
        }
    }

    private fun resolveRectCollision(transform: Transform, hitbox: Hitbox, target: MutableRect) {
        val dx = entityBounds.center.x - target.center.x
        val dy = entityBounds.center.y - target.center.y
        val ox = (entityBounds.width + target.width) * 0.5f - abs(dx)
        val oy = (entityBounds.height + target.height) * 0.5f - abs(dy)
        if (ox > 0 && oy > 0) {
            if (ox < oy) transform.offset(x = if (dx > 0) ox else -ox)
            else transform.offset(y = if (dy > 0) oy else -oy)
            entityBounds.set(transform, hitbox.rect)
        }
    }
}