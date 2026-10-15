# Succubus Fn118 — PARTIAL

依据 D2MOO `AITHINK_Fn118_Succubus`（AiThink.cpp，0x6FCE0CD0），
`UNITS_GetCurrentLifePercentage` 和 `SKILLS_SrvDo030_Curse`。
七个真实 MonStats 行接入专用 Succubus AI：诅咒优先级、生命阈值、严格距离门、
近战概率/等待、Skill5 使用 slot7、目标连续性及接近/等待顺序。
生命百分比先分别截断当前/最大生命，再做整数除法。

真实七行 Skill1/2 为空；定向测试在脱离全局表的 MonStats 副本上使用真实
Amplify Damage/Weaken 技能行覆盖原生休眠分支。自然场不修改数据表或概率，
不强制 SkillDo，不关闭碰撞。

## 修前、修后证据

- `baseline-init-failure.log`：初次隔离 init 文件未复制到快照，Gradle 启动失败，
  不计行为测试。
- `baseline-initial-tests.log` / `baseline-initial.xml`：最初测试错误假设真实 Skill1/2
  非空，10 项失败；不将这组全部当作有效行为基线。
- 更正副本 fixture 后 `baseline-tests.log` / `baseline.xml`：10 项、8 失败、2 通过。
  `d0-baseline*`：14 场使用 GenericMonster fallback。
- `fixed-tests.log` 和早期 75 项：专用 AI 实现首次通过。
  `d[0-2]-first/repeat*`：AI-only 阶段84次执行，保留原始行为；
  当时 Defense Curse 技能事件不代表状态已应用。
- `curse-baseline-tests.log` / `curse-baseline.xml`：两项真实 Skills/States 行测试均失败，
  证实状态解析漏掉 Defense Curse/Blood Mana。
- `curse-fixed-tests.log` / `curse-mapping-only.xml`：仅补映射仍失败，诅咒掩码未识别两状态。
- `final-tests.log` / `curse-projection-test-failure.log`：进一步发现真实护甲属性尚未解析；
  最初用兼容字段观测，随后改为检查真实 stat-list 项。
  `final-verified-tests.log`：直接检查真实项仍失败，排除仅兼容投影问题。
- 最终补齐两状态映射、诅咒分类/掩码和护甲属性解析。
  `final-verified2-tests.log` 与 `TEST-*.xml` 保存最终定向/相关回归结果。
  `d[0-2]-fixed-first/fixed-repeat*` 是最终自然矩阵；不覆盖此前记录。

自然参数：真实1.10F MPQ，seed=1，每场500 tick，七行×近远×三难度×独立复跑，
最终84次执行。每轮原始 journal 1180条，其中1166条 NOT_RUN，不能计为通过。
场景日志新增只读 `SuccubusCurse`，检查实际目标 state、来源、技能、诅咒掩码、
持续时间和护甲 stat-list 值；保持 `BASELINE_ONLY`，不放宽原有专属 oracle。
`verify.ps1` 核对两阶段 JSONL 复跑、最终逐场日志复跑、实际状态观测、
动作数、告警/错误、未选场计数及源码快照一致性。
具体统计见 `verification.log`，原始逐场日志在对应 `*-scenes.zip` 中。
最终86项测试全部通过；84次最终自然执行累计188次真实诅咒状态观测，
所有观测均为Defense Curse且护甲百分比为负；每场都有实际伤害，
累计75844HP（含独立复跑，不能当作独立覆盖量）。
三难度最终JSONL与逐场日志复跑一致，零fallback/警告/错误。

## 可复现性

共享工作区有其他聊天的佣兵/UI/寻路修改；本批使用
`source-base-sha.txt` 中固定提交的源码快照，仅覆盖本批七个生产/测试文件。
`prepare-source.ps1` 创建修前快照，`-Repaired` 覆盖最终实现。
`isolated.init.gradle` 隔离 buildDir、JavaCompile/sourceSet 输出；Gradle project cache
也位于快照内。`run-arena.ps1` 拒绝覆盖既存输出。
测试选择为 `*Succubus*Test`、`*NecromancerCurse*Test`、Act1NativeAi、DeathMauler、
HellMeteor、`*MinionAiIntegrationTest`、Spirit、TrappedSoul。
这不是共享工作区完整集成或项目全量测试结果。

## 未验收

- Blood Mana 状态可应用，不代表其后续施法消耗生命逻辑完成；该语义本批未实现。
- 自然夹具主要覆盖 Defense Curse；玩家生命/法力配置未构成 Blood Mana 专用自然场。
- 原版无目标移动/全局调度尚未建模，当前无目标等待 slot5。
- `hasCurse` 为当前状态掩码近似，不等价于任意原生 flag0x20 stat-list。
- 原生共享单位 RNG、全局事件/动画动作点、真实地形/门/房间、GL/TCP、多seed、
  其他聊天未提交修改的联合验证仍待；整体保持 PARTIAL。
