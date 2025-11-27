package com.game.engine.core

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.ui.NavDisplay
import com.game.engine.context.PlatformContext
import com.game.engine.dsl.GameSceneProvider
import com.game.engine.dsl.GameSceneProviderScope
import com.game.engine.dsl.SceneBuilderScope

/**
 * The main entry point of the game.
 * Example:
 **
 * ```kotlin
 * data object Main
 * data object Game
 *
 * val sceneStack = rememberGameSceneStack<Any>(Main)
 * KGame(context, sceneStack = sceneStack, virtualSize = Size(800f, 600f)) {
 *     scene<Main>() {
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
 *     scene<Game>() {
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
 *
 *          onUpdate { dt ->
 *              // Update logic
 *              if (input.isKeyUp(Key.Escape)) {
 *                  sceneStack.pop()
 *              }
 *          }
 *     }
 * }
 * ```
 *
 */
@Composable
fun <T : Any> KGame(
    context: PlatformContext,
    sceneStack: GameSceneStack<T>,
    virtualSize: Size = Size(800f, 600f),
    modifier: Modifier = Modifier,
    foreground: (@Composable BoxScope.() -> Unit)? = null,
    background: (@Composable BoxScope.() -> Unit)? = null,
    sceneBuilder: GameSceneProviderScope<T>.() -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    val engine = remember {
        GameEngine(
            context = context,
            textMeasurer = textMeasurer,
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            when {
                event == Lifecycle.Event.ON_START -> engine.enable()
                event == Lifecycle.Event.ON_STOP -> engine.disable()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            engine.release()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(virtualSize) {
        engine.virtualSizeChanged(virtualSize)
    }

    LaunchedEffect(Unit) {
        engine.scheduleFrameLoop()
    }

    GameShell(
        engine = engine,
        sceneStack = sceneStack,
        background = background,
        foreground = foreground,
        sceneBuilder = sceneBuilder,
        modifier = modifier,
    )
}

@Composable
fun KSimpleGame(
    context: PlatformContext,
    virtualSize: Size = Size(800f, 600f),
    modifier: Modifier = Modifier,
    foreground: (@Composable BoxScope.() -> Unit)? = null,
    background: (@Composable BoxScope.() -> Unit)? = null,
    sceneBuilder: SceneBuilderScope<Unit>.() -> Unit
) {
    KGame(
        context = context,
        sceneStack = rememberGameSceneStack(Unit),
        virtualSize = virtualSize,
        modifier = modifier,
        foreground = foreground,
        background = background,
    ) {
        addSceneProvider(Unit::class) {
            sceneBuilder()
        }
    }
}

@Composable
private fun <T : Any> GameShell(
    engine: GameEngine,
    sceneStack: GameSceneStack<T>,
    background: (@Composable BoxScope.() -> Unit)?,
    foreground: (@Composable BoxScope.() -> Unit)?,
    sceneBuilder: GameSceneProviderScope<T>.() -> Unit,
    modifier: Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .focusProperties {
                canFocus = false
                onExit = { focusRequester.requestFocus() }
            }
            .onPreviewKeyEvent { engine.handleKeyEvent(it) }
            .onFocusChanged { engine.focusChanged(it) }
            .onSizeChanged { engine.actualSizeChanged(it.toSize()) },
        contentAlignment = Alignment.Center
    ) {
        ScaledGameViewport(
            engine = engine,
            sceneStack = sceneStack,
            background = background,
            foreground = foreground,
            sceneBuilder = sceneBuilder
        )
    }
}

@Composable
private fun <T : Any> ScaledGameViewport(
    engine: GameEngine,
    sceneStack: GameSceneStack<T>,
    background: (@Composable BoxScope.() -> Unit)?,
    foreground: (@Composable BoxScope.() -> Unit)?,
    sceneBuilder: GameSceneProviderScope<T>.() -> Unit
) {
    val originalDensity = LocalDensity.current

    val viewportTransform = engine.viewportTransform
    val scaleFactor = viewportTransform.scaleFactor
    val scaledSize = viewportTransform.scaledSize

    val (scaledWidthDp, scaledHeightDp) = remember(scaledSize) {
        with(originalDensity) {
            scaledSize.width.toDp() to scaledSize.height.toDp()
        }
    }

    val scaledDensity = remember(scaleFactor) {
        Density(density = scaleFactor)
    }

    Box(modifier = Modifier.size(scaledWidthDp, scaledHeightDp).clipToBounds()) {
        CompositionLocalProvider(LocalDensity provides scaledDensity) {
            background?.invoke(this)
        }

        NavDisplay(
            backStack = sceneStack.backStack,
            onBack = sceneStack::pop,
            modifier = Modifier.matchParentSize(),
            transitionSpec = { DefaultContentTransform },
            popTransitionSpec = { DefaultContentTransform },
            predictivePopTransitionSpec = { DefaultContentTransform },
            entryProvider = GameSceneProvider(engine, builder = sceneBuilder)
        )

        CompositionLocalProvider(LocalDensity provides scaledDensity) {
            foreground?.invoke(this)
        }
    }
}

private val DefaultContentTransform = ContentTransform(
    fadeIn(animationSpec = tween(700)),
    fadeOut(animationSpec = tween(700))
)

