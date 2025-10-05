package com.nash.dubbingstudio.ui.projects

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.nash.dubbingstudio.databinding.FragmentNewProjectBinding

class NewProjectFragment : Fragment() {

    private var _binding: FragmentNewProjectBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewProjectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.btnWriteScript.setOnClickListener {
            // الانتقال لكتابة النص
        }
        
        binding.btnImportSrtOption.setOnClickListener {
            // الانتقال لاستيراد SRT
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}