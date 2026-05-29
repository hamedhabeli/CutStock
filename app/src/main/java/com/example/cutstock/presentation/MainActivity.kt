package com.example.cutstock.presentation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cutstock.BuildConfig
import com.example.cutstock.CutStockApplication
import com.example.cutstock.R
import com.example.cutstock.data.ProjectSettings
import com.example.cutstock.databinding.ActivityMainBinding
import com.example.cutstock.domain.FreemiumPolicy
import com.example.cutstock.domain.ProjectBackupManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.File
import java.text.DecimalFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var userEditedBulkInput = false
    private var upgradeShownForSession = false

    private val cuttingPlanAdapter = CuttingPlanAdapter()

    private val container by lazy { application as CutStockApplication }

    private val viewModel: ProjectViewModel by viewModels {
        container.projectViewModelFactory()
    }

    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(ProjectBackupManager.MIME_TYPE)
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        contentResolver.openOutputStream(uri)?.let { stream ->
            viewModel.exportBackup(stream)
        }
    }

    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        contentResolver.openInputStream(uri)?.let { stream ->
            viewModel.importBackup(stream) { projectId ->
                viewModel.bindProject(projectId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.cuttingPlanRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.cuttingPlanRecyclerView.adapter = cuttingPlanAdapter

        binding.bulkInputEditText.doAfterTextChanged {
            userEditedBulkInput = binding.bulkInputEditText.isFocused
        }

        binding.solveButton.setOnClickListener {
            userEditedBulkInput = false
            viewModel.solveFromBulkInput(binding.bulkInputEditText.text?.toString().orEmpty())
        }

        binding.settingsButton.setOnClickListener { showSettingsDialog() }

        val projectId = intent.getLongExtra(EXTRA_PROJECT_ID, -1L)
        if (projectId > 0L) {
            viewModel.bindProject(projectId)
        } else {
            finish()
            return
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { renderState(it) }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { handleEvent(it) }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        if (BuildConfig.DEBUG) {
            menu.add(0, MENU_DEBUG_PRO, 0, R.string.debug_pro_toggle)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.menu_export_pdf -> {
            viewModel.exportPdf()
            true
        }
        R.id.menu_export_backup -> {
            exportBackupLauncher.launch(getString(R.string.backup_file_name))
            true
        }
        R.id.menu_import_backup -> {
            importBackupLauncher.launch(arrayOf(ProjectBackupManager.MIME_TYPE, "application/*"))
            true
        }
        R.id.menu_help -> {
            showHelpDialog()
            true
        }
        R.id.menu_about -> {
            showAboutDialog()
            true
        }
        R.id.menu_upgrade -> {
            showUpgradeDialog()
            true
        }
        MENU_DEBUG_PRO -> {
            lifecycleScope.launch {
                val state = viewModel.uiState.value as? ProjectUiState.Success
                container.billingManager.setProForDebug(state?.isPro != true)
            }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun renderState(state: ProjectUiState) {
        when (state) {
            ProjectUiState.Idle -> {
                binding.progressBar.isVisible = false
                binding.errorTextView.isVisible = false
                binding.solveButton.isEnabled = true
            }

            ProjectUiState.Loading -> {
                binding.progressBar.isVisible = true
                binding.errorTextView.isVisible = false
                binding.solveButton.isEnabled = false
            }

            is ProjectUiState.Error -> {
                binding.progressBar.isVisible = false
                binding.errorTextView.isVisible = true
                binding.errorTextView.text = state.message
                binding.solveButton.isEnabled = true
            }

            is ProjectUiState.Success -> {
                binding.progressBar.isVisible = false
                binding.errorTextView.isVisible = false
                binding.solveButton.isEnabled = true

                binding.toolbar.title = state.projectName
                binding.projectTitleTextView.text = state.projectName

                if (!userEditedBulkInput && binding.bulkInputEditText.text?.toString() != state.bulkInputText) {
                    binding.bulkInputEditText.setText(state.bulkInputText)
                }

                val sales = state.sales
                binding.demandsCountTextView.text =
                    getString(R.string.demands_count_label, state.demandCount)
                binding.totalPiecesTextView.text =
                    getString(R.string.total_pieces_format, state.totalPieces)

                if (sales != null) {
                    binding.barsNeededTextView.text =
                        getString(R.string.bars_needed_format, sales.barsNeeded)
                    binding.wastePercentTextView.text =
                        getString(R.string.waste_percent_format, formatPercent(sales.wastePercent))
                    binding.moneySavedTextView.text =
                        getString(R.string.money_saved_format, formatTomans(sales.moneySavedTomans))
                    binding.naiveWasteTextView.text = getString(
                        R.string.naive_waste_format,
                        getString(R.string.naive_waste_label),
                        formatKg(sales.naiveWasteKg)
                    )
                    binding.actualWasteTextView.text = getString(
                        R.string.naive_waste_format,
                        getString(R.string.actual_waste_label),
                        formatKg(sales.actualWasteKg)
                    )
                    binding.savedWasteTextView.text = getString(
                        R.string.naive_waste_format,
                        getString(R.string.saved_waste_label),
                        formatKg(sales.savedWasteKg)
                    )
                } else {
                    binding.barsNeededTextView.text = getString(R.string.no_plan_yet)
                    binding.wastePercentTextView.text = ""
                    binding.moneySavedTextView.text = ""
                    binding.naiveWasteTextView.text = ""
                    binding.actualWasteTextView.text = ""
                    binding.savedWasteTextView.text = ""
                }

                binding.cuttingPlanSummaryTextView.text = if (state.cuttingPlan == null) {
                    getString(R.string.no_plan_yet)
                } else {
                    getString(R.string.plan_loaded)
                }

                val plan = state.cuttingPlan
                if (plan != null && plan.bins.isNotEmpty()) {
                    binding.emptyPlanTextView.isVisible = false
                    binding.cuttingPlanRecyclerView.isVisible = true
                    cuttingPlanAdapter.submitList(
                        plan.bins.mapIndexed { index, bin ->
                            CuttingBinItem(index = index + 1, stockLengthMm = bin.stockLengthMm, bin = bin)
                        }
                    )
                } else {
                    binding.emptyPlanTextView.isVisible = true
                    binding.cuttingPlanRecyclerView.isVisible = false
                    cuttingPlanAdapter.submitList(emptyList())
                }

                renderAdvancedReport(state)

                if (!state.isPro &&
                    state.demandCount > FreemiumPolicy.FREE_MAX_DEMAND_TYPES &&
                    !upgradeShownForSession
                ) {
                    upgradeShownForSession = true
                    showUpgradeDialog()
                }
            }
        }
    }

    private fun renderAdvancedReport(state: ProjectUiState.Success) {
        val sales = state.sales
        if (!state.isPro) {
            binding.advancedLockedTextView.isVisible = true
            binding.avgUtilizationTextView.isVisible = false
            binding.largestWasteTextView.isVisible = false
            binding.smallestWasteTextView.isVisible = false
            return
        }

        binding.advancedLockedTextView.isVisible = false
        if (sales == null) {
            binding.avgUtilizationTextView.isVisible = false
            binding.largestWasteTextView.isVisible = false
            binding.smallestWasteTextView.isVisible = false
            return
        }

        binding.avgUtilizationTextView.isVisible = true
        binding.largestWasteTextView.isVisible = true
        binding.smallestWasteTextView.isVisible = true
        binding.avgUtilizationTextView.text = getString(
            R.string.avg_utilization_format,
            getString(R.string.avg_utilization_label),
            formatPercent(sales.averageUtilizationPercent)
        )
        binding.largestWasteTextView.text = getString(
            R.string.naive_waste_format,
            getString(R.string.largest_waste_label),
            "${sales.largestWasteMm} mm"
        )
        binding.smallestWasteTextView.text = getString(
            R.string.naive_waste_format,
            getString(R.string.smallest_waste_label),
            "${sales.smallestWasteMm} mm"
        )
    }

    private fun showSettingsDialog() {
        val state = viewModel.uiState.value as? ProjectUiState.Success ?: return
        val view = layoutInflater.inflate(R.layout.dialog_project_settings, null, false)
        view.findViewById<EditText>(R.id.projectNameEditText).setText(state.settings.name)
        view.findViewById<EditText>(R.id.kerfEditText).setText(state.settings.kerfMm.toString())
        view.findViewById<EditText>(R.id.diameterEditText).setText(state.settings.diameterMm.toString())
        view.findViewById<EditText>(R.id.priceEditText).setText(state.settings.pricePerKgTomans.toString())
        view.findViewById<EditText>(R.id.stockLengthsEditText).setText(
            state.settings.stockLengthsMm.joinToString("\n")
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = view.findViewById<EditText>(R.id.projectNameEditText).text?.toString().orEmpty()
                val kerf = view.findViewById<EditText>(R.id.kerfEditText).text?.toString()?.toIntOrNull() ?: 0
                val diameter = view.findViewById<EditText>(R.id.diameterEditText).text?.toString()?.toIntOrNull()
                    ?: 16
                val price = view.findViewById<EditText>(R.id.priceEditText).text?.toString()?.toLongOrNull()
                    ?: 35_000L
                val stocks = view.findViewById<EditText>(R.id.stockLengthsEditText).text?.toString()
                    .orEmpty()
                    .lines()
                    .mapNotNull { it.trim().toIntOrNull() }
                    .filter { it > 0 }
                    .distinct()
                    .sortedDescending()

                if (name.isBlank() || stocks.isEmpty()) {
                    Snackbar.make(binding.root, R.string.settings_invalid, Snackbar.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                viewModel.updateProjectSettings(
                    ProjectSettings(
                        name = name,
                        kerfMm = kerf,
                        diameterMm = diameter,
                        pricePerKgTomans = price,
                        steelDensityKgM3 = state.settings.steelDensityKgM3,
                        stockLengthsMm = stocks
                    )
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun handleEvent(event: ProjectEvent) {
        when (event) {
            is ProjectEvent.ShowMessage ->
                Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()

            ProjectEvent.ShowUpgrade -> showUpgradeDialog()

            is ProjectEvent.SharePdf -> sharePdf(event.file)
        }
    }

    private fun sharePdf(file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            this,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_pdf_title)))
    }

    private fun showHelpDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.menu_help)
            .setMessage(R.string.help_message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.menu_about)
            .setMessage(R.string.about_message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showUpgradeDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_upgrade, null, false)
        dialog.setContentView(view)
        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.purchaseButton)
            .setOnClickListener {
                viewModel.purchasePro()
                dialog.dismiss()
            }
        dialog.show()
    }

    private fun formatPercent(value: Double): String = String.format(Locale.US, "%.2f%%", value)

    private fun formatKg(value: Double): String = String.format(Locale.US, "%.2f kg", value)

    private fun formatTomans(value: Long): String = "${DecimalFormat("#,###").format(value)} تومان"

    companion object {
        const val EXTRA_PROJECT_ID = "extra_project_id"
        private const val MENU_DEBUG_PRO = 1001
    }
}
