package com.game.engine.graphics.shader

import org.intellij.lang.annotations.Language

object MesmerizingLens : Shader {

    override val metadata = ShaderMetadata(
        name = "MesmerizingLens",
        authorName = "Mike Penz",
        authorUrl = "https://github.com/mikepenz/",
        credit = "https://github.com/mikepenz/HypnoticCanvas",
        license = "MIT License",
        licenseUrl = "https://opensource.org/license/mit"
    )

    override val speedModifier: Float
        get() = 0.2f

    @Language("AGSL")
    override val sksl = """
        uniform float uTime;
        uniform vec3 uResolution;
        
        vec2 aspectCorrectedUV(vec2 _s6u, vec2 _s6v) {
          vec2 _s6w = _s6u / _s6v * 2.0 - 1.0;
          _s6w.x = _s6w.x * (_s6v.x / _s6v.y);
          return _s6w;
        }
        vec4 creation(vec2 uv, float time) {
          vec3 c;
          float l, z = abs(time);
        
          for (int i = 0; i < 3; i++) {
            vec2 u, p = uv / 2.0;
            u = p;
            z += 0.07;
            l = length(p);
            u += p / l * (sin(z) + 1.0) * abs(sin(l * 9.0 - z - z));
            c[i] = 0.01 / length(mod(u, 1.0) - 0.5);
          }
        
          return vec4(c / l, abs(time));
        }
        vec4 main( vec2 fragCoord ) {
          return creation(
            fragCoord.xy / uResolution.xy,
            sin(
              abs(
                pow(
                  length(aspectCorrectedUV(fragCoord.xy, uResolution.xy)) * 0.5 +
                    sin(uTime),
                  2.0
                )
              ) + 2.0
            )
          );
        }
    """
}