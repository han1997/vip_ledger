package com.han1997.vipledger.core.domain.nlp

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

/**
 * P2 日期抽取：**所有相对日期都相对显式传入的 [today] 解析，绝不读系统时钟**（研究 §2.1）。
 *
 * 覆盖研究 §2.2–§2.4 的常见表达；`这周X` / `下周X` 采用项目选定语义：
 * - 裸 `周X`：严格晚于今天的下一个 X；
 * - `这周X`：本周（周一为首）内不早于今天的 X，跨周顺延；
 * - `上周X`：上一个自然周（周一为首）里的 X；
 * - `下周X`：**自然周读法**——下一自然周的 X（与 Duckling ZH 的「下周三 = 明天」不同，属有意差异）。
 *
 * 只返回一个日期（一句话多笔共用该日期）；找到即认领 span，避免月/日里的数字被金额吞掉。
 */
object DateExtractor {

    private const val WEEK_DAYS = 7

    data class Result(val date: LocalDate, val span: Span)

    fun extract(text: String, table: SpanTable, today: LocalDate): LocalDate? {
        val candidates = collect(text, today)
        val best = candidates
            .sortedWith(compareBy({ it.span.start }, { -it.span.length }))
            .firstOrNull { !table.isTaken(it.span.start, it.span.end) }
            ?: return null
        if (!table.tryClaim(best.span)) return null
        return best.date
    }

    private data class Candidate(val date: LocalDate, val span: Span)

    private fun collect(text: String, today: LocalDate): List<Candidate> {
        val result = ArrayList<Candidate>()

        ABSOLUTE_FULL.findAll(text).forEach { m ->
            val year = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            val day = m.groupValues[3].toInt()
            if (isValid(year, month, day)) result += Candidate(LocalDate(year, month, day), m.span())
        }
        YEAR_MONTH.findAll(text).forEach { m ->
            val year = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            if (month in 1..12) result += Candidate(LocalDate(year, month, 1), m.span())
        }
        MONTH_DAY.findAll(text).forEach { m ->
            val month = m.groupValues[1].toInt()
            val day = m.groupValues[2].toInt()
            if (isValid(today.year, month, day)) result += Candidate(LocalDate(today.year, month, day), m.span())
        }
        SLASH_DAY.findAll(text).forEach { m ->
            val month = m.groupValues[1].toInt()
            val day = m.groupValues[2].toInt()
            if (isValid(today.year, month, day)) result += Candidate(LocalDate(today.year, month, day), m.span())
        }
        MONTH_ONLY.findAll(text).forEach { m ->
            val month = m.groupValues[1].toInt()
            if (month in 1..12) result += Candidate(LocalDate(today.year, month, 1), m.span())
        }
        RELATIVE_DAYS.findAll(text).forEach { m ->
            relativeDate(m.groupValues[1], today)?.let { result += Candidate(it, m.span()) }
        }
        DAYS_OFFSET.findAll(text).forEach { m ->
            val amount = ChineseNumerals.toArabicString(m.groupValues[1])?.toIntOrNull() ?: return@forEach
            val signed = if (m.groupValues[2] == "前") -amount else amount
            result += Candidate(today.plus(signed, DateTimeUnit.DAY), m.span())
        }
        WEEKEND.findAll(text).forEach { m ->
            result += Candidate(nextWeekday(today, 6), m.span())
        }
        WEEKDAY.findAll(text).forEach { m ->
            weekdayDate(m.groupValues[1], m.groupValues[2], today)?.let { result += Candidate(it, m.span()) }
        }
        YEAR_WORDS.findAll(text).forEach { m ->
            val date = when (m.value) {
                "去年" -> LocalDate(today.year - 1, today.monthNumber, today.dayOfMonth)
                "明年" -> LocalDate(today.year + 1, today.monthNumber, today.dayOfMonth)
                else -> today
            }
            result += Candidate(date, m.span())
        }
        MONTH_WORDS.findAll(text).forEach { m ->
            val delta = when (m.value) {
                "上个月", "上月" -> -1
                "下个月", "下月" -> 1
                else -> 0
            }
            result += Candidate(monthShifted(today, delta), m.span())
        }
        BARE_DAY.findAll(text).forEach { m ->
            val day = m.groupValues[1].toInt()
            bareDay(today, day)?.let { result += Candidate(it, m.span()) }
        }

        return result
    }

