@file:OptIn(ExperimentalTime::class)

package com.example.kgame.games.aircraftwar

import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import com.example.kgame.games.GameAssets
import com.kgame.ecs.Component
import com.kgame.ecs.ComponentType
import com.kgame.ecs.Entity
import com.kgame.ecs.Fixed
import com.kgame.ecs.IntervalSystem
import com.kgame.ecs.IteratingSystem
import com.kgame.ecs.SystemPriority
import com.kgame.ecs.World.Companion.family
import com.kgame.ecs.World.Companion.inject
import com.kgame.engine.asset.AssetsManager
import com.kgame.engine.audio.AudioManager
import com.kgame.engine.core.KGame
import com.kgame.engine.core.rememberGameSceneStack
import com.kgame.engine.geometry.expandFromBottom
import com.kgame.engine.geometry.expandFromCenter
import com.kgame.engine.graphics.material.Material
import com.kgame.engine.graphics.material.MaterialEffect
import com.kgame.engine.input.InputManager
import com.kgame.engine.math.random
import com.kgame.engine.ui.GameJoypad
import com.kgame.engine.ui.applyJoypad
import com.kgame.engine.utils.FpsCalculator
import com.kgame.plugins.components.AlphaAnimation
import com.kgame.plugins.components.Axis
import com.kgame.plugins.components.Boundary
import com.kgame.plugins.components.BoundaryStrategy
import com.kgame.plugins.components.Camera
import com.kgame.plugins.components.CameraShake
import com.kgame.plugins.components.CameraTarget
import com.kgame.plugins.components.CharacterStats
import com.kgame.plugins.components.CleanupTag
import com.kgame.plugins.components.EnemyBulletTag
import com.kgame.plugins.components.EnemyTag
import com.kgame.plugins.components.InfiniteRepeatable
import com.kgame.plugins.components.PlayerBulletTag
import com.kgame.plugins.components.PlayerTag
import com.kgame.plugins.components.Renderable
import com.kgame.plugins.components.RigidBody
import com.kgame.plugins.components.Scroller
import com.kgame.plugins.components.SpriteAnimation
import com.kgame.plugins.components.Transform
import com.kgame.plugins.components.WorldBounds
import com.kgame.plugins.components.applyKinematicMovement
import com.kgame.plugins.services.AnimationService
import com.kgame.plugins.services.CameraService
import com.kgame.plugins.services.particles.ParticleContext
import com.kgame.plugins.services.particles.ParticleNodeScope
import com.kgame.plugins.services.particles.ParticleService
import com.kgame.plugins.services.play
import com.kgame.plugins.systems.PhysicsSystem
import com.kgame.plugins.systems.SystemPriorityAnchors
import com.kgame.plugins.visuals.images.ImageVisual
import com.kgame.plugins.visuals.images.SpriteVisual
import org.intellij.lang.annotations.Language
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.ExperimentalTime

/**
 * @param baseColor The theme color of the plasma.
 */
class PlasmaFireMaterial(val baseColor: Color, val context: ParticleContext) : Material {

    @Language("AGSL")
    override val sksl: String = """
        uniform float uTime;
        uniform float uProgress;
        uniform vec4 uColor;
        
        vec4 main(vec2 uv) {
            // Transform UV to -1.0 -> 1.0 space
            vec2 st = uv * 2.0 - 1.0;
            float d = length(st);
            
            // 1. Noise calculation for dynamic shape
            float noise = sin(st.x * 10.0 + uTime * 5.0) * cos(st.y * 10.0 - uTime * 3.0);
            
            // 2. Improved strength calculation (to prevent shrinking)
            // Using a higher power curve to keep the center thick and edges crisp
            float strength = 1.0 - pow(clamp(d + noise * 0.05, 0.0, 1.0), 3.0);
            
            // 3. Life cycle progress
            float lifeFade = 1.0 - smoothstep(0.7, 1.0, uProgress); 
            
            // 4. Color Logic
            // Inner glowing white-ish core based on baseColor
            vec3 coreColor = mix(uColor.rgb, vec3(1.0, 1.0, 0.9), 0.6);
            // Blend base color to core color based on strength
            vec3 finalColor = mix(uColor.rgb, coreColor, strength);
            
            // 5. Visual dynamic effects
            float flash = sin(uTime * 15.0) * 0.1 + 0.9;
            float edgeFade = smoothstep(1.0, 0.8, d); // Soften the quad edges
            
            // 6. Alpha assembly
            // Ensure finalAlpha includes uColor.a from the DSL/Material constructor
            float finalAlpha = strength * lifeFade * edgeFade * uColor.a;
            
            // 7. Output with Premultiplied Alpha for better glowing effect
            return vec4(finalColor * flash * finalAlpha, finalAlpha);
        }
    """.trimIndent()

