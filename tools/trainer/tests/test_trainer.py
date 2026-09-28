"""tools/trainer 的纯标准库单元测试（unittest）。

用 ``python -m unittest discover -s tests -v``（在 tools/trainer 目录下）跑。
特征口径的锚点与 Kotlin ``CharNgramFeaturizerTest`` 钉死的是同一组桶值：任何一侧改了哈希/归一化口径，
两边的测试都会变红。
"""

import json
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import featurizer  # noqa: E402
import model_io  # noqa: E402
import samples_io  # noqa: E402
import trainer  # noqa: E402


class FeaturizerTest(unittest.TestCase):

    def test_hashes_known_ngrams_to_stable_buckets(self):
        # 与 Kotlin CharNgramFeaturizerTest.hashesKnownNgramsToStableBuckets 同值。
        self.assertEqual([204402], featurizer.features_of_text("午饭")[0])
        self.assertEqual([1.0], featurizer.features_of_text("午饭")[1])

        indices, values = featurizer.features_of_text("吃午饭")
        self.assertEqual([204402, 228793, 253254], indices)
        for value in values:
            self.assertAlmostEqual(1.0 / 3.0, value, places=9)

    def test_returns_empty_for_blank_or_too_short_text(self):
        self.assertEqual(([], []), featurizer.features_of_text(""))
        self.assertEqual(([], []), featurizer.features_of_text("   \t\n "))
        self.assertEqual(([], []), featurizer.features_of_text("a"))

    def test_splits_by_code_point_not_utf16_unit(self):
        # 单个 emoji 是 1 个码点：「🍜面」应只产出一个 2-gram。
        self.assertEqual(1, len(featurizer.features_of_text("🍜面")[0]))

    def test_normalization_lowercases_and_collapses_whitespace(self):
        self.assertEqual(
            featurizer.features_of_text("starbucks coffee"),
            featurizer.features_of_text("  STARBUCKS   Coffee  "),
        )

    def test_tf_values_sum_to_one(self):
        _, values = featurizer.features_of_text("午饭 打 车 12")
        self.assertTrue(len(values) > 0)
        self.assertAlmostEqual(1.0, sum(values), places=9)

    def test_join_text_matches_training_sample_convention(self):
        self.assertEqual("午饭", featurizer.join_text("午饭", ""))
        self.assertEqual("备注", featurizer.join_text("", "备注"))
        self.assertEqual("午饭 好吃", featurizer.join_text("午饭", "好吃"))
        self.assertEqual("", featurizer.join_text("", ""))


class DeterministicRandomTest(unittest.TestCase):

    def test_same_seed_produces_same_permutation(self):
        self.assertEqual(
            trainer.DeterministicRandom(42).permutation(50),
            trainer.DeterministicRandom(42).permutation(50),
        )

    def test_permutation_is_complete(self):
        self.assertEqual(list(range(100)), sorted(trainer.DeterministicRandom(7).permutation(100)))

    def test_zero_seed_uses_nonzero_start(self):
        # seed=0 时构造器改用 GOLDEN（0x9E3779B97F4A7C15），避免全零状态。
        self.assertEqual(
            trainer.DeterministicRandom(0x9E3779B97F4A7C15).next_long(),
            trainer.DeterministicRandom(0).next_long(),
        )


class TrainerTest(unittest.TestCase):

    def _dataset(self):
        food = ["午饭 面", "晚饭 米饭", "早餐 包子", "午饭 盖饭", "夜宵 烧烤", "午饭 快餐"]
        transport = ["打车 回家", "地铁 上班", "公交 两元", "打车 机场", "高铁 车票", "地铁 通勤"]
        return ([{"text": t, "categoryId": "cat-food"} for t in food]
                + [{"text": t, "categoryId": "cat-transport"} for t in transport])

    def test_is_deterministic_for_same_seed_and_samples(self):
        config = trainer.TrainingConfig(epochs=30, learning_rate=0.5, seed=12345)
        first, _ = trainer.train(self._dataset(), config)
        second, _ = trainer.train(self._dataset(), config)
        self.assertEqual(first["labels"], second["labels"])
        self.assertEqual(first["bias"], second["bias"])
        self.assertEqual(first["weights"], second["weights"])

    def test_skips_classes_below_sample_threshold(self):
        samples = ([{"text": "午饭 %d" % i, "categoryId": "cat-food"} for i in range(5)]
                   + [{"text": "机票 %d" % i, "categoryId": "cat-travel"} for i in range(2)])
        model, _ = trainer.train(samples, trainer.TrainingConfig(min_samples_per_class=3))
        self.assertEqual(["cat-food"], model["labels"])
        self.assertEqual({"cat-travel": 2}, model["pending_counts"])

    def test_returns_none_when_there_is_nothing_to_record(self):
        model, trained = trainer.train([], trainer.TrainingConfig())
        self.assertIsNone(model)
        self.assertEqual(0, trained)

    def test_learns_trivially_separable_toy_set(self):
        model, _ = trainer.train(self._dataset(), trainer.TrainingConfig(epochs=200))
        for sample in self._dataset():
            labels = model["labels"]
            logits = []
            indices, values = featurizer.features_of_text(sample["text"])
            for c in range(len(labels)):
                row = model["weights"][c]
                total = model["bias"][c]
                for i in range(len(indices)):
                    total += row.get(indices[i], 0.0) * values[i]
                logits.append(total)
            best = max(range(len(logits)), key=lambda c: logits[c])
            self.assertEqual(sample["categoryId"], labels[best])


