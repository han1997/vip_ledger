# 研究:一木记账 BIFF8 `.xls` 测试夹具(表头 + 前 40 条数据行)的生成方案

- **Query**: 从真实一木记账 `.xls` 剔出一个**可入库的最小 BIFF8 测试夹具**(表头 + 前 40 条数据行,含至少一条跨 CONTINUE 边界的长备注),用于 `core/data` 的离线单元测试,并给出可复现的记录级方案。
- **Scope**: internal(分析真实现有文件)+ 对 xlrd 2.0.2 源码与行为的验证。
- **Date**: 2026-10-13
- **源素材**: 仓库根 `账单_1009122349.zip`(未跟踪);已解出副本 `~/.claude-scratch/ym_bill_13488/账单_1009122349.xls`;xlrd 2.0.2 全量 dump 在 `~/.claude-scratch/ymtools/rows.json`(6008 行 × 23 列)。

> 规范说明: 本文件是**研究结论**、不是实现。原文件是「下载/解包出来的不可信数据」,所有脚本只读它、把路径当参数,绝不在其目录内运行解释器或构建工具(解释器会从脚本目录与当前工作目录加载模块,一旦有植入的 `json.py` 会在 `import json` 时执行)。

---

## 0. 前提核实(实测,`python -I` + xlrd 2.0.2)

- OLE/CFB 魔数 `d0cf11e0a1b11ae1`,sector shift = 9(512 字节),FAT 扇区 37 个;`Workbook` 流长度 **2,357,760 字节 = 4,659 × 512**。
- 1 个 sheet「账单」;`BOUNDSHEET.lbPlyPos = 263281`(= globals 子流长度,sheet 紧跟其后)。
- 行列: **6009 行(含表头)= 6008 条数据;23 列**。
- 记录密度: ROW 6009 / LABELSST 132199 / NUMBER 6008 / CONTINUE 31 / SST 1 / BOUNDSHEET 1 / **EXTSST 1(0x00FF,任务给定事实里没提到它)** / DIMENSIONS 1。**无** FORMULA、MERGEDCELLS、BLANK、MULBLANK、RK、MULRK、RSTRING、隐藏 sheet。
- SST: `cstTotal=132199`、`cstUnique=8461`;**全部宽字符**(`options=0x01`,bit0=1);1 个 SST 记录 + 31 个 CONTINUE。
- 单元格类型: 每行 23 列中,列 2(金额)是 NUMBER,其余 22 列全是 LABELSST。

---

## 1. 记录级裁剪方案

### 1.1 目标文件记录布局(实测)

Workbook 流 = **globals 子流(record 0..96)+ sheet 子流(record 97..144546)**。

globals 子流记录顺序(record 下标 : 记录号 : 名称 : payload 长度):

```
0  : 0x0809 BOF          16   dt=0x0005 (workbook globals)
1  : 0x00E1 XFEXT         2
2  : 0x00C1               2
3  : 0x00E2               0
4  : 0x005C WRITEACCESS 112
5  : 0x0042 CODEPAGE      2   = 1252
6  : 0x0161               2
7  : 0x013D               2
8  : 0x009C BUILTINFMTCOUNT 2
9  : 0x0019 WINDOWPROTECT 2
10 : 0x0012 SAVERECALC    2
11 : 0x0013 SAVEASLOOP    2
12 : 0x01AF               2
13 : 0x01BC               2
14 : 0x003D WINDOW1      18
15 : 0x0040 BACKUP        2
16 : 0x008D HIDEOBJ       2
17 : 0x0022 DATEMODE      2
18 : 0x000E PRECISION     2
19 : 0x01B7               2
20 : 0x00DA BOOKBOOL      2
21..26 : 0x0031 FONT       26   (6 个)
27 : 0x041E FORMAT        25   (1 个自定义格式)
28..53 : 0x00E0 XF          20   (26 个)
54..59 : 0x0293 STYLE        4   (6 个)
60 : 0x0160                2
61 : 0x0085 BOUNDSHEET    12   lbPlyPos=263281
62 : 0x008C COUNTRY        4
63 : 0x00FC SST          8224
64..94 : 0x003C CONTINUE  (31 个, 6035..8224 字节)
95 : 0x00FF EXTSST        1018 (127 项, 8 字节/项)
96 : 0x000A EOF            0
```

