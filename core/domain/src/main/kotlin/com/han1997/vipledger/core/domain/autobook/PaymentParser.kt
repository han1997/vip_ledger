package com.han1997.vipledger.core.domain.autobook

import com.han1997.vipledger.core.model.TransactionType
import java.math.BigDecimal

/** Conservative, offline templates. Platform capture adapters establish the source boundary. */
object PaymentParser {
    private val success = Regex("支付成功|付款成功|交易成功|收款到账|收款成功|已收款|退款成功|退款到账|已退款|转账成功|转账到账|提现成功|充值成功")
    private val unfinished = Regex("待付款|等待付款|支付失败|付款失败|交易关闭|退款申请|退款处理中|退款审核|退款失败|等待收款|待收款")
    private val risky = Regex("借款|借钱|还款|代付|AA收款|红包")
    private val money = "(?<![0-9.,-])([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)(?![0-9.])"
    private val labeledAmount = Regex("(?:实付金额|实际付款|实付款|实付|付款金额|支付金额|到账金额|收款金额|退款金额|转账金额)[：:\\s]*[¥￥]?\\s*" + money)
    private val currencyAmount = Regex("[¥￥]\\s*" + money + "|" + money + "\\s*元")
    private val token = Regex("(?:付款方式|支付方式|收款账户|到账账户|退款账户|转出账户)[：:\\s]*([^\\n;；]+)")
    private val target = Regex("(?:转入账户)[：:\\s]*([^\\n;；]+)")
    private val merchant = Regex("(?:商户全称|商户名称|收款方|付款方|交易对方|商品名称|商家)[：:\\s]*([^\\n;；]+)")
    private val external = Regex("(?:退款单号|交易单号|交易号|订单编号|订单号)[：:\\s]*([A-Za-z0-9_-]{6,80})")
    private val original = Regex("(?:原交易单号|原订单号)[：:\\s]*([A-Za-z0-9_-]{6,80})")
    private val paidTime = Regex("(?:交易时间|付款时间|到账时间|退款时间)[：:\\s]*([0-9]{4}[-/][0-9]{2}[-/][0-9]{2} [0-9]{2}:[0-9]{2}(?::[0-9]{2})?)")

    fun isPaymentPage(text: String): Boolean = success.containsMatchIn(text) || unfinished.containsMatchIn(text)

    fun parse(observation: PaymentObservation): PaymentFacts? {
        val text = observation.text.take(16_384).replace('\u00a0', ' ')
        if (!isPaymentPage(text)) return null
        val complete = success.containsMatchIn(text) && !unfinished.containsMatchIn(text)
        val type = when {
            risky.containsMatchIn(text) -> null
            Regex("退款成功|退款到账|已退款").containsMatchIn(text) -> TransactionType.REFUND
            target.containsMatchIn(text) && Regex("转账成功|提现成功|充值成功").containsMatchIn(text) -> TransactionType.TRANSFER
            Regex("收款到账|收款成功|已收款|转账到账").containsMatchIn(text) -> TransactionType.INCOME
            Regex("支付成功|付款成功").containsMatchIn(text) -> TransactionType.EXPENSE
            // “转账成功” alone does not establish whether the recipient is another own account.
            else -> null
        }
        val labeledMatches = labeledAmount.findAll(text).toList()
        val labeled = labeledMatches.mapNotNull { cents(it.groupValues[1]) }
        val amounts = if (labeledMatches.isNotEmpty()) labeled else currencyAmount.findAll(text)
            .mapNotNull { cents(it.groupValues[1].ifBlank { it.groupValues[2] }) }.toList()
        val foreignCurrency = Regex("USD|HKD|EUR|JPY|美元|港币|港元|欧元|日元|US\\$", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val priceOnly = labeledMatches.isEmpty() && Regex("原价|优惠|订单金额|商品金额|应付").containsMatchIn(text)
        val amount = amounts.distinct().singleOrNull().takeUnless { foreignCurrency || priceOnly || labeled.size != labeledMatches.size }
        val originalMatch = original.find(text)
        val idText = if (originalMatch == null) text else text.removeRange(originalMatch.range)
        val paidAt = paidTime.find(text)?.groupValues?.get(1)?.let { value -> runCatching {
            val format = java.time.format.DateTimeFormatter.ofPattern(if (value.length == 19) "uuuu-MM-dd HH:mm:ss" else "uuuu-MM-dd HH:mm")
                .withResolverStyle(java.time.format.ResolverStyle.STRICT)
            java.time.LocalDateTime.parse(value.replace('/', '-'), format).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull() }
        return PaymentFacts(
            type, amount, merchant.find(text)?.groupValues?.get(1)?.trim()?.take(120).orEmpty(),
            token.find(text)?.groupValues?.get(1)?.let(::normalizeToken),
            target.find(text)?.groupValues?.get(1)?.let(::normalizeToken),
            external.find(idText)?.groupValues?.get(1), originalMatch?.groupValues?.get(1), complete,
            when { !complete -> ReviewReason.NOT_SUCCESS; amount == null -> ReviewReason.AMOUNT
                type == null -> ReviewReason.DIRECTION
                paidAt == null && Regex("交易时间|付款时间|到账时间|退款时间").containsMatchIn(text) -> ReviewReason.TIME
                else -> ReviewReason.NONE }, paidAt,
        )
    }

    fun normalizeToken(value: String): String = value.trim().replace(Regex("\\s+"), "")
        .replace('（', '(').replace('）', ')').take(120)

    private fun cents(value: String): Long? = runCatching {
        BigDecimal(value.replace(",", "")).movePointRight(2).longValueExact().takeIf { it > 0 }
    }.getOrNull()
}
