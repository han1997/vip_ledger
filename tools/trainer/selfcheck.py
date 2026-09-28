"""同构自检：在钉死的 fixture 上训练，并与 checked-in 的期望模型按容差比对。

这是同构验收（PRD AC：「Python 脚本与 Kotlin 侧对同一份样本、同一组超参产出的权重在容差内一致」）
里**唯一真正的跨语言比对方向**：

- `fixtures/expected_model.json` 由 **Kotlin** 产出（设备侧才是权重格式的权威，重生成入口见
  `PythonIsomorphismFixtureTest.regenerateGoldenWhenRequested`）；
- 本脚本用 **Python** 重新训练，与那份 Kotlin golden 按容差比对。

因此本脚本**必须看到非零、但在容差内的差异**：Kotlin 全程 float32、Python 标准库是 float64，
逐位相同才反常（说明它退化成「拿 Python 输出比 Python 输出」的自证，或容差把比对吞掉了）。
容差比较量级与理由见 README。

用法：``python train.py --selfcheck``（或直接 ``python selfcheck.py``）。不一致时非零退出。
"""

import json
import os
import sys

import model_io
from featurizer import features_of_text
from samples_io import read_samples
from trainer import TrainingConfig, model_version_for, sample_count_of, train

FIXTURES_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fixtures")


def _read_json(path):
    with open(path, "r", encoding="utf-8-sig") as handle:
        return json.load(handle)


def logits_of(model, text):
    """用 ``model`` dict 对文本做前向，返回每类 logits（与推理路径一致）。"""
    indices, values = features_of_text(text)
    logits = list(model["bias"])
    for c in range(len(logits)):
        row = model["weights"][c]
        for i in range(len(indices)):
            logits[c] += row.get(indices[i], 0.0) * values[i]
    return logits


def predict(model, text):
    """返回置信度最高的标签（并列时取索引较小者，与 Kotlin 的 ``>` 比较一致）。"""
    logits = logits_of(model, text)
    if not logits:
        return None
    best = 0
    for c in range(1, len(logits)):
        if logits[c] > logits[best]:
            best = c
    return model["labels"][best]


def compare(produced, expected, tolerance):
    """比对两个已 [model_io.decode] 的模型，返回 (ok, measurements...)。"""
    problems = []

    if produced["labels"] != expected["labels"]:
        problems.append("labels differ: %r vs %r" % (produced["labels"], expected["labels"]))
    if produced["label_counts"] != expected["label_counts"]:
        problems.append("labelCounts differ: %r vs %r"
                        % (produced["label_counts"], expected["label_counts"]))
    if produced["pending_counts"] != expected["pending_counts"]:
        problems.append("pendingCounts differ: %r vs %r"
                        % (produced["pending_counts"], expected["pending_counts"]))

    # 逐类、逐桶比较偏置与权重（未列出的桶视为 0）。
    bias_max = 0.0
    if len(produced["bias"]) == len(expected["bias"]):
        for p, e in zip(produced["bias"], expected["bias"]):
            bias_max = max(bias_max, abs(p - e))
    else:
        problems.append("bias length differ: %d vs %d"
                        % (len(produced["bias"]), len(expected["bias"])))

    weight_max = 0.0
    weight_buckets = 0
    if len(produced["weights"]) == len(expected["weights"]):
        for c in range(len(produced["weights"])):
            p_row = produced["weights"][c]
            e_row = expected["weights"][c]
            for bucket in set(p_row) | set(e_row):
                diff = abs(p_row.get(bucket, 0.0) - e_row.get(bucket, 0.0))
                weight_max = max(weight_max, diff)
                weight_buckets += 1
    else:
        problems.append("weights row count differ: %d vs %d"
                        % (len(produced["weights"]), len(expected["weights"])))

    if bias_max > tolerance:
        problems.append("bias max abs diff %.3e > tolerance %.3e" % (bias_max, tolerance))
    if weight_max > tolerance:
        problems.append("weight max abs diff %.3e > tolerance %.3e" % (weight_max, tolerance))

    measurements = {
        "bias_max_abs_diff": bias_max,
        "weight_max_abs_diff": weight_max,
        "weight_buckets_compared": weight_buckets,
    }
    return problems, measurements


def run(fixtures_dir=FIXTURES_DIR, quiet=False):
    """跑自检；返回进程退出码（0 通过 / 1 失败）。"""
    fixture_path = os.path.join(fixtures_dir, "fixture.json")
    samples_path = os.path.join(fixtures_dir, "samples.jsonl")
    expected_path = os.path.join(fixtures_dir, "expected_model.json")
    for path in (fixture_path, samples_path, expected_path):
        if not os.path.exists(path):
            sys.stderr.write("selfcheck: missing fixture file: %s\n" % path)
            return 1

    fixture = _read_json(fixture_path)
    config = TrainingConfig.from_dict(fixture)
    trained_at = fixture["trainedAt"]
    tolerance = float(fixture["tolerance"])
    model_version = fixture.get("modelVersion") or model_version_for(trained_at)

    samples = read_samples(samples_path)
    model, trained_samples = train(samples, config)
    if model is None:
        sys.stderr.write("selfcheck: training produced no model\n")
        return 1
    produced_json = model_io.encode(
        model, trained_at, model_version, sample_count_of(model))
    produced = model_io.decode(produced_json)
    expected = model_io.decode(open(expected_path, "r", encoding="utf-8-sig").read())

    problems, measurements = compare(produced, expected, tolerance)

    # 自证护栏：fixture 两侧分别是 float32（Kotlin golden）与 float64（Python 标准库），
    # 逐位相同是「退化回自证」的信号（最可能是 expected_model.json 又被 Python 生成），
    # 必须当成失败——否则同构比对可以「自己跟自己比」地永远通过。
    if measurements["bias_max_abs_diff"] == 0.0 and measurements["weight_max_abs_diff"] == 0.0:
        problems.append(
            "Python output is bit-identical to the golden (0.0 diff); expected a small non-zero "
            "float32/float64 difference -- regenerate expected_model.json from Kotlin, not Python"
        )

    # 预测一致性：fixture 每条样本上两边 argmax 必须相同。
    prediction_mismatches = 0
    for sample in samples:
        if predict(produced, sample["text"]) != predict(expected, sample["text"]):
            prediction_mismatches += 1
    predictions_agree = prediction_mismatches == 0
    if not predictions_agree:
        problems.append("prediction mismatch on %d fixture samples" % prediction_mismatches)

    if not quiet:
        print("selfcheck (Python-trained vs checked-in Kotlin golden)")
        print("  fixtures:            %s" % fixtures_dir)
        print("  labels:              %s" % produced["labels"])
        print("  labelCounts:         %s" % produced["label_counts"])
        print("  pendingCounts:       %s" % produced["pending_counts"])
        print("  trainedSamples:      %d" % trained_samples)
        print("  buckets compared:    %d" % measurements["weight_buckets_compared"])
        print("  bias  max abs diff:  %.6e" % measurements["bias_max_abs_diff"])
        print("  weight max abs diff: %.6e" % measurements["weight_max_abs_diff"])
        print("  tolerance:           %.6e" % tolerance)
        print("  predictions agree:   %s" % predictions_agree)

    if problems:
        sys.stderr.write("selfcheck FAILED:\n")
        for problem in problems:
            sys.stderr.write("  - %s\n" % problem)
        return 1

    if not quiet:
        print("selfcheck OK")
    return 0


def main(argv):
    fixtures_dir = FIXTURES_DIR
    if argv:
        fixtures_dir = argv[0]
    return run(fixtures_dir)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
