package com.example.cutstock.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BulkInputParserTest {

    @Test
    fun parse_validLines_mergesDuplicateLengths() {
        // parse() now returns ParseResult; extract the demands list via .demands
        val result = BulkInputParser.parse(
            """
            4000 150
            4000 50
            3200 80
            invalid
            1000
            """.trimIndent()
        ).demands
        assertEquals(2, result.size)
        assertEquals(4000, result[0].lengthMm)
        assertEquals(200, result[0].quantity)
        assertEquals(3200, result[1].lengthMm)
        assertEquals(80, result[1].quantity)
    }

    @Test
    fun parse_empty_returnsEmpty() {
        assertTrue(BulkInputParser.parse("").demands.isEmpty())
    }

    @Test
    fun format_roundTrip() {
        val demands = listOf(
            com.example.cutstock.data.DemandInput(4000, 10),
            com.example.cutstock.data.DemandInput(2000, 5)
        )
        val formatted = BulkInputParser.format(demands)
        // parse() returns ParseResult; compare only the demands list
        val parsed = BulkInputParser.parse(formatted).demands
        assertEquals(demands, parsed)
    }

    @Test
    fun parse_persianDigits_parsedCorrectly() {
        val rawInput = "۴۰۰۰ ۱۰\n۳۲۰۰ ۵"
        val result = BulkInputParser.parse(rawInput).demands
        assertEquals(2, result.size)
        assertEquals(4000, result[0].lengthMm)
        assertEquals(10, result[0].quantity)
        assertEquals(3200, result[1].lengthMm)
        assertEquals(5, result[1].quantity)
    }

    @Test
    fun parse_negativeNumbers_ignored() {
        val rawInput = "4000 -10\n3200 5\n3000 -2"
        val result = BulkInputParser.parse(rawInput).demands
        assertEquals(1, result.size)
        assertEquals(3200, result[0].lengthMm)
        assertEquals(5, result[0].quantity)
    }

    @Test
    fun parse_ignoredLineCount_isAccurate() {
        val rawInput = "4000 10\nignoreThisLine\n3000 -5\n2000 5"
        val result = BulkInputParser.parse(rawInput)
        assertEquals(2, result.demands.size)
        assertEquals(2, result.ignoredLines)
    }
}
