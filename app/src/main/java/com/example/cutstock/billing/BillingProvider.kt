package com.example.cutstock.billing

import com.example.cutstock.data.UserPreferences
import com.example.cutstock.domain.billing.BillingManager

interface BillingProvider {
    fun create(userPreferences: UserPreferences): BillingManager
}
