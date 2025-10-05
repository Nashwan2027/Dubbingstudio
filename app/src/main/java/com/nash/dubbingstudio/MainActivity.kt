/**
 * Copyright (c) 2024 Nashwan.
 * 
 * Licensed under the MIT License.
 * See the LICENSE file for details.
 */

package com.nash.dubbingstudio

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import com.nash.dubbingstudio.databinding.ActivityMainBinding
import com.nash.dubbingstudio.model.DialogueCard
import com.nash.dubbingstudio.ui.ViewPagerAdapter
import com.nash.dubbingstudio.ui.home.HomeFragment
import com.nash.dubbingstudio.ui.projects.ProjectsFragment
import com.nash.dubbingstudio.ui.settings.SettingsFragment
import com.nash.dubbingstudio.ui.srtimport.SrtImportActivity
import com.nash.dubbingstudio.ui.studio.DubbingStudioActivity
import com.nash.dubbingstudio.ui.tools.ToolsFragment
import com.nash.dubbingstudio.ui.writescript.WriteScriptActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewPagerAdapter: ViewPagerAdapter
    private var isFabMenuOpen = false
    private var currentPage = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupBottomNavigation()
        setupFabMenu()
        setupOutsideClickListener()
        handleOnBackPressed()
        updateNavigationState(0)
    }

    private fun setupViewPager() {
        viewPagerAdapter = ViewPagerAdapter(this)
        viewPagerAdapter.addFragment(HomeFragment())
        viewPagerAdapter.addFragment(ProjectsFragment())
        viewPagerAdapter.addFragment(ToolsFragment())
        viewPagerAdapter.addFragment(SettingsFragment())

        binding.viewPager.adapter = viewPagerAdapter
        binding.viewPager.isUserInputEnabled = true
        
        binding.viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                updateNavigationState(position)
                if (isFabMenuOpen) {
                    closeFabMenu()
                }
            }
        })
    }

    private fun setupBottomNavigation() {
        binding.navHome.setOnClickListener { 
            setCurrentPage(0)
            if (isFabMenuOpen) closeFabMenu()
        }
        binding.navProjects.setOnClickListener { 
            setCurrentPage(1)
            if (isFabMenuOpen) closeFabMenu()
        }
        binding.navTools.setOnClickListener { 
            setCurrentPage(2)
            if (isFabMenuOpen) closeFabMenu()
        }
        binding.navSettings.setOnClickListener { 
            setCurrentPage(3)
            if (isFabMenuOpen) closeFabMenu()
        }
    }

    private fun setCurrentPage(position: Int) {
        if (position in 0 until viewPagerAdapter.itemCount) {
            binding.viewPager.currentItem = position
        }
    }

    private fun updateNavigationState(selectedPosition: Int) {
        resetNavigationItems()
        
        val selectedIconColor = ContextCompat.getColor(this, R.color.nav_icon_selected)
        val selectedTextColor = ContextCompat.getColor(this, R.color.nav_text_selected)
        
        when (selectedPosition) {
            0 -> {
                binding.iconHome.setColorFilter(selectedIconColor)
                binding.textHome.setTextColor(selectedTextColor)
            }
            1 -> {
                binding.iconProjects.setColorFilter(selectedIconColor)
                binding.textProjects.setTextColor(selectedTextColor)
            }
            2 -> {
                binding.iconTools.setColorFilter(selectedIconColor)
                binding.textTools.setTextColor(selectedTextColor)
            }
            3 -> {
                binding.iconSettings.setColorFilter(selectedIconColor)
                binding.textSettings.setTextColor(selectedTextColor)
            }
        }
    }

    private fun resetNavigationItems() {
        val normalIconColor = ContextCompat.getColor(this, R.color.nav_icon_normal)
        val normalTextColor = ContextCompat.getColor(this, R.color.nav_text_normal)
        
        binding.iconHome.setColorFilter(normalIconColor)
        binding.textHome.setTextColor(normalTextColor)
        
        binding.iconProjects.setColorFilter(normalIconColor)
        binding.textProjects.setTextColor(normalTextColor)
        
        binding.iconTools.setColorFilter(normalIconColor)
        binding.textTools.setTextColor(normalTextColor)
        
        binding.iconSettings.setColorFilter(normalIconColor)
        binding.textSettings.setTextColor(normalTextColor)
    }

    private fun setupFabMenu() {
        binding.fabMain.setOnClickListener {
            if (isFabMenuOpen) {
                closeFabMenu()
            } else {
                showFabMenu()
            }
        }

        binding.fabRecord.setOnClickListener {
            // يمكنك تفعيل وظيفة التسجيل هنا عندما تصبح جاهزة
            // navigateToRecording()
            closeFabMenu()
        }

        binding.fabImport.setOnClickListener {
            navigateToSrtImport()
            closeFabMenu()
        }

        binding.fabTranslate.setOnClickListener {
            // يمكنك تفعيل وظيفة الترجمة هنا عندما تصبح جاهزة
            // navigateToTranslation()
            closeFabMenu()
        }
    }

    private fun showFabMenu() {
        isFabMenuOpen = true
        binding.fabRecord.visibility = View.VISIBLE
        binding.fabImport.visibility = View.VISIBLE
        binding.fabTranslate.visibility = View.VISIBLE

        val distanceX = resources.getDimension(R.dimen.fab_popup_distance_x)
        val distanceY = resources.getDimension(R.dimen.fab_popup_distance_y)
        val distanceDiagonal = resources.getDimension(R.dimen.fab_popup_distance_diagonal)

        // 1. زر التسجيل (Record): يتحرك رأسيًا فقط للأعلى
        binding.fabRecord.animate()
            .translationY(-distanceY) // ضعف المسافة للأعلى
            .alpha(1f).setDuration(300).start()

        // 2. زر الاستيراد (Import): يتحرك قطريًا (إلى الأعلى واليسار)
        binding.fabImport.animate()
            .translationX(-distanceDiagonal) // ضعف المسافة لليسار
            .translationY(-distanceDiagonal) // ضعف المسافة للأعلى
            .alpha(1f).setDuration(300).setStartDelay(50).start()

        // 3. زر الترجمة (Translate): يتحرك قطريًا (إلى الأعلى واليمين)
        binding.fabTranslate.animate()
            .translationX(distanceDiagonal) // ضعف المسافة لليمين
            .translationY(-distanceDiagonal) // ضعف المسافة للأعلى
            .alpha(1f).setDuration(300).setStartDelay(100).start()
            
        // تدوير الزر الرئيسي
        binding.fabMain.animate()
            .rotation(45f)
            .setDuration(300)
            .start()
            
        // إظهار overlay للضغط على أي جزء
        binding.overlayView.visibility = View.VISIBLE
        binding.overlayView.alpha = 0f
        binding.overlayView.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }

    private fun closeFabMenu() {
        isFabMenuOpen = false
        
        binding.fabRecord.animate().alpha(0f)
            .setDuration(300).withEndAction { 
                binding.fabRecord.visibility = View.GONE 
                binding.fabRecord.translationY = 0f // إعادة التعيين بعد الانتهاء
            }.start()
            
        binding.fabImport.animate().alpha(0f)
            .setDuration(300).setStartDelay(50).withEndAction { 
                binding.fabImport.visibility = View.GONE 
                binding.fabImport.translationX = 0f
                binding.fabImport.translationY = 0f
            }.start()
            
        binding.fabTranslate.animate().alpha(0f)
            .setDuration(300).setStartDelay(100).withEndAction { 
                binding.fabTranslate.visibility = View.GONE 
                binding.fabTranslate.translationX = 0f
                binding.fabTranslate.translationY = 0f
            }.start()
            
        // تدوير الزر الرئيسي مرة أخرى إلى 0
        binding.fabMain.animate()
            .rotation(0f)
            .setDuration(300)
            .start()
            
        // إخفاء overlay
        binding.overlayView.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                binding.overlayView.visibility = View.GONE
            }
            .start()
    }

    private fun showFabWithAnimation(fab: com.google.android.material.floatingactionbutton.FloatingActionButton) {
        fab.visibility = View.VISIBLE
        
        val scaleX = ObjectAnimator.ofFloat(fab, View.SCALE_X, 0f, 1f)
        val scaleY = ObjectAnimator.ofFloat(fab, View.SCALE_Y, 0f, 1f)
        val alpha = ObjectAnimator.ofFloat(fab, View.ALPHA, 0f, 1f)
        
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleX, scaleY, alpha)
        animatorSet.duration = 300
        animatorSet.start()
    }

    private fun hideFabWithAnimation(fab: com.google.android.material.floatingactionbutton.FloatingActionButton) {
        val scaleX = ObjectAnimator.ofFloat(fab, View.SCALE_X, 1f, 0f)
        val scaleY = ObjectAnimator.ofFloat(fab, View.SCALE_Y, 1f, 0f)
        val alpha = ObjectAnimator.ofFloat(fab, View.ALPHA, 1f, 0f)
        
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleX, scaleY, alpha)
        animatorSet.duration = 300
        animatorSet.start()
        
        fab.postDelayed({
            fab.visibility = View.GONE
        }, 300)
    }

    private fun rotateFab(fab: com.google.android.material.floatingactionbutton.FloatingActionButton, rotation: Float) {
        fab.animate()
            .rotation(rotation)
            .setDuration(300)
            .start()
    }

    private fun setupOutsideClickListener() {
        // إضافة النقر على overlay لإغلاق القائمة
        binding.overlayView.setOnClickListener {
            if (isFabMenuOpen) {
                closeFabMenu()
            }
        }
        
        binding.viewPager.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN && isFabMenuOpen) {
                closeFabMenu()
            }
            false
        }
        
        binding.bottomAppBar.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN && isFabMenuOpen) {
                closeFabMenu()
            }
            false
        }
    }

    private fun handleOnBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isFabMenuOpen) {
                    closeFabMenu()
                } else {
                    if (binding.viewPager.currentItem != 0) {
                        binding.viewPager.currentItem = 0
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    fun navigateToWriteScript() {
        val intent = Intent(this, WriteScriptActivity::class.java)
        val options = ActivityOptionsCompat.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out)
        startActivity(intent, options.toBundle())
    }

    fun navigateToSrtImport() {
        val intent = Intent(this, SrtImportActivity::class.java)
        val options = ActivityOptionsCompat.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out)
        startActivity(intent, options.toBundle())
    }

    fun navigateToDubbingStudio(dialogueCards: Array<DialogueCard>) {
        val intent = Intent(this, DubbingStudioActivity::class.java).apply {
            putExtra("dialogue_cards", dialogueCards)
        }
        val options = ActivityOptionsCompat.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out)
        startActivity(intent, options.toBundle())
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.viewPager.adapter = null
    }
}