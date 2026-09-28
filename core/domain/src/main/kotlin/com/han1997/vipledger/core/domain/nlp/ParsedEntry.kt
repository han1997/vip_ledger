package com.han1997.vipledger.core.domain.nlp

import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * 离散证据分档，**不伪造概率**。
 *
 * - HIGH：金额带货币单位/符号，且类型有明确线索（或 `merchant_rule` 命中）；
 * - MEDIUM：金额带货币单位但类型无线索，或类型有线索但金额无单位；
 * - LOW：纯裸数字（无任何货币单位）且类型无线索，或存在歧义 token（单字「还」等）。
 *
 * 依据研究 §3.3：tier1 只用枚举，把连续置信度留给 M7 的端侧分类器。
 */
enum class Confidence { HIGH, MEDIUM, LOW }

/** 解析层可能「未能识别」的字段。未识别一律明示，绝不静默降级成默认值（PRD Requirement 8）。 */
enum class ParsedField {
    AMOUNT,
    TYPE,

    /**
     * 保留档位：tier 1（规则层）**从不**产出它——规则层没有日期线索时按项目决策默认「今天」
     * （研究 §2.6，用户说「午饭35」几乎总指当下），这是有意的例外，不算静默降级。
     * 该档位留给 tier 3 云端合并：云端日期不可信且规则层无日期时标此档（PRD Open Questions）。
     */
    DATE,

    /** 保留档位：tier 1 用 `ParsedEntry.time` 为 null 表达「未识别钟点」，不重复标此档。 */
    TIME,

    ACCOUNT,
    TO_ACCOUNT,
    CATEGORY,
    TITLE,
}

/** 解析过程中的已知歧义/局限，透传到草稿供 UI 提示，不写特判 hack。 */
enum class NlpWarning {
    /** 单字「还」等无法判定 hái / huán 的歧义线索。 */
    AMBIGUOUS_TYPE_CUE,

    /** `requiresTargetAccount` 为真但缺目标账户（如「转账 500」没写转到哪）。 */
    MISSING_TARGET_ACCOUNT,

    /** 出现折扣/比例词但规则层算不出折后价。 */
    RATIO_NOT_RESOLVED,

    /** 金额来自裸整数，无货币单位支撑。 */
    LOW_CONFIDENCE_AMOUNT,

    /**
     * 句中同时存在「带货币单位」与「裸数字」两类金额候选，后者被 [AmountExtractor] 的
     * 「有单位优先」启发式丢弃（用于剔除「买了 3 个包子 10 块」里的量词 `3`）。
     * 丢弃本身保留启发式，但必须让用户看见，否则「午饭 35 块 打车 28」会静默少一笔
     * （PRD Requirement 8「未识别字段要明示」的精神）。
     */
    BARE_AMOUNT_DROPPED,
}

/**
 * 一句话里解析出的一笔待确认草稿。
 *
 * 字段类型刻意与云端 tier 3 复用：`accountId` / `categoryId` / `toAccountId` 为 null 表示
 * 「未识别」，配合 [unresolved] 明示；调用方必须拿真实账户/分类列表做 id 校验后再落库。
 */
data class ParsedEntry(
    val amount: Money,
    val type: TransactionType,
    val date: LocalDate,
    /** 无法从文本确定具体钟点时为空，由落库层决定是否用「当前时刻」。 */
    val time: LocalTime?,
    val accountId: String?,
    val toAccountId: String?,
    val categoryId: String?,
    val title: String,
    val remark: String,
    val confidence: Confidence,
    /** 金额 span 是否带货币单位/符号，供上层判断证据强度。 */
    val amountHasUnit: Boolean,
    /** `merchant_rule` 命中时的规则 id。 */
    val matchedRuleId: String? = null,
    /** 规则层未能确定的字段，明示给用户（PRD Requirement 8）。 */
    val unresolved: Set<ParsedField> = emptySet(),
    val warnings: List<NlpWarning> = emptyList(),
    /** 已消费的 span，调试/审计用。 */
    val consumed: List<Span> = emptyList(),
)

/** 解析失败的原因分类（不抛异常，也不把自由文本当用户文案，见 error-handling.md）。 */
enum class ParseFailure {
    /** 去掉「帮我记一笔」等触发词后没有剩余内容。 */
    EMPTY_INPUT,

    /** 文本里找不到任何可当金额的片段。 */
    NO_AMOUNT,

    /** 抽取器内部异常（被 runCatching 收敛）。 */
    INTERNAL_ERROR,
}

/**
 * 规则层解析结果，`Ok / Partial / Failed` 语义，**永不抛异常**（PRD Requirement 2）。
 *
 * - [Ok]：至少一笔，且所有关键字段都已确定；
 * - [Partial]：至少识别到部分信息，但存在未识别字段。允许 [entries] 为空
 *   （例如只说了「今天」——识别到日期但没有金额，尚无完整可落库的草稿）；
 * - [Failed]：什么都没识别到。
 */
sealed interface ParseResult {

    val entries: List<ParsedEntry>
    val unresolved: Set<ParsedField>
    val warnings: List<NlpWarning>

    data class Ok(override val entries: List<ParsedEntry>) : ParseResult {
        override val unresolved: Set<ParsedField> get() = emptySet()
        override val warnings: List<NlpWarning> get() = emptyList()
    }

    data class Partial(
        override val entries: List<ParsedEntry>,
        override val unresolved: Set<ParsedField>,
        override val warnings: List<NlpWarning> = emptyList(),
    ) : ParseResult

    data class Failed(val reason: ParseFailure) : ParseResult {
        override val entries: List<ParsedEntry> get() = emptyList()
        override val unresolved: Set<ParsedField> get() = emptySet()
        override val warnings: List<NlpWarning> get() = emptyList()
    }
}
