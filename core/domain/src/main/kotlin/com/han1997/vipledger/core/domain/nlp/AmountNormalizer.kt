package com.han1997.vipledger.core.domain.nlp

/**
 * 金额归一化：把量词文法（`元/块/角/毛/分` + 中文数词 + 口语省位）转成十进制字符串，
 * **再交给 `Money.parse`**（`core/model/.../money/Money.kt`）做最终换算与校验。
 *
 * 不重复实现货币解析：`Money.parse` 只认 `¥/￥/$`、千分位与前导 `±`，
 * 本层负责补上「中文数字 + 块/毛/角 + 省位」这段空白（PRD「与既有代码的接缝」）。
 *
 * 覆盖研究 A1–A19；歧义陷阱见 [AmountExtractor]。
 */
object AmountNormalizer {

    private const val YUAN_UNITS = "元块圆员"
    private const val JIAO_UNITS = "角毛"
    private const val FEN_UNIT = '分'
    private const val SKIP_UNIT = '零'
    private const val CURRENCY_SYMBOLS = "¥￥$"

    /** 归一后的十进制字符串（单位：元），无法解析返回 null。 */
    fun normalize(raw: String): String? {
        var text = raw.trim()
        text = text.filterNot { it == ' ' || it == ',' || it in CURRENCY_SYMBOLS }
        if (text.isEmpty()) return null

        val yuanIndex = text.indexOfFirst { it in YUAN_UNITS }
        if (yuanIndex >= 0) {
            val yuanText = text.substring(0, yuanIndex)
            val rest = text.substring(yuanIndex + 1)
            return buildYuan(yuanText, rest)
        }

        if (text.any { it in JIAO_UNITS } || text.contains(FEN_UNIT)) {
            return buildJiaoFenOnly(text)
        }

        return ChineseNumerals.toArabicString(text)
    }

    /** 是否带货币单位或符号（决定金额证据强度）。 */
    fun hasCurrencyUnit(raw: String): Boolean =
        raw.any { it in YUAN_UNITS || it in JIAO_UNITS || it == FEN_UNIT || it in CURRENCY_SYMBOLS }

    private fun buildYuan(yuanText: String, restRaw: String): String? {
        val yuanStr = if (yuanText.isEmpty()) "0" else {
            ChineseNumerals.toArabicString(yuanText) ?: return null
        }
        // 小数元（如 `1.5元`）：直接返回，不再拼接角分（该写法与 `1元5角` 混用极罕见）。
        val yuan = yuanStr.toLongOrNull() ?: return yuanStr
        val rest = restRaw
        if (rest.isEmpty()) return "$yuan.00"

        // `零` 显式跳档到分：12块零5 = 12.05
        if (rest.startsWith(SKIP_UNIT)) {
            val fen = numToDigits(rest.drop(1))?.takeIf { it.isNotEmpty() } ?: return null
            return format(yuan, jiao = 0, fen = fen[0] - '0')
        }

        val jiaoIndex = rest.indexOfFirst { it in JIAO_UNITS }
        val fenIndex = rest.indexOf(FEN_UNIT)
        return when {
            jiaoIndex >= 0 -> {
                val jiaoText = rest.substring(0, jiaoIndex)
                val fenTextEnd = if (fenIndex > jiaoIndex) fenIndex else rest.length
                val fenText = rest.substring(jiaoIndex + 1, fenTextEnd)
                val jiao = lastDigit(jiaoText) ?: 0
                val fen = lastDigit(fenText) ?: 0
                format(yuan, jiao, fen)
            }
            fenIndex >= 0 -> {
                val fenText = rest.substring(0, fenIndex)
                val fen = lastDigit(fenText) ?: return null
                format(yuan, jiao = 0, fen = fen)
            }
            else -> {
                // `块/元` 后裸数字：第一位是角，第二位是分（12块5 = 12.50）
                val digits = numToDigits(rest)?.takeIf { it.isNotEmpty() } ?: return null
                val jiao = digits[0] - '0'
                val fen = if (digits.length >= 2) digits[1] - '0' else 0
                format(yuan, jiao, fen)
            }
        }
    }

    private fun buildJiaoFenOnly(text: String): String? {
        val jiaoIndex = text.indexOfFirst { it in JIAO_UNITS }
        val fenIndex = text.indexOf(FEN_UNIT)
        var jiao = 0
        var fen = 0
        when {
            jiaoIndex >= 0 -> {
                val jiaoText = text.substring(0, jiaoIndex)
                jiao = lastDigit(jiaoText) ?: 0
                val fenTextEnd = if (fenIndex > jiaoIndex) fenIndex else text.length
                val fenText = text.substring(jiaoIndex + 1, fenTextEnd)
                fen = lastDigit(fenText) ?: 0
            }
            fenIndex >= 0 -> {
                fen = lastDigit(text.substring(0, fenIndex)) ?: return null
            }
            else -> return null
        }
        return format(0L, jiao, fen)
    }

    /** 取一串数字/数词的个位（角、分都是单个数字位）。 */
    private fun lastDigit(raw: String): Int? {
        val digits = numToDigits(raw) ?: return null
        if (digits.isEmpty()) return 0
        return digits.last() - '0'
    }

    /** 把阿拉伯或中文数词串转成纯数字串（无小数点）。 */
    private fun numToDigits(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return "0"
        return if (ChineseNumerals.containsNumeral(text)) {
            ChineseNumerals.toArabicString(text)?.replace(".", "")
        } else {
            text.filter { it.isDigit() }
        }
    }

    private fun format(yuan: Long, jiao: Int, fen: Int): String =
        "$yuan.${jiao.coerceIn(0, 9)}${fen.coerceIn(0, 9)}"
}
