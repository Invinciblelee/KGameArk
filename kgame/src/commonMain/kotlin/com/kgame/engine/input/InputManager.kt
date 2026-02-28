package com.kgame.engine.input

import androidx.collection.MutableLongLongMap
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.isForwardPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import com.kgame.engine.geometry.ResolutionManager

fun interface KeyInterceptor {
    fun shouldIntercept(key: Key): Boolean

    companion object : KeyInterceptor {
        override fun shouldIntercept(key: Key): Boolean {
            return when (key) {
                Key.VolumeUp, Key.VolumeDown, Key.VolumeMute -> false
                else -> true
            }
        }
    }
}

/**
 * High-performance Input API for Zen Edition.
 * Provides polling-based access to keyboard, mouse, and multi-touch states.
 */
interface InputManager {
    /** Number of active pointers (fingers/mouse buttons) currently pressed down. */
    val pointerCount: Int

    /** Global check: returns true if any pointer is currently pressed. */
    val isPointerDown: Boolean

    /** Global check: returns true if any physical keyboard key is being held. */
    val isKeyDown: Boolean

    /** Displacement of the mouse scroll wheel in the current frame. */
    val scrollDelta: Offset

    // --- Pointer Polling (Multi-touch 0-9) ---
    fun getPointerPosition(index: Int = 0): Offset
    fun isPointerDown(index: Int = 0): Boolean
    fun isPointerJustPressed(index: Int = 0): Boolean
    fun getPointerId(index: Int): Long

    // --- Keyboard Polling ---
    fun isKeyDown(key: Key): Boolean
    fun isKeyJustPressed(key: Key): Boolean
    fun isKeyJustReleased(key: Key): Boolean

    // --- Mouse Polling (Legacy Support) ---
    fun isMouseDown(button: Int = 0): Boolean
    fun isMouseJustPressed(button: Int = 0): Boolean
    fun isMouseJustReleased(button: Int = 0): Boolean

    // --- Utility Methods ---
    fun getAxis(positive: Key, negative: Key): Float
    fun intercept(interceptor: KeyInterceptor)
    fun simulateKey(key: Key)

    // --- System Injection & Lifecycle ---
    fun onPointerEvent(event: PointerEvent)
    fun onKeyEvent(event: KeyEvent): Boolean
    fun endFrame()
    fun clear()
}

@Stable
class DefaultInputManager(private val resolution: ResolutionManager) : InputManager {

    private companion object {
        const val MAX_POINTERS = 10
        const val MOUSE_BASE_ID = 1_000_000L

        const val MASK_IS_DOWN = 1 shl 0
        const val MASK_WAS_DOWN = 1 shl 1
        const val MASK_PENDING_UP = 1 shl 2
    }

    private val pointerIds = LongArray(MAX_POINTERS) { -1L }
    private val pointerPositions = LongArray(MAX_POINTERS)
    private val pointerStates = IntArray(MAX_POINTERS)

    // Dynamic arrays to prevent key state update drops when exceeding 64 keys
    private var keyUpdateCodes = LongArray(64)
    private var keyUpdateStates = LongArray(64)

    private var activeBitmask: Int = 0

    private val keyStates = MutableLongLongMap()
    private var keyInterceptor: KeyInterceptor = KeyInterceptor

    override var pointerCount: Int = 0
        private set

    override var scrollDelta: Offset = Offset.Zero
        private set

    override val isPointerDown: Boolean get() = pointerCount > 0

    override val isKeyDown: Boolean
        get() {
            keyStates.forEach { code, state ->
                if (code < MOUSE_BASE_ID && (state.toInt() and MASK_IS_DOWN) != 0) {
                    return true
                }
            }
            return false
        }

    override fun getAxis(positive: Key, negative: Key): Float {
        var v = 0f
        if (isKeyDown(positive)) v += 1f
        if (isKeyDown(negative)) v -= 1f
        return v
    }

