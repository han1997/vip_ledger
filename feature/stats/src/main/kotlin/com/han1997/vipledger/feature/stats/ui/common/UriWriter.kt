package com.han1997.vipledger.feature.stats.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap

/**
 * 往 SAF 返回的 URI 里写文件。
 *
 * 导出走 `ActivityResultContracts.CreateDocument`，拿到用户选的 URI 后直接写，
 * 不申请存储权限、不配 FileProvider（见 PRD 第 5 节）。
 * 任何异常都收敛成 false，由界面提示「导出失败」而不是崩掉。
 */
fun writeTextToUri(context: Context, uri: Uri, text: String): Boolean = runCatching {
    context.contentResolver.openOutputStream(uri)?.use { stream ->
        stream.write(text.toByteArray(Charsets.UTF_8))
    } != null
}.getOrDefault(false)

fun writeImageToUri(context: Context, uri: Uri, image: ImageBitmap): Boolean = runCatching {
    val bitmap: Bitmap = image.asAndroidBitmap()
    context.contentResolver.openOutputStream(uri)?.use { stream ->
        bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, stream)
    } != false
}.getOrDefault(false)

private const val PNG_QUALITY = 100