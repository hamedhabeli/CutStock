package com.example.cutstock.nativecore

/**
 * Decodes native solver int payloads (v1 and v2) for app use and JVM unit tests.
 */
object CuttingPlanPayloadDecoder {
    const val MAGIC = 0x52534431
    const val VERSION_V1 = 1
    const val VERSION_V2 = 2

    fun decode(raw: IntArray): CuttingPlan {
        require(raw.size >= 7) { "Invalid native payload" }
        require(raw[0] == MAGIC) { "Native solver rejected input" }
        return when (raw[1]) {
            VERSION_V1 -> decodeV1(raw)
            VERSION_V2 -> decodeV2(raw)
            else -> error("Unsupported native payload version: ${raw[1]}")
        }
    }

    private fun decodeV1(raw: IntArray): CuttingPlan {
        val stockLengthMm = raw[2]
        val binCount = raw[3].coerceAtLeast(0)
        val totalWasteMm = raw[4].toLong()
        val wastePercentBasisPoints = raw[5]

        var cursor = 7
        val bins = ArrayList<Bin>(binCount)
        repeat(binCount) {
            require(cursor + 3 <= raw.size) { "Corrupted native payload" }
            val usedMm = raw[cursor++]
            val wasteMm = raw[cursor++]
            val count = raw[cursor++].coerceAtLeast(0)
            require(cursor + count <= raw.size) { "Corrupted native payload" }
            val lengths = IntArray(count) { raw[cursor++] }
            bins += Bin(
                stockLengthMm = stockLengthMm,
                usedMm = usedMm,
                wasteMm = wasteMm,
                lengthsMm = lengths
            )
        }

        return CuttingPlan(
            stockLengthMm = stockLengthMm,
            kerfMm = 0,
            binCount = binCount,
            totalWasteMm = totalWasteMm,
            wastePercentBasisPoints = wastePercentBasisPoints,
            bins = bins
        )
    }

    private fun decodeV2(raw: IntArray): CuttingPlan {
        require(raw.size >= 8) { "Invalid v2 payload" }
        val stockLengthMm = raw[2]
        val kerfMm = raw[3]
        val binCount = raw[4].coerceAtLeast(0)
        val totalWasteMm = raw[5].toLong()
        val wastePercentBasisPoints = raw[6]

        var cursor = 8
        val bins = ArrayList<Bin>(binCount)
        repeat(binCount) {
            require(cursor + 4 <= raw.size) { "Corrupted native payload" }
            val binStock = raw[cursor++]
            val usedMm = raw[cursor++]
            val wasteMm = raw[cursor++]
            val count = raw[cursor++].coerceAtLeast(0)
            require(cursor + count <= raw.size) { "Corrupted native payload" }
            val lengths = IntArray(count) { raw[cursor++] }
            bins += Bin(
                stockLengthMm = binStock,
                usedMm = usedMm,
                wasteMm = wasteMm,
                lengthsMm = lengths
            )
        }

        return CuttingPlan(
            stockLengthMm = stockLengthMm,
            kerfMm = kerfMm,
            binCount = binCount,
            totalWasteMm = totalWasteMm,
            wastePercentBasisPoints = wastePercentBasisPoints,
            bins = bins
        )
    }
}