class ModelIoTest(unittest.TestCase):

    def _model(self):
        return {
            "labels": ["cat-a", "cat-b"],
            "label_counts": [10, 20],
            "pending_counts": {"cat-c": 2},
            "bias": [0.125, -0.5],
            "weights": [{3: 0.25, 123456: -0.75}, {7: 0.5}],
        }

    def test_encode_decode_round_trips(self):
        text = model_io.encode(self._model(), trained_at=1700000000000, model_version="v1-x",
                               sample_count=30)
        decoded = model_io.decode(text)
        self.assertEqual(["cat-a", "cat-b"], decoded["labels"])
        self.assertEqual([10, 20], decoded["label_counts"])
        self.assertEqual({"cat-c": 2}, decoded["pending_counts"])
        self.assertEqual({3: 0.25, 123456: -0.75}, decoded["weights"][0])
        self.assertEqual({7: 0.5}, decoded["weights"][1])

    def test_emits_exact_modelcodec_field_names_and_sparse_rows(self):
        text = model_io.encode(self._model(), trained_at=1, model_version="v1-1", sample_count=30)
        document = json.loads(text)
        for key in ("formatVersion", "featureVersion", "vocabVersion", "buckets",
                    "modelVersion", "trainedAt", "sampleCount", "labels", "labelCounts",
                    "pendingCounts", "bias", "weights"):
            self.assertIn(key, document)
        self.assertEqual(1, document["formatVersion"])
        self.assertEqual(1, document["featureVersion"])
        self.assertEqual("char2-3gram-fnv1a-v1", document["vocabVersion"])
        self.assertEqual(262144, document["buckets"])
        # 只序列化非零桶，indices 升序。
        self.assertEqual({"indices": [3, 123456], "values": [0.25, -0.75]}, document["weights"][0])
        # cat-b 只有桶 7。
        self.assertEqual([7], document["weights"][1]["indices"])


class SampleInputContractTest(unittest.TestCase):
    """PR3 的样本导出必须满足的输入契约（见 README「输入格式」）。"""

    def _write(self, raw_bytes):
        handle = tempfile.NamedTemporaryFile(suffix=".jsonl", delete=False)
        handle.write(raw_bytes)
        handle.close()
        self.addCleanup(os.unlink, handle.name)
        return handle.name

    def test_parses_valid_lines_tolerating_blank_lines_crlf_and_missing_trailing_newline(self):
        path = self._write(
            b'{"text":"a b","categoryId":"c1"}\r\n'
            b"\n"
            b'{"text":"c d","categoryId":"c2"}'
        )
        self.assertEqual(
            [{"text": "a b", "categoryId": "c1"}, {"text": "c d", "categoryId": "c2"}],
            samples_io.read_samples(path),
        )

    def test_tolerates_utf8_bom_at_start_of_file(self):
        path = self._write(b'\xef\xbb\xbf{"text":"a b","categoryId":"c1"}\n')
        self.assertEqual([{"text": "a b", "categoryId": "c1"}], samples_io.read_samples(path))

    def test_ignores_unknown_fields(self):
        path = self._write(b'{"text":"a b","categoryId":"c1","source":"IMPORT"}\n')
        self.assertEqual([{"text": "a b", "categoryId": "c1"}], samples_io.read_samples(path))

    def test_rejects_non_object_line(self):
        path = self._write(b'[1,2,3]\n')
        with self.assertRaises(samples_io.SampleFormatError):
            samples_io.read_samples(path)

    def test_rejects_non_string_text(self):
        path = self._write(b'{"text":123,"categoryId":"c1"}\n')
        with self.assertRaises(samples_io.SampleFormatError):
            samples_io.read_samples(path)

    def test_rejects_whitespace_only_category_id(self):
        # 全空白 categoryId 永远匹配不到真实分类，必须当畸形拒掉，而不是造出一个垃圾标签。
        path = self._write(b'{"text":"a b","categoryId":"   "}\n')
        with self.assertRaises(samples_io.SampleFormatError):
            samples_io.read_samples(path)

    def test_rejects_missing_category_id(self):
        path = self._write(b'{"text":"a b"}\n')
        with self.assertRaises(samples_io.SampleFormatError):
            samples_io.read_samples(path)

    def test_rejects_malformed_json(self):
        path = self._write(b'{"text":"a b",\n')
        with self.assertRaises(samples_io.SampleFormatError):
            samples_io.read_samples(path)

    def test_rejects_file_with_no_samples(self):
        path = self._write(b"\n\n")
        with self.assertRaises(samples_io.SampleFormatError):
            samples_io.read_samples(path)


if __name__ == "__main__":
    unittest.main()
