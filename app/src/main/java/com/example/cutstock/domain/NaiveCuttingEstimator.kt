package com.example.cutstock.domain

import com.example.cutstock.data.DemandInput
import kotlin.math.ceil
import kotlin.math.max

/**
 * Sequential bar-by-bar baseline (no global optimization) with kerf between cuts.
 */
object NaiveCuttingEstimator {
    private const val FLAT_EXPANSION_LIMIT = 10_000

    fun estimateTotalWasteMm(
        demands: List<DemandInput>,
        stockLengthMm: Int,
        kerfMm: Int,
    ): Long {
        if (demands.isEmpty() || stockLengthMm <= 0) return 0L

        val totalPieces = demands.sumOf { it.quantity.toLong() }
        return if (totalPieces <= FLAT_EXPANSION_LIMIT) {
            estimateTotalWasteExact(demands, stockLengthMm, kerfMm)
        } else {
            estimateTotalWasteApproximate(demands, stockLengthMm, kerfMm)
        }
    }

    fun estimateBarsNeeded(
        demands: List<DemandInput>,
        stockLengthMm: Int,
        kerfMm: Int,
    ): Int {
        if (demands.isEmpty() || stockLengthMm <= 0) return 0

        val totalPieces = demands.sumOf { it.quantity.toLong() }
        return if (totalPieces <= FLAT_EXPANSION_LIMIT) {
            estimateBarsNeededExact(demands, stockLengthMm, kerfMm)
        } else {
            estimateBarsNeededApproximate(demands, stockLengthMm, kerfMm)
        }
    }

    private fun estimateTotalWasteExact(
        demands: List<DemandInput>,
        stockLengthMm: Int,
        kerfMm: Int,
    ): Long {
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
            cutsInBar += 1
        }

        if (used > 0) {
            totalWaste += (stockLengthMm - used).toLong()
        }

        return max(0L, totalWaste)
    }

    private fun estimateBarsNeededExact(
        demands: List<DemandInput>,
        stockLengthMm: Int,
        kerfMm: Int,
    ): Int {
        val pieces = demands
            .flatMap { demand -> List(demand.quantity) { demand.lengthMm } }
            .sortedDescending()

        var bars = 0
        var used = 0
        var cutsInBar = 0

        for (pieceLen in pieces) {
            val kerfCost = if (cutsInBar == 0) 0 else kerfMm
            if (used + pieceLen + kerfCost > stockLengthMm) {
                bars += 1
                used = 0
                cutsInBar = 0
            }
            val kerfForThis = if (cutsInBar == 0) 0 else kerfMm
            used += pieceLen + kerfForThis
            cutsInBar += 1
        }

        return bars + if (used > 0) 1 else 0
    }

    private fun estimateTotalWasteApproximate(
        demands: List<DemandInput>,
        stockLengthMm: Int,
        kerfMm: Int,
    ): Long {
        val totalPieces = demands.sumOf { it.quantity.toLong() }
        val totalPieceLength = demands.sumOf { it.lengthMm.toLong() * it.quantity.toLong() }
        val barsNeeded = estimateBarsNeededApproximate(demands, stockLengthMm, kerfMm).toLong().coerceAtLeast(1L)

        val totalRequiredWithKerf = totalPieceLength + kerfMm.toLong() * max(0L, totalPieces - 1L)
        val minimumBars = ceil(totalRequiredWithKerf / stockLengthMm.toDouble()).toLong().coerceAtLeast(1L)
        val effectiveBars = max(barsNeeded, minimumBars)
        val usedLength = totalPieceLength + kerfMm.toLong() * max(0L, totalPieces - effectiveBars)
        val waste = effectiveBars * stockLengthMm.toLong() - usedLength
        return max(0L, waste)
    }

    private fun estimateBarsNeededApproximate(
        demands: List<DemandInput>,
        stockLengthMm: Int,
        kerfMm: Int,
    ): Int {
        val totalPieces = demands.sumOf { it.quantity.toLong() }
        val totalPieceLength = demands.sumOf { it.lengthMm.toLong() * it.quantity.toLong() }
        val totalRequiredWithKerf = totalPieceLength + kerfMm.toLong() * max(0L, totalPieces - 1L)
        return ceil(totalRequiredWithKerf / stockLengthMm.toDouble()).toInt().coerceAtLeast(1)
    }
}
