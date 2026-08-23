package com.easit.aiscanner.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.easit.aiscanner.R
import com.easit.aiscanner.databinding.ItemListRecentHistoryBinding
import com.easit.aiscanner.model.Scan

class RecentHistoryAdapter(
    private val clickListener: ScanHistoryClickListener,
    private val fontSize: Int):
    ListAdapter<Scan, RecentHistoryViewHolder>(DiffCallback) {

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
        return RecentHistoryViewHolder(ItemListRecentHistoryBinding.inflate(layoutInflater, parent, false))
    }

    override fun onBindViewHolder(holder: RecentHistoryViewHolder, position: Int) {
        val scanItem = getItem(position)
        holder.bind(clickListener, scanItem, fontSize)
    }
}

class ScanHistoryClickListener(
    private val clickListener: (scan: Scan) -> Unit,
    private val favoriteListener: (scan: Scan) -> Unit = {}
) {
    fun onClick(scan: Scan) = clickListener(scan)
    fun onFavorite(scan: Scan) = favoriteListener(scan)
}

class RecentHistoryViewHolder(
    private var binding: ItemListRecentHistoryBinding
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(clickListener: ScanHistoryClickListener, scan: Scan, myFontSize: Int) {

        binding.scan = scan
        binding.clickListener = clickListener
        binding.run {

            val (icon, background) = when (scan.scanType) {
                "image" -> R.drawable.ic_baseline_insert_photo_24 to R.drawable.background_image
                "audio" -> R.drawable.ic_baseline_mic_24 to R.drawable.background_audio
                "barcode" -> R.drawable.ic_baseline_qr_code_scanner_24 to R.drawable.background_barcode
                else -> R.drawable.ic_baseline_edit_24 to R.drawable.background_text
            }
            scanType.setImageResource(icon)
            backgroundColor.setImageResource(background)
            scanDate.textSize = myFontSize.toFloat()
            scanText.textSize = myFontSize.toFloat()
            favoriteScan.setImageResource(
                if (scan.isFavorite) R.drawable.ic_star_24 else R.drawable.ic_star_border_24
            )
            executePendingBindings()
        }
    }
}
