package com.easit.aiscanner.ui.settings

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.easit.aiscanner.databinding.FragmentThirdPartyNoticeBinding

class ThirdPartyNoticeFragment : Fragment() {

    private var _binding: FragmentThirdPartyNoticeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentThirdPartyNoticeBinding.inflate(inflater, container, false)
        return binding.root
    }
}