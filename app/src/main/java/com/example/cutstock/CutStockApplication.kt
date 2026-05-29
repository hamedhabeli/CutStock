package com.example.cutstock

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.example.cutstock.BuildConfig
import com.example.cutstock.data.AppDatabase
import com.example.cutstock.data.ProjectRepository
import com.example.cutstock.data.UserPreferences
import com.example.cutstock.domain.ProjectBackupManager
import com.example.cutstock.domain.PdfReportGenerator
import com.example.cutstock.domain.billing.BillingManager
import com.example.cutstock.domain.billing.DebugBillingManager
import com.example.cutstock.domain.billing.StubBillingManager
import com.example.cutstock.presentation.AppContainer
import com.example.cutstock.presentation.ProjectListViewModel
import com.example.cutstock.presentation.ProjectViewModel
class CutStockApplication : Application(), AppContainer {

    private val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "cutstock.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }

    override val projectRepository: ProjectRepository by lazy {
        ProjectRepository(database.projectDao(), database)
    }

    override val userPreferences: UserPreferences by lazy {
        UserPreferences(this)
    }

    override val pdfReportGenerator: PdfReportGenerator by lazy {
        PdfReportGenerator(this)
    }

    override val backupManager: ProjectBackupManager by lazy {
        ProjectBackupManager(projectRepository)
    }

    override val billingManager: BillingManager by lazy {
        if (BuildConfig.DEBUG) {
            DebugBillingManager(userPreferences)
        } else {
            StubBillingManager(userPreferences)
        }
    }

    override fun projectViewModelFactory(): ViewModelProvider.Factory =
        ProjectViewModel.Factory(
            repository = projectRepository,
            userPreferences = userPreferences,
            pdfReportGenerator = pdfReportGenerator,
            backupManager = backupManager,
            billingManager = billingManager
        )

    override fun projectListViewModelFactory(): ViewModelProvider.Factory =
        ProjectListViewModel.Factory(
            repository = projectRepository,
            userPreferences = userPreferences,
            billingManager = billingManager
        )

}
