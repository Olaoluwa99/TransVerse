package com.easit.aiscanner.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scanTable")
data class Scan (
    @PrimaryKey(autoGenerate = false) @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "time")val time: String,
    @ColumnInfo(name = "scan_type")val scanType: String,
    @ColumnInfo(name = "transcript_text")val transcriptText: String,
    @ColumnInfo(name = "translated_text")val translatedText: String,
    @ColumnInfo(name = "entities") val entities: String,
    @ColumnInfo(name = "smart_replies") val smartReplies: String,
    @ColumnInfo(name = "image_url") val imageUrl: String,
    @ColumnInfo(name = "audio_url") val audioUrl: String,
    @ColumnInfo(name = "barcode_scan") val barcodeScan: String
        )

/*
@ColumnInfo(name = "entities") val entities: ArrayList<String>,
    @ColumnInfo(name = "smart_replies") val smartReplies: ArrayList<String>,
 */