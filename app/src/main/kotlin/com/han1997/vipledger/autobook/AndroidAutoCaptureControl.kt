package com.han1997.vipledger.autobook

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import com.han1997.vipledger.core.domain.autobook.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidAutoCaptureControl @Inject constructor(@param:ApplicationContext private val context: Context) : AutoCaptureControl {
    private val state = MutableStateFlow(AutoBookRuntime())
    override val runtime = state.asStateFlow()
    fun update(transform: (AutoBookRuntime) -> AutoBookRuntime) = state.update(transform)
    override fun permissions(): CapturePermissions {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val own = ComponentName(context, PaymentAccessibilityService::class.java)
        return CapturePermissions(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName),
            manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
                ComponentName.unflattenFromString(it.id) == own
            })
    }
    override fun openSetting(setting: CaptureSetting): Boolean {
        val intent = when (setting) {
            CaptureSetting.NOTIFICATIONS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            CaptureSetting.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            CaptureSetting.BATTERY -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            CaptureSetting.APP_DETAILS -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent); true }.getOrElse {
            runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
        }
    }
}
@Module
@InstallIn(SingletonComponent::class)
abstract class CaptureModule {
    @Binds abstract fun control(impl: AndroidAutoCaptureControl): AutoCaptureControl
}
