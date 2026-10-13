package com.han1997.vipledger.feature.ledger.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.han1997.vipledger.core.designsystem.theme.serifTitle
import com.han1997.vipledger.feature.ledger.R
import kotlinx.datetime.LocalDate

fun validCalendarDate(value: String?): LocalDate? = value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.takeIf { it.year in 1900..9998 }

@Composable
fun MonthNavigation(month: LocalDate, onPrevious: () -> Unit, onNext: () -> Unit, onToday: () -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, enabled = enabled && month > LocalDate(1900, 1, 1)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.ledger_month_previous))
        }
        // 原型 .cal-head .mo：衬线墨绿月份标题。
        Text(
            text = stringResource(R.string.ledger_month_label, month.year, month.monthNumber),
            Modifier.weight(1f),
            style = serifTitle,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onNext, enabled = enabled && month < LocalDate(9998, 12, 1)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.ledger_month_next))
        }
        TextButton(onClick = onToday, enabled = enabled) { Text(stringResource(R.string.ledger_today)) }
    }
}
