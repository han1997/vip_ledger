package com.han1997.vipledger.feature.sync.ui.common

import android.content.Context
import android.net.Uri

/**
 * 往 SAF 返回的 URI 里写文本。
 *
 * 镜像 `feature:stats/ui/common/UriWriter.kt` 的契约：不申请存储权限、不配 FileProvider，
 * 任何异常都收敛成 `false`，由界面提示「导出失败」而不崩（见 import-export.md）。
 */
fun writeTextToUri(context: Context, uri: Uri, text: String): Boolean = runCatching {
    context.contentResolver.openOutputStream(uri)?.use { stream ->
        stream.write(text.toByteArray(Charsets.UTF_8))
    } != null
}.getOrDefault(false)

/** 读取用户通过 `OpenDocument` 选中的 CSV 文本；失败返回 null。 */
fun readTextFromUri(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        stream.readBytes().toString(Charsets.UTF_8)
    }
}.getOrNull()