    override fun MaterialEffect.onSetup() {
        uniform(Material.COLOR, baseColor)
    }

    override fun MaterialEffect.onUpdate() {
        uniform(Material.PROGRESS, context.progress)
    }

}

fun ParticleNodeScope.explosion(center: Offset) {
    // 1. Explosion Core: The hot, dense center
    layer("explosion_core", center) {
        config {
            count = 800
            duration = 1.0f
            material = PlasmaFireMaterial(
                baseColor = Color(red = 1.0f, green = 0.5f, blue = 0.0f),
                context = context
            )
        }

        val angle = math.random(0f, 360f)
        val rad = math.toRadians(angle)

        // Distribution: Use pow(random, 2.0) to cluster more particles near the center
        val speed = math.pow(math.random(0.0f, 1.0f), 2.0f) * 180.0f

        // Physics: Exponential decay to simulate air resistance (Fluid Drag)
        // Position = origin + velocity * (1.0 - exp(-k * t)) / k
        val dragK = 3.0f
        val resistance = (1.0f - math.exp(env.time * -dragK)) / dragK

        position = vec2(
            math.cos(rad) * speed * resistance,
            math.sin(rad) * speed * resistance + (40f * env.time * env.time) // Gravity
        )

        // Visuals: Glowing particles that shrink over time
        size = math.random(2.0f, 5.0f) * (1.0f - env.progress.smoothstep(0.6f, 1.0f))

        // Color: Transitions from White-Hot to Orange
//        val alpha = math.smoothstep(0.0f, 0.1f, env.progress) * (1.0f - math.smoothstep(0.7f, 1.0f, env.progress))
//        val hotColor = color(1.0f, 1.0f, 1.0f, alpha = alpha)
//        val fireColor = color(1.0f, 0.8f, 0.2f, alpha = alpha)
//        color = math.mix(hotColor, fireColor, env.progress)
    }

    // 2. Explosion Blast: High-velocity sparks and debris
    layer("explosion_blast", center) {
        config {
            count = 2500
            duration = 1.5f
            material = PlasmaFireMaterial(
                baseColor = Color(red = 1.0f, green = 0.95f, blue = 0.8f),
                context = context
            )
        }

        val angle = math.random(0f, 360f)
        val rad = math.toRadians(angle)

        // Distribution: Sqrt makes particles favor the outer edge
        val speed = math.sqrt(math.random(0.0f, 1.0f)) * 350.0f

        // Physics: Different drag for lighter debris
        val dragK = 1.5f
        val resistance = (1.0f - math.exp(env.time * -dragK)) / dragK

        position = vec2(
            math.cos(rad) * speed * resistance,
            math.sin(rad) * speed * resistance + (60f * env.time * env.time)
        )

        // Visuals: Elongated "sparks" effect by making size small
        size = math.random(0.5f, 2.5f)

        // Color: Deep red/embers using a select for variety
//        val alpha = (1.0f - env.progress) * 0.9f
//        val ashColor = color(0.2f, 0.1f, 0.05f, alpha = alpha)
//        val emberColor = color(1.0f, 0.3f, 0.1f, alpha = alpha)
//        color = select(Ratio(0.4f), emberColor, ashColor)
    }

    // 3. Shockwave: A subtle, fast-expanding ring
    layer("shockwave", center) {
        config {
            count = 100
            duration = 0.4f
            material = PlasmaFireMaterial(
                baseColor = Color(red = 0.85f, green = 0.95f, blue = 1.0f, alpha = 0.6f),
                context = context
            )
        }

        val angle = (env.index / env.count) * 360f
        val rad = math.toRadians(angle)

        // Speed: Very fast expansion with high drag
        val speed = 500.0f
        val expansion = speed * (1.0f - math.exp(env.time * -8.0f))

        position = vec2(
            math.cos(rad) * expansion,
            math.sin(rad) * expansion
        )

        size = 2.0f + env.progress * 10.0f // Expanding ring dots
//
//        val alpha = (1.0f - math.smoothstep(0.0f, 1.0f, env.progress)) * 0.5f
//        color = color(1f, 1f, 1f, alpha = alpha)
    }
}

