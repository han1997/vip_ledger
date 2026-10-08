package com.han1997.vipledger.autobook

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.han1997.vipledger.MainActivity
import com.han1997.vipledger.R
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.autobook.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentCaptureProcessor @Inject constructor(
    private val repository: AutoBookRepository,
    private val control: AndroidAutoCaptureControl,
    @param:ApplicationContext private val context: Context,
) {
    suspend fun submit(value: PaymentObservation) {
        control.update { it.copy(lastEventAt = value.observedAt, lastCaptureOutcome = CaptureOutcome.PROCESSING,
            lastAmountRecognized = PaymentParser.parse(value)?.amountCents != null) }
        when (val result = repository.capture(value)) {
            is AppResult.Failure -> control.update {
                it.copy(issue = RuntimeIssue.STORAGE_FAILED, lastCaptureOutcome = CaptureOutcome.FAILED)
            }
            is AppResult.Success -> {
                control.update { it.copy(issue = null,
                    lastRecordAt = if (result.value != null) value.observedAt else it.lastRecordAt,
                    lastCaptureOutcome = if (result.value != null) CaptureOutcome.UPDATED else CaptureOutcome.UNCHANGED) }
                result.value?.let { candidate ->
                    if (candidate.status != CandidateStatus.POSTED && candidate.status != CandidateStatus.PENDING) return@let
                    val pendingReview = candidate.status == CandidateStatus.PENDING
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return@let
                    val manager = context.getSystemService(NotificationManager::class.java)
                    manager.createNotificationChannel(NotificationChannel("autobook", context.getString(R.string.auto_channel), NotificationManager.IMPORTANCE_LOW))
                    val intent = Intent(context, MainActivity::class.java).putExtra("openAutoBook", true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    val pending = PendingIntent.getActivity(context, 2002, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    manager.notify(2002, NotificationCompat.Builder(context, "autobook")
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle(context.getString(if (pendingReview) R.string.auto_pending else R.string.auto_posted))
                        .setContentText(context.getString(if (pendingReview) R.string.auto_pending_body else R.string.auto_posted_body))
                        .setContentIntent(pending).setOnlyAlertOnce(true).setAutoCancel(true).build())
                }
            }
        }
    }
}