sheet 子流头部(record 97..139): BOF(dt=0x0010) → INDEX(0x020B,768 字节) → CALCMODE/CALCCOUNT/REFMODE/ITERATION/DELTA/SAVERECALC/PASSWORD/PROT4REV/GRIDSET/GUTS/DEFAULTROWHEIGHT/WSBOOL/HEADER/FOOTER/HCENTER/VCENTER/SETUP/DEFCOLWIDTH → COLINFO(0x007D)×23 → DIMENSIONS(0x0200) → 之后是 ROW/LABELSST/NUMBER 交替,末尾 SELECTION(0x001D)、EOF(0x000A),再往后是 118 个 0x0000 空记录(填充)。

### 1.2 保留 / 丢弃决策(每条给出理由)

**globals 子流:**

| 记录 | 保留? | 理由 |
|---|---|---|
| BOF(0x0809, dt=0x0005) | **保留** | 版本判别(BIFF8)。丢弃后 xlrd 无法识别流。 |
| BOUNDSHEET(0x0085) | **保留,必须改 lbPlyPos** | 指向 sheet BOF 的流内偏移;裁剪后 SST 变短,sheet 前移,偏移必须重算(见 1.3)。 |
| SST(0x00FC) + CONTINUE(0x003C) | **保留,内容重建** | 见第 2 节(重排索引)。 |
| EOF(0x000A) | **保留** | globals 结束标志。 |
| FONT / XF / FORMAT / STYLE / CODEPAGE / WINDOW1 / 其余设置记录 | **保留(原样照抄)** | xlrd 对它们「认识就存、不认识就跳过」;照抄最稳。裁掉 XF 会让 `xf_index` 越界(xlrd 只 warning,但没必要冒风险)。 |
| EXTSST(0x00FF) | **丢弃** | 只是 SST 的加速索引。**实测**:把记录号 0x00FF 改成 0x0000(长度不变)后 xlrd 2.0.2 仍正常打开,6009×23 全部读对(见 1.4)。 |

**sheet 子流:**

| 记录 | 保留? | 理由 |
|---|---|---|
| BOF(0x0809, dt=0x0010) | **保留** | sheet 起始。 |
| INDEX(0x020B) | **丢弃** | 里面是 DBCELL 偏移,裁剪后指向已不存在的记录;只用于加速随机访问,顺序读取不需要。 |
| DIMENSIONS(0x0200) | **保留,必须改行列范围** | 原值 `rw1=0, rw2=6009, c1=0, c2=23, reserved=0`;裁剪后 `rw2 = 41`(表头 + 40),`c2` 不变。 |
| COLINFO ×23 / SETUP / GUTS / DEFCOLWIDTH 等 | **保留(原样照抄)** | 纯展示信息;照抄最稳。 |
| ROW(0x0208) | **保留,每个保留行各一条** | 与原文件同构(每非空行一条);**不用于跟踪行号**——行号取自 LABELSST/NUMBER 自带的 row 字段(见 1.5)。 |
| LABELSST(0x00FD) / NUMBER(0x0203) | **保留,SST 索引重写** | 只保留前 41 行(表头 + 40 条数据)。 |
| EOF(0x000A) | **保留** | sheet 结束标志。 |
| sheet 末尾 118 个 0x0000 空记录 | **丢弃** | 无意义填充。 |

### 1.3 关键约束: 内部偏移必须重算

- **BOUNDSHEET.lbPlyPos**: 原值 263281 = globals 子流长度。裁剪后 SST 变短,sheet 偏移变小。生成时先拼好 SST 块,再回填 `len(globals)`(lbPlyPos 是 u32 定长,回填不改变长度,一次回填即可;脚本里迭代 3 次保险)。
- `.zip` 形态只要包含一个 `.xls` 成员即可,与裸 `.xls` 解析路径相同。

### 1.4 EXTSST 实测结论(回答「能不能丢」)

**实测(非推断)**: 把 record 95 的 EXTSST 记录头 `FF 00 FA 03` 改成 `00 00 FA 03`(记录号变 0x0000、长度不变)后,xlrd 2.0.2 仍能正常打开,`nrows=6009, ncols=23`,首行 `2026-10-08 20:48 / -0.07` 完全正确。

