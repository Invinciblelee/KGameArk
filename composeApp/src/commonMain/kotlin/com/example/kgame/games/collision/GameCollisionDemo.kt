package com.example.kgame.games.collision

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kgame.games.GameAssets
import com.kgame.ecs.Entity
import com.kgame.ecs.IteratingSystem
import com.kgame.ecs.World.Companion.family
import com.kgame.ecs.World.Companion.inject
import com.kgame.engine.core.GameEnvironment
import com.kgame.engine.core.KSimpleGame
import com.kgame.engine.math.random
import com.kgame.engine.math.randomOffset
import com.kgame.engine.utils.FpsCalculator
import com.kgame.plugins.components.Camera
import com.kgame.plugins.components.CameraTarget
import com.kgame.plugins.components.CircleVisual
import com.kgame.plugins.components.Renderable
import com.kgame.plugins.components.RigidBody
import com.kgame.plugins.components.ScaleAnimation
import com.kgame.plugins.components.Spring
import com.kgame.plugins.components.SpriteVisual
import com.kgame.plugins.components.SpriteAnimation
import com.kgame.plugins.components.Transform
import com.kgame.plugins.components.WorldBounds
import com.kgame.plugins.services.AnimationService
import com.kgame.plugins.services.CameraService
import com.kgame.plugins.systems.AnimationSystem
import com.kgame.plugins.systems.CollisionSystem
import com.kgame.plugins.systems.RenderSystem
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
fun GameCollisionDemo(environment: GameEnvironment) {
    KSimpleGame(
        environment = environment,
        modifier = Modifier.fillMaxSize(),
    ) {
        val anim = ScaleAnimation(
            from = 0f,
            to = 1f,
            spec = Spring(
                stiffness = 80f,
                damping = 15f,
            )
        )

        val world = world(configuration = {
            systems {
                +CollisionSystem()
                +MovementSystem()
                +AnimationSystem()
                +RenderSystem()
            }
        }) {
            val bounds = Rect(-400f, -300f, 400f, 300f)

            entities(50) {
                val velX = (-40f..40f).random()
                val velY = (-40f..40f).random()
                val mass = 1f + Random.nextFloat()
                val color = Color.random()

                // 创建随机移动和碰撞的实体
                +Transform(bounds.randomOffset())
                +RigidBody(Offset(velX, velY), mass = mass)
                +Renderable(CircleVisual(color, 40f), zIndex = 1)
            }

            // 创建一个静态墙体来验证分离逻辑 (mass = 0f)
            val e = entity {
                +Transform()
                +RigidBody(Offset.Zero, mass = 0f)
                +anim
                +SpriteAnimation("run")
                +Renderable(
                    visual = SpriteVisual(assets[GameAssets.Atlas.Walk], "frame_0_0"),
                    zIndex = 1
                )
            }

            entity {
                +Transform()
                +WorldBounds(bounds)
                +CameraTarget(e)
                +Camera(isMain = true, bounds = bounds)
            }
        }

        resources {
            +GameAssets.Atlas.Walk
        }

        val fpsCalculator = FpsCalculator()

        onRender { fpsCalculator.advanceFrame() }

        onForegroundUI {
            Text("FPS: ${fpsCalculator.fps}")

            Button(
                onClick = {
                    world.get<AnimationService>().play(anim)
                },
                modifier = Modifier.padding(top = 100.dp)
            ) {
                Text("Test")
            }
        }
    }
}