object RainbowCircleMaterial : Material {

    @Language("AGSL")
    override val sksl: String = """
        uniform float uTime;
        
        vec4 main(vec2 uv) {
            vec2 st = uv * 2.0 - 1.0;
            float d = length(st);

            // 1. 核心光晕逻辑：用指数衰减模拟光的扩散
            // 越靠近中心越亮，边缘通过 power 函数形成柔和的羽化效果
            // 这里的 0.8 可以控制光晕的范围，数值越小，光晕越散
            float glow = pow(max(0.0, 1.0 - d), 2.5); 
            
            // 2. 边缘平滑处理（抗锯齿）
            // 即使是光晕，我们也希望在 UV 边界处彻底归零，防止出现你讨厌的“方框边角”
            float alpha = smoothstep(1.0, 0.5, d);

            // 3. 动态彩虹色（保持你之前的逻辑）
            float hue = fract(uTime * 0.5);
            vec3 color = 0.5 + 0.5 * cos(6.28318 * (hue + vec3(0.0, 0.33, 0.67)));

            // 4. 增加中心亮度（让它看起来更像一个发光的实体）
            // 中心部分叠加上白色或者增强原色
            vec3 finalColor = color + vec3(0.5) * pow(max(0.0, 0.4 - d), 2.0);

            // 5. 最终输出：颜色 * 强度 * 预乘Alpha
            // 注意：glow 和 alpha 的结合是消灭方块边缘的关键
            float finalAlpha = glow * alpha;
            return vec4(finalColor * finalAlpha, finalAlpha);
        }
    """.trimIndent()
}

fun ParticleNodeScope.testSimple(center: Offset) {
    layer("ribbon_trail", center) {
        config {
            count = 800 // 既然追求丝带感，密度可以再高点
            duration = 2.0f
            decayPadding = 0.5f
            material = SmoothSmokeMaterial(Color(0.2f, 0.6f, 1.0f), context)
        }

        val iRatio = env.index / env.count
        val p = env.progress

        // --- 魔法发生在这里 ---

        // 1. 让旋转角度随进度 p 变化，实现整体旋转
        // 2. 加入一个随 index 和 p 共同变化的偏移，实现类似“蛇行”的摆动
        val wave = math.sin(iRatio * 15f + p * 8f) * 0.5f // 随时间流动的波浪
        val angle = iRatio * 12f + p * 4f + wave

        // 3. 半径也可以带一点抖动
        val radius = iRatio * 250f + math.cos(p * 3f) * 20f

        position = vec2(
            math.cos(angle) * radius,
            math.sin(angle) * radius
        )

        // 4. 头粗尾细
        size = (1f - iRatio) * 50f
    }
}

/**
 * 赛博网格材质：通过构造方法接收颜色和时长
 */
class CyberGridMaterial(val baseColor: Color, val context: ParticleContext) : Material {

    @Language("AGSL")
    override val sksl: String = """
        uniform float uTime;
        uniform float uProgress;
        uniform vec4 uColor; // 对应 Material.COLOR
        
        vec4 main(vec2 uv) {
            vec2 st = uv * 2.0 - 1.0;
            // 使用 abs 制造正方形边框感
            float d = max(abs(st.x), abs(st.y)); 
            
            // 1. 扫描线效果：在 Y 轴上循环滚动
            float scanline = sin(uv.y * 40.0 - uTime * 15.0) * 0.5 + 0.5;
            
            // 2. 边缘发光：只保留外圈 20% 的厚度
            float border = smoothstep(0.6, 1.0, d);
            
            // 3. 随机闪烁：增加不稳定能量感
            float flicker = fract(sin(uTime * 30.0)) > 0.4 ? 1.0 : 0.7;
            
            // 4. 色彩合成：基础色 + 扫描线增强
            vec3 color = uColor.rgb * (scanline + 0.6);
            
            // 5. 生命周期透明度淡出
            float lifeFade = 1.0 - smoothstep(0.8, 1.0, uProgress);
            
            float alpha = border * uColor.a * lifeFade;
            return vec4(color * alpha * flicker, alpha);
        }
    """.trimIndent()

