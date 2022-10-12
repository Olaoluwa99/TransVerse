package com.easit.aiscanner.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import com.easit.aiscanner.R
import com.easit.aiscanner.databinding.FragmentLinearBinding
import com.easit.aiscanner.databinding.FragmentOtherBinding
import com.google.android.material.chip.ChipGroup

class LinearFragment : Fragment() {

    private var _binding: FragmentLinearBinding? = null
    private val binding get() = _binding!!

    private lateinit var cardView: CardView
    private lateinit var translationTextview: TextView
    private lateinit var translationCopy: ImageButton
    private lateinit var entityTextview: TextView
    private lateinit var entityCopy: ImageButton
    private lateinit var smartReplyGroup: ChipGroup

    private lateinit var openTranslation: ImageButton
    private lateinit var openEntity: ImageButton
    private lateinit var openReplies: ImageButton

    private lateinit var closeLayout: ImageButton

    private lateinit var translationLayout: LinearLayout
    private lateinit var entityLayout: LinearLayout
    private lateinit var smartReplyLayout: HorizontalScrollView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentLinearBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializations()
        openTranslation.setOnClickListener {
            if (translationLayout.isVisible){
                translationLayout.visibility = View.GONE
                translationTextview.visibility = View.GONE
                translationCopy.visibility = View.GONE
            }
            translationLayout.visibility = View.VISIBLE
            translationTextview.visibility = View.VISIBLE
            translationCopy.visibility = View.VISIBLE
        }
        openEntity.setOnClickListener {
            if (entityLayout.isVisible){
                entityLayout.visibility = View.GONE
                entityTextview.visibility = View.GONE
                entityCopy.visibility = View.GONE
            }
            entityLayout.visibility = View.VISIBLE
            entityTextview.visibility = View.VISIBLE
            entityCopy.visibility = View.VISIBLE
        }
        openReplies.setOnClickListener {
            if (smartReplyLayout.isVisible){
                smartReplyLayout.visibility = View.GONE
                smartReplyGroup.visibility = View.GONE
            }
            smartReplyLayout.visibility = View.VISIBLE
            smartReplyGroup.visibility = View.VISIBLE
        }
    }

    private fun initializations(){
        translationTextview = binding.translationTextview
        translationCopy = binding.translationCopy
        entityTextview = binding.entityTextview
        entityCopy = binding.entityCopy
        smartReplyGroup = binding.smartReplyGroup

        openTranslation = binding.openTranslation
        openEntity = binding.openEntity
        openReplies = binding.openReplies

        closeLayout = binding.closeLayout

        smartReplyLayout = binding.smartReplyLayout
        translationLayout = binding.translationLayout
        entityLayout = binding.entityLayout

    }
}