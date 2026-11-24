# 毁天灭地服务端周期落点核对

本轮继续核对已经完成的飓风/毁天灭地批次，没有启动原版进程。依据是固定版本 D2MOO 的 1.10f 兼容实现、现有 Riiablo 服务端代码以及已有后台客户端采集。

## 原版流程

`SKILLS_SrvDo124_Armageddon_Hurricane` 只负责安装或刷新 aura state，并安排 `Param4` 帧周期事件。毁天灭地的 state 行 `srvactivefunc=146`，对应 `SKILLS_SrvDo146_PeriodicRandomMissile`：

1. 检查 aura 仍存在、技能等级有效且不在城镇。
2. 读取 aura 范围；毁天灭地表中为 `Param3=8`。
3. 以施法者技能种子为随机流，最多五次生成 `x/y`：施法者整数坐标加上 `[0,2*range]` 的随机值再减去 range。
4. 通过径向导弹屏障检查和房间有效性后，立即生成一个 `armageddoncontrol`。没有有效格子时，本周期不生成。
5. 控制导弹的 `SrvHit56` 在它的位置执行范围伤害，并创建 `armageddonfire`。命中区域不是最近怪物坐标。

静态依据：`D2MOO/source/D2Game/src/SKILLS/SkillDruid.cpp` 的 `SKILLS_SrvDo124_Armageddon_Hurricane`、`SKILLS_SrvDo146_PeriodicRandomMissile`，以及 `MISSILES/MissMode.cpp` 的 `MISSMODE_SrvHit56_ArmageddonControl`。本地代码没有把 D2MOO 源码当作运行时证据；它只用于解释固定 1.10f 行为。

## 发现的 Riiablo 差异与修复

此前 `StateUpdater.processDruidStorm` 对两种技能使用同一逻辑：先找范围内最近敌人，再把周期导弹放到最近敌人的位置。这与毁天灭地的随机落点明确不同，也会让客户端已修正的下落画面继续集中到错误位置。

现在 Armageddon 分支：

- 最多尝试五个随机格。
- 有真实地图区域时要求坐标属于区域且不带 `FLAG_BLOCK_JUMP`；没有加载地图的离线测试地图保持兼容。
- 首个有效格作为 `armageddoncontrol` 的位置。
- Hurricane 现有周期路径暂时保持，后续单独改为原版 `SrvActive145` 的范围伤害脉冲。

本轮又把 Hurricane 的有目标周期改为真正的范围伤害脉冲：使用技能快照的物理/冷伤害范围，对范围内每个敌人分别执行抗性、穿透、吸收和死亡结算，不创建每目标导弹。没有目标的空房间仍保留短期载体用于网络空场景测试。

现在 `UnitState.runtimeValue` 保存 Armageddon 的 Param1 等价低字：施法时保存施法者种子推进后的低 16 位，周期时用它初始化 `D2Seed(low,666)`，先推进一次并按原版取模 1,000,000 写回下一 Param1，再消耗坐标随机数。该值随状态快照和检查点保存。当前角色对象仍没有暴露原版 D2Unit 的真实 pSeed 初始值，因此初始 pSeed 继续使用服务器确定性种子派生，不能声称从真实原版角色种子逐次相同。

新增静态输出 `tools/original-observer/druid-storm-server-static-1.10f-2026-11-22.txt`：固定 D2Game.dll（SHA256 `2bd92fb25e3e4b8269930712620ba9db00cdc1aa44569d87dc4aba76553272b6`）的 SrvDo124 位于 RVA `0xCF870`，施法时可见 pUnit seed 推进和 `and eax,0xffff` 的低 16 位 Param1；SrvActive146 位于 RVA `0xCFAB0`，与 D2MOO 源码的 Param1 重置、`%1000000` 和五次坐标尝试对应。该输出是离线 Capstone 静态事实，不是运行时调用栈。

## 验证

新增 `DruidStormAuraIntegrationTest.armageddonPulseUsesRandomCellAttemptsInsteadOfNearestTargetPosition`，验证周期会生成毁天灭地控制导弹，位置落在施法者半径 8 内；原有五项 aura/快照/伤害测试继续通过。日志为 `tools/original-observer/druid-storm-server-validation-2026-11-22.log`，本轮没有原版进程或前台窗口。

已新增离线边界回归：地图存在但五次候选格都不属于已加载房间时不生成控制导弹。`DruidStormAuraIntegrationTest` 8 项全部通过。真实地图跨房间运行仍需后台采集验证；初始 pUnit seed 的角色来源仍需从运行时技能对象进一步定位。
