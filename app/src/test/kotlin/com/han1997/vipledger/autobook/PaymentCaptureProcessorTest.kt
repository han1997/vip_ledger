package com.han1997.vipledger.autobook

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.domain.autobook.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PaymentCaptureProcessorTest {
    @Test fun duplicateAttemptDoesNotAdvanceRecordTimeOrClaimAnUpdate() = runBlocking {
        val repository = mockk<AutoBookRepository>()
        val context = mockk<Context>()
        val control = AndroidAutoCaptureControl(context)
        val processor = PaymentCaptureProcessor(repository, control, context)
        val observation = PaymentObservation(PaymentApp.WECHAT, CaptureSource.ACCESSIBILITY, "event", 1000,
            "支付成功\n实付金额：12.30")
        val candidate = AutoCandidate("id", "ledger", observation.app, observation.source, 1000,
            requireNotNull(PaymentParser.parse(observation)))
        coEvery { repository.capture(observation) } returns AppResult.Success(candidate)
        mockkStatic(ContextCompat::class)
        try {
            every { ContextCompat.checkSelfPermission(context, any()) } returns PackageManager.PERMISSION_DENIED
            processor.submit(observation)
            assertEquals(CaptureOutcome.UPDATED, control.runtime.value.lastCaptureOutcome)
            assertEquals(1000L, control.runtime.value.lastRecordAt)
            assertEquals(true, control.runtime.value.lastAmountRecognized)

            val incomplete = observation.copy(observedAt = 2000, text = "支付成功")
            coEvery { repository.capture(incomplete) } returns AppResult.Success(null)
            processor.submit(incomplete)
            assertEquals(2000L, control.runtime.value.lastEventAt)
            assertEquals(1000L, control.runtime.value.lastRecordAt)
            assertEquals(CaptureOutcome.UNCHANGED, control.runtime.value.lastCaptureOutcome)
            assertEquals(false, control.runtime.value.lastAmountRecognized)

            val failed = observation.copy(observedAt = 3000)
            coEvery { repository.capture(failed) } returns AppResult.Failure(AppError.Storage())
            processor.submit(failed)
            assertEquals(CaptureOutcome.FAILED, control.runtime.value.lastCaptureOutcome)
            assertEquals(RuntimeIssue.STORAGE_FAILED, control.runtime.value.issue)
            assertEquals(1000L, control.runtime.value.lastRecordAt)
        } finally {
            unmockkStatic(ContextCompat::class)
        }
    }
}
