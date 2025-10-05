/**
 * Copyright (c) 2024 Nashwan.
 * 
 * Licensed under the MIT License.
 * See the LICENSE file for details.
 */

package com.nash.dubbingstudio.ui.projects

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.nash.dubbingstudio.databinding.FragmentProjectsBinding

class ProjectsFragment : Fragment() {

    private var _binding: FragmentProjectsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProjectsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // تهيئة واجهة المشاريع هنا
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}