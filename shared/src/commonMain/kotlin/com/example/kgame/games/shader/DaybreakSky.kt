package com.example.kgame.games.shader

import com.kgame.engine.graphics.shader.Shader
import com.kgame.engine.graphics.shader.ShaderMetadata
import org.intellij.lang.annotations.Language

object DaybreakSky : Shader {

    override val metadata = ShaderMetadata(
        name = "Daybreak Sky"
    )

    // You can adjust this modifier to make the day/night cycle faster or slower.
    override val speedModifier: Float
        get() = 0.1f

    @Language("AGSL")
    override val sksl = """
        uniform float uTime;
        // **CORRECTED**: Declared as vec3 to match the data sent from Kotlin.
        // uResolution.xy contains the width and height.
        // uResolution.z could contain the aspect ratio if needed, but here we just use .xy
        uniform vec3 uResolution;

        // --- Configurable Constants ---
        const vec3 SKY_COLOR_DAY = vec3(0.5, 0.7, 0.9);
        const vec3 SKY_COLOR_NIGHT = vec3(0.05, 0.1, 0.2);
        const vec3 SUN_COLOR = vec3(1.0, 0.8, 0.6);
        const vec3 MOON_COLOR = vec3(0.9, 0.9, 1.0);
        const vec3 CLOUD_COLOR = vec3(1.0);

        // --- Utility function for noise generation ---
        // 2D Random
        float random(in vec2 st) {
            return fract(sin(dot(st.xy, vec2(12.9898, 78.233))) * 43758.5453123);
        }

        // 2D Noise based on Morgan McGuire @morgan3d
        // https://www.shadertoy.com/view/4dS3Wd
        float noise(in vec2 st) {
            vec2 i = floor(st);
            vec2 f = fract(st);

            float a = random(i);
            float b = random(i + vec2(1.0, 0.0));
            float c = random(i + vec2(0.0, 1.0));
            float d = random(i + vec2(1.0, 1.0));

            vec2 u = f * f * (3.0 - 2.0 * f);
            return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
        }

        // --- FBM (Fractal Brownian Motion) for cloud layers ---
        float fbm(in vec2 st) {
            float value = 0.0;
            float amplitude = 0.5;
            for (int i = 0; i < 4; i++) {
                value += amplitude * noise(st);
                st *= 2.0;
                amplitude *= 0.5;
            }
            return value;
        }
        
        // --- Main shader function ---
        vec4 main(vec2 fragCoord) {
            // **CORRECTED**: Use uResolution.xy to get the width and height.
            vec2 uv = fragCoord.xy / uResolution.xy;

            // 1. Day/Night Cycle (0.0 for midnight, 1.0 for noon)
            float dayCycle = (sin(uTime) + 1.0) / 2.0;

            // 2. Sky Color Gradient based on vertical position
            vec3 skyColor = mix(SKY_COLOR_NIGHT, SKY_COLOR_DAY, dayCycle);
            vec3 finalColor = skyColor * (1.0 - uv.y);

            // 3. Sun and Moon
            // Calculate a circular path for sun/moon
            float sunMoonAngle = uTime;
            vec2 sunPos = vec2(cos(sunMoonAngle), sin(sunMoonAngle)) * 0.3 + 0.5;
            vec2 moonPos = vec2(cos(sunMoonAngle + 3.14159), sin(sunMoonAngle + 3.14159)) * 0.3 + 0.5;
            
            float sunGlow = 0.01 / distance(uv, sunPos);
            float moonGlow = 0.005 / distance(uv, moonPos);

            finalColor += SUN_COLOR * sunGlow * dayCycle;
            finalColor += MOON_COLOR * moonGlow * (1.0 - dayCycle);

            // 4. Clouds
            vec2 cloudUV = uv;
            cloudUV.x += uTime * 0.02; // Clouds slowly drift
            float cloudNoise = fbm(cloudUV * 5.0);
            
            // Make clouds fade out at night
            float cloudAlpha = cloudNoise * pow(dayCycle, 0.5);
            finalColor = mix(finalColor, CLOUD_COLOR, cloudAlpha * 0.3);

            return vec4(finalColor, 1.0);
        }
    """
}
