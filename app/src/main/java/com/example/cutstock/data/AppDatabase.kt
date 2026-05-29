package com.example.cutstock.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProjectEntity::class, DemandEntity::class],
    version = 2,
    exportSchema = true
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
    }
}
