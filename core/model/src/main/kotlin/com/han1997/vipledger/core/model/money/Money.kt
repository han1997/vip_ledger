package com.han1997.vipledger.core.model.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 金额值对象。
 *
 * 内部以「分」为单位的 [Long] 存储，恒不使用浮点数做金额运算（见 docs/DATA-MODEL.md 第 1 节）。
 * 金额本身不表达方向：正负由账目类型决定，外部只对「结余」这类派生值展示负号。
 */
@JvmInline
value class Money private constructor(val cents: Long) : Comparable<Money> {

    val isZero: Boolean get() = cents == 0L

    val isPositive: Boolean get() = cents > 0L

    val isNegative: Boolean get() = cents < 0L

    operator fun plus(other: Money): Money = Money(Math.addExact(cents, other.cents))

    operator fun minus(other: Money): Money = Money(Math.subtractExact(cents, other.cents))

    operator fun times(factor: Int): Money = Money(Math.multiplyExact(cents, factor.toLong()))

    operator fun unaryMinus(): Money = Money(-cents)

    override fun compareTo(other: Money): Int = cents.compareTo(other.cents)

    fun abs(): Money = if (cents < 0L) Money(-cents) else this

    /** 按百分比取值，四舍五入到分。用于利息、折扣、分摊比例等场景。 */
    fun percentage(percent: Double): Money {
        val result = BigDecimal.valueOf(cents)
            .multiply(BigDecimal.valueOf(percent))
            .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
        return Money(result.toLong())
    }

    /**
     * 均分为 [parts] 份，除不尽的余数依次分给前几份，保证各份之和恰好等于原值。
     * 分期摊销、多人分摊、按比例拆账都依赖这个不变式。
     */
    fun splitEvenly(parts: Int): List<Money> {
        require(parts > 0) { "份数必须为正数，实际为 " + parts }
        val sign = if (cents < 0L) -1L else 1L
        val total = cents * sign
        val base = total / parts
        val remainder = (total % parts).toInt()
        return List(parts) { index ->
            val extra = if (index < remainder) 1L else 0L
            Money(sign * (base + extra))
        }
    }

    /** 纯数字文本，无千分位与货币符号，如 1234.56、-0.05。 */
    fun toPlainString(): String {
        val sign = if (cents < 0L) "-" else ""
        val abs = if (cents < 0L) -cents else cents
        val yuan = abs / 100
        val fraction = abs % 100
        return sign + yuan.toString() + "." + fraction.toString().padStart(2, '0')
    }

    /** 展示文本，默认带人民币符号与千分位，如 ¥1,234.56。 */
    fun toDisplayString(includeSymbol: Boolean = true, groupSize: Int = 3): String {
        require(groupSize > 0) { "分组长度必须为正数" }
        val sign = if (cents < 0L) "-" else ""
        val abs = if (cents < 0L) -cents else cents
        val yuanText = grouped((abs / 100).toString(), groupSize)
        val fraction = abs % 100
        val symbol = if (includeSymbol) "¥" else ""
        return sign + symbol + yuanText + "." + fraction.toString().padStart(2, '0')
    }

    override fun toString(): String = toPlainString()

    private fun grouped(digits: String, groupSize: Int): String {
        if (digits.length <= groupSize) return digits
        val builder = StringBuilder()
        var count = 0
        for (index in digits.indices.reversed()) {
            if (count > 0 && count % groupSize == 0) builder.append(',')
            builder.append(digits[index])
            count++
        }
        return builder.reverse().toString()
    }

    companion object {
        val ZERO: Money = Money(0L)

        fun ofCents(cents: Long): Money = Money(cents)

        fun ofYuan(yuan: Long): Money = Money(Math.multiplyExact(yuan, 100L))

        fun sum(values: Iterable<Money>): Money = values.fold(ZERO) { acc, value -> acc + value }

        /**
         * 解析用户输入或导入文本中的金额。
         *
         * 容忍：前后空格、货币符号 ¥/￥/$、千分位逗号、前导 +/-；
         * 超过两位小数时按 HALF_UP 舍入到分（只看被丢弃部分首位，与银行习惯一致）；
         * 无法解析或溢出返回 null，由调用方提示用户。
         */
        fun parse(text: String): Money? {
            var raw = text.trim()
            raw = raw.filterNot { it == ',' || it == ' ' || it == '¥' || it == '￥' || it == '$' }
            if (raw.isEmpty()) return null

            var negative = false
            if (raw.startsWith("-")) {
                negative = true
                raw = raw.substring(1)
            } else if (raw.startsWith("+")) {
                raw = raw.substring(1)
            }
            if (raw.isEmpty()) return null

            val dotCount = raw.count { it == '.' }
            if (dotCount > 1) return null

            val parts = raw.split('.')
            val intPart = parts[0]
            val fractionPart = parts.getOrNull(1).orEmpty()
            if (intPart.isEmpty() && fractionPart.isEmpty()) return null
            if (!intPart.all { it.isDigit() } || !fractionPart.all { it.isDigit() }) return null

            val yuan = intPart.ifEmpty { "0" }.toLongOrNull() ?: return null
            val keptFraction = fractionPart.take(2).padEnd(2, '0')
            val droppedFraction = fractionPart.drop(2)
            val roundUp = droppedFraction.isNotEmpty() && droppedFraction.first() >= '5'

            val totalCents = try {
                Math.addExact(
                    Math.multiplyExact(yuan, 100L),
                    keptFraction.toLong() + if (roundUp) 1L else 0L,
                )
            } catch (_: ArithmeticException) {
                return null
            }
            return Money(if (negative) -totalCents else totalCents)
        }
    }
}
