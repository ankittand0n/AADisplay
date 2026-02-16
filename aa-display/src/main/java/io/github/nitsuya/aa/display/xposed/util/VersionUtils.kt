package io.github.nitsuya.aa.display.xposed.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.nitsuya.aa.display.xposed.log

object VersionUtils {
    private const val TAG = "AAD_VersionUtils"

    const val PACKAGE_ANDROID_AUTO = "com.google.android.projection.gearhead"
    const val PACKAGE_SYSTEM_UI = "com.android.systemui"

    fun getAppVersion(context: Context, packageName: String): String? {
        return try {
            context.packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            log(TAG, "Package not found: $packageName")
            null
        } catch (e: Throwable) {
            log(TAG, "Failed to get version for $packageName: ${e.message}", e)
            null
        }
    }

    fun getAppVersionCode(context: Context, packageName: String): Long {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            log(TAG, "Package not found: $packageName")
            -1L
        } catch (e: Throwable) {
            log(TAG, "Failed to get version code for $packageName: ${e.message}", e)
            -1L
        }
    }

    fun isAadEnabled(context: Context): Boolean {
        return try {
            android.provider.Settings.Global.getInt(
                context.contentResolver,
                "aad_display_enable",
                1
            ) == 1
        } catch (e: Throwable) {
            log(TAG, "Failed to read aad_display_enable setting, defaulting to enabled: ${e.message}")
            true
        }
    }

    fun isFeatureSupported(context: Context, minVersion: String): Boolean {
        val currentVersion = getAppVersion(context, PACKAGE_ANDROID_AUTO) ?: return false
        return try {
            compareVersions(currentVersion, minVersion) >= 0
        } catch (e: Throwable) {
            log(TAG, "Failed to compare versions: ${e.message}")
            false
        }
    }

    private fun compareVersions(version1: String, version2: String): Int {
        val parts1 = version1.split(".", "-", "_").map { it.toIntOrNull() ?: 0 }
        val parts2 = version2.split(".", "-", "_").map { it.toIntOrNull() ?: 0 }
        val maxLength = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLength) {
            val v1 = parts1.getOrNull(i) ?: 0
            val v2 = parts2.getOrNull(i) ?: 0
            if (v1 != v2) {
                return v1 - v2
            }
        }
        return 0
    }

    fun getDebugInfo(context: Context): Map<String, String> {
        return mapOf(
            "Android Version" to "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            "Device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "Android Auto Version" to (getAppVersion(context, PACKAGE_ANDROID_AUTO) ?: "Not installed"),
            "SystemUI Version" to (getAppVersion(context, PACKAGE_SYSTEM_UI) ?: "Unknown"),
            "AAD Enabled" to isAadEnabled(context).toString()
        )
    }

    fun logDebugInfo(context: Context) {
        val info = getDebugInfo(context)
        val sb = StringBuilder("=== AADisplay Environment Info ===\n")
        info.forEach { (key, value) ->
            sb.append("$key: $value\n")
        }
        sb.append("=================================")
        log(TAG, sb.toString())
    }
}