验证脚本 `~/.claude-scratch/ymtools/test_extsst.py`:
```python
assert d[262767] == 0xFF and d[262768] == 0x00   # EXTSST 记录头(物理偏移)
d[262767] = 0x00; d[262768] = 0x00               # 0x00FF -> 0x0000(未知记录)
b = xlrd.open_workbook(out); sh = b.sheet_by_index(0)
# 输出: rows 6009 cols 23  r1c0 2026-10-08 20:48  r1c2 -0.07
```
**结论**: EXTSST 对顺序读取无关紧要,丢弃安全。(自写解析器本来就忽略它。)

### 1.5 ROW / LABELSST 的行号来源(⚠️ 本节先前结论已作废)

> **2026-10-14 订正**:本节原先的「实测」结论是**错的**,根因是旧探针的对比逻辑写错(见下文「先前的错误从何而来」)。已作废的原文保留在本节末尾的 `<details>` 里以便追溯。
> **正确结论**:本文件 LABELSST 自带的 `row` 字段**完全可靠**,行号应直接取单元格自身的 `row`;ROW 记录在本文件是**每个非空行一条**(6009 条对应 6009 行),并非「每 32 行一个块」。

- 本文件 LABELSST payload 是 10 字节: `row(u16) col(u16) xf(u16) isst(u32)`。规范允许 row 字段为 0,但**本文件不是那种极端情况**。
- **订正后的实测**(脚本 `~/.claude-scratch/ymtools_verify/rowcheck.py`,oracle = xlrd 2.0.2):
  - 以「单元格自带 row/col」为键铺开全部 132,199 条 LABELSST,得到 **132,199 个互不重叠的格子**(`distinct_slots == own_label_cells`)—— 双射,无键碰撞。
  - xlrd 报出的文本格数恰为 132,199,逐格检查 **缺失 = 0**(`text_cells_missing_in_ownrow == 0`)。
  - ROW 记录数 = **6009**,`rowrecs_unique == 6009`,前 8 条 rw 依次为 `0,1,2,3,4,5,6,7`。
  - 结论:`Biff8Reader` 用单元格自带的 row 建行是**正确**的,其输出与 xlrd 逐格一致(138,207 格 0 mismatch)。
- **先前的错误从何而来**:旧探针 `probe2.py` 用
  `cells_ownrow[(r,c)]` 与 `cells_rowtrack[(cur_row,c)]` 求差,`cur_row` 取的是「上一条 ROW 记录的行号」。
  两个字典的 key 根本不是同一个格子,统计出的 128,063 也就不是「row 字段错」,而是**探针自身的对比逻辑写错了**。
- **夹具影响**:夹具仍**保留 ROW 记录**(与原文件同构,每保留行一条即可),但目的不是「靠它跟踪行号」,而是与真实文件保持结构一致;LABELSST 的 `row` 字段**原样照抄**,无需重编号。

<details>
<summary>已作废的原文(2026-10-13)</summary>

- 本文件 LABELSST payload 是 10 字节: `row(u16) col(u16) xf(u16) isst(u32)`。规范允许 row 字段为 0,行号必须靠 ROW 记录跟踪。
- **实测发现**: 本文件 128,063 条 LABELSST 的 payload row 字段与「按 ROW 记录跟踪出来的行号」**全部不一致**(`labelsst_rowfield_mismatches=128063`,样本 `[0,0],[0,1],[0,2]...`——即 row 字段几乎全是 0 或错值)。
- **因此夹具必须保留 ROW 记录并逐行对应**,绝不能靠 LABELSST 的 row 字段。

</details>
- ROW 记录 payload 16 字节,裁出来的 ROW 记录**可原样照抄**(rw 值保持原行号 0..40,不需要紧缩重编号——xlrd 与自写解析器都按 ROW 的 rw 建行,不要求连续)。

### 1.6 表头 + 40 行的 SST 规模

- 前 41 行实际引用的**不同 SST 字符串 = 137 条**(`distinct_strings_rows0to40=137`;全文件 8461 条)。
- 这 41 行里**最长字符串 = 66 字符**(数据行 26 的备注,「上海地铁-市域线国际旅游度假区-2026-10-05 09:51:06-市域线虹桥T2航站楼-2026-10-05 10:29:40」)。按全部宽字符编码,137 条字符串的 SST 内容约 **2.6 KB**,远小于 8224 单记录上限——**默认不会跨 CONTINUE**,必须人为制造边界(见第 2 节)。

