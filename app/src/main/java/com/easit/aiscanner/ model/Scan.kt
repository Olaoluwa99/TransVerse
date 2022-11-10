package com.easit.aiscanner.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scanTable")
data class Scan (
    @PrimaryKey(autoGenerate = false) @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "dateTime") val dateTime: String,
    @ColumnInfo(name = "timeMilliseconds")val timeMilliseconds: Long,
    @ColumnInfo(name = "scan_type")val scanType: String,
    @ColumnInfo(name = "transcript_text")val transcriptText: String,
    @ColumnInfo(name = "translated_text")val translatedText: String,
    @ColumnInfo(name = "source_language")val sourceLanguage: String,
    @ColumnInfo(name = "translated_language")val translatedLanguage: String,
    @ColumnInfo(name = "entities") val entities: List<String>,
    @ColumnInfo(name = "smart_replies") val smartReplies: List<String>,
    @ColumnInfo(name = "image_url") val imageUrl: String,
    @ColumnInfo(name = "audio_url") val audioUrl: String,
    @ColumnInfo(name = "barcode_scan") val barcodeScan: String
        )

/*
@ColumnInfo(name = "entities") val entities: ArrayList<String>,
    @ColumnInfo(name = "smart_replies") val smartReplies: ArrayList<String>,
 */