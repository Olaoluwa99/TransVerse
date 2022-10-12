package com.easit.aiscanner.adapters

import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import com.easit.aiscanner.model.Scan
import com.easit.aiscanner.ui.typeChooser.ScanHistoryStatus

@BindingAdapter("listAllScansHistory")
fun bindHomepageRecyclerView(recyclerView: RecyclerView, data: List<Scan>?) {
    val adapter = recyclerView.adapter!! as RecentHistoryAdapter
    adapter.submitList(data)
}

@BindingAdapter("errorScanHistoryStatus")
fun bindErrorStatus(statusImageView: ImageView, status: ScanHistoryStatus?) {
    when (status) {
        ScanHistoryStatus.LOADING -> {
            statusImageView.visibility = View.GONE
        }
        ScanHistoryStatus.ERROR -> {
            statusImageView.visibility = View.VISIBLE
        }
        ScanHistoryStatus.DONE -> {
            statusImageView.visibility = View.GONE
        }
        ScanHistoryStatus.NO_PROJECT -> {
            statusImageView.visibility = View.GONE
        }
    }
}

@BindingAdapter("loadingScanHistoryStatus")
fun bindLoadingStatus(statusProgressDialog: ProgressBar, status: ScanHistoryStatus?) {
    when (status) {
        ScanHistoryStatus.LOADING -> {
            statusProgressDialog.visibility = View.VISIBLE
        }
        ScanHistoryStatus.ERROR -> {
            statusProgressDialog.visibility = View.GONE
        }
        ScanHistoryStatus.DONE -> {
            statusProgressDialog.visibility = View.GONE
        }
        ScanHistoryStatus.NO_PROJECT -> {
            statusProgressDialog.visibility = View.GONE
        }
    }
}

@BindingAdapter("emptyScanHistoryStatus")
fun bindEmptyStatus(noProjectText: TextView, status: ScanHistoryStatus?) {
    when (status) {
        ScanHistoryStatus.LOADING -> {
            noProjectText.visibility = View.GONE
        }
        ScanHistoryStatus.ERROR -> {
            noProjectText.visibility = View.GONE
        }
        ScanHistoryStatus.DONE -> {
            noProjectText.visibility = View.GONE
        }
        ScanHistoryStatus.NO_PROJECT -> {
            noProjectText.visibility = View.VISIBLE
        }
    }
}