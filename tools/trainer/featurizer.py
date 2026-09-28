"""字符 2-3 gram 哈希 TF 特征 —— `CharNgramFeaturizer` 的逐位复刻。

对应 Kotlin:
`ml/classifier/src/main/kotlin/com/han1997/vipledger/ml/classifier/feature/CharNgramFeaturizer.kt`

契约（改这里必须同步改 Kotlin 侧，反之亦然）：

1. 归一化：小写 -> 折叠连续 **ASCII** 空白（``[ \\t\\n\\r\\u000B\\f]``+）成单个空格 -> 去首尾空白。
   只处理 ASCII 空白，**不要**用 Python 的 Unicode 版 ``\\s``（两边字符类必须一致）。
2. 按 **Unicode 码点**（不是 UTF-16 code unit）取长度 2 与 3 的 n-gram。
   Python 的 ``str`` 天然是码点序列，``text[i:i+n]`` 与 Kotlin ``codePoints()`` 一一对应。
   因此 ``"🍜面"`` 只产出 1 个 2-gram。
3. 对每个 n-gram 的 **UTF-8 字节**做 FNV-1a 32 位：
   ``h = 2166136261; for b in bytes: h = ((h ^ b) * 16777619) & 0xFFFFFFFF``。
4. 桶 ``bucket = h & (BUCKETS - 1)``（BUCKETS 是 2 的幂，等价于 ``h % BUCKETS`` 且恒非负）。
5. TF：每个桶先记出现次数（整数），最后整体除以 n-gram 总数（L1）。索引升序。

纯标准库：只用 ``re``（PRD D2 要求零第三方依赖）。
"""

import re

# 哈希桶数（ARCHITECTURE §7 的 1<<18）。
BUCKETS = 1 << 18

# n-gram 长度，ARCHITECTURE §7 的「字符 2-3 gram」。
NGRAM_SIZES = (2, 3)

# 特征规格版本；归一化、哈希或 n-gram 口径发生**任何**不兼容变化时必须 +1。
FEATURE_VERSION = 1

# 词表版本标签（人读用），与 FEATURE_VERSION 绑定。
VOCAB_VERSION = "char2-3gram-fnv1a-v1"

_FNV_OFFSET_BASIS = 2166136261  # 0x811C9DC5
_FNV_PRIME = 16777619  # 0x01000193

# ASCII 空白，逐字符与 Kotlin 侧保持一致（不要用 \s）。
_ASCII_WHITESPACE = re.compile(r"[ \t\n\r\v\f]+")


def join_text(title, remark):
    """「标题 + 备注」的拼接口径。

    与 M6 写入训练样本的 ``sampleText`` 一致：
    ``listOf(title, remark).filter { it.isNotBlank() }.joinToString(" ")``。
    """
    parts = [part for part in (title, remark) if part.strip() != ""]
    return " ".join(parts)


def normalize(text):
    """小写 -> 折叠 ASCII 空白 -> 去首尾空白。"""
    return _ASCII_WHITESPACE.sub(" ", text.lower()).strip()


def _bucket_of(ngram):
    """单个 n-gram 落到哪个桶（FNV-1a 32 位，输入为其 UTF-8 字节）。"""
    h = _FNV_OFFSET_BASIS
    for byte in ngram.encode("utf-8"):
        h = ((h ^ byte) * _FNV_PRIME) & 0xFFFFFFFF
    return h & (BUCKETS - 1)


def features_of_text(text):
    """把任意文本转成稀疏 TF 向量。

    返回 ``(indices, values)``：``indices`` 升序且唯一，``values`` 一一对应；
    文本为空/只有空白/短于 2 个码点时返回 ``([], [])``。
    """
    normalized = normalize(text)
    if not normalized:
        return [], []

    code_points = list(normalized)  # Python str 即码点序列
    counts = {}
    total = 0
    for size in NGRAM_SIZES:
        if len(code_points) < size:
            continue
        for start in range(len(code_points) - size + 1):
            bucket = _bucket_of("".join(code_points[start:start + size]))
            counts[bucket] = counts.get(bucket, 0) + 1
            total += 1
    if total == 0:
        return [], []

    indices = sorted(counts)
    values = [counts[bucket] / total for bucket in indices]
    return indices, values


def features_of(title, remark):
    """把「标题 + 备注」转成稀疏 TF 向量（先按 [join_text] 拼接）。"""
    return features_of_text(join_text(title, remark))
