package com.han1997.vipledger.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens

/** 分段控件的一个选项。 */
data class SegmentOption(val id: String, val label: String)

/**
 * 分段控件(原型 .seg / .typebar):soft 底 + 发丝边框 + 圆角 13,
 * 选中项墨绿填充、象牙金白字。选项多时横向滚动。
 */
@Composable
fun SegmentedControl(
    options: List<SegmentOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(VipLedgerTokens.BorderThin, MaterialTheme.colorScheme.outlineVariant),
    ) {
        val rowModifier = if (scrollable) {
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        } else {
            Modifier.fillMaxWidth()
        }
        Row(
            modifier = rowModifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            options.forEach { option ->
                val selected = option.id == selectedId
                Box(
                    modifier = Modifier
                        .then(if (scrollable) Modifier else Modifier.weight(1f))
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        )
                        .clickable { onSelect(option.id) }
                        .padding(horizontal = if (scrollable) 14.dp else 0.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.5.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Preview(name = "SegmentedControl", showBackground = true)
@Composable
private fun SegmentedControlPreview() {
    VipLedgerTheme {
        SegmentedControl(
            options = listOf(
                SegmentOption("week", "本周"),
                SegmentOption("month", "本月"),
                SegmentOption("year", "本年"),
                SegmentOption("custom", "自定义"),
            ),
            selectedId = "month",
            onSelect = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}