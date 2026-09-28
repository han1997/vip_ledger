package com.han1997.vipledger.core.domain.nlp

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.Category
import kotlinx.datetime.LocalDate

/**
 * 云端 LLM 解析上下文：调用方把参考日与**真实**账户/分类列表一并传入，
 * 供实现校验模型返回的 id 是否对得上（PRD Requirement 8「未识别字段要明示」）。
 */
data class ParseContext(
    val today: LocalDate,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
)

/**
 * 云端解析（tier 3）接缝（PRD D4）。
 *
 * **接口在 `:core:domain`，实现放 `:core:data`**（复用 M5 已在用的 OkHttp 栈，
 * `OpenAiCompatibleLlmParserClient`，调用 OpenAI 兼容的 `POST {baseUrl}/chat/completions`）。
 * 调用方在「允许联网」开关关闭时传 `null`（见 [NlEntryPipeline]），实现自身也会在
 * [com.han1997.vipledger.core.domain.ai.AiSettings.networkEnabled] 为 false 时直接返回失败、
 * 不发起网络请求。
 *
 * 实现约定（研究 `llm-structured-output.md`）：失败一律返回 [AppResult.Failure]，
 * 不抛异常；返回的 id 必须用 [ParseContext] 里的真实列表校验，对不上则置空并标
 * [ParsedField] 未识别。
 */
interface LlmParserClient {

    suspend fun parse(text: String, context: ParseContext): AppResult<List<ParsedEntry>>
}
