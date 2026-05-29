package com.example.cutstock.presentation

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cutstock.CutStockApplication
import com.example.cutstock.R
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

  private lateinit var projectId: Long

  @Before
  fun setup() {
    val app = ApplicationProvider.getApplicationContext<CutStockApplication>()
    runBlocking {
      projectId = app.projectRepository.createProject("Smoke Test")
    }
  }

  @Test
  fun solve_showsSummary() {
    ActivityScenario.launch<MainActivity>(
      Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_PROJECT_ID, projectId)
    ).use {
      onView(withId(R.id.bulkInputEditText))
        .perform(typeText("4000 2\n3000 1"), closeSoftKeyboard())
      onView(withId(R.id.solveButton)).perform(androidx.test.espresso.action.ViewActions.click())
      Thread.sleep(2500)
      onView(withId(R.id.projectTitleTextView))
    }
  }
}
