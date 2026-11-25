package com.easit.aiscanner.ui.translate

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Decodes a picked, photographed or shared image for the crop editor: upright (EXIF orientation
 * applied), downsampled so its longer side is at most [MAX_SIDE_PX], in a software bitmap that
 * cropping and ML Kit can read.
 */
internal object CropImageDecoder {

    /** Longest side of the decoded image: plenty for OCR, bounded memory. */
    const val MAX_SIDE_PX = 3000

    /**
     * @throws IOException if [uri] can't be opened or isn't a decodable image
     * @throws SecurityException if access to [uri] was revoked
     * @throws OutOfMemoryError if even the downsampled image doesn't fit
     */
    @WorkerThread
    fun decode(context: Context, uri: Uri, maxSide: Int = MAX_SIDE_PX): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeWithImageDecoder(context.contentResolver, uri, maxSide)
        } else {
            decodeWithBitmapFactory(context.contentResolver, uri, maxSide)
        }

    /** ImageDecoder applies EXIF orientation itself; its sizes are already upright. */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(resolver: ContentResolver, uri: Uri, maxSide: Int): Bitmap {
        val source = ImageDecoder.createSource(resolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            val longest = max(width, height)
            if (longest > maxSide) {
                val scale = maxSide.toFloat() / longest
                decoder.setTargetSize(
                    (width * scale).roundToInt().coerceAtLeast(1),
                    (height * scale).roundToInt().coerceAtLeast(1),
                )
            }
            // Hardware bitmaps can't be read back for cropping or text recognition.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    /** API 26–27: sampled BitmapFactory decode, then the EXIF orientation applied by hand. */
    private fun decodeWithBitmapFactory(resolver: ContentResolver, uri: Uri, maxSide: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(resolver, uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not a decodable image: $uri")

        // The largest power-of-two sample that keeps the image at least maxSide, then an exact
        // scale down: sharper than sampling straight below the limit.
        val longest = max(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (longest / (sampleSize * 2) >= maxSide) sampleSize *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bitmap = open(resolver, uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Could not decode $uri")

        val decodedLongest = max(bitmap.width, bitmap.height)
        if (decodedLongest > maxSide) {
            val scale = maxSide.toFloat() / decodedLongest
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
            if (scaled !== bitmap) bitmap.recycle()
            bitmap = scaled
        }

        val orientation = try {
            open(resolver, uri).use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        } catch (_: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }
        return applyOrientation(bitmap, orientation)
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (oriented !== bitmap) bitmap.recycle()
        return oriented
    }

    private fun open(resolver: ContentResolver, uri: Uri): InputStream =
        resolver.openInputStream(uri) ?: throw FileNotFoundException("Could not open $uri")
}
