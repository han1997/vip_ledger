package com.han1997.vipledger.core.designsystem.saf

import android.content.Context
import android.net.Uri
import kotlinx.datetime.LocalDate

/**
 * SAF（Storage Access Framework）文本读写的**共享**实现。
 *
 * `import-export.md` 约定：界面导出一律走 `ActivityResultContracts.CreateDocument` 的
 * 「一次性状态 + 回传」流程，零存储权限、零 FileProvider；`feature:stats` 与 `feature:sync`
 * 各有一份同契约镜像。**`:feature:ai` 是第三个需要它的 feature**，按该文档「再有第三个 feature
 * 需要它时，就该下沉到 `core:designsystem`」，新代码用这一份，旧的两份镜像保持不动
 * （避免为一个共享件同时改动 stats / sync 的调用点）。
 *
 * 任何异常都收敛成 `false` / `null`，由界面提示失败而不崩（见 `import-export.md`）。
 */
fun writeTextToUri(context: Context, uri: Uri, text: String): Boolean = runCatching {
    context.contentResolver.openOutputStream(uri)?.use { stream ->
        stream.write(text.toByteArray(Charsets.UTF_8))
    } != null
}.getOrDefault(false)

/** 读取用户通过 `OpenDocument` 选中的文本；失败返回 null。 */
fun readTextFromUri(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        stream.readBytes().toString(Charsets.UTF_8)
    }
}.getOrNull()

/**
 * SAF 建议文件名：ASCII 前缀 + 日期（`vipledger-{prefix}-yyyyMMdd.{ext}`）。
 * 中文文件名在部分文件管理器/Provider 上会乱码或保存失败，因此固定 ASCII。
 */
fun safSuggestedFileName(prefix: String, extension: String, date: LocalDate): String =
    "vipledger-" + prefix + "-" + date.toString().replace("-", "") + "." + extension
