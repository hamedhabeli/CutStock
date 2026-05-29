package com.example.cutstock.domain.billing

import com.example.cutstock.data.UserPreferences
import kotlinx.coroutines.flow.Flow

/**
 * Placeholder for Cafe Bazaar Poolakey integration.
 * Replace purchase/restore with real IAB calls in a dedicated release PR.
 */
class CafeBazaarBillingManager(
    private val userPreferences: UserPreferences
) : BillingManager {
    override val proStatus: Flow<Boolean> = userPreferences.isPro

    override suspend fun purchasePro(): BillingResult =
        BillingResult.Error("اتصال کافه‌بازار هنوز پیاده‌سازی نشده است.")

    override suspend fun restorePurchases(): BillingResult =
        BillingResult.Error("اتصال کافه‌بازار هنوز پیاده‌سازی نشده است.")

    override suspend fun refreshStatus() = Unit

    override suspend fun setProForDebug(enabled: Boolean) = Unit
}
