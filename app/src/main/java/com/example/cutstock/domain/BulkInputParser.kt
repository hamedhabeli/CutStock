package com.example.cutstock.domain

import com.example.cutstock.data.DemandInput

// Replace the return type with ParseResult so the parser can report ignored lines.
data class ParseResult(
    val demands: List<DemandInput>,
    val ignoredLines: Int,
)

object BulkInputParser {
    private val numberRegex = Regex("""[\d\u06F0-\u06F9]+""")  // ASCII + Extended Arabic-Indic

    private fun normalizeDigits(input: String): String {
        return input.map { c ->
            when (c) {
                in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')  // Persian digits
                in '\u0660'..'\u0669' -> '0' + (c - '\u0660')  // Arabic-Indic digits
                else -> c
            }
        }.joinToString("")
    }

    fun parse(raw: String): ParseResult {
        val grouped = linkedMapOf<Int, Int>()
        var ignoredLines = 0

        raw.lineSequence().forEach { line ->
            val normalizedLine = normalizeDigits(line)
            val trimmed = normalizedLine.trim()
            if (trimmed.isEmpty()) return@forEach

            // Reject explicit negative numbers so "4000 -5" is ignored rather than parsed as 5.
            if (containsNegativeNumber(trimmed)) {
                ignoredLines += 1
                return@forEach
            }

            val values = numberRegex.findAll(trimmed)
                .mapNotNull { it.value.toIntOrNull() }
                .take(2)
                .toList()

            if (values.size < 2) {
                ignoredLines += 1
                return@forEach
            }

            val lengthMm = values[0]
            val quantity = values[1]
            if (lengthMm <= 0 || quantity <= 0) {
                ignoredLines += 1
                return@forEach
            }

            grouped[lengthMm] = (grouped[lengthMm] ?: 0) + quantity
        }

        val demands = grouped.entries
            .map { DemandInput(lengthMm = it.key, quantity = it.value) }
            .sortedByDescending { it.lengthMm }

        return ParseResult(demands = demands, ignoredLines = ignoredLines)
    }


    fun parseDemands(raw: String): List<DemandInput> = parse(raw).demands

    fun format(demands: List<DemandInput>): String = demands.joinToString(separator = "\n") {
        "${it.lengthMm} ${it.quantity}"
    }

    private fun containsNegativeNumber(input: String): Boolean {
        return Regex("""(^|[^\d])[-−﹣－]\s*\d+""").containsMatchIn(input)
    }
}
