# 记账页交互与视觉优化

用户已批准完整计划并要求实施。

## 确定范围
- 类型自适应换行、分类网格完整名称；二级区插入被点一级所在行下，一次展开一个父级，允许不细分/清除，编辑自动定位父级。
- 当前页搜索/新增标签，成功自动选中，同账本同名复用，失败保留输入；标签即时持久化。
- 日期时间精确到秒，改日期保留时分秒/毫秒，手动改时间秒级，今天/昨天实时计算。AI 已识别时刻必须保留，无时间沿用表单时刻。原历史时间不猜改。
- 账户紧凑选择，金额突出，单 LazyColumn，底部单一保存操作，保存后返回，不做连续记账；错误就地提示，保存防重、未保存退出确认。
- 沿用现有绿色设计系统/深浅主题，处理大字号/窄屏/IME；类型切换及 AI 应用同步校验账户与分类。
- 流水显示秒，排序按 occurredAt/createdAt/id 稳定排序；不改数据库结构。

## 验收
- 纯逻辑与 ViewModel 回归覆盖分类展开/切换、标签新建同名失败、时间与 AI、重复保存。
- DAO 回归覆盖同秒/同毫秒排序；Compose/模拟器验证网格展开位置、长名称、保存与键盘布局。
- test assembleDebug assembleRelease lintDebug 通过；检查不变的账务余额与统计回归。
- 不做支付识别测试、不处理根目录用户账单 ZIP；连接真机时直接安装 Release，手机页面由用户操作。

## 技术依据
现状六种类型 weight(1f)+单行 Ellipsis；CategorySection 将全部一级与二级分开；SelectDate/fromPickerUtcMillis 归零，applyParsed 忽略 ParsedEntry.time。复用 SaveTagUseCase、TxnValidator、TimeProvider、VipLedgerTokens，不新增依赖。
