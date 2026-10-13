package com.han1997.vipledger

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
        // 本轮锁定浅色（任务决策 D4）：状态栏/导航栏图标强制深色，不跟随系统深色模式。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            VipLedgerTheme {
                VipLedgerApp(
                    openAutoBook = intent.getBooleanExtra("openAutoBook", false),
                    openBudgetLedger = intent.getStringExtra("openBudgetLedger"),
                    openSharedImages = extractSharedImageUris(intent),
                )
            }
        }
        lifecycleScope.launch { ensureInitialData() }
        requestNotificationPermissionIfNeeded()
    }

    /** 系统分享（ACTION_SEND / SEND_MULTIPLE，image 类型）：提取图片 Uri 供 AI 页逐张 OCR。 */
    private fun extractSharedImageUris(intent: Intent?): List<String> {
        if (intent == null) return emptyList()
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.streamExtra())
            Intent.ACTION_SEND_MULTIPLE -> intent.streamExtras().orEmpty()
            else -> emptyList()
        }
        return uris.distinct().map { it.toString() }
    }

    private fun Intent.streamExtra(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM)
        }

    @Suppress("DEPRECATION")
    private fun Intent.streamExtras(): ArrayList<Uri>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM)
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
