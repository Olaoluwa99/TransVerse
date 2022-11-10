package com.easit.aiscanner.camera

import android.app.Application
import android.content.Context
import androidx.annotation.MainThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.HashSet

/** View model for handling application workflow based on camera preview.  */
class WorkflowModel(application: Application) : AndroidViewModel(application) {

    val workflowState = MutableLiveData<WorkflowState>()
    val detectedBarcode = MutableLiveData<Barcode>()

    private val objectIdsToSearch = HashSet<Int>()

    var isCameraLive = false
        private set

    //private var confirmedObject: DetectedObjectInfo? = null

    private val context: Context
        get() = getApplication<Application>().applicationContext

    /**
     * State set of the application workflow.
     */
    enum class WorkflowState {
        NOT_STARTED,
        DETECTING,
        DETECTED,
        CONFIRMING,
        CONFIRMED,
        SEARCHING,
        SEARCHED
    }

    @MainThread
    fun setWorkflowState(workflowState: WorkflowState) {
        if (workflowState != WorkflowState.CONFIRMED &&
            workflowState != WorkflowState.SEARCHING &&
            workflowState != WorkflowState.SEARCHED
        ) {
            //confirmedObject = null
        }
        this.workflowState.value = workflowState
    }

    fun markCameraLive() {
        isCameraLive = true
        objectIdsToSearch.clear()
    }

    fun markCameraFrozen() {
        isCameraLive = false
    }


}