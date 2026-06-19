package com.example.cutstock.presentation

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.cutstock.data.DemandInput
import com.example.cutstock.databinding.ActivityCompetitorPlanBinding
import com.example.cutstock.domain.SteelWeightCalculator
import com.example.cutstock.nativecore.NativeSolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.util.Locale

class CompetitorPlanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCompetitorPlanBinding
    private var solveJob: Job? = null

    // Project parameters passed from MainActivity
    private var stockLength = 12000
    private var kerf = 3
    private var diameter = 16
    private var density = 7850.0
    private var price = 35000L

    private var parsedDemandsText = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCompetitorPlanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        stockLength = intent.getIntExtra("stock_length", 12000)
        kerf = intent.getIntExtra("kerf", 3)
        diameter = intent.getIntExtra("diameter", 16)
        density = intent.getDoubleExtra("density", 7850.0)
        price = intent.getLongExtra("price", 35000L)

        binding.competitorPlanEditText.doAfterTextChanged {
            scheduleAnalysis(it?.toString().orEmpty())
        }

        binding.ctaButton.setOnClickListener {
            if (parsedDemandsText.isNotEmpty()) {
                val resultIntent = Intent().apply {
                    putExtra("imported_text", parsedDemandsText)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        }
    }

    private fun scheduleAnalysis(input: String) {
        solveJob?.cancel()
        solveJob = lifecycleScope.launch {
            delay(400) // debounce
            analyzePlan(input)
        }
    }

    private suspend fun analyzePlan(input: String) {
        val lines = input.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            binding.comparisonCard.isVisible = false
            binding.ctaButton.isVisible = false
            binding.errorTextView.isVisible = false
            return
        }

        binding.progressBar.isVisible = true
        binding.errorTextView.isVisible = false

        try {
            val parseResult = withContext(Dispatchers.Default) {
                val allPieces = mutableListOf<Int>()
                var competitorTotalWasteLength = 0L
                var competitorTotalStockLength = 0L

                lines.forEach { line ->
                    val pieces = parseLinePieces(line)
                    if (pieces.isNotEmpty()) {
                        allPieces.addAll(pieces.toList())
                        val piecesSum = pieces.sum()
                        val piecesKerf = (pieces.size - 1).coerceAtLeast(0) * kerf
                        val usedLength = piecesSum + piecesKerf
                        val waste = (stockLength - usedLength).coerceAtLeast(0)

                        competitorTotalWasteLength += waste
                        competitorTotalStockLength += stockLength
                    }
                }
                Triple(allPieces, competitorTotalWasteLength, competitorTotalStockLength)
            }

            val allPieces = parseResult.first
            val competitorTotalWasteLength = parseResult.second
            val competitorTotalStockLength = parseResult.third

            if (allPieces.isEmpty() || competitorTotalStockLength == 0L) {
                binding.progressBar.isVisible = false
                binding.comparisonCard.isVisible = false
                binding.ctaButton.isVisible = false
                return
            }

            // Group pieces into demand inputs
            val groupedDemands = allPieces.groupBy { it }.map { (length, list) ->
                DemandInput(length, list.size)
            }.sortedByDescending { it.lengthMm }

            // Store formatted text for CTA import
            parsedDemandsText = groupedDemands.joinToString("\n") { "${it.lengthMm} ${it.quantity}" }

            // Solve using CutMize solver
            val optimalPlan = withContext(Dispatchers.Default) {
                NativeSolver.solveCuttingPlan(
                    kerfMm = kerf,
                    stockLengthsMm = intArrayOf(stockLength),
                    lengthsMm = groupedDemands.map { it.lengthMm }.toIntArray(),
                    quantities = groupedDemands.map { it.quantity }.toIntArray()
                )
            }

            // Calculate metrics
            val competitorWastePercent = (competitorTotalWasteLength.toDouble() / competitorTotalStockLength.toDouble()) * 100.0
            val optimalWastePercent = (optimalPlan.totalWasteMm.toDouble() / (optimalPlan.binCount * stockLength).toDouble().coerceAtLeast(1.0)) * 100.0

            val competitorWasteKg = SteelWeightCalculator.weightKg(competitorTotalWasteLength, diameter, density)
            val optimalWasteKg = SteelWeightCalculator.weightKg(optimalPlan.totalWasteMm, diameter, density)

            val savedWeightKg = maxOf(0.0, competitorWasteKg - optimalWasteKg)
            val moneySavedTomans = (savedWeightKg * price).toLong()

            // Update UI
            binding.progressBar.isVisible = false
            binding.comparisonCard.isVisible = true
            binding.ctaButton.isVisible = true

            binding.compareTextView.text = String.format(
                Locale.US,
                "برنامه رقیب: %.2f%% ضایعات | کات‌میز: %.2f%% ضایعات",
                competitorWastePercent,
                optimalWastePercent
            )

            binding.savingsTextView.text = "صرفه‌جویی: ${formatTomans(moneySavedTomans)} تومان"

        } catch (e: Exception) {
            binding.progressBar.isVisible = false
            binding.errorTextView.isVisible = true
            binding.errorTextView.text = "خطا در تجزیه و تحلیل برنامه برش: ${e.message}"
        }
    }

    private fun parseLinePieces(line: String): IntArray {
        val normalized = normalizeDigits(line)
        val parts = normalized.split("+")
        return parts.mapNotNull { part ->
            val digitMatch = Regex("""\d+""").find(part)
            digitMatch?.value?.toIntOrNull()
        }.toIntArray()
    }

    private fun normalizeDigits(input: String): String {
        return input.map { c ->
            when (c) {
                in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
                in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
                else -> c
            }
        }.joinToString("")
    }

    private fun formatTomans(value: Long): String {
        return DecimalFormat("#,###").format(value)
    }
}
