package com.easit.aiscanner.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import android.provider.MediaStore
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.easit.aiscanner.bottomSheets.theme.THemeActionBottom
import com.easit.aiscanner.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private lateinit var viewModel: SettingsViewModel
    //private lateinit var screenshot: Button

    private var _binding : FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var themeButton: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializations()

        themeButton.setOnClickListener {
            val openBottomSheet = THemeActionBottom.newInstance()
            openBottomSheet.show(
                requireFragmentManager(), THemeActionBottom.TAG
            )
        }
/*
        screenshot.setOnClickListener {
            if(ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED){

            }else{
                requestPermissions(arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE )
                    , 0)
            }
        }*/

    }



    private fun initializations(){
        themeButton = binding.appTheme
        //screenshot = binding.screenshot
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}