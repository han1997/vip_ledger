package com.han1997.vipledger.core.data.autobook

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Bundled model, no remote service and no bitmap persistence. */
@Singleton
class PaymentOcr @Inject constructor() {
    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    suspend fun recognize(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        val owned = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        recognizer.process(InputImage.fromBitmap(owned, 0))
            .addOnSuccessListener { if (continuation.isActive) continuation.resume(it.text) }
            .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
            .addOnCompleteListener { owned.recycle() }
    }
}
