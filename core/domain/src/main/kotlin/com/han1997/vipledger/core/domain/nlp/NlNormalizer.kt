package com.han1997.vipledger.core.domain.nlp

/**
 * P0 归一化 + P1 触发词剥离（研究 §4.2）。
 *
 * 只做字符级归一，不做语义切分。归一化后的文本才是 span 的坐标系，
 * 因此所有抽取器的下标都相对归一化文本，而非用户原始输入。
 */
object NlNormalizer {

    /** 「帮我记一笔 / 记一笔 / 帮我记」这类前缀触发词，剥离后不参与语义。 */
    private val LEADING_TRIGGERS = listOf(
        "帮我记一笔",
        "帮我记账",
        "帮我记",
        "记一笔",
        "记个账",
        "记账",
    )

    private const val TRAILING_PUNCTUATION = "。！？!?；;，,、"

    fun normalize(text: String): String {
        val halfWidth = toHalfWidth(text)
        val visible = halfWidth.filterNot { it.isInvisible() }
        val unified = visible
            .replace('週', '周')
            .replace("禮拜", "礼拜")
            .replace('圓', '元')
        return stripTriggers(unified)
    }

    /** 全角数字/标点/货币符号 → 半角；其余原样。 */
    private fun toHalfWidth(text: String): String = buildString(text.length) {
        for (c in text) {
            append(
                when (c) {
                    in '０'..'９' -> '0' + (c - '０')
                    '．' -> '.'
                    '，' -> ','
                    '：' -> ':'
                    '；' -> ';'
                    '！' -> '!'
                    '？' -> '?'
                    '（' -> '('
                    '）' -> ')'
                    '－' -> '-'
                    '＋' -> '+'
                    '　' -> ' '
                    '￥' -> '¥'
                    else -> c
                },
            )
        }
    }

    private fun Char.isInvisible(): Boolean =
        this == '﻿' || this in '​'..'‏' || this in '‪'..'‮'

    private fun stripTriggers(text: String): String {
        var result = text.trim()
        for (trigger in LEADING_TRIGGERS) {
            if (result.startsWith(trigger)) {
                result = result.removePrefix(trigger)
                break
            }
        }
        result = result.trimStart()
        while (result.isNotEmpty() && result.last() in TRAILING_PUNCTUATION) {
            result = result.dropLast(1)
        }
        return result.trim()
    }
}
