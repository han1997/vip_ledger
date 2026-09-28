"""JSON Lines 训练样本的读写契约 —— PR3 的样本导出必须产出这个格式。

train.py（CLI）与 selfcheck.py（自检）共用这一份解析，避免「两个解析器各写一遍、慢慢漂移」
——样本格式是 App 侧 PR3 要对齐的契约，只允许有一处定义。

格式（一行一个对象，空行忽略）::

    {"text": "午饭 牛肉面", "categoryId": "cat-food"}
    {"text": "打车 回家", "categoryId": "cat-transport"}

- ``text``（string，必填）：**已拼接**的「标题 + 备注」，口径与
  ``CharNgramFeaturizer.joinText(title, remark)`` / ``ml_training_sample.text`` 一致；
- ``categoryId``（string，必填，**非空白**）：该样本归属的真实分类 id；
- 未知字段忽略；空行（含文件末尾换行）忽略；
- 文件编码 UTF-8，**容忍开头的 UTF-8 BOM**（Windows 工具导出常见）；``CRLF`` 与「末行无换行」都接受；
- **任一行畸形（非法 JSON、非对象、字段缺失/类型不符、categoryId 全空白）→ 抛 [SampleFormatError]**，
  绝不静默跳过：静默跳过会让「样本没进训练」变得无从察觉。
"""

import json

# 用 utf-8-sig 读：有 BOM 时剥掉，无 BOM 时与 utf-8 完全等价。
_SAMPLE_ENCODING = "utf-8-sig"


class SampleFormatError(Exception):
    """样本输入格式错误（带行号，便于定位）。"""


def read_samples(path):
    """解析 JSON Lines 样本；任一行畸形抛 [SampleFormatError]（不静默跳过）。"""
    samples = []
    with open(path, "r", encoding=_SAMPLE_ENCODING) as handle:
        for lineno, line in enumerate(handle, 1):
            stripped = line.strip()
            if not stripped:
                continue  # 允许空行（含文件末尾换行）
            try:
                record = json.loads(stripped)
            except ValueError as error:
                raise SampleFormatError("line %d: not valid JSON: %s" % (lineno, error))
            if not isinstance(record, dict):
                raise SampleFormatError("line %d: expected a JSON object" % lineno)
            text = record.get("text")
            category_id = record.get("categoryId")
            if not isinstance(text, str):
                raise SampleFormatError("line %d: field \"text\" must be a string" % lineno)
            if not isinstance(category_id, str) or category_id.strip() == "":
                raise SampleFormatError(
                    "line %d: field \"categoryId\" must be a non-blank string" % lineno)
            samples.append({"text": text, "categoryId": category_id})
    if not samples:
        raise SampleFormatError("no samples found in %s" % path)
    return samples
