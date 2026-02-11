package com.kgame.plugins.services.particles

import com.kgame.engine.graphics.shader.Shader
import com.kgame.engine.graphics.shader.ShaderEffect

object ParticleShaderParser : ParticleParser<Shader> {

    override fun translate(scope: ParticleNodeScope): Shader {
        val layers = scope.layers
        val layerCount = layers.size

        val skslCode = buildString {
            // --- 1. Global Uniforms (Updated Externally) ---
            appendLine("uniform float uTime;")
            appendLine("uniform float uDeltaTime;")
            appendLine("uniform vec2 uResolution;") // x:w, y:h

            // --- 2. Context & Layer Uniforms ---
            appendLine("uniform vec2 uOrigin;")     // Interaction origin

            var i = 0
            while (i < layerCount) {
                appendLine("uniform float uLayerDuration$i;")
                appendLine("uniform float uLayerParticleCount$i;")
                i++
            }

            // --- 3. SkSL Helpers & Resolver ---
            appendLine("""
                float iHash(float x) {
                    return fract(sin(x * 12.9898) * 43758.5453123);
                }
            """.trimIndent())

            appendLine("void resolveLayer(int lIdx, float pIndexParam, out vec2 outPos, out float outSize, out vec4 outColor) {")
            appendLine("    outPos = uOrigin;")
            appendLine("    outSize = 1.0;")
            appendLine("    outColor = vec4(1.0);")

            i = 0
            while (i < layerCount) {
                val layer = layers[i]
                appendLine("    if (lIdx == $i) {")
                appendLine("        float pProgress = clamp(uTime / uLayerDuration$i, 0.0, 1.0);")
                appendLine("        float pLayerCount = uLayerParticleCount$i;")
                appendLine("        float pIndex = pIndexParam;")

                appendLine("        outPos = ${GpuNodeResolver.resolve(layer.position)};")
                appendLine("        outSize = ${GpuNodeResolver.resolve(layer.size)};")
                appendLine("        outColor = ${GpuNodeResolver.resolve(layer.color)};")
                appendLine("        outColor.a *= ${GpuNodeResolver.resolve(layer.alpha)};")
                appendLine("    }")
                i++
            }
            appendLine("}")

            // --- 4. Main Fragment Shader ---
            appendLine("""
                vec4 main(vec2 fragCoord) {
                    vec4 finalColor = vec4(0.0);
                    for (int l = 0; l < $layerCount; l++) {
                        float maxCount = 0.0;
            """)

            i = 0
            while (i < layerCount) {
                appendLine("        if (l == $i) maxCount = uLayerParticleCount$i;")
                i++
            }

            appendLine("""
                        for (int i = 0; i < 400; i++) { 
                            if (float(i) >= maxCount) break;

                            float pIndexValue = float(i);
                            vec2 pPos; float pSize; vec4 pCol;
                            
                            resolveLayer(l, pIndexValue, pPos, pSize, pCol);
                            
                            float dist = length(fragCoord - pPos);
                            float mask = smoothstep(pSize, pSize - 1.0, dist);
                            
                            vec4 particle = vec4(pCol.rgb, mask * pCol.a);
                            finalColor = finalColor + particle * (1.0 - finalColor.a);
                        }
                    }
                    return finalColor;
                }
            """.trimIndent())
        }

        return object : Shader {
            override val sksl: String = skslCode

            override fun ShaderEffect.applyUniforms() {
                // Only sync context-specific and layer-specific data
                val origin = scope.context.getOffset(ParticleContext.ORIGIN)
                uniform("uOrigin", origin.x, origin.y)

                var i = 0
                while (i < layerCount) {
                    val layer = layers[i]
                    uniform("uLayerDuration$i", layer.duration)
                    uniform("uLayerParticleCount$i", layer.spawnCount.toFloat())
                    i++
                }
            }
        }
    }
}