    override fun MaterialEffect.onSetup() {
        uniform(Material.COLOR, baseColor)
    }

    override fun MaterialEffect.onUpdate() {
        uniform(Material.PROGRESS, context.progress)
    }

}

/**
 * 赛博核心汇聚特效 DSL
 */
fun ParticleNodeScope.cyberConvergence(center: Offset) {
    // Layer 1: 核心轨道（
    layer("core_orbits", center) {
        config {
            count = 80 // 增加粒子数
            duration = 4.0f
            material = CyberGridMaterial(
                baseColor = Color(red = 0.2f, green = 0.4f, blue = 1.0f, alpha = 0.8f),
                context = context
            )
        }

        // 利用 index 分配到不同的轨道
        val orbitId = env.index % 3f // 分成 3 个圈
        val baseRadius = 80f + orbitId * 40f // 轨道半径分别为 80, 120, 160

        // 旋转逻辑：不同轨道速度不同，且方向交替
        val speed = orbitId.eq(1f).select(1.5f, -1.0f)
        val angle = env.index * (360f / (80f / 3f)) + env.time * 40f * speed
        val rad = math.toRadians(angle)

        // 呼吸效果：让整个轨道群有节奏地扩张
        val pulse = math.sin(env.time * 2f) * 10f
        val r = baseRadius + pulse

        position = vec2(
            math.cos(rad) * r,
            math.sin(rad) * r
        )

        // 粒子尺寸：极小，保持精致感
        size = math.random(3f, 6f)
    }

    // Layer 2: 螺旋汇聚数据流（霓虹青色）
    layer("data_shards", center) {
        config {
            count = 1500
            duration = 2.5f
            material = CyberGridMaterial(
                baseColor = Color(red = 0.0f, green = 1.0f, blue = 0.8f, alpha = 1.0f),
                context = context
            )
        }

        // 初始随机状态
        val baseAngle = random(0f, 360f)
        val startRadius = 450f

        val direction = select(
            condition = env.index % 2f,
            onTrue = 1.0f,
            onFalse = -1.0f
        )

        // 进度与缓动
        val p = env.time / 2.5f
        val easeIn = 1.0f - math.pow(p, 2.5f) // 非线性入场：越近越快
        val currentRadius = startRadius * easeIn

        // 螺旋轨迹逻辑：进度越深，角度偏移越大
        val spiralTurn = math.pow(p, 2.0f) * 150f * direction
        val rad = math.toRadians(baseAngle + spiralTurn)

        position = vec2(
            math.cos(rad) * currentRadius,
            math.sin(rad) * currentRadius
        )

        // 尺寸：在中心压缩时略微变大增加爆发感，随后消失
        size = random(6f, 14f) * (1.0f + math.smoothstep(0.7f, 1.0f, p) * 1.5f)
    }

    // Layer 3: 汇聚触达时的数码火星（纯顶点色，不加 Material）
    layer("digital_glitch", center) {
        config {
            count = 600
            duration = 0.8f
        }

        val p = env.progress
        // 瞬间喷发逻辑：从中心向四周极小范围炸开
        val angle = random(0f, 360f)
        val rad = math.toRadians(angle)
        val speed = random(50f, 200f) * p

        position = vec2(math.cos(rad) * speed, math.sin(rad) * speed)

        // 这里体现顶点色的“五彩斑斓”：随机青色到白色的闪烁
        color = math.mix(
            color(0.0f, 1.0f, 0.9f, 1.0f - p),
            color(1.0f, 1.0f, 1.0f, 1.0f - p),
            random(0f, 1f)
        )

        size = random(2f, 5f)
    }
}

class TornadoEnergyMaterial(val color: Color) : Material {