    private fun nextWeekday(today: LocalDate, targetIso: Int): LocalDate {
        val delta = (targetIso - today.dayOfWeek.isoDayNumber + WEEK_DAYS) % WEEK_DAYS
        return today.plus(if (delta == 0) WEEK_DAYS else delta, DateTimeUnit.DAY)
    }

    private fun weekdayDate(prefix: String, weekdayText: String, today: LocalDate): LocalDate? {
        val target = weekdayNumber(weekdayText) ?: return null
        val thisMonday = today.minusDays(today.dayOfWeek.isoDayNumber - 1)
        return when {
            prefix.startsWith("上") -> thisMonday.plus(target - 1 + (-WEEK_DAYS), DateTimeUnit.DAY)
            prefix.startsWith("下") -> thisMonday.plus(WEEK_DAYS + target - 1, DateTimeUnit.DAY)
            prefix.isNotEmpty() -> {
                val candidate = thisMonday.plus(target - 1, DateTimeUnit.DAY)
                if (candidate < today) candidate.plus(WEEK_DAYS, DateTimeUnit.DAY) else candidate
            }
            else -> nextWeekday(today, target)
        }
    }

    private fun relativeDate(word: String, today: LocalDate): LocalDate? = when (word) {
        "大前天" -> today.plus(-3, DateTimeUnit.DAY)
        "前天", "前日" -> today.plus(-2, DateTimeUnit.DAY)
        "昨天", "昨日", "昨儿" -> today.plus(-1, DateTimeUnit.DAY)
        "今天", "今日", "本日", "今儿" -> today
        "明天", "明日" -> today.plus(1, DateTimeUnit.DAY)
        "后天" -> today.plus(2, DateTimeUnit.DAY)
        "大后天" -> today.plus(3, DateTimeUnit.DAY)
        else -> null
    }

    private fun bareDay(today: LocalDate, day: Int): LocalDate? {
        if (day < 1 || day > 31) return null
        val inCurrent = runCatching { LocalDate(today.year, today.monthNumber, day) }.getOrNull()
        if (inCurrent != null && inCurrent <= today) return inCurrent
        // 该日号已过或当月无此日（如 31 号在 30 天月份）→ 取上个月，与 PREFER_DATES_FROM=past 一致。
        return monthShifted(today, -1).withDayClamped(day)
    }

    private fun monthShifted(today: LocalDate, delta: Int): LocalDate {
        val index = today.year * 12 + (today.monthNumber - 1) + delta
        val year = index.floorDiv(12)
        val month = index.mod(12) + 1
        return LocalDate(year, month, 1)
    }

    private fun LocalDate.withDayClamped(day: Int): LocalDate {
        val last = daysInMonth(year, monthNumber)
        return LocalDate(year, monthNumber, day.coerceIn(1, last))
    }

    private fun daysInMonth(year: Int, month: Int): Int =
        LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH).plus(-1, DateTimeUnit.DAY).dayOfMonth

    private fun isValid(year: Int, month: Int, day: Int): Boolean =
        month in 1..12 && day in 1..daysInMonth(year, month)

    private fun weekdayNumber(text: String): Int? = when (text) {
        "一" -> 1
        "二" -> 2
        "三" -> 3
        "四" -> 4
        "五" -> 5
        "六" -> 6
        "日", "天" -> 7
        else -> null
    }

    private fun LocalDate.minusDays(days: Int): LocalDate = plus(-days, DateTimeUnit.DAY)

    private fun MatchResult.span() = Span(range.first, range.last + 1, SpanKind.DATE)

    private val ABSOLUTE_FULL = Regex("(\\d{4})年(\\d{1,2})月(\\d{1,2})[日号]?")
    private val YEAR_MONTH = Regex("(\\d{4})年(\\d{1,2})月")
    private val MONTH_DAY = Regex("(\\d{1,2})月(\\d{1,2})[日号]?")
    private val SLASH_DAY = Regex("(\\d{1,2})[/\\-](\\d{1,2})")
    private val MONTH_ONLY = Regex("(\\d{1,2})月")
    private val RELATIVE_DAYS = Regex("(大前天|大后天|前天|前日|昨天|昨日|昨儿|今天|今日|本日|今儿|明天|明日|后天)")
    private val DAYS_OFFSET = Regex("([零〇一二两俩三四五六七八九十\\d]+)天(前|后)")
    private val WEEKEND = Regex("周末")
    private val WEEKDAY = Regex("(上周|上个星期|上礼拜|这周|本周|这个星期|这个礼拜|下周|下个星期|下礼拜|周|星期|礼拜)([一二三四五六日天])")
    private val YEAR_WORDS = Regex("(去年|今年|明年)")
    private val MONTH_WORDS = Regex("(上个月|上月|这个月|本月|下个月|下月)")
    private val BARE_DAY = Regex("(\\d{1,2})[日号]")
}

