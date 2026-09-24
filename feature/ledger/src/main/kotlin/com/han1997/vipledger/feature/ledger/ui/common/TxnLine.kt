package com.han1997.vipledger.feature.ledger.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.han1997.vipledger.core.designsystem.component.SignedAmountText
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.feature.ledger.R
import kotlinx.datetime.TimeZone

/**
 * 账目行（首页最近账目与明细列表共用）。
 *
 * 主标题优先用备注标题，没有标题就退回分类名，再退回「未分类」；副标题是
 * 「分类 · 账户 · 标签」的拼接，全部是数据不是文案，所以不占资源。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TxnLine(
    txn: Txn,
    categoryName: String?,
    accountName: String?,
    tagNames: List<String>,
    zone: TimeZone = TimeZone.currentSystemDefault(),
    modifier: Modifier = Modifier,
    showTime: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val uncategorized = stringResource(R.string.txn_list_uncategorized)
    val headline = txn.title.ifBlank { categoryName ?: uncategorized }
    val subtitle = buildList {
        categoryName?.let { add(it) }
        accountName?.let { add(it) }
        if (tagNames.isNotEmpty()) add(tagNames.joinToString(separator = " "))
        if (showTime) add(timeLabel(txn.occurredAt, zone))
    }.joinToString(separator = " · ")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = onClick != null || onLongClick != null,
                onClick = { onClick?.invoke() },
                onLongClick = { onLongClick?.invoke() },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = headline,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        SignedAmountText(amount = txn.amount, type = txn.type)
    }
}