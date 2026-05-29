package com.example.cutstock.presentation

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cutstock.CutStockApplication
import com.example.cutstock.R
import com.example.cutstock.domain.FreemiumPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.core.app.ApplicationProvider

@RunWith(AndroidJUnit4::class)
class ProjectListFreemiumTest {

  @Before
  fun seedMaxProjects() {
    val app = ApplicationProvider.getApplicationContext<CutStockApplication>()
    runBlocking {
      app.billingManager.setProForDebug(false)
      val count = app.projectRepository.countProjects()
      val toCreate = (FreemiumPolicy.FREE_MAX_PROJECTS - count).coerceAtLeast(0)
      repeat(toCreate) { i ->
        app.projectRepository.createProject("P${count}_$i")
      }
    }
  }

  @Test
  fun createFourthProject_blockedWhenFree() {
    ActivityScenario.launch(ProjectListActivity::class.java).use {
      onView(withId(R.id.addProjectFab)).perform(click())
      Thread.sleep(500)
      onView(withId(R.id.freeTierHintTextView)).check(matches(isDisplayed()))
    }
  }
}
