package com.kgame.engine.utils.internal

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Analytical solver for the Spring differential equation.
 * Solves: m*x'' + c*x' + k*x = 0
 */
internal object SpringSolver {

    /**
     * Calculates the animation progress fraction (0.0 to 1.0).
     * @param elapsedTime Time elapsed since the start of the animation.
     * @param stiffness The spring constant (k).
     * @param damping The damping coefficient (c).
     * @param initialVelocity The starting velocity (v0) at t=0.
     */
    fun getFraction(elapsedTime: Float, stiffness: Float, damping: Float, initialVelocity: Float): Float {
        if (stiffness <= 0f) return 1f

        val natFreq = sqrt(stiffness.toDouble())
        val dampingRatio = damping / (2.0 * natFreq)
        val t = elapsedTime.toDouble()
        val v0 = initialVelocity.toDouble()

        // x(t) is the displacement from equilibrium (target).
        // At t=0, x(0) = 1.0 (start position), v(0) = v0.
        val displacement: Double = when {
            dampingRatio > 1.0 -> overDamped(t, natFreq, dampingRatio, v0)
            dampingRatio == 1.0 -> criticallyDamped(t, natFreq, v0)
            else -> underDamped(t, natFreq, dampingRatio, v0)
        }

        // Fraction is (1.0 - displacement) to go from 0.0 to 1.0
        return (1.0 - displacement).toFloat()
    }

    private fun overDamped(t: Double, n: Double, zeta: Double, v0: Double): Double {
        val s = n * sqrt(zeta * zeta - 1.0)
        val r = -zeta * n
        val gammaPlus = r + s
        val gammaMinus = r - s

        // Solve for coefficients A and B:
        // 1) A + B = 1.0 (initial pos)
        // 2) A*gammaPlus + B*gammaMinus = v0 (initial vel)
        val a = (v0 - gammaMinus) / (gammaPlus - gammaMinus)
        val b = 1.0 - a

        return a * exp(gammaPlus * t) + b * exp(gammaMinus * t)
    }

    private fun criticallyDamped(t: Double, n: Double, v0: Double): Double {
        val a = 1.0
        // v(0) = -a*n + b = v0  => b = v0 + n
        val b = v0 + n

        return (a + b * t) * exp(-n * t)
    }

    private fun underDamped(t: Double, n: Double, zeta: Double, v0: Double): Double {
        val dampedFreq = n * sqrt(1.0 - zeta * zeta)
        val realPart = -zeta * n

        val a = 1.0
        // v(0) = a*realPart + b*dampedFreq = v0
        val b = (v0 - a * realPart) / dampedFreq

        val envelope = exp(realPart * t)
        val oscillation = a * cos(dampedFreq * t) + b * sin(dampedFreq * t)

        return envelope * oscillation
    }
}