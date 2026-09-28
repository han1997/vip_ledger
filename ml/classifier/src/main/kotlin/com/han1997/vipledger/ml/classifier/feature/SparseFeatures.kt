package com.han1997.vipledger.ml.classifier.feature

/**
 * 稀疏 TF 特征向量（ARCHITECTURE §7 的「TF 向量」）。
 *
 * - [indices] **升序且唯一**，[values] 与之一一对应（第 i 个非零桶的权重）；
 * - 升序不只是好看：浮点求和顺序会影响结果，`tools/trainer/` 的 Python 侧必须按**同样的升序**
 *   累加点积与更新权重，否则同构验证会在小数点后对不上；
 * - 未出现在 [indices] 里的桶权重一律为 0。
 */
class SparseFeatures(
    val indices: IntArray,
    val values: FloatArray,
) {

    val size: Int get() = indices.size

    /** 展开成长度为 [dimension] 的稠密向量；仅用于确有必要时（推理与训练都走稀疏路径）。 */
    fun toDense(dimension: Int): FloatArray {
        val out = FloatArray(dimension)
        for (i in indices.indices) out[indices[i]] = values[i]
        return out
    }

    fun asMap(): Map<Int, Float> {
        val map = LinkedHashMap<Int, Float>(indices.size)
        for (i in indices.indices) map[indices[i]] = values[i]
        return map
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SparseFeatures) return false
        return indices.contentEquals(other.indices) && values.contentEquals(other.values)
    }

    override fun hashCode(): Int = 31 * indices.contentHashCode() + values.contentHashCode()

    override fun toString(): String = "SparseFeatures(${asMap()})"

    companion object {
        val EMPTY = SparseFeatures(IntArray(0), FloatArray(0))
    }
}