    @Language("AGSL")
    override val sksl: String = """
        uniform float uTime;
        uniform vec4 uColor;
        
        vec4 main(vec2 uv) {
            // 1. 将 UV 中心化并进行纵向缩放，模拟“拉丝”感
            vec2 st = uv * 2.0 - 1.0;
            st.y *= 0.5; // 纵向拉伸，让圆点变成长条
            
            // 2. 高斯模糊效果的圆心距离
            float d = length(st);
            
            // 3. 核心流光：利用 sin 和噪点制造“内部涌动”的效果
            float wave = sin(uv.y * 10.0 - uTime * 20.0) * 0.5 + 0.5;
            
            // 4. 边缘消隐：让粒子看起来是半透明的能量束
            float alpha = smoothstep(1.0, 0.0, d);
            
            // 5. 颜色增强：边缘暗，中心亮，带一点发光（Glow）
            vec3 finalColor = uColor.rgb * (wave + 0.5);
            
            // 最终输出：中心更亮，且整体透明度随距离衰减
            return vec4(finalColor * alpha, alpha * uColor.a);
        }
    """.trimIndent()

    override fun MaterialEffect.onSetup() {
        uniform(Material.COLOR, color)
    }
}

fun ParticleNodeScope.cyberTornado(center: Offset) {
    layer("tornado_main", center) {
        config {
            count = 3000
            duration = 4.0f // Longer life for better density
            material = TornadoEnergyMaterial(Color(0.2f, 0.7f, 1.0f, 0.8f))
        }

        // 1. Introduce birth offset using index to break synchronicity
        val p = (env.time + env.index * 0.01f) % 4.0f / 4.0f

        // 2. Add randomness to radius and height to fill the volume
        val seed = env.index
        val hJitter = random(-20f, 20f, seed = seed)
        val rJitter = random( 0.8f, 1.2f, seed = seed + 1f)

        // 3. Physical trajectory
        val height = p * 800f + hJitter
        val baseRadius = 15f + math.pow(p, 1.5f) * 250f
        val radius = baseRadius * rJitter

        // 4. Spiral logic with high frequency
        val spiralSpeed = 120f + p * 300f
        val angle = env.index * 137.5f + env.time * spiralSpeed
        val rad = math.toRadians(angle)

        position = vec2(
            math.cos(rad) * radius,
            -height
        )

        size = 2f + (1.0f - p) * 6f
    }
}

// In ParticleNodes.kt or a new file like Materials.kt
class SmoothSmokeMaterial(val baseColor: Color, val context: ParticleContext) : Material {

    @Language("AGSL")
    override val sksl: String = """
        uniform float uProgress; // 核心进度 [0.0, 1.0]
        uniform vec4 uColor;

        float hash(vec2 p) {
            return fract(sin(dot(p, vec2(12.71, 311.7))) * 43758.5453);
        }

        float noise(vec2 p) {
            vec2 i = floor(p); vec2 f = fract(p);
            vec2 u = f * f * (3.0 - 2.0 * f);
            return mix(mix(hash(i), hash(i + vec2(1,0)), u.x),
                       mix(hash(i + vec2(0,1)), hash(i + vec2(1,1)), u.x), u.y);
        }

        vec4 main(vec2 uv) {
            vec2 st = uv * 2.0 - 1.0;
            float d = length(st);

            // 1. 进度截断（处理可能的 Overshot）
            float p = clamp(uProgress, 0.0, 1.0);
            
            // 2. 提前归零逻辑 (0.95 处彻底消失)
            // 这样不管插值器怎么回弹，最后 5% 的物理生命全是透明的
            float lifeFade = clamp(1.0 - (p / 0.95), 0.0, 1.0);
            lifeFade = lifeFade * lifeFade * lifeFade; // 三次幂更丝滑

            // 3. 用进度模拟时间滚动
            // p * 10.0 模拟时间流逝，让噪声在生命周期内匀速滚动
            float n = noise(st * 1.5 - p * 5.0);
            n = mix(n, 0.5, p * 0.4); // 随进度增加，噪声对比度降低，减少闪烁

            // 4. 圆形遮罩
            float alphaMask = smoothstep(1.0, 0.4, d);
            
            // 5. 物理边界切断
            // 解决你之前 64x64 尺寸带来的边缘残留问题
            if (d > 0.99) alphaMask = 0.0;

            float finalAlpha = n * alphaMask * lifeFade * uColor.a;

            // 终极断路器：直接跟进度挂钩
            if (p >= 0.96 || finalAlpha < 0.001) {
                return vec4(0.0);
            }

            vec3 color = uColor.rgb * (n + 0.6);
            return vec4(color * finalAlpha, finalAlpha);
        }
    """.trimIndent()

