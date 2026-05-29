package com.example.cutstock.domain.billing

import com.example.cutstock.data.UserPreferences
import kotlinx.coroutines.flow.Flow

class StubBillingManager(
    private val userPreferences: UserPreferences
) : BillingManager {
    override val proStatus: Flow<Boolean> = userPreferences.isPro

    override suspend fun purchasePro(): BillingResult =
        BillingResult.Error("خرید هنوز فعال نشده است.")

    override suspend fun restorePurchases(): BillingResult =
        BillingResult.Error("بازیابی خرید هنوز فعال نشده است.")

    override suspend fun refreshStatus() = Unit

    override suspend fun setProForDebug(enabled: Boolean) = Unit
}
