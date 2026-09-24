package com.han1997.vipledger.core.model

/** 商户/关键字规则的匹配方式（docs/DATA-MODEL.md 3.8）。 */
enum class RuleMatchType { CONTAINS, REGEX, EQUALS }

/** 规则匹配的字段范围。 */
enum class RuleTargetField { TITLE, REMARK, ANY }

/** 规则来源：用户手写、内置、从修正样本学习。 */
enum class RuleSource { USER, BUILTIN, LEARNED }