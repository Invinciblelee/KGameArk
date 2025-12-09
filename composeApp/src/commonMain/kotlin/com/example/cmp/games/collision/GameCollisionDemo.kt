package com.example.cmp.games.collision

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.cmp.games.GameAssets
import com.game.ecs.Entity
import com.game.ecs.IteratingSystem
import com.game.ecs.World.Companion.family
import com.game.ecs.World.Companion.inject
import com.game.engine.context.PlatformContext
import com.game.engine.core.KSimpleGame
import com.game.engine.geometry.ViewportTransform
import com.game.engine.geometry.clampInBounds
import com.game.engine.math.random
import com.game.engine.math.randomOffset
import com.game.engine.utils.FpsCalculator
import com.game.plugins.components.AlphaAnimation
import com.game.plugins.components.Circle
import com.game.plugins.components.InfiniteRepeatable
import com.game.plugins.components.Renderable
import com.game.plugins.components.RigidBody
import com.game.plugins.components.ScaleAnimation
import com.game.plugins.components.Spring
import com.game.plugins.components.Sprite
import com.game.plugins.components.SpriteAnimation
import com.game.plugins.components.Transform
import com.game.plugins.components.Tween
import com.game.plugins.components.play
import com.game.plugins.services.CameraService
import com.game.plugins.systems.AnimationSystem
import com.game.plugins.systems.CollisionSystem
import com.game.plugins.systems.RenderSystem
import kotlin.random.Random

private class MovementSystem(
    val cameraService: CameraService = inject(),
    val viewportTransform: ViewportTransform = inject()
) : IteratingSystem(
    family { all(Transform, RigidBody) }
) {

    val worldBounds = Rect(Offset.Zero, viewportTransform.virtualSize)

    override fun onTickEntity(entity: Entity) {
        val t = entity[Transform]
        val r = entity[RigidBody]

        /* 运动（复用基本类型） */
        t.position = t.position.copy(
            x = t.position.x + r.velocity.x * deltaTime,
            y = t.position.y + r.velocity.y * deltaTime
        )

        /* 边界反弹：一行调用你的封装（零临时对象） */
        val clamped = viewportTransform.clampInBounds(
            worldBounds = worldBounds,
            position = t.position
        )

        /* 若被 clamp → 反弹速度 */
        if (clamped.x != t.position.x) r.velocity = r.velocity.copy(x = -r.velocity.x)
        if (clamped.y != t.position.y) r.velocity = r.velocity.copy(y = -r.velocity.y)

        /* 写回最终位置（零临时对象） */
        t.position = clamped
    }
}

private val EaseOutOvershoot = CubicBezierEasing(0.4f, 1.5f, 0.8f, 1.0f)

@Composable
fun GameCollisionDemo(context: PlatformContext) {
    KSimpleGame(
        context = context,
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

        world(configuration = {
            systems {
                +CollisionSystem()
                +MovementSystem()
                +AnimationSystem()
                +RenderSystem()
            }
        }) {
            val entityCount = 50
            val entitySize = Size(40f, 40f)

            val bounds = Rect(0f, 0f, 800f, 600f)

            entities(entityCount) {
                val velX = (-40f..40f).random()
                val velY = (-40f..40f).random()
                val mass = 1f + Random.nextFloat()
                val color = Color.random()

                // 创建随机移动和碰撞的实体
                +Transform(bounds.randomOffset(), entitySize)
                +RigidBody(Offset(velX, velY), mass = mass)
                +ScaleAnimation(
                    from = 0f,
                    to = 1f,
                    spec = InfiniteRepeatable(
                        Tween(1f, easing = EaseOutOvershoot),
                        RepeatMode.Reverse
                    )
                )
                +AlphaAnimation(
                    from = 0f,
                    to = 1f,
                    spec = InfiniteRepeatable(
                        Tween(2f, easing = LinearEasing),
                        RepeatMode.Reverse
                    )
                )
                +Renderable(Circle(color), zIndex = 1)
            }

            // 创建一个静态墙体来验证分离逻辑 (mass = 0f)
            entity {
                +Transform(Offset(400f, 300f), Size(100f, 100f))
                +RigidBody(Offset.Zero, mass = 0f)
                +anim
                +SpriteAnimation("run")
                +Renderable(
                    visual = Sprite(assets[GameAssets.Atlas.Walk], "frame_0_0"),
                    zIndex = 1
                )
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
                onClick = { anim.play() },
                modifier = Modifier.padding(top = 100.dp)
            ) {
                Text("Test")
            }
        }
    }
}