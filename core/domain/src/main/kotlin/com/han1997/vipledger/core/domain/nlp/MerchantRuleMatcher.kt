package com.han1997.vipledger.core.domain.nlp

import com.han1997.vipledger.core.domain.model.MerchantRule
import com.han1997.vipledger.core.model.RuleMatchType
import com.han1997.vipledger.core.model.RuleTargetField

/**
 * P6 `merchant_rule` 匹配：命中直接定分类（ARCHITECTURE §6）。
 *
 * 规则按 `priority` 升序尝试（越小越优先），只取第一条命中；`enabled = false` 跳过。
 * 正则规则解析失败时静默跳过该条，不影响其余规则（不抛异常到上层）。
 */
object MerchantRuleMatcher {

    data class Hit(val ruleId: String, val categoryId: String)

    fun firstHit(title: String, remark: String, rules: List<MerchantRule>): Hit? {
        val ordered = rules.filter { it.enabled && it.pattern.isNotBlank() }.sortedBy { it.priority }
        for (rule in ordered) {
            val target = when (rule.targetField) {
                RuleTargetField.TITLE -> title
                RuleTargetField.REMARK -> remark
                RuleTargetField.ANY -> "$title $remark"
            }
            if (matches(target, rule)) return Hit(rule.id, rule.categoryId)
        }
        return null
    }

    private fun matches(text: String, rule: MerchantRule): Boolean = when (rule.matchType) {
        RuleMatchType.EQUALS -> text == rule.pattern
        RuleMatchType.CONTAINS -> text.contains(rule.pattern)
        RuleMatchType.REGEX -> runCatching { Regex(rule.pattern).containsMatchIn(text) }.getOrDefault(false)
    }
}
