package com.han1997.vipledger.core.designsystem.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTokens
import com.han1997.vipledger.core.designsystem.theme.VipLedgerTheme

/**
 * 二次确认弹窗（删除/恢复等不可逆操作一律先确认）。
 *
 * 在既有 [ConfirmDialog] 语义上统一视觉：
 * - [danger] = true 时确认按钮文字用语义红，提醒「不可逆」；
 * - 圆角统一 [VipLedgerTokens.RadiusLarge]。
 * 全部文案由调用方传入（strings.xml），组件内不写死用户可见文字。
 */
@Composable
fun VipLedgerConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = RoundedCornerShape(VipLedgerTokens.RadiusLarge),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    fontWeight = FontWeight.SemiBold,
                    color = if (danger) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = dismissLabel)
            }
        },
    )
}

@Preview(name = "VipLedgerConfirmDialog - 危险", showBackground = true)
@Composable
private fun DangerConfirmDialogPreview() {
    VipLedgerTheme {
        VipLedgerConfirmDialog(
            title = "删除账目",
            message = "删除后无法恢复，确定要删除这笔账目吗？",
            confirmLabel = "删除",
            dismissLabel = "取消",
            onConfirm = {},
            onDismiss = {},
            danger = true,
        )
    }
}

@Preview(name = "VipLedgerConfirmDialog - 普通", showBackground = true)
@Composable
private fun PlainConfirmDialogPreview() {
    VipLedgerTheme {
        VipLedgerConfirmDialog(
            title = "恢复备份",
            message = "恢复会覆盖当前账本数据，已自动生成安全副本。",
            confirmLabel = "恢复",
            dismissLabel = "取消",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
