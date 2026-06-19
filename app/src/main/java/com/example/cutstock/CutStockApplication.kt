package com.example.cutstock

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.FirebaseApp
import androidx.room.Room
import com.example.cutstock.billing.BillingProviderImpl
import com.example.cutstock.data.AppDatabase
import com.example.cutstock.data.ProjectRepository
import com.example.cutstock.data.UserPreferences
import com.example.cutstock.domain.ProjectBackupManager
import com.example.cutstock.domain.PdfReportGenerator
import com.example.cutstock.domain.billing.BillingManager
import com.example.cutstock.presentation.AppContainer
import com.example.cutstock.presentation.ProjectListViewModel
import com.example.cutstock.presentation.ProjectViewModel

class CutStockApplication : Application(), AppContainer {

    companion object {
        lateinit var instance: CutStockApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        FirebaseApp.initializeApp(this)
    }

    private val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "cutstock.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
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
        BillingProviderImpl().create(userPreferences)
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