/** 时段词的代表时刻（研究 §2.5 建议值）与钟点解析。 */
object TimeExtractor {

    data class Result(val time: LocalTime, val span: Span)

    fun extract(text: String, table: SpanTable): LocalTime? {
        val candidates = ArrayList<Result>()

        CLOCK.findAll(text).forEach { m ->
            val hour = ChineseNumerals.toArabicString(m.groupValues[1])?.toIntOrNull() ?: return@forEach
            val minute = resolveMinute(m.groupValues[3], m.groupValues[4]) ?: return@forEach
            val meridian = meridianBefore(text, m.range.first)
            candidates += Result(LocalTime(adjustHour(hour, meridian), minute), m.span())
        }
        COLON.findAll(text).forEach { m ->
            val hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].toInt()
            if (hour in 0..23 && minute in 0..59) {
                candidates += Result(LocalTime(hour, minute), m.span())
            }
        }

        candidates.sortedBy { it.span.start }.firstOrNull { !table.isTaken(it.span.start, it.span.end) }?.let {
            if (table.tryClaim(it.span)) return it.time
        }

        PERIOD.findAll(text).forEach { m ->
            if (!table.isTaken(m.range.first, m.range.last + 1) && table.tryClaim(m.span())) {
                return periodTime(m.value)
            }
        }
        return null
    }

    private fun resolveMinute(marker: String, minuteText: String): Int? = when {
        marker == "半" -> 30
        marker == "一刻" -> 15
        marker == "三刻" -> 45
        marker == "整" -> 0
        minuteText.isNotEmpty() -> {
            val value = ChineseNumerals.toArabicString(minuteText)?.toIntOrNull() ?: return null
            if (value in 0..59) value else null
        }
        else -> 0
    }

    private fun adjustHour(hour: Int, meridian: Meridian): Int = when {
        meridian == Meridian.PM -> if (hour < 12) hour + 12 else hour
        meridian == Meridian.AM -> hour
        hour in 1..11 -> hour + 12
        else -> hour
    }

    /** 钟点前是否出现 AM / PM 提示（研究 §2.5）。按词判定，避免「上/下午」都含「午」。 */
    private fun meridianBefore(text: String, hourIndex: Int): Meridian {
        val from = (hourIndex - 3).coerceAtLeast(0)
        val context = text.substring(from, hourIndex)
        return when {
            AM_WORDS.any { context.contains(it) } -> Meridian.AM
            PM_WORDS.any { context.contains(it) } -> Meridian.PM
            else -> Meridian.NONE
        }
    }

    private enum class Meridian { AM, PM, NONE }

    private fun periodTime(word: String): LocalTime = when (word) {
        "凌晨" -> LocalTime(5, 0)
        "早上", "早晨" -> LocalTime(8, 0)
        "上午" -> LocalTime(9, 0)
        "中午" -> LocalTime(12, 0)
        "下午" -> LocalTime(15, 0)
        else -> LocalTime(20, 0)
    }

    private fun MatchResult.span() = Span(range.first, range.last + 1, SpanKind.TIME)

    private val AM_WORDS = listOf("凌晨", "早上", "早晨", "上午")
    private val PM_WORDS = listOf("下午", "晚上", "晚间", "傍晚", "中午")

    private val CLOCK = Regex("([零〇一二两俩三四五六七八九十\\d]+)(点|时)(半|一刻|三刻|整)?([零〇一二两俩三四五六七八九十\\d]*)(分)?")
    private val COLON = Regex("(\\d{1,2}):(\\d{2})")
    private val PERIOD = Regex("(凌晨|早上|早晨|上午|中午|下午|晚上|晚间|傍晚)")
}
