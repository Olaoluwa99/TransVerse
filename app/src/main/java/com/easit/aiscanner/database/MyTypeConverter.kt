package com.easit.aiscanner.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MyTypeConverter {
    @TypeConverter
    fun fromJson(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        val listType = object : TypeToken<List<String>>(){}.type
        return runCatching { Gson().fromJson<List<String>>(value, listType).orEmpty() }
            .getOrDefault(emptyList())
    }

    @TypeConverter
    fun toJson(list: List<String>?): String = Gson().toJson(list.orEmpty())
}
