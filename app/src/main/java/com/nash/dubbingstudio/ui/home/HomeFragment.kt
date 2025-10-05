package com.nash.dubbingstudio.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.Fragment
import com.nash.dubbingstudio.databinding.FragmentHomeBinding
import com.nash.dubbingstudio.ui.srtimport.SrtImportActivity
import com.nash.dubbingstudio.ui.writescript.WriteScriptActivity

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, 
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.cardWriteScript.setOnClickListener {
            navigateToWriteScript()
        }
        
        binding.cardImportSrt.setOnClickListener {
            navigateToSrtImport()
        }
        
        Toast.makeText(requireContext(), "مرحباً في استوديو الدبلجة! اختر طريقة البدء", Toast.LENGTH_SHORT).show()
        setupCardAnimations()
    }
    
    private fun setupCardAnimations() {
        val cards = listOf(binding.cardWriteScript, binding.cardImportSrt)
        
        cards.forEach { card ->
            card.setOnTouchListener { view, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    }
                }
                false
            }
        }
    }
    
    private fun navigateToWriteScript() {
        try {
            val intent = Intent(requireContext(), WriteScriptActivity::class.java)
            // استخدام ActivityOptionsCompat للتعامل مع الانتقالات بشكل حديث وآمن
            val options = ActivityOptionsCompat.makeCustomAnimation(
                requireContext(),
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            startActivity(intent, options.toBundle())
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "حدث خطأ في فتح شاشة الكتابة", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun navigateToSrtImport() {
        try {
            val intent = Intent(requireContext(), SrtImportActivity::class.java)
            // استخدام ActivityOptionsCompat للتعامل مع الانتقالات بشكل حديث وآمن
            val options = ActivityOptionsCompat.makeCustomAnimation(
                requireContext(),
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            startActivity(intent, options.toBundle())
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "حدث خطأ في فتح شاشة الاستيراد", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
