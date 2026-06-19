package com.example.cutstock.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Insert
    suspend fun insertProject(project: ProjectEntity): Long

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    @Insert
    suspend fun insertDemand(demand: DemandEntity): Long

    @Insert
    suspend fun insertDemands(demands: List<DemandEntity>): List<Long>

    @Update
    suspend fun updateDemand(demand: DemandEntity)

    @Delete
    suspend fun deleteDemand(demand: DemandEntity)

    @Query("DELETE FROM demands WHERE projectId = :projectId")
    suspend fun deleteDemandsForProject(projectId: Long)

    @Query("SELECT * FROM demands WHERE projectId = :projectId ORDER BY id ASC")
    suspend fun getDemandsOnce(projectId: Long): List<DemandEntity>

    @Query("SELECT * FROM projects WHERE id = :projectId LIMIT 1")
    suspend fun getProjectById(projectId: Long): ProjectEntity?

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun countProjects(): Int

    @Query("SELECT * FROM projects ORDER BY updatedAtMillis DESC, id DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :projectId LIMIT 1")
    fun observeProjectById(projectId: Long): Flow<ProjectEntity?>

    @Transaction
    @Query("SELECT * FROM projects WHERE id = :projectId LIMIT 1")
    fun observeProjectWithDemands(projectId: Long): Flow<ProjectWithDemands?>

    @Query("SELECT projectId, COUNT(*) as demandCount FROM demands GROUP BY projectId")
    fun observeDemandCountsMap(): Flow<List<DemandCountRow>>
}

data class DemandCountRow(
    val projectId: Long,
    val demandCount: Int
)
