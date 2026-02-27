package com.example.kgame.games.collision

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Velocity
import com.example.kgame.games.GameAssets
import com.kgame.ecs.Entity
import com.kgame.ecs.IteratingSystem
import com.kgame.ecs.World.Companion.family
import com.kgame.ecs.World.Companion.inject
import com.kgame.engine.core.KSimpleGame
import com.kgame.engine.math.random
import com.kgame.engine.math.randomOffset
import com.kgame.engine.utils.FpsCalculator
import com.kgame.plugins.components.Camera
import com.kgame.plugins.components.CameraTarget
import com.kgame.plugins.components.Hitbox
import com.kgame.plugins.components.Renderable
import com.kgame.plugins.components.RigidBody
import com.kgame.plugins.components.SpriteAnimation
import com.kgame.plugins.components.Transform
import com.kgame.plugins.components.WorldBounds
import com.kgame.plugins.services.CameraService
import com.kgame.plugins.systems.AnimationSystem
import com.kgame.plugins.systems.AnimationTickSystem
import com.kgame.plugins.systems.CollisionSystem
import com.kgame.plugins.systems.RenderSystem
import com.kgame.plugins.visuals.images.SpriteVisual
import com.kgame.plugins.visuals.shapes.CircleVisual
import kotlin.random.Random

private class MovementSystem(
    val cameraService: CameraService = inject(),
) : IteratingSystem(
    family { all(Transform, RigidBody, Renderable) }
) {
    override fun onTickEntity(entity: Entity, deltaTime: Float) {
        val tf = entity[Transform]
        val rb = entity[RigidBody]
        val rr = entity[Renderable]

        /* 运动（复用基本类型） */
        tf.position = tf.position.copy(
            x = tf.position.x + rb.velocity.x * deltaTime,
            y = tf.position.y + rb.velocity.y * deltaTime
        )

        /* 边界反弹：一行调用你的封装（零临时对象） */
        val clamped = cameraService.transformer.clampToViewport(tf.position, rr.size)

        /* 若被 clamp → 反弹速度 */
        if (clamped.x != tf.position.x) rb.velocity = rb.velocity.copy(x = -rb.velocity.x)
        if (clamped.y != tf.position.y) rb.velocity = rb.velocity.copy(y = -rb.velocity.y)

        /* 写回最终位置（零临时对象） */
        tf.position = clamped
    }
}

private val EaseOutOvershoot = CubicBezierEasing(0.4f, 1.5f, 0.8f, 1.0f)

@Composable
fun CollisionGame() {
    KSimpleGame(
        modifier = Modifier.fillMaxSize(),
    ) {
        onWorld {
            configure {
                systems {
                    +CollisionSystem()
                    +MovementSystem()
                    +AnimationTickSystem()
                    +AnimationSystem()
                    +RenderSystem()
                }
            }

            spawn {
                val bounds = Rect(-400f, -300f, 400f, 300f)

                entities(50) {
                    val velX = (-40f..40f).random()
                    val velY = (-40f..40f).random()
                    val mass = 1f + Random.nextFloat()
                    val color = Color.random()

                    // 创建随机移动和碰撞的实体
                    +Transform(bounds.randomOffset())
                    +RigidBody(Velocity(velX, velY), mass = mass)
                    +Hitbox(Rect(-20f, -20f, 20f, 20f))
                    +Renderable(CircleVisual(40f, color), zIndex = 1)
                }

                // 创建一个静态墙体来验证分离逻辑 (mass = 0f)
                val e = entity {
                    +Transform()
                    +RigidBody(mass = 0f)
                    +Hitbox(Rect(-50f, -50f, 50f, 50f))
                    +SpriteAnimation("run")
                    +Renderable(
                        visual = SpriteVisual(assets[GameAssets.Atlas.Walk], "frame_0_0", size = Size(100f, 100f)),
                        zIndex = 1,
                    )
                }

                entity {
                    +Transform()
                    +WorldBounds(bounds)
                    +CameraTarget(e)
                    +Camera(isMain = true, bounds = bounds)
                }
            }
        }

        onCreate {
            assets.load(GameAssets.Atlas.Walk)

            RenderSystem.isDebugging = true
        }

        val fpsCalculator = FpsCalculator()

        onRender { fpsCalculator.advanceFrame() }

        onForegroundUI {
            Text("FPS: ${fpsCalculator.fps}")
        }
    }
}