# 移动怪物坐标来源接入前置记录

首轮只追踪源码与确定接入边界；后续来源保留实现见文末。

## 已确认的房间人口链路

- NativeRoomMonsterPopulation.addGroupAt 使用 int x/y 选点，通过 canPlace 后调用
  RoomEx.addMonsterSpawn；addCompanion 也使用 int 候选点，但其领队基准来自
  Math.round(leader.x/y)，不能因此声称保留了领队原生低位。
- Map.RoomEx.addMonsterSpawn 的现有重载全部接受 float worldX/worldY。
  Map.MonsterSpawn 也只保存 float x/y，整数来源标记在此丢失。
- RoomActivationSystem.spawnActiveRoomPopulations 的实际消费链路是遍历 pending spawn，
  调用 factory.createMonster(spawn.monsterId, spawn.x, spawn.y, ...)。
  不能在这个末端用强转或“float 恰为整数”证明可信来源。
- 另一条 Map.NativeMonster 链路也只存 float。MapManager.createNativeTownMonsters
  只消费城镇记录，不能将它当作普通房间移动怪物的完整生产入口。

## 原版依据与适配约束

本地 D2MOO source/D2Common/src/Path/Path.cpp 的 PATH_AllocDynamicPath
（约 704、718–719 行）以 int nX/nY 调用 PATH_ToFP16Center 初始化动态路径。
这支持保留分配参数，不支持从已有 float 位置恢复 fp16 低位。

下一批应增加显式命名的整数来源入口，并在实际 int 选点处选择它：

- 保留原 float API 的未知来源语义；不要增加隐式 int 重载，避免现有调用被悄然升级。
- deferred spawn 保存独立来源标记和整数值，不从其 float 字段重新计算。
- 实体工厂可能调整出生落点，必须核对最终提交位置；若已漂移且无可信整数结果，
  原选择点不能继续作为实体最终 raw。失败、不分配和池复用也必须覆盖。
- 接入前核对 uint16 域和宿主格心投影约定；不要为了适配 raw 而改变原有刷怪成功率。
- 先保留来源，再在工厂最终落点和生命周期契约明确后启用；这不等于完成
  NativePathUnitBinding 的动态路径、房间/Act 身份或原生逐帧积分。

## 验证要求

后续跨 Map、人口生成、房间激活及工厂的行为修改不按极小补丁处理，需执行定向测试：
float 整值仍未知、显式来源跨 deferred 队列保留、非法坐标与失败不伪造 raw、
工厂落点调整不能沿用旧 raw、房间重复激活不重复分配，以及实际移动后的失效。

本轮仅源码检查与文档静态检查，未编译、未运行测试；未验证自然地图或客户端行为。

## 后续实现：延迟队列保留整数选点

RoomEx.addIntegerMonsterSpawn 是显式新入口，MonsterSpawn 保存独立整数来源标记及原始 int。
旧 float 构造和 API 不建立来源。NativeRoomMonsterPopulation 的两处实际 int 选点使用新入口。
完整 int 域仍可被记录，不等于允许直接建立 uint16 raw；实体工厂与房间激活尚未消费此来源。
同伴选点保存的是本次候选整数，不是恢复领队 fp16 低位。

定向验证使用独立 buildDir / project-cache-dir，日志 build/ai-mobile-selection-tests.log。
结果：MonsterSpawnIntegerSelectionTest 3 项、人口真实路径用例 1 项全部通过，
失败/错误/跳过均为 0；architectureRegression 通过（其模块测试为 UP-TO-DATE，未声称全部重跑）。
未运行人口资源测试类中的完整自然 Act 地图用例，也未新增图形客户端验收。
