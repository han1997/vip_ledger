#!/usr/bin/env python3
"""端侧线性分类器的 Python 训练脚本（纯标准库，PRD D2）。

读一个 JSON Lines 样本文件，训出与 Kotlin ``LinearModelTrainer`` 同构的权重，写出 App 可直接加载的
权重 JSON（``ModelCodec`` 格式）。用法见 README.md。

    python train.py --samples samples.jsonl --output model.json
    python train.py --selfcheck
    python selfcheck.py

样本文件格式（JSON Lines，一行一个对象；空行忽略）::

    {"text": "午饭 牛肉面", "categoryId": "cat-food"}
    {"text": "打车 回家", "categoryId": "cat-transport"}

- ``text``：**已拼接**的「标题 + 备注」，口径与 App 侧
  ``CharNgramFeaturizer.joinText(title, remark)`` / ``ml_training_sample.text`` 一致；
- ``categoryId``：该样本归属的真实分类 id，必须是**非空白**字符串（全空白等于没有分类）；
- ``text`` 必须是字符串；未知字段忽略；任一行畸形即报错并非零退出。
- 文件编码 UTF-8；**允许**开头有 UTF-8 BOM（Windows 工具导出常见），行尾 ``CRLF`` 与
  「末行无换行」都接受。
"""

import argparse
import os
import sys
import time

import model_io
import selfcheck as selfcheck_module
from samples_io import SampleFormatError, read_samples
from trainer import (
    DEFAULT_EPOCHS,
    DEFAULT_LEARNING_RATE,
    DEFAULT_MIN_SAMPLES_PER_CLASS,
    DEFAULT_SEED,
    TrainingConfig,
    model_version_for,
    sample_count_of,
    train,
)


def build_parser():
    parser = argparse.ArgumentParser(
        description="Train the on-device linear classifier and emit ModelCodec-compatible weights JSON.",
    )
    parser.add_argument("--samples", help="JSON Lines samples file (required unless --selfcheck)")
    parser.add_argument("--output", help="weights JSON output path (required unless --selfcheck)")
    parser.add_argument("--epochs", type=int, default=DEFAULT_EPOCHS,
                        help="training epochs (default: %d)" % DEFAULT_EPOCHS)
    parser.add_argument("--lr", type=float, default=DEFAULT_LEARNING_RATE,
                        help="learning rate (default: %s)" % DEFAULT_LEARNING_RATE)
    parser.add_argument("--min-samples", type=int, default=DEFAULT_MIN_SAMPLES_PER_CLASS,
                        help="min samples per class (default: %d)" % DEFAULT_MIN_SAMPLES_PER_CLASS)
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED,
                        help="shuffle seed (default: %d)" % DEFAULT_SEED)
    parser.add_argument("--trained-at", type=int, default=None,
                        help="trainedAt / model id in epoch millis (default: now)")
    parser.add_argument("--model-version", default=None,
                        help="modelVersion string (default: v1-<trained-at>)")
    parser.add_argument("--selfcheck", action="store_true",
                        help="train on the checked-in fixture and compare within tolerance")
    parser.add_argument("--fixtures", default=None,
                        help="fixtures dir for --selfcheck (default: tools/trainer/fixtures)")
    return parser


def main(argv=None):
    parser = build_parser()
    args = parser.parse_args(argv)

    if args.selfcheck:
        fixtures_dir = args.fixtures or selfcheck_module.FIXTURES_DIR
        return selfcheck_module.run(fixtures_dir)

    if not args.samples or not args.output:
        parser.error("--samples and --output are required (or use --selfcheck)")

    try:
        samples = read_samples(args.samples)
    except SampleFormatError as error:
        sys.stderr.write("sample input error: %s\n" % error)
        return 2
    except IOError as error:
        sys.stderr.write("cannot read samples: %s\n" % error)
        return 2

    try:
        config = TrainingConfig(
            epochs=args.epochs,
            learning_rate=args.lr,
            min_samples_per_class=args.min_samples,
            seed=args.seed,
        )
    except ValueError as error:
        sys.stderr.write("invalid hyperparameters: %s\n" % error)
        return 2

    trained_at = args.trained_at if args.trained_at is not None else int(time.time() * 1000)
    model_version = args.model_version or model_version_for(trained_at)

    model, trained_samples = train(samples, config)
    if model is None:
        sys.stderr.write("training produced no model (no samples)\n")
        return 2

    document = model_io.encode(model, trained_at, model_version, sample_count_of(model))
    output_dir = os.path.dirname(os.path.abspath(args.output))
    if output_dir and not os.path.isdir(output_dir):
        os.makedirs(output_dir)
    with open(args.output, "w", encoding="utf-8") as handle:
        handle.write(document)

    print("trained: epochs=%d lr=%s minSamples=%d seed=%d"
          % (config.epochs, config.learning_rate, config.min_samples_per_class, config.seed))
    print("  samples:        %d (trained %d)" % (len(samples), trained_samples))
    print("  labels:         %s" % model["labels"])
    print("  labelCounts:    %s" % model["label_counts"])
    print("  skippedClasses: %s" % model["pending_counts"])
    print("  sampleCount:    %d" % sample_count_of(model))
    print("  modelVersion:   %s" % model_version)
    print("  written to:     %s" % os.path.abspath(args.output))
    return 0


if __name__ == "__main__":
    sys.exit(main())
