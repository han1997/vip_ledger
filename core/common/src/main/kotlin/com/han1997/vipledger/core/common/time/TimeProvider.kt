package com.han1997.vipledger.core.common.time

import kotlinx.datetime.TimeZone

/**
 * 统一时间来源。业务代码禁止直接调 `System.currentTimeMillis()`，
 * 便于单测注入固定时钟（见 .trellis/spec/android/testing.md）。
 */
interface TimeProvider {
    fun nowMillis(): Long

    /** 设备时区：账目归属到哪一天、统计按天分桶都以它为准。 */
    val zone: TimeZone
}

object SystemTimeProvider : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()

    override val zone: TimeZone get() = TimeZone.currentSystemDefault()
}