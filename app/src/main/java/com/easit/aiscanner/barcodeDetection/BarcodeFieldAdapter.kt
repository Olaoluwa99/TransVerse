package com.easit.aiscanner.barcodeDetection

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.easit.aiscanner.R

/** Presents a list of field info in the detected barcode.  */
internal class BarcodeFieldAdapter(private val barcodeFieldList: List<BarcodeField>) :
    RecyclerView.Adapter<BarcodeFieldAdapter.BarcodeFieldViewHolder>() {

    internal class BarcodeFieldViewHolder private constructor(view: View) : RecyclerView.ViewHolder(view) {

        private val labelView: TextView = view.findViewById(R.id.barcode_field_label)
        private val valueView: TextView = view.findViewById(R.id.barcode_field_value)

        fun bindBarcodeField(barcodeField: BarcodeField) {
            labelView.text = barcodeField.label
            valueView.text = barcodeField.value
        }

        companion object {

            fun create(parent: ViewGroup): BarcodeFieldViewHolder {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.barcode_field, parent, false)
                return BarcodeFieldViewHolder(view)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BarcodeFieldViewHolder =
        BarcodeFieldViewHolder.create(parent)

    override fun onBindViewHolder(holder: BarcodeFieldViewHolder, position: Int) =
        holder.bindBarcodeField(barcodeFieldList[position])

    override fun getItemCount(): Int =
        barcodeFieldList.size
}
