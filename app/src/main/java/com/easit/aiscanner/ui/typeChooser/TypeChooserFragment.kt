package com.easit.aiscanner.ui.typeChooser

import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.easit.aiscanner.R
import com.easit.aiscanner.adapters.RecentHistoryAdapter
import com.easit.aiscanner.adapters.ScanHistoryClickListener
import com.easit.aiscanner.databinding.FragmentTypeChooserBinding
import com.google.android.material.card.MaterialCardView

class TypeChooserFragment : Fragment() {

    private var _binding: FragmentTypeChooserBinding? = null
    private val binding get() = _binding!!

    private lateinit var audioCard: MaterialCardView
    private lateinit var textCard: MaterialCardView
    private lateinit var imageCard: MaterialCardView
    private lateinit var barcodeCard: MaterialCardView
    private lateinit var selectedScanId: String

    private lateinit var scanHistoryRecyclerView: RecyclerView

    private lateinit var viewModel: TypeChooserViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewModel = ViewModelProvider(this).get(TypeChooserViewModel::class.java)
        _binding = FragmentTypeChooserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializations()
        viewModel.getAllScansHistory()
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
            findNavController().navigate(R.id.action_typeChooserFragment_to_barcodeCaptureFragment, Bundle().apply {
                putString("selectedScanId", "")
            })
        }
    }

    private fun initializations(){
        audioCard = binding.openAudioGround
        textCard = binding.openTextGround
        imageCard = binding.openImageGround
        barcodeCard = binding.openBarcodeScanner
        scanHistoryRecyclerView = binding.scanHistoryRecyclerView
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
            if (scan.scanType == "barcode"){
                findNavController()
                    .navigate(R.id.action_typeChooserFragment_to_barcodeCaptureFragment, Bundle().apply {
                        putString("selectedScanId", selectedScanId)
                    })
            }
        })

        scanHistoryRecyclerView.adapter = adapter

        viewModel.allScans.observe(this.viewLifecycleOwner) { items ->
            items.let {
                adapter.submitList(it)
            }
        }
    }

}