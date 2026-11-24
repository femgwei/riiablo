# 德鲁伊 Raven(221) 下一批验证基线

> 本文为早期记录；等级 5/8/20 的旧数量结论已撤回，`EXHAUSTED` 实现与 Gradle 阻断描述已过时。以 [Raven 生命周期修复](druid-raven-lifecycle-fix-2026-11-22.md) 为准：已有效采集原版等级 1/5 的 12/16 次攻击尝试及 DT/DD，并修复 Riiablo 对应生命周期。

本轮停止继续打磨 Hurricane/Armageddon，切换到德鲁伊召唤族的 Raven。先完成生产代码和数据基线回归，再用后台原版采集补齐运行证据。

## 原版表驱动边界

Raven 使用 `SrvDoFunc=114`，召唤 `druidhawk`，`PetType=raven`，`PetMax=min(level, Param2)`，Param2=5，`Calc2=ulvl + Param1 + lvl`，Param1=-2。Param5=12，Param6=1，表示基础啄击预算为 12、随技能等级每级增加 1。技能本身没有 Missile 或 Overlay 行；可见行为来自召唤单位的 COF/MonStats/AI，而不是技能导弹。

## 当前 Riiablo 实现

- `ServerSkillSystem.spawnDruidSummon` 已按 114/115/119 共用召唤入口。
- Raven 的召唤数量、`Calc2` 基础等级、PetType 和所属者由 `DruidSummonIntegrationTest` 覆盖。
- `Raven` AI 保存有限 `pecks`，初始化为 `12 + raven level - 1`；每次普通近战攻击消耗一次，耗尽后进入 `EXHAUSTED`，不重新补充。
- `SummonedPetSystem` 负责主人死亡、主人缺失、跨区域、死亡展示和清理；Raven PetType 不支持 Unsummon 时采用硬删除语义。

## 本轮验证

通过的离线测试：

- `DruidSummonIntegrationTest`：11 项，包括所有 10 个德鲁伊召唤入口、Raven Calc2 等级、灵体光环、跨区域及快照恢复。
- `NativeDruidSummonDataTest`：2 项，确认表行、MonStats、PetType 和最大数量公式。
- `AiRuntimeStateTest`：扩展 AI 状态与断点恢复回归全部通过。

日志：`tools/original-observer/druid-raven-next-validation-2026-11-22.log`。这些是本地 Riiablo 生产测试，不是原版运行证据。

## 原版后台首个运行证据（2026-11-22）

已使用常驻 elevated broker 在独立、非输入桌面运行 `D2v110各版本免CD全功能启动.exe -w -res800`，没有切换桌面、没有全局输入、没有写入原版进程内存，也没有附加调试器。调度器自动进入 Blood Moor 后通过 Raven 的 F1 右手热键向私有窗口发送一次右键消息，采集器只读轮询 D2Client 的单位哈希表。

运行记录：等级 1 为 `tools/original-observer/broker/20261121-165133-4490d598/runs/20261122-190639-78d59e0a/orchestrator/orchestrator.json`，等级 5 为 `tools/original-observer/broker/20261121-165133-4490d598/runs/20261122-192036-8e069428/orchestrator/orchestrator.json`，等级 8/20 为批次 `tools/original-observer/broker/20261121-165133-4490d598/runs/20261122-194129-23ad07b3`；可复用样本为 `core/src/test/resources/original/druid-raven-1.10f.json`、`druid-raven-level5-1.10f.json`、`druid-raven-level8-1.10f.json` 和 `druid-raven-level20-1.10f.json`。

本轮采集结果：在重新生成的干净 `CXDruRaven` 存档中，施法前有场景单位，施法后新增 1 个 `type=1` 单位，`class_id=419`，与 `MonStats.druidhawk.hcIdx=419` 一致；采集到 502 个 Raven 样本，客户端模式出现 `8/1/2` 三段，动画计数随模式变化，末段模式 2 的动画速率为 156。Raven 的单位 owner 字段在这个客户端布局中读到 0，不能把该字段当作归属证据；本轮归属依据是干净存档、施法后新 unit key 以及 class 映射。游戏进程正常关闭，私有桌面从未成为输入桌面。

这证明了 Riiablo 的 Raven 召唤目标、单位类型和 class 映射可以与 1.10F 的后台运行证据对齐，也首次拿到了客户端的模式/动画字段序列；它还没有证明啄击次数、攻击动画、死亡/换图清理或伤害结算。下一步应保持同一私有桌面流程，扩展到技能等级 8/20 和带可控目标的活动采集，记录 Raven 的攻击模式、单位生命周期和耗尽行为。

等级 5 的五次施法在间隔 0.9 秒后接受了 5 个新单位，施法前后单位数差为 5，和 `PetMax=min(level,5)` 一致。快速连续点击只接受 2 次，说明原版存在施法动作/节流窗口，采集器必须等待动作完成后再发下一次输入。

等级 8 和等级 20 在同样的 5 次、0.9 秒间隔输入下也各自新增 5 个单位，单位差均为 5。之前样本中的模式 `12/1/2` 应解释为 `DD/NU/WL`，其中 12 是死亡模式，不能当作攻击；怪物 `A1` 的正确模式值是 4。因此等级 5/8/20 的召唤数量上限已经有后台运行证据支持，尚未覆盖完整啄击预算和耗尽行为。

本轮随后接入了受控目标 harness：原版在 `CXDruRaven` 房间生成一个 `class_id=19` Fallen，恢复自然运行 1040 帧；Raven 只读采集到模式 `1/2/4/8`，其中模式 4 是 A1，并在 16 秒窗口内记录到 4 次 A1 进入。该结果证明 Raven 已经选中受控目标并进入攻击动画阶段，但仍不是伤害或啄击结算计数。

已补充 Riiablo 确定性测试 `RavenAiIntegrationTest`：等级 1 目标近战场景应完成 12 次攻击后进入 `EXHAUSTED`，等级 5 应完成 16 次攻击后进入 `EXHAUSTED`。该测试覆盖 `Param5 + skillLevel - Param6` 的运行时预算，独立于客户端窗口采集；Gradle 当前仍受环境里的 `native-platform.dll` 启动错误影响，尚未实际执行该新增 Java 测试。

原版受控目标生成方案见 `docs/druid-raven-target-harness-analysis-2026-11-22.md`；生成阶段使用调试器和远程调用，Raven 观察阶段仍是只读 collector。受控目标样本已固化为 `core/src/test/resources/original/druid-raven-target-1.10f.json`。

## 下一次原版后台采集范围

建立只学习 Raven(221) 的 `CXDruRaven` 裸装存档，进入 Blood Moor 后一次采集：

1. 创建数量上限：技能等级 1、5、8、20 分开验证 `min(level,5)`。
2. 召唤单位：MonStats class、PetType、ownerId、出生坐标和基础等级。
3. AI：主人跟随、目标选择、首次攻击模式、12+level-1 啄击计数及耗尽后的状态。
4. 清理：主人死亡、跨 RoomEx、离开场景、Raven 耗尽和重新施法是否替换旧单位。
5. 客户端：Raven 的 COF token、方向、攻击/NU/DT/死亡模式和资源加载；不把技能导弹数量误当作特效。

当前未宣称 Raven 与原版完整一致，也未增加德鲁伊完成计数；需要补齐数量、AI、清理和回放后再更新 manifest。
