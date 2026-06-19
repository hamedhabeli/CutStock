package com.example.cutstock.presentation

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.cutstock.databinding.ActivityExcelImportBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook

class ExcelImportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExcelImportBinding
    private var activeSheet: Sheet? = null
    private var activeWorkbook: XSSFWorkbook? = null

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            loadExcelFile(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExcelImportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Show back button on toolbar if we had one, or let's just make sure they can exit.
        binding.selectFileButton.setOnClickListener {
            binding.errorTextView.isVisible = false
            pickFileLauncher.launch(
                arrayOf(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-excel"
                )
            )
        }

        binding.importButton.setOnClickListener {
            doImport()
        }

        val textWatcher = binding.startRowEditText.doAfterTextChanged {
            updatePreview()
        }

        val itemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updatePreview()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.lengthColumnSpinner.onItemSelectedListener = itemSelectedListener
        binding.quantityColumnSpinner.onItemSelectedListener = itemSelectedListener
    }

    private fun loadExcelFile(uri: Uri) {
        binding.progressBar.isVisible = true
        binding.optionsContainer.isVisible = false
        binding.errorTextView.isVisible = false

        val fileName = uri.path?.substringAfterLast('/') ?: "Excel File"
        binding.selectedFileNameTextView.text = fileName

        lifecycleScope.launch {
            try {
                val workbook = withContext(Dispatchers.IO) {
                    val inputStream = contentResolver.openInputStream(uri)
                    XSSFWorkbook(inputStream)
                }
                activeWorkbook = workbook
                val sheet = workbook.getSheetAt(0)
                activeSheet = sheet

                val maxCols = getSheetMaxColumns(sheet)
                val columns = (0 until maxCols).map { index ->
                    val letter = ('A' + (index % 26)).toString()
                    val headerVal = sheet.getRow(0)?.getCell(index)?.let { getCellStringValue(it) }?.trim()
                    if (!headerVal.isNullOrBlank()) {
                        "ستون $letter ($headerVal)"
                    } else {
                        "ستون $letter"
                    }
                }

                val adapter = ArrayAdapter(
                    this@ExcelImportActivity,
                    android.R.layout.simple_spinner_item,
                    columns
                ).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }

                binding.lengthColumnSpinner.adapter = adapter
                binding.quantityColumnSpinner.adapter = adapter

                // Auto select columns if possible (Length at 0, Quantity at 1)
                if (maxCols > 0) binding.lengthColumnSpinner.setSelection(0)
                if (maxCols > 1) binding.quantityColumnSpinner.setSelection(1)

                binding.progressBar.isVisible = false
                binding.optionsContainer.isVisible = true
                updatePreview()

            } catch (e: Exception) {
                binding.progressBar.isVisible = false
                binding.errorTextView.isVisible = true
                binding.errorTextView.text = "خطا در خواندن فایل: ${e.message}"
            }
        }
    }

    private fun getSheetMaxColumns(sheet: Sheet): Int {
        var maxCols = 1
        for (i in 0..minOf(sheet.lastRowNum, 50)) {
            val row = sheet.getRow(i) ?: continue
            maxCols = maxOf(maxCols, row.lastCellNum.toInt())
        }
        return maxCols
    }

    private fun updatePreview() {
        binding.previewContainer.removeAllViews()
        val sheet = activeSheet ?: return
        val lengthColIdx = binding.lengthColumnSpinner.selectedItemPosition
        val qtyColIdx = binding.quantityColumnSpinner.selectedItemPosition
        val startRowStr = binding.startRowEditText.text?.toString() ?: "2"
        val startRow = (startRowStr.toIntOrNull() ?: 2) - 1

        if (lengthColIdx < 0 || qtyColIdx < 0 || startRow < 0) return

        val endRow = minOf(sheet.lastRowNum, startRow + 4)
        for (i in startRow..endRow) {
            val row = sheet.getRow(i) ?: continue
            val lenCell = row.getCell(lengthColIdx)
            val qtyCell = row.getCell(qtyColIdx)

            val lenVal = getCellStringValue(lenCell).trim()
            val qtyVal = getCellStringValue(qtyCell).trim()

            val itemRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 4, 0, 4)
            }
            val label = TextView(this).apply {
                text = "ردیف ${i + 1}:  "
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val value = TextView(this).apply {
                text = "طول: $lenVal | تعداد: $qtyVal"
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
            itemRow.addView(label)
            itemRow.addView(value)
            binding.previewContainer.addView(itemRow)
        }
    }

    private fun doImport() {
        val sheet = activeSheet ?: return
        val lengthColIdx = binding.lengthColumnSpinner.selectedItemPosition
        val qtyColIdx = binding.quantityColumnSpinner.selectedItemPosition
        val startRowStr = binding.startRowEditText.text?.toString() ?: "2"
        val startRow = (startRowStr.toIntOrNull() ?: 2) - 1

        if (lengthColIdx < 0 || qtyColIdx < 0 || startRow < 0) return

        val importedLines = mutableListOf<String>()
        for (i in startRow..sheet.lastRowNum) {
            val row = sheet.getRow(i) ?: continue
            val lenCell = row.getCell(lengthColIdx)
            val qtyCell = row.getCell(qtyColIdx)

            val lenVal = getCellStringValue(lenCell).trim()
            val qtyVal = getCellStringValue(qtyCell).trim()

            // Try to parse as integer
            val length = lenVal.toIntOrNull() ?: lenVal.toDoubleOrNull()?.toInt()
            val quantity = qtyVal.toIntOrNull() ?: qtyVal.toDoubleOrNull()?.toInt()

            if (length != null && quantity != null && length > 0 && quantity > 0) {
                importedLines.add("$length $quantity")
            }
        }

        if (importedLines.isEmpty()) {
            binding.errorTextView.isVisible = true
            binding.errorTextView.text = "هیچ داده معتبری برای وارد کردن پیدا نشد."
            return
        }

        val formattedText = importedLines.joinToString("\n")
        val resultIntent = Intent().apply {
            putExtra("imported_text", formattedText)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun getCellStringValue(cell: Cell?): String {
        if (cell == null) return ""
        return try {
            when (cell.cellType) {
                CellType.NUMERIC -> {
                    val doubleVal = cell.numericCellValue
                    if (doubleVal - doubleVal.toLong() == 0.0) {
                        doubleVal.toLong().toString()
                    } else {
                        doubleVal.toString()
                    }
                }
                CellType.STRING -> cell.stringCellValue.orEmpty()
                CellType.BOOLEAN -> cell.booleanCellValue.toString()
                CellType.FORMULA -> {
                    try {
                        cell.stringCellValue
                    } catch (e: Exception) {
                        try {
                            cell.numericCellValue.toString()
                        } catch (ex: Exception) {
                            cell.toString()
                        }
                    }
                }
                else -> cell.toString()
            }
        } catch (e: Exception) {
            cell.toString()
        }
    }

    override fun onDestroy() {
        try {
            activeWorkbook?.close()
        } catch (e: Exception) {}
        super.onDestroy()
    }
}
