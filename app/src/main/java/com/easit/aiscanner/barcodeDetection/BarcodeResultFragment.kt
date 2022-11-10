package com.easit.aiscanner.barcodeDetection

import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.easit.aiscanner.R
import com.easit.aiscanner.camera.WorkflowModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/** Displays the bottom sheet to present barcode fields contained in the detected barcode.  */
class BarcodeResultFragment : BottomSheetDialogFragment() {

    private lateinit var viewModel: WorkflowModel

    override fun onCreateView(
        layoutInflater: LayoutInflater,
        viewGroup: ViewGroup?,
        bundle: Bundle?
    ): View {
        viewModel = ViewModelProvider(this).get(WorkflowModel::class.java)
        val view = layoutInflater.inflate(R.layout.barcode_bottom_sheet, viewGroup)

        val arguments = arguments
        val barcodeFieldList: ArrayList<BarcodeField> =
            if (arguments?.containsKey(ARG_BARCODE_FIELD_LIST) == true) {
                arguments.getParcelableArrayList(ARG_BARCODE_FIELD_LIST) ?: ArrayList()
            } else {
                Log.e(TAG, "No barcode field list passed in!")
                ArrayList()
            }

        view.findViewById<RecyclerView>(R.id.barcode_field_recycler_view).apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(activity)
            adapter = BarcodeFieldAdapter(barcodeFieldList)
        }

        return view
    }

    override fun onDismiss(dialogInterface: DialogInterface) {
        activity?.let {
            // Back to working state after the bottom sheet is dismissed.
            //ViewModelProviders.of(it).get(WorkflowModel::class.java).setWorkflowState(WorkflowModel.WorkflowState.DETECTING)
            viewModel.setWorkflowState(WorkflowModel.WorkflowState.DETECTING)
        }
        super.onDismiss(dialogInterface)
    }

    companion object {

        private const val TAG = "BarcodeResultFragment"
        private const val ARG_BARCODE_FIELD_LIST = "arg_barcode_field_list"

        fun show(fragmentManager: FragmentManager, barcodeFieldArrayList: ArrayList<BarcodeField>) {
            val barcodeResultFragment = BarcodeResultFragment()
            barcodeResultFragment.arguments = Bundle().apply {
                putParcelableArrayList(ARG_BARCODE_FIELD_LIST, barcodeFieldArrayList)
            }
            barcodeResultFragment.show(fragmentManager, TAG)
        }

        fun dismiss(fragmentManager: FragmentManager) {
            (fragmentManager.findFragmentByTag(TAG) as BarcodeResultFragment?)?.dismiss()
        }
    }
}
