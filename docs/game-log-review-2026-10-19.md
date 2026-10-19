# 2026-10-19 game.log 后续核查

输入：用户提供的桌面 game.log，20740 行；日志作为运行证据使用。

## 技能警告

- `Actioneer` 的 `srvdofunc(101)` 13 次不是导弹未生成：同次施法随后有
  `ServerSkillSystem phase=missile`，skill 196 / FingerMageSpider，并有碰撞音效记录。
  D2MOO `SkillMonst.cpp::SKILLS_SrvDo101_FingerMageSpider` 创建 `srvMissileA`，
  当前由 `SkillDoEvent` 的服务端导弹订阅者执行；补充 Actioneer 显式委托分支。
  原生 nLoops 是否影响寿命还要看创建 flags：此函数 flags=3，而 Missiles.cpp
  仅在 flags&8 时把 nLoops 加入 range，不能直接把 calc1 当作额外导弹数量。
- 客户端 `srvstfunc(49)`、`srvstfunc(53)` 分别为 Nest 和 MonInferno，服务端
  Actioneer 已有实现。客户端显式复用服务端状态；Nest 的 `cltdofunc(50)`
  复用权威生成的怪物。保持已有声音、overlay 和快照路径。
- 上述修复明确分发责任，不表示这些技能的所有原版分支已经完成验收。

## 传送点与 Warp

- D2MOO `DrlgOutDesr.cpp` 对 Dry Hills / Far Oasis / Lost City 调用
  `DRLGOUTDOORS_SpawnAct12Waypoint`。原生 RoomEx 导出之后，旧 preset-grid
  placer 又尝试放置，出现三条 Failed to place waypoint 调试信息。
  原生拓扑现在跳过重复放置，非原生兼容布局继续使用旧放置器。
- `fallback=UNIT_TILE` 是 DrlgRoomTile 在 RoomTile 暂未连接时创建入口单位的
  路径；日志随后报告 `missingTargets=0`、`missingReverse=0`。
  不应仅因出现 fallback 就删除现有入口恢复逻辑。
- `unitTileWarp=2/4` 表示导出的四条 UNIT_TILE 中两条解析到了运行时 Vis/Warp
  slot，不能解释成四个入口都已通过。仍需对实际入口位置、碰撞和双向穿越做实机验收。

## 其他信息

Java 内存分配回退、跳过 level 0、PowerShell 包装编译器 stderr 均没有导致本次
运行失败；原日志有角色与 Automap 保存证据。没有依据修改战斗公式或存档格式。

## 验证结果

- 使用本机 1.10f MPQ，8 套定向测试共 77 项、零失败：NestSwarmIntegrationTest、
  MonInfernoIntegrationTest、NativeMonInfernoDataTest、NativeWaypointResourceTest、
  Act2MapBuilderD2MooWarpTest、Act2SewerWarpPairingTest、NativeWarpSlotResolverTest、
  MonsterBehaviorScenarioIntegrationTest。
- NativeWaypointResourceTest 覆盖日志地图种子 127897359 的 A2-A5 全部 30 个
  传送点目标原生房间，以及延迟房间传送点实例化；不替代实机点击和穿越验收。
- `:desktop:offscreenCamp -Pd2Version=1.10f` 通过；输出
  `desktop/build/visual-tests/log-review-20261019/rogue-encampment-manifest.txt`
  的 `result=PASS`，客户端关闭清理 PASS。
- 第一轮并发 Gradle 验证出现 Skills 类加载失败；编译完成后复用稳定输出重跑通过。
- `git diff --check` 通过。另一台电脑仍需复验 A4 Nest/Inferno/FingerMage 表现、
  A2 三处传送点和实际入口双向穿越。
