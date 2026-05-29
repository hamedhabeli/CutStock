package com.example.cutstock.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverters
import com.example.cutstock.nativecore.CuttingPlan

@TypeConverters(CuttingPlanConverters::class, IntListConverters::class)
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val stockLengthMm: Int = 12_000,
    val kerfMm: Int = 3,
    val diameterMm: Int = 16,
    val pricePerKgTomans: Long = 35_000L,
    val steelDensityKgM3: Double = 7850.0,
    val stockLengthsMm: List<Int> = listOf(12_000),
    val cuttingPlan: CuttingPlan? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(
    tableName = "demands",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class DemandEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val projectId: Long,
    val lengthMm: Int,
    val quantity: Int,
    val createdAtMillis: Long
)

data class ProjectWithDemands(
    @Embedded val project: ProjectEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "projectId"
    )
    val demands: List<DemandEntity>
)

data class ProjectSettings(
    val name: String,
    val kerfMm: Int,
    val diameterMm: Int,
    val pricePerKgTomans: Long,
    val steelDensityKgM3: Double,
    val stockLengthsMm: List<Int>
) {
    val primaryStockLengthMm: Int get() = stockLengthsMm.maxOrNull() ?: 12_000
}
