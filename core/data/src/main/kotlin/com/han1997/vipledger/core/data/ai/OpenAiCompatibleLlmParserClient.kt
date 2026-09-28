@file:OptIn(ExperimentalTime::class)

package com.han1997.vipledger.core.data.ai

import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.ai.AiSettings
import com.han1997.vipledger.core.domain.ai.AiSettingsRepository
import com.han1997.vipledger.core.domain.nlp.Confidence
import com.han1997.vipledger.core.domain.nlp.LlmParserClient
import com.han1997.vipledger.core.domain.nlp.ParseContext
import com.han1997.vipledger.core.domain.nlp.ParsedEntry
import com.han1997.vipledger.core.domain.nlp.ParsedField
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * OpenAI 兼容的云端解析客户端（tier 3，实现 [LlmParserClient]，PRD D4）。
 *
 * 只依赖既有 OkHttp（不引入 OpenAI SDK / Retrofit）；`POST {baseUrl}/chat/completions`，
 * 严格 JSON 出参 + 解析失败重试一次。
 *
 * **降级阶梯**（研究 §5 / PRD AC「云端返回不认识的 `response_format` 而 400 时降级」）：
 * 1. 第 1 次：`response_format: {"type":"json_object"}` + 提示词内嵌 schema；
 * 2. 命中 `response_format` 相关 400 或内容解析失败时，**恰好一次**提示式修复重试（去掉
 *    `response_format`，附上上一次输出与具体错误）；strict `json_schema` 是可选升级档，不是基线。
 *
 * 整个云端级网络预算 **≤ 2 次**；任何失败都返回 [AppResult.Failure]，绝不抛异常给调用方。
 *
 * **隐私红线**：`apiKey`、请求/响应正文（用户账目文本）**一律不进日志**——本类不做任何日志输出。
 */
