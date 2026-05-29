package com.example.cutstock.domain

import com.example.cutstock.data.DemandInput
import kotlin.math.max

/**
 * Sequential bar-by-bar baseline (no global optimization) with kerf between cuts.
 */
object NaiveCuttingEstimator {
    fun estimateTotalWasteMm(
        demands: List<DemandInput>,
        stockLengthMm: Int,
        kerfMm: Int
    ): Long {
        if (demands.isEmpty() || stockLengthMm <= 0) return 0L

        val pieces = demands
            .flatMap { demand -> List(demand.quantity) { demand.lengthMm } }
            .sortedDescending()

        var totalWaste = 0L
        var used = 0
        var cutsInBar = 0

        for (pieceLen in pieces) {
            val kerfCost = if (cutsInBar == 0) 0 else kerfMm
            val needed = pieceLen + kerfCost
            if (used + needed > stockLengthMm) {
                totalWaste += (stockLengthMm - used).toLong()
                used = 0
                cutsInBar = 0
            }
            val kerfForThis = if (cutsInBar == 0) 0 else kerfMm
            used += pieceLen + kerfForThis
            cutsInBar++
        }
        if (used > 0) {
            totalWaste += (stockLengthMm - used).toLong()
        }
        return max(0L, totalWaste)
    }

    fun estimateBarsNeeded(
        demands: List<DemandInput>,
        stockLengthMm: Int,
        kerfMm: Int
    ): Int {
        if (demands.isEmpty() || stockLengthMm <= 0) return 0

        val pieces = demands
            .flatMap { demand -> List(demand.quantity) { demand.lengthMm } }
            .sortedDescending()

        var bars = 0
        var used = 0
        var cutsInBar = 0

        for (pieceLen in pieces) {
            val kerfCost = if (cutsInBar == 0) 0 else kerfMm
            if (used + pieceLen + kerfCost > stockLengthMm) {
                bars++
                used = 0
                cutsInBar = 0
            }
            val kerfForThis = if (cutsInBar == 0) 0 else kerfMm
            used += pieceLen + kerfForThis
            cutsInBar++
        }
        return bars + if (used > 0) 1 else 0
    }
}
