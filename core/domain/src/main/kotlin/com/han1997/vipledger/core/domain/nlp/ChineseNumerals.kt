package com.han1997.vipledger.core.domain.nlp

import java.math.BigDecimal

/**
 * 中文数词 → 阿拉伯数字串（研究 §1.2）。
 *
 * 只负责「数词」这一层，不认货币单位（`元/块/角/分` 由 [AmountNormalizer] 处理）。
 * 覆盖研究 A8–A19：标准式、口语省位（`一百二`=120、`三千五`=3500、`一万五`=15000）、
 * `零` 隔断不省位（`一万零五`=10005）、小数（`一点二三`=1.23）、混写（`1百23`=123）、
 * 大数乘数（`3000万`、`1.5亿`）。
 */
object ChineseNumerals {

    private val DIGITS: Map<Char, Int> = mapOf(
        '零' to 0,
        '〇' to 0,
        '一' to 1,
        '二' to 2,
        '两' to 2,
        '俩' to 2,
        '三' to 3,
        '四' to 4,
        '五' to 5,
        '六' to 6,
        '七' to 7,
        '八' to 8,
        '九' to 9,
    )

    private const val TEN = '十'
    private const val HUNDRED = '百'
    private const val THOUSAND = '千'
    private const val MYRIAD = '万'
    private const val HUNDRED_MILLION = '亿'
    private const val POINT = '点'

    /** 口语省位：`<前缀><单位><个位数>` 时，尾随个位进「单位 / 10」那一位。 */
    private val SHENG_WEI = Regex("^(.*[百千万亿])([一二两俩三四五六七八九])$")

    private val UNIT_DIVISOR: Map<Char, Long> = mapOf(
        HUNDRED to 10L,
        THOUSAND to 100L,
        MYRIAD to 1000L,
        HUNDRED_MILLION to 10_000_000L,
    )

    /** 阿拉伯数字 + `万/亿` 乘数，如 `3000万` / `1.5亿`。 */
    private val ARABIC_MULTIPLIER = Regex("^(\\d+(?:\\.\\d+)?)([万亿])$")

    fun containsNumeral(text: String): Boolean =
        text.any { it in DIGITS || it == TEN || it == HUNDRED || it == THOUSAND || it == MYRIAD || it == HUNDRED_MILLION }

    /**
     * 把数词串归一成阿拉伯串；纯阿拉伯原样返回；无法解析返回 null。
     * 结果可含一个小数点（如 `12.30`），但**不含货币单位**。
     */
    fun toArabicString(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        ARABIC_MULTIPLIER.matchEntire(text)?.let { match ->
            val (base, unit) = match.destructured
            val multiplier = if (unit == "万") BigDecimal("10000") else BigDecimal("100000000")
            return BigDecimal(base).multiply(multiplier).stripTrailingZeros().toPlainString()
        }

        // 纯阿拉伯（无中文数词）：原样返回（去千分位），交给 Money.parse 做最终校验。
        if (!containsNumeral(text) && text.all { it.isDigit() || it == '.' || it == ',' }) {
            return text.replace(",", "")
        }

        val pointIndex = text.indexOf(POINT)
        if (pointIndex >= 0) {
            val intPart = text.substring(0, pointIndex)
            val fracPart = text.substring(pointIndex + 1)
            val integer = parseInteger(intPart.ifEmpty { "零" }) ?: return null
            if (fracPart.isEmpty()) return integer.toString()
            val fraction = buildString {
                for (c in fracPart) {
                    val digit = digitValue(c) ?: return null
                    append(digit)
                }
            }
            return integer.toString() + "." + fraction
        }

        if (!containsNumeral(text) && !text.all { it.isDigit() || it == '.' || it == ',' }) {
            return null
        }
        val cleaned = text.replace(",", "")
        val integer = parseInteger(cleaned) ?: return null
        return integer.toString()
    }

    /** 解析整数部分（不含小数点）。失败返回 null。 */
    fun parseInteger(raw: String): Long? {
        val text = raw.replace(",", "")
        if (text.isEmpty()) return null

        SHENG_WEI.matchEntire(text)?.let { match ->
            val prefix = match.groupValues[1]
            val trailing = match.groupValues[2].first()
            val unit = prefix.last()
            val divisor = UNIT_DIVISOR[unit] ?: return null
            val base = parseStandard(prefix) ?: return null
            val digit = digitValue(trailing) ?: return null
            return base + digit * divisor
        }

        return parseStandard(text)
    }

    /** 标准中文数词（含混写阿拉伯），不含口语省位。 */
    private fun parseStandard(raw: String): Long? {
        val text = raw.replace(",", "")
        if (text.isEmpty()) return null

        var total = 0L
        var section = 0L
        var current = 0L
        var sawAnything = false

        for (c in text) {
            val digit = digitValue(c)
            when {
                digit != null -> {
                    current = current * 10 + digit
                    sawAnything = true
                }
                c == TEN || c == HUNDRED || c == THOUSAND -> {
                    val unit = when (c) {
                        TEN -> 10L
                        HUNDRED -> 100L
                        else -> 1000L
                    }
                    val value = if (current == 0L) 1L else current
                    section += value * unit
                    current = 0L
                    sawAnything = true
                }
                c == MYRIAD || c == HUNDRED_MILLION -> {
                    section += current
                    current = 0L
                    val multiplier = if (c == MYRIAD) 10_000L else 100_000_000L
                    total = (total + section) * multiplier
                    section = 0L
                    sawAnything = true
                }
                else -> return null
            }
        }
        if (!sawAnything) return null
        return total + section + current
    }

    private fun digitValue(c: Char): Int? = when (c) {
        in '0'..'9' -> c - '0'
        else -> DIGITS[c]
    }
}
