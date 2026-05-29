package com.example.cutstock.domain

import com.example.cutstock.data.DemandInput
import com.example.cutstock.data.ProjectEntity
import com.example.cutstock.nativecore.Bin
import com.example.cutstock.nativecore.CuttingPlan
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesCalculatorTest {

    @Test
    fun calculate_savedWeightNonNegative() {
        val project = ProjectEntity(
            name = "Test",
            kerfMm = 3,
            diameterMm = 16,
            pricePerKgTomans = 35_000L,
            stockLengthsMm = listOf(12_000),
            createdAtMillis = 0L,
            updatedAtMillis = 0L
        )
        val plan = CuttingPlan(
            stockLengthMm = 12_000,
            kerfMm = 3,
            binCount = 1,
            totalWasteMm = 500L,
            wastePercentBasisPoints = 400,
            bins = listOf(
                Bin(stockLengthMm = 12_000, usedMm = 11_500, wasteMm = 500, lengthsMm = intArrayOf(6000, 5500))
            )
        )
        val demands = listOf(DemandInput(6000, 1), DemandInput(5500, 1))
        val summary = SalesCalculator.calculate(plan, project, demands)
        assertTrue(summary.savedWasteKg >= 0.0)
        assertTrue(summary.moneySavedTomans >= 0L)
        assertTrue(summary.actualWasteKg > 0.0)
    }
}