    override fun onPointerEvent(event: PointerEvent) {
        val changes = event.changes
        val changeSize = changes.size
        var eventMask = 0

        var i = 0
        while (i < changeSize) {
            val change = changes[i]
            val slot = findOrAssignSlot(change.id.value)

            if (slot != -1) {
                eventMask = eventMask or (1 shl slot)
                activeBitmask = activeBitmask or (1 shl slot)

                val virtualPos = resolution.actualToVirtual(change.position)
                if (virtualPos.packedValue != pointerPositions[slot]) {
                    pointerPositions[slot] = virtualPos.packedValue
                }

                if (change.pressed) {
                    pointerStates[slot] = pointerStates[slot] or MASK_IS_DOWN
                } else {
                    if ((pointerStates[slot] and MASK_IS_DOWN) != 0) {
                        pointerStates[slot] = (pointerStates[slot] and MASK_IS_DOWN.inv()) or MASK_WAS_DOWN
                    }
                }
            }
            i++
        }

        var tempMask = activeBitmask
        var activeTotal = 0
        while (tempMask != 0) {
            val slot = tempMask.countTrailingZeroBits()
            val bit = 1 shl slot

            if ((eventMask and bit) == 0) {
                if ((pointerStates[slot] and MASK_IS_DOWN) != 0) {
                    pointerStates[slot] = (pointerStates[slot] and MASK_IS_DOWN.inv()) or MASK_WAS_DOWN
                } else if (pointerStates[slot] == 0) {
                    // Free the slot if a hover pointer leaves the screen to prevent slot exhaustion
                    pointerIds[slot] = -1L
                    activeBitmask = activeBitmask and bit.inv()
                }
            }

            if ((pointerStates[slot] and MASK_IS_DOWN) != 0) {
                activeTotal++
            }
            tempMask = tempMask and (tempMask - 1)
        }
        pointerCount = activeTotal

        val btns = event.buttons
        updateKeyInternal(MOUSE_BASE_ID + 0, btns.isPrimaryPressed)
        updateKeyInternal(MOUSE_BASE_ID + 1, btns.isSecondaryPressed)
        updateKeyInternal(MOUSE_BASE_ID + 2, btns.isTertiaryPressed)
        updateKeyInternal(MOUSE_BASE_ID + 3, btns.isBackPressed)
        updateKeyInternal(MOUSE_BASE_ID + 4, btns.isForwardPressed)

        var sx = 0f; var sy = 0f; var k = 0
        while (k < changeSize) {
            val d = changes[k].scrollDelta
            sx += d.x; sy += d.y; k++
        }
        scrollDelta = Offset(sx, sy)
    }

    private fun findOrAssignSlot(id: Long): Int {
        var i = 0
        while (i < MAX_POINTERS) {
            if (pointerIds[i] == id) return i
            i++
        }
        var j = 0
        while (j < MAX_POINTERS) {
            if (pointerIds[j] == -1L) {
                pointerIds[j] = id
                pointerStates[j] = 0
                return j
            }
            j++
        }
        return -1
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        updateKeyInternal(event.key.keyCode, event.type == KeyEventType.KeyDown)
        return keyInterceptor.shouldIntercept(event.key)
    }

    override fun getPointerPosition(index: Int): Offset = if (index in 0..<MAX_POINTERS) Offset(pointerPositions[index]) else Offset.Zero
    override fun isPointerDown(index: Int): Boolean = if (index in 0..<MAX_POINTERS) (pointerStates[index] and MASK_IS_DOWN) != 0 else false
    override fun isPointerJustPressed(index: Int): Boolean {
        if (index !in 0..<MAX_POINTERS) return false
        val s = pointerStates[index]
        return (s and MASK_IS_DOWN) != 0 && (s and MASK_WAS_DOWN) == 0
    }
    override fun getPointerId(index: Int): Long = if (index in 0..<MAX_POINTERS) pointerIds[index] else -1L

    override fun isKeyDown(key: Key): Boolean = (keyStates.getOrDefault(key.keyCode, 0L).toInt() and MASK_IS_DOWN) != 0
    override fun isKeyJustPressed(key: Key): Boolean {
        val s = keyStates.getOrDefault(key.keyCode, 0L).toInt()
        return (s and MASK_IS_DOWN) != 0 && (s and MASK_WAS_DOWN) == 0
    }
    override fun isKeyJustReleased(key: Key): Boolean {
        val s = keyStates.getOrDefault(key.keyCode, 0L).toInt()
        return (s and MASK_IS_DOWN) == 0 && (s and MASK_WAS_DOWN) != 0
    }

