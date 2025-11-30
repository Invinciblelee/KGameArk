package com.game.plugins.systems

import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Offset
import com.game.ecs.IntervalSystem
import com.game.ecs.World.Companion.family
import com.game.ecs.World.Companion.inject
import com.game.plugins.components.RigidBody
import com.game.plugins.components.Transform
import com.game.plugins.components.applyCollision
import com.game.plugins.components.getBounds
import com.game.plugins.services.CameraService
import kotlin.math.abs

/**
 * The CollisionSystem is responsible for handling rigid body collisions.
 */
class CollisionSystem(
    private val cameraService: CameraService = inject()
) : IntervalSystem() {

    private val family = family { all(RigidBody, Transform) }

    private var keys    = FloatArray(2048)
    private var indices = IntArray(2048)
    private var count   = 0

    private val bounds1 = MutableRect(0f, 0f, 0f, 0f)
    private val bounds2 = MutableRect(0f, 0f, 0f, 0f)

    override fun onTick() {
        val n = family.entitySize
        ensureCapacity(n)
        count = 0

        var i = 0
        while (i < n) {
            val entity = family[i]
            val t = entity[Transform]
            if (cameraService.culler.overlaps(t)) {
                keys[count] = t.position.x - t.size.width / 2f
                indices[count] = i
                count++
            }
            i++
        }

        quickSort(0, count - 1)

        var idx = 0
        while (idx < count) {
            val e1   = family[indices[idx]]
            val t1   = e1[Transform]; val r1 = e1[RigidBody]
            val max1 = t1.position.x + t1.size.width / 2f

            var j = idx + 1
            while (j < count) {
                val e2   = family[indices[j]]
                val t2   = e2[Transform]; val r2 = e2[RigidBody]
                val min2 = t2.position.x - t2.size.width / 2f

                if (min2 > max1) break
                if (overlaps(t1, t2)) separate(t1, r1, t2, r2)
                j++
            }
            idx++
        }
    }

    private fun ensureCapacity(required: Int) {
        if (keys.size >= required) return
        val newSize = (required * 1.5f).toInt().coerceAtLeast(2048)
        keys    = FloatArray(newSize)
        indices = IntArray(newSize)
    }

    private fun quickSort(left: Int, right: Int) {
        var l = left
        var r = right
        val pivot = keys[(l + r) shr 1]
        while (l <= r) {
            while (keys[l] < pivot) l++
            while (keys[r] > pivot) r--
            if (l <= r) {
                val tmpK   = keys[l];   keys[l]   = keys[r];   keys[r]   = tmpK
                val tmpIdx = indices[l]; indices[l] = indices[r]; indices[r] = tmpIdx
                l++; r--
            }
        }
        if (left < r)  quickSort(left, r)
        if (l < right) quickSort(l, right)
    }

    private fun overlaps(t1: Transform, t2: Transform): Boolean {
        t1.getBounds(bounds1)
        t2.getBounds(bounds2)
        return bounds1.overlaps(bounds2)
    }

    private fun separate(t1: Transform, r1: RigidBody, t2: Transform, r2: RigidBody) {
        val overlapX = (bounds1.width / 2f + bounds2.width / 2f) - abs(t1.position.x - t2.position.x)
        val overlapY = (bounds1.height / 2f + bounds2.height / 2f) - abs(t1.position.y - t2.position.y)

        if (overlapX <= 0 || overlapY <= 0) return

        val separation = if (overlapX < overlapY) {
            Offset(overlapX * if (t1.position.x > t2.position.x) 1f else -1f, 0f)
        } else {
            Offset(0f, overlapY * if (t1.position.y > t2.position.y) 1f else -1f)
        }

        RigidBody.applyCollision(r1, t1, r2, t2, separation)
    }
}