package com.easit.aiscanner.ui.barcode

import android.app.Application
import android.os.Build.VERSION_CODES
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException

/** View model for interacting with CameraX.  */
class BarcodeCaptureViewModel
/**
 * Create an instance which interacts with the camera service via the given application context.
 */
    (application: Application) : AndroidViewModel(application) {
    private var cameraProviderLiveData: MutableLiveData<ProcessCameraProvider>? = null

    // Handle any errors (including cancellation) here.
    val processCameraProvider: MutableLiveData<ProcessCameraProvider>?
        get() {
            if (cameraProviderLiveData == null) {
                cameraProviderLiveData = MutableLiveData<ProcessCameraProvider>()
                val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> =
                    ProcessCameraProvider.getInstance(getApplication())
                cameraProviderFuture.addListener(
                    {
                        try {
                            cameraProviderLiveData!!.setValue(cameraProviderFuture.get())
                        } catch (e: ExecutionException) {
                            // Handle any errors (including cancellation) here.
                            Log.e(
                                TAG,
                                "Unhandled exception",
                                e
                            )
                        } catch (e: InterruptedException) {
                            Log.e(
                                TAG,
                                "Unhandled exception",
                                e
                            )
                        }
                    },
                    ContextCompat.getMainExecutor(getApplication())
                )
            }
            return cameraProviderLiveData
        }

    companion object {
        private const val TAG = "CameraXViewModel"
    }
}