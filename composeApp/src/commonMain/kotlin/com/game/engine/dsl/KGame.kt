package com.game.engine.dsl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.game.engine.context.PlatformContext
import com.game.engine.core.GameEngine
import com.game.engine.geometry.DefaultViewportTransform
import com.game.engine.input.InputManager
import com.game.engine.input.KeyInterceptor

/**
 * The main entry point of the game.
 * Example:
 **
 * ```kotlin
 * KGame(context, initialScene = "main", virtualSize = Size(800f, 600f)) {
 *     scene("main") {
 *          onEnter {
 *              // Scene entered
 *          }
 *
 *          onExit {
 *              // Scene exited
 *          }
 *
 *          onUpdate { dt ->
 *              // Update logic
 *          }
 *
 *          // behind the game canvas
 *          onBackgroundUI {
 *              Rectangle(Color.Black)
 *          }
 *
 *          // render game canvas
 *          onRender {
 *              drawRect(Color.Green)
 *          }
 *
 *          // above the game canvas
 *          onForegroundUI {
 *              // Use dp units directly. The engine has already handled screen scaling for virtualSize = Size(800f, 600f).
 *              // For example, 400.dp = half canvas width, 300.dp = half canvas height.
 *              // This draws a rectangle centered over the game canvas.
 *              Rectangle(Color.White, width = 400.dp, height = 300.dp, modifier = Modifier.align(Alignment.Center))
 *              Text("Welcome to My Game!", modifier = Modifier.align(Alignment.Center))
 *          }
 *     }
 *
 *     scene("game") {
 *          world(configuration = {
 *              injectables {
 *                  "key" + "value"
 *                  + ViewModel()
 *              }
 *              systems {
 *                  + PhysicsSystem()
 *                  + RenderSystem()
 *              }
 *          }) {
 *              entity {
 *                  it += Transform()
 *                  it += Camera()
 *              }
 *
 *              entities(100) {
 *                  it += Transform()
 *                  it += Renderable(Rectangle(Color.Red))
 *              }
 *          }
 *     }
 * }
 * ```
 *
 */
@Composable
fun KGame(
    context: PlatformContext,
    initialScene: String? = null,
    virtualSize: Size = Size(800f, 600f),
    modifier: Modifier = Modifier,
    foreground: (@Composable BoxScope.() -> Unit)? = null,
    background: (@Composable BoxScope.() -> Unit)? = null,
    init: GameConfigBuilder.() -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    val engine = remember {
        GameEngine(
            context = context,
            textMeasurer = textMeasurer
        )
    }
    val focusRequester = remember { FocusRequester() }
    var frameTrigger by remember { mutableLongStateOf(0L) }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            when {
                event == Lifecycle.Event.ON_START -> {
                    engine.resume()
                }

                event == Lifecycle.Event.ON_STOP -> {
                    engine.pause()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        GameConfigBuilder(engine).init()
        engine.init(initialScene)

        onDispose {
            engine.release()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        engine.withFrameLoop { frameTimeNanos ->
            frameTrigger = frameTimeNanos
        }
    }

    LaunchedEffect(engine.currentScene) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(virtualSize) {
        engine.virtualSizeChanged(virtualSize)
    }

    GameShell(
        modifier = modifier.preferredFrameRate(FrameRateCategory.High),
        engine = engine,
        focusRequester = focusRequester,
        virtualSize = virtualSize,
        frameTrigger = frameTrigger,
        background = background,
        foreground = foreground
    )
}

@Composable
private fun GameShell(
    modifier: Modifier,
    engine: GameEngine,
    focusRequester: FocusRequester,
    virtualSize: Size,
    frameTrigger: Long,
    background: (@Composable BoxScope.() -> Unit)?,
    foreground: (@Composable BoxScope.() -> Unit)?
) {
    val physicalDensity = LocalDensity.current
    val viewportTransform = engine.viewportTransform

    val (scaledWidthDp, scaledHeightDp) = remember(viewportTransform.scaleFactor, virtualSize) {
        with(physicalDensity) {
            val widthPx = virtualSize.width * viewportTransform.scaleFactor
            val heightPx = virtualSize.height * viewportTransform.scaleFactor
            widthPx.toDp() to heightPx.toDp()
        }
    }

    val scaledDensity = remember(viewportTransform.scaleFactor) {
        Density(density = viewportTransform.scaleFactor)
    }

    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onSizeChanged { engine.actualSizeChanged(it.toSize()) }
            .onKeyEvent { engine.handleKeyEvent(it) }
            .onFocusChanged { focusState -> engine.focusChanged(focusState) },
        contentAlignment = Alignment.Center
    ) {
        ScaledGameViewport(
            engine = engine,
            virtualSize = virtualSize,
            scaledDensity = scaledDensity,
            scaledWidthDp = scaledWidthDp,
            scaledHeightDp = scaledHeightDp,
            frameTrigger = frameTrigger,
            background = background,
            foreground = foreground
        )
    }
}

@Composable
private fun ScaledGameViewport(
    engine: GameEngine,
    virtualSize: Size,
    scaledDensity: Density,
    scaledWidthDp: Dp,
    scaledHeightDp: Dp,
    frameTrigger: Long,
    background: (@Composable BoxScope.() -> Unit)?,
    foreground: (@Composable BoxScope.() -> Unit)?
) {
    Box(modifier = Modifier.size(scaledWidthDp, scaledHeightDp)) {
        CompositionLocalProvider(
            LocalDensity provides scaledDensity
        ) {
            background?.invoke(this)
            engine.BackgroundUI()
        }
        GameCanvasRenderer(engine, virtualSize) { frameTrigger }

        CompositionLocalProvider(
            LocalDensity provides scaledDensity
        ) {
            engine.ForegroundUI()
            foreground?.invoke(this)
        }
    }
}

@Composable
private fun GameCanvasRenderer(engine: GameEngine, virtualSize: Size, frameTrigger: () -> Long) {
    var canvasOffset by remember { mutableStateOf(Offset.Zero) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { canvasOffset = it.positionInRoot() }
            .onSizeChanged { engine.canvasSizeChanged(it.toSize()) }
            .pointerInput(Unit) { engine.handlePointerEvent(this, canvasOffset) }
    ) {
        frameTrigger()

        drawContext.size = virtualSize
        engine.render(this)
    }
}

class GameConfigBuilder(
    val engine: GameEngine
) {
    @GameDslMarker
    fun scene(id: String, block: SceneBuilder.() -> Unit) {
        engine.registerScene(id, SceneBuilder(id, engine).apply(block).build())
    }
}