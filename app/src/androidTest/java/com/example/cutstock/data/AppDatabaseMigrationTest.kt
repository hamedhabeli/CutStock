package com.example.cutstock.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cutstock.data.AppDatabase.Companion.MIGRATION_1_2
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

  private val testDb = "migration-test"

  @get:Rule
  val helper: MigrationTestHelper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    AppDatabase::class.java,
    emptyList(),
    FrameworkSQLiteOpenHelperFactory()
  )

  @Test
  @Throws(IOException::class)
  fun migrate1To2_addsWorkshopColumns() {
    helper.createDatabase(testDb, 1).apply {
      execSQL(
        """
        INSERT INTO projects (id, name, stockLengthMm, cuttingPlan, createdAtMillis, updatedAtMillis)
        VALUES (1, 'P', 12000, NULL, 1, 1)
        """.trimIndent()
      )
      close()
    }

    val db = helper.runMigrationsAndValidate(testDb, 2, true, MIGRATION_1_2)
    db.query("SELECT kerfMm, diameterMm, stockLengthsMm FROM projects WHERE id = 1").use { cursor ->
      cursor.moveToFirst()
      assertEquals(3, cursor.getInt(0))
      assertEquals(16, cursor.getInt(1))
      assertEquals("[12000]", cursor.getString(2))
    }
    db.close()
  }
}
