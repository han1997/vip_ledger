package com.han1997.vipledger.autobook

/** Monotonic-time throttle; a failed first screenshot must not lock an order page forever. */
class PaymentOcrThrottle {
    private var lastStartedAt: Long? = null
    fun remainingDelay(now: Long): Long = lastStartedAt?.let { (2_000L - (now - it)).coerceAtLeast(0L) } ?: 0L
    fun started(now: Long) { lastStartedAt = now }
}
