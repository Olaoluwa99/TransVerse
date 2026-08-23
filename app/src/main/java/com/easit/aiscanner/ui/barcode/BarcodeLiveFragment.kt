package com.easit.aiscanner.ui.barcode

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Camera
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.easit.aiscanner.R
import com.easit.aiscanner.barcodeDetection.BarcodeProcessor
import com.easit.aiscanner.camera.CameraSource
import com.easit.aiscanner.camera.CameraSourcePreview
import com.easit.aiscanner.camera.GraphicOverlay
import com.easit.aiscanner.camera.WorkflowModel
import com.easit.aiscanner.databinding.FragmentBarcodeLiveBinding
import java.io.IOException
import com.google.android.material.snackbar.Snackbar

class BarcodeLiveFragment : Fragment(), View.OnClickListener {

    private var _binding: FragmentBarcodeLiveBinding? = null
    private val binding get() = requireNotNull(_binding) {
        "Binding accessed outside the view lifecycle"
    }
    private val viewModel by viewModels<WorkflowModel>()

    private var cameraSource: CameraSource? = null
    private val preview get() = binding.root.findViewById<CameraSourcePreview>(R.id.camera_preview)
    private val graphicOverlay
        get() = binding.root.findViewById<GraphicOverlay>(R.id.camera_preview_graphic_overlay)
    private val flashButton get() = binding.root.findViewById<View>(R.id.flash_button)
    private var currentWorkflowState: WorkflowModel.WorkflowState? = null
    private var processorConfigured = false

    private val transcriptEditText get() = binding.transcribedEdittext
    private val infoText get() = binding.infoText
    private val copyBarcodeValue get() = binding.barcodeValueCopy

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && _binding != null) {
                configureScanner()
            } else {
                _binding?.root?.let {
                    Snackbar.make(it, R.string.camera_permission_required, Snackbar.LENGTH_LONG).show()
                }
            }
        }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentBarcodeLiveBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializations()
        if (isCameraPermissionGranted()) {
            configureScanner()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        copyBarcodeValue.setOnClickListener {
            if (transcriptEditText.text.toString() != ""){
                saveToClipboard("BARCODE_VALUE_COPY", transcriptEditText.text.toString())
            }
        }
    }

    private fun initializations(){
        //
        graphicOverlay.apply {
            setOnClickListener(this@BarcodeLiveFragment)
            cameraSource = CameraSource(this)
        }

        flashButton.apply {
            setOnClickListener(this@BarcodeLiveFragment)
        }

        setUpWorkflowModel()
    }


    override fun onResume() {
        super.onResume()

        if (!isCameraPermissionGranted()) return
        configureScanner()
    }

    private fun configureScanner() {
        viewModel.markCameraFrozen()
        currentWorkflowState = WorkflowModel.WorkflowState.NOT_STARTED
        if (!processorConfigured) {
            cameraSource?.setFrameProcessor(BarcodeProcessor(graphicOverlay, viewModel))
            processorConfigured = true
        }
        viewModel.setWorkflowState(WorkflowModel.WorkflowState.DETECTING)
    }

    override fun onPause() {
        super.onPause()
        currentWorkflowState = WorkflowModel.WorkflowState.NOT_STARTED
        stopCameraPreview()
    }

    override fun onDestroyView() {
        cameraSource?.release()
        cameraSource = null
        processorConfigured = false
        _binding = null
        super.onDestroyView()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.flash_button -> {
                flashButton.let {
                    if (it.isSelected) {
                        it.isSelected = false
                        cameraSource?.updateFlashMode(Camera.Parameters.FLASH_MODE_OFF)
                    } else {
                        it.isSelected = true
                        cameraSource?.updateFlashMode(Camera.Parameters.FLASH_MODE_TORCH)
                    }
                }
            }
        }
    }

    private fun saveToClipboard(label: String, saveText: String) {
        val clipboard = activity?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, saveText)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun startCameraPreview() {
        val workflowModel = viewModel
        val cameraSource = this.cameraSource ?: return
        if (!workflowModel.isCameraLive) {
            try {
                workflowModel.markCameraLive()
                preview.start(cameraSource)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to start camera preview!", e)
                cameraSource.release()
                this.cameraSource = null
            }
        }
    }

    private fun stopCameraPreview() {
        val workflowModel = viewModel
        if (workflowModel.isCameraLive) {
            workflowModel.markCameraFrozen()
            flashButton.isSelected = false
            preview.stop()
        }
    }

    private fun setUpWorkflowModel() {
        viewModel.workflowState.observe(viewLifecycleOwner, Observer { workflowState ->
            if (workflowState == null || currentWorkflowState == workflowState) {
                return@Observer
            }

            currentWorkflowState = workflowState
            Log.d(TAG, "Current workflow state: ${workflowState.name}")

            when (workflowState) {
                WorkflowModel.WorkflowState.DETECTING -> {
                    infoText.visibility = View.VISIBLE
                    infoText.setText(R.string.prompt_point_at_a_barcode)
                    startCameraPreview()
                }
                WorkflowModel.WorkflowState.CONFIRMING -> {
                    infoText.visibility = View.VISIBLE
                    infoText.setText(R.string.prompt_move_camera_closer)
                    startCameraPreview()
                }
                WorkflowModel.WorkflowState.SEARCHING -> {
                    infoText.visibility = View.VISIBLE
                    infoText.setText(R.string.prompt_searching)
                    stopCameraPreview()
                }
                WorkflowModel.WorkflowState.DETECTED, WorkflowModel.WorkflowState.SEARCHED -> {
                    infoText.visibility = View.GONE
                    stopCameraPreview()
                }
                else -> infoText.visibility = View.GONE
            }
        })

        viewModel.detectedBarcode.observe(viewLifecycleOwner, Observer { barcode ->
            if (barcode != null) {
                transcriptEditText.setText(barcode.rawValue)
            }
        })
    }


    //PERMISSIONS
    private fun isCameraPermissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        //private const val TAG = "CameraXLivePreview"
        private const val TAG = "LiveBarcodeActivity"
    }

}
