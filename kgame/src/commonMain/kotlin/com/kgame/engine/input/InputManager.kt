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

interface InputManager {
    /** The virtual position of the pointer (mouse/touch) after resolution scaling. */
    val pointerPosition: Offset

    /** The scroll wheel or trackpad delta for the current frame. */
    val scrollDelta: Offset

    /** Whether any pointer (mouse button 0 or touch) is currently active. */
    val isPointerDown: Boolean

    /** Whether ANY keyboard key is currently being held down. */
    val isKeyDown: Boolean

    /** Register an interceptor to block specific keys from reaching the game. */
    fun intercept(interceptor: KeyInterceptor)

    /** Manually inject a key press (useful for virtual on-screen buttons). */
    fun simulateKey(key: Key)

    /** Entry point for Compose Multiplatform pointer events. */
    fun onPointerEvent(event: PointerEvent)

    /** Entry point for Compose Multiplatform keyboard events. */
    fun onKeyEvent(event: KeyEvent): Boolean

    // --- Keyboard Polling ---

    /** Checks if a specific key is currently held down. */
    fun isKeyDown(key: Key): Boolean

    fun isKeyJustPressed(key: Key): Boolean

    fun isKeyJustReleased(key: Key): Boolean

    // --- Mouse Polling (0: Left, 1: Right, 2: Middle) ---

    fun isMouseDown(button: Int = 0): Boolean

    fun isMouseJustPressed(button: Int = 0): Boolean

    fun isMouseJustReleased(button: Int = 0): Boolean

    /**
     * Helper to get a normalized axis value (-1.0 to 1.0).
     */
    fun getAxis(positive: Key, negative: Key): Float

    /** Must be called at the very end of the game loop frame. */
    fun endFrame()

    /** Resets all input states. */
    fun clear()
}

@Stable
class DefaultInputManager(private val resolution: ResolutionManager) : InputManager {

    private companion object {
        const val MASK_IS_DOWN: Long = 1L shl 0
        const val MASK_WAS_DOWN: Long = 1L shl 1
        const val MASK_PENDING_UP: Long = 1L shl 2
        const val MOUSE_BASE_ID = 1_000_000L
    }

    private val keyStates = MutableLongLongMap()
    private var keyInterceptor: KeyInterceptor = KeyInterceptor

    override var pointerPosition: Offset = Offset.Zero
        private set

    override var scrollDelta: Offset = Offset.Zero
        private set

    override var isPointerDown: Boolean = false
        private set

    override val isKeyDown: Boolean
        get() {
            keyStates.forEach { code, state ->
                if (code < MOUSE_BASE_ID && (state and MASK_IS_DOWN) != 0L) {
                    return true
                }
            }
            return false
        }

    // --- Interceptor & Simulation ---

    override fun intercept(interceptor: KeyInterceptor) {
        keyInterceptor = interceptor
    }

    override fun simulateKey(key: Key) {
        val keyCode = key.keyCode
        val currentState = keyStates.getOrDefault(keyCode, 0L)
        keyStates.put(keyCode, currentState or MASK_IS_DOWN or MASK_PENDING_UP)
    }

    // --- Event Handling ---

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.key.keyCode
        updateInternalState(keyCode, event.type == KeyEventType.KeyDown)
        return keyInterceptor.shouldIntercept(event.key)
    }

    /**
     * Correct implementation based on Compose PointerEvent source.
     */
    override fun onPointerEvent(event: PointerEvent) {
        // 1. Update Position & Scroll from changes
        val changes = event.changes
        var i = 0
        var accScroll = Offset.Zero
        var pointerDown = false
        while (i < changes.size) {
            val change = changes[i]
            if (change.pressed) pointerDown = true
            if (i == 0) {
                // Using the first pointer's position as the main pointerPosition
                pointerPosition = resolution.actualToVirtual(change.position)
            }
            accScroll += change.scrollDelta
            i++
        }
        scrollDelta = accScroll
        isPointerDown = pointerDown

        // 2. Update Mouse Button States using event.buttons mask
        val buttons = event.buttons
        updateInternalState(MOUSE_BASE_ID + 0, buttons.isPrimaryPressed)
        updateInternalState(MOUSE_BASE_ID + 1, buttons.isSecondaryPressed)
        updateInternalState(MOUSE_BASE_ID + 2, buttons.isTertiaryPressed)
        updateInternalState(MOUSE_BASE_ID + 3, buttons.isBackPressed)
        updateInternalState(MOUSE_BASE_ID + 4, buttons.isForwardPressed)
    }

    /**
     * Preserves original logic: Handles IS_DOWN and transitions to WAS_DOWN.
     */
    private fun updateInternalState(code: Long, isDown: Boolean) {
        val currentState = keyStates.getOrDefault(code, 0L) and MASK_PENDING_UP.inv()
        if (isDown) {
            keyStates.put(code, currentState or MASK_IS_DOWN)
        } else {
            // Transition from DOWN to WAS_DOWN for JustReleased detection
            if ((currentState and MASK_IS_DOWN) != 0L) {
                keyStates.put(code, (currentState and MASK_IS_DOWN.inv()) or MASK_WAS_DOWN)
            }
        }
    }

    // --- Polling Methods ---

    override fun isKeyDown(key: Key): Boolean = check(key.keyCode, MASK_IS_DOWN)
    override fun isKeyJustPressed(key: Key): Boolean = checkJustPressed(key.keyCode)
    override fun isKeyJustReleased(key: Key): Boolean = checkJustReleased(key.keyCode)

    override fun isMouseDown(button: Int): Boolean = check(MOUSE_BASE_ID + button, MASK_IS_DOWN)
    override fun isMouseJustPressed(button: Int): Boolean = checkJustPressed(MOUSE_BASE_ID + button)
    override fun isMouseJustReleased(button: Int): Boolean = checkJustReleased(MOUSE_BASE_ID + button)

    private fun check(code: Long, mask: Long): Boolean = (keyStates.getOrDefault(code, 0L) and mask) != 0L

    private fun checkJustPressed(code: Long): Boolean {
        val state = keyStates.getOrDefault(code, 0L)
        return (state and MASK_WAS_DOWN) == 0L && (state and MASK_IS_DOWN) != 0L
    }

    private fun checkJustReleased(code: Long): Boolean {
        val state = keyStates.getOrDefault(code, 0L)
        return (state and MASK_WAS_DOWN) != 0L && (state and MASK_IS_DOWN) == 0L
    }

    override fun getAxis(positive: Key, negative: Key): Float {
        var axisValue = 0f
        if (isKeyDown(positive)) axisValue += 1f
        if (isKeyDown(negative)) axisValue -= 1f
        return axisValue
    }

    // --- Lifecycle ---

    override fun endFrame() {
        keyStates.forEach { keyCode, state ->
            val nextState = if ((state and MASK_PENDING_UP) != 0L) {
                ((state and MASK_IS_DOWN.inv()) and MASK_PENDING_UP.inv()) or MASK_WAS_DOWN
            } else {
                if ((state and MASK_IS_DOWN) != 0L) (state or MASK_WAS_DOWN) else (state and MASK_WAS_DOWN.inv())
            }
            keyStates.put(keyCode, nextState)
        }
        scrollDelta = Offset.Zero
    }

    override fun clear() {
        keyStates.clear()
        pointerPosition = Offset.Zero
        scrollDelta = Offset.Zero
    }
}