package com.han1997.vipledger.core.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.han1997.vipledger.core.designsystem.R
import com.han1997.vipledger.core.designsystem.format.timeLabel
import com.han1997.vipledger.core.designsystem.theme.amountColorOf
import com.han1997.vipledger.core.domain.model.Txn
import kotlinx.datetime.TimeZone

/**
 * 账目行（首页最近账目、明细列表、搜索结果与统计下钻共用）。
 *
 * 主标题优先用备注标题，没有标题就退回分类名，再退回「未分类」；副标题是
 * 「分类 · 账户 · 标签」的拼接，全部是数据不是文案，所以不占资源。
 * 因为 `feature:ledger` 与 `feature:stats` 都要用，按架构规范放在 designsystem。
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
    val uncategorized = stringResource(R.string.common_uncategorized)
    val draftLabel = stringResource(R.string.common_draft_pending)
    val headline = txn.title.ifBlank { categoryName ?: uncategorized }
    val subtitle = buildList {
        categoryName?.let { add(it) }
        accountName?.let { add(it) }
        if (tagNames.isNotEmpty()) add(tagNames.joinToString(separator = " "))
        if (showTime) add(timeLabel(txn.occurredAt, zone))
    }.joinToString(separator = " · ")
    val mark = headline.trim().firstOrNull()?.toString() ?: "·"
    val accent = amountColorOf(txn.type)

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
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = mark,
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (txn.isDraft) {
                    Text(
                        text = draftLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                    )
                }
            }
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        SignedAmountText(amount = txn.amount, type = txn.type)
    }
}
