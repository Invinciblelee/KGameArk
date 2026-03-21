package com.example.kgame.games.weaver

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgame.ecs.Component
import com.kgame.ecs.ComponentType
import com.kgame.ecs.EntityTag
import com.kgame.ecs.IntervalSystem
import com.kgame.ecs.World.Companion.inject
import com.kgame.engine.core.KSimpleGame
import com.kgame.engine.graphics.material.Material
import com.kgame.engine.graphics.material.MaterialEffect
import com.kgame.engine.input.InputManager
import com.kgame.engine.math.random
import com.kgame.engine.ui.Rectangle
import com.kgame.plugins.components.Camera
import com.kgame.plugins.components.Renderable
import com.kgame.plugins.components.Transform
import com.kgame.plugins.components.distanceTo
import com.kgame.plugins.services.CameraService
import com.kgame.plugins.services.particles.ParticleContext
import com.kgame.plugins.services.particles.ParticleNodeScope
import com.kgame.plugins.services.particles.ParticleService
import com.kgame.plugins.visuals.material.MaterialVisual
import org.intellij.lang.annotations.Language
import kotlin.math.sin

// --- 1. Components & State ---

/** Tag for all resonant nodes */
private data object AuraNodeTag : EntityTag()

/** Internal state of a single Aura Node, managing its harmonic properties */
internal class AuraNode(
    val baseColor: Color = Color.random(),
    var frequency: Float = (0.5f..2.0f).random(),
    var energy: Float = 1.0f,
    var harmony: Int = 0
) : Component<AuraNode> {
    override fun type() = AuraNode
    companion object : ComponentType<AuraNode>()
}

/** UI-facing state */
class WeaverState {
    var nodeCount by mutableIntStateOf(0)
}

// --- 2. Shaders & Visuals ---

/** Material for the core of an Aura Node, creating a soft, pulsing light */
internal class AuraCoreMaterial(private val node: AuraNode) : Material {
    @Language("AGSL")
    override val sksl: String = """
        uniform float uTime;
        uniform vec4 uColor;
        uniform float uFrequency;
        uniform float uEnergy;
        
        vec4 main(vec2 uv) {
            float d = length(uv - 0.5) * 2.0;
            // Harmonic pulse based on node's unique frequency
            float pulse = sin(uTime * uFrequency) * 0.4 + 0.6;
            float core = 1.0 - smoothstep(0.0, 0.7 * pulse, d);
            float glow = exp(-d * 4.0) * pulse * uEnergy;
            vec3 col = uColor.rgb * (core + glow);
            return vec4(col, (core + glow) * 0.6);
        }
    """.trimIndent()

    override fun MaterialEffect.onSetup() {
        uniform("uColor", node.baseColor)
    }

    override fun MaterialEffect.onUpdate() {
        uniform("uTime", elapsedTime)
        uniform("uFrequency", node.frequency)
        uniform("uEnergy", node.energy)
    }
}

/** Material for the resonance wave when nodes harmonize */
class HarmonyWaveMaterial(val baseColor: Color, val context: ParticleContext) : Material {
    @Language("AGSL")
    override val sksl: String = """
        uniform float uProgress;
        uniform vec4 uColor;
        vec4 main(vec2 uv) {
            float d = length(uv - 0.5) * 2.0;
            // Dual-ring expansion effect
            float ring1 = smoothstep(uProgress - 0.1, uProgress, d) - smoothstep(uProgress, uProgress + 0.05, d);
            float ring2 = smoothstep(uProgress - 0.25, uProgress - 0.1, d) - smoothstep(uProgress - 0.1, uProgress - 0.05, d);
            float alpha = (ring1 + ring2 * 0.4) * pow(1.0 - uProgress, 2.5);
            return vec4(uColor.rgb * alpha, alpha);
        }
    """.trimIndent()
    override fun MaterialEffect.onSetup() { uniform("uColor", baseColor) }
    override fun MaterialEffect.onUpdate() { uniform("uProgress", context.progress) }
}

