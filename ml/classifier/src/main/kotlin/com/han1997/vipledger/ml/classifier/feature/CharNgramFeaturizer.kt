package com.han1997.vipledger.ml.classifier.feature

import java.util.Locale

/**
 * 字符 2–3 gram 哈希 TF 特征（ARCHITECTURE §7）：
 * 「标题 + 备注」→ 归一化 → 取所有长度 2 与 3 的字符 n-gram → FNV-1a 32 位哈希到 `1 shl 18` 个桶 →
 * 按 n-gram 总数做 L1 归一化的 TF 向量（稀疏表示，见 [SparseFeatures]）。
 *
 * 纯 Kotlin、无模型下载、无第三方依赖；这正是 `tools/trainer/` 必须**逐位复刻**的那段逻辑，
 * 因此下面每一步都写成显式、可跨语言复现的形式，禁止用 `String.hashCode()` 这类依赖 JVM 实现的写法。
 *
 * ## 与 Python 训练脚本的同构契约（改这里必须同步改 `tools/trainer/`）
 *
 * 1. **拼接**：`[title, remark].filter { 非空白 }.join(" ")`，与 M6 落库训练样本的 `sampleText` 逐字一致。
 * 2. **归一化**：`lowercase()` → 把连续 ASCII 空白（`[ \t\n\r\u000B\f]`+）折叠成单个空格 → `strip()`。
 *    注意只处理 ASCII 空白，不要用 Python 的 Unicode 版 `\s`（两边字符类必须一致）。
 * 3. **码点切分**：按 **Unicode 码点**（不是 UTF-16 code unit）取 n-gram。Kotlin 侧先 `codePoints()`，
 *    Python 侧 `str` 天然就是码点序列，`s[i:i+n]` 直接对应。
 * 4. **哈希**：对每个 n-gram 的 **UTF-8 字节**做 FNV-1a 32 位：
 *    `h = 2166136261; for b in bytes: h = ((h ^ b) * 16777619) & 0xFFFFFFFF`。
 * 5. **桶**：`bucket = h & (BUCKETS - 1)`（[BUCKETS] 是 2 的幂，等价于 `h % BUCKETS` 且恒非负）。
 * 6. **TF**：每个桶先记**出现次数**（整数），最后整体除以 n-gram 总数（L1）。
 */
object CharNgramFeaturizer {

    /** 哈希桶数（ARCHITECTURE §7 的 `1<<18`）。 */
    const val BUCKETS: Int = 1 shl 18

    /** n-gram 长度，ARCHITECTURE §7 的「字符 2–3 gram」。 */
    val NGRAM_SIZES: IntArray = intArrayOf(2, 3)

    /**
     * 特征规格版本。归一化、哈希或 n-gram 口径发生**任何**不兼容变化时必须 +1，
     * 权重文件里的 `featureVersion` 与外层 `version` 不同即判定为「当前词表不可加载」。
     */
    const val FEATURE_VERSION: Int = 1

    /** 词表版本标签（人读用），与 [FEATURE_VERSION] 绑定。 */
    const val VOCAB_VERSION: String = "char2-3gram-fnv1a-v1"

    private const val FNV_OFFSET_BASIS: Int = -2128831035 // 0x811C9DC5 的有符号表示
    private const val FNV_PRIME: Int = 16777619 // 0x01000193

    /** ASCII 空白，逐字符与 Python 侧保持一致（不要用 `\s`）。 */
    private val ASCII_WHITESPACE = Regex("[ \\t\\n\\r\\u000B\\f]+")

    /**
     * 「标题 + 备注」的拼接口径，**必须与 M6 写入训练样本时的口径一致**
     * （`ConfirmAiEntryUseCase.sampleText`：`listOf(title, remark).filter { it.isNotBlank() }.joinToString(" ")`）。
     * 两边只有拼接方式相同，推理时的特征才能对上训练时落库的 `text`。
     */
    fun joinText(title: String, remark: String): String =
        listOf(title, remark).filter { it.isNotBlank() }.joinToString(" ")

    /** 把「标题 + 备注」转成稀疏 TF 向量。 */
    fun featuresOf(title: String, remark: String): SparseFeatures = featuresOfText(joinText(title, remark))

    /** 把任意文本转成稀疏 TF 向量（文本为空/只有空白/短于 2 个码点时返回空向量）。 */
    fun featuresOfText(text: String): SparseFeatures {
        val normalized = normalize(text)
        if (normalized.isEmpty()) return SparseFeatures.EMPTY

        val codePoints = normalized.codePoints().toArray()
        // 桶 -> 出现次数；用 TreeMap 之外的排序在最后一步统一做，保证升序。
        val counts = HashMap<Int, Int>()
        var total = 0
        for (n in NGRAM_SIZES) {
            if (codePoints.size < n) continue
            for (start in 0..(codePoints.size - n)) {
                val bucket = bucketOf(codePoints, start, n)
                counts[bucket] = (counts[bucket] ?: 0) + 1
                total++
            }
        }
        if (total == 0) return SparseFeatures.EMPTY

        val buckets = counts.keys.sorted()
        val indices = IntArray(buckets.size)
        val values = FloatArray(buckets.size)
        for (i in buckets.indices) {
            indices[i] = buckets[i]
            values[i] = counts.getValue(buckets[i]).toFloat() / total.toFloat()
        }
        return SparseFeatures(indices, values)
    }

    /** 归一化：小写 → 折叠 ASCII 空白 → 去首尾空白。 */
    internal fun normalize(text: String): String =
        text.lowercase(Locale.ROOT).replace(ASCII_WHITESPACE, " ").trim()

    /** 单个 n-gram（码点区间 `[start, start+len)`）落到哪个桶。 */
    private fun bucketOf(codePoints: IntArray, start: Int, len: Int): Int {
        val ngram = String(codePoints, start, len)
        var hash = FNV_OFFSET_BASIS
        for (byte in ngram.toByteArray(Charsets.UTF_8)) {
            hash = (hash xor (byte.toInt() and 0xFF)) * FNV_PRIME
        }
        return hash and (BUCKETS - 1)
    }
}
