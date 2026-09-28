"""多项式逻辑回归（softmax）+ L1-TF 特征的增量 SGD —— `LinearModelTrainer` 的同构对应物。

对应 Kotlin:
`ml/classifier/src/main/kotlin/com/han1997/vipledger/ml/classifier/train/{LinearModelTrainer,DeterministicRandom,Training}.kt`

复刻要点（顺序也一致，否则同构对比会漂移）：

- 特征：见 [featurizer]；
- 累计每个分类的样本数（继承 base 的 [label_counts] 与 [pending_counts]），
  累计数 < ``min_samples_per_class`` 的分类既不训练也不进标签表；
- 标签表升序；洗牌走 [DeterministicRandom]（xorshift64*），每 epoch 用 ``seed + epoch`` 重新播种；
- 前向 ``logits[c] += w[c][f] * v``（按特征索引升序累加）；
- 更新 ``step = lr * diff; w[c][f] -= step * v``（``diff = probs[c] - (c == target)``）；
- softmax 先减最大值、再 ``exp``、最后除以总和（与 Kotlin ``softmaxOf`` 同序）。

## 已知的浮点差

Kotlin 侧全程 **float32**，Python 标准库是 **float64**。两边都用朴素浮点运算，但 24 位与 53 位
尾数在若干轮 SGD 后会累积微小差异，因此**不追求逐位相同**，只做容差比较（见 README 与
``fixtures/fixture.json`` 的 ``tolerance``）。
"""

import math

from featurizer import BUCKETS, features_of_text

DEFAULT_EPOCHS = 20
DEFAULT_LEARNING_RATE = 0.5
DEFAULT_MIN_SAMPLES_PER_CLASS = 3
DEFAULT_SEED = 20260928

# xorshift64* 的常量与掩码（两边一致，64 位无符号按 2^64 取模）。
_MASK64 = 0xFFFFFFFFFFFFFFFF
_GOLDEN = 0x9E3779B97F4A7C15


class DeterministicRandom(object):
    """显式、可跨语言复现的伪随机数发生器（xorshift64*），只用于训练时打乱样本顺序。

    对应 Kotlin ``DeterministicRandom``：``x ^= x >> 12; x ^= x << 25; x ^= x >> 27; return x * GOLDEN``。
    """

    def __init__(self, seed):
        seed &= _MASK64
        self._state = _GOLDEN if seed == 0 else seed

    def next_long(self):
        x = self._state
        x ^= x >> 12
        x ^= (x << 25) & _MASK64
        x ^= x >> 27
        self._state = x
        return (x * _GOLDEN) & _MASK64

    def next_int(self, bound):
        """``[0, bound)`` 内的均匀整数；只用非负半区，避免负余数。"""
        if bound <= 0:
            raise ValueError("bound must be positive")
        return (self.next_long() >> 1) % bound

    def permutation(self, n):
        """Fisher-Yates，返回 ``range(n)`` 的一个确定性排列。"""
        order = list(range(n))
        for i in range(n - 1, 0, -1):
            j = self.next_int(i + 1)
            order[i], order[j] = order[j], order[i]
        return order


class TrainingConfig(object):
    """训练超参与可配阈值（对应 Kotlin ``TrainingConfig``，默认值一致）。"""

    def __init__(self, epochs=DEFAULT_EPOCHS, learning_rate=DEFAULT_LEARNING_RATE,
                 min_samples_per_class=DEFAULT_MIN_SAMPLES_PER_CLASS, seed=DEFAULT_SEED):
        if epochs < 0:
            raise ValueError("epochs must be >= 0")
        if learning_rate <= 0:
            raise ValueError("learning_rate must be > 0")
        if min_samples_per_class < 1:
            raise ValueError("min_samples_per_class must be >= 1")
        self.epochs = epochs
        self.learning_rate = learning_rate
        self.min_samples_per_class = min_samples_per_class
        self.seed = seed

    @classmethod
    def from_dict(cls, data):
        return cls(
            epochs=data.get("epochs", DEFAULT_EPOCHS),
            learning_rate=data.get("learningRate", DEFAULT_LEARNING_RATE),
            min_samples_per_class=data.get("minSamplesPerClass", DEFAULT_MIN_SAMPLES_PER_CLASS),
            seed=data.get("seed", DEFAULT_SEED),
        )


