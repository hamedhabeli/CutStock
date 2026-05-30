package com.example.cutstock.domain

import com.example.cutstock.data.DemandInput
import com.example.cutstock.data.ProjectRepository
import com.example.cutstock.nativecore.CuttingPlan
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.InputStream
import java.io.OutputStream

data class ProjectBackup(
    val version: Int = ProjectBackupManager.BACKUP_VERSION,
    val name: String,
    val stockLengthMm: Int,
    val kerfMm: Int = 3,
    val diameterMm: Int = 16,
    val pricePerKgTomans: Long = 35_000L,
    val steelDensityKgM3: Double = 7850.0,
    val stockLengthsMm: List<Int> = listOf(12_000),
    val demands: List<DemandInput>,
    val cuttingPlan: CuttingPlan?,
    val exportedAtMillis: Long = System.currentTimeMillis()
)

class ProjectBackupManager(
    private val repository: ProjectRepository
) {
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()

    suspend fun exportProject(projectId: Long, output: OutputStream) {
        val snapshot = repository.getProjectSnapshot(projectId)
            ?: error("Project not found: $projectId")

        val project = snapshot.project
        val backup = ProjectBackup(
            name = project.name,
            stockLengthMm = project.stockLengthMm,
            kerfMm = project.kerfMm,
            diameterMm = project.diameterMm,
            pricePerKgTomans = project.pricePerKgTomans,
            steelDensityKgM3 = project.steelDensityKgM3,
            stockLengthsMm = project.stockLengthsMm,
            demands = snapshot.demands.map { DemandInput(it.lengthMm, it.quantity) },
            cuttingPlan = project.cuttingPlan
        )
        output.bufferedWriter().use { writer ->
            writer.write(gson.toJson(backup))
        }
    }

    suspend fun importProject(input: InputStream): Long {
        val backup = gson.fromJson(input.bufferedReader().readText(), ProjectBackup::class.java)
        require(backup.version in 1..BACKUP_VERSION) { "Unsupported backup version" }
        require(backup.name.isNotBlank()) { "Backup name is empty" }
        require(backup.stockLengthMm > 0) { "Invalid stock length" }

        val stockLengths = when {
            backup.stockLengthsMm.isNotEmpty() -> backup.stockLengthsMm
            else -> listOf(backup.stockLengthMm)
        }

        val projectId = repository.createProject(
            name = backup.name,
            defaults = com.example.cutstock.data.WorkshopDefaults(
                kerfMm = backup.kerfMm,
                diameterMm = backup.diameterMm,
                pricePerKgTomans = backup.pricePerKgTomans,
                steelDensityKgM3 = backup.steelDensityKgM3,
                stockLengthsMm = stockLengths
            )
        )

        if (backup.demands.isNotEmpty()) {
            repository.replaceDemands(projectId, backup.demands)
        }
        backup.cuttingPlan?.let { plan ->
            repository.saveCuttingPlan(projectId, plan)
        }
        return projectId
    }

    companion object {
        const val BACKUP_VERSION = 2
        const val MIME_TYPE = "application/json"
    }
}