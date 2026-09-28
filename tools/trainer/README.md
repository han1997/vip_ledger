# tools/trainer —— 端侧线性分类器的 Python 训练脚本

在电脑上训练端侧分类器（`ml:classifier` 的多项式逻辑回归 / softmax），导出一个 App 可直接加载的
权重 JSON。**纯 Python 标准库**（PRD 决策 D2），零第三方依赖，不需要 numpy/torch。

脚本与 Kotlin 侧**同构**：同一份特征、同一个模型结构、同一份权重导出格式、同一套洗牌与 SGD 顺序。
这一点由一对**钉在同一份 fixture 上**的测试守住（见下「同构验证」）。

## 环境

- Python **3.8+**（本仓库在 Python 3.8.10 上验证）。
- 只用标准库：`re` / `json` / `math` / `argparse` / `os` / `sys` / `time` / `unittest`，零第三方依赖。

## 快速开始

在 `tools/trainer/` 目录下：

```bash
# 训练并导出权重
python train.py --samples samples.jsonl --output category_model.json

# 只跑同构自检（读 fixtures/，与 checked-in 期望模型比对，非零退出表示不一致）
python train.py --selfcheck
# 等价：python selfcheck.py
```

超参与 Kotlin `TrainingConfig` 的默认值一致，可显式覆盖：

| 参数 | 默认值 | 对应 Kotlin |
|---|---|---|
| `--epochs` | `20` | `TrainingConfig.DEFAULT_EPOCHS` |
| `--lr` | `0.5` | `TrainingConfig.DEFAULT_LEARNING_RATE` |
| `--min-samples` | `3` | `TrainingConfig.DEFAULT_MIN_SAMPLES_PER_CLASS` |
| `--seed` | `20260928` | `TrainingConfig.DEFAULT_SEED` |

另有 `--trained-at`（epoch 毫秒，默认「现在」）与 `--model-version`（默认 `v1-<trained-at>`）。

## 输入格式（JSON Lines）—— PR3 的样本导出必须产出这个

一行一个 JSON 对象，**逐字**如下（这是 Python 侧定义的契约，App 侧 PR3 的样本导出要照此产出）：

```json
{"text": "午饭 牛肉面", "categoryId": "cat-food"}
{"text": "打车 回家", "categoryId": "cat-transport"}
```

- **`text`**（string，必填）：**已拼接**的「标题 + 备注」。
  口径必须与 App 侧 `CharNgramFeaturizer.joinText(title, remark)` /
  `ml_training_sample.text` 一致 —— 即
  `listOf(title, remark).filter { it.isNotBlank() }.joinToString(" ")`。
  **不要把标题和备注拆成两个字段**：特征是在拼接后的整串上做的。
- **`categoryId`**（string，必填，**非空白**）：该样本归属的真实分类 id。
- 未知字段忽略；**空行**（含文件末尾换行）忽略。
- 文件编码 UTF-8，**容忍开头的 UTF-8 BOM**（Windows 工具导出常见）；`CRLF` 行尾与「末行无换行」都接受。
  `USER_CORRECTION` 与 `IMPORT` 两种来源都可以喂进来，本格式不区分来源、也不加权。
- **任一行畸形（非法 JSON、非对象、字段缺失/类型不符、`categoryId` 全空白）→ 打印带行号的错误并以非零码退出**，
  绝不静默跳过 —— 静默跳过会让「样本没进训练」变得无从察觉。

## 输出格式

`train.py` 写出与 Kotlin `ModelCodec` **逐字段同名**的权重 JSON：

```json
{
  "formatVersion": 1,
  "featureVersion": 1,
  "vocabVersion": "char2-3gram-fnv1a-v1",
  "buckets": 262144,
  "modelVersion": "v1-1695800000000",
  "trainedAt": 1695800000000,
  "sampleCount": 13,
  "labels": ["cat-food", "cat-transport"],
  "labelCounts": [7, 6],
  "pendingCounts": {"cat-travel": 2},
  "bias": [0.2386, -0.2386],
  "weights": [
    {"indices": [1277, 3478], "values": [0.51, -0.12]},
    {"indices": [1277], "values": [-0.40]}
  ]
}
```

