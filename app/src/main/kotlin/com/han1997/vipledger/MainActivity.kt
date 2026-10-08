package com.han1997.vipledger

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.domain.usecase.EnsureInitialDataUseCase
import com.han1997.vipledger.ui.VipLedgerApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** 首启初始化：保证「至少有一个默认账本」这条不变式，幂等。 */
    @Inject
    lateinit var ensureInitialData: EnsureInitialDataUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            VipLedgerTheme {
                VipLedgerApp(openAutoBook = intent.getBooleanExtra("openAutoBook", false))
            }
        }
        lifecycleScope.launch { ensureInitialData() }
        requestNotificationPermissionIfNeeded()
    }

    /** API 33+ 的通知权限；未授权时后台通知静默跳过，应用内提醒卡照常可用。 */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_NOTIFICATIONS,
        )
    }

    private companion object {
        const val REQUEST_NOTIFICATIONS: Int = 1001
    }
}
