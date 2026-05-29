package com.example.cutstock.nativecore

import org.junit.Assert.assertEquals
import org.junit.Test

class CuttingPlanPayloadDecoderTest {

    @Test
    fun decodeV1_payload() {
        val raw = intArrayOf(
            CuttingPlanPayloadDecoder.MAGIC,
            CuttingPlanPayloadDecoder.VERSION_V1,
            12_000,
            1,
            200,
            167,
            11_800,
            11_800,
            200,
            2,
            6000,
            5800
        )
        val plan = CuttingPlanPayloadDecoder.decode(raw)
        assertEquals(12_000, plan.stockLengthMm)
        assertEquals(1, plan.binCount)
        assertEquals(2, plan.bins[0].lengthsMm.size)
    }

    @Test
    fun decodeV2_payload() {
        val raw = intArrayOf(
            CuttingPlanPayloadDecoder.MAGIC,
            CuttingPlanPayloadDecoder.VERSION_V2,
            12_000,
            3,
            1,
            100,
            83,
            11_900,
            12_000,
            11_900,
            100,
            2,
            6000,
            5900
        )
        val plan = CuttingPlanPayloadDecoder.decode(raw)
        assertEquals(12_000, plan.stockLengthMm)
        assertEquals(3, plan.kerfMm)
        assertEquals(1, plan.binCount)
        assertEquals(12_000, plan.bins[0].stockLengthMm)
    }
}
