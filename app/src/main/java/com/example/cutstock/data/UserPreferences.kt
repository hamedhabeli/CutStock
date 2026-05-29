package com.example.cutstock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cutstock_prefs")

data class WorkshopDefaults(
    val kerfMm: Int = 3,
    val diameterMm: Int = 16,
    val pricePerKgTomans: Long = 35_000L,
    val steelDensityKgM3: Double = 7850.0,
    val stockLengthsMm: List<Int> = listOf(12_000)
)

class UserPreferences(context: Context) {
    private val dataStore = context.applicationContext.dataStore

    val isPro: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_IS_PRO] ?: false
    }

    val workshopDefaults: Flow<WorkshopDefaults> = dataStore.data.map { prefs ->
        WorkshopDefaults(
            kerfMm = prefs[KEY_DEFAULT_KERF] ?: 3,
            diameterMm = prefs[KEY_DEFAULT_DIAMETER] ?: 16,
            pricePerKgTomans = prefs[KEY_DEFAULT_PRICE] ?: 35_000L,
            steelDensityKgM3 = prefs[KEY_DEFAULT_DENSITY]?.toDoubleOrNull() ?: 7850.0,
            stockLengthsMm = parseStockLengths(prefs[KEY_DEFAULT_STOCKS])
        )
    }

    suspend fun setPro(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_IS_PRO] = enabled
        }
    }

    suspend fun updateWorkshopDefaults(defaults: WorkshopDefaults) {
        dataStore.edit { prefs ->
            prefs[KEY_DEFAULT_KERF] = defaults.kerfMm
            prefs[KEY_DEFAULT_DIAMETER] = defaults.diameterMm
            prefs[KEY_DEFAULT_PRICE] = defaults.pricePerKgTomans
            prefs[KEY_DEFAULT_DENSITY] = defaults.steelDensityKgM3.toString()
            prefs[KEY_DEFAULT_STOCKS] = defaults.stockLengthsMm.joinToString(",")
        }
    }

    private fun parseStockLengths(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return listOf(12_000)
        return raw.split(',', ' ', '\n')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it > 0 }
            .distinct()
            .sortedDescending()
            .ifEmpty { listOf(12_000) }
    }

    companion object {
        private val KEY_IS_PRO = booleanPreferencesKey("is_pro")
        private val KEY_DEFAULT_KERF = intPreferencesKey("default_kerf_mm")
        private val KEY_DEFAULT_DIAMETER = intPreferencesKey("default_diameter_mm")
        private val KEY_DEFAULT_PRICE = longPreferencesKey("default_price_per_kg")
        private val KEY_DEFAULT_DENSITY = stringPreferencesKey("default_density_kg_m3")
        private val KEY_DEFAULT_STOCKS = stringPreferencesKey("default_stock_lengths")
    }
}
