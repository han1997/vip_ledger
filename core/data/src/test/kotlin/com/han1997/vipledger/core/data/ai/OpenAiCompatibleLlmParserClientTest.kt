package com.han1997.vipledger.core.data.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.han1997.vipledger.core.common.result.AppError
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.ai.AiSettings
import com.han1997.vipledger.core.domain.ai.AiSettingsRepository
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.nlp.Confidence
import com.han1997.vipledger.core.domain.nlp.ParseContext
import com.han1997.vipledger.core.domain.nlp.ParsedEntry
import com.han1997.vipledger.core.domain.nlp.ParsedField
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.runner.RunWith

/**
 * 云端客户端契约（PRD PR3a / Requirement 3、8；研究 `llm-structured-output.md`）：
 *
 * - 降级阶梯：`json_object` → 一次提示式修复重试，整个云端级 **≤ 2 次**网络调用；
 * - 容错抽取：围栏 / 散文 / 思维链 / 尾逗号 / 截断，都能被吸收或安全降级；
 * - 字段后校验：幻觉 id、错误单位/区间的日期 → 明示未识别，不静默降级；
 * - 失败一律 [AppResult.Failure]，绝不抛异常。
 *
 * 走 Robolectric：`org.json` 在纯 JVM 单测里只是 stub（同 `WebDavClientTest`）。
 */
@RunWith(AndroidJUnit4::class)
class OpenAiCompatibleLlmParserClientTest {

    private lateinit var server: MockWebServer

    private val accounts = listOf(
        Account(id = "a_wx", ledgerId = "l1", name = "微信", type = AccountType.VIRTUAL, createdAt = 0, updatedAt = 0),
    )
    private val categories = listOf(
        Category(id = "c_food", ledgerId = "l1", name = "餐饮", kind = CategoryKind.EXPENSE, createdAt = 0, updatedAt = 0),
    )
    private val context = ParseContext(today = REF, accounts = accounts, categories = categories)

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    // ---- 正常路径与容错抽取 ----

    @Test
    fun validJsonObjectResponseMapsFields() = runTest {
        enqueueChat(entriesJson(entryJson()))

        val entries = successEntries(client())

        val entry = entries.single()
        assertEquals(Money.ofCents(3500), entry.amount)
        assertEquals(TransactionType.EXPENSE, entry.type)
        assertEquals("c_food", entry.categoryId)
        assertEquals("a_wx", entry.accountId)
        assertEquals("午饭", entry.title)
        assertEquals(Confidence.HIGH, entry.confidence, "confidence 0.9 → HIGH")
        assertEquals(REF, entry.date, "occurredAt 缺失时与规则层默认一致用今天，不标未识别")
        assertTrue(entry.unresolved.isEmpty())
    }

    @Test
    fun multipleEntriesParsed() = runTest {
        enqueueChat(entriesJson(entryJson(amountCents = 3500, title = "午饭"), entryJson(amountCents = 2800, title = "打车")))

        val entries = successEntries(client())

        assertEquals(2, entries.size, "一句话多笔（Requirement 6）")
        assertEquals(listOf(Money.ofCents(3500), Money.ofCents(2800)), entries.map { it.amount })
    }

    @Test
    fun fenceWrappedResponseIsParsed() = runTest {
        // 实测：云端与本地端都会包 ```json 围栏，且前面可能有空行。
        enqueueChat("\n\n```json\n${entriesJson(entryJson())}\n```")

        assertEquals(1, successEntries(client()).size)
    }

    @Test
    fun fenceWithoutLanguageTagIsParsed() = runTest {
        enqueueChat("```\n${entriesJson(entryJson())}\n```")
        assertEquals(1, successEntries(client()).size)
    }

    @Test
    fun proseWrappedResponseIsParsed() = runTest {
        enqueueChat("Sure! Here is the JSON: ${entriesJson(entryJson())} Hope this helps!")

        assertEquals(1, successEntries(client()).size)
    }

