package com.easit.aiscanner.ui.typeChooser

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.easit.aiscanner.MainActivity
import com.easit.aiscanner.R
import com.easit.aiscanner.adapters.RecentHistoryAdapter
import com.easit.aiscanner.adapters.ScanHistoryClickListener
import com.easit.aiscanner.adapters.SwipeToDeleteCallback
import com.easit.aiscanner.databinding.FragmentTypeChooserBinding
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar

class TypeChooserFragment : Fragment() {

    private var _binding: FragmentTypeChooserBinding? = null
    private val binding get() = _binding!!

    private lateinit var chooserText: TextView

    private lateinit var audioText: TextView
    private lateinit var imageText: TextView
    private lateinit var barcodeText: TextView
    private lateinit var textText: TextView

    private lateinit var historyText: TextView

    private lateinit var audioCard: MaterialCardView
    private lateinit var textCard: MaterialCardView
    private lateinit var imageCard: MaterialCardView
    private lateinit var barcodeCard: MaterialCardView
    private lateinit var selectedScanId: String

    private lateinit var launchScanner: Button

    private lateinit var scanHistoryRecyclerView: RecyclerView

    val viewModel by activityViewModels<TypeChooserViewModel>()
    var appFontSize = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentTypeChooserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //Transition
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }

        //Main
        initializations()
        setFontSize()
        setUpRecyclerViewAdapter()
        audioCard.setOnClickListener {
            findNavController().navigate(
                R.id.action_typeChooserFragment_to_audioGroundFragment, Bundle().apply {
                    putString("selectedScanId", "")
                })
        }
        textCard.setOnClickListener {
            findNavController().navigate(R.id.action_typeChooserFragment_to_homeFragment, Bundle().apply {
                putString("selectedScanId", "")
            })
        }
        imageCard.setOnClickListener {
            findNavController().navigate(R.id.action_typeChooserFragment_to_imageGroundFragment, Bundle().apply {
                putString("selectedScanId", "")
            })
        }
        barcodeCard.setOnClickListener {
            findNavController().navigate(R.id.action_typeChooserFragment_to_barcodeLiveFragment, Bundle().apply {
                putString("selectedScanId", "")
            })
        }
        launchScanner.setOnClickListener {
            (activity as MainActivity).launchScanner()
        }
    }

    private fun initializations(){
        chooserText = binding.welcomeText

        audioCard = binding.openAudioGround
        textCard = binding.openTextGround
        imageCard = binding.openImageGround
        barcodeCard = binding.openBarcodeScanner
        scanHistoryRecyclerView = binding.scanHistoryRecyclerView

        launchScanner = binding.launchScanner

        textText = binding.textText
        audioText = binding.audioText
        imageText = binding.imageText
        barcodeText = binding.barcodeText

        historyText = binding.historyTitle

    }

    private fun setFontSize() {
        //
        appFontSize = viewModel.selectedFontSize!!
        chooserText.textSize = (appFontSize + 6).toFloat()
        textText.textSize = (appFontSize + 2).toFloat()
        audioText.textSize = (appFontSize + 2).toFloat()
        barcodeText.textSize = (appFontSize + 2).toFloat()
        imageText.textSize = (appFontSize + 2).toFloat()

        launchScanner.textSize = appFontSize.toFloat()

        historyText.textSize = (appFontSize + 6).toFloat()
    }

    fun deleteScan(id: String){
        viewModel.getSelectedScanObject(id)
        viewModel.currentHistoryItem.observe(
            viewLifecycleOwner,
            androidx.lifecycle.Observer { scanObject ->
                viewModel.deleteScan(scanObject)
            })
    }

    private fun setUpRecyclerViewAdapter(){
        val adapter = RecentHistoryAdapter(ScanHistoryClickListener { scan ->
            viewModel.onScanObjectClicked(scan)
            selectedScanId = scan.id

            if (scan.scanType == "text"){
                findNavController()
                    .navigate(R.id.action_typeChooserFragment_to_homeFragment, Bundle().apply {
                        putString("selectedScanId", selectedScanId)
                    })
            }
            if (scan.scanType == "audio"){
                findNavController()
                    .navigate(R.id.action_typeChooserFragment_to_audioGroundFragment, Bundle().apply {
                        putString("selectedScanId", selectedScanId)
                    })
            }
            if (scan.scanType == "image"){
                findNavController()
                    .navigate(R.id.action_typeChooserFragment_to_imageGroundFragment, Bundle().apply {
                        putString("selectedScanId", selectedScanId)
                    })
            }
        }, viewModel.selectedFontSize!!)

        //On swipe action performed
        val swipeToDeleteCallback = object : SwipeToDeleteCallback(){
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val scanObject = viewModel.allScans.value?.get(position)
                viewModel.deleteScan(scanObject!!)
                scanHistoryRecyclerView.adapter?.notifyItemRemoved(position)
                Snackbar.make(scanHistoryRecyclerView, "Undo", Snackbar.LENGTH_LONG)
                    .setAction("Undo", View.OnClickListener {
                        viewModel.addScan(scanObject)
                    }).show()
            }
        }
        val itemTouchHelper = ItemTouchHelper(swipeToDeleteCallback)
        itemTouchHelper.attachToRecyclerView(scanHistoryRecyclerView)

        //On delete button clicked
        adapter.setOnDeleteClickListener(object : RecentHistoryAdapter.OnDeleteClickListener {
            override fun onImageClick(position: Int) {
                val scanObject = viewModel.allScans.value?.get(position)
                viewModel.deleteScan(scanObject!!)
                Snackbar.make(scanHistoryRecyclerView, "Undo", Snackbar.LENGTH_LONG)
                    .setAction("Undo", View.OnClickListener {
                        viewModel.addScan(scanObject)
                    }).show()
            }
        })

        scanHistoryRecyclerView.adapter = adapter

        //On observation
        viewModel.allScans.observe(this.viewLifecycleOwner) { items ->
            items.let {
                adapter.submitList(it)
            }
        }
    }

}