package com.example.cutstock.domain

import android.os.Bundle
import com.example.cutstock.CutStockApplication
import com.google.firebase.analytics.FirebaseAnalytics

object AnalyticsTracker {
    private val firebaseAnalytics: FirebaseAnalytics by lazy {
        FirebaseAnalytics.getInstance(CutStockApplication.instance)
    }

    fun trackSolveCompleted(wastePercent: Double, demandCount: Int) {
        val bundle = Bundle().apply {
            putDouble("waste_percent", wastePercent)
            putInt("demand_count", demandCount)
        }
        firebaseAnalytics.logEvent("solve_completed", bundle)
    }

    fun trackPaywallShown(trigger: String) {
        val bundle = Bundle().apply {
            putString("trigger", trigger)
        }
        firebaseAnalytics.logEvent("paywall_shown", bundle)
    }

    fun trackPurchaseInitiated() {
        firebaseAnalytics.logEvent("purchase_initiated", null)
    }

    fun trackPdfExported() {
        firebaseAnalytics.logEvent("pdf_exported", null)
    }
}
