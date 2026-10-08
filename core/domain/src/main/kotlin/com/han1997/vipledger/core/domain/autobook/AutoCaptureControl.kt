package com.han1997.vipledger.core.domain.autobook

import kotlinx.coroutines.flow.StateFlow

enum class CaptureSetting { NOTIFICATIONS, ACCESSIBILITY, APP_DETAILS, BATTERY }
data class CapturePermissions(val notifications: Boolean = false, val accessibility: Boolean = false)
interface AutoCaptureControl {
    val runtime: StateFlow<AutoBookRuntime>
    fun permissions(): CapturePermissions
    fun openSetting(setting: CaptureSetting): Boolean
}
