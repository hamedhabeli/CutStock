package com.example.cutstock.data

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.example.cutstock.nativecore.CuttingPlan
import com.example.cutstock.nativecore.NativeSolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class DemandInput(
    val lengthMm: Int,
    val quantity: Int
)

class ProjectRepository(
    private val dao: ProjectDao,
    private val database: RoomDatabase
) {
    fun observeProjects(): Flow<List<ProjectEntity>> = dao.observeProjects()

    fun observeProjectById(projectId: Long): Flow<ProjectEntity?> = dao.observeProjectById(projectId)

    fun observeProjectWithDemands(projectId: Long): Flow<ProjectWithDemands?> =
        dao.observeProjectWithDemands(projectId)

    fun observeDemandCountsMap(): Flow<List<DemandCountRow>> = dao.observeDemandCountsMap()

    suspend fun createProject(
        name: String,
        defaults: WorkshopDefaults = WorkshopDefaults()
    ): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val stockLengths = defaults.stockLengthsMm.ifEmpty { listOf(12_000) }

        dao.insertProject(
            ProjectEntity(
                name = name,
                kerfMm = defaults.kerfMm,
                diameterMm = defaults.diameterMm,
                pricePerKgTomans = defaults.pricePerKgTomans,
                steelDensityKgM3 = defaults.steelDensityKgM3,
                stockLengthsMm = stockLengths,
                cuttingPlan = null,
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )
    }

    suspend fun updateProjectSettings(
        projectId: Long,
        settings: ProjectSettings
    ) = withContext(Dispatchers.IO) {
        require(settings.name.isNotBlank()) { "Project name cannot be blank" }
        require(settings.kerfMm >= 0) { "kerfMm must be >= 0" }
        require(settings.diameterMm > 0) { "diameterMm must be > 0" }
        require(settings.stockLengthsMm.isNotEmpty()) { "At least one stock length required" }
        require(settings.stockLengthsMm.all { it > 0 }) { "stock lengths must be > 0" }

        val project = dao.getProjectById(projectId) ?: return@withContext
        val sortedStocks = settings.stockLengthsMm.distinct().sortedDescending()
        val now = System.currentTimeMillis()

        database.withTransaction {
            val updatedProject = project.copy(
                name = settings.name.trim(),
                kerfMm = settings.kerfMm,
                diameterMm = settings.diameterMm,
                pricePerKgTomans = settings.pricePerKgTomans,
                steelDensityKgM3 = settings.steelDensityKgM3,
                stockLengthsMm = sortedStocks,
                updatedAtMillis = now
            )
            dao.updateProject(updatedProject)
            dao.updateProject(updatedProject.copy(cuttingPlan = null, updatedAtMillis = now))
        }
    }

    suspend fun getProjectSnapshot(projectId: Long): ProjectWithDemands? = withContext(Dispatchers.IO) {
        val project = dao.getProjectById(projectId) ?: return@withContext null
        val demands = dao.getDemandsOnce(projectId)
        ProjectWithDemands(project = project, demands = demands)
    }

    suspend fun countProjects(): Int = withContext(Dispatchers.IO) {
        dao.countProjects()
    }

    suspend fun saveCuttingPlan(projectId: Long, plan: CuttingPlan) = withContext(Dispatchers.IO) {
        val project = dao.getProjectById(projectId) ?: error("Project not found: $projectId")
        dao.updateProject(
            project.copy(
                cuttingPlan = plan,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteProject(projectId: Long) = withContext(Dispatchers.IO) {
        val project = dao.getProjectById(projectId) ?: return@withContext
        dao.deleteProject(project)
    }

    suspend fun replaceDemands(projectId: Long, demands: List<DemandInput>) = withContext(Dispatchers.IO) {
        database.withTransaction {
            dao.deleteDemandsForProject(projectId)
            val now = System.currentTimeMillis()
            val entities = demands
                .asSequence()
                .filter { it.lengthMm > 0 && it.quantity > 0 }
                .map {
                    DemandEntity(
                        projectId = projectId,
                        lengthMm = it.lengthMm,
                        quantity = it.quantity,
                        createdAtMillis = now
                    )
                }
                .toList()

            if (entities.isNotEmpty()) {
                dao.insertDemands(entities)
            }

            val project = dao.getProjectById(projectId) ?: return@withTransaction
            dao.updateProject(project.copy(updatedAtMillis = now, cuttingPlan = null))
        }
    }

    suspend fun solveProject(
        projectId: Long,
        timeLimitMicros: Long = 1_500_000L
    ): CuttingPlan = withContext(Dispatchers.Default) {
        val project = dao.getProjectById(projectId) ?: error("Project not found: $projectId")
        val demands = dao.getDemandsOnce(projectId)

        require(demands.isNotEmpty()) { "Project has no demands" }

        val grouped = demands
            .asSequence()
            .filter { it.lengthMm > 0 && it.quantity > 0 }
            .groupBy { it.lengthMm }
            .map { (lengthMm, items) ->
                DemandInput(
                    lengthMm = lengthMm,
                    quantity = items.sumOf { it.quantity }
                )
            }
            .sortedByDescending { it.lengthMm }
            .toList()

        require(grouped.isNotEmpty()) { "Project has no valid demands" }

        val maxStock = project.stockLengthsMm.maxOrNull() ?: 12_000
        grouped.forEach { demand ->
            require(demand.lengthMm <= maxStock) {
                "طول ${demand.lengthMm} از بزرگ‌ترین میلگرد (${maxStock}mm) بیشتر است."
            }
        }

        val lengthsMm = IntArray(grouped.size) { index -> grouped[index].lengthMm }
        val quantities = IntArray(grouped.size) { index -> grouped[index].quantity }
        val stockLengths = project.stockLengthsMm
            .distinct()
            .sortedDescending()
            .toIntArray()

        val plan = NativeSolver.solveCuttingPlan(
            kerfMm = project.kerfMm,
            stockLengthsMm = stockLengths,
            lengthsMm = lengthsMm,
            quantities = quantities,
            timeLimitMicros = timeLimitMicros
        )

        val now = System.currentTimeMillis()
        database.withTransaction {
            val fresh = dao.getProjectById(projectId) ?: error("Project not found: $projectId")
            dao.updateProject(
                fresh.copy(
                    cuttingPlan = plan,
                    updatedAtMillis = now
                )
            )
        }
        plan
    }

    suspend fun clearCuttingPlan(projectId: Long) = withContext(Dispatchers.IO) {
        val project = dao.getProjectById(projectId) ?: return@withContext
        dao.updateProject(project.copy(cuttingPlan = null, updatedAtMillis = System.currentTimeMillis()))
    }
}