    @Test
    fun trailingCommaIsTolerated() = runTest {
        enqueueChat("""{"entries":[{"amountCents":3500,"type":"expense","categoryId":"c_food","accountId":"a_wx","title":"午饭",},],}""")

        assertEquals(Money.ofCents(3500), successEntries(client()).single().amount)
    }

    @Test
    fun thinkPrefixedResponseIsParsed() = runTest {
        enqueueChat("\u003Cthink\u003Ereasoning about { an unbalanced brace\u003C/think\u003E\n" + entriesJson(entryJson()))

        assertEquals(1, successEntries(client()).size)
    }

    @Test
    fun wrongScaleDecimalAmountIsRepairedAsYuan() = runTest {
        // 分是整数；带小数的数字只可能是「元」→ 明确 ×100。
        enqueueChat(entriesJson(entryJson(amountCents = 12.5)))

        assertEquals(Money.ofCents(1250), successEntries(client()).single().amount)
    }

    // ---- 字段后校验（Requirement 8 / 硬约束 2）----

    @Test
    fun hallucinatedCategoryIdBecomesUnresolved() = runTest {
        enqueueChat(entriesJson(entryJson(categoryId = "c_hallucinated")))

        val entry = successEntries(client()).single()
        assertNull(entry.categoryId, "不认识的 id 不得被接受")
        assertTrue(entry.unresolved.contains(ParsedField.CATEGORY), "必须明示未识别，不静默降级成未分类")
    }

    @Test
    fun hallucinatedAccountIdBecomesUnresolved() = runTest {
        enqueueChat(entriesJson(entryJson(accountId = "a_nope")))
        assertTrue(successEntries(client()).single().unresolved.contains(ParsedField.ACCOUNT))
    }

    @Test
    fun categoryNameIsAcceptedAsIdRepair() = runTest {
        enqueueChat(entriesJson(entryJson(categoryId = "餐饮")))
        assertEquals("c_food", successEntries(client()).single().categoryId)
    }

    @Test
    fun untrustedEpochSecondsDateIsMarkedUnresolved() = runTest {
        // 实测：deepseek-r1 返回 10 位 epoch 秒且日期错误（2021）。数量级对但区间远超今天 ±1 年 → 不可信。
        enqueueChat(entriesJson(entryJson(occurredAt = 1_623_456_000L)))

        val entry = successEntries(client()).single()
        assertTrue(entry.unresolved.contains(ParsedField.DATE), "不可信日期必须标未识别")
        assertEquals(REF, entry.date)
    }

    @Test
    fun trustedIsoDateStringIsUsed() = runTest {
        enqueueChat(entriesJson(entryJson(occurredAt = "2026-09-20")))

        val entry = successEntries(client()).single()
        assertEquals(LocalDate(2026, 9, 20), entry.date)
        assertTrue(!entry.unresolved.contains(ParsedField.DATE))
    }

    @Test
    fun unknownTypeBecomesUnresolvedExpense() = runTest {
        enqueueChat(entriesJson(entryJson(type = "投资")))

        val entry = successEntries(client()).single()
        assertEquals(TransactionType.EXPENSE, entry.type)
        assertTrue(entry.unresolved.contains(ParsedField.TYPE))
    }

    @Test
    fun confidenceOverOneIsNormalisedPercent() = runTest {
        enqueueChat(entriesJson(entryJson(confidence = 95)))
        assertEquals(Confidence.HIGH, successEntries(client()).single().confidence)
    }

    // ---- 降级阶梯：≤ 2 次调用，绝不抛异常 ----

