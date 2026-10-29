# Codex 交接记录（2026-10-29）

## 当前仓库

- 路径：`F:\3rd_src\riiablo`
- 分支：`master`
- 远端：`origin/master`
- 工作区：本批文档提交后应保持干净。

## 最近已推送提交

- `6409267e8`：D2SWriter96 测试初始化 `Riiablo.files`。
- `0858ac56c`：MapTourProbe 纯 flags 夹具显式使用 legacy collision policy。
- `12d48d153`：移植 D2MOO tile class `-1` 的 `DRLGLOGIC_SetTileGridFlags` 修复。
- `1bd298cee`：锁定 NativeMonStats 负 spawnx/spawny 解析回归。
- `c87efb674`：记录 Charged 技能最高等级修复的 1.11+ 版本边界。

## 验证状态

- 完整 `:server:d2gs:test`：151 项全部通过。
- `D2SWriter96HeaderTest`：通过。
- `NativeMonStatsTest`：通过。
- `:D2MOO_JAVA:compileJava`：通过。
- `git diff --check`：通过。
- 完整 `:core:test` 尚未收口；用户已明确本阶段不继续处理 core 历史资源/旧期望问题。

## 上游审计结论

- D2MOO 最新 tile class `-1` 修复已移植。
- D2MOO Charged 技能最高等级修复限定 1.11+，当前 1.10f 不移植。
- D2MOO `8075cae5` 的有符号 MonStats/Hireling 字段已确认 Java 文本解析链路可保留负值；已加入 spawn offset 回归。
- dark-magic 最新 Den warp、Cold Plains–Stony Field seam、Stony/Den population、Death Sentry exclusive roll 多数已有等价实现；1.14d 数据/规则不直接覆盖 1.10f。
- 详细列表：`docs/upstream-update-audit-2026-10-29.md`

## 下一任务

对照 D2MOO `eea008b8` 的 Quill Rat spread seed 修复，检查 riiablo Quill Rat AI 的单位级 RNG、扩散方向和 seed 消耗顺序；先补确定性回归，再决定是否改生产代码。暗黑规则不确定时优先查 D2MOO，再查 The Phrozen Keep。

## 资源与约束

- 原版 1.10f：`G:\BaiduNetdiskDownload\Diablo II 1.10F`
- 用户要求：代码变更后测试、`git diff --check`、提交并推送；极小修改可不编译但仍需推送。
- 不要把 D2GS fixture 的全局 seed 兼容边界强行改成局部 RNG。
