package com.han1997.vipledger.core.domain.usecase.home

import com.han1997.vipledger.core.domain.calculator.StatsCalculator
import com.han1997.vipledger.core.domain.calculator.TxnDayGrouper
import com.han1997.vipledger.core.domain.model.*
import com.han1997.vipledger.core.domain.repository.TxnRepository
import com.han1997.vipledger.core.model.DraftStatus
import com.han1997.vipledger.core.model.time.DateRangePreset
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

data class CalendarReport(val days: List<PeriodStat>, val groups: List<TxnDayGroup>)

class ObserveCalendarUseCase(private val txns: TxnRepository) {
    operator fun invoke(ledgerId: String, month: LocalDate, zone: TimeZone) =
        txns.observe(TxnFilter(ledgerId = ledgerId, range = DateRangePreset.THIS_MONTH.resolve(month), draftStatus = DraftStatus.CONFIRMED)).map { entries ->
            CalendarReport(StatsCalculator.byPeriod(entries, PeriodGranularity.DAY, DateRangePreset.THIS_MONTH.resolve(month), zone), TxnDayGrouper.group(entries, zone))
        }
}
