package com.han1997.vipledger.core.domain.usecase.home

import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.model.time.toLocalDate
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

data class LocalDay(val date: LocalDate, val zone: TimeZone)

/** 每次订阅立即读取；长驻最多30秒刷新，时区变化也会发射。只有订阅时运行。 */
class ObserveLocalDayUseCase(private val time: TimeProvider) {
    operator fun invoke(): Flow<LocalDay> = flow {
        while (currentCoroutineContext().isActive) {
            val zone = time.zone
            emit(LocalDay(time.nowMillis().toLocalDate(zone), zone))
            delay(30_000)
        }
    }.distinctUntilChanged()
}
