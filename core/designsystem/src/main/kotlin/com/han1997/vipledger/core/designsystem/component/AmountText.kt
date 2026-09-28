package com.han1997.vipledger.core.designsystem.component

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.han1997.vipledger.core.designsystem.theme.amountColorOf
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money

/**
 * 金额展示：等宽数字（`tnum`）避免列表滚动时数字左右跳动，右对齐对齐小数点。
 */
@Composable
fun AmountText(
    amount: Money,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = LocalContentColor.current,
    showSymbol: Boolean = true,
    showSign: Boolean = false,
    textAlign: TextAlign = TextAlign.Unspecified,
) {
    val text = if (showSign && amount.isPositive) {
        "+" + amount.toDisplayString(includeSymbol = showSymbol)
    } else {
        amount.toDisplayString(includeSymbol = showSymbol)
    }
    Text(
        text = text,
        modifier = modifier,
        style = style.copy(
            fontFeatureSettings = "tnum",
            fontWeight = style.fontWeight ?: FontWeight.SemiBold,
        ),
        color = color,
        textAlign = textAlign,
    )
}

/** 按账目类型自动着色并带方向符号的金额（列表与概览用）。 */
@Composable
fun SignedAmountText(
    amount: Money,
    type: TransactionType,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
) {
    val sign = when (type) {
        TransactionType.EXPENSE -> "-"
        TransactionType.INCOME -> "+"
        TransactionType.TRANSFER, TransactionType.DEBT_REPAY, TransactionType.ADJUST -> ""
    }
    Text(
        text = sign + amount.abs().toDisplayString(),
        modifier = modifier,
        style = style.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold),
        color = amountColorOf(type),
        textAlign = TextAlign.End,
    )
}
