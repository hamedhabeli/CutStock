package com.example.cutstock.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class SteelWeightCalculatorTest {

    @Test
    fun weightKg_positiveForValidInput() {
        val kg = SteelWeightCalculator.weightKg(lengthMm = 1000, diameterMm = 16)
        assertTrue(kg > 0.0)
    }

    @Test
    fun weightKg_zeroForInvalid() {
        assertTrue(SteelWeightCalculator.weightKg(0, 16) == 0.0)
    }
}
