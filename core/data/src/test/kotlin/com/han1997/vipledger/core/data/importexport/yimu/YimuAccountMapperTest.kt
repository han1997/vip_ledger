package com.han1997.vipledger.core.data.importexport.yimu

import com.han1997.vipledger.core.model.AccountType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 一木账户名 → [AccountType] 与归一化匹配(R5 / D10)。
 *
 * 归一化的用途是「同一张卡/同一个钱包」判重:全角、括号、间隔符差异不能算出两个账户。
 */
class YimuAccountMapperTest {

    // ---- 18 个真实账户名 → 类型(先命中先用) ----

    @Test
    fun realAccountNamesMapToExpectedTypes() {
        val table = mapOf(
            "支付宝" to AccountType.VIRTUAL,
            "微信零钱" to AccountType.VIRTUAL,
            "微信零钱通" to AccountType.VIRTUAL,
            "微信钱包" to AccountType.VIRTUAL,
            "安徽农金信用卡" to AccountType.CREDIT_CARD,
            "农业银行" to AccountType.BANK_DEBIT,
            "安徽农金" to AccountType.BANK_DEBIT,
            "现金" to AccountType.CASH,
        )
        table.forEach { (name, expected) ->
            assertEquals(expected, YimuAccountMapper.inferType(YimuAccountMapper.normalize(name)), name)
        }
    }

    @Test
    fun debtWalletsArePayable() {
        listOf("花呗", "白条", "美团月付", "抖音月付").forEach { name ->
            assertEquals(AccountType.PAYABLE, YimuAccountMapper.inferType(YimuAccountMapper.normalize(name)), name)
        }
    }

    @Test
    fun creditCardBeatsOtherKeywords() {
        // 「信用卡」优先级最高:即便名字里含「微信」也应是信用卡。
        assertEquals(AccountType.CREDIT_CARD, YimuAccountMapper.inferType("微信信用卡"))
    }

    @Test
    fun providentFundIsInvestment() {
        assertEquals(AccountType.INVESTMENT, YimuAccountMapper.inferType("公积金"))
    }

    @Test
    fun unknownBankNamesFallBackToBankDebit() {
        listOf("招商银行", "工商储蓄", "某某银行", "储蓄卡").forEach { name ->
            assertEquals(AccountType.BANK_DEBIT, YimuAccountMapper.inferType(name), name)
        }
    }

    // ---- 归一化 ----

    @Test
    fun normalizationTrimsAndConvertsFullWidthToHalfWidth() {
        assertEquals("ABC123", YimuAccountMapper.normalize("  ＡＢＣ１２３  "))
    }

    @Test
    fun normalizationUnifiesBrackets() {
        // 括号判重只要求同一张卡不分裂:ASCII / 全角(（）) / 【】 / [] 各类括号
        // 一律压成同一个 '(' (开闭不分)。
        val variants = listOf(
            "招商【储蓄】",
            "招商[储蓄]",
            "招商（储蓄）",
            "招商(储蓄)",
        ).map { YimuAccountMapper.normalize(it) }.toSet()
        assertEquals(1, variants.size, "四种括号写法必须归一成同一个名字")
        assertEquals("招商(储蓄(", variants.first())
    }

    @Test
    fun normalizationUnifiesSeparators() {
        assertEquals("中国 银行", YimuAccountMapper.normalize("中国·银行"))
        assertEquals("中国 银行", YimuAccountMapper.normalize("中国—银行"))
        assertEquals("中国 银行", YimuAccountMapper.normalize("中国_银行"))
    }

    @Test
    fun normalizationMakesEquivalentNamesCollide() {
        assertEquals(
            YimuAccountMapper.normalize("支付宝"),
            YimuAccountMapper.normalize("  支付宝 "),
        )
        assertEquals(
            YimuAccountMapper.normalize("美团月付"),
            YimuAccountMapper.normalize("美团月付 "),
        )
    }

    @Test
    fun defaultAccountNameIsCash() {
        assertEquals("现金", YimuAccountMapper.DEFAULT_ACCOUNT_NAME)
        assertEquals(AccountType.CASH, YimuAccountMapper.inferType(YimuAccountMapper.DEFAULT_ACCOUNT_NAME))
    }
}