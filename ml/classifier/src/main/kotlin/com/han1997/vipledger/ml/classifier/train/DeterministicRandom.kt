package com.han1997.vipledger.ml.classifier.train

/**
 * 显式、可跨语言复现的伪随机数发生器（xorshift64*），只用于训练时**打乱样本顺序**。
 *
 * 为什么要自己写而不是 `java.util.Random`：`tools/trainer/` 的 Python 侧要复刻同一个洗牌序列才能同构，
 * Java 的 LCG 与 Python 的 Mersenne Twister 互不相通，而 xorshift64* 只有几行、两边都能照抄。
 *
 * 算法（两边一致，64 位无符号按 2^64 取模）：
 * ```
 * x ^= x >> 12; x ^= x << 25; x ^= x >> 27; return x * 0x9E3779B97F4A7C15
 * ```
 */
internal class DeterministicRandom(seed: Long) {

    private var state: Long = if (seed == 0L) GOLDEN else seed

    fun nextLong(): Long {
        var x = state
        x = x xor (x ushr 12)
        x = x xor (x shl 25)
        x = x xor (x ushr 27)
        state = x
        return x * GOLDEN
    }

    /** `[0, bound)` 内的均匀整数；只用非负半区，避免负余数。 */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        return ((nextLong() ushr 1) % bound).toInt()
    }

    /** Fisher–Yates，返回 `0 until n` 的一个确定性排列。 */
    fun permutation(n: Int): IntArray {
        val order = IntArray(n) { it }
        for (i in n - 1 downTo 1) {
            val j = nextInt(i + 1)
            val tmp = order[i]
            order[i] = order[j]
            order[j] = tmp
        }
        return order
    }

    private companion object {
        /** 0x9E3779B97F4A7C15 的有符号表示（借它给出非零初值）。 */
        const val GOLDEN: Long = -7046029254386353131L
    }
}
