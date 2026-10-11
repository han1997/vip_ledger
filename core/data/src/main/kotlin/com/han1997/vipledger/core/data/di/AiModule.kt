package com.han1997.vipledger.core.data.di

import com.han1997.vipledger.core.data.ai.AiSettingsRepositoryImpl
import com.han1997.vipledger.core.data.ai.OpenAiCompatibleLlmParserClient
import com.han1997.vipledger.core.data.ai.SystemSpeechTranscriber
import com.han1997.vipledger.core.domain.ai.AiSettingsRepository
import com.han1997.vipledger.core.domain.ai.SpeechTranscriber
import com.han1997.vipledger.core.domain.nlp.LlmParserClient
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * AI 配置与云端客户端的 Hilt 装配（PRD D4）。
 *
 * `:feature:ai` 只依赖 `:core:domain` 的 [AiSettingsRepository] 与 [LlmParserClient] 接缝，
 * 实现在 `:core:data`（此处绑定）。[LlmParserClient] 每次调用都重新读配置，因此界面改完
 * `baseUrl`/`model`/开关即时生效；开关关闭时实现自身也会直接返回失败、不发起网络请求。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindAiSettingsRepository(impl: AiSettingsRepositoryImpl): AiSettingsRepository

    @Binds
    @Singleton
    abstract fun bindSpeechTranscriber(impl: SystemSpeechTranscriber): SpeechTranscriber

    companion object {

        @Provides
        @Singleton
        fun provideLlmParserClient(settings: AiSettingsRepository): LlmParserClient =
            OpenAiCompatibleLlmParserClient(settings)
    }
}
