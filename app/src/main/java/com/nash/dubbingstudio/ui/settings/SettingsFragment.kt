package com.nash.dubbingstudio.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.nash.dubbingstudio.R

class SettingsFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        setupPreferences()
    }

    override fun onResume() {
        super.onResume()
        preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        updatePreferenceSummaries()
    }

    override fun onPause() {
        super.onPause()
        preferenceScreen.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            "pref_dark_mode" -> {
                val darkMode = sharedPreferences?.getBoolean(key, false) ?: false
                applyDarkMode(darkMode)
            }
            else -> {
                // تحديث الملخص لأي ListPreference يتم تغييرها
                updateListPreferenceSummary(key)
            }
        }
    }

    private fun setupPreferences() {
        // يمكنك هنا إضافة منطق لأزرار الإعدادات التي لا تحفظ قيمة
        findPreference<Preference>("pref_storage_management")?.setOnPreferenceClickListener {
            Toast.makeText(requireContext(), "سيتم تفعيل هذه الميزة قريباً", Toast.LENGTH_SHORT).show()
            true
        }

        findPreference<Preference>("pref_clear_cache")?.setOnPreferenceClickListener {
            Toast.makeText(requireContext(), "تم مسح الذاكرة المؤقتة (محاكاة)", Toast.LENGTH_SHORT).show()
            true
        }

        findPreference<Preference>("pref_about")?.setOnPreferenceClickListener {
            showAboutDialog()
            true
        }
    }

    private fun updatePreferenceSummaries() {
        updateListPreferenceSummary("pref_voice_engine")
        updateListPreferenceSummary("pref_default_language")
        updateListPreferenceSummary("pref_voice_speed")
        updateListPreferenceSummary("pref_export_format")
        updateListPreferenceSummary("pref_audio_quality")
    }

    private fun updateListPreferenceSummary(key: String?) {
        key?.let {
            val preference = findPreference<ListPreference>(it)
            preference?.summary = preference?.entry
        }
    }

    private fun applyDarkMode(enabled: Boolean) {
        if (enabled) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun showAboutDialog() {
        // يمكنك هنا عرض مربع حوار بمعلومات التطبيق
        Toast.makeText(requireContext(), "استوديو الدبلجة - إصدار 1.0.0", Toast.LENGTH_LONG).show()
    }
}
