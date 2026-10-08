package com.han1997.vipledger.core.data.autobook

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.han1997.vipledger.core.domain.autobook.PaymentApp
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Bundled model, no remote service and no bitmap persistence. */
@Singleton
class PaymentOcr @Inject constructor() {
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
}
