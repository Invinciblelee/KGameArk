package com.example.kgame.games.shader

import androidx.compose.ui.graphics.Color
import com.kgame.engine.graphics.material.Material
import com.kgame.engine.graphics.material.Material.Companion.RESOLUTION
import com.kgame.engine.graphics.material.Material.Companion.TIME
import com.kgame.engine.graphics.material.MaterialEffect
import com.kgame.engine.graphics.material.MaterialMetadata
import org.intellij.lang.annotations.Language

class BlueSky(
    val scale: Float = 1.5f,
    val cloudCover: Float = 0.35f,
    val cloudSharpness: Float = 0.25f,
    val skyColorTop: Color = Color(0.05f, 0.15f, 0.4f),
    val skyColorHorizon: Color = Color(0.55f, 0.75f, 1.0f)
) : Material {
    companion object {
        // Uniform names
        private const val SCALE = "uScale"
        private const val CLOUD_PARAMS = "uCloudParams"
        private const val SKY_COLOR_TOP = "uSkyColorTop"
        private const val SKY_COLOR_HORIZON = "uSkyColorHorizon"
    }

    override val metadata = MaterialMetadata(
        name = "Blue Sky",
        authorName = "Kirk"
    )

    override val speedModifier: Float
        get() = 1f

    @Language("AGSL")
    override val sksl: String = """
        uniform float3 $RESOLUTION;
        uniform float $TIME;
        uniform float $SCALE;
        uniform float2 $CLOUD_PARAMS; 
        uniform vec3 $SKY_COLOR_TOP;
        uniform vec3 $SKY_COLOR_HORIZON;

        float random(vec2 p) {
            return fract(sin(dot(p.xy, vec2(12.9898, 78.233))) * 43758.5453123);
        }

        float noise(vec2 p) {
            vec2 i = floor(p);
            vec2 f = fract(p);
            vec2 u = f * f * (3.0 - 2.0 * f); // Smoothstep
            float a = random(i);
            float b = random(i + vec2(1.0, 0.0));
            float c = random(i + vec2(0.0, 1.0));
            float d = random(i + vec2(1.0, 1.0));
            return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
        }
        
        // Simplified FBM with fewer iterations
        float fbm(vec2 p) {
            float value = 0.0;
            float amplitude = 0.5;
            // **PERFORMANCE FIX**: Reduced loop from 7 to 6, can be lower
            for (int i = 0; i < 6; i++) {
                value += amplitude * noise(p);
                p *= 2.0; // Move to the next octave
                amplitude *= 0.5; // Reduce amplitude
            }
            return value;
        }

        /* ---------- Main Render Function ---------- */
        vec4 main(in vec2 fragCoord) {
            // 1. Correctly calculate aspect-corrected UV coordinates
            vec2 uv = (2.0 * fragCoord.xy - $RESOLUTION.xy) / min($RESOLUTION.x, $RESOLUTION.y);
            
            /* 2. Create the Sky Gradient */
            float gradient = smoothstep(-1.0, 1.0, uv.y);
            vec3 skyColor = mix($SKY_COLOR_TOP, $SKY_COLOR_HORIZON, gradient);

            /* 3. Create Clouds (Simplified) */
            // Animate cloud movement
            vec2 cloudUV = uv * $SCALE;
            cloudUV.y -= $TIME * 0.1; // Cloud drift
            
            // Generate cloud noise
            float cloudNoise = fbm(cloudUV);
            
            // Use smoothstep to create sharper cloud edges based on parameters
            float cloudDensity = smoothstep($CLOUD_PARAMS.x, $CLOUD_PARAMS.x + $CLOUD_PARAMS.y, cloudNoise);

            /* 4. Color and Mix */
            // Simple cloud color
            vec3 cloudColor = vec3(1.0); 
            
            // Mix sky and clouds
            vec3 finalColor = mix(skyColor, cloudColor, cloudDensity);

            return vec4(finalColor, 1.0);
        }
    """

    override fun MaterialEffect.applyUniforms() {
        uniform(SCALE, scale)
        uniform(CLOUD_PARAMS, cloudCover, cloudSharpness)
        uniform(SKY_COLOR_TOP, skyColorTop)
        uniform(SKY_COLOR_HORIZON, skyColorHorizon)
    }
}
