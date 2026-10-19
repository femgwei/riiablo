# 怪物全量审计失败项修复

## 范围与原版依据

起因是 `0713ff267` 基线的怪物分层聚合及相关专项扩测：308 类、2001 个去重用例，
9 个不同失败，零跳过。本轮基于 `7dcdaa836` 修复，不修改无关的城镇复活逻辑。

- **房间休眠**：AIStepper 的跨 Zone 接缝回退调用 `Map.areZonesAdjacent`，而该函数
  对同一 Zone 返回 true，导致本区域内隔多个 RoomEx 的玩家也能唤醒怪物。
  回退现只允许同一 Map 内的不同相邻 Zone；本 Zone 仍以房间引用状态为准。
  依据 D2MOO `DrlgActivate.cpp / DRLGACTIVATE_ChangeClientRoom` 的房间状态传播。
  保留原有接缝兼容行为，不宣称已实现跨关卡的完整原生 RoomEx 邻接拓扑。
- **扼杀者空参数**：合成/不完整 Missiles 行的 Param 可为 null 或短数组。
  追踪修正只在所需参数可读时执行；缺参数不凭空指定追踪距离，继续原有碰撞/寿命处理。
  真实参数行的追踪间隔、方向和距离计算不变。
  依据 D2MOO `MissMode.cpp / MISSMODE_SrvDo11_FingerMageSpider`；原版表为定长参数数组，
  此防护解决 Java 合成行的兼容性，不代表发现原版 MPQ 会触发该空指针。
- **怪物 Charge 命中**：进一步核对 D2MOO `SkillPal.cpp / SKILLS_SrvDo067_Charge` 后，
  不能将整批失败仅归为随机测试波动。怪物分支直接设置 `wResultFlags=9`
  （SUCCESSFULHIT | KNOCKBACK）；玩家分支才调用 `SUNITDMG_GetResultFlags`。
  `SUnitDmg.cpp / SUNITDMG_AllocCombat` 消费已有标志，不重新抽取 AR/格挡/闪避。
  新增显式的预设命中策略，仅由怪物 Charge 使用；不把所有 alwaysHit 攻击改成无视防御。
  伤害、抗性与结算仍走现有共享链路。本轮不是整个 Charge 状态机、击退及特效的完整移植。
- **无资源寻路夹具**：没有有效 skillId 的 Casting 不是 Charge，不应读取全局 Skills。
  在查表前拒绝负 skillId，避免测试因前一个资源测试残留的全局数据而偶然通过。

## 测试与门禁修正

- Amazon 箭矢、Blessed Hammer、Fist of Heavens 的表现夹具显式注册 clientContext；
  Fallen 动画夹具注册 AnimStepper 实际依赖的 CofManager，不将缺依赖测试改为跳过。
- 原绕墙失败的最高浮点 Y 为 `15.999845`，实际上已绕过墙端。
  按 Map.round 的权威碰撞网格检查净空，并额外要求确实穿过墙列、所有穿越位置
  均能容纳 size-three 体型；原有逐帧地形/动态占位检查、到达另一侧和接近目标断言均保留。
- 扩测相关整类时发现 Blessed Hammer 测试把原版 dParam 当成 50/50。
  本机 1.10f 实际为 50/0；用真实表单独验证亡灵加成和缺失的恶魔加成，
  用独立合成 50/30 行验证 SrvDmg05 两次加成可叠加，不修改共享资源或生产伤害实现。
- Charge 回归固定并恢复测试局部 RNG，分别使用上下端点，叠加极高护甲和被动防御，
  验证只在动作点结算一次实际伤害。数值层另验证没有命中/防御随机消费，抗性仍生效。
- `monsterPursuitTest` 纳入全部 Pathfinder*Test 和 MonsterRoomActivationTest；
  `monsterMissileTest` 纳入 FingerMage*Test；`monsterDataTest` 纳入近战动画回归。
  因此普通 `monsterCombatAcceptance` 不再漏掉本次对应的寻路、扼杀者和动画专项。

## 复验入口

2026-10-20 使用本机原版 1.10f 资源完成复验：

- 相关 10 类分别在独立 JVM 中执行，123 项通过，失败/错误/跳过均为 0。
- 最终扩大聚合覆盖 310 类、2039 个去重用例；各层共 3800 项结果，失败/错误/跳过均为 0。
  原来 9 个失败和额外发现的寻路隔离问题均通过；追加专项 `core:test` 为 1514 项通过。
- D2MOO 严格来源审计 97/97；算术层 271/271，server-domain 普通层 287/287。
  怪物追击层现为 82 项、导弹层 200 项、数据/动画层 45 项，均通过。
- 独立构建目录为 `.gradle/monster-audit-fixes-20261020`；全量日志
  `.gradle/monster-audit-fixes-full.log`，最终聚合耗时 19 分 28 秒。
  本地临时 init script 仅隔离构建产物和设置隔离复查，不修改用例断言；
  聚合复验复用已生成源码，未重复生成 FlatBuffers/版本源码。
- 构建模块边界门禁和 `git diff --check` 通过；复验前后的代码 SHA256 摘要一致。

使用原版 1.10f 安装资源。先可独立运行无资源寻路测试，确认没有其他资源测试的前置依赖：

```powershell
.\gradlew.bat :core:test --tests '*PathfinderRecoveryTargetTest' --no-daemon --max-workers=1
```

完整怪物分层、独立数值审计与相关专项：

```powershell
.\gradlew.bat :core:monsterCombatAcceptance :server-systems:test :core:test `
  --tests '*Monster*' --tests 'com.riiablo.engine.server.ai.*' `
  --tests '*Pathfinder*' --tests '*Missile*' --tests '*FingerMage*' `
  --tests '*Regurgitator*' --tests '*Shaman*' --tests '*Enchanted*' `
  --tests '*CursedMonster*' --tests '*StateOverlay*' --tests '*ElementalHit*' `
  --tests '*AmazonArrowPresentationTest' --tests '*BlessedHammerIntegrationTest' `
  --tests '*FistOfHeavensIntegrationTest' --tests '*SpecialAttackSnapshotMigrationTest' `
  --tests '*PaladinMeleeSkillDataTest' `
  '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' `
  --continue --no-daemon --max-workers=1 --console=plain
```

报告中的分层执行会重复运行部分用例，不能将执行次数当作不同测试总数。
这些是自动化行为/数据测试，不启动原版 EXE，不替代另一台电脑的游戏画面对照、
扼杀者拖尾密度与命中表现验收，也不等于整个 core 或完整桌面构建。
