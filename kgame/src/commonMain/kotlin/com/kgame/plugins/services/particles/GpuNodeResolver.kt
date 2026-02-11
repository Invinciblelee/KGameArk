package com.kgame.plugins.services.particles

/**
 * Stateless GPU Resolver for SkSL string generation.
 * Maps ParticleNode tree to shader expressions using 'p' prefix for calculated/local attributes.
 */
object GpuNodeResolver {

    /**
     * Translates a ParticleNode tree into a SkSL/GLSL compatible expression string.
     * Standardized grouping for better SDK maintainability.
     */
    fun resolve(node: ParticleNode): String = when (node) {
        // --- System Inputs (Uniforms) ---
        is ParticleNode.Time -> "uTime"
        is ParticleNode.DeltaTime -> "uDeltaTime"

        is ParticleNode.Resolution -> "uResolution"

        // --- Context Attributes (Scoped Locals) ---
        is ParticleNode.Count -> "pCount"
        is ParticleNode.Progress -> "pProgress"
        is ParticleNode.Index -> "pIndex"

        // --- Core Containers ---
        is ParticleNode.Scalar -> {
            val s = node.value.toString()
            // If the string representation doesn't contain a dot, append .0
            if (!s.contains(".")) "$s.0" else s
        }
        is ParticleNode.Vector2 -> "vec2(${resolve(node.x)}, ${resolve(node.y)})"
        is ParticleNode.Color -> "vec4(${resolve(node.red)}, ${resolve(node.green)}, ${resolve(node.blue)}, ${resolve(node.alpha)})"

        // --- Arithmetic Operations ---
        is ParticleNode.Add -> "(${resolve(node.left)} + ${resolve(node.right)})"
        is ParticleNode.Subtract -> "(${resolve(node.left)} - ${resolve(node.right)})"
        is ParticleNode.Multiply -> "(${resolve(node.left)} * ${resolve(node.right)})"
        is ParticleNode.Divide -> "(${resolve(node.left)} / ${resolve(node.right)})"

        // --- Vector Functions ---
        is ParticleNode.Dot -> "dot(${resolve(node.left)}, ${resolve(node.right)})"
        is ParticleNode.Length -> "length(${resolve(node.node)})"
        is ParticleNode.Distance -> "distance(${resolve(node.p1)}, ${resolve(node.p2)})"
        is ParticleNode.Normalize -> "normalize(${resolve(node.node)})"

        // --- Trigonometric Functions ---
        is ParticleNode.Sin -> "sin(${resolve(node.node)})"
        is ParticleNode.Cos -> "cos(${resolve(node.node)})"
        is ParticleNode.Tan -> "tan(${resolve(node.node)})"
        is ParticleNode.Atan -> "atan(${resolve(node.node)})"
        is ParticleNode.Atan2 -> "atan(${resolve(node.y)}, ${resolve(node.x)})"

        // --- Exponential and Power Functions ---
        is ParticleNode.Pow -> "pow(${resolve(node.base)}, ${resolve(node.exponent)})"
        is ParticleNode.Exp -> "exp(${resolve(node.node)})"
        is ParticleNode.Sqrt -> "sqrt(${resolve(node.node)})"
        is ParticleNode.Abs -> "abs(${resolve(node.node)})"

        // --- Shaping and Cycling ---
        is ParticleNode.Fract -> "fract(${resolve(node.node)})"
        is ParticleNode.Mod -> "mod(${resolve(node.left)}, ${resolve(node.right)})"
        is ParticleNode.Floor -> "floor(${resolve(node.node)})"
        is ParticleNode.Ceil -> "ceil(${resolve(node.node)})"
        is ParticleNode.Sign -> "sign(${resolve(node.node)})"

        // --- Interpolation and Steps ---
        is ParticleNode.Mix -> "mix(${resolve(node.start)}, ${resolve(node.end)}, ${resolve(node.t)})"
        is ParticleNode.Step -> "step(${resolve(node.edge)}, ${resolve(node.v)})"
        is ParticleNode.SmoothStep -> "smoothstep(${resolve(node.e0)}, ${resolve(node.e1)}, ${resolve(node.v)})"

        // --- Comparisons and Selections ---
        is ParticleNode.Min -> "min(${resolve(node.first)}, ${resolve(node.second)})"
        is ParticleNode.Max -> "max(${resolve(node.first)}, ${resolve(node.second)})"
        is ParticleNode.Clamp -> "clamp(${resolve(node.value)}, ${resolve(node.min)}, ${resolve(node.max)})"

        is ParticleNode.Comparison -> {
            // SkSL ternary logic for float output
            "((${resolve(node.left)}) ${node.op.symbol} (${resolve(node.right)}) ? 1.0 : 0.0)"
        }

        is ParticleNode.Combine -> "(((${resolve(node.left)} > 0.5) ${node.op.symbol} (${resolve(node.right)} > 0.5)) ? 1.0 : 0.0)"

        is ParticleNode.RandomRange -> {
            // SkSL FIX: Replace XOR (^) with Addition and use float salt
            val salt = (node.hashCode() % 100000).let { if (it < 0) -it else it }.toFloat()
            val hash = "iHash(pIndex + $salt)"
            "mix(${node.min}, ${node.max}, pow($hash, ${node.exp}))"
        }

        is ParticleNode.Select -> {
            val conditionStr = when (val cond = node.condition) {
                // SkSL FIX: Avoid bitwise ops and use float iHash
                is SelectCondition.Ratio -> {
                    val salt = (cond.hashCode() % 100000).let { if (it < 0) -it else it }.toFloat()
                    "(iHash(pIndex + $salt) < ${cond.value})"
                }
                is SelectCondition.Threshold -> "(pIndex < ${cond.value})"
                is SelectCondition.Modulo -> "(mod(pIndex, ${cond.divisor.toFloat()}) == 0.0)"
            }
            "($conditionStr ? ${resolve(node.onTrue)} : ${resolve(node.onFalse)})"
        }

        is SlotNode -> {
            val baseName = when (node.key) {
                ParticleContext.ORIGIN -> "uOrigin"
                ParticleContext.RESOLUTION -> "uResolution"
                ParticleContext.TIME -> "uTime"
                ParticleContext.PROGRESS -> "pProgress"
                ParticleContext.INDEX -> "pIndex"
                else -> error("Unsupported GPU slot: ${node.key}")
            }

            when (node.mapping) {
                AttributeMapping.LowFloat -> "$baseName.x"
                AttributeMapping.HighFloat -> "$baseName.y"
                else -> baseName
            }
        }
    }
}