- `labels` **升序**，`labelCounts` / `bias` / `weights` 与之逐行对齐；
- `weights` 只存**非零桶**（`indices` 升序、`values` 一一对应）；未列出的桶恒为 0；
- `pendingCounts` 是累计样本数仍低于阈值、尚未进标签表的分类（键与 `labels` 不相交）；
- 字段顺序与 `ModelCodec` 的 DTO 一致（顺序本身不影响加载）。

### 放到设备上

App 侧 `ModelFileStore` 默认文件名是 **`category_model.json`**，位于应用私有 `filesDir`。
把导出的文件放到该路径即可被推理加载：

```
<app filesDir>/category_model.json
```

调试构建可用 `adb push` 到 `/data/data/<applicationId>/files/category_model.json`（或经
模型管理页的导入入口，PR3）。加载失败（版本不符 / 结构损坏）时 App 视为「无可用模型」，不崩溃。

## 同构验证（本目录的验收核心）

同构验证**只允许有一个真正的跨语言比对方向**，否则就会「拿 Python 输出比 Python 输出」地自证其说。
当前分工：

| 方向 | 位置 | 性质 |
|---|---|---|
| Python → Kotlin golden | `python train.py --selfcheck`（不在 Gradle 里） | **真·跨语言比对**（唯一） |
| Kotlin → 自己 | `PythonIsomorphismFixtureTest.kotlinTrainingIsDeterministicAndMatchesPinnedGolden` | **护栏**（非跨语言），防 golden 过期 |

fixture 三件套：

| 文件 | 作用 |
|---|---|
| `fixtures/samples.jsonl` | 样本（上面的 JSON Lines 格式） |
| `fixtures/fixture.json` | 钉死的超参、`seed`、`trainedAt`、`tolerance` |
| `fixtures/expected_model.json` | **由 Kotlin `ModelCodec.encode` 产出的**期望权重（设备侧定义格式，是权威） |

`samples.jsonl` 刻意覆盖了最容易两边走偏的输入，且都真正进了训练集（不是摆设）：

- **大小写 + 连续空格 + emoji**：`"  STARBUCKS   Coffee ☕  "`（BMP emoji）—— 归一化（小写 / 折叠空白）
  只要有一侧没做，权重就会明显漂移（实测单侧不做折叠 → 权重差 ≈ 2.5e-1），会被抓住；
- **补充平面码点**：`"聚餐 火锅 🍲"`（U+1F372，UTF-16 下是 2 个 code unit）—— 若有一侧按 UTF-16 code
  unit 而非 Unicode 码点切 n-gram，这条样本就会暴露（实测权重差 ≈ 2.9e-1）；
- **低于阈值的分类**：`cat-travel` 只有 2 条 → 进 `pendingCounts`、不进标签表，用来守住 `minSamplesPerClass`。

- **Python 半边（真·跨语言比对）**：`python train.py --selfcheck` —— 用 **Python** 重新训练，与
  Kotlin golden 按容差比对，并核对每条 fixture 样本的预测一致；不一致非零退出。
  **它必须看到非零、但在容差内的差异**（两侧分别是 float64 / float32）；若报 `0.0` 差异，
  本脚本**主动判失败**，因为那意味着 golden 又被 Python 生成、比对退化成了自证。
- **Kotlin 半边（护栏）**：`ml/classifier/src/test/.../train/PythonIsomorphismFixtureTest.kt` —— 断言
  Kotlin 连训两次逐字节相同、且等于 golden。这是「Kotlin 口径漂移 + golden 过期」的护栏，
  **不是**跨语言比对（两侧都是 Kotlin）。Kotlin 一变它先红，提示先重生成 golden，再让 Python
  selfcheck 去暴露真正的语言间分歧。

### 重生成 golden（必须走 Kotlin，禁止手改浮点）

golden 的权威是设备侧（Kotlin）。改了 Kotlin 的特征/洗牌/SGD/导出格式后：

```powershell
$env:REGENERATE_ISOMORPHISM_GOLDEN=1
.\gradlew.bat :ml:classifier:test --rerun-tasks `
  --tests "*PythonIsomorphismFixtureTest.regenerateGoldenWhenRequested"
