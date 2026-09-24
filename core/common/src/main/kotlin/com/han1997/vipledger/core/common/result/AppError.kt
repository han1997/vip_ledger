package com.han1997.vipledger.core.common.result

/**
 * 全应用统一的错误模型。
 *
 * 数据层把底层异常（OkHttp/SQLite/解析）翻译成这里的类型，UI 层只把类型或 [message]
 * 映射成用户可读文案，不直接依赖底层异常（见 docs/ARCHITECTURE.md 第 4 节）。
 */
sealed class AppError(open val message: String? = null, open val cause: Throwable? = null) {

    /** 网络不可达、超时、DNS 失败等。 */
    data class Network(override val message: String? = null, override val cause: Throwable? = null) :
        AppError(message, cause)

    /** 鉴权失败：WebDAV 401、AI 端点 401/403。 */
    data class Auth(override val message: String? = null, override val cause: Throwable? = null) :
        AppError(message, cause)

    /** 目标不存在：远端目录、备份文件、账目 ID。 */
    data class NotFound(override val message: String? = null, override val cause: Throwable? = null) :
        AppError(message, cause)

    /** 唯一约束冲突或并发写入冲突。 */
    data class Conflict(override val message: String? = null, override val cause: Throwable? = null) :
        AppError(message, cause)

    /** 解析失败：AI 返回非法 JSON、导入文件格式不符。 */
    data class Parse(override val message: String? = null, override val cause: Throwable? = null) :
        AppError(message, cause)

    /** 本地存储失败：磁盘满、文件损坏、加密口令错误。 */
    data class Storage(override val message: String? = null, override val cause: Throwable? = null) :
        AppError(message, cause)

    /** 业务校验失败：金额为零、两级分类超深、账户不属于当前账本。 */
    data class Validation(override val message: String? = null, override val cause: Throwable? = null) :
        AppError(message, cause)

    /** 兜底类型；业务代码禁止用它替代明确分类。 */
    data class Unknown(override val message: String? = null, override val cause: Throwable? = null) :
        AppError(message, cause)
}
