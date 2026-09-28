package com.han1997.vipledger.core.domain.ai

/**
 * 云端 AI 解析配置（PRD Requirement 5）。
 *
 * [apiKey] 由实现加密存储，绝不以明文落盘或进日志（`.trellis/spec/android/logging-guidelines.md`）。
 * [baseUrl] 由用户填写（如本地 Ollama `http://192.168.10.4:11434/v1`）；**不内置**任何机器地址。
 */
data class AiSettings(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    /** 全局「允许联网解析」总开关，默认关（PRD D3）。关闭时云端那一级完全不调用、不发起网络请求。 */
    val networkEnabled: Boolean = false,
    /**
     * 端侧分类器的**自动训练**开关，默认关（PRD Requirement 3 / M7 验收）。
     * 关闭时修正写入样本后**不发生任何训练**；开启后由 `MlModelTrainer` 在「未训练样本 ≥ 阈值」时触发。
     * 与 [networkEnabled] 无关：训练完全本地，不联网。
     */
    val autoTrainEnabled: Boolean = false,
) {
    companion object {
        /** 基准实测性价比最高的默认模型（`research/ollama-model-benchmark.md`）。 */
        const val DEFAULT_MODEL: String = "gemma3:4b"

        /** `scheme://user:pass@host` 里的 userinfo 段（可能含凭据），打印前剥掉。 */
        private val USERINFO = Regex("^(\\w+://)[^/@]*@")
    }

    /**
     * 覆盖 data class 生成的 `toString`：字段被插值进日志/异常/crash report 时，
     * **绝不**带出 [apiKey] 明文，也不带出 [baseUrl] 里可能内嵌的 `user:pass@` 凭据
     * （`.trellis/spec/android/logging-guidelines.md` 红线：禁止打印 apiKey 与含认证串的完整 URL）。
     */
    override fun toString(): String =
        "AiSettings(baseUrl=${USERINFO.replace(baseUrl, "$1")}, " +
            "apiKey=${if (apiKey.isEmpty()) "" else "***"}, " +
            "model=$model, networkEnabled=$networkEnabled, autoTrainEnabled=$autoTrainEnabled)"
}

/**
 * AI 配置读写接缝：接口在 `:core:domain`，加密实现放 `:core:data`（PRD D4，与
 * `MerchantRuleRepository` / `MlSampleRepository` 同构）。
 *
 * `:feature:ai` 只依赖本接口即可读写配置，无需依赖 `:core:data`。
 */
interface AiSettingsRepository {

    fun settings(): AiSettings

    fun save(settings: AiSettings)
}
