package com.example.cutstock.data

import androidx.room.TypeConverter
import com.example.cutstock.nativecore.CuttingPlan
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken

class CuttingPlanConverters {
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    @TypeConverter
    fun fromCuttingPlan(value: CuttingPlan?): String? = value?.let { gson.toJson(it) }

    @TypeConverter
    fun toCuttingPlan(value: String?): CuttingPlan? =
        value?.takeIf { it.isNotBlank() }?.let { gson.fromJson(it, CuttingPlan::class.java) }
}

class IntListConverters {
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
    private val listType = object : TypeToken<List<Int>>() {}.type

    @TypeConverter
    fun fromIntList(value: List<Int>?): String? = value?.let { gson.toJson(it) }

    @TypeConverter
    fun toIntList(value: String?): List<Int> {
        if (value.isNullOrBlank()) return listOf(12_000)
        return gson.fromJson(value, listType) ?: listOf(12_000)
    }
}
