# 本地 Ollama 模型在真实记账解析任务上的对比

> 自动生成；任务 `09-24-m6-ai-entry`。每个用例都是真实记账句子，逐字段按预期结果打分。
> 「今天」固定为 2026-09-28；系统提示词带上了当天日期（生产提示词本应如此）。

## `deepseek-r1:1.5b`

| 用例 | 得分 | 耗时 | 输出 tokens | 解析结果 / 问题 |
|---|---|---|---|---|
| 昨天午饭 35 块 微信 | 2/9 | 21.7s | 397 | [{"amount": 35, "type": "expense", "category_id": 1, "account_id": 1, "occurredAt": 1030000, "title": "昨天午饭", "remark": "", "confidence": 0.5}]  << needed tolerant extraction (fence/thinking wrapper); amounts got=[] want=[3500]; categoryId got=None want='c_food'; accountId got=None want='a_wx'; date got=1970-01-12 want=2026-09-27 |
| 收到工资 12000 | 3/8 | 18.3s | 413 | [{"amountCents": 12000, "type": "income", "categoryId": "c_salary", "accountId": "c_salary", "occurredAt": 12000, "title": "收到工资", "remark": "", "confidence": 0.5}]  << needed tolerant extraction (fence/thinking wrapper); amounts got=[12000] want=[1200000]; date got=1970-01-01 want=2026-09-28 |
| 上周三打车 28.5 | 0/8 | 22.7s | 551 | [{"amountCents": 28.5, "type": "c_wx", "categoryId": null, "accountId": null, "occurredAt": null, "title": "上周三打车", "remark": " remark", "confidence": 0.9}]  << needed tolerant extraction (fence/thinking wrapper); amounts got=[] want=[2850]; type got='c_wx' want='expense'; categoryId got=None want='c_transport'; date missing/unparseable raw=None |
| 午饭35 打车28 | 0/5 | 22.8s | 476 | [{"amountCents": [100, 28], "type": ["transportation", "transfer"], "categoryId": "c_transport", "accountId": "a_card", "occurredAt": null, "title": "金额：100.28元", "remark": "打车费用", "confidence": 0.5}]  << needed tolerant extraction (fence/thinking wrapper); amounts got=[] want=[2800, 3500]; type got=['transportation', 'transfer'] want='expense' |
| 12块5 买咖啡 | 0/6 | 19.8s | 398 | [{"amountCents": 125, "type": "c_shopping", "categoryId": "a_wx", "accountId": null, "occurredAt": 0, "title": "买咖啡", "remark": ""}]  << needed tolerant extraction (fence/thinking wrapper); amounts got=[125] want=[1250]; type got='c_shopping' want='expense'; categoryId got='a_wx' want='c_food' |

**小计**：13.9%（5/36）· 平均 21.1s · 平均 447 tokens · 完全正确 0/5

## `gemma3:4b`

| 用例 | 得分 | 耗时 | 输出 tokens | 解析结果 / 问题 |
|---|---|---|---|---|
| 昨天午饭 35 块 微信 | 8/9 | 18.6s | 79 | [{"amountCents": 3500, "type": "expense", "categoryId": "c_food", "accountId": "a_wx", "occurredAt": 1698422400000, "title": "昨天午饭", "remark": null, "confidence": 0.95}]  << date got=2023-10-27 want=2026-09-27 |
| 收到工资 12000 | 7/8 | 15.0s | 79 | [{"amountCents": 1200000, "type": "income", "categoryId": "c_salary", "accountId": "a_bank", "occurredAt": 1698435200000, "title": "工资", "remark": "", "confidence": 0.95}]  << date got=2023-10-27 want=2026-09-28 |
| 上周三打车 28.5 | 7/8 | 13.9s | 77 | [{"amountCents": 2850, "type": "expense", "categoryId": "c_transport", "accountId": "a_cash", "occurredAt": 1698432000000, "title": "打车", "remark": "", "confidence": 0.95}]  << date got=2023-10-27 want=2026-09-23 |
| 午饭35 打车28 | 5/5 | 25.4s | 194 | [{"amountCents": 3500, "type": "expense", "categoryId": "c_food", "accountId": "a_cash", "occurredAt": 1698427200000, "title": "午饭", "remark": "", "confidence": 0.95}, {"amountCents": 2800, "type": "expense", "categoryId": "c_transport", "accountId": "a_cash", "occurredAt": 1698427200000, "title": "打车", "remark": "", "confidence": 0.9}] |
| 12块5 买咖啡 | 3/6 | 14.8s | 76 | [{"amountCents": 1290, "type": "expense", "categoryId": "c_food", "accountId": "a_wx", "occurredAt": 1699873600000, "title": "咖啡", "remark": "", "confidence": 0.95}]  << amounts got=[1290] want=[1250] |

**小计**：83.3%（30/36）· 平均 17.5s · 平均 101 tokens · 完全正确 1/5

## `deepseek-r1:7b`

