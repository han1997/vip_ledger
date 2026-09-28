"""权重文件的 JSON 编解码 —— `ModelCodec` 的同构对应物。

对应 Kotlin:
`ml/classifier/src/main/kotlin/com/han1997/vipledger/ml/classifier/model/ModelCodec.kt`

产出的 JSON 字段名、顺序与语义必须与 Kotlin 侧逐字段一致，否则 App 的
``ModelCodec.decode`` 会抛 ``ModelFormatException``（「当前词表不可加载」）：

```json
{
  "formatVersion": 1,
  "featureVersion": 1,
  "vocabVersion": "char2-3gram-fnv1a-v1",
  "buckets": 262144,
  "modelVersion": "v1-1695800000000",
  "trainedAt": 1695800000000,
  "sampleCount": 120,
  "labels": ["cat-food", "cat-transport"],
  "labelCounts": [40, 80],
  "pendingCounts": { "cat-travel": 2 },
  "bias": [0.1, -0.2],
  "weights": [
    { "indices": [12, 4567], "values": [0.51, -0.12] },
    { "indices": [12], "values": [-0.40] }
  ]
}
```

- ``labels`` 升序，``labelCounts`` / ``bias`` / ``weights`` 逐行对齐；
- ``weights`` 只存**非零桶**（稠密的 1<<18 桶 × 分类数会让文件膨胀到几十 MB），
  ``indices`` 升序、``values`` 一一对应，未列出的桶恒为 0；
- 浮点用 Python ``float``（float64）写出，Kotlin 侧按 ``Float``（float32）读回：
  两边存在**已知的浮点舍入差**，比较时按容差（见 README）。
"""

import json

from featurizer import BUCKETS, FEATURE_VERSION, VOCAB_VERSION

# 权重文件容器格式版本；字段增删或语义变化时 +1。
FORMAT_VERSION = 1


def encode(model, trained_at, model_version, sample_count):
    """把训练结果编成 ModelCodec 可加载的 JSON 字符串。

    ``model`` 形如::

        {
            "labels": ["cat-food", ...],          # 升序
            "label_counts": [7, 6, ...],          # 与 labels 对齐
            "pending_counts": {"cat-travel": 2},  # 键与 labels 不相交
            "bias": [0.0, ...],                   # 与 labels 对齐
            "weights": [ {bucket: value, ...}, ...],  # 每类一个稀疏 dict
        }
    """
    labels = model["labels"]
    weight_rows = []
    for row in model["weights"]:
        indices = sorted(bucket for bucket, value in row.items() if value != 0.0)
        weight_rows.append({
            "indices": indices,
            "values": [row[bucket] for bucket in indices],
        })

    document = {
        "formatVersion": FORMAT_VERSION,
        "featureVersion": FEATURE_VERSION,
        "vocabVersion": VOCAB_VERSION,
        "buckets": BUCKETS,
        "modelVersion": model_version,
        "trainedAt": trained_at,
        "sampleCount": sample_count,
        "labels": labels,
        "labelCounts": model["label_counts"],
        "pendingCounts": model["pending_counts"],
        "bias": model["bias"],
        "weights": weight_rows,
    }
    return json.dumps(document, ensure_ascii=False, separators=(",", ":"))


def decode(text):
    """解析权重 JSON 为与 [encode] 对称的 ``model`` dict（用于自检对比）。"""
    document = json.loads(text)
    labels = list(document["labels"])
    biases = list(document["bias"])
    rows = []
    for row in document["weights"]:
        if len(row["indices"]) != len(row["values"]):
            raise ValueError("weights row indices/values size mismatch")
        rows.append({int(i): float(v) for i, v in zip(row["indices"], row["values"])})
    return {
        "formatVersion": document["formatVersion"],
        "featureVersion": document["featureVersion"],
        "vocabVersion": document["vocabVersion"],
        "buckets": document["buckets"],
        "modelVersion": document["modelVersion"],
        "trainedAt": document["trainedAt"],
        "sampleCount": document["sampleCount"],
        "labels": labels,
        "label_counts": [int(c) for c in document["labelCounts"]],
        "pending_counts": {k: int(v) for k, v in document.get("pendingCounts", {}).items()},
        "bias": biases,
        "weights": rows,
    }
