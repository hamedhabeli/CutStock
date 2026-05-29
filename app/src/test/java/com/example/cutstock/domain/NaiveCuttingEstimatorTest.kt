package com.example.cutstock.domain

import com.example.cutstock.data.DemandInput
import org.junit.Assert.assertTrue
import org.junit.Test

class NaiveCuttingEstimatorTest {

    @Test
    fun estimateBarsNeeded_singleBarFitsAll() {
        val bars = NaiveCuttingEstimator.estimateBarsNeeded(
            demands = listOf(DemandInput(4000, 2), DemandInput(3000, 1)),
            stockLengthMm = 12_000,
            kerfMm = 3
        )
        assertTrue(bars <= 2)
    }

    @Test
    fun estimateTotalWaste_nonNegative() {
        val waste = NaiveCuttingEstimator.estimateTotalWasteMm(
            demands = listOf(DemandInput(5000, 3)),
            stockLengthMm = 12_000,
            kerfMm = 3
        )
        assertTrue(waste >= 0)
    }
}