| 用例 | 得分 | 耗时 | 输出 tokens | 解析结果 / 问题 |
|---|---|---|---|---|
| 昨天午饭 35 块 微信 | 7/9 | 80.1s | 421 | [{"amountCents": 3500, "type": "expense", "categoryId": "c_food", "accountId": "a_wx", "occurredAt": 1675401600000, "title": "昨天午饭支付", "remark": "", "confidence": 0.8}]  << needed tolerant extraction (fence/thinking wrapper); date got=2023-02-03 want=2026-09-27 |
| 收到工资 12000 | 5/8 | 75.1s | 450 | [{"amountCents": 1200000, "type": "income", "categoryId": "c_salary", "accountId": "a_wx", "occurredAt": 0, "title": "收到工资", "remark": "", "confidence": 1}]  << needed tolerant extraction (fence/thinking wrapper); date missing/unparseable raw=0 |
| 上周三打车 28.5 | 6/8 | 58.8s | 362 | [{"amountCents": 2850, "type": "expense", "categoryId": "c_transport", "accountId": "a_wx", "occurredAt": 1735454400000, "title": "打车费用", "remark": "", "confidence": 0.8}]  << needed tolerant extraction (fence/thinking wrapper); date got=2024-12-29 want=2026-09-23 |
| 午饭35 打车28 | 4/5 | 65.1s | 387 | [{"amountCents": 3500, "type": "expense", "categoryId": "c_food", "accountId": "a_cash", "occurredAt": 0, "title": "午饭", "remark": "", "confidence": 1}, {"amountCents": 2800, "type": "expense", "categoryId": "c_transport", "accountId": "a_cash", "occurredAt": 0, "title": "打车", "remark": "", "confidence": 1}]  << needed tolerant extraction (fence/thinking wrapper) |
| 12块5 买咖啡 | 5/6 | 66.6s | 396 | [{"amountCents": 1250, "type": "expense", "categoryId": "c_food", "accountId": "a_wx", "occurredAt": 1675332000000, "title": "买咖啡", "remark": "", "confidence": 0.8}]  << needed tolerant extraction (fence/thinking wrapper) |

**小计**：75.0%（27/36）· 平均 69.1s · 平均 403 tokens · 完全正确 0/5

## `qwen3-14b-q4km:latest`

| 用例 | 得分 | 耗时 | 输出 tokens | 解析结果 / 问题 |
|---|---|---|---|---|
| 昨天午饭 35 块 微信 | 8/9 | 119.3s | 326 | [{"amountCents": 3500, "type": "expense", "categoryId": "c_food", "accountId": "a_wx", "occurredAt": 1764384000000, "title": "午饭", "confidence": 1}]  << date got=2025-11-29 want=2026-09-27 |
| 收到工资 12000 | 7/8 | 210.3s | 673 | [{"amountCents": 1200000, "type": "income", "categoryId": "c_salary", "accountId": "a_card", "occurredAt": 1764355200000, "title": "收到工资", "remark": "", "confidence": 1.0}]  << date got=2025-11-28 want=2026-09-28 |
| 上周三打车 28.5 | 7/8 | 170.6s | 557 | [{"amountCents": 2850, "type": "expense", "categoryId": "c_transport", "accountId": "a_card", "occurredAt": 1785312000000, "title": "打车", "remark": "", "confidence": 0.7}]  << date got=2026-07-29 want=2026-09-23 |
| 午饭35 打车28 | 5/5 | 118.3s | 377 | [{"amountCents": 3500, "type": "expense", "categoryId": "c_food", "accountId": "a_cash", "occurredAt": 1764326400000, "title": "午饭", "remark": "", "confidence": 0.9}, {"amountCents": 2800, "type": "expense", "categoryId": "c_transport", "accountId": "a_cash", "occurredAt": 1764326400000, "title": "打车", "remark": "", "confidence": 0.9}] |
| 12块5 买咖啡 | 6/6 | 118.6s | 378 | [{"amountCents": 1250, "type": "expense", "categoryId": "c_food", "accountId": "a_wx", "occurredAt": 1764355200000, "title": "买咖啡", "remark": "", "confidence": 0.8}] |

**小计**：91.7%（33/36）· 平均 147.4s · 平均 462 tokens · 完全正确 2/5

## `qwen3:30b-a3b`

| 用例 | 得分 | 耗时 | 输出 tokens | 解析结果 / 问题 |
|---|---|---|---|---|
| 昨天午饭 35 块 微信 | 8/9 | 211.6s | 56 | [{"amountCents": 3500, "type": "expense", "categoryId": "c_food", "accountId": "a_wx", "occurredAt": 1764201600000, "title": "午饭", "remark": "", "confidence": 1.0}]  << date got=2025-11-27 want=2026-09-27 |
| 收到工资 12000 | - | - | - | ERROR: timeout('timed out') |
| 上周三打车 28.5 | - | - | - | ERROR: timeout('timed out') |
| 午饭35 打车28 | 5/5 | 294.2s | 113 | [{"amountCents": 3500, "type": "expense", "categoryId": "c_food", "accountId": "a_cash", "occurredAt": 1785408000000, "title": "午饭", "remark": "", "confidence": 0.9}, {"amountCents": 2800, "type": "expense", "categoryId": "c_transport", "accountId": "a_wx", "occurredAt": 1785408000000, "title": "打车", "remark": "", "confidence": 0.9}] |
| 12块5 买咖啡 | - | - | - | ERROR: timeout('timed out') |

**小计**：92.9%（13/14）· 平均 252.9s · 平均 84 tokens · 完全正确 1/5

## 汇总

| 模型 | 得分率 | 平均耗时 | 平均 tokens | 完全正确用例 |
|---|---|---|---|---|
| `qwen3:30b-a3b` | 92.9% (13/14) | 252.9s | 84 | 1/5 |
| `qwen3-14b-q4km:latest` | 91.7% (33/36) | 147.4s | 462 | 2/5 |
| `gemma3:4b` | 83.3% (30/36) | 17.5s | 101 | 1/5 |
| `deepseek-r1:7b` | 75.0% (27/36) | 69.1s | 403 | 0/5 |
| `deepseek-r1:1.5b` | 13.9% (5/36) | 21.1s | 447 | 0/5 |

