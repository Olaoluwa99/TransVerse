package com.easit.aiscanner.bottomSheets.theme

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.easit.aiscanner.bottomSheets.ItemClickListener
import com.easit.aiscanner.databinding.BottomSheetThemeBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.lang.RuntimeException

class ThemeBottomSheet: BottomSheetDialogFragment() {

    private var _binding: BottomSheetThemeBinding? = null
    private val binding get() = _binding!!
    private lateinit var themeGroup: RadioGroup

    private var mListener: ItemClickListener? = null
    //private lateinit var prefs: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = BottomSheetThemeBinding.inflate(inflater, container, false)
        //prefs = provideSharedPref()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializations()
        themeGroup.setOnCheckedChangeListener{ theme, checkedId ->
            if(theme.id == 1){
                Toast.makeText(context, "Id = $checkedId", Toast.LENGTH_LONG).show()
                //TODO Show default theme
            }
            if(theme.id == 2){
                Toast.makeText(context, "Id = $checkedId", Toast.LENGTH_LONG).show()
                //TODO Show light theme
            }
            if(theme.id == 3){
                Toast.makeText(context, "Id = $checkedId", Toast.LENGTH_LONG).show()
                //TODO Show dark theme
            }
            //TODO Store new theme in shared preferences
            //prefs.edit().putString("jwt", ).apply()
            //val token = prefs.getString("jwt", null)
        }
    }

    private fun initializations(){
        themeGroup = binding.themeGroup
    }

    fun provideSharedPref(app: Application): SharedPreferences {
        return app.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mListener = if (context is ItemClickListener){
            context
        }else{
            throw RuntimeException(
                "$context Must Implement ItemClickListener"
            )
        }
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
    }

}
class THemeActionBottom {
    companion object{
        const val TAG = "ThemeBottomSheet"
        fun newInstance():ThemeBottomSheet{
            return ThemeBottomSheet()
        }
    }
}