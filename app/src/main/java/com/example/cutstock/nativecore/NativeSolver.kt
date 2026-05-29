package com.example.cutstock.nativecore

import com.google.gson.Gson
import com.google.gson.GsonBuilder

data class Bin(
    val stockLengthMm: Int = 0,
    val usedMm: Int = 0,
    val wasteMm: Int = 0,
    val lengthsMm: IntArray = intArrayOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Bin
        return stockLengthMm == other.stockLengthMm &&
            usedMm == other.usedMm &&
            wasteMm == other.wasteMm &&
            lengthsMm.contentEquals(other.lengthsMm)
    }

    override fun hashCode(): Int {
        var result = stockLengthMm
        result = 31 * result + usedMm
        result = 31 * result + wasteMm
        result = 31 * result + lengthsMm.contentHashCode()
        return result
    }
}

data class CuttingPlan(
    val stockLengthMm: Int = 0,
    val kerfMm: Int = 0,
    val binCount: Int = 0,
    val totalWasteMm: Long = 0L,
    val wastePercentBasisPoints: Int = 0,
    val bins: List<Bin> = emptyList()
)

object NativeSolver {
    init {
        System.loadLibrary("solver")
    }

    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    external fun solveCuttingPlanNative(
        kerfMm: Int,
        stockLengthsMm: IntArray,
        lengthsMm: IntArray,
        quantities: IntArray,
        timeLimitMicros: Long
    ): IntArray

    fun solveCuttingPlan(
        kerfMm: Int,
        stockLengthsMm: IntArray,
        lengthsMm: IntArray,
        quantities: IntArray,
        timeLimitMicros: Long = 1_500_000L
    ): CuttingPlan {
        return CuttingPlanPayloadDecoder.decode(
            solveCuttingPlanNative(kerfMm, stockLengthsMm, lengthsMm, quantities, timeLimitMicros)
        )
    }

    fun encode(plan: CuttingPlan): String = gson.toJson(plan)

    fun decode(json: String): CuttingPlan = gson.fromJson(json, CuttingPlan::class.java)
}
