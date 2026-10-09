package com.han1997.vipledger.autobook

import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentOcrThrottleTest {
    @Test fun samePageCanRetryAfterQuietPeriodWithoutBursting() {
        val throttle = PaymentOcrThrottle()
        assertEquals(0L, throttle.remainingDelay(1000))
        throttle.started(1000)
        assertEquals(1500L, throttle.remainingDelay(1500))
        assertEquals(0L, throttle.remainingDelay(3000))
        throttle.started(3000)
        assertEquals(1900L, throttle.remainingDelay(3100))
        assertEquals(0L, throttle.remainingDelay(8000))
    }
}
