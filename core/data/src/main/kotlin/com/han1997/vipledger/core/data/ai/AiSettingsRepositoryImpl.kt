package com.han1997.vipledger.core.data.ai

import android.content.Context
import com.han1997.vipledger.core.domain.ai.AiSettings
import com.han1997.vipledger.core.domain.ai.AiSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AiSettingsRepository] 的加密实现（PRD D4 / Requirement 5）。
 *
 * 读写都经 [EncryptedAiSettingsStore]，`apiKey` 不以明文落盘；任何路径都不打印配置内容或账单文本。
 */
@Singleton
class AiSettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : AiSettingsRepository {

    private val store = EncryptedAiSettingsStore(context)

    override fun settings(): AiSettings = store.read()

    override fun save(settings: AiSettings) = store.write(settings)
}