def softmax(logits):
    """数值稳定的 softmax，与 Kotlin ``softmaxOf`` 同序（先减最大值、再 exp、最后除以总和）。"""
    if not logits:
        return []
    max_logit = max(logits)
    out = [math.exp(x - max_logit) for x in logits]
    total = 0.0
    for value in out:
        total += value
    inv = 1.0 / total
    return [value * inv for value in out]


def train(samples, config, base=None):
    """训练（可选增量），返回 ``(model, trained_samples)``；无可记录内容时 ``model`` 为 ``None``。

    - ``samples``：``[{"text": str, "categoryId": str}, ...]``（顺序有意义，洗牌基于它）；
    - ``base``：上一轮 ``model`` dict（增量训练时继承已有标签的权重/偏置与累计计数）；
    - ``model``：``labels`` 升序，``weights`` 为每类一个稀疏 ``{bucket: value}``。
    """
    # 1. 累计每个分类的样本数（继承 base 的历史计数，含尚未进标签表的「待定」分类）。
    merged_counts = {}
    if base is not None:
        for i, label in enumerate(base["labels"]):
            merged_counts[label] = base["label_counts"][i]
        for label, count in base["pending_counts"].items():
            merged_counts[label] = merged_counts.get(label, 0) + count
    for sample in samples:
        label = sample["categoryId"]
        merged_counts[label] = merged_counts.get(label, 0) + 1

    if not merged_counts:
        return None, 0

    # 2. 按阈值分流；标签升序是确定性契约的一部分。
    eligible_labels = sorted(
        label for label, count in merged_counts.items()
        if count >= config.min_samples_per_class
    )
    pending_counts = {
        label: merged_counts[label] for label in sorted(merged_counts)
        if merged_counts[label] < config.min_samples_per_class
    }
    pending_set = set(pending_counts)
    trainable = [s for s in samples if s["categoryId"] not in pending_set]

    # 3. 继承 base 已有标签的权重/偏置，新标签从零开始。
    weights = [{} for _ in eligible_labels]
    bias = [0.0] * len(eligible_labels)
    if base is not None:
        base_index = {label: i for i, label in enumerate(base["labels"])}
        for c, label in enumerate(eligible_labels):
            if label in base_index:
                weights[c] = dict(base["weights"][base_index[label]])
                bias[c] = base["bias"][base_index[label]]
    label_counts = [merged_counts[label] for label in eligible_labels]
    label_index = {label: i for i, label in enumerate(eligible_labels)}

    # 4. 逐 epoch 打乱后做单样本 SGD。
    if trainable and eligible_labels and config.epochs > 0:
        lr = config.learning_rate
        class_count = len(eligible_labels)
        for epoch in range(config.epochs):
            order = DeterministicRandom(config.seed + epoch).permutation(len(trainable))
            for position in order:
                sample = trainable[position]
                indices, values = features_of_text(sample["text"])
                if not indices:
                    continue

                logits = list(bias)
                for i in range(len(indices)):
                    bucket = indices[i]
                    value = values[i]
                    for c in range(class_count):
                        logits[c] += weights[c].get(bucket, 0.0) * value
                probs = softmax(logits)
                target = label_index[sample["categoryId"]]
                for c in range(class_count):
                    diff = probs[c] - (1.0 if c == target else 0.0)
                    if diff == 0.0:
                        continue
                    step = lr * diff
                    bias[c] -= step
                    row = weights[c]
                    for i in range(len(indices)):
                        bucket = indices[i]
                        row[bucket] = row.get(bucket, 0.0) - step * values[i]

    model = {
        "labels": eligible_labels,
        "label_counts": label_counts,
        "pending_counts": pending_counts,
        "bias": bias,
        "weights": weights,
    }
    return model, len(trainable)


def model_version_for(trained_at):
    """模型版本号：人类可读、含特征规格版本与训练时间（与 Kotlin ``modelVersionFor`` 一致）。"""
    return "v1-%d" % trained_at


def sample_count_of(model):
    return sum(model["label_counts"])
