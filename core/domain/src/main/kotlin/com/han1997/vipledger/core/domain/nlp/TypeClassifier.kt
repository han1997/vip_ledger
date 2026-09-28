package com.han1997.vipledger.core.domain.nlp

import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.model.TransactionType

/**
 * P7 收支类型判定：类型线索词 → [TransactionType]（研究 §3.1）。
 *
 * 处理研究 §3.2 的陷阱：`还信用卡` 判 DEBT_REPAY 而非 EXPENSE；`退款` 判 INCOME；
 * `借出` 判 TRANSFER（债权）而非收入；单字「还」的 hái/huán 歧义进
 * [NlpWarning.AMBIGUOUS_TYPE_CUE]，不硬判、不写特判 hack。
 */
object TypeClassifier {

    data class Result(
        val type: TransactionType,
        val hasCue: Boolean,
        val warnings: List<NlpWarning>,
    )

    fun classify(text: String, matchedAccounts: List<AccountMatcher.Match>, accounts: List<Account>): Result {
        val debtAccountMatched = matchedAccounts.any { match ->
            accounts.firstOrNull { it.id == match.accountId }?.isDebt == true
        }

        if (STRONG_DEBT.containsMatchIn(text) || (text.contains('还') && debtAccountMatched)) {
            return Result(TransactionType.DEBT_REPAY, hasCue = true, warnings = emptyList())
        }
        if (TRANSFER.containsMatchIn(text)) {
            return Result(TransactionType.TRANSFER, hasCue = true, warnings = emptyList())
        }
        if (INCOME.containsMatchIn(text)) {
            return Result(TransactionType.INCOME, hasCue = true, warnings = emptyList())
        }
        if (ADJUST.containsMatchIn(text)) {
            return Result(TransactionType.ADJUST, hasCue = true, warnings = emptyList())
        }
        if (text.contains('还')) {
            // 单字「还」：可能是 hái（还是）或 huán（归还），词表面不足以判类型。
            return Result(
                TransactionType.EXPENSE,
                hasCue = false,
                warnings = listOf(NlpWarning.AMBIGUOUS_TYPE_CUE),
            )
        }
        return Result(TransactionType.EXPENSE, hasCue = false, warnings = emptyList())
    }

    private val STRONG_DEBT = Regex("还款|还清|还花呗|还借呗|还房贷|还车贷|还信用卡|还贷")
    private val TRANSFER = Regex("转账|转给|转到|转入|转出|提现|充值|借出")
    private val INCOME = Regex("收到|到账|发工资|工资|收入|退款|退回|报销|利息|分红|中奖|收了|卖了|红包")
    private val ADJUST = Regex("余额调整|对账|更正")
}
