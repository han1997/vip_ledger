package com.han1997.vipledger.core.data.importexport.yimu

import com.han1997.vipledger.core.model.AccountType

/**
 * 一木记账账户名 → VIP Ledger 账户（纯函数，`.trellis/spec/android/import-export.md` 一木一节）。
 *
 * - 归一化：去首尾空白、全角转半角、统一括号与间隔符；
 * - 类型按名称推断（先命中先用，D10）：信用卡 → `CREDIT_CARD`；花呗/白条/月付 → `PAYABLE`；
 *   微信/支付宝/零钱通/钱包 → `VIRTUAL`；现金 → `CASH`；公积金 → `INVESTMENT`；其余 → `BANK_DEBIT`；
 * - `initialBalance` 一律 0（`balanceOf = initialBalance + Σdelta`，给非 0 初值会让迁移余额凭空翻倍）。
 */
object YimuAccountMapper {

    /** 账户列为空时落到的账户名（全新账本经 `CreateLedgerUseCase` 必有一个内置「现金」）。 */
    const val DEFAULT_ACCOUNT_NAME: String = "现金"

    /**
     * 归一化账户名：去首尾空白、全角字母数字与常见全角标点转半角、统一各类括号与间隔符。
     * 匹配已有账户与「是否新建」的判重都用归一化后的名字。
     */
    fun normalize(name: String): String {
        val trimmed = name.trim()
        val builder = StringBuilder(trimmed.length)
        trimmed.forEach { ch ->
            when {
                // 括号(开闭不分、全半角不分)一律压成同一个 '(';判重只要求同一张卡不分裂。
                ch.isBracket() -> builder.append('(')
                ch.isFullWidthAlnum() -> builder.append(ch.toHalfWidth())
                ch.isSeparator() -> builder.append(' ')
                else -> builder.append(ch)
            }
        }
        return builder.toString().trim()
    }

    /** 按归一化名推断账户类型；先命中先用（D10）。 */
    fun inferType(normalizedName: String): AccountType = when {
        normalizedContains(normalizedName, "信用卡") -> AccountType.CREDIT_CARD
        normalizedContains(normalizedName, "花呗") ||
            normalizedContains(normalizedName, "白条") ||
            normalizedContains(normalizedName, "月付") -> AccountType.PAYABLE
        normalizedContains(normalizedName, "微信") ||
            normalizedContains(normalizedName, "支付宝") ||
            normalizedContains(normalizedName, "零钱通") ||
            normalizedContains(normalizedName, "钱包") -> AccountType.VIRTUAL
        normalizedContains(normalizedName, "现金") -> AccountType.CASH
        normalizedContains(normalizedName, "公积金") -> AccountType.INVESTMENT
        else -> AccountType.BANK_DEBIT
    }

    private fun normalizedContains(normalized: String, keyword: String): Boolean = normalized.contains(keyword)

    // ---- 全角 → 半角（U+FF01..U+FF5E 映射到 U+0021..U+007E；U+3000 → 空格） ----

    private fun Char.isFullWidthAlnum(): Boolean =
        this in '！'..'～' || this == '　'

    private fun Char.toHalfWidth(): Char = when (this) {
        '　' -> ' '
        else -> this - 0xFEE0
    }

    private fun Char.isBracket(): Boolean =
        this == '(' || this == ')' || this == '[' || this == ']' || this == '{' || this == '}' ||
            this == '（' || this == '）' || this == '【' || this == '】' ||
            this == '〔' || this == '〕' || this == '〈' || this == '〉' ||
            this == '《' || this == '》'

    private fun Char.isSeparator(): Boolean =
        this == '　' || this == '·' || this == '•' || this == '-' || this == '—' || this == '_'
}