    @Test
    fun truncatedResponseTriggersOneRepairRetryThatSucceeds() = runTest {
        // 第一次截断（finish_reason=length，JSON 不配平）→ 一次修复重试成功。
        enqueueChat("{\"entries\":[{\"amountCents\":3500,\"type\":\"expense", finishReason = "length")
        enqueueChat(entriesJson(entryJson()))

        val entries = successEntries(client())

        assertEquals(1, entries.size)
        assertEquals(2, server.requestCount, "整个云端级 ≤ 2 次网络调用")
        assertEquals("/v1/chat/completions", server.takeRequest().path)
        val repair = server.takeRequest()
        assertTrue(repair.body.readUtf8().contains("无法解析"), "修复重试应附上具体错误")
    }

    @Test
    fun twoFailedAttemptsReturnFailureWithinBudget() = runTest {
        enqueueChat("garbage not json")
        enqueueChat("still garbage")

        val failure = failure(client().parse("午饭35", context))

        assertTrue(failure.error is AppError.Parse)
        assertEquals(2, server.requestCount, "预算耗尽即失败，不无限重试")
    }

    @Test
    fun responseFormatRejected400RetriesWithoutIt() = runTest {
        // 未知 response_format 被 400 拒绝时降级，而不是直接失败（PRD AC）。
        server.enqueue(MockResponse().setResponseCode(400))
        enqueueChat(entriesJson(entryJson()))

        val entries = successEntries(client())

        assertEquals(1, entries.size)
        assertEquals(2, server.requestCount)
        assertTrue(server.takeRequest().body.readUtf8().contains("response_format"), "首次应携带 json_object")
        assertTrue(!server.takeRequest().body.readUtf8().contains("response_format"), "修复重试应去掉 response_format")
    }

    @Test
    fun authFailureDoesNotRetry() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        val failure = failure(client().parse("午饭35", context))

