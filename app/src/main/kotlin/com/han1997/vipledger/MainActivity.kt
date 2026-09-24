package com.han1997.vipledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
                VipLedgerApp()
            }
        }
        lifecycleScope.launch { ensureInitialData() }
    }
}