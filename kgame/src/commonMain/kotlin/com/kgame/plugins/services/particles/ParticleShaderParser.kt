package com.kgame.plugins.services.particles

import com.kgame.engine.graphics.shader.Shader
import com.kgame.engine.graphics.shader.ShaderEffect

object ParticleShaderParser : ParticleParser<Shader> {

    override fun translate(scope: ParticleNodeScope): Shader {
        val resolver = GpuNodeResolver()
        val layers = scope.layers

        val skslCode = buildString {
            // --- Global Uniforms ---
            appendLine("uniform float uTime;")
            appendLine("uniform vec2 uResolution;")

            // Dynamic layer durations from DSL
            layers.forEachIndexed { i, _ ->
                appendLine("uniform float uLayerDuration$i;")
            }

            // --- Vertex Attributes (Varying) ---
            appendLine("varying vec2 pPosition;")
            appendLine("varying vec2 pVelocity;")
            appendLine("varying float pLayerIndex;")
            appendLine("varying float i;")
            appendLine("varying vec4 pColor;") // Initial color from CPU

            // Helper for random-like variations if needed
            appendLine("float iHash(int id) { return fract(sin(float(id) * 43758.5453)); }")

            // --- Logic Resolver ---
            // Returns: x=size, y=red, z=green, w=blue
            appendLine("vec4 computeLayer(int lIdx, int pIdx, out float outAlpha) {")
            appendLine("    float size = 0.0;")
            appendLine("    float alpha = 0.0;")
            appendLine("    vec3 rgb = vec3(0.0);")
            appendLine("    outAlpha = 0.0;")

            layers.forEachIndexed { idx, layer ->
                appendLine("    if (lIdx == $idx) {")
                appendLine("        float layerDuration = uLayerDuration$idx;")
                appendLine("        float progress = clamp(uTime / layerDuration, 0.0, 1.0);")
                appendLine("        float fadeOut = 1.0 - smoothstep(0.9, 1.0, progress);")

                // Resolve DSL nodes into SkSL expressions
                appendLine("        size = ${resolver.resolve(layer.size)};")
                appendLine("        alpha = ${resolver.resolve(layer.alpha)} * fadeOut;")

                // Resolve color node - Resolver must convert ParticleNode.Color to vec4/vec3
                appendLine("        vec4 dynamicColor = ${resolver.resolve(layer.color)};")
                appendLine("        rgb = dynamicColor.rgb;")
                appendLine("        outAlpha = alpha;")
                appendLine("    }")
            }
            appendLine("    return vec4(size, rgb);")
            appendLine("}")

            // --- Fragment Entry Point ---
            appendLine("""
                vec4 main(vec2 fragCoord) {
                    float finalAlpha;
                    // Fetch size and dynamic RGB
                    vec4 data = computeLayer(int(pLayerIndex), int(i), finalAlpha);
                    
                    float finalSize = data.x;
                    vec3 finalRGB = data.yzw;
                    
                    // Optimization: Discard pixels for dead or invisible particles
                    if (finalAlpha <= 0.0) discard;
                    
                    // Physics: p_t = p0 + v0 * t (consistent with VertexEffect)
                    // Note: Gravity/Friction are currently handled CPU-side during emission for GPU path,
                    // or can be added here as: pPosition + pVelocity * uTime + 0.5 * gravity * uTime * uTime
                    vec2 currentPos = pPosition + pVelocity * uTime;
                    
                    // Distance-based circle rendering
                    float dist = length(fragCoord - currentPos);
                    
                    // Antialiased mask for the particle shape
                    float mask = smoothstep(finalSize, finalSize - 1.0, dist);
                    
                    // Combine dynamic RGB with initial vertex alpha and resolved node alpha
                    return vec4(finalRGB, mask * finalAlpha * pColor.a);
                }
            """.trimIndent())
        }

        return object : Shader {
            override val sksl: String = skslCode

            /**
             * Aggregated update method.
             * Encapsulates all uniform synchronization logic.
             */
            override fun ShaderEffect.applyUniforms() {
                // Synchronize all layer durations to their respective 'u' uniforms
                layers.forEachIndexed { index, layer ->
                    uniform("uLayerDuration$index", layer.duration)
                }
            }
        }
    }
}

/**
 * Transpiles the ParticleNode tree into a Shader-compatible string.
 */
private class GpuNodeResolver {
    /**
     * Recursively builds the math expression as a String for the GPU.
     */
    fun resolve(node: ParticleNode): String = when (node) {
        is ParticleNode.Scalar -> "${node.value}"

        is ParticleNode.Vector2 -> "vec2(${node.x}, ${node.y})"

        is ParticleNode.RandomRange -> {
            // iHash is a pseudo-random function implemented in the Shader source
            "mix(${node.min}, ${node.max}, pow(iHash(i), ${node.exp}))"
        }

        is ParticleNode.IndexMod -> {
            // Standard GLSL/SkSL ternary operator
            "(mod(float(i), ${node.divisor}.0) == 0.0 ? ${resolve(node.onTrue)} : ${resolve(node.onFalse)})"
        }

        is ParticleNode.Add -> "(${resolve(node.left)} + ${resolve(node.right)})"

        is ParticleNode.Multiply -> "(${resolve(node.left)} * ${resolve(node.right)})"

        is ParticleNode.Color -> {
            val argb = node.argb
            val a = ((argb shr 24) and 0xFF) / 255f
            val r = ((argb shr 16) and 0xFF) / 255f
            val g = ((argb shr 8) and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f
            // Formats as vec4(r, g, b, a) for SkSL/GLSL
            "vec4($r, $g, $b, $a)"
        }

        // --- Contextual Mappings ---
        ParticleNode.Index -> "float(i)"

        // uTime represents the elapsedTime passed to the shader
        ParticleNode.Time -> "uTime"

        ParticleNode.Progress -> "progress"
    }
}