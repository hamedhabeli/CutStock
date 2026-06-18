package com.example.cutstock.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cutstock.data.DemandInput
import com.example.cutstock.data.ProjectRepository
import com.example.cutstock.data.ProjectSettings
import com.example.cutstock.data.UserPreferences
import com.example.cutstock.domain.BulkInputParser
import com.example.cutstock.domain.FreemiumPolicy
import com.example.cutstock.domain.PdfReportGenerator
import com.example.cutstock.domain.ProjectBackupManager
import com.example.cutstock.domain.SalesCalculator
import com.example.cutstock.domain.SalesSummary
import com.example.cutstock.domain.billing.BillingManager
import com.example.cutstock.domain.billing.BillingResult
import com.example.cutstock.nativecore.CuttingPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

sealed interface ProjectUiState {
    data object Idle : ProjectUiState
    data object Loading : ProjectUiState

    data class Success(
        val projectId: Long,
        val projectName: String,
        val settings: ProjectSettings,
        val demandCount: Int,
        val totalPieces: Int,
        val bulkInputText: String,
        val cuttingPlan: CuttingPlan?,
        val sales: SalesSummary?,
        val isPro: Boolean,
        val isPlanStale: Boolean
    ) : ProjectUiState

    data class Error(val message: String) : ProjectUiState
}

sealed interface ProjectEvent {
    data class ShowMessage(val message: String) : ProjectEvent
    data object ShowUpgrade : ProjectEvent
    data class SharePdf(val file: File) : ProjectEvent
}

