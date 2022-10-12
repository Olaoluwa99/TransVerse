package com.easit.aiscanner.model

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.easit.aiscanner.R

class Message(val text: String, val isLocalUser: Boolean, val timestamp: Long) {


    fun getIcon(context: Context): Drawable {
        val drawable =
            ContextCompat.getDrawable(context, R.drawable.ic_baseline_tag_faces_24)
                ?: throw IllegalStateException("Could not get drawable ic_tag_faces_black_24dp")

        if (isLocalUser) {
            DrawableCompat.setTint(drawable.mutate(), Color.BLUE)
        } else {
            DrawableCompat.setTint(drawable.mutate(), Color.RED)
        }

        return drawable
    }
}