package com.example.cutstock.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cutstock.data.ProjectRepository
import com.example.cutstock.data.UserPreferences
import com.example.cutstock.domain.FreemiumPolicy
import com.example.cutstock.domain.billing.BillingManager
import com.example.cutstock.domain.billing.BillingResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ProjectListItem(
    val id: Long,
    val name: String,
    val maxStockLengthMm: Int,
    val demandCount: Int,
    val hasPlan: Boolean,
    val updatedAtMillis: Long
)

sealed interface ProjectListUiState {
    data object Loading : ProjectListUiState
    data class Success(
        val projects: List<ProjectListItem>,
        val isPro: Boolean
    ) : ProjectListUiState
}

sealed interface ProjectListEvent {
    data class OpenProject(val projectId: Long) : ProjectListEvent
    data class ShowMessage(val message: String) : ProjectListEvent
    data object ShowUpgrade : ProjectListEvent
}

class ProjectListViewModel(
    private val repository: ProjectRepository,
    private val userPreferences: UserPreferences,
    private val billingManager: BillingManager
) : ViewModel() {
    private val _events = MutableSharedFlow<ProjectListEvent>()
    val events: SharedFlow<ProjectListEvent> = _events.asSharedFlow()

    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating.asStateFlow()

    private val _uiState = MutableStateFlow<ProjectListUiState>(ProjectListUiState.Loading)
    val uiState: StateFlow<ProjectListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.observeProjects(),
                repository.observeDemandCountsMap(),
                userPreferences.isPro
            ) { projects, demandCounts, isPro ->
                val countsByProjectId = demandCounts.associate { it.projectId to it.demandCount }
                val items = projects.map { project ->
                    ProjectListItem(
                        id = project.id,
                        name = project.name,
                        maxStockLengthMm = project.stockLengthsMm.maxOrNull() ?: 12_000,
                        demandCount = countsByProjectId[project.id] ?: 0,
                        hasPlan = project.cuttingPlan != null,
                        updatedAtMillis = project.updatedAtMillis
                    )
                }
                ProjectListUiState.Success(items, isPro)
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun createProject(defaultName: String) {
        viewModelScope.launch {
            _isCreating.value = true
            try {
                val isPro = userPreferences.isPro.first()
                if (!isPro) {
                    val count = repository.countProjects()
                    if (count >= FreemiumPolicy.FREE_MAX_PROJECTS) {
                        _events.emit(ProjectListEvent.ShowUpgrade)
                        _events.emit(
                            ProjectListEvent.ShowMessage(
                                "در نسخه رایگان حداکثر ${FreemiumPolicy.FREE_MAX_PROJECTS} پروژه مجاز است."
                            )
                        )
                        return@launch
                    }
                }

                val defaults = userPreferences.workshopDefaults.first()
                val projectId = repository.createProject(defaultName, defaults)
                _events.emit(ProjectListEvent.OpenProject(projectId))
            } catch (t: Throwable) {
                _events.emit(ProjectListEvent.ShowMessage(t.message ?: "ایجاد پروژه ناموفق بود."))
            } finally {
                _isCreating.value = false
            }
        }
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            try {
                repository.deleteProject(projectId)
            } catch (t: Throwable) {
                _events.emit(ProjectListEvent.ShowMessage(t.message ?: "حذف پروژه ناموفق بود."))
            }
        }
    }

    fun openProject(projectId: Long) {
        viewModelScope.launch {
            _events.emit(ProjectListEvent.OpenProject(projectId))
        }
    }

    fun purchasePro() {
        viewModelScope.launch {
            when (val result = billingManager.purchasePro()) {
                BillingResult.Success -> _events.emit(ProjectListEvent.ShowMessage("نسخه حرفه‌ای فعال شد."))
                is BillingResult.Error -> _events.emit(ProjectListEvent.ShowMessage(result.message))
                BillingResult.Cancelled -> Unit
            }
        }
    }

    fun toggleDebugPro(enabled: Boolean) {
        viewModelScope.launch {
            billingManager.setProForDebug(enabled)
        }
    }

    class Factory(
        private val repository: ProjectRepository,
        private val userPreferences: UserPreferences,
        private val billingManager: BillingManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ProjectListViewModel::class.java))
            return ProjectListViewModel(repository, userPreferences, billingManager) as T
        }
    }
}
