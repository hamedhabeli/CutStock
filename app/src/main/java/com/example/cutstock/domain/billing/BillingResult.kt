package com.example.cutstock.domain.billing

sealed class BillingResult {
    data object Success : BillingResult()
    data class Error(val message: String) : BillingResult()
    data object Cancelled : BillingResult()
}
