package com.han1997.vipledger.core.domain.model

import com.han1997.vipledger.core.model.RuleMatchType
import com.han1997.vipledger.core.model.RuleSource
import com.han1997.vipledger.core.model.RuleTargetField

/** 商户/关键字 → 分类规则（docs/DATA-MODEL.md 3.8）。匹配引擎在 M6，本层只负责存取。 */
data class MerchantRule(
    val id: String,
    val ledgerId: String,
    val pattern: String,
    val matchType: RuleMatchType = RuleMatchType.CONTAINS,
    val targetField: RuleTargetField = RuleTargetField.ANY,
    val categoryId: String,
    val priority: Int = 100,
    val source: RuleSource = RuleSource.USER,
    val enabled: Boolean = true,
    val hitCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)