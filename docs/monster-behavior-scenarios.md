# 怪物行为场景回归

运行（需要原版资源；其他电脑替换路径）：

```powershell
./gradlew.bat :core:monsterBehaviorTest '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' --no-daemon
```

报告：`core/build/reports/tests/monsterBehaviorTest/index.html`。
单独迭代新增场景可追加 `--tests '*MonsterBehaviorScenarioIntegrationTest'`。

需要更快的分组执行或核对原版命中/伤害时，见 [分层入口与原版对照](monster-combat-parity.md)。
行为场景通过不等于数值与原版一致；严格源码预期审计是独立任务，不随生产实现改写预期。
一次运行所有怪物分层、审计与架构门禁可用 `:core:monsterCombatAcceptance`，仍需追加资源路径。
该门禁现包含双字种子和普通怪物 OW 的实际命中/状态/生命回归；特殊包/技能全部原版一致性与原版进程逐击记录仍不是它的完成声明。

## 结果断言，而非仅检查 AI 状态

- Zombie、Skeleton、Fallen、CorruptRogue、Goatman、Brute：相邻攻击必须产生正伤害事件并降低玩家生命；原生动画场景不注入命中事件，必须由动画动作点完成攻击。
- 同上六类：静止目标及每帧移动目标均须产生实际位移、持续速度并缩短距离；目标在攻击动作点前逃走不得受到近战伤害。
- FoulCrowNest、Sarcophagus、MaggotEgg、SandMaggotQueen、VileMother：AI 施法经过技能系统后必须创建有生命值的子实体；检查相应类型、孵化消耗、Nest 归属及标志。另测无敌人不生成、Nest 数量上限和地形阻挡拒绝生成。
- FallenShaman：远程弹体必须抵达并造成伤害；聚合入口同时包含已有复活、尸体和归属场景。
- Succubus：诅咒必须真正安装到玩家状态列表，不能只留下施法状态。
- SuicideMinion：物理、火焰、冰冷参数分支必须在死亡后第 4 帧结算；范围外和友军不受伤，后续不重复伤害，表现弹体不能二次命中。
- 聚合已有 AI 测试和特殊技能场景，包括 `RemainingMonsterSkillIntegrationTest`、`NativeExplosionLifecycleTest`、`MonsterAttackProfileIntegrationTest`、`NativeMonsterChargeTest`，及移动目标重寻路回归。

## 测试边界

新增 Scene 使用真实 AI、Actioneer、Pathfinder、速度积分、技能和弹体伤害系统，加载本机原版表格。地形查询及路径形状可控，分配器创建真实 ECS 实体，但不等于生产地图人口/碰撞分配器验收；也不包含客户端绘制、音效和联机同步。

普通功能场景主动注入动画动作点并结束施法，隔离“AI → 技能 → 实际效果”；另有独立原生动画攻击场景验证动作点产生链路。概率场景固定 RNG 并限定循环次数，特殊生成/诅咒分支适当设置概率或计时参数，避免把随机未触发误判为技能失效。并非所有怪物变体均有完整端到端覆盖。

扩展时须断言最终生命、位置、状态或创建实体，保留失败诊断中的怪物 ID、AI 状态、坐标及生命值。不要仅断言进入 ATTACK/CAST，或为通过测试取消命中/生成断言。

## 原版依据

本地 D2MOO `source/D2Game/src/AI/AiThink.cpp` 的 Fn010、Fn040、Fn043、Fn045、Fn066、Fn068、Fn117、Fn118；`SKILLS/SkillMonst.cpp` 的 SrvDo084、087、091；`MONSTER/MonsterUnique.cpp` 的 `MONSTERUNIQUE_SuicideModeChange` 和 `MONSTERUNIQUE_CastSuicideExplodeMissile`。后者决定死亡后 4 帧、属性分支和表现弹体创建条件。
