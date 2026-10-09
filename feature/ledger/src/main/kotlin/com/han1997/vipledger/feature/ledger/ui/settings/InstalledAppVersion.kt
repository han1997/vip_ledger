package com.han1997.vipledger.feature.ledger.ui.settings

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.pm.PackageInfoCompat

data class InstalledAppVersion(val name: String, val code: Long)

/** Read our own installed package once per route, independently of ledger state. */
@Composable
internal fun rememberInstalledAppVersion(): InstalledAppVersion {
    val context = LocalContext.current
    return remember(context) {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        InstalledAppVersion(info.versionName.orEmpty(), PackageInfoCompat.getLongVersionCode(info))
    }
}
