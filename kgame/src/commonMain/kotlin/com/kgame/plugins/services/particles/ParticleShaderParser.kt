package com.kgame.plugins.services.particles

import com.kgame.engine.graphics.shader.Shader
import com.kgame.engine.graphics.shader.ShaderEffect

object ParticleShaderParser : ParticleParser<List<ShaderEffect>> {

    override fun translate(scope: ParticleNodeScope): List<ShaderEffect> {
        return scope.layers.map { layer ->
            val skslCode = buildString {
                // --- Uniforms ---
                appendLine("uniform float uTime;")
                appendLine("uniform vec2 uResolution;")

                // --- Common Math Helpers ---
                appendLine("""
                // Precision constants
                const float PI = 3.14159265359;
                const float TWO_PI = 6.28318530718;

                float iHash(float x) { 
                    return fract(sin(x * 12.9898) * 43758.5453123); 
                }
            """.trimIndent())

                appendLine("vec4 main(vec2 fragCoord) {")
                // Re-center coordinates: (0,0) is the center of the effect
                appendLine("    vec2 p = fragCoord - uResolution * 0.5;")
                appendLine("    float pCount = ${layer.count}.0;")
                appendLine("    float pProgress = clamp(uTime / ${layer.duration}, 0.0, 1.0);")
                appendLine("    vec4 finalColor = vec4(0.0);")

                // --- Spatial Index Prediction ---
                // Instead of looping through all particles, we predict which
                // particles might overlap the current pixel based on polar coordinates.
                appendLine("    float angle = atan(p.y, p.x);")
                appendLine("    if (angle < 0.0) angle += TWO_PI;")

                // Map the angle to a "sector" (segment) of the particle circle
                appendLine("    float sector = angle / (TWO_PI / pCount);")

                // Check current, previous, and next sectors to prevent clipping
                // when particles have a large 'pSize'.
                appendLine("    for (float iOffset = -1.0; iOffset <= 1.0; iOffset++) {")
                appendLine("        float pIndex = mod(floor(sector + iOffset), pCount);")

                // --- DSL Expression Injection ---
                // Resolve the position, size, and color nodes into pure SkSL code
                appendLine("        vec2 pPos = ${GpuNodeResolver.resolve(layer.position)};")
                appendLine("        float pSize = ${GpuNodeResolver.resolve(layer.size)};")
                appendLine("        vec4 pCol = ${GpuNodeResolver.resolve(layer.color)};")

                // --- Distance Field Rendering ---
                appendLine("""
                float dist = length(p - pPos);
                // Sharp edge for particles using smoothstep
                float mask = smoothstep(pSize, pSize - 1.0, dist);
                vec4 particle = vec4(pCol.rgb, mask * pCol.a);
                
                // Use max blending to prevent white-out (over-saturation)
                finalColor = max(finalColor, particle);
            } // End of sector loop
            
            return finalColor;
        }
            """.trimIndent())
            }

            val shader = Shader(skslCode)
            ShaderEffect(shader).also { effect ->
                effect.setResolution(layer.frame.size)
            }
        }
    }


}