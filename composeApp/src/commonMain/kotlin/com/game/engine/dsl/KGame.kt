package com.game.engine.dsl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.toSize
import com.game.engine.core.GameEngine
import kotlinx.coroutines.isActive

@Composable
fun KGame(
    initialScene: String,
    init: GameConfigBuilder.() -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    val engine = remember { GameEngine(textMeasurer) }
    val focusRequester = remember { FocusRequester() }

    // 1. 初始化 DSL 配置 & 加载初始场景
    LaunchedEffect(Unit) {
        val builder = GameConfigBuilder(engine)
        builder.init()
        engine.switchScene(initialScene)
        focusRequester.requestFocus()
    }

    var frameTrigger by remember { mutableLongStateOf(0L) }

    // 2. 游戏主循环
    LaunchedEffect(Unit) {
        var lastFrameTime = 0L

        while (isActive) {
            withFrameNanos { frameTimeNanos ->
                // 第一帧初始化时间
                if (lastFrameTime == 0L) {
                    lastFrameTime = frameTimeNanos
                }

                // 计算 dt (秒)
                val dt = (frameTimeNanos - lastFrameTime) / 1_000_000_000f
                lastFrameTime = frameTimeNanos

                // 限制 dt 最大值，防止卡顿后瞬移
                engine.update(dt.coerceAtMost(0.1f))

                frameTrigger = frameTimeNanos
            }
        }
    }

    // 3. 视图层
    Box(
        modifier = Modifier
            .preferredFrameRate(FrameRateCategory.High)
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onSizeChanged { engine.screenSize = it.toSize() }
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown) engine.input.onKeyDown(it.key)
                else if (it.type == KeyEventType.KeyUp) engine.input.onKeyUp(it.key)
                true
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { 
                        engine.input.onMouseUpdate(it, true) 
                    },
                    onDragEnd = { 
                        engine.input.onMouseUpdate(engine.input.mousePosition, false)
                    },
                    onDragCancel = {
                        engine.input.onMouseUpdate(engine.input.mousePosition, false)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        // 拖拽时也是按下状态
                        engine.input.onMouseUpdate(change.position, true)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            frameTrigger

            engine.render(this)
        }
    }
}

class GameConfigBuilder(
    val engine: GameEngine
) {
    @GameDsl
    fun scene(id: String, block: SceneBuilder.() -> Unit) {
        engine.registerScene(id, SceneBuilder(engine).apply(block).build())
    }
}