package com.kgame.plugins.services.particles

import com.kgame.engine.graphics.shader.Shader
import com.kgame.engine.graphics.shader.ShaderEffect

object ParticleShaderParser : ParticleParser<Shader> {

    override fun translate(scope: ParticleNodeScope): Shader {
        val layers = scope.layers
        val layerCount = layers.size

        val skslCode = buildString {
            // --- Global Uniforms ---
            appendLine("uniform float uTime;")

            var index = 0
            while (index < layerCount) {
                appendLine("uniform float uLayerDuration$index;")
                index++
            }

            // --- Varyings ---
            appendLine("varying vec2 position;")
            appendLine("varying vec2 velocity;")
            appendLine("varying float layerIndex;")
            appendLine("varying float i;")
            appendLine("varying vec4 color;")

            // --- Helpers ---
            appendLine("float iHash(float id) { return fract(sin(id * 43758.5453)); }")

            // --- Logic Resolver ---
            appendLine("float resolveLayer(int lIdx, out vec4 outColor, out float outFric, out float outGrav) {")
            appendLine("    float size = 0.0;")
            appendLine("    float duration = 1.0;")
            appendLine("    outColor = vec4(1.0);")
            appendLine("    outFric = 1.0;")
            appendLine("    outGrav = 0.0;")

            index = 0
            while (index < layerCount) {
                val layer = layers[index]
                appendLine("    if (lIdx == $index) {")
                appendLine("        duration = uLayerDuration$index;")
                // progress calculation moved INSIDE resolveLayer
                appendLine("        float progress = clamp(uTime / duration, 0.0, 1.0);")

                // Now GpuNodeResolver can safely use the 'progress' local variable
                appendLine("        size = ${GpuNodeResolver.resolve(layer.size)};")
                appendLine("        outColor = ${GpuNodeResolver.resolve(layer.color)};")
                appendLine("        outColor.a *= ${GpuNodeResolver.resolve(layer.alpha)};")
                appendLine("        outFric = ${GpuNodeResolver.resolve(layer.friction)};")
                appendLine("        outGrav = ${GpuNodeResolver.resolve(layer.gravity)};")
                appendLine("    }")
                index++
            }
            appendLine("    return size;")
            appendLine("}")

            // --- Fragment Entry Point ---
            appendLine("""
                vec4 main(vec2 fragCoord) {
                    vec4 dynamicColor;
                    float fric, grav;
                    
                    // All logic (including progress) is handled here
                    float finalSize = resolveLayer(int(layerIndex), dynamicColor, fric, grav);
                    
                    float t = uTime;
                    vec2 currentPos;
                    if (fric >= 1.0) {
                        currentPos = position + velocity * t + 0.5 * vec2(0.0, grav) * t * t;
                    } else {
                        currentPos = position + velocity * ((1.0 - pow(fric, t)) / -log(fric)) + 0.5 * vec2(0.0, grav) * t * t;
                    }
                    
                    float dist = length(fragCoord - currentPos);
                    float mask = smoothstep(finalSize, finalSize - 1.0, dist);
                    
                    return vec4(dynamicColor.rgb, mask * dynamicColor.a * color.a);
                }
            """.trimIndent())
        }

        return object : Shader {
            override val sksl: String = skslCode
            override fun ShaderEffect.applyUniforms() {
                var i = 0
                while (i < layerCount) {
                    uniform("uLayerDuration$i", layers[i].duration)
                    i++
                }
            }
        }
    }
}