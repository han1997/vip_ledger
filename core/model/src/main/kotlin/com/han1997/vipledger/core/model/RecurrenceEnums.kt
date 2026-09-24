package com.han1997.vipledger.core.model

/** 周期规则的重复频率（docs/DATA-MODEL.md 3.6）。 */
enum class RecurrenceFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
    EVERY_N_DAYS,
    EVERY_N_MONTHS,
    ;

    /** 按天推进的频率：推算时用「天」做步长，其余按月/年。 */
    val isDayBased: Boolean
        get() = when (this) {
            DAILY, WEEKLY, EVERY_N_DAYS -> true
            MONTHLY, YEARLY, EVERY_N_MONTHS -> false
        }
}

/** 周期规则的结束条件。 */
enum class RecurrenceEndType { NEVER, UNTIL_DATE, COUNT }

/** 周期规则生成流水的方式：默认只生成待确认草稿。 */
enum class GenerateMode { DRAFT, AUTO }

/** 错过周期的处理策略：默认跳过，可选补记。 */
enum class MissedPolicy { SKIP, CATCH_UP }