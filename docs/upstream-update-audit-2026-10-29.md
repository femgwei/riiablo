# D2MOO / dark-magic 上游更新审计（2026-10-29）

## 已移植

- D2MOO `6728113f`：环境周期在 Act IV eclipse 模式下切换时，边界快照继续使用
  Act IV 周期表；riiablo `EnvironmentCycle` 已修正并加入确定性回归。

- D2MOO `5c2b7095` / `4f032990`：`DRLGLOGIC_SetTileGridFlags` 对 tile class `-1`
  的有定义处理。`D2MOO_JAVA` 现在在初始调用时只标记无方向墙自身，方向洪泛不穿墙；
  同时保留越界索引保护。

## 值得参考、但不直接覆盖当前 1.10f Java 数值

- D2MOO `8075cae5`：`MonStats` spawn 坐标/索引改为有符号字段，佣兵生命与边界计算使用
  有符号整数；适合继续审计 `NativeHirelingStatsUpdater` 与 spawn 坐标解码。
- D2MOO `55f1f494`：Charged 类技能最高等级查找；需要按当前 1.10f `Skills.txt` 行和
  `SkillCalc` 语义核对后再移植。该提交的行为开关明确是 1.11+；riiablo 当前目标为
  1.10f，且没有对应的 1.11+ 最高等级查找路径，因此本批不改生产代码，避免错误跳过
  1.10f Charged 技能。
- D2MOO `eea008b8`：Quill Rat spread seed；可作为 AI 单位 RNG 顺序回归依据。
- D2MOO `e440a0a5`：Unholy Bolt missile range；只在确认 1.10f missile 行一致后采用。

## dark-magic 最新主线可复用内容

- `1f67eda0` / `dd7d9c35` / `0540d502` / `361b1e17`：Den of Evil warp、Cold Plains–Stony
  Field seam、Stony Field/Den population 与真实回归测试结构。riiablo 已有对应 Act1
  Den、warp、population 和 headless quest gate；可继续借鉴测试分层与断线恢复断言。
- `1f06540c`：Death Sentry 尸体伤害使用 exclusive upper bound。riiablo 当前
  `AssassinTrapSystem` 已使用 `maximum - minimum + 1`，无需重复覆盖。
- `7e700324`：connected resistances；属于 dark-magic 1.14d 数据/规则，不能直接替换
  1.10f 原版抗性表，只可移植测试结构。

## 暂不移植

- dark-magic 的 G14 world/entryworld 及 1.14d 数据层改动；当前 Java 地图桥接已按 D2MOO
  1.10f 语义实现，直接覆盖会引入版本混用。
