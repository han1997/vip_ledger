package com.han1997.vipledger.core.data.autobook

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.han1997.vipledger.core.domain.autobook.PaymentApp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Bundled model, no remote service and no bitmap persistence. */
@Singleton
class PaymentOcr @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    suspend fun recognize(bitmap: Bitmap, app: PaymentApp? = null): String = suspendCancellableCoroutine { continuation ->
        val owned = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        recognizer.process(InputImage.fromBitmap(owned, 0))
            .addOnSuccessListener { result ->
                val text = if (app == PaymentApp.JD) PaymentOcrReadingOrder.format(result.textBlocks.flatMap { block ->
                    block.lines.mapNotNull { line -> line.boundingBox?.let { box ->
                        PaymentOcrLine(line.text, box.left, box.top, box.right, box.bottom)
                    } }
                }) else result.text
                if (continuation.isActive) continuation.resume(text)
            }
            .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
            .addOnCompleteListener { owned.recycle() }
    }

    /**
     * 从内容 Uri 识别（P5 图片切片复用同一本机中文识别器）。
     *
     * `InputImage.fromFilePath` 自行处理解码与 EXIF 旋转；原文不落盘。
     * [IOException]（含 `FileNotFoundException`，图片不可读/已失效）与 ML Kit 失败都向上抛，
     * 由 [com.han1997.vipledger.core.data.ai.MlKitImageTextReader] 归类为 [com.han1997.vipledger.core.common.result.AppError]。
     */
    suspend fun recognizeUri(uri: Uri): String = suspendCancellableCoroutine { continuation ->
        val image = try {
            InputImage.fromFilePath(context, uri)
        } catch (e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
            return@suspendCancellableCoroutine
        }
        recognizer.process(image)
            .addOnSuccessListener { result ->
                if (continuation.isActive) continuation.resume(result.text)
            }
            .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    }
}