---

## 2. 保证夹具里至少有一条跨 CONTINUE 边界的字符串

### 2.1 现状: 前 40 行没有任何字符串跨 CONTINUE

实测: 全文件 27 条跨边界字符串(`crossing_count=27`),但 **`crossing_in_40 = []`**(前 41 行里 0 条)。原样剔前 40 行,SST 续接逻辑在夹具里形同虚设。

跨边界字符串的实际分布(实测)——大多是日期串,落在行 142、301、436、601、760、...、5809:

```
idx 339  row 142/143   "2026-09-03 11:20"
idx 616  row 301/302   "2026-07-18 06:18"
idx 885  row 436       "2026-06-15 12:33"
idx 1145 row 601/602   "安徽省黄山市屯溪区昱西街道率水路4..." (18 字)
idx 1413 row 760       "2026-04-02 21:16"
... 共 27 条
```

### 2.2 为什么「只塞字符串」会破坏一致性(回答任务问题 2 的方案选择)

任务问: 「如果前 40 行里没有任何足够长的字符串,建议方案是『从全量里挑一条最长的备注,把它所在的整行替换进夹具』而不是『只把字符串填进去』——请说明为什么后者会破坏一致性。」

**「只塞字符串」破坏一致性的三个原因**:
1. **索引错位**: SST 是共享字符串表,`LABELSST.isst` 是 u32 索引。往 SST 中间插一条字符串,其后所有字符串索引 +1,必须连带改所有引用旧索引的 LABELSST(本文件 SST 复用率高,极易漏改)。
2. **校验等式失效**: 夹具的契约是「解析夹具 == 原文件前 41 行的逐格镜像」。多出一条「前 41 行没引用到的字符串」后,SST 不再是这 41 行的最小闭包,多出来的字符串无从核对,校验脚本只能放过它——校验就不再完备。
3. **数据真实性**: 凭空多出的字符串在夹具里没有任何单元格引用,它测的是「解析器容忍垃圾」而不是「解析器正确续接」。

**但本研究有一个更重要的实测发现**: 「长」和「跨边界」在本文件里**不是同一回事**——全文件最长的 10 条字符串(159/88/87/83/82/70/66/66/62/58 字)**全部 `cross=False`**(它们都完整落在单个 chunk 里)。所以「换一条最长的行进来」**并不自动**产生跨边界;必须配合 2.3 的收窄手段。

**实测结论: 前 40 行根本不需要换行**。数据行 26 本身就带 66 字的长备注(全文件并列第 7 长),只要把 SST 的 chunk 上限收窄到比它小,它自然被切开。**推荐方案: 不换行,直接用前 40 行 + 收窄 CAP**。这样夹具 = 原文件前 41 行的**严格子集镜像**,校验等式最干净。

### 2.3 保证「该字符串必定跨边界」: SST chunk 上限应设为多少

- BIFF8 记录 payload 上限 = **8224 字节**(本文件实测 chunk 长度 8224/8223/8219/6035,均 ≤ 8224)。
- 要稳定触发: 让每个 SST/CONTINUE chunk 的 payload = CAP 字节,并让长字符串恰好被 CAP 切在中间。
- **实测生成器做法**: 选定最长字符串(66 字,数据行 26 的备注)后取 `CAP = (该字符串在 logical 流中的结束偏移 - 8) - 2*CUT_CHARS`,即把它切在第 CUT_CHARS 个字符处。`CUT_CHARS=45` 时 CAP=1657,`CUT_CHARS=15` 时 CAP=1717——**两种都实测通过**(xlrd 打开 + 逐格一致 + 恰好 1 条跨边界)。
- **回答任务问题**: 「为让长字符串必定跨边界,SST 记录的最大 payload 应设成多少?」——**规格上限 8224 不变;但生成夹具时应主动收窄 CAP 到『长字符串结束偏移 - 8 - 2×CUT_CHARS』(约 1.6~1.7 KB),让切割点可控、必定命中**。xlrd 实测接受任意 ≤ 8224 的 CAP(本文件原生 chunk 就有 8219/8223 等非 8224 值)。
- **续接字节规则(实测)**: 每条 CONTINUE 若起始位置落在某字符串的字符数据中间,其**首字节 = 新的 options 字节**(本例 `0x01`,bit0=1 宽字符);若起始位置正好在字符串 header 处,则**不加该字节**。生成器用 `inside_char_data(pos)` 判断是否 prepend `b'\x01'`。源文件里 CONTINUE 首字节实测有 `0x01` 和 `0x10`(bit4;SST 续接只要求 bit0)两种。

