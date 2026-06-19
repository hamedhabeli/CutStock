package com.example.cutstock.domain

import com.example.cutstock.data.DemandInput
import com.example.cutstock.data.ProjectEntity
import com.example.cutstock.nativecore.CuttingPlan
import kotlin.math.max
import kotlin.math.roundToLong

data class SalesSummary(
    val barsNeeded: Int,
    val wastePercent: Double,
    val naiveWasteKg: Double,
    val actualWasteKg: Double,
    val savedWasteKg: Double,
    val moneySavedTomans: Long,
    val averageUtilizationPercent: Double,
    val largestWasteMm: Int,
    val smallestWasteMm: Int,
)

object SalesCalculator {
    fun calculate(
        plan: CuttingPlan,
        project: ProjectEntity,
        demands: List<DemandInput>,
    ): SalesSummary {
        val barsNeeded = plan.binCount.coerceAtLeast(0)
        val wastePercent = plan.wastePercentBasisPoints / 100.0
        val primaryStock = project.stockLengthsMm.maxOrNull() ?: 12_000

        val naiveWasteMm = NaiveCuttingEstimator.estimateTotalWasteMm(
            demands = demands,
            stockLengthMm = primaryStock,
            kerfMm = project.kerfMm,
        )
        val naiveWasteKg = SteelWeightCalculator.weightKg(
            lengthMm = naiveWasteMm,
            diameterMm = project.diameterMm,
            densityKgM3 = project.steelDensityKgM3,
        )
        val actualWasteKg = SteelWeightCalculator.weightKg(
            lengthMm = plan.totalWasteMm,
            diameterMm = project.diameterMm,
            densityKgM3 = project.steelDensityKgM3,
        )
        val savedWasteKg = max(0.0, naiveWasteKg - actualWasteKg)
        val moneySavedTomans = (savedWasteKg * project.pricePerKgTomans).roundToLong()

        val wasteValues = plan.bins.map { it.wasteMm }
        val averageUtilizationPercent = if (plan.bins.isEmpty()) {
            0.0
        } else {
            plan.bins.map { bin ->
                val cap = bin.stockLengthMm.coerceAtLeast(1)
                bin.usedMm.toDouble() / cap.toDouble() * 100.0
            }.average()
        }

        return SalesSummary(
            barsNeeded = barsNeeded,
            wastePercent = wastePercent,
            naiveWasteKg = naiveWasteKg,
            actualWasteKg = actualWasteKg,
            savedWasteKg = savedWasteKg,
            moneySavedTomans = moneySavedTomans,
            averageUtilizationPercent = averageUtilizationPercent,
            largestWasteMm = wasteValues.maxOrNull() ?: 0,
            smallestWasteMm = wasteValues.minOrNull() ?: 0,
        )
    }
}
