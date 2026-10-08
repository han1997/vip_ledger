package com.han1997.vipledger.core.data.autobook

import com.han1997.vipledger.core.database.entity.*
import com.han1997.vipledger.core.domain.autobook.*
import com.han1997.vipledger.core.model.TransactionType
import org.json.JSONObject

internal fun AutoConfigEntity?.domain() = this?.let {
    AutoBookConfig(ledgerId, enabled, ocrEnabled, apps(enabledApps), apps(verifiedApps))
} ?: AutoBookConfig()
private fun apps(text: String) = text.split(',').mapNotNull { name -> PaymentApp.entries.find { it.name == name } }.toSet()
internal fun AutoBookConfig.entity() = AutoConfigEntity(1, ledgerId, enabled, ocrEnabled,
    enabledApps.joinToString(",") { it.name }, verifiedApps.joinToString(",") { it.name })
internal fun AccountBinding.entity() = AutoBindingEntity(ledgerId, app.name, PaymentParser.normalizeToken(token), accountId)
internal fun AutoBindingEntity.domain() = AccountBinding(ledgerId, PaymentApp.valueOf(app), token, accountId)

internal fun AutoCandidate.entity(): AutoCandidateEntity {
    val json = JSONObject().put("type", facts.type?.name).put("amount", facts.amountCents)
        .put("merchant", facts.merchant).put("account", facts.accountToken).put("target", facts.targetToken)
        .put("external", facts.externalId).put("original", facts.originalId).put("success", facts.success)
        .put("reason", facts.reason.name).put("paidAt", facts.paidAt).toString()
    return AutoCandidateEntity(id, ledgerId, app.name, source.name, occurredAt, json, accountId,
        targetAccountId, status.name, reason.name, txnId, refundOfTxnId, duplicateTxnId)
}
internal fun AutoCandidateEntity.domain(): AutoCandidate {
    val json = JSONObject(facts)
    fun optional(key: String) = if (json.isNull(key)) null else json.getString(key)
    val facts = PaymentFacts(optional("type")?.let(TransactionType::valueOf),
        if (json.isNull("amount")) null else json.getLong("amount"), json.getString("merchant"),
        optional("account"), optional("target"), optional("external"), optional("original"),
        json.getBoolean("success"), ReviewReason.valueOf(json.getString("reason")),
        if (json.isNull("paidAt")) null else json.getLong("paidAt"))
    return AutoCandidate(id, ledgerId, PaymentApp.valueOf(app), CaptureSource.valueOf(source), occurredAt,
        facts, accountId, targetAccountId, CandidateStatus.valueOf(status), ReviewReason.valueOf(reason),
        txnId, refundOfTxnId, duplicateTxnId)
}
