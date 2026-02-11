// Copyright 2024, Mike Penz and Giorgi Azmaipharashvili, Author of the Glossy Gradients Shader
// SPDX-License-Identifier: MIT
package com.example.kgame.games.shader

import com.kgame.engine.graphics.shader.Shader
import com.kgame.engine.graphics.shader.ShaderMetadata
import org.intellij.lang.annotations.Language

object GlossyGradient : Shader {

    override val metadata = ShaderMetadata(
        name = "GlossyGradients",
        authorName = "Giorgi Azmaipharashvili",
        authorUrl = "https://www.shadertoy.com/user/Peace",
        credit = "https://www.shadertoy.com/view/lX2GDR",
        license = "MIT License",
        licenseUrl = "https://opensource.org/license/mit"
    )

    @Language("AGSL")
    override val sksl = """
        uniform float uTime;
        uniform vec2 uResolution;
        
        vec4 main( vec2 fragCoord )
        {
            float mr = min(uResolution.x, uResolution.y);
            vec2 uv = (fragCoord * 2.0 - uResolution) / mr;
        
            float d = -uTime * 0.5;
            float a = 0.0;
            for (float i = 0.0; i < 8.0; ++i) {
                a += cos(i - d - a * uv.x);
                d += sin(uv.y * i + a);
            }
            d += uTime * 0.5;
            vec3 col = vec3(cos(uv * vec2(d, a)) * 0.6 + 0.4, cos(a + d) * 0.5 + 0.5);
            col = cos(col * cos(vec3(d, a, 2.5)) * 0.5 + 0.5);
            return vec4(col,1.0);
        }
    """
}