    override fun MaterialEffect.onSetup() {
        uniform(Material.COLOR, baseColor)
    }

    override fun MaterialEffect.onUpdate() {
        // 只传进度，不传时间
        uniform("uProgress", context.progress)
    }
}

fun ParticleNodeScope.risingSmoke(center: Offset) {
    layer("smoke_puff", center) {
        val life = 3.0f // 缩短生命周期，让它散得快、消失得也快
        config {
            count = 200 // 增加密度，减小单体体积
            // 颜色一定要极其淡，这里 alpha 给到 0.02
            material = SmoothSmokeMaterial(Color(0.8f, 0.8f, 0.8f), context)
        }

        val p = ((env.time + env.index * 0.05f) % life) / life

        // --- 核心修正：局部扩散模型 ---
        val angle = env.index * 137.5f

        // 模拟空气阻力：p 越大，单位时间位移越小 (使用 sqrt 让它初速度快，后期缓)
        val resistanceP = math.sqrt(p)
        val maxDist = 150f // 严格限制扩散半径在 150 像素以内，不再铺满屏幕
        val dist = resistanceP * maxDist

        position = vec2(
            math.cos(angle) * dist,
            math.sin(angle) * dist - (p * 50f) // 极轻微的上升
        )

        // 尺寸也大幅压缩：初始小，最终也不要超过 150
        size = 30f + p * 120f
    }
}


private data class WeaponComponent(
    val cooldown: Float = 0.3f,
    var timeUntilNextShot: Float = 0f
) : Component<WeaponComponent> {
    override fun type() = WeaponComponent
    companion object : ComponentType<WeaponComponent>()
}

// --- 3. 游戏系统 (Game Systems) ---

private class AircraftControlSystem(
    val cameraService: CameraService = inject(),
    val input: InputManager = inject(),
    val assets: AssetsManager = inject(),
    priority: SystemPriority
) : IteratingSystem(
    family = family { all(PlayerTag, Transform, Renderable, WeaponComponent) },
    priority = priority
) {
    val texture = assets[GameAssets.Atlas.Texture]

    override fun onTickEntity(entity: Entity, deltaTime: Float) {
        val transform = entity[Transform]
        val renderable = entity[Renderable]
        val weapon = entity[WeaponComponent]

        // 移动控制 (WASD/Arrows)
        var deltaX = 0f; var deltaY = 0f
        if (input.isKeyDown(Key.W) || input.isKeyDown(Key.DirectionUp)) deltaY -= 1f
        if (input.isKeyDown(Key.S) || input.isKeyDown(Key.DirectionDown)) deltaY += 1f
        if (input.isKeyDown(Key.A) || input.isKeyDown(Key.DirectionLeft)) deltaX -= 1f
        if (input.isKeyDown(Key.D) || input.isKeyDown(Key.DirectionRight)) deltaX += 1f

        transform.applyKinematicMovement(deltaTime = deltaTime, rawDeltaX = deltaX, rawDeltaY = deltaY, speed = 200f)
        transform.position = cameraService.transformer.clampToBounds(transform.position)

        // 射击控制 (Spacebar)
        weapon.timeUntilNextShot -= deltaTime
        if (input.isKeyDown(Key.Spacebar) && weapon.timeUntilNextShot <= 0f) {
            weapon.timeUntilNextShot = weapon.cooldown

            // 创建子弹实体
            world.entity {
                +Transform(
                    position = transform.position + Offset(0f, -renderable.size.height / 2f),
                )
                +RigidBody(velocity = Offset(0f, -600f), drag = 0f)
                +Boundary(strategy = BoundaryStrategy.Cleanup)
                +PlayerBulletTag(damage = 10f)
                +Renderable(SpriteVisual(atlas = texture, name = "stormplane_bullet_hero.png"), zIndex = -1)
            }
        }
    }
}

