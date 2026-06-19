package com.example.cutstock.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProjectEntity::class, DemandEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(CuttingPlanConverters::class, IntListConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN kerfMm INTEGER NOT NULL DEFAULT 3")
                db.execSQL("ALTER TABLE projects ADD COLUMN diameterMm INTEGER NOT NULL DEFAULT 16")
                db.execSQL(
                    "ALTER TABLE projects ADD COLUMN pricePerKgTomans INTEGER NOT NULL DEFAULT 35000"
                )
                db.execSQL(
                    "ALTER TABLE projects ADD COLUMN steelDensityKgM3 REAL NOT NULL DEFAULT 7850.0"
                )
                db.execSQL(
                    "ALTER TABLE projects ADD COLUMN stockLengthsMm TEXT NOT NULL DEFAULT '[12000]'"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys=OFF")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS projects_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        kerfMm INTEGER NOT NULL,
                        diameterMm INTEGER NOT NULL,
                        pricePerKgTomans INTEGER NOT NULL,
                        steelDensityKgM3 REAL NOT NULL,
                        stockLengthsMm TEXT NOT NULL,
                        cuttingPlan TEXT,
                        createdAtMillis INTEGER NOT NULL,
                        updatedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO projects_new (
                        id,
                        name,
                        kerfMm,
                        diameterMm,
                        pricePerKgTomans,
                        steelDensityKgM3,
                        stockLengthsMm,
                        cuttingPlan,
                        createdAtMillis,
                        updatedAtMillis
                    )
                    SELECT
                        id,
                        name,
                        kerfMm,
                        diameterMm,
                        pricePerKgTomans,
                        steelDensityKgM3,
                        stockLengthsMm,
                        cuttingPlan,
                        createdAtMillis,
                        updatedAtMillis
                    FROM projects
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE projects")
                db.execSQL("ALTER TABLE projects_new RENAME TO projects")
                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }
    }
}
