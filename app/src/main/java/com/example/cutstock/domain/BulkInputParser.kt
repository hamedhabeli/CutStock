package com.example.cutstock.domain

import com.example.cutstock.data.DemandInput

object BulkInputParser {
    private val numberRegex = Regex("""-?\d+""")

    fun parse(raw: String): List<DemandInput> {
        val grouped = linkedMapOf<Int, Int>()

        raw.lineSequence().forEach { line ->
            val values = numberRegex.findAll(line)
                .mapNotNull { it.value.toIntOrNull() }
                .take(2)
                .toList()

            if (values.size < 2) return@forEach

            val lengthMm = values[0]
            val quantity = values[1]
            if (lengthMm <= 0 || quantity <= 0) return@forEach

            grouped[lengthMm] = (grouped[lengthMm] ?: 0) + quantity
        }

        return grouped.entries
            .map { DemandInput(lengthMm = it.key, quantity = it.value) }
            .sortedByDescending { it.lengthMm }
    }

    fun format(demands: List<DemandInput>): String =
        demands.joinToString(separator = "\n") { "${it.lengthMm} ${it.quantity}" }
}