// --- 3. Systems ---

/** Conducts the harmonic interactions between nodes */
private class AuraWeaverSystem(
    private val state: WeaverState = inject(),
    private val input: InputManager = inject(),
    private val camera: CameraService = inject(),
    private val particle: ParticleService = inject()
) : IntervalSystem() {

    private val nodes = world.family { all(AuraNodeTag, AuraNode, Transform) }

    override fun onTick(deltaTime: Float) {
        // Conduct birth: On tap, weave a new node into the orchestra
        if (input.isPointerJustPressed()) {
            val worldPos = camera.transformer.virtualToWorld(input.getPointerPosition())
            spawnNode(worldPos)
        }

        // Conduct Harmony: Nearby nodes influence each other's resonance
        nodes.forEach { entity1 ->
            val node1 = entity1[AuraNode]
            val trans1 = entity1[Transform]
            var currentHarmony = 0

            nodes.forEach { entity2 ->
                if (entity1 == entity2) return@forEach

                val node2 = entity2[AuraNode]
                val trans2 = entity2[Transform]
                val dist = trans1.distanceTo(trans2.position)

                if (dist < 180f) {
                    currentHarmony++
                    // Convergence: Frequencies slowly drift towards their neighbors' average
                    node1.frequency += (node2.frequency - node1.frequency) * deltaTime * 0.5f
                }
            }
            
            // Visual surge when new connections are formed
            if (currentHarmony > node1.harmony) {
                particle.emit { harmonyEffect(trans1.position, node1.baseColor) }
            }
            
            // Energy scaling based on harmony density
            node1.energy = 1.0f + currentHarmony * 0.4f
            node1.harmony = currentHarmony
        }

        state.nodeCount = nodes.entitySize
    }

    private fun spawnNode(pos: Offset) {
        if (nodes.entitySize >= 40) {
            // Echoes of the past: Remove the oldest node to maintain balance
            nodes.firstOrNull()?.remove()
        }
        world.entity {
            val node = AuraNode()
            +AuraNodeTag
            +node
            +Transform(pos)
            +Renderable(MaterialVisual(AuraCoreMaterial(node), Size(120f, 120f)))
        }
    }

    private fun harmonyEffect(pos: Offset, color: Color) {
        particle.emit {
            layer("harmonyBurst", pos) {
                config { count = 1; duration = 1.2f; material = HarmonyWaveMaterial(color, context) }
                size = scalar(350f)
            }
        }
    }
}

// --- 4. UI Entry ---

@Composable
fun AuraWeaverGame() {
    val state = remember { WeaverState() }

    KSimpleGame(virtualSize = Size(800f, 600f)) {
        onWorld {
            useDefaultSystems()
            configure {
                injectables { +state }
                systems { +AuraWeaverSystem() }
            }
            spawn {
                entity {
                    +Transform()
                    +Camera(isMain = true)
                }
            }
        }

        // The background is a dark, meditative void
        onBackgroundUI {
            Rectangle(color = Color(0xFF020105), modifier = Modifier.fillMaxSize())
        }

        onForegroundUI {
            Box(Modifier.fillMaxSize().padding(40.dp)) {
                // Minimalist HUD to stay in the flow
                Column(Modifier.align(Alignment.TopStart)) {
                    Text("AURA WEAVER", color = Color.White.copy(alpha = 0.3f), fontSize = 14.sp, fontWeight = FontWeight.ExtraLight)
                    Text("${state.nodeCount} RESONANT NODES", color = Color.White.copy(alpha = 0.8f), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                }
                
                Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "TAP TO PLANT HARMONIC NODES",
                        color = Color.White.copy(alpha = 0.2f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Light
                    )
                    Text(
                        "OBSERVE THE RESONANCE",
                        color = Color.White.copy(alpha = 0.1f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraLight
                    )
                }
            }
        }
    }
}
