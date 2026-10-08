<!-- TRELLIS:START -->
# Trellis Instructions

These instructions are for AI assistants working in this project.

This project is managed by Trellis. The working knowledge you need lives under `.trellis/`:

- `.trellis/workflow.md` — development phases, when to create tasks, skill routing
- `.trellis/spec/` — package- and layer-scoped coding guidelines (read before writing code in a given layer)
- `.trellis/workspace/` — per-developer journals and session traces
- `.trellis/tasks/` — active and archived tasks (PRDs, research, jsonl context)

If a Trellis command is available on your platform (e.g. `/trellis:finish-work`, `/trellis:continue`), prefer it over manual steps. Not every platform exposes every command.

If you're using Codex or another agent-capable tool, additional project-scoped helpers may live in:
- `.agents/skills/` — reusable Trellis skills
- `.codex/agents/` — optional custom subagents

Managed by Trellis. Edits outside this block are preserved; edits inside may be overwritten by a future `trellis update`.

<!-- TRELLIS:END -->

## 用户偏好（持续有效）

- 手机通过 ADB 连接时，构建/交付新版后直接用 `adb -s <真机序列号> install -r <最新 Release APK>` 覆盖安装到手机，无需再次询问，也不要只给安装包让用户手动安装。保留应用数据，禁止通过卸载重装处理安装失败。
- 同时连接模拟器与真机时明确选择真机；多台真机且目标不明确时再确认设备。安装后核验结果。
- 真机页面由用户操作；安装授权不代表可以自动点击或切换页面。
