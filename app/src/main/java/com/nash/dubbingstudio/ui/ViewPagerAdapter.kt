package com.nash.dubbingstudio.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    private val fragmentList = mutableListOf<Fragment>()

    fun addFragment(fragment: Fragment) {
        fragmentList.add(fragment)
    }

    override fun getItemCount(): Int {
        return fragmentList.size
    }

    override fun createFragment(position: Int): Fragment {
        return if (position in 0 until fragmentList.size) {
            fragmentList[position]
        } else {
            // ✅ إرجاع فراغمنت افتراضي آمن
            fragmentList.firstOrNull() ?: throw IllegalStateException("No fragments available")
        }
    }
    
    // ✅ دالة مساعدة للحصول على Fragment بالاسم
    fun getFragmentAt(position: Int): Fragment? {
        return if (position in 0 until fragmentList.size) {
            fragmentList[position]
        } else {
            null
        }
    }
}