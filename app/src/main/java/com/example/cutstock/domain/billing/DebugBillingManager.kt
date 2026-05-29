package com.example.cutstock.domain.billing

import com.example.cutstock.data.UserPreferences
import kotlinx.coroutines.flow.first

class DebugBillingManager(
    private val userPreferences: UserPreferences
) : BillingManager {
    override val proStatus: Flow<Boolean> = userPreferences.isPro

    override suspend fun purchasePro(): BillingResult {
        userPreferences.setPro(true)
        return BillingResult.Success
    }

    override suspend fun restorePurchases(): BillingResult {
        val isPro = userPreferences.isPro.first()
        return if (isPro) BillingResult.Success else BillingResult.Error("خریدی یافت نشد.")
    }

    override suspend fun refreshStatus() = Unit

    override suspend fun setProForDebug(enabled: Boolean) {
        userPreferences.setPro(enabled)
    }
}