```

然后**务必再跑一次** `python train.py --selfcheck`，确认跨语言差异仍是非零且在容差内。

`fixtures/fixture.json` 当前钉死：

```json
{"epochs": 20, "learningRate": 0.5, "minSamplesPerClass": 3,
 "seed": 20260928, "trainedAt": 1695800000000, "tolerance": 0.0001}
```

### 洗牌一致性：做到了

Kotlin 用自写的 **xorshift64\***（`DeterministicRandom`，每 epoch 以 `seed + epoch` 重新播种）
而不是 `java.util.Random`，正是为了让 Python 能逐位复刻。Python 侧 `trainer.DeterministicRandom`
按同一算法实现（64 位无符号、`x ^= x >> 12; x ^= x << 25; x ^= x >> 27; return x * GOLDEN`；
`next_int` 用 `nextLong() ushr 1` 的非负半区），因此**两侧的每 epoch 样本顺序完全相同**。
同构验证因此不受洗牌差异干扰，剩下的只有浮点表示差。

### 已知的浮点差（不追求逐位相同）

- **Kotlin 全程 `Float`（IEEE float32，24 位尾数）；Python 标准库是 `float`（float64，53 位尾数）。**
  两边都用朴素浮点运算（D2 的取舍），但尾数位宽不同，若干轮 SGD 后会累积出微小差异，
  因此**不预期、也不要求逐位相同**。
- **实测（本机，Python 训练结果 vs Kotlin golden）**：在 fixture 上 20 epoch、两边全部桶比较，
  偏置最大绝对差 **≈ 1.80e-7**、权重最大绝对差 **≈ 2.91e-7**（量级就是 float32 的少数 ULP），
  fixture 上每条样本两侧的 argmax 预测**完全一致**。差异**非零**正是两侧真在跨语言比对的证据。
- **容差取 `1e-4`**：约为噪声底（3e-7）的 300×，足以吸收不同平台的 libm 差异
  （`Java Math.exp` 与 Python `math.exp` 的 double 结果可能差 1 ULP，round 到 float32 后几乎恒等）。
- **灵敏度**：fixture 能抓住任何**单点超过 `1e-4` 绝对差**的漂移。实测口径类 bug 的幅度远大于此，
  会被**响亮地**抓住：洗牌差一步（seed+1）→ 权重差 ≈ 2e-2；丢掉 L1 归一化 → ≈ 7e-1；
  只取 2-gram → ≈ 1.1；`minSamplesPerClass` 改动导致标签表变动 → 直接报 `labels/labelCounts/
  pendingCounts differ` 等离散项。反过来，只有当改动让某个权重相对变化小于约 `2e-4` 时才可能漏过
  —— 那不是「口径 bug」，是浮点噪声量级。
  要调就改 `fixtures/fixture.json` 的 `tolerance`，Python selfcheck 立即生效。

## 单元测试

```bash
cd tools/trainer
python -m unittest discover -s tests -v
```

特征锚点与 Kotlin `CharNgramFeaturizerTest` 用的是**同一组桶值**
（`午饭` → 桶 204402；`吃午饭` → 204402/228793/253254；`🍜面` → 1 个 n-gram），
任一侧改了哈希/归一化口径都会变红。

## 文件一览

| 文件 | 职责 |
|---|---|
| `featurizer.py` | 字符 2-3 gram 哈希 TF 特征（`CharNgramFeaturizer` 的复刻） |
| `model_io.py` | 权重 JSON 编解码（`ModelCodec` 的同构对应物） |
| `trainer.py` | softmax + 增量 SGD、xorshift64\* 洗牌（`LinearModelTrainer` / `DeterministicRandom` 的复刻） |
| `samples_io.py` | JSON Lines 样本解析契约（`train.py` 与 `selfcheck.py` 共用，PR3 对齐这一处） |
| `train.py` | 命令行入口（训练 / `--selfcheck`） |
| `selfcheck.py` | 同构自检实现（真·跨语言比对方向） |
| `tests/test_trainer.py` | 纯标准库单测 |
| `fixtures/` | 同构 fixture（golden 由 Kotlin 产出） |
