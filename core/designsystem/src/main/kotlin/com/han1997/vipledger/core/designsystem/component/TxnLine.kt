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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.han1997.vipledger.core.designsystem.R
import com.han1997.vipledger.core.designsystem.format.timeLabel
import com.han1997.vipledger.core.designsystem.theme.ExpenseRed
import com.han1997.vipledger.core.designsystem.theme.IncomeGreen
import com.han1997.vipledger.core.designsystem.theme.TransferBlue
import com.han1997.vipledger.core.designsystem.theme.amountColorOf
import com.han1997.vipledger.core.designsystem.theme.displayAmount
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.model.TransactionType
import kotlinx.datetime.TimeZone

/**
 * 账目行(首页最近账目、明细列表、搜索结果与统计下钻共用)。
 *
 * 原型 v2.1 流水行:方形洗色图标(收入绿底 / 支出赭红底 / 转账靛蓝底,
 * 金额按类型着色)+「标题 / 分类·账户·标签」+ 右侧「时间 + 衬线金额」。
 * 金额用衬线 [displayAmount];列表不随数字变化而抖动。
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
    showTime: Boolean = true,
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
    }.joinToString(separator = " · ")
    val mark = headline.trim().firstOrNull()?.toString() ?: "·"
    val wash = containerColorOf(txn.type)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = onClick != null || onLongClick != null,
                onClick = { onClick?.invoke() },
                onLongClick = { onLongClick?.invoke() },
            )
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(wash, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = mark,
                style = MaterialTheme.typography.titleSmall,
                color = markColorOf(txn.type),
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
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
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
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (showTime) {
                Text(
                    text = timeLabel(txn.occurredAt, zone),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SignedAmountText(amount = txn.amount, type = txn.type, style = displayAmount)
        }
    }
}

/** 账目类型对应的洗色底（图标方块）。原型：收入绿洗底、支出赭红底、转账蓝底。 */
private fun containerColorOf(type: TransactionType): Color = when (type) {
    TransactionType.INCOME, TransactionType.REFUND -> Color(0xFFE8EEE9)
    TransactionType.TRANSFER, TransactionType.DEBT_REPAY -> Color(0xFFE6EDF3)
    TransactionType.ADJUST -> Color(0xFFF0EDE4)
    TransactionType.EXPENSE -> Color(0xFFF7E7E3)
}

/** 图标方块首字的墨色：收入用墨绿主色（原型 `.ico` 默认 tint 底 + accent 墨）。 */
@Composable
private fun markColorOf(type: TransactionType): Color = when (type) {
    TransactionType.INCOME, TransactionType.REFUND -> MaterialTheme.colorScheme.primary
    else -> amountColorOf(type)
}

/** 语义色引用集中在这里,避免上面 when 分支里散落金额色。 */
private fun amountColorOf(type: TransactionType): Color = when (type) {
    TransactionType.EXPENSE -> ExpenseRed
    TransactionType.INCOME, TransactionType.REFUND -> IncomeGreen
    TransactionType.TRANSFER, TransactionType.DEBT_REPAY -> TransferBlue
    TransactionType.ADJUST -> Color(0xFF7C7767)
}