class ProjectViewModel(
    private val repository: ProjectRepository,
    private val userPreferences: UserPreferences,
    private val pdfReportGenerator: PdfReportGenerator,
    private val backupManager: ProjectBackupManager,
    private val billingManager: BillingManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProjectUiState>(ProjectUiState.Idle)
    val uiState: StateFlow<ProjectUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ProjectEvent>()
    val events: SharedFlow<ProjectEvent> = _events.asSharedFlow()

    private var currentProjectId: Long? = null
    private var observeJob: kotlinx.coroutines.Job? = null
    private var isPlanStale = false

    fun bindProject(projectId: Long) {
        currentProjectId = projectId
        isPlanStale = false
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repository.observeProjectWithDemands(projectId).collectLatest { snapshot ->
                if (snapshot == null) {
                    _uiState.value = ProjectUiState.Error("پروژه یافت نشد.")
                    return@collectLatest
                }

                val isPro = userPreferences.isPro.first()
                val project = snapshot.project
                val demands = snapshot.demands.map { DemandInput(it.lengthMm, it.quantity) }
                val settings = ProjectSettings(
                    name = project.name,
                    kerfMm = project.kerfMm,
                    diameterMm = project.diameterMm,
                    pricePerKgTomans = project.pricePerKgTomans,
                    steelDensityKgM3 = project.steelDensityKgM3,
                    stockLengthsMm = project.stockLengthsMm
                )
                val sales = snapshot.project.cuttingPlan?.let { plan ->
                    withContext(Dispatchers.Default) {
                        SalesCalculator.calculate(plan, project, demands)
                    }
                }

                _uiState.value = ProjectUiState.Success(
                    projectId = snapshot.project.id,
                    projectName = project.name,
                    settings = settings,
                    demandCount = demands.size,
                    totalPieces = demands.sumOf { it.quantity },
                    bulkInputText = BulkInputParser.format(demands),
                    cuttingPlan = snapshot.project.cuttingPlan,
                    sales = sales,
                    isPro = isPro,
                    isPlanStale = isPlanStale
                )
            }
        }
    }

    fun solveFromBulkInput(rawBulkInput: String) {
        val projectId = currentProjectId ?: run {
            _uiState.value = ProjectUiState.Error("ابتدا یک پروژه باز کنید.")
            return
        }

        viewModelScope.launch {
            val parseResult = withContext(Dispatchers.Default) {
                BulkInputParser.parse(rawBulkInput)
            }
            val parsedDemands = parseResult.demands

            if (parsedDemands.isEmpty()) {
                _uiState.value = ProjectUiState.Error("هیچ خط معتبری یافت نشد.")
                return@launch
            }

            if (parseResult.ignoredLines > 0) {
                _events.emit(
                    ProjectEvent.ShowMessage(
                        "${parseResult.ignoredLines} خط نامعتبر نادیده گرفته شد."
                    )
                )
            }

            val isPro = userPreferences.isPro.first()
            val solveCount = userPreferences.solveCount.first()
            if (!isPro && solveCount >= FreemiumPolicy.FREE_MAX_SOLVES) {
                _events.emit(ProjectEvent.ShowUpgrade)
                _events.emit(
                    ProjectEvent.ShowMessage(
                        "${FreemiumPolicy.FREE_MAX_SOLVES} بار حل برش رایگان استفاده شد. برای ادامه ارتقا دهید."
                    )
                )
                return@launch
            }

            _uiState.value = ProjectUiState.Loading

            try {
                withContext(Dispatchers.IO) {
                    repository.replaceDemands(projectId, parsedDemands)
                }
                withContext(Dispatchers.IO) {
                    repository.solveProject(projectId)
                }
                isPlanStale = false
                if (!isPro) {
                    userPreferences.incrementSolveCount()
                }
            } catch (t: Throwable) {
                _uiState.value = ProjectUiState.Error(t.message ?: "حل برش ناموفق بود.")
            }
        }
    }

    fun updateProjectSettings(settings: ProjectSettings) {
        val projectId = currentProjectId ?: return

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.updateProjectSettings(projectId, settings)
                }
                isPlanStale = true
                _events.emit(ProjectEvent.ShowMessage("تنظیمات ذخیره شد. برای به‌روزرسانی نتیجه، «حل برش» را دوباره اجرا کنید."))
            } catch (t: Throwable) {
                _events.emit(ProjectEvent.ShowMessage(t.message ?: "ذخیره تنظیمات ناموفق بود."))
            }
        }
    }

    fun purchasePro() {
        viewModelScope.launch {
            when (val result = billingManager.purchasePro()) {
                BillingResult.Success ->
                    _events.emit(ProjectEvent.ShowMessage("نسخه حرفه‌ای فعال شد."))
                is BillingResult.Error ->
                    _events.emit(ProjectEvent.ShowMessage(result.message))
                BillingResult.Cancelled -> Unit
            }
        }
    }

    fun exportPdf() {
        viewModelScope.launch {
            val state = _uiState.value as? ProjectUiState.Success ?: return@launch
            if (!state.isPro) {
                _events.emit(ProjectEvent.ShowUpgrade)
                _events.emit(ProjectEvent.ShowMessage("خروجی PDF فقط در نسخه حرفه‌ای فعال است."))
                return@launch
            }

            val plan = state.cuttingPlan
            val sales = state.sales
            if (plan == null || sales == null) {
                _events.emit(ProjectEvent.ShowMessage("ابتدا برنامه برش را محاسبه کنید."))
                return@launch
            }

            try {
                val snapshot = withContext(Dispatchers.IO) {
                    repository.getProjectSnapshot(state.projectId)
                } ?: return@launch

                val demands = snapshot.demands.map { DemandInput(it.lengthMm, it.quantity) }
                val file = withContext(Dispatchers.IO) {
                    pdfReportGenerator.generate(
                        projectName = state.projectName,
                        settings = state.settings,
                        demands = demands,
                        plan = plan,
                        sales = sales
                    )
                }
                _events.emit(ProjectEvent.SharePdf(file))
            } catch (t: Throwable) {
                _events.emit(ProjectEvent.ShowMessage(t.message ?: "ساخت PDF ناموفق بود."))
            }
        }
    }

    fun exportBackup(output: OutputStream) {
        viewModelScope.launch {
            val state = _uiState.value as? ProjectUiState.Success ?: return@launch
            if (!state.isPro) {
                _events.emit(ProjectEvent.ShowUpgrade)
                _events.emit(ProjectEvent.ShowMessage("پشتیبان‌گیری فقط در نسخه حرفه‌ای فعال است."))
                return@launch
            }

            try {
                withContext(Dispatchers.IO) {
                    backupManager.exportProject(state.projectId, output)
                }
                _events.emit(ProjectEvent.ShowMessage("پشتیبان با موفقیت ذخیره شد."))
            } catch (t: Throwable) {
                _events.emit(ProjectEvent.ShowMessage(t.message ?: "پشتیبان‌گیری ناموفق بود."))
            }
        }
    }

    fun importBackup(input: InputStream, onImported: (Long) -> Unit) {
        viewModelScope.launch {
            val isPro = userPreferences.isPro.first()
            if (!isPro) {
                _events.emit(ProjectEvent.ShowUpgrade)
                _events.emit(ProjectEvent.ShowMessage("بازیابی پشتیبان فقط در نسخه حرفه‌ای فعال است."))
                return@launch
            }

            try {
                val projectId = withContext(Dispatchers.IO) {
                    backupManager.importProject(input)
                }
                _events.emit(ProjectEvent.ShowMessage("پروژه با موفقیت بازیابی شد."))
                onImported(projectId)
            } catch (t: Throwable) {
                _events.emit(ProjectEvent.ShowMessage(t.message ?: "بازیابی ناموفق بود."))
            }
        }
    }

    override fun onCleared() {
        observeJob?.cancel()
        super.onCleared()
    }

    class Factory(
        private val repository: ProjectRepository,
        private val userPreferences: UserPreferences,
        private val pdfReportGenerator: PdfReportGenerator,
        private val backupManager: ProjectBackupManager,
        private val billingManager: BillingManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ProjectViewModel::class.java))
            return ProjectViewModel(
                repository,
                userPreferences,
                pdfReportGenerator,
                backupManager,
                billingManager
            ) as T
        }
    }
}
