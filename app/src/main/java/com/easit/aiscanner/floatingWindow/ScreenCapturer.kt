package com.easit.aiscanner.floatingWindow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Grabs single frames of the real display from a [MediaProjection].
 *
 * One virtual display is created on the first [capture] and kept: Android 14+ allows only one
 * `createVirtualDisplay` per projection. Between captures (capture session mode) [detach] removes
 * its surface so nothing is composed for it; the next [capture] attaches it again.
 *
 * Call everything except the image callbacks (which run on [handler]) from the main thread.
 */
internal class ScreenCapturer(
    private val context: Context,
    private val projection: MediaProjection,
    private val handler: Handler,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var reader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    /**
     * Captures one frame of the screen.
     *
     * @param settle waits until the screen shows what should be captured (e.g. the consent
     *   activity has gone). Frames composed before it returns are skipped, except that the latest
     *   one is used if the screen hasn't changed since.
     * @throws kotlinx.coroutines.TimeoutCancellationException if no frame arrives in time
     */
    suspend fun capture(settle: suspend () -> Unit): Bitmap {
        val (width, height) = realDisplaySize()
        val density = context.resources.displayMetrics.densityDpi
        val reader = readerFor(width, height)
        val grab = FrameGrab()
        reader.setOnImageAvailableListener({ grab.onImageAvailable(it) }, handler)

        val display = virtualDisplay
        if (display == null) {
            virtualDisplay = projection.createVirtualDisplay(
                DISPLAY_NAME,
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler,
            ) ?: throw IllegalStateException("The virtual display could not be created")
        } else {
            display.resize(width, height, density)
            display.surface = reader.surface
        }

        try {
            settle()
            handler.post { grab.markReady() }
            // A static screen produces no new frames; re-attaching the surface forces one.
            return withTimeoutOrNull(NUDGE_AFTER_MS) { grab.result.await() } ?: run {
                virtualDisplay?.surface = null
                virtualDisplay?.surface = reader.surface
                withTimeout(CAPTURE_TIMEOUT_MS) { grab.result.await() }
            }
        } finally {
            reader.setOnImageAvailableListener(null, null)
            handler.post { grab.discard() }
        }
    }

    /** Stops composing frames for the virtual display until the next [capture]. */
    fun detach() {
        runCatching { virtualDisplay?.surface = null }
        // Frames queued after the last capture would fill the reader and block the next one.
        val current = reader ?: return
        handler.post { drain(current) }
    }

    private fun drain(reader: ImageReader) {
        try {
            while (true) {
                val image = reader.acquireNextImage() ?: break
                image.close()
            }
        } catch (_: IllegalStateException) {
            // Closed, or images still held elsewhere: nothing more to drain.
        }
    }

    /** Releases the virtual display and reader. The projection itself is the owner's to stop. */
    fun release() {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        reader?.let { old -> handler.post { runCatching { old.close() } } }
        reader = null
    }

    private fun readerFor(width: Int, height: Int): ImageReader {
        reader?.let { existing ->
            if (existing.width == width && existing.height == height) return existing
            // Rotated since the last capture: swap in a reader of the new size.
            runCatching { virtualDisplay?.surface = null }
            handler.post { runCatching { existing.close() } }
        }
        // Three images: one held as "latest so far" plus two for acquireLatestImage.
        return ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, MAX_IMAGES)
            .also { reader = it }
    }

    /** Real display size including system bars, so the capture covers the whole screen. */
    private fun realDisplaySize(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val realMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(realMetrics)
            realMetrics.widthPixels to realMetrics.heightPixels
        }

    /**
     * Picks the frame to keep: the first frame after [markReady], or, if the screen didn't change
     * after it became ready, the latest frame before. Only touched on the capture thread.
     */
    private class FrameGrab {
        val result = CompletableDeferred<Bitmap>()
        private var held: Image? = null
        private var ready = false

        fun onImageAvailable(reader: ImageReader) {
            val image = try {
                reader.acquireLatestImage()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Could not acquire a frame", e)
                null
            } ?: return
            if (result.isCompleted) {
                image.close()
                return
            }
            if (ready) {
                complete(image)
            } else {
                held?.close()
                held = image
            }
        }

        fun markReady() {
            ready = true
            held?.let { image ->
                held = null
                complete(image)
            }
        }

        fun discard() {
            held?.close()
            held = null
        }

        private fun complete(image: Image) {
            try {
                result.complete(image.toBitmap())
            } catch (e: RuntimeException) {
                result.completeExceptionally(e)
            } finally {
                image.close()
            }
        }
    }

    companion object {
        private const val TAG = "ScreenCapturer"
        private const val DISPLAY_NAME = "TransVerse screen capture"
        private const val MAX_IMAGES = 3
        private const val NUDGE_AFTER_MS = 1_200L
        private const val CAPTURE_TIMEOUT_MS = 6_000L

        /** RGBA_8888 frame → bitmap, dropping the row padding some devices add. */
        private fun Image.toBitmap(): Bitmap {
            val plane = planes.first()
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val paddedWidth = width + (rowStride - pixelStride * width) / pixelStride
            val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
            padded.copyPixelsFromBuffer(plane.buffer)
            if (paddedWidth == width) return padded
            return Bitmap.createBitmap(padded, 0, 0, width, height).also { padded.recycle() }
        }
    }
}
