package com.easit.aiscanner.camera

import android.content.Context
import android.graphics.RectF
import android.preference.PreferenceManager
import androidx.annotation.StringRes
import com.easit.aiscanner.R
import com.google.android.gms.common.images.Size
import com.google.mlkit.vision.barcode.common.Barcode

/** Utility class to retrieve shared preferences. */
object PreferenceUtils {

    fun isAutoSearchEnabled(context: Context): Boolean =
        getBooleanPref(context, R.string.pref_key_enable_auto_search, true)

    fun isMultipleObjectsMode(context: Context): Boolean =
        getBooleanPref(context, R.string.pref_key_object_detector_enable_multiple_objects, false)

    fun isClassificationEnabled(context: Context): Boolean =
        getBooleanPref(context, R.string.pref_key_object_detector_enable_classification, false)

    fun saveStringPreference(context: Context, @StringRes prefKeyId: Int, value: String?) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(context.getString(prefKeyId), value)
            .apply()
    }

    fun getUserSpecifiedPreviewSize(context: Context): CameraSizePair? {
        return try {
            val previewSizePrefKey = context.getString(R.string.pref_key_rear_camera_preview_size)
            val pictureSizePrefKey = context.getString(R.string.pref_key_rear_camera_picture_size)
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            CameraSizePair(
                Size.parseSize(sharedPreferences.getString(previewSizePrefKey, null)!!),
                Size.parseSize(sharedPreferences.getString(pictureSizePrefKey, null)!!)
            )
        } catch (e: Exception) {
            null
        }
    }

    fun getConfirmationTimeMs(context: Context): Int =
        when {
            isMultipleObjectsMode(context) -> 300
            isAutoSearchEnabled(context) ->
                getIntPref(context, R.string.pref_key_confirmation_time_in_auto_search, 1500)
            else -> getIntPref(context, R.string.pref_key_confirmation_time_in_manual_search, 500)
        }

    fun getProgressToMeetBarcodeSizeRequirement(overlay: GraphicOverlay, barcode: Barcode): Float {
        val context = overlay.context
        return if (getBooleanPref(context, R.string.pref_key_enable_barcode_size_check, false)) {
            val reticleBoxWidth = getBarcodeReticleBox(overlay).width()
            val barcodeWidth = overlay.translateX(barcode.boundingBox?.width()?.toFloat() ?: 0f)
            val requiredWidth =
                reticleBoxWidth * getIntPref(context, R.string.pref_key_minimum_barcode_width, 50) / 100
            (barcodeWidth / requiredWidth).coerceAtMost(1f)
        } else {
            1f
        }
    }

    fun getBarcodeReticleBox(overlay: GraphicOverlay): RectF {
        val context = overlay.context
        val overlayWidth = overlay.width.toFloat()
        val overlayHeight = overlay.height.toFloat()
        val boxWidth =
            overlayWidth * getIntPref(context, R.string.pref_key_barcode_reticle_width, 80) / 100
        val boxHeight =
            overlayHeight * getIntPref(context, R.string.pref_key_barcode_reticle_height, 35) / 100
        val cx = overlayWidth / 2
        val cy = overlayHeight / 2
        return RectF(cx - boxWidth / 2, cy - boxHeight / 2, cx + boxWidth / 2, cy + boxHeight / 2)
    }

    fun shouldDelayLoadingBarcodeResult(context: Context): Boolean =
        getBooleanPref(context, R.string.pref_key_delay_loading_barcode_result, true)

    private fun getIntPref(context: Context, @StringRes prefKeyId: Int, defaultValue: Int): Int {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val prefKey = context.getString(prefKeyId)
        return sharedPreferences.getInt(prefKey, defaultValue)
    }

    private fun getBooleanPref(
        context: Context,
        @StringRes prefKeyId: Int,
        defaultValue: Boolean
    ): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context.getString(prefKeyId), defaultValue)
}