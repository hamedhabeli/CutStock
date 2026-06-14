package com.example.cutstock.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class SteelWeightCalculatorTest {

    @Test
    fun weightKg_positiveForValidInput() {
        // lengthMm parameter is Long — use the L suffix to avoid a type-mismatch compile error
        val kg = SteelWeightCalculator.weightKg(lengthMm = 1000L, diameterMm = 16)
        assertTrue(kg > 0.0)
    }

    @Test
    fun weightKg_zeroForInvalid() {
        // Same fix: pass 0L instead of 0
        assertTrue(SteelWeightCalculator.weightKg(0L, 16) == 0.0)
    }
}
