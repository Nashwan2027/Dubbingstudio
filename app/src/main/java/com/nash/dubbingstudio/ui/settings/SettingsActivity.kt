/**
 * Copyright (c) 2024 Nashwan.
 * 
 * Licensed under the MIT License.
 * See the LICENSE file for details.
 */

package com.nash.dubbingstudio.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.nash.dubbingstudio.R
import com.nash.dubbingstudio.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commit()
    }
}
