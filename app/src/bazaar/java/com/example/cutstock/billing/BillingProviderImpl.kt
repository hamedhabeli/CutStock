package com.example.cutstock.billing

import com.example.cutstock.CutStockApplication
import com.example.cutstock.data.UserPreferences
import com.example.cutstock.domain.billing.BillingManager

class BillingProviderImpl : BillingProvider {
    override fun create(userPreferences: UserPreferences): BillingManager = 
        CafeBazaarBillingManager(CutStockApplication.instance, userPreferences)
}
