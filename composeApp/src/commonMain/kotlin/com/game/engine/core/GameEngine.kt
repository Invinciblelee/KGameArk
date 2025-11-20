package com.game.engine.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import com.game.engine.audio.AudioManager
import com.game.engine.input.InputManager

class GameEngine(override val textMeasurer: TextMeasurer) : GameScope {

    private enum class TransitionState {
        Idle,       // 正常运行
        FadingOut, // 正在变黑
        FadingIn   // 正在变亮
    }

    override val input = InputManager()
    override val audio = AudioManager()

    // 屏幕尺寸 (由 UI 层更新)
    var screenSize by mutableStateOf(Size.Zero)
    override val size: Size get() = screenSize

    override var fps: Int = 0
        private set
    private var frameCount = 0
    private var timeAccumulator = 0f

    // 场景管理
    private val scenes = mutableMapOf<String, RuntimeScene>()
    var currentScene by mutableStateOf<RuntimeScene?>(null)

    private var transitionState by mutableStateOf(TransitionState.Idle)
    private var transitionAlpha by mutableStateOf(0f) // 0f = 透明, 1f = 全黑
    private var pendingSceneId: String? = null // 等待切换的目标场景
    private val transitionSpeed = 2.0f // 0.5秒完成

    fun registerScene(id: String, scene: RuntimeScene) {
        scenes[id] = scene
    }

    override fun switchScene(id: String) {
        if (transitionState == TransitionState.Idle) {
            pendingSceneId = id
            transitionState = TransitionState.FadingOut
        }
    }

    // 主循环
    fun update(dt: Float) {
        when (transitionState) {
            TransitionState.FadingOut -> {
                transitionAlpha += transitionSpeed * dt
                if (transitionAlpha >= 1f) {
                    transitionAlpha = 1f
                    performSceneSwitch()
                    transitionState = TransitionState.FadingIn
                }
            }
            TransitionState.FadingIn -> {
                transitionAlpha -= transitionSpeed * dt
                if (transitionAlpha <= 0f) {
                    transitionAlpha = 0f
                    transitionState = TransitionState.Idle
                }
            }
            TransitionState.Idle -> {
                currentScene?.update(dt)
            }
        }

        calculateFps(dt)
    }

    // 渲染循环
    fun render(drawScope: DrawScope) {
        currentScene?.render(drawScope)

        if (transitionAlpha > 0f) {
            drawScope.drawRect(
                color = Color.Black.copy(alpha = transitionAlpha),
                size = drawScope.size
            )
        }
    }

    fun log(msg: String) = println("[KEngine] $msg")

    // 私有：执行硬切换
    private fun performSceneSwitch() {
        val id = pendingSceneId ?: return
        currentScene?.onExit?.invoke()

        val next = scenes[id] ?: run {
            log("Error: Scene '$id' not found!")
            return
        }

        next.bind(this)
        currentScene = next
        next.onEnter?.invoke()

        pendingSceneId = null
    }

    private fun calculateFps(dt: Float) {
        frameCount++
        timeAccumulator += dt

        // 每过 1 秒，结算一次
        if (timeAccumulator >= 1.0f) {
            fps = frameCount
            frameCount = 0
            timeAccumulator -= 1.0f
        }
    }

}