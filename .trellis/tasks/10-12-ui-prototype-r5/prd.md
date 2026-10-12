# Prototype round 5: editor redesign — page never scrolls (mainstream-app architecture)

## Goal

记一笔页面多轮反馈后,用户最终要求:**「忘记我对你所有的要求,只有一项:尽可能避免上下滑动,
参考这两张截图,做出我满意的设计」**。参考应用为手机内两款主流记账软件(随手记 / 钱迹类)。

## Final design (v2.0)

参考应用的核心不是某个控件,而是**架构**:

```
┌───────────────────────────────┐
│ 类型分段(支出/收入/转账)        │ 固定
│ ┌───────────────────────────┐ │
│ │ 分类图标区(唯一可内部滚动)   │ │ flex:1
│ │  一级 5 列圆形图标           │ │
│ │  二级浅底胶囊面板(内联插入)   │ │
│ └───────────────────────────┘ │
│ 备注………………  ¥128.00        │ 固定
│ [账户][时间][标签][AI]          │ 固定
├───────────────────────────────┤
│ 1 2 3 │                        │
│ 4 5 6 │  保存                  │ 固定(常驻)
│ 7 8 9 │                        │
│ . 0 ⌫ │                        │
└───────────────────────────────┘
```

* **页面本身永不滚动**:`.content.edit-mode` 为 `overflow:hidden` 纵向 flex;
  唯一可内部滚动的是中间分类区(`flex:1 + overflow-y:auto`),且实测全部状态下无需滚动。
* **键盘常驻**(与参考应用一致):删除「焦点离开金额自动收起」机制。
* 金额与备注同一行(29px);分类为图标方块;账户/时间/标签/AI 为底部胶囊行。

## Acceptance Criteria

* [x] 全部状态(新建支出 / 二级展开 / 收入 / 转账 / 编辑已有账目)下:
  页面 `scrollHeight == clientHeight`、分类区 `scrollHeight == clientHeight`、
  键盘不超出手机壳 —— **零滑动**。
* [x] 键盘常驻;无假按钮;无 `prompt`/`alert`/`confirm`;无脚本错误、无外部请求。
* [x] 冒烟测试全绿(85 个动作全有处理器);README / BASELINE 同步(新哈希)。

## Out of Scope

* 不改 Android 源码;不做 Compose 迁移;深色模式不做。

## Technical Notes

* 关键 CSS:`.content.edit-mode{overflow:hidden}` + `.cat-grid{flex:1;overflow-y:auto;min-height:0}`
  (配 `::before/::after` 安全垂直居中,溢出时退化为顶部对齐)。
* 删除:`state.amountFocused`、`focus-amount`、`.pad-collapsed`、`keypad-open` class、
  点击处理器中的失焦逻辑。
* 参考截图(未纳入版本库):`7420795ccd0a0ee114f678e3dd6f2ff3.jpg`、`8c34194a1b2e395cd2b6c347655120a4.jpg`。

## 过程教训

1. 先问清「参考什么」:参考应用的可取之处是**信息架构**(固定头尾 + 中间弹性区),
   而不是某个具体控件的样式。
2. 「避免滑动」的正确解法是**结构**(overflow 分区),不是把控件压小。
   压小字号/触摸目标只会换来「不舒服」。
3. 用脚本改 CSS 时切片下标必须 `start < end`,否则 `s[:start] + s[end:]` 会复制出重复块
   (本轮曾把 style.css 弄坏,靠 `git checkout` 回滚后重写)。

## v2.1 补充:三处交互修正

1. **备注改真实输入框**(用户:点备注直接弹输入法,不要弹窗):
   `<input data-field="note">` 内联;删除 pick-note 弹层与 save-note。
2. **金额点击区扩大**(用户:金额点击区域太小):`.amt-zone` 48x88px,
   点按收起输入法回到数字键盘;字号 29->31px。
3. **二级面板自动定位**(用户:点第二行一级分类时做好定位,避免滑动找二级):
   `revealSubPanel()` 在 pick-cat / set-type 后调用,面板被裁切时自动滚入视野;
   矮屏(手机壳压缩到 600px)实测选第二行一级后 panelVisible=true。

冒烟测试新增:备注为真实 INPUT、金额区 >=44px、矮屏下选行二一级后面板自动可见。
