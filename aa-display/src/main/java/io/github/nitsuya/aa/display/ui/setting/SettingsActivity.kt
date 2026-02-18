package io.github.nitsuya.aa.display.ui.setting

import android.os.Bundle
import android.widget.Toast
import androidx.preference.PreferenceFragmentCompat
import com.topjohnwu.superuser.Shell
import io.github.duzhaokun123.template.bases.BaseActivity
import io.github.nitsuya.aa.display.BuildConfig
import io.github.nitsuya.aa.display.R
import io.github.nitsuya.aa.display.databinding.ActivitySettingsBinding
import io.github.nitsuya.aa.display.util.AADisplayConfig


class SettingsActivity : BaseActivity<ActivitySettingsBinding>(ActivitySettingsBinding::class.java) {
    override fun initViews() {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fl_root, SettingsFragment())
            .commit()
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            requireContext().theme.applyStyle(rikka.material.preference.R.style.ThemeOverlay_Rikka_Material3_Preference, true)
            preferenceManager.apply {
                sharedPreferencesName = AADisplayConfig.ConfigName
                sharedPreferencesMode = MODE_WORLD_READABLE
            }
            setPreferencesFromResource(R.xml.pref_aadisplay_config, rootKey)

            findPreference<rikka.material.preference.MaterialSwitchPreference>("CloseLauncherDashboard")?.setOnPreferenceChangeListener { _, _ ->
                // Restart both Android Auto and AADisplay after preference is saved
                view?.postDelayed({
                    Toast.makeText(requireContext(), "Restarting Android Auto and AADisplay...", Toast.LENGTH_SHORT).show()
                    Shell.cmd(
                        "am force-stop com.google.android.projection.gearhead",
                        "am force-stop ${BuildConfig.APPLICATION_ID}",
                        "am start -n ${BuildConfig.APPLICATION_ID}/${BuildConfig.APPLICATION_ID}.ui.main.MainActivity"
                    ).exec()
                }, 300)
                true
            }
        }
    }



}