### 2.4 实测验证结果(生成 → xlrd 打开 → 逐格比对)

生成器 `~/.claude-scratch/ymtools/build_fixture.py`(参数 `NROWS=40, CUT_CHARS=45`):

| 指标 | 值 |
|---|---|
| 保留行数 | 41(表头 + 40) |
| LABELSST 记录数 | 903(41 行 × 22 列 + 表头 23 列中 1 个是 NUMBER... 实际 22×41 + 1 = 903) |
| NUMBER 记录数 | 40(每数据行 1 个) |
| ROW 记录数 | 41 |
| SST 字符串数 | 137(`cstTotal=903, cstUnique=137`) |
| SST chunk | 2 个(1657 + 1201 字节),CONTINUE 首字节 `0x01` |
| 跨边界字符串 | **恰好 1 条**(idx 94,66 字,数据行 26 的备注,切在第 45 字符处) |
| 裁剪后 Workbook 流 | 18,769 字节(原 2,357,760) |
| 紧凑 CFB 文件 | **20,480 字节**(40 个 512 字节扇区) |
| 可复现性 | 两次生成 SHA-256 完全一致(`b06c0d9f...`) |

**xlrd 2.0.2 逐格校验通过**(脚本 `verify_fixture.py`): `nrows=41, ncols=23`,表头 23 列逐列相等,40 行 × 23 列每格 type + value 与 `rows.json`(原文件 dump)完全一致,mismatch = 0。zip 包裹形态(5,152 字节)同样通过。

---

## 3. 校验方法(用 xlrd 2.0.2 验证「夹具 == 原文件前 40 行 + 表头」)

脚本在 `~/.claude-scratch/ymtools/verify_fixture.py`,**不进仓库**;Python 一律 `python -I`。

```python
# verify_fixture.py(实测通过的版本,支持多文件参数)
import json, xlrd, sys

gold = json.load(open(r'C:/Users/hanhu/.claude-scratch/ymtools/rows.json', encoding='utf-8'))
gold_headers = gold['headers']
gold_rows = gold['rows'][:40]

for fix in sys.argv[1:]:
    b = xlrd.open_workbook(fix)
    sh = b.sheet_by_index(0)
    assert sh.nrows == 41 and sh.ncols == 23
    assert [sh.cell_value(0, c) for c in range(23)] == gold_headers
    bad = 0
    for r in range(40):
        for c in range(23):
            t, v = sh.cell_type(r + 1, c), sh.cell_value(r + 1, c)
            exp = gold_rows[r][c]
            if exp is None:
                ok = (t == 0)
            elif isinstance(exp, float):
                ok = (t == 2 and abs(v - exp) < 1e-9)
            else:
                ok = (t == 1 and v == exp)
            if not ok: bad += 1
    print(fix, 'OK' if bad == 0 else 'FAIL %d' % bad)
```

**校验清单(逐项覆盖)**:
- 行数 = 41、列数 = 23。
- 表头 23 列逐列相等。
- 每格 **type 与 value 都一致**(空 vs 0、文本 vs 数字都要区分;xlrd type 0=EMPTY, 1=TEXT, 2=NUMBER)。
- 夹具里**至少一条字符串跨 CONTINUE**(用独立脚本 `check_fixture_cross.py` 数 crossing,实测 = 1)。
- `xlrd.open_workbook` 正常打开、无异常。
- (可选)zip 包裹形态解包后再开,结果一致。

---

## 4. 产物落位建议

