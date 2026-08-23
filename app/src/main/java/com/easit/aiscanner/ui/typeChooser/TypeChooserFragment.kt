package com.easit.aiscanner.ui.typeChooser

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.core.widget.doAfterTextChanged
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
import com.google.android.material.snackbar.Snackbar

class TypeChooserFragment : Fragment() {

    private var _binding: FragmentTypeChooserBinding? = null
    private val binding get() = requireNotNull(_binding) {
        "Binding accessed outside the view lifecycle"
    }

    private val chooserText get() = binding.welcomeText

    private val audioText get() = binding.audioText
    private val imageText get() = binding.imageText
    private val barcodeText get() = binding.barcodeText
    private val textText get() = binding.textText

    private val historyText get() = binding.historyTitle

    private val audioCard get() = binding.openAudioGround
    private val textCard get() = binding.openTextGround
    private val imageCard get() = binding.openImageGround
    private val barcodeCard get() = binding.openBarcodeScanner
    private val launchScanner get() = binding.launchScanner

    private val scanHistoryRecyclerView get() = binding.scanHistoryRecyclerView

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
        setFontSize()
        setUpRecyclerViewAdapter()
        binding.historySearch.doAfterTextChanged { viewModel.setSearchQuery(it?.toString().orEmpty()) }
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

    private fun setFontSize() {
        //
        appFontSize = viewModel.selectedFontSize
        chooserText.textSize = (appFontSize + 6).toFloat()
        textText.textSize = (appFontSize + 2).toFloat()
        audioText.textSize = (appFontSize + 2).toFloat()
        barcodeText.textSize = (appFontSize + 2).toFloat()
        imageText.textSize = (appFontSize + 2).toFloat()

        launchScanner.textSize = appFontSize.toFloat()

        historyText.textSize = (appFontSize + 6).toFloat()
    }

    private fun setUpRecyclerViewAdapter(){
        val adapter = RecentHistoryAdapter(
            ScanHistoryClickListener(
                clickListener = { scan -> openScan(scan) },
                favoriteListener = viewModel::toggleFavorite
            ),
            viewModel.selectedFontSize
        )

        //On swipe action performed
        val swipeToDeleteCallback = object : SwipeToDeleteCallback(){
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val scanObject = adapter.currentList.getOrNull(position) ?: return
                viewModel.deleteScan(scanObject)
                Snackbar.make(scanHistoryRecyclerView, R.string.scan_deleted, Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_undo) { viewModel.restoreScan(scanObject) }
                    .show()
            }
        }
        val itemTouchHelper = ItemTouchHelper(swipeToDeleteCallback)
        itemTouchHelper.attachToRecyclerView(scanHistoryRecyclerView)

        scanHistoryRecyclerView.adapter = adapter

        //On observation
        viewModel.allScans.observe(this.viewLifecycleOwner) { items ->
            adapter.submitList(items)
            binding.historyEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun openScan(scan: com.easit.aiscanner.model.Scan) {
        val args = Bundle().apply { putString("selectedScanId", scan.id) }
        val action = when (scan.scanType) {
            "audio" -> R.id.action_typeChooserFragment_to_audioGroundFragment
            "image" -> R.id.action_typeChooserFragment_to_imageGroundFragment
            "barcode" -> R.id.action_typeChooserFragment_to_barcodeLiveFragment
            else -> R.id.action_typeChooserFragment_to_homeFragment
        }
        findNavController().navigate(action, args)
    }

    override fun onDestroyView() {
        scanHistoryRecyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }

}
