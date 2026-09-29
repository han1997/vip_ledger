package com.han1997.vipledger.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 空列表占位：图标 + 一句说明 + 可选的动作按钮。
 *
 * - [icon] 可选：传了就渲染图标（默认 tint 跟随 primary），不传就退回占位圆点；
 * - [iconContentDescription] 传了才朗读，装饰性图标保持 null；
 * - 文案由调用方从 strings.xml 传入，组件内不写死任何用户可见文字。
 */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconContentDescription: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val containerColor = MaterialTheme.colorScheme.primaryContainer
        val contentColor = MaterialTheme.colorScheme.primary
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(containerColor)
                .semantics {
                    if (iconContentDescription != null) {
                        contentDescription = iconContentDescription
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(28.dp),
                )
            } else {
                Text(
                    text = "·",
                    style = MaterialTheme.typography.titleLarge,
                    color = contentColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) {
                Text(text = actionLabel)
            }
        }
    }
}