- 落点: `core/data/src/test/resources/`。
- 建议文件名: `yimu_bill_first40.xls`(紧凑 CFB,20,480 字节);zip 形态 `yimu_bill_first40.zip`(约 5 KB,内含同名 `.xls`),用于验证「zip 与裸 xls 两条入口一致」。
- 大小: **紧凑 CFB 20 KB / zip 5 KB**(实测)。注意: 若走「原容器原地替换」路线,文件仍是 2.39 MB(扇区链没变)——**必须重建紧凑 CFB 才能入库**;生成器两种都产出,入库用 compact 版。
- 原始 2.3 MB 文件**不入库**(按 D11);全量 6008 行回归只在本地跑。
- 生成器落位: 按 PRD 写成 `core/data/src/test/` 下的 Kotlin 测试工具或一次性 Python 脚本(本研究的 Python 版在 `~/.claude-scratch/ymtools/build_fixture.py`,可直接照抄逻辑),**不进 main source set、不随 APK 发布**。

---

## 5. 实测 vs 推断标注

| 结论 | 性质 |
|---|---|
| EXTSST 丢弃后 xlrd 仍正常打开(6009×23 全对) | **实测**(test_extsst.py) |
| 前 41 行引用 137 条不同字符串、最长 66 字 | **实测**(sheetinfo.py / probe2.py) |
| 前 40 行无任何跨 CONTINUE 字符串;全文件 27 条 | **实测**(probe2.py) |
| 全文件最长 10 条字符串都不跨边界 | **实测**(probe2.py) |
| LABELSST row 字段与 ROW 跟踪全不一致(128,063 条) | ~~**实测**(probe2.py)~~ **已作废 —— 探针对比逻辑写错,row 字段实际完全可靠(见 1.5 订正)** |
| 收窄 CAP=1657/1717 后 xlrd 接受且恰好 1 条跨边界 | **实测**(build_fixture.py + verify_fixture.py + check_fixture_cross.py) |
| 两次生成字节完全一致(SHA-256 相同) | **实测** |
| 紧凑 CFB(自写 40 扇区容器)被 xlrd 接受 | **实测**(fixture_40_small.xls 逐格校验通过) |
| INDEX(0x020B) 丢弃后 xlrd 顺序读干净 | **实测**(夹具里已无 INDEX,校验通过) |
| DIMENSIONS 改成 41 行后 xlrd 正常 | **实测**(夹具校验通过) |
| 压缩/rich-text/Far-East 字符串头 | 本文件**一个都没有**(100% 宽字符);夹具只能覆盖非压缩一种,另三种建议在单元测试里直接构造 SST 字节喂 `Biff8Reader`,不要指望从真实文件剔出 |
| Kotlin 版生成器照抄 Python 逻辑后同样可复现 | **推断**(需实现阶段实测) |

---

## 6. 生成器核心逻辑(供 Kotlin 实现照抄)

`build_fixture.py` 的步骤(全部已实测跑通):

1. **解 CFB**: 读 OLE 头(sector shift 9、FAT 扇区数、DIFAT[109])→ 拼 FAT → 沿目录链找 `Workbook` 目录项(start sector + size)→ 沿 FAT 链拼出流字节。
2. **扫记录**: `(rid: u16, rlen: u16)` 顺序扫;找 globals BOF(dt=0x0005)与 sheet BOF(dt=0x0010,在 BOUNDSHEET.lbPlyPos 处)。
3. **解 SST**: SST 记录 + 后续 CONTINUE 串成 chunk 列表;游标从 chunk0 偏移 8 起;每字符串读 `cch(u16)+grbit(u8)`;字符游标越界时跳下一 CONTINUE 偏移 0 读新 options 字节(bit0);宽字符剩 1 字节时丢弃该字节。得到 8461 条字符串。
4. **收集前 41 行**: 沿 sheet 子流,ROW 记录按 rw ≤ 40 收集(原样照抄);LABELSST/NUMBER 按**单元格自带的 row 字段** ≤ 40 收集(见 1.5)。
5. **重排 SST**: `used` = 前 41 行引用的旧索引集合(137 条);`used_sorted` 升序 → `newidx` 旧→新映射;每条保留的 LABELSST 的 `isst` 字段(u32,payload 偏移 6)改写为 `newidx[old]`。
6. **重建 SST 字节**: `logical = cstTotal(=903) + cstUnique(=137) + Σ(3 字节头 + 2×字符数)`;全部宽字符(options=0x01)。
7. **收窄切分**: `CAP = 最长字符串结束偏移 - 8 - 2×CUT_CHARS`;按 CAP 切 chunk;每个后续 chunk 若起始位置落在字符数据中间则 prepend `0x01`。
8. **拼 globals**: record 0..62 原样(BOUNDSHEET 的 lbPlyPos 回填 `len(globals)`) + SST 块 + EOF;**丢弃 EXTSST**。
9. **拼 sheet**: sheet BOF + record 98..139(丢 INDEX,DIMENSIONS 改 `rw2=41, c2=23`) + 每行 ROW + 该行 LABELSST(索引已改写)/NUMBER + EOF;**丢弃尾部 0x0000 填充**。
10. **写紧凑 CFB**(推荐入库形态): Workbook 流 pad 到 ≥ 4096(避开 miniFAT)且 512 对齐 → 扇区布局 `[workbook | dir | FAT]` → FAT 链(workbook 链 + dir ENDOFCHAIN + FATSECT 自标) → 目录项 Root Entry(type 5, child=1) + Workbook(type 2, start=0, size=流长) → 512 字节头(魔数、sector shift 9、mini cutoff 4096、DIFAT[0]=FAT 扇区号、其余 FREESECT)。

