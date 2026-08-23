package com.easit.aiscanner

import com.easit.aiscanner.database.MyTypeConverter
import org.junit.Assert.assertEquals
import org.junit.Test

class TypeConverterTest {
    private val converter = MyTypeConverter()

    @Test
    fun stringLists_roundTripWithoutLosingOrderOrUnicode() {
        val original = listOf("Lagos", "こんにちは", "€12.50")
        assertEquals(original, converter.fromJson(converter.toJson(original)))
    }

    @Test
    fun emptyList_roundTrips() {
        assertEquals(emptyList<String>(), converter.fromJson(converter.toJson(emptyList())))
    }

    @Test
    fun malformedLegacyValue_doesNotCrashDatabaseReads() {
        assertEquals(emptyList<String>(), converter.fromJson("not-json"))
    }
}
