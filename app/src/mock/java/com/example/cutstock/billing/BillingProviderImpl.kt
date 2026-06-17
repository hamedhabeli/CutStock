package com.example.cutstock.billing

import com.example.cutstock.data.UserPreferences
import com.example.cutstock.domain.billing.BillingManager
import com.example.cutstock.domain.billing.DebugBillingManager

class BillingProviderImpl : BillingProvider {
    override fun create(userPreferences: UserPreferences): BillingManager = 
        DebugBillingManager(userPreferences)
}
