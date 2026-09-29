package com.han1997.vipledger.core.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.han1997.vipledger.core.designsystem.theme.ExpenseRed
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme

/**
 * 统一按钮视觉：主操作（实心品牌绿）、危险操作（语义红）、次操作（描边）。
 *
 * 全部直接包装 Material 3 按钮，保证触摸目标、禁用态与涟漪一致；
 * 调用方只传文案（strings.xml）与 onClick，不关心颜色。
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(VipLedgerTokens.RadiusMedium),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(text = text, fontWeight = FontWeight.SemiBold)
    }
}

/** 危险操作按钮：删除、放弃草稿、恢复备份等，用语义红让「不可逆」一眼可辨。 */
@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(VipLedgerTokens.RadiusMedium),
        colors = ButtonDefaults.buttonColors(
            containerColor = ExpenseRed,
            contentColor = Color.White,
        ),
    ) {
        Text(text = text, fontWeight = FontWeight.SemiBold)
    }
}

/** 次要操作：不抢主按钮的注意力，用于「取消」「稍后再说」。 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(VipLedgerTokens.RadiusMedium),
    ) {
        Text(text = text, fontWeight = FontWeight.Medium)
    }
}

/** 行内/对话框里的弱操作（纯文字）。 */
@Composable
fun VipLedgerTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Text(text = text, fontWeight = FontWeight.Medium)
    }
}

@Preview(name = "Buttons", showBackground = true)
@Composable
private fun ButtonsPreview() {
    VipLedgerTheme {
        androidx.compose.foundation.layout.Column(
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            PrimaryButton(text = "保存", onClick = {})
            SecondaryButton(text = "取消", onClick = {})
            DangerButton(text = "删除账目", onClick = {})
            PrimaryButton(text = "保存（禁用）", onClick = {}, enabled = false)
            DangerButton(text = "删除（禁用）", onClick = {}, enabled = false)
        }
    }
}
