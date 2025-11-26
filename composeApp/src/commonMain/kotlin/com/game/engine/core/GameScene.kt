package com.game.engine.core

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.game.engine.asset.AssetKey
import com.game.engine.dsl.GameWorld
import kotlinx.atomicfu.atomic

/**
 * Represents a scene in the game.
 *
 * @param id The unique identifier of the scene.
 * @param scope The game scope.
 * @param onEnter A function to be called when the scene is entered.
 * @param onExit A function to be called when the scene is exited
 * @param update A function to be called every frame to update the scene.
 * @param render A function to be called to render the scene on game canvas.
 * @param foregroundUI A function to be called to render the foreground UI, which is rendered on top of the game canvas.
 * @param backgroundUI A function to be called to render the background UI, which is rendered behind the game canvas.
 * @param world The game world.
 * @param config The scene config.
 * @param assets The assets to be loaded.
 */
@Immutable
class GameScene(
    val id: String,
    private val scope: GameScope,
    private val onEnter: (() -> Unit)?,
    private val onExit: (() -> Unit)?,
    private val update: ((Float) -> Unit)?,
    private val render: (DrawScope.() -> Unit)?,
    private val foregroundUI: (@Composable BoxScope.() -> Unit)?,
    private val backgroundUI: (@Composable BoxScope.() -> Unit)?,
    private val world: GameWorld?,
    private val config: SceneConfig,
    private val assets: Set<AssetKey<*>> = emptySet()
) {
    private var isActive by atomic(false)

    private var isLoading by atomic(false)

    internal fun configure(params: Map<String, Any>) {
        config.update(params)
    }

    internal suspend fun load(progress: (Float) -> Unit) {
        if (isLoading) return

        if (assets.isEmpty()) {
            progress(1f)
            return
        }

        isLoading = true
        try {
            progress(0f)
            val totalAssets = assets.size.toFloat()
            assets.forEachIndexed { index, key ->
                scope.assets.load(key)
                progress((index + 1) / totalAssets)
            }
            progress(1f)
        } finally {
            isLoading = false
        }
    }

    internal fun unload() {
        for (key in assets) {
            scope.assets.unload(key)
        }
    }

    internal fun enter() {
        isActive = true

        world?.enter()

        onEnter?.invoke()
    }

    internal fun exit() {
        isActive = false

        onExit?.invoke()

        world?.exit()
    }

    internal fun update(deltaTime: Float) {
        if (isActive) {
            world?.update(deltaTime)
        }
        update?.invoke(deltaTime)
    }

    internal fun render(drawScope: DrawScope) {
        if (isActive) {
            world?.render(drawScope)
        }
        render?.invoke(drawScope)
    }

    @Composable
    internal fun BoxScope.ForegroundUI() {
        foregroundUI?.invoke(this)
    }

    @Composable
    internal fun BoxScope.BackgroundUI() {
        backgroundUI?.invoke(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as GameScene

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

}