private class EnemyWeaponSystem(
    assets: AssetsManager = inject(),
    priority: SystemPriority
) : IteratingSystem(
    family = family { all(EnemyTag, Transform, WeaponComponent) },
    priority = priority
) {
    val texture = assets[GameAssets.Atlas.Texture]

    override fun onTickEntity(entity: Entity, deltaTime: Float) {
        val weapon = entity[WeaponComponent]
        weapon.timeUntilNextShot -= deltaTime
        if (weapon.timeUntilNextShot <= 0f) {
            weapon.timeUntilNextShot = weapon.cooldown

            val transform = entity[Transform]
            world.entity {
                +Transform(
                    position = transform.position,
                )
                +RigidBody(velocity = Offset(0f, 300f), drag = 0f) // 向下
                +Boundary(strategy = BoundaryStrategy.Cleanup)
                +EnemyBulletTag(damage = 15f)
                +Renderable(SpriteVisual(atlas = texture, name = "stormplane_bullet_elite.png"), zIndex = -1)
            }
        }
    }
}

private class EnemySpawnSystem(
    assets: AssetsManager = inject(),
    priority: SystemPriority,
) : IntervalSystem(interval = Fixed(0.5f), priority = priority) { // 每 0.5 秒生成一波敌人

    val texture = assets[GameAssets.Atlas.Texture]

    val worldBounds = Rect(-400f, -400f, 400f, 300f)

    override fun onTick(deltaTime: Float) {
        val spawnXMin = worldBounds.left
        val spawnXMax = worldBounds.right

        // Y轴的生成位置固定在世界顶边之外一点点，给敌人一个进场的空间
        // worldBounds.top 在我们的中心坐标系里是负值 (e.g., -300f)
        val spawnY = worldBounds.top // e.g., at y = -300 - 50 = -350

        // 在屏幕上方随机生成 1-3 个敌人
        repeat(Random.nextInt(1, 4)) {
            world.entity {
                +Transform(
                    position = Offset(
                        x = Random.nextFloat() * (spawnXMax - spawnXMin) + spawnXMin,
                        y = spawnY
                    ),
                )
                +RigidBody(velocity = Offset((-100f..100f).random(), 150f), drag = 0f) // 缓慢向下
                +CharacterStats(maxHp = 20f)
                +WorldBounds(worldBounds)
                +Boundary(strategy = BoundaryStrategy.Cleanup)
                +WeaponComponent(cooldown = 0.5f)
                +EnemyTag
                val name = if (Random.nextFloat() > 0.8) "stormplane_mob.png" else "stormplane_elite.png"
                +Renderable(SpriteVisual(atlas = texture, name = name, size = Size(60f, 60f)))
            }
        }
    }
}

private class AircraftCollisionSystem(
    val cameraService: CameraService = inject(),
    val animationService: AnimationService = inject(),
    val particleService: ParticleService = inject(),
    audio: AudioManager = inject(),
    assets: AssetsManager = inject(),
    priority: SystemPriority
) : IntervalSystem(priority = priority) {
    private val playerBulletFamily = family { all(PlayerBulletTag, Transform); none(EnemyTag) }
    private val enemyFamily = family { all(EnemyTag, Transform, CharacterStats) }
    private val playerFamily = family { all(PlayerTag, Transform, CharacterStats) }

    private val texture = assets[GameAssets.Atlas.Texture]

    override fun onTick(deltaTime: Float) {
        // --- 1. 玩家子弹 vs 敌人 ---
        playerBulletFamily.forEach { bullet ->
            enemyFamily.forEach { enemy ->
                val bPos = bullet[Transform].position
                val ePos = enemy[Transform].position
                val eRadius = enemy[Renderable].size.width / 2f

                // 简单的圆形碰撞检测 (使用距离平方优化)
                if ((bPos - ePos).getDistanceSquared() < (eRadius * eRadius)) {
                    val stats = enemy[CharacterStats]
                    stats.hp -= bullet[PlayerBulletTag].damage

                    if (stats.hp <= 0) {
                        bullet.configure { +CleanupTag }

                        particleService.emit {
                            explosion(ePos)
                        }
//                        world.entity {
//                            +Transform(position = ePos)
//                            +SpriteAnimation(name = "boom", loop = false)
//                            +AutoCleanupTag
//                            +Renderable(
//                                SpriteVisual(
//                                    atlas = texture,
//                                    name = "stormplane_boom_1.png",
//                                    size = Size(60f, 60f)
//                                )
//                            )
//                        }
                    }
                }
            }
        }

        // --- 2. 敌人 vs 玩家 (为了简化，只检查敌机本体) ---
        playerFamily.forEach { player ->
            enemyFamily.forEach { enemy ->
                val pPos = player[Transform].position
                val ePos = enemy[Transform].position
                val eRadius = enemy[Renderable].size.width / 2f
                val pRadius = player[Renderable].size.width / 2f

                if ((pPos - ePos).getDistanceSquared() < (eRadius + pRadius).pow(2)) {
                    val stats = player[CharacterStats]
//                    stats.hp -= 100f // 敌机撞到玩家，直接扣大量血

                    animationService.play(player, "flash")

                    cameraService.director.shake(0.5f)
                    enemy.configure { +CleanupTag }
                }
            }
        }
    }

}


