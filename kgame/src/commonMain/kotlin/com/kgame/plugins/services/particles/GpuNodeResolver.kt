package com.kgame.plugins.services.particles

/**
 * Stateless GPU Resolver for SkSL string generation.
 */
object GpuNodeResolver {

    fun resolve(node: ParticleNode): String = when (node) {
        is ParticleNode.Scalar -> "${node.value}"
        is ParticleNode.Vector2 -> "vec2(${resolve(node.x)}, ${resolve(node.y)})"
        is ParticleNode.Time -> "uTime"
        is ParticleNode.Progress -> "progress"
        is ParticleNode.Index -> "i"
        
        is ParticleNode.RandomRange -> {
            "mix(${node.min}, ${node.max}, pow(iHash(int(i)), ${node.exp}))"
        }

        is ParticleNode.Select -> {
            val conditionStr = when (val cond = node.condition) {
                is SelectCondition.Ratio -> "((i / uCount) < ${cond.value})"

                is SelectCondition.Threshold -> "(int(i) < ${cond.value})"

                is SelectCondition.Modulo -> "(int(i) % ${cond.divisor} == 0)"
            }
            "($conditionStr ? ${resolve(node.onTrue)} : ${resolve(node.onFalse)})"
        }

        is ParticleNode.Add -> "(${resolve(node.left)} + ${resolve(node.right)})"
        is ParticleNode.Multiply -> "(${resolve(node.left)} * ${resolve(node.right)})"
        
        is ParticleNode.Color -> {
            val argb = node.argb
            // Ensure Alpha parity with CPU path
            val finalColor = if ((argb ushr 24) == 0) argb or (0xFF shl 24) else argb
            
            val a = ((finalColor ushr 24) and 0xFF) / 255f
            val r = ((finalColor ushr 16) and 0xFF) / 255f
            val g = ((finalColor ushr 8) and 0xFF) / 255f
            val b = (finalColor and 0xFF) / 255f
            "vec4($r, $g, $b, $a)"
        }
    }
}