class OpenAiCompatibleLlmParserClient(
    private val settings: AiSettingsRepository,
    private val client: OkHttpClient = defaultAiHttpClient(),
) : LlmParserClient {

    override suspend fun parse(text: String, context: ParseContext): AppResult<List<ParsedEntry>> {
        // 每次调用都重新读配置（界面改完即时生效）。读配置会走 AndroidKeyStore + AES/GCM 解密，
        // 是阻塞 IO，必须放 IO 线程，避免在 Main 上读配置造成掉帧（PRD D4）。
        val config = runCatching { withContext(Dispatchers.IO) { settings.settings() } }
            .getOrElse { return AppResult.failure(AppError.Storage("AI 配置读取失败", it)) }
        if (!config.networkEnabled) return AppResult.failure(AppError.Validation("联网解析未开启"))
        if (config.baseUrl.isBlank()) return AppResult.failure(AppError.Validation("未配置 AI 服务地址"))
        val endpoint = chatCompletionsUrl(config.baseUrl)
            ?: return AppResult.failure(AppError.Validation("AI 服务地址无效"))

        val system = systemPrompt(context)
        val first = execute(endpoint, config, conversation(system, text), jsonMode = true)
        val firstContent = (first as? Attempt.Ok)?.content
        if (firstContent != null) {
            val entries = StrictJson.extractObject(firstContent)?.let { toEntries(it, context) }
            if (!entries.isNullOrEmpty()) return AppResult.success(entries)
        } else {
            immediateFailure(first)?.let { return AppResult.failure(it) }
        }

        // 唯一一次修复重试：去掉 response_format（最可能的 400 来源），并附上上次输出与错误。
        val reason = failureReason(firstContent)
        val second = execute(endpoint, config, repairConversation(system, text, firstContent, reason), jsonMode = false)
        return when (second) {
            is Attempt.Ok -> {
                val entries = StrictJson.extractObject(second.content)?.let { toEntries(it, context) }
                if (!entries.isNullOrEmpty()) {
                    AppResult.success(entries)
                } else {
                    AppResult.failure(AppError.Parse(reason))
                }
            }
            is Attempt.HttpError -> AppResult.failure(httpError(second.code))
            Attempt.TransportError -> AppResult.failure(AppError.Network("云端解析超时或网络不可达"))
        }
    }

    // ---- 网络 ----

    private suspend fun execute(
        endpoint: String,
        config: AiSettings,
        messages: JSONArray,
        jsonMode: Boolean,
    ): Attempt = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
                .put("model", config.model)
                .put("messages", messages)
                .put("stream", false)
            if (jsonMode) body.put("response_format", JSONObject().put("type", "json_object"))
            val builder = Request.Builder()
                .url(endpoint)
                .post(body.toString().toRequestBody(JSON_MEDIA))
            // 本地服务器常无需 key；有 key 才带 Authorization，绝不打印该头。
            if (config.apiKey.isNotBlank()) builder.header("Authorization", "Bearer ${config.apiKey}")
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) return@use Attempt.HttpError(response.code)
                Attempt.Ok(contentOf(response.body?.string()))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            Attempt.TransportError
        } catch (_: Throwable) {
            // 极兜底（如非法 URL）；同样收敛为可降级的结果，不抛给调用方。
            Attempt.TransportError
        }
    }

    private fun contentOf(body: String?): String {
        val root = runCatching { JSONObject(body.orEmpty()) }.getOrNull() ?: return ""
        val message = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message") ?: return ""
        // 思考型模型可能把内容放在 content，也可能 content 为空而正文在 reasoning_content。
        return message.optString("content").ifBlank { message.optString("reasoning_content") }
    }

    // ---- 消息 ----

    private fun conversation(system: String, text: String): JSONArray = JSONArray()
        .put(message("system", system))
        .put(message("user", text))

    private fun repairConversation(
        system: String,
        text: String,
        previous: String?,
        reason: String?,
    ): JSONArray {
        val array = conversation(system, text)
        if (!previous.isNullOrBlank()) array.put(message("assistant", previous))
        val note = buildString {
            append("上一条回复无法解析")
            if (!reason.isNullOrBlank()) append("（").append(reason).append("）")
            append("。请只返回一个符合上面结构的 JSON 对象，不要任何解释、不要 Markdown 代码块。")
        }
        array.put(message("user", note))
        return array
    }

    private fun message(role: String, content: String): JSONObject =
        JSONObject().put("role", role).put("content", content)

    /**
     * 系统提示词内嵌完整字段契约与真实 id 列表（研究 §5「schema 单一来源」）。
     * 必须出现字面量 `JSON`：部分服务商的 `json_object` 模式要求消息里含 "JSON"，否则 400。
     */
    private fun systemPrompt(context: ParseContext): String = buildString {
        append("你是记账解析助手，把用户的一句话转成结构化 JSON。\n")
        append("只返回一个 JSON 对象，不要任何解释、不要 Markdown 代码块。\n")
        append(
            "结构：{\"entries\":[{\"amountCents\":<整数>,\"type\":\"expense|income|transfer\"," +
                "\"categoryId\":<字符串或 null>,\"accountId\":<字符串或 null>," +
                "\"occurredAt\":<整数 epoch 毫秒或 null>,\"title\":<字符串>,\"remark\":<字符串>," +
                "\"confidence\":<0 到 1>}]}\n",
        )
        append("- amountCents 单位是「分」不是「元」：35 元 → 3500，12 块 5 → 1250。\n")
        append("- type 只能是 expense / income / transfer 之一。\n")
        append("- occurredAt 不确定就填 null，不要臆造日期。\n")
        append("- 一句话多笔（如「午饭35 打车28」）请输出多条 entries。\n")
        if (context.categories.isNotEmpty()) {
            append("- categoryId 只能取下列 id（格式 id=名称），取不到就填 null：")
            append(context.categories.joinToString("；") { "${it.id}=${it.name}" })
            append("\n")
        } else {
            append("- categoryId 取不到就填 null。\n")
        }
        if (context.accounts.isNotEmpty()) {
            append("- accountId 只能取下列 id（格式 id=名称），取不到就填 null：")
            append(context.accounts.joinToString("；") { "${it.id}=${it.name}" })
            append("\n")
        } else {
            append("- accountId 取不到就填 null。\n")
        }
    }

    // ---- 字段映射（把实测到的模型缺陷吸收进这里）----

    private fun toEntries(root: JSONObject, context: ParseContext): List<ParsedEntry> {
        val array = root.optJSONArray("entries")
        val objects = if (array != null) {
            (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        } else if (root.firstOf(*RECOGNIZED_KEYS) != null) {
            listOf(root)
        } else {
            emptyList()
        }
        return objects.mapNotNull { toEntry(it, context) }
    }

    private fun toEntry(obj: JSONObject, context: ParseContext): ParsedEntry? {
        if (obj.firstOf(*RECOGNIZED_KEYS) == null) return null
        val unresolved = mutableSetOf<ParsedField>()

        val amount = resolveAmount(obj, unresolved)
        val type = resolveType(obj.optString("type"), unresolved)
        val categoryId = resolveId(
            obj.firstOf("categoryId", "category_id").asText(),
            context.categories.associate { it.id to it.name }.reverse(),
            context.categories.map { it.id }.toSet(),
            unresolved,
            ParsedField.CATEGORY,
        )
        val accountId = resolveId(
            obj.firstOf("accountId", "account_id").asText(),
            context.accounts.associate { it.id to it.name }.reverse(),
            context.accounts.map { it.id }.toSet(),
            unresolved,
            ParsedField.ACCOUNT,
        )
        val date = resolveDate(obj.firstOf("occurredAt", "occurred_at", "date"), context.today, unresolved)
        val title = obj.optString("title").trim()
        if (title.isEmpty()) unresolved += ParsedField.TITLE

        return ParsedEntry(
            amount = amount ?: Money.ZERO,
            type = type,
            date = date,
            time = null,
            accountId = accountId,
            toAccountId = null,
            categoryId = categoryId,
            title = title,
            remark = obj.optString("remark").trim(),
            confidence = resolveConfidence(obj),
            amountHasUnit = true,
            unresolved = unresolved,
        )
    }

    /** `amountCents`：整数按「分」；带小数的 JSON 数字只可能是「元」（分是整数）→ 明确按 ×100 修复。 */
    private fun resolveAmount(obj: JSONObject, unresolved: MutableSet<ParsedField>): Money? {
        val node = obj.firstOf("amountCents", "amount_cents", "amount", "cents")
        val money = when (node) {
            is Number -> {
                val value = node.toDouble()
                val cents = if (value % 1.0 == 0.0) value.toLong() else (value * 100).roundToLong()
                if (cents > 0) Money.ofCents(cents) else null
            }
            is String -> {
                val text = node.trim()
                if (text.contains('.')) {
                    Money.parse(text)?.takeIf { it.isPositive }
                } else {
                    text.toLongOrNull()?.takeIf { it > 0 }?.let { Money.ofCents(it) }
                }
            }
            else -> null
        }
        if (money == null) unresolved += ParsedField.AMOUNT
        return money
    }

    private fun resolveType(raw: String, unresolved: MutableSet<ParsedField>): TransactionType =
        when (raw.trim().lowercase()) {
            "expense", "支出", "花费" -> TransactionType.EXPENSE
            "income", "收入" -> TransactionType.INCOME
            "transfer", "转账" -> TransactionType.TRANSFER
            "debt_repay", "debtrepay", "debt", "还款" -> TransactionType.DEBT_REPAY
            "adjust", "调整" -> TransactionType.ADJUST
            else -> {
                unresolved += ParsedField.TYPE
                TransactionType.EXPENSE
            }
        }

    /**
     * id 后校验（PRD Requirement 8）：只接受 [context] 里的真实 id（或与其一一对应且唯一的
     * 真实名称）；模型凭空编造的 id **不静默降级**，而是置空并标未识别，交给用户在草稿里选。
     */
    private fun resolveId(
        raw: String,
        nameToId: Map<String, String>,
        ids: Set<String>,
        unresolved: MutableSet<ParsedField>,
        field: ParsedField,
    ): String? {
        val value = raw.trim()
        when {
            value.isEmpty() || value.equals("null", true) || value.equals("none", true) -> unresolved += field
            value in ids -> return value
            value in nameToId -> return nameToId.getValue(value)
            ids.firstOrNull { it.equals(value, true) } != null -> return ids.first { it.equals(value, true) }
            else -> unresolved += field
        }
        return null
    }

    private fun Map<String, String>.reverse(): Map<String, String> =
        entries.fold(mutableMapOf()) { acc, (id, name) ->
            acc.putIfAbsent(name, id)
            acc
        }

    /**
     * 日期处理（PRD 硬约束 2）：模型给的 `occurredAt` **不可信**（基准实测 5/5 模型算错）。
     * 仅在单位与区间都可信时采纳，否则退回 [today] 并标 [ParsedField.DATE] 未识别，
     * 由管线用规则层日期覆盖（规则层也没有时才显示「未能识别」）。
     */
    private fun resolveDate(node: Any?, today: LocalDate, unresolved: MutableSet<ParsedField>): LocalDate {
        // 模型未给日期（缺失或 null）：与规则层「无日期线索默认今天」一致，不算静默降级。
        if (node == null) return today
        val parsed = parseTrustedDate(node, today)
        if (parsed == null) {
            unresolved += ParsedField.DATE
            return today
        }
        return parsed
    }

    private fun parseTrustedDate(node: Any?, today: LocalDate): LocalDate? {
        val date = when (node) {
            is Number -> node.toLong().toLocalDate()
            is String -> parseDateString(node.trim())
            else -> null
        } ?: return null
        val withinRange = abs(date.toEpochDays() - today.toEpochDays()) <= MAX_DAYS_FROM_TODAY
        return date.takeIf { withinRange }
    }

    private fun parseDateString(text: String): LocalDate? {
        if (text.isEmpty()) return null
        if (text.all { it.isDigit() }) return text.toLongOrNull()?.toLocalDate()
        runCatching { return Instant.parse(text).toLocalDateTime(TimeZone.currentSystemDefault()).date }
        runCatching { return LocalDate.parse(text) }
        runCatching { return LocalDateTime.parse(text).date }
        return null
    }

    /** epoch 秒（10 位）×1000 / epoch 毫秒（13 位）→ 本地日期；其余数量级判为不可信。 */
    private fun Long.toLocalDate(): LocalDate? = when (this) {
        in EPOCH_SECONDS_RANGE -> Instant.fromEpochMilliseconds(this * 1000)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        in EPOCH_MILLIS_RANGE -> Instant.fromEpochMilliseconds(this)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        else -> null
    }

    private fun resolveConfidence(obj: JSONObject): Confidence {
        val node = obj.firstOf("confidence")
        val value = when (node) {
            is Number -> node.toDouble()
            is String -> node.trim().lowercase().let {
                it.toDoubleOrNull() ?: when (it) {
                    "high" -> 1.0
                    "medium", "mid" -> 0.6
                    "low" -> 0.2
                    else -> null
                }
            }
            else -> null
        } ?: return Confidence.MEDIUM
        val clamped = (if (value > 1.0 && value <= 100.0) value / 100.0 else value).coerceIn(0.0, 1.0)
        return when {
            clamped >= 0.7 -> Confidence.HIGH
            clamped >= 0.4 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
    }

    // ---- 失败归类 ----

    /** 非重试类失败直接返回；`response_format` 相关 400/422 返回 null（交给唯一一次修复重试）。 */
    private fun immediateFailure(attempt: Attempt): AppError? = when (attempt) {
        Attempt.TransportError -> AppError.Network("云端解析超时或网络不可达")
        is Attempt.HttpError -> when {
            attempt.code == 401 || attempt.code == 403 -> AppError.Auth("AI 端点鉴权失败")
            attempt.code == 404 -> AppError.NotFound("AI 端点或模型不存在")
            attempt.code >= 500 -> AppError.Network("AI 端点错误（${attempt.code}）")
            attempt.code == 429 -> AppError.Network("AI 端点限流（429）")
            else -> null
        }
        is Attempt.Ok -> null
    }

    private fun httpError(code: Int): AppError = when {
        code == 401 || code == 403 -> AppError.Auth("AI 端点鉴权失败")
        code == 404 -> AppError.NotFound("AI 端点或模型不存在")
        code >= 500 -> AppError.Network("AI 端点错误（$code）")
        else -> AppError.Parse("AI 端点返回错误（$code）")
    }

    private fun failureReason(content: String?): String =
        if (content.isNullOrBlank()) "响应内容为空" else "响应不是合法 JSON 对象"

    private sealed interface Attempt {
        data class Ok(val content: String) : Attempt
        data class HttpError(val code: Int) : Attempt
        data object TransportError : Attempt
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private val EPOCH_SECONDS_RANGE = 1_000_000_000L..9_999_999_999L
        private val EPOCH_MILLIS_RANGE = 1_000_000_000_000L..9_999_999_999_999L
        private const val MAX_DAYS_FROM_TODAY = 366

        private val RECOGNIZED_KEYS = arrayOf(
            "amountCents", "amount_cents", "amount", "cents", "type",
            "categoryId", "category_id", "accountId", "account_id",
            "occurredAt", "occurred_at", "date", "title", "remark", "confidence",
        )

        /** `{baseUrl}/chat/completions`；baseUrl 允许带/不带尾部斜杠。非法 URL 返回 null。 */
        fun chatCompletionsUrl(baseUrl: String): String? {
            val trimmed = baseUrl.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            val url = "$trimmed/chat/completions"
            return url.takeIf { runCatching { it.toHttpUrl() }.isSuccess }
        }

        /**
         * 默认 HTTP 客户端：连接快超时、**读超时放宽到 120s**（7B 本地模型单次解析实测 ~65s，
         * 14B 更久），整体 callTimeout 兜底 180s。超时降级到规则层，不崩。
         */
        fun defaultAiHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build()
    }
}

/** 取首个存在且非 JSON null 的键值；都没有返回 null。 */
private fun JSONObject.firstOf(vararg keys: String): Any? {
    for (key in keys) {
        if (has(key) && !isNull(key)) return opt(key)
    }
    return null
}

/** 把任意 JSON 值转成文本：字符串原样，null/缺失为空串，其余 `toString()`。 */
private fun Any?.asText(): String = when (this) {
    null, JSONObject.NULL -> ""
    is String -> this
    else -> toString()
}
