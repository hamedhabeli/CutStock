package com.example.cutstock.domain

import kotlin.math.PI

object SteelWeightCalculator {
    /** Weight in kg for a rebar length in mm. */
    fun weightKg(lengthMm: Long, diameterMm: Int, densityKgM3: Double = 7850.0): Double {
        if (lengthMm <= 0 || diameterMm <= 0) return 0.0
        val radiusM = (diameterMm / 1000.0) / 2.0
        val lengthM = lengthMm / 1000.0
        val crossSectionM2 = PI * radiusM * radiusM
        val volumeM3 = crossSectionM2 * lengthM
        return volumeM3 * densityKgM3
    }
}
