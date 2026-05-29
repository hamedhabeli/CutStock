package com.example.cutstock.domain.billing

import kotlinx.coroutines.flow.Flow

interface BillingManager {
    val proStatus: Flow<Boolean>
    suspend fun purchasePro(): BillingResult
    suspend fun restorePurchases(): BillingResult
    suspend fun refreshStatus()
    suspend fun setProForDebug(enabled: Boolean)
}
