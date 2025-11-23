package com.game.engine.dsl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import com.game.engine.context.PlatformContext
import com.game.engine.core.GameEngine

/**
 * 游戏入口
 * @param initialScene 游戏初始场景
 * @param virtualSize 虚拟屏幕大小
 * @param modifier Modifier
 * @param foreground 前景
 * @param background 背景
 * @param init 游戏配置
 */
@Composable
fun KGame(
    context: PlatformContext,
    initialScene: String,
    virtualSize: Size = Size(800f, 600f),
    modifier: Modifier = Modifier,
    foreground: (@Composable BoxScope.() -> Unit)? = null,
    background: (@Composable BoxScope.() -> Unit)? = null,
    init: GameConfigBuilder.() -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    val engine = remember { GameEngine(context, textMeasurer) }
    val focusRequester = remember { FocusRequester() }
    var frameTrigger by remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) {
        GameConfigBuilder(engine).init()
        engine.presentScene(initialScene)
        onDispose {
            engine.release()
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
        background?.let {
            CompositionLocalProvider(
                LocalDensity provides scaledDensity
            ) {
                it.invoke(this)
            }
        }

        GameCanvasRenderer(engine, virtualSize) { frameTrigger }

        CompositionLocalProvider(
            LocalDensity provides scaledDensity
        ) {
            engine.UI()
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