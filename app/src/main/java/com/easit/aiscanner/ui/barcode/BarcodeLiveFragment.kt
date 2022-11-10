package com.easit.aiscanner.ui.barcode

import android.Manifest
import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Camera
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.easit.aiscanner.R
import com.easit.aiscanner.barcodeDetection.BarcodeField
import com.easit.aiscanner.barcodeDetection.BarcodeProcessor
import com.easit.aiscanner.barcodeDetection.BarcodeResultFragment
import com.easit.aiscanner.camera.CameraSource
import com.easit.aiscanner.camera.CameraSourcePreview
import com.easit.aiscanner.camera.GraphicOverlay
import com.easit.aiscanner.camera.WorkflowModel
import com.easit.aiscanner.databinding.FragmentAudioGroundBinding
import com.easit.aiscanner.databinding.FragmentBarcodeLiveBinding
import com.google.android.material.chip.Chip
import com.google.common.base.Objects
import java.io.IOException
import java.util.ArrayList

class BarcodeLiveFragment : Fragment(), View.OnClickListener {

    private var _binding: FragmentBarcodeLiveBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: WorkflowModel

    private var cameraSource: CameraSource? = null
    private lateinit var preview: CameraSourcePreview
    private var graphicOverlay: GraphicOverlay? = null
    private var settingsButton: View? = null
    private var flashButton: View? = null
    private var promptChip: Chip? = null
    private var promptChipAnimator: AnimatorSet? = null
    //private var workflowModel: WorkflowModel? = null
    private var currentWorkflowState: WorkflowModel.WorkflowState? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewModel = ViewModelProvider(this).get(WorkflowModel::class.java)
        _binding = FragmentBarcodeLiveBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!allRuntimePermissionsGranted()) {
            getRuntimePermissions()
        }
        initializations()
    }

    private fun initializations(){
        preview = requireActivity().findViewById(R.id.camera_preview)
        graphicOverlay = requireActivity().findViewById<GraphicOverlay>(R.id.camera_preview_graphic_overlay).apply {
            setOnClickListener(this@BarcodeLiveFragment)
            cameraSource = CameraSource(this)
        }

        promptChip = requireActivity().findViewById(R.id.bottom_prompt_chip)
        promptChipAnimator =
            (AnimatorInflater.loadAnimator(requireContext(), R.animator.bottom_prompt_chip_enter) as AnimatorSet).apply {
                setTarget(promptChip)
            }

        //requireActivity().findViewById<View>(R.id.close_button).setOnClickListener(this)
        flashButton = requireActivity().findViewById<View>(R.id.flash_button).apply {
            setOnClickListener(this@BarcodeLiveFragment)
        }
        /*
        settingsButton = requireActivity().findViewById<View>(R.id.settings_button).apply {
            setOnClickListener(this@BarcodeLiveFragment)
        }*/

        setUpWorkflowModel()
    }


    override fun onResume() {
        super.onResume()

        viewModel?.markCameraFrozen()
        settingsButton?.isEnabled = true
        currentWorkflowState = WorkflowModel.WorkflowState.NOT_STARTED
        cameraSource?.setFrameProcessor(BarcodeProcessor(graphicOverlay!!, viewModel!!))
        viewModel?.setWorkflowState(WorkflowModel.WorkflowState.DETECTING)
    }

    /*
    override fun onPostResume() {
        super.onPostResume()
        BarcodeResultFragment.dismiss(requireActivity().supportFragmentManager)
    }*/

    override fun onPause() {
        super.onPause()
        currentWorkflowState = WorkflowModel.WorkflowState.NOT_STARTED
        stopCameraPreview()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraSource?.release()
        cameraSource = null
    }

    override fun onClick(view: View) {
        when (view.id) {
            //R.id.close_button -> findNavController().popBackStack()
            R.id.flash_button -> {
                flashButton?.let {
                    if (it.isSelected) {
                        it.isSelected = false
                        cameraSource?.updateFlashMode(Camera.Parameters.FLASH_MODE_OFF)
                    } else {
                        it.isSelected = true
                        cameraSource!!.updateFlashMode(Camera.Parameters.FLASH_MODE_TORCH)
                    }
                }
            }
            //R.id.settings_button -> {}
        }
    }

    private fun startCameraPreview() {
        val workflowModel = this.viewModel ?: return
        val cameraSource = this.cameraSource ?: return
        if (!workflowModel.isCameraLive) {
            try {
                workflowModel.markCameraLive()
                preview?.start(cameraSource)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to start camera preview!", e)
                cameraSource.release()
                this.cameraSource = null
            }
        }
    }

    private fun stopCameraPreview() {
        val workflowModel = this.viewModel ?: return
        if (workflowModel.isCameraLive) {
            workflowModel.markCameraFrozen()
            flashButton?.isSelected = false
            preview?.stop()
        }
    }

    private fun setUpWorkflowModel() {
        //workflowModel = ViewModelProviders.of(this).get(WorkflowModel::class.java)

        // Observes the workflow state changes, if happens, update the overlay view indicators and
        // camera preview state.
        viewModel!!.workflowState.observe(viewLifecycleOwner, Observer { workflowState ->
            if (workflowState == null || Objects.equal(currentWorkflowState, workflowState)) {
                return@Observer
            }

            currentWorkflowState = workflowState
            Log.d(TAG, "Current workflow state: ${currentWorkflowState!!.name}")

            val wasPromptChipGone = promptChip?.visibility == View.GONE

            when (workflowState) {
                WorkflowModel.WorkflowState.DETECTING -> {
                    promptChip?.visibility = View.VISIBLE
                    promptChip?.setText(R.string.prompt_point_at_a_barcode)
                    startCameraPreview()
                }
                WorkflowModel.WorkflowState.CONFIRMING -> {
                    promptChip?.visibility = View.VISIBLE
                    promptChip?.setText(R.string.prompt_move_camera_closer)
                    startCameraPreview()
                }
                WorkflowModel.WorkflowState.SEARCHING -> {
                    promptChip?.visibility = View.VISIBLE
                    promptChip?.setText(R.string.prompt_searching)
                    stopCameraPreview()
                }
                WorkflowModel.WorkflowState.DETECTED, WorkflowModel.WorkflowState.SEARCHED -> {
                    promptChip?.visibility = View.GONE
                    stopCameraPreview()
                }
                else -> promptChip?.visibility = View.GONE
            }

            val shouldPlayPromptChipEnteringAnimation = wasPromptChipGone && promptChip?.visibility == View.VISIBLE
            promptChipAnimator?.let {
                if (shouldPlayPromptChipEnteringAnimation && !it.isRunning) it.start()
            }
        })

        viewModel?.detectedBarcode?.observe(viewLifecycleOwner, Observer { barcode ->
            if (barcode != null) {
                val barcodeFieldList = ArrayList<BarcodeField>()
                barcodeFieldList.add(BarcodeField("Raw Value", barcode.rawValue ?: ""))
                BarcodeResultFragment.show(requireActivity().supportFragmentManager, barcodeFieldList)
            }
        })
    }


    //PERMISSIONS
    private fun allRuntimePermissionsGranted(): Boolean {
        for (permission in REQUIRED_RUNTIME_PERMISSIONS) {
            permission?.let {
                if (!isPermissionGranted(requireContext(), it)) {
                    return false
                }
            }
        }
        return true
    }

    private fun getRuntimePermissions() {
        val permissionsToRequest = ArrayList<String>()
        for (permission in REQUIRED_RUNTIME_PERMISSIONS) {
            permission?.let {
                if (!isPermissionGranted(requireContext(), it)) {
                    permissionsToRequest.add(permission)
                }
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUESTS
            )
        }
    }

    private fun isPermissionGranted(context: Context, permission: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "Permission granted: $permission")
            return true
        }
        Log.i(TAG, "Permission NOT granted: $permission")
        return false
    }

    companion object {
        //private const val TAG = "CameraXLivePreview"
        private const val BARCODE_SCANNING = "Barcode Scanning"
        private const val PERMISSION_REQUESTS = 1
        private const val TAG = "LiveBarcodeActivity"

        private val REQUIRED_RUNTIME_PERMISSIONS =
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
    }

}