    override fun isMouseDown(button: Int): Boolean = (keyStates.getOrDefault(MOUSE_BASE_ID + button, 0L).toInt() and MASK_IS_DOWN) != 0
    override fun isMouseJustPressed(button: Int): Boolean {
        val s = keyStates.getOrDefault(MOUSE_BASE_ID + button, 0L).toInt()
        return (s and MASK_IS_DOWN) != 0 && (s and MASK_WAS_DOWN) == 0
    }
    override fun isMouseJustReleased(button: Int): Boolean {
        val s = keyStates.getOrDefault(MOUSE_BASE_ID + button, 0L).toInt()
        return (s and MASK_IS_DOWN) == 0 && (s and MASK_WAS_DOWN) != 0
    }

    private fun updateKeyInternal(code: Long, isDown: Boolean) {
        val current = keyStates.getOrDefault(code, 0L).toInt() and MASK_PENDING_UP.inv()
        if (isDown) {
            keyStates.put(code, (current or MASK_IS_DOWN).toLong())
        } else if ((current and MASK_IS_DOWN) != 0) {
            keyStates.put(code, ((current and MASK_IS_DOWN.inv()) or MASK_WAS_DOWN).toLong())
        }
    }

    override fun simulateKey(key: Key) {
        val c = key.keyCode
        val s = keyStates.getOrDefault(c, 0L).toInt()
        keyStates.put(c, (s or MASK_IS_DOWN or MASK_PENDING_UP).toLong())
    }

    override fun intercept(interceptor: KeyInterceptor) { keyInterceptor = interceptor }

    // Ensures arrays are large enough to handle all keys currently tracked
    private fun ensureKeyUpdateCapacity(requiredSize: Int) {
        if (keyUpdateCodes.size < requiredSize) {
            var newSize = keyUpdateCodes.size * 2
            while (newSize < requiredSize) newSize *= 2
            keyUpdateCodes = keyUpdateCodes.copyOf(newSize)
            keyUpdateStates = keyUpdateStates.copyOf(newSize)
        }
    }

    override fun endFrame() {
        ensureKeyUpdateCapacity(keyStates.size)

        var updateCount = 0
        keyStates.forEach { code, state ->
            val s = state.toInt()
            val next = if ((s and MASK_PENDING_UP) != 0) {
                ((s and MASK_IS_DOWN.inv()) and MASK_PENDING_UP.inv()) or MASK_WAS_DOWN
            } else {
                if ((s and MASK_IS_DOWN) != 0) (s or MASK_WAS_DOWN) else (s and MASK_WAS_DOWN.inv())
            }

            keyUpdateCodes[updateCount] = code
            keyUpdateStates[updateCount] = next.toLong()
            updateCount++
        }

        var i = 0
        while (i < updateCount) {
            keyStates.put(keyUpdateCodes[i], keyUpdateStates[i])
            i++
        }

        var tempMask = activeBitmask
        while (tempMask != 0) {
            val slot = tempMask.countTrailingZeroBits()
            val s = pointerStates[slot]

            if ((s and MASK_WAS_DOWN) != 0 && (s and MASK_IS_DOWN) == 0) {
                pointerStates[slot] = 0
                pointerIds[slot] = -1L
                activeBitmask = activeBitmask and (1 shl slot).inv()
            } else if ((s and MASK_IS_DOWN) != 0) {
                pointerStates[slot] = pointerStates[slot] or MASK_WAS_DOWN
            }
            tempMask = tempMask and (tempMask - 1)
        }
        scrollDelta = Offset.Zero
    }

    override fun clear() {
        keyStates.clear(); activeBitmask = 0; var i = 0
        while (i < MAX_POINTERS) {
            pointerIds[i] = -1L; pointerStates[i] = 0; pointerPositions[i] = 0L; i++
        }
        pointerCount = 0; scrollDelta = Offset.Zero
    }
}