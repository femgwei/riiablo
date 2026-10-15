# riiablo 寻路交接记录

> 本文为2026-10-12历史交接，下面部分“待实现”与旧失败记录已被后续提交更新。
> 当前状态请先看 [2026-10-15寻路碰撞比较](path-collision-upstream-comparison-2026-10-15.md)
> 和 [当前交接索引](current-chat-ownership.md)，不要仅按本文旧清单再次移植或回退近期优化。

更新时间：2026-10-12

## 当前已推送提交

- `338f2fe1 Preserve native no-path blockers between monsters`
  - 修复普通怪物动态 `NO_PATH` 标记没有互相阻挡的问题。
  - Fallen、Skeleton、Zombie 等普通怪物现在会参与动态阻挡。
- `a6b8c443 Match native flying monster path mask`
  - 增加普通、可开门、飞行、玩家四种动态路径 profile。
  - 飞行单位动态层忽略 `COLLIDE_PET`，仍检测普通 `NO_PATH`。
- `a7e12c2b Match native open-door monster pathing`
  - `MonStats.opendoors=1` 的怪物可通过纯门碰撞格。
  - 普通物体仍会阻挡可开门怪物。
  - A*、路径平滑、射线/备用路径检查统一使用门规则。

## 已核实的 D2MOO 原版规则

`D2MOO/source/D2Common/src/Path/Path.cpp` 的 `PATH_AllocDynamicPath`：

- 普通怪物：`COLLIDE_MONSTER` footprint，普通 `COLLIDE_MASK_MONSTER_PATH` 移动掩码。
- 可开门怪物：`COLLIDE_MASK_MONSTER_THAT_CAN_OPEN_DOORS`，忽略 `COLLIDE_DOOR`。
- 飞行单位：`COLLIDE_MASK_FLYING_UNIT = MISSILE_BARRIER | DOOR | NO_PATH`。
- Wraith1：`SMALL_NO_PRESENCE`，移动掩码为 `MISSILE_BARRIER | DOOR`。
- 普通 presence pattern 会在中心写入 `COLLIDE_NO_PATH`；pet pattern 中心写入 `COLLIDE_PET`。

## 当前实现已覆盖

- 普通地面怪物的动态 `NO_PATH/PET` 阻挡。
- 动态阻挡后等待并重新寻路，避免狭窄通道中怪物只播放走路动画而不移动。
- 可开门怪物的门碰撞区别，覆盖 A*、平滑和直线碰撞检查。
- 飞行单位动态层的 `PET` 忽略规则。
- critter 不加入动态路径障碍；部分 Wraith/Bird/Parrot 不写入普通 presence 网格。

## 尚需细化的列表（优先级顺序）

### P0：飞行单位完整静态路径掩码

当前 `Pathfinder.java` 仍主要使用 `DT1.Tile.FLAG_BLOCK_WALK`。需要为飞行 profile 建立独立静态路径策略，使其接近原版：

- 检测 `FLAG_BLOCK_JUMP`/missile barrier 和关闭门。
- 不把普通 `COLLIDE_OBJECT`/普通地面阻挡错误当作飞行障碍。
- A*、平滑、raycast、动态步进必须使用同一 profile。
- 优先用 D2MOO 和现有 `Map.playerFlyingFlags`/对象门引用实现，不要凭经验新增规则。

### P1：Wraith1 与无 presence 单位

- 精确识别原版 `Wraith1`，不要只依赖字符串前缀。
- 使用 `SMALL_NO_PRESENCE` footprint。
- 使用 `MISSILE_BARRIER | DOOR` 移动掩码，不应沿用普通 `FLAG_BLOCK_WALK`。
- 核对 `PATH_SetUnitAliveCollision` 中 Wraith/Bird/Parrot 的复活后碰撞差异。

### P1：presence/pet 模式精确化

当前 `DynamicUnitCollisionSystem.isPetPresence` 主要依据 `MonStats2.revive/small/isAtt`，这是近似实现。需要对照 D2MOO 的 `D2Common_11281_CollisionPatternFromSize`：

- `MONSTERS_CanBeInTown`。
- `MonStats.interact`。
- `SMALL_UNIT_PRESENCE`、`BIG_UNIT_PRESENCE`、`SMALL_PET_PRESENCE`、`BIG_PET_PRESENCE`。
- 普通召唤物、复活怪物、佣兵、Valkyrie 的 owner/pet 忽略关系。

### P2：DeadCol/尸体碰撞

- 核对 `MonStats2.deadCol` 和 D2MOO `PATH_SetUnitDeadCollision`。
- 当前动态单位索引需确认是否会把死亡怪物继续当作活体 `NO_PATH/PET` 障碍。
- 分离活体 footprint 与尸体 `COLLIDE_CORPSE`，并覆盖尸体阻挡测试。

### P2：AIType 受阻恢复差异

- 当前 Pathfinder 对所有怪物使用统一 stalled repath。
- 需要继续对照 D2MOO/Phrozen Keep 资料，区分 Fallen、普通近战、飞行和其他 AIType 的受阻恢复。
- 先保证不重新引入“原地走路动画”或完全不能移动，再逐步做行为差异化。

## 关键实现文件

- `core/src/main/java/com/riiablo/engine/server/Pathfinder.java`
- `core/src/main/java/com/riiablo/engine/server/DynamicUnitCollisionSystem.java`
- `core/src/main/java/com/riiablo/engine/server/UnitCollisionGrid.java`
- `core/src/main/java/com/riiablo/map/Map.java`
- `core/src/main/java/com/riiablo/map/MapGraph.java`
- `core/src/main/java/com/riiablo/map/pfa/RaycastCollisionDetector.java`
- `core/src/test/java/com/riiablo/engine/server/PathfinderTest.java`
- `core/src/test/java/com/riiablo/engine/server/DynamicUnitCollisionSystemTest.java`
- `core/src/test/java/com/riiablo/engine/server/UnitCollisionGridTest.java`
- `core/src/test/java/com/riiablo/map/ObjectCollisionUpdaterTest.java`

## 验证命令

```powershell
.\gradlew.bat :core:test `
  --tests com.riiablo.engine.server.PathfinderTest `
  --tests com.riiablo.engine.server.DynamicUnitCollisionSystemTest `
  --tests com.riiablo.engine.server.UnitCollisionGridTest `
  --tests com.riiablo.map.ObjectCollisionUpdaterTest.openingMonsterIgnoresDoorButNotOrdinaryDynamicObject `
  --no-daemon
```

上述针对性测试已通过。

注意：完整 `ObjectCollisionUpdaterTest` 当前有 3 个既有门/对象生命周期断言失败，与本次寻路修改无关，需要单独处理。

## 用户约束

- 暗黑设定不清楚时优先查 D2MOO，仍不清楚再查 The Phrozen Keep。
- 代码修改完成后提交 Git 并推送，供另一台电脑验证。
- 后续极小修改可以不编译，直接推送；寻路逻辑修改应运行针对性测试。
- `core/src/main/java/com/riiablo/engine/client/automap/AutomapManager.java` 的原有修改已在提交 `9e1267f9 Render only the active automap level` 中纳入；当前工作区已恢复干净，后续寻路修改仍不要回退或覆盖该提交。
