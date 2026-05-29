package com.example.cutstock.presentation

import androidx.lifecycle.ViewModelProvider
import com.example.cutstock.data.ProjectRepository
import com.example.cutstock.data.UserPreferences
import com.example.cutstock.domain.ProjectBackupManager
import com.example.cutstock.domain.PdfReportGenerator
import com.example.cutstock.domain.billing.BillingManager

interface AppContainer {
    val projectRepository: ProjectRepository
    val userPreferences: UserPreferences
    val pdfReportGenerator: PdfReportGenerator
    val backupManager: ProjectBackupManager
    val billingManager: BillingManager
    fun projectViewModelFactory(): ViewModelProvider.Factory
    fun projectListViewModelFactory(): ViewModelProvider.Factory
}