---

## 7. Caveats / 尚未确定

1. **Kotlin 版生成器未写**(本研究只有 Python 版);照抄第 6 节逻辑时应逐条对照实测值(903/137/1657/1201/41/23)做断言。
2. **压缩 / rich-text / Far-East 三种字符串头**: 真实文件里一个都没有,夹具无法覆盖;建议在 `Biff8ReaderTest` 里直接构造 SST 字节数组(不走完整 .xls)覆盖四种头。
3. **CONTINUE 首字节 `0x10`**: 源文件出现过(bit4=1),xlrd 的 `unpack_SST_table` 只看 bit0(`options & 0x01`),自写解析器也应只看 bit0。夹具里生成的是 `0x01`。
4. **多 sheet**: 本研究只处理单 sheet;PRD 已明确多 sheet 不在范围。
5. **夹具里 `cstTotal=903`**: 等于 LABELSST 记录数(每条引用计一次),不是字符串数;这是 Excel 的语义(总引用次数),xlrd 不校验该值,但自写解析器不应依赖它做循环上限——**以 `cstUnique` 为准**。
6. **生成器参数**: `NROWS=40, CUT_CHARS=45` 为推荐值;`CUT_CHARS=15` 也实测通过(CAP=1717)。切割点不同 → 文件 SHA-256 不同,入库时应固定参数并在测试里固化哈希。

---

## 8. 附: 本研究脚本清单(`~/.claude-scratch/ymtools/`)

| 脚本 | 用途 |
|---|---|
| `census.py` | 全流记录统计、globals 记录序列、EXTSST/DIMENSIONS/XF 存在性 |
| `sheetinfo.py` | SST 解析 + 每字符串长度/grbit/跨 CONTINUE 标记 + 前 41 行引用闭包 |
| `probe2.py` | CONTINUE 首字节、跨边界字符串清单、BOUNDSHEET/EXTSST payload、LABELSST row 字段 vs ROW 跟踪 |
| `layout.py` | 全流 record run-length、BOF payloads、DIMENSIONS/COLINFO/XF payload |
| `sheethead.py` | sheet 头部 43 条记录 + 尾部 0x0000 填充 |
| `test_extsst.py` | EXTSST 丢弃 → xlrd 仍可用的实测 |
| `build_fixture.py` | **夹具生成器**(原地版 + 紧凑 CFB 版,已跑通) |
| `verify_fixture.py` | xlrd 逐格校验(已跑通,mismatch=0) |
| `check_fixture_cross.py` | 夹具 SST 跨边界计数(实测 = 1) |
| `olecheck.py` | CFB 头/目录项检查工具 |

**已生成的夹具产物**(在 scratch,未入库): `fixture_40_final.xls`(20,480 字节,SHA-256 `b06c0d9fe9c59c180d93a4eca31906f50ce30e58ccaa7615fdecddb4ab90cb5d`)、`fixture_40.zip`(5,152 字节)。