// --- 4. 场景结构 (Scenes) ---

private sealed interface Scenes {
    data object Menu: Scenes
    data object Battle: Scenes
}

@Composable
fun GameAircraftWarDemo() {
    val sceneStack = rememberGameSceneStack<Any>(Scenes.Menu)
    KGame(sceneStack = sceneStack) {
        scene<Scenes.Menu> {
            onStart {
                println("Enter")
            }

            onForegroundUI {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Text("=== 飞机大战 Demo ===", style = MaterialTheme.typography.titleLarge)
                    Button(onClick = { sceneStack.push(Scenes.Battle) }) {
                        Text("开始战斗")
                    }
                }
            }
        }

        scene<Scenes.Battle> {
            world {
                useDefaultSystems()

                configure {
                    systems {
                        +PhysicsSystem(gravity = Offset.Zero)

                        +AircraftControlSystem(priority = SystemPriorityAnchors.Input)

                        +AircraftCollisionSystem(priority = SystemPriorityAnchors.Physics after 1)

                        +EnemySpawnSystem(priority = SystemPriorityAnchors.Logic)

                        +EnemyWeaponSystem(priority = SystemPriorityAnchors.Logic after 1)
                    }
                }

                spawn {
                    val worldBounds = Rect(-400f, -400f, 400f, 300f)

                    entity {
                        val image = assets[GameAssets.Image.Background]
                        +Transform()
                        +Scroller(speed = -120f, axis = Axis.Y)
                        +Renderable(ImageVisual(image), zIndex = -100)
                    }

                    // 1. 玩家实体
                    val player = entity {
                        +Transform()
                        +PlayerTag
                        +CharacterStats(maxHp = 100f)
                        +WeaponComponent(cooldown = 0.2f)
                        +SpriteAnimation(name = "hero")
                        +AlphaAnimation(name = "flash", 1.0f, to = 0.0f, spec = InfiniteRepeatable(repeatMode = RepeatMode.Restart, iterations = 4))
                        +WorldBounds(worldBounds)
                        +Boundary(margin = 0f, strategy = BoundaryStrategy.Clamp)
                        +Renderable(
                            SpriteVisual(
                                atlas = assets[GameAssets.Atlas.Texture],
                                name = "stormplane_hero1.png",
                                size = Size(80f, 80f)
                            )
                        )
                    }

                    entity {
                        +Transform()
                        +WorldBounds(worldBounds)
                        +CameraTarget(player)
                        +CameraShake()
                        +Camera("player", isMain = true, isTracking = false)
                    }
                }
            }

            onCreate {
                load(GameAssets.Image.Background)
                load(GameAssets.Atlas.Texture)
            }

            onUpdate {
                if (input.isKeyJustPressed(Key.Escape)) sceneStack.pop()
            }

            val fpsCalculator = FpsCalculator()

            onRender { fpsCalculator.advanceFrame() }

            onForegroundUI {
                GameJoypad(onValue = input::applyJoypad)


                Text("FPS: ${fpsCalculator.fps}", modifier = Modifier.padding(16.dp))
            }
        }
    }
}