package com.kgame.plugins.systems

import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.kgame.ecs.IntervalSystem
import com.kgame.ecs.SystemPriority
import com.kgame.ecs.World.Companion.family
import com.kgame.engine.geometry.set
import com.kgame.plugins.components.Hitbox
import com.kgame.plugins.components.RigidBody
import com.kgame.plugins.components.Transform
import com.kgame.plugins.components.applyCollision
import kotlin.math.abs

/**
 * The CollisionSystem is responsible for handling rigid body collisions.
 */
class CollisionSystem(priority: SystemPriority = SystemPriorityAnchors.Physics): IntervalSystem(priority = priority) {

    private val family = family { all(RigidBody, Transform, Hitbox) }

    private var keys = FloatArray(2048)
    private var indices = IntArray(2048)
    private var count = 0

    private val bounds1 = MutableRect(0f, 0f, 0f, 0f)
    private val bounds2 = MutableRect(0f, 0f, 0f, 0f)

    override fun onTick(deltaTime: Float) {
        val n = family.entitySize

        if (n == 0) return

        ensureCapacity(n)
        count = 0

        var index = 0
        while (index < n) {
            val entity = family[index]
            val transform = entity[Transform]
            val hitbox = entity[Hitbox]
            keys[count] = transform.position.x - hitbox.rect.width / 2f
            indices[count] = index
            count++
            index++
        }

        quickSort(0, count - 1)

        var i = 0
        while (i < count) {
            val entity1 = family[indices[i]]
            val transform1 = entity1[Transform]
            val rigidBody1 = entity1[RigidBody]
            val hitbox1 = entity1[Hitbox]
            val max1 = transform1.position.x + hitbox1.rect.width / 2f

            var j = i + 1
            while (j < count) {
                val entity2 = family[indices[j]]
                val transform2 = entity2[Transform]
                val rigidBody2 = entity2[RigidBody]
                val hitbox2 = entity2[Hitbox]
                val min2 = transform2.position.x - hitbox2.rect.width / 2f

                if (min2 > max1) break
                if (overlaps(transform1, hitbox1.rect, transform2, hitbox2.rect)) {
                    separate(transform1, rigidBody1, transform2, rigidBody2)
                }
                j++
            }
            i++
        }
    }

    private fun ensureCapacity(required: Int) {
        if (keys.size >= required) return
        val newSize = (required * 1.5f).toInt().coerceAtLeast(2048)
        keys = FloatArray(newSize)
        indices = IntArray(newSize)
    }

    private fun quickSort(left: Int, right: Int) {
        var l = left
        var r = right
        if (l >= r) return

        val pivot = keys[(l + r) shr 1]
        while (l <= r) {
            while (l <= right && keys[l] < pivot) l++
            while (r >= left  && keys[r] > pivot) r--
            if (l <= r) {
                val tmpK = keys[l]; keys[l] = keys[r]; keys[r] = tmpK
                val tmpIdx = indices[l]; indices[l] = indices[r]; indices[r] = tmpIdx
                l++; r--
            }
        }
        if (left < r) quickSort(left, r)
        if (l < right) quickSort(l, right)
    }

    private fun overlaps(t1: Transform, r1: Rect, t2: Transform, r2: Rect): Boolean {
        bounds1.set(t1, r1)
        bounds2.set(t2, r2)
        return bounds1.overlaps(bounds2)
    }

    private fun separate(t1: Transform, r1: RigidBody, t2: Transform, r2: RigidBody) {
        val overlapX =
            (bounds1.width / 2f + bounds2.width / 2f) - abs(t1.position.x - t2.position.x)
        val overlapY =
            (bounds1.height / 2f + bounds2.height / 2f) - abs(t1.position.y - t2.position.y)

        if (overlapX <= 0 || overlapY <= 0) return

        val separation = if (overlapX < overlapY) {
            Offset(overlapX * if (t1.position.x > t2.position.x) 1f else -1f, 0f)
        } else {
            Offset(0f, overlapY * if (t1.position.y > t2.position.y) 1f else -1f)
        }

        RigidBody.applyCollision(r1, t1, r2, t2, separation)
    }
}