        assertTrue(failure.error is AppError.Auth)
        assertEquals(1, server.requestCount, "鉴权失败重试无意义，不消耗第二次调用")
    }

    @Test
    fun forbiddenDoesNotRetry() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        assertTrue(failure(client().parse("午饭35", context)).error is AppError.Auth)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun notFoundDoesNotRetry() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        assertTrue(failure(client().parse("午饭35", context)).error is AppError.NotFound)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun serverErrorDoesNotRetry() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(failure(client().parse("午饭35", context)).error is AppError.Network)
        assertEquals(1, server.requestCount, "5xx 不重试，不消耗第二次调用")
    }

    @Test
    fun rateLimitDoesNotRetry() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        assertTrue(failure(client().parse("午饭35", context)).error is AppError.Network)
        assertEquals(1, server.requestCount, "限流不重试，不消耗第二次调用")
    }

    @Test
    fun readTimeoutDegradesToNetworkFailure() = runTest {
        val slow = OkHttpClient.Builder().readTimeout(500, TimeUnit.MILLISECONDS).build()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(chatResponse(entriesJson(entryJson()), "stop"))
                .setBodyDelay(3, TimeUnit.SECONDS),
        )

        val failure = failure(client(slow).parse("午饭35", context))

        assertTrue(failure.error is AppError.Network, "超时必须降级到 Network，不崩")
        assertEquals(1, server.requestCount, "网络/超时属于非重试类失败，不消耗第二次调用")
    }

    @Test
    fun networkSwitchOffNeverHitsTheWire() = runTest {
        val failure = failure(
            client(config = AiSettings(baseUrl = server.url("/v1").toString(), networkEnabled = false))
                .parse("午饭35", context),
        )

        assertTrue(failure.error is AppError.Validation)
        assertEquals(0, server.requestCount, "开关关闭时完全不发起请求")
    }

    @Test
    fun blankBaseUrlFailsBeforeNetwork() = runTest {
        val failure = failure(client(config = AiSettings(baseUrl = "", networkEnabled = true)).parse("午饭35", context))

        assertTrue(failure.error is AppError.Validation)
        assertEquals(0, server.requestCount)
    }

    // ---- 隐私 / 认证 ----

    @Test
    fun bearerTokenSentOnlyWhenApiKeyConfigured() = runTest {
        enqueueChat(entriesJson(entryJson()))
        successEntries(client(config = settings(apiKey = "sk-secret")))
        assertEquals("Bearer sk-secret", server.takeRequest().getHeader("Authorization"))

        enqueueChat(entriesJson(entryJson()))
        successEntries(client(config = settings(apiKey = "")))
        assertNull(server.takeRequest().getHeader("Authorization"), "无 key 时不带 Authorization（本地服务器）")
    }

    @Test
    fun browserUserAgentSentOnEveryRequest() = runTest {
        // 实测 api.kunyou.asia：无 UA（含 OkHttp 默认 okhttp/4.12.0）被 Cloudflare 403，
        // 浏览器风格 UA 才 200 → 客户端必须显式带上，且首次请求与修复重试都要带。
        server.enqueue(MockResponse().setResponseCode(400))
        enqueueChat(entriesJson(entryJson()))

        val entries = successEntries(client())

        assertEquals(1, entries.size)
        assertEquals(2, server.requestCount, "首次 + 修复重试各一次")
        val firstUa = server.takeRequest().getHeader("User-Agent")
        assertTrue(firstUa != null && firstUa.contains("Mozilla"), "首次请求必须带浏览器风格 User-Agent，实际 $firstUa")
        val retryUa = server.takeRequest().getHeader("User-Agent")
        assertTrue(retryUa != null && retryUa.contains("Mozilla"), "修复重试也必须带 User-Agent，实际 $retryUa")
    }

    // ---- helpers ----

    private class FakeSettings(var value: AiSettings) : AiSettingsRepository {
        override fun settings(): AiSettings = value

        override fun save(settings: AiSettings) {
            value = settings
        }
    }

    private fun settings(apiKey: String = "") =
        AiSettings(baseUrl = server.url("/v1").toString(), apiKey = apiKey, networkEnabled = true)

    private fun client(
        okHttp: OkHttpClient = OkHttpClient(),
        config: AiSettings = settings(),
    ): OpenAiCompatibleLlmParserClient = OpenAiCompatibleLlmParserClient(FakeSettings(config), okHttp)

    private suspend fun successEntries(client: OpenAiCompatibleLlmParserClient): List<ParsedEntry> {
        val result = client.parse("午饭35", context)
        assertTrue(result is AppResult.Success, "期望成功，实际 $result")
        return (result as AppResult.Success).value
    }

    private fun failure(result: AppResult<List<ParsedEntry>>): AppResult.Failure {
        assertTrue(result is AppResult.Failure, "期望失败，实际 $result")
        return result as AppResult.Failure
    }

    private fun enqueueChat(content: String, finishReason: String = "stop") {
        server.enqueue(MockResponse().setResponseCode(200).setBody(chatResponse(content, finishReason)))
    }

    private fun chatResponse(content: String, finishReason: String): String = JSONObject()
        .put(
            "choices",
            JSONArray().put(
                JSONObject()
                    .put("message", JSONObject().put("role", "assistant").put("content", content))
                    .put("finish_reason", finishReason),
            ),
        )
        .toString()

    private fun entryJson(
        amountCents: Any? = 3500,
        type: String? = "expense",
        categoryId: Any? = "c_food",
        accountId: Any? = "a_wx",
        occurredAt: Any? = null,
        title: String = "午饭",
        confidence: Any? = 0.9,
    ): JSONObject = JSONObject().apply {
        if (amountCents != null) put("amountCents", amountCents)
        if (type != null) put("type", type)
        put("categoryId", categoryId ?: JSONObject.NULL)
        put("accountId", accountId ?: JSONObject.NULL)
        put("occurredAt", occurredAt ?: JSONObject.NULL)
        put("title", title)
        if (confidence != null) put("confidence", confidence)
    }

    private fun entriesJson(vararg entries: JSONObject): String =
        JSONObject().put("entries", JSONArray().apply { entries.forEach { put(it) } }).toString()

    private companion object {
        val REF: LocalDate = LocalDate(2026, 9, 28)
    }
}
