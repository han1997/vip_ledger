package com.han1997.vipledger.feature.ai.ui.entry

import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.core.domain.ai.AiSettings
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.nlp.LlmParserClient
import com.han1997.vipledger.core.domain.nlp.NlEntryPipeline
import com.han1997.vipledger.core.domain.nlp.ParseContext
import com.han1997.vipledger.core.domain.nlp.ParsedEntry
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.ml.classifier.PlaceholderCategoryClassifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/**
 * 隐私开关（PRD D3）的落地验证：开关关闭时云端那一级**完全不可达**——即使管线判据认为规则层
 * 产出不足，也不会调用云端客户端、不发起任何网络请求。
 */
class AiCloudGateTest {

    private val today = LocalDate(2026, 9, 28)

    private val account = Account(
        id = "a_wx",
        ledgerId = "l1",
        name = "微信",
        type = AccountType.VIRTUAL,
        createdAt = 0,
        updatedAt = 0,
    )

    private class RecordingLlmClient : LlmParserClient {
        var calls: Int = 0
        override suspend fun parse(text: String, context: ParseContext): AppResult<List<ParsedEntry>> {
            calls++
            return AppResult.success(emptyList())
        }
    }

    /** 句子没有账户线索 → ACCOUNT 未识别 → 规则层产出不足，开关打开时会触发云端。 */
    private fun pipelineWith(settings: AiSettings, client: LlmParserClient): NlEntryPipeline =
        NlEntryPipeline(
            accounts = listOf(account),
            categories = emptyList(),
            classifier = PlaceholderCategoryClassifier(),
            cloud = cloudClientFor(settings, client),
        )

    @Test
    fun switchOffNeverInvokesCloudClient() = runTest {
        val client = RecordingLlmClient()
        val pipeline = pipelineWith(AiSettings(networkEnabled = false), client)

        pipeline.parse("昨天午饭 35 块", today)

        assertEquals(0, client.calls, "开关关闭时不得调用云端客户端")
    }

    @Test
    fun switchOnInvokesCloudClientWhenRuleLayerIsInsufficient() = runTest {
        val client = RecordingLlmClient()
        val pipeline = pipelineWith(AiSettings(networkEnabled = true), client)

        pipeline.parse("昨天午饭 35 块", today)

        assertEquals(1, client.calls, "开关打开且规则层产出不足时应调用一次云端")
    }
}
