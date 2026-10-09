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
    private val merchantIdentity = Regex("(?:商户全称|商户名称|收款方全称|付款方全称|收款方|付款方)(?:[：:][ \\t]*|\\s+)([^\\n;；]+)")
    private val merchantFallback = Regex("(?:交易对方|商品名称|商家)(?:[：:][ \\t]*|\\s+)([^\\n;；]+)")
    private val external = Regex("(?:退款单号|交易单号|交易号|订单编号|订单号)[：:\\s]*([A-Za-z0-9_-]{6,80})")
    private val original = Regex("(?:原交易单号|原订单号)[：:\\s]*([A-Za-z0-9_-]{6,80})")
    private val paidTimeLabel = Regex("交易时间|付款时间|支付时间|到账时间|退款时间")
    private val paidTime = Regex("(?:${paidTimeLabel.pattern})[：:\\s]*([0-9]{4}(?:[-/][0-9]{1,2}[-/][0-9]{1,2}|年[0-9]{1,2}月[0-9]{1,2}日)\\s+[0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?)")
    private val wechatDebit = Regex("(?m)^[ \\t]*[-−][ \\t]*([0-9]+(?:,[0-9]{3})*\\.[0-9]{2})[ \\t]*$")

    fun isPaymentPage(text: String, app: PaymentApp? = null): Boolean =
        success.containsMatchIn(text) || unfinished.containsMatchIn(text) || (app == PaymentApp.PDD && isPddSignedOrder(text)) ||
            (app == PaymentApp.JD && isJdCompletedOrder(text))

    private fun isJdCompletedOrder(text: String): Boolean =
        Regex("(?m)^[ \\t✓✔√]*完成[ \\t]*$").containsMatchIn(text) && text.contains("实付款") &&
            text.contains("支付时间") && token.containsMatchIn(text) && external.containsMatchIn(text)

    private fun isPddSignedOrder(text: String): Boolean =
        Regex("(?m)^[ \\t]*已签收[ \\t]*$").containsMatchIn(text) && text.contains("实付") &&
            text.contains("下单时间") && token.containsMatchIn(text) && external.containsMatchIn(text)

    fun parse(observation: PaymentObservation): PaymentFacts? {
        val sourceText = observation.text.take(16_384).replace('\u00a0', ' ')
        val text = when {
            observation.app == PaymentApp.PDD -> sourceText.replace(Regex("实付[：:]?[,，\\s]+"), "实付 ")
            observation.app == PaymentApp.JD && observation.source == CaptureSource.OCR -> sourceText
                .replace(Regex("(?m)^[ \\t]*[oO0○©][ \\t]*完成[ \\t]*$"), "完成")
                .replace(Regex("(?m)^订单蝙号(?=[：:\\s])"), "订单编号")
                .replace(Regex("(?m)^支付万式(?=[：:\\s])"), "支付方式")
                .replace(Regex("(?m)^支付时闻(?=[：:\\s])"), "支付时间")
                .lineSequence().joinToString("\n") { line ->
                    if (line.trimStart().startsWith("实付款")) line.replace(Regex("台计(?=[：:\\s]*[¥￥0-9])"), "合计") else line
                }
            else -> sourceText
        }
        val pddOrder = observation.app == PaymentApp.PDD && observation.source != CaptureSource.NOTIFICATION && isPddSignedOrder(text)
        val jdOrder = observation.app == PaymentApp.JD && observation.source != CaptureSource.NOTIFICATION && isJdCompletedOrder(text)
        if (!isPaymentPage(text) && !pddOrder && !jdOrder) return null
        val alipayHeader = if (observation.app == PaymentApp.ALIPAY && observation.source != CaptureSource.NOTIFICATION &&
            text.contains("账单详情") && text.contains("支付时间") && text.contains("付款方式")) {
            val lines = text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
                .fold(mutableListOf<String>()) { result, line -> result.apply { if (lastOrNull() != line) add(line) } }
            Regex("(?m)^([^\\n]+)\\n(?:[-−][ \\t]*([0-9]+(?:,[0-9]{3})*\\.[0-9]{2})|支出[ \\t]*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)[ \\t]*元)\\n交易成功$")
                .find(lines.joinToString("\n"))
        } else null
        val complete = (success.containsMatchIn(text) || pddOrder || jdOrder) && !unfinished.containsMatchIn(text)
        val type = when {
            risky.containsMatchIn(text) -> null
            Regex("退款成功|退款到账|已退款").containsMatchIn(text) -> TransactionType.REFUND
            target.containsMatchIn(text) && Regex("转账成功|提现成功|充值成功").containsMatchIn(text) -> TransactionType.TRANSFER
            Regex("收款到账|收款成功|已收款|转账到账").containsMatchIn(text) -> TransactionType.INCOME
            Regex("支付成功|付款成功").containsMatchIn(text) || alipayHeader != null || pddOrder || jdOrder -> TransactionType.EXPENSE
            // “转账成功” alone does not establish whether the recipient is another own account.
            else -> null
        }
        val labeledMatches = labeledAmount.findAll(text).toList()
        val labeled = labeledMatches.mapNotNull { cents(it.groupValues[1]) }
        // Verified WeChat bill layout: the signed header is the actual debit; discounts are separate.
        val wechatBill = observation.app == PaymentApp.WECHAT && observation.source != CaptureSource.NOTIFICATION &&
            type == TransactionType.EXPENSE && Regex("当前状态[：:\\s]*支付成功").containsMatchIn(text) &&
            text.contains("支付时间") && external.containsMatchIn(text)
        val debitMatches = if (wechatBill) wechatDebit.findAll(text).toList() else emptyList()
        val debits = debitMatches.mapNotNull { cents(it.groupValues[1]) }
        val alipayAmount = alipayHeader?.let { cents(it.groupValues[2].ifBlank { it.groupValues[3] }) }
        val jdTotals = if (jdOrder) Regex("实付款[\\s\\S]{0,120}?合计[：:\\s]*[¥￥]?\\s*" + money).findAll(text).toList() else emptyList()
        val jdAmounts = jdTotals.mapNotNull { cents(it.groupValues[1]) }
        val amounts = if (jdOrder) jdAmounts + labeled else if (alipayHeader != null) listOfNotNull(alipayAmount) + labeled
            else if (debitMatches.isNotEmpty()) debits + labeled else if (labeledMatches.isNotEmpty()) labeled else currencyAmount.findAll(text)
            .mapNotNull { cents(it.groupValues[1].ifBlank { it.groupValues[2] }) }.toList()
        val foreignCurrency = Regex("USD|HKD|EUR|JPY|美元|港币|港元|欧元|日元|US\\$", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val priceOnly = !jdOrder && alipayHeader == null && labeledMatches.isEmpty() && debitMatches.isEmpty() && Regex("原价|优惠|订单金额|商品金额|应付").containsMatchIn(text)
        val amount = amounts.distinct().singleOrNull().takeUnless {
            foreignCurrency || priceOnly || labeled.size != labeledMatches.size || debits.size != debitMatches.size ||
                (alipayHeader != null && alipayAmount == null) || (jdOrder && (jdAmounts.isEmpty() || jdAmounts.size != jdTotals.size))
        }
        val originalMatch = original.find(text)
        val idText = if (originalMatch == null) text else text.removeRange(originalMatch.range)
        val paidAt = paidTime.find(text)?.groupValues?.get(1)?.let { value -> runCatching {
            val normalized = value.replace('/', '-').replace('年', '-').replace('月', '-').replace("日", "")
                .replace(Regex("\\s+"), " ")
            val format = java.time.format.DateTimeFormatter.ofPattern("uuuu-M-d H:mm[:ss]")
                .withResolverStyle(java.time.format.ResolverStyle.STRICT)
            java.time.LocalDateTime.parse(normalized, format).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull() }
        val orderMerchant = if (pddOrder || jdOrder) Regex("(?m)^([^\\n:：,，]{1,120}(?:旗舰店|专卖店|专营店|自营店))(?:[ \\t]+进店)?[ \\t]*$")
            .findAll(text).map { it.groupValues[1].trim() }.distinct().singleOrNull() else null
        val jdStoreHeader = if (jdOrder) Regex("(?m)^([^\\n:：,，]{1,120})[ \\t]+进店[ \\t>›]*$")
            .findAll(text).map { it.groupValues[1].trim() }.distinct().singleOrNull() else null
        return PaymentFacts(
            type, amount, (merchantIdentity.find(text)?.groupValues?.get(1) ?: alipayHeader?.groupValues?.get(1)
                ?: jdStoreHeader ?: orderMerchant ?: merchantFallback.find(text)?.groupValues?.get(1))?.trim()?.take(120).orEmpty(),
            token.find(text)?.groupValues?.get(1)?.let {
                normalizeToken(when (observation.app) {
                    PaymentApp.ALIPAY -> it.trim().removeSuffix("查看扣款顺序")
                    PaymentApp.JD -> it.trim().trimEnd('>', '›')
                    else -> it
                })
            },
            target.find(text)?.groupValues?.get(1)?.let(::normalizeToken),
            external.find(idText)?.groupValues?.get(1), originalMatch?.groupValues?.get(1), complete,
            when { !complete -> ReviewReason.NOT_SUCCESS; amount == null -> ReviewReason.AMOUNT
                type == null -> ReviewReason.DIRECTION
                pddOrder || jdOrder -> ReviewReason.TIME // Historical order pages always require review.
                paidAt == null && paidTimeLabel.containsMatchIn(text) -> ReviewReason.TIME
                else -> ReviewReason.NONE }, paidAt,
        )
    }

    fun normalizeToken(value: String): String = value.trim().replace(Regex("\\s+"), "")
        .replace('（', '(').replace('）', ')').take(120)

    private fun cents(value: String): Long? = runCatching {
        BigDecimal(value.replace(",", "")).movePointRight(2).longValueExact().takeIf { it > 0 }
    }.getOrNull()
}
