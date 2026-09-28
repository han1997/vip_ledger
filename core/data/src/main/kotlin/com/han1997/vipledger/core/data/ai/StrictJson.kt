package com.han1997.vipledger.core.data.ai

import org.json.JSONObject

/**
 * 容错 JSON 抽取（研究 `llm-structured-output.md` §2.2）。
 *
 * 实测证据（PRD「由实测推出的三条硬约束」第 1 条）：云端与本地两类端点**都**会给结果包
 * ```json 围栏，思考型模型还可能先输出 ` thinking…`。直接 `JSONObject(text)` 会失败，
 * 因此按阶梯逐级降级：去围栏/去思维链 → 取首个配平 `{...}` → 容忍尾逗号。
 *
 * **不**改引号、**不**改标量类型——悄悄「修复」类型会掩盖模型错误，也可能改坏中文文本。
 */
internal object StrictJson {

    private val THINK_BLOCK = Regex("(?s)<think(?:ing)?>.*?</think(?:ing)?>")
    private val TRAILING_COMMA = Regex(",(\\s*[}\\]])")

    /** 抽取并解析出 JSON 对象；无法解析返回 null（调用方据此触发一次修复重试）。 */
    fun extractObject(raw: String): JSONObject? {
        val cleaned = stripFence(stripThink(raw)).trim()
        val candidate = firstBalancedObject(cleaned) ?: return null
        parse(candidate)?.let { return it }
        // 最末手段：仅去掉尾逗号（`{...},}` / `[...],]`）。
        return parse(TRAILING_COMMA.replace(candidate, "$1"))
    }

    private fun parse(text: String): JSONObject? = runCatching { JSONObject(text) }.getOrNull()

    /** 去掉 `…` 块（可能未闭合——此时其后的内容属于思维链，一并丢弃）。 */
    private fun stripThink(text: String): String {
        val stripped = THINK_BLOCK.replace(text, "")
        val open = stripped.indexOf("<think")
        return if (open >= 0) stripped.substring(0, open) else stripped
    }

    /**
     * 去掉 Markdown 代码围栏。**不假设围栏恰是 ```json**：语言标记可省略或任意，
     * 前后可能有空白/空行。非围栏文本原样返回（交给配平扫描处理句首/句尾的散文）。
     */
    private fun stripFence(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val withoutOpen = trimmed.removePrefix("```")
        val newline = withoutOpen.indexOf('\n')
        val body = if (newline >= 0) withoutOpen.substring(newline + 1) else withoutOpen
        val close = body.lastIndexOf("```")
        return (if (close >= 0) body.substring(0, close) else body).trim()
    }

    /**
     * 返回首个花括号配平的 `{...}` 子串；扫描时跳过字符串内的花括号与转义字符。
     * 未配平（截断）返回 null。
     */
    private fun firstBalancedObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }
}
