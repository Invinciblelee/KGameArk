package com.game.engine.core

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.text.TextMeasurer
import com.game.ecs.injectables.CoordinateTransform
import com.game.ecs.injectables.ViewportTransform
import com.game.engine.asset.AssetsManager
import com.game.engine.audio.AudioManager
import com.game.engine.context.PlatformContext
import com.game.engine.geometry.DefaultCoordinateTransform
import com.game.engine.geometry.DefaultViewportTransform
import com.game.engine.graphics.withViewportTransform
import com.game.engine.input.InputManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GameEngine(
    override val context: PlatformContext,
    override val textMeasurer: TextMeasurer
) : GameScope {

    private enum class TransitionState {
        Idle,
        FadingOut,
        FadingIn
    }

    companion object {
        private const val TRANSITION_DURATION_MS = 300
    }

    override var actualSize: Size by mutableStateOf(Size.Zero)
        private set

    override var virtualSize: Size by mutableStateOf(Size.Zero)
        private set

    override var canvasSize: Size by mutableStateOf(Size.Zero)
        private set

    override val viewportTransform: ViewportTransform = DefaultViewportTransform()
    override val coordinateTransform: CoordinateTransform = DefaultCoordinateTransform(viewportTransform)

    override var fps: Int by mutableIntStateOf(0)
        private set
    private var frameCount = 0
    private var timeAccumulator = 0f

    override val input = InputManager()
    override val audio = AudioManager(context)
    override val assets = AssetsManager()

    private val sceneRegistry = mutableMapOf<String, GameScene>()
    private val sceneHistory = ArrayDeque<String>()
    var currentScene by mutableStateOf<GameScene?>(null)
        private set

    private var pendingSceneId: String? = null
    private var pendingSceneParams: Map<String, Any>? = null
    private var transitionState by mutableStateOf(TransitionState.Idle)
    private var transitionAlpha = 0f
    private val transitionSpeed = 1000f / TRANSITION_DURATION_MS
    private var transitionJob: Job? = null

    override var transitionProgress: Float by mutableFloatStateOf(0f)
        private set

    private val paint = Paint().apply {
        blendMode = BlendMode.SrcOver
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun registerScene(id: String, scene: GameScene) {
        sceneRegistry[id] = scene
    }

    override fun presentScene(id: String, params: Map<String, Any>) {
        if (sceneRegistry.containsKey(id) && transitionState == TransitionState.Idle) {
            sceneHistory.addLast(id)
            pendingSceneId = id
            pendingSceneParams = params
            transitionState = TransitionState.FadingOut
        }
    }

    override fun dismissScene(params: Map<String, Any>) {
        if (transitionState == TransitionState.Idle) {
            transitionJob?.cancel()
            sceneHistory.removeLastOrNull()
            pendingSceneId = sceneHistory.lastOrNull() ?: return
            pendingSceneParams = params
            transitionState = TransitionState.FadingOut
        }
    }

    internal fun actualSizeChanged(size: Size) {
        if (this.actualSize == size) return
        this.actualSize = size
        viewportTransform.applyToSize(size, virtualSize)
    }

    internal fun virtualSizeChanged(size: Size) {
        if (this.virtualSize == size) return
        this.virtualSize = size
        viewportTransform.applyToSize(actualSize, size)
    }

    internal fun canvasSizeChanged(size: Size) {
        this.canvasSize = size
    }

    internal fun focusChanged(state: FocusState) {
        if (!state.isFocused) {
            input.clear()
        }
    }

    internal fun handleKeyEvent(event: KeyEvent): Boolean {
        when (event.type) {
            KeyEventType.KeyDown -> input.onKeyDown(event.key)
            KeyEventType.KeyUp -> input.onKeyUp(event.key)
        }
        return true
    }

    internal suspend fun handlePointerEvent(scope: PointerInputScope, canvasOffset: Offset) {
        scope.detectDragGestures(
            onDragStart = { position ->
                val globalPosition = position + canvasOffset
                val virtualPosition = viewportTransform.actualToVirtual(globalPosition)
                input.onPointerUpdate(virtualPosition, down = true)
            },
            onDragEnd = { input.onPointerUpdate(down = false) },
            onDragCancel = { input.onPointerUpdate(down = false) },
            onDrag = { change, _ ->
                change.consume()
                val globalPosition = change.position + canvasOffset
                val virtualPosition = viewportTransform.actualToVirtual(globalPosition)
                input.onPointerUpdate(virtualPosition, down = true)
            }
        )
    }

    internal suspend inline fun withFrameLoop(crossinline block: (Long) -> Unit) {
        var lastFrameTime = 0L
        while (currentCoroutineContext().isActive) {
            withFrameNanos { frameTimeNanos ->
                if (lastFrameTime == 0L) {
                    lastFrameTime = frameTimeNanos
                }

                val dt = (frameTimeNanos - lastFrameTime) / 1_000_000_000f
                lastFrameTime = frameTimeNanos

                tick(dt.coerceAtMost(0.1f))

                block(frameTimeNanos)
            }
        }
    }

    private fun tick(deltaTime: Float) {
        when (transitionState) {
            TransitionState.FadingOut -> {
                transitionAlpha += transitionSpeed * deltaTime
                if (transitionAlpha >= 1f) {
                    transitionAlpha = 1f
                    transitionJob = scope.launch { performSceneSwitch() }
                    transitionState = TransitionState.FadingIn
                }
            }

            TransitionState.FadingIn -> {
                transitionAlpha -= transitionSpeed * deltaTime
                if (transitionAlpha <= 0f) {
                    transitionAlpha = 0f
                    transitionState = TransitionState.Idle
                }
            }

            TransitionState.Idle -> {
                currentScene?.update(deltaTime)
            }
        }

        calculateFps(deltaTime)

        input.endFrame()
    }

    internal fun render(drawScope: DrawScope) {
        drawScope.withViewportTransform(viewportTransform) {
            if (transitionState == TransitionState.Idle) {
                currentScene?.render(this)
            } else {
                paint.alpha = 1f - transitionAlpha

                drawContext.canvas.saveLayer(
                    bounds = Rect(Offset.Zero, size),
                    paint = paint
                )

                currentScene?.render(this)

                drawContext.canvas.restore()
            }
        }
    }

    @Composable
    internal fun UI() {
        val scene = currentScene ?: return

        val animatedAlpha by animateFloatAsState(
            targetValue = when (transitionState) {
                TransitionState.FadingOut -> 0f
                TransitionState.FadingIn -> 1f
                TransitionState.Idle -> 1f
            },
            animationSpec = tween(
                durationMillis = TRANSITION_DURATION_MS,
                easing = LinearEasing
            ),
            label = "TransitionAlpha"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(animatedAlpha)
        ) {
            with(scene) { UI() }
        }
    }

    internal fun release() {
        audio.shutdown()
    }

    private suspend fun performSceneSwitch() {
        val id = pendingSceneId ?: return
        currentScene?.exit()
        currentScene?.unload()

        val next = sceneRegistry[id] ?: return

        currentScene = next
        next.configure(pendingSceneParams.orEmpty())
        next.load {
            transitionProgress = it
        }
        next.enter()

        pendingSceneId = null
    }

    private fun calculateFps(deltaTime: Float) {
        frameCount++
        timeAccumulator += deltaTime

        if (timeAccumulator >= 1.0f) {
            fps = frameCount
            frameCount = 0
            timeAccumulator -= 1.0f
        }
    }

}