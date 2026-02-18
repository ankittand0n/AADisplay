package io.github.nitsuya.aa.display.ui.main

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.topjohnwu.superuser.Shell
import io.github.duzhaokun123.template.bases.BaseActivity
import io.github.nitsuya.aa.display.BuildConfig
import io.github.nitsuya.aa.display.CoreApi
import io.github.nitsuya.aa.display.R
import io.github.nitsuya.aa.display.databinding.ActivityMainBinding
import io.github.nitsuya.aa.display.util.AADisplayConfig
import io.github.nitsuya.template.bases.getAttr


class MainActivity: BaseActivity<ActivityMainBinding>(ActivityMainBinding::class.java, Config.NO_BACK, Config.LAYOUT_MATCH_HORI) {
    companion object {
        const val TAG = "AADisplay_MainActivity"
    }

    private var initXposed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        ActivityMainBinding.inflate(LayoutInflater.from(this))
        super.onCreate(savedInstanceState)
    }

    override fun initViews() {
        super.initViews()
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fl_settings, SettingsFragment())
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

    @SuppressLint("SetTextI18n")
    override fun initData() {
        val buildTime = CoreApi.buildTime
        Log.d(TAG, "buildtime: $buildTime ${BuildConfig.BUILD_TIME}")
        when(buildTime) {
            0L -> {
                baseBinding.ivIcon.setImageResource(R.drawable.ic_error_outline_24)
                baseBinding.tvActive.setText(R.string.not_activated)
                baseBinding.tvVersion.text = ""
                val colorError = android.R.attr.colorError
                val colorOnError = theme.getAttr(com.google.android.material.R.attr.colorOnError).data
                baseBinding.mcvStatus.setCardBackgroundColor(colorError)
                baseBinding.mcvStatus.outlineAmbientShadowColor = colorError
                baseBinding.mcvStatus.outlineSpotShadowColor = colorError
                baseBinding.tvActive.setTextColor(colorOnError)
                baseBinding.tvVersion.setTextColor(colorOnError)
                baseBinding.mcvInfo.visibility = View.GONE
            }
            BuildConfig.BUILD_TIME -> {
                initXposed = true
                baseBinding.ivIcon.setImageResource(R.drawable.ic_round_check_circle_24)
                baseBinding.tvActive.setText(R.string.activated)
                baseBinding.tvVersion.text = "${CoreApi.versionName}"
            }
            else -> {
                initXposed = true
                baseBinding.ivIcon.setImageResource(R.drawable.ic_warning_amber_24)
                baseBinding.tvActive.setText(R.string.need_reboot)
                baseBinding.tvVersion.text = "system: ${CoreApi.versionName}\nmodule: ${BuildConfig.VERSION_NAME}"
                baseBinding.mcvStatus.setCardBackgroundColor(MaterialColors.harmonizeWithPrimary(this, getColor(R.color.color_warning)))
                baseBinding.mcvStatus.setOnClickListener {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.need_reboot)
                        .setPositiveButton(R.string.reboot) { _, _->
                            Shell.getShell().newJob().add("reboot").exec()
                        }
                        .show()
                }
            }
        }
        if (Build.VERSION.PREVIEW_SDK_INT != 0) {
            baseBinding.systemVersion.text = "${Build.VERSION.CODENAME} Preview (API ${Build.VERSION.SDK_INT})"
        } else {
            baseBinding.systemVersion.text = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        }
        baseBinding.rootPrivilege.text = if(Shell.getShell().isRoot) "YES" else "NO"
    }



    override fun onDestroy() {
        super.onDestroy()
    }
}