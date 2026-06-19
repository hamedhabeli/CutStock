package com.example.cutstock.solver

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cutstock.domain.BulkInputParser
import com.example.cutstock.nativecore.NativeSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeSolverIntegrationTest {

    @Test
    fun solve_4demandTypes_producesValidPlan() {
        val startTime = System.currentTimeMillis()
        val kerfMm = 3
        val stockLengths = intArrayOf(12_000)
        val lengthsMm = intArrayOf(6000, 4000, 3000, 2000)
        val quantities = intArrayOf(10, 15, 20, 25)

        val plan = NativeSolver.solveCuttingPlan(
            kerfMm = kerfMm,
            stockLengthsMm = stockLengths,
            lengthsMm = lengthsMm,
            quantities = quantities
        )

        val duration = System.currentTimeMillis() - startTime

        // 1. All pieces are accounted for (sum of bin pieces = sum of demand × quantity)
        val expectedPieces = quantities.sum()
        val actualPieces = plan.bins.sumOf { it.lengthsMm.size }
        assertEquals("All pieces must be accounted for", expectedPieces, actualPieces)

        // 2. No bin exceeds its stock length (including kerf)
        for (bin in plan.bins) {
            val totalPieceLen = bin.lengthsMm.sum()
            val kerfCount = (bin.lengthsMm.size - 1).coerceAtLeast(0)
            val totalRequiredWithKerf = totalPieceLen + kerfCount * kerfMm
            assertTrue(
                "Bin used length ($totalRequiredWithKerf) must not exceed stock length (${bin.stockLengthMm})",
                totalRequiredWithKerf <= bin.stockLengthMm
            )
        }

        // 3. Waste percentage < 15% for standard construction inputs
        val wastePercent = plan.wastePercentBasisPoints / 100.0
        assertTrue("Waste percentage ($wastePercent%) must be under 15%", wastePercent < 15.0)

        // 4. Total execution time < 2000ms
        assertTrue("Execution time ($duration ms) must be under 2s", duration < 2000)
    }

    @Test
    fun solve_resultWasteNotExceedTheoretical() {
        val kerfMm = 3
        val stockLengths = intArrayOf(12_000)
        val lengthsMm = intArrayOf(5500, 4200, 3100, 1500)
        val quantities = intArrayOf(8, 12, 15, 20)

        val plan = NativeSolver.solveCuttingPlan(
            kerfMm = kerfMm,
            stockLengthsMm = stockLengths,
            lengthsMm = lengthsMm,
            quantities = quantities
        )

        // All pieces accounted for
        val expectedPieces = quantities.sum()
        val actualPieces = plan.bins.sumOf { it.lengthsMm.size }
        assertEquals(expectedPieces, actualPieces)

        // No bin exceeds stock size
        for (bin in plan.bins) {
            val totalPieceLen = bin.lengthsMm.sum()
            val kerfCount = (bin.lengthsMm.size - 1).coerceAtLeast(0)
            val totalRequiredWithKerf = totalPieceLen + kerfCount * kerfMm
            assertTrue(totalRequiredWithKerf <= bin.stockLengthMm)
        }

        // Waste percentage < 15%
        val wastePercent = plan.wastePercentBasisPoints / 100.0
        assertTrue("Waste percentage ($wastePercent%) must be under 15%", wastePercent < 15.0)
    }

    @Test
    fun solve_timeLimitRespected_under2Seconds() {
        val startTime = System.currentTimeMillis()
        val kerfMm = 3
        val stockLengths = intArrayOf(12_000)
        // Harder cutting problem to exercise the solver
        val lengthsMm = intArrayOf(5800, 4200, 3100, 2700, 1900)
        val quantities = intArrayOf(30, 40, 50, 60, 70)

        val plan = NativeSolver.solveCuttingPlan(
            kerfMm = kerfMm,
            stockLengthsMm = stockLengths,
            lengthsMm = lengthsMm,
            quantities = quantities,
            timeLimitMicros = 1_500_000L
        )

        val duration = System.currentTimeMillis() - startTime
        assertTrue("Solver finished in $duration ms", duration < 2000)

        val expectedPieces = quantities.sum()
        val actualPieces = plan.bins.sumOf { it.lengthsMm.size }
        assertEquals(expectedPieces, actualPieces)
    }

    @Test
    fun solve_persianNumeralInput_parsedCorrectly() {
        val persianInput = "۴۰۰۰ ۱۰\n۳۲۰۰ ۵\n۲۵۰۰ ۸"
        val parseResult = BulkInputParser.parse(persianInput)
        val demands = parseResult.demands

        val lengthsMm = demands.map { it.lengthMm }.toIntArray()
        val quantities = demands.map { it.quantity }.toIntArray()

        val plan = NativeSolver.solveCuttingPlan(
            kerfMm = 3,
            stockLengthsMm = intArrayOf(12_000),
            lengthsMm = lengthsMm,
            quantities = quantities
        )

        val expectedTotalPieces = demands.sumOf { it.quantity }
        val actualTotalPieces = plan.bins.sumOf { it.lengthsMm.size }
        assertEquals("Persian digits parsed and JNI solved correctly", expectedTotalPieces, actualTotalPieces)

        for (bin in plan.bins) {
            val totalPieceLen = bin.lengthsMm.sum()
            val kerfCount = (bin.lengthsMm.size - 1).coerceAtLeast(0)
            val totalRequiredWithKerf = totalPieceLen + kerfCount * 3
            assertTrue(totalRequiredWithKerf <= bin.stockLengthMm)
        }
    }

    @Test
    fun solve_noFreeze_largeQuantities() {
        val startTime = System.currentTimeMillis()
        val kerfMm = 3
        val stockLengths = intArrayOf(12_000)
        val lengthsMm = intArrayOf(5000, 4000, 3000, 2000)
        val quantities = intArrayOf(1000, 1000, 1000, 1000) // 4 types x 1000 pieces each

        val plan = NativeSolver.solveCuttingPlan(
            kerfMm = kerfMm,
            stockLengthsMm = stockLengths,
            lengthsMm = lengthsMm,
            quantities = quantities,
            timeLimitMicros = 1_000_000L
        )

        val duration = System.currentTimeMillis() - startTime
        assertTrue("Solver should not freeze for large quantities; finished in $duration ms", duration < 2000)

        val expectedPieces = quantities.sum()
        val actualPieces = plan.bins.sumOf { it.lengthsMm.size }
        assertEquals(expectedPieces, actualPieces)

        for (bin in plan.bins) {
            val totalPieceLen = bin.lengthsMm.sum()
            val kerfCount = (bin.lengthsMm.size - 1).coerceAtLeast(0)
            val totalRequiredWithKerf = totalPieceLen + kerfCount * kerfMm
            assertTrue(totalRequiredWithKerf <= bin.stockLengthMm)
        }
    }
}
