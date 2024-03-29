package com.easit.aiscanner.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.easit.aiscanner.R
import com.easit.aiscanner.databinding.ItemListRecentHistoryBinding
import com.easit.aiscanner.model.Scan

class RecentHistoryAdapter (
    private val clickListener: ScanHistoryClickListener,
    private val fontSize: Int):
    ListAdapter<Scan, RecentHistoryViewHolder>(DiffCallback) {

    lateinit var deleteClickListener: OnDeleteClickListener

    interface OnDeleteClickListener{
        fun onImageClick(position: Int)
    }

    fun setOnDeleteClickListener(listener: OnDeleteClickListener){
        deleteClickListener = listener
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Scan>() {
        override fun areItemsTheSame(oldItem: Scan, newItem: Scan): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Scan, newItem: Scan): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentHistoryViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return RecentHistoryViewHolder(deleteClickListener,
            ItemListRecentHistoryBinding.inflate(layoutInflater, parent, false)
        )
    }

    override fun onBindViewHolder(holder: RecentHistoryViewHolder, position: Int) {
        val scanItem = getItem(position)
        holder.bind(clickListener, scanItem, fontSize)
    }
}

class ScanHistoryClickListener(val clickListener: (scan: Scan) -> Unit) {
    fun onClick(scan: Scan) = clickListener(scan)

}

class RecentHistoryViewHolder(
    deleteClickListener: RecentHistoryAdapter.OnDeleteClickListener,
    private var binding: ItemListRecentHistoryBinding
) : RecyclerView.ViewHolder(binding.root) {

    var img1 = R.drawable.ic_baseline_edit_24
    var img2 = R.drawable.ic_baseline_insert_photo_24
    var img3 = R.drawable.ic_baseline_mic_24
    val img4 = R.drawable.ic_baseline_qr_code_scanner_24

    fun bind(clickListener: ScanHistoryClickListener, scan: Scan, myFontSize: Int) {

        binding.scan = scan
        binding.clickListener = clickListener
        binding.run {

            if (scan.scanType == "text") {
                Glide
                    .with(scanType.context)
                    .load(img1)
                    .into(scanType)
                backgroundColor.setImageResource(R.drawable.background_text)
            }
            if (scan.scanType == "image") {
                Glide
                    .with(scanType.context)
                    .load(img2)
                    .into(scanType)
                backgroundColor.setImageResource(R.drawable.background_image)
            }
            if (scan.scanType == "audio") {
                Glide
                    .with(scanType.context)
                    .load(img3)
                    .into(scanType)
                backgroundColor.setImageResource(R.drawable.background_audio)
            }
            if (scan.scanType == "barcode") {
                Glide
                    .with(scanType.context)
                    .load(img4)
                    .into(scanType)
                backgroundColor.setImageResource(R.drawable.background_barcode)
            }
            scanDate.textSize = myFontSize.toFloat()
            scanText.textSize = myFontSize.toFloat()
            scanDate.text = scan.dateTime
            scanText.text = scan.translatedText
            executePendingBindings()
        }
    }
}
