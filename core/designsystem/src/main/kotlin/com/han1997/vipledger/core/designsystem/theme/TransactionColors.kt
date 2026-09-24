package com.han1997.vipledger.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.han1997.vipledger.core.model.TransactionType

/**
 * 账目类型对应的语义色：支出红、收入绿、转账（含还款）蓝、调整中性色。
 * 屏幕里禁止写死颜色值，一律走这里（见 .trellis/spec/android/ui.md）。
 */
@Composable
fun amountColorOf(type: TransactionType): Color = when (type) {
    TransactionType.EXPENSE -> ExpenseRed
    TransactionType.INCOME -> IncomeGreen
    TransactionType.TRANSFER, TransactionType.DEBT_REPAY -> TransferBlue
    TransactionType.ADJUST -> MaterialTheme.colorScheme.onSurfaceVariant
}