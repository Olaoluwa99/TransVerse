package com.easit.aiscanner.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MyTypeConverter {
    @TypeConverter
    fun fromString(value : String?): List<String>{
        val listType = object : TypeToken<List<String>>(){}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromList(list: List<String>?): String{
        return Gson().toJson(list)
    }
}