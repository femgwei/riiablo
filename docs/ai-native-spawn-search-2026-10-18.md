# 普通出生搜索与客户端 MonSeq（2026-10-18）

## 后续：复活活体占位的同帧恢复

- 来源：SkillMonst.cpp 的 SKILLS_ResurrectUnit 在恢复生命与 footprint mask 后调用 D2Common_10214，并立即 COLLISION_SetMaskWithPattern；Path.cpp 的 D2Common_10214 也同步重建 pattern/footprint，而不是等待下一帧单位列表刷新。
- 普通复活/Revive 共用的 restoreMonster 成功结束时，现在立即登记活体占位；原实体 reinitializeMonster 刷新现有订阅后再登记恢复者，避免 Velocity 在死亡时被移除、复活时订阅尚未重插造成的同帧出生空窗。
- 沿用已有 hasPresence/单位 pattern 分类与 source 排除，不改变落点选择、AI 思考、复活技能时序或失败请求；原始共享 CORPSE bit 残留、复活幽灵等完整 pattern 切换矩阵仍不属于本批验证范围。
- 验证：CorpseSpawnCollisionIntegrationTest（4）、MaggotSpawnExecutionTest（9）、ServerMonsterCorpseSystemTest（6）、NativeSelfResurrectDataTest（1），共 20 项通过，architectureRegression 通过；日志 `build/ai-resurrection-marker-validation.log`。未运行全量测试或客户端图形验收。

## 后续：怪物服务端 CORPSE 出生标记层

- 来源：MonsterMode.cpp 死亡开始仅在非 deadCol 分支调用 PATH_SetUnitDeadCollision(...,1)；Path.cpp 的服务端分支移除活体 footprint 并写 3×3 COLLIDE_CORPSE。SkillMonst.cpp 的 SrvSt43_MaggotEgg 无条件调用同一函数。客户端的 SMALL_NO_PRESENCE 尸体十字不是服务端规则。
- 普通怪物死亡开始立即登记 3×3 CORPSE=0x8000，移除活体索引；deadCol 保留原占位且不新增该标记。虫卵孵化开始也写该层，不再只清活体占位。
- 登记独立于 Velocity 和 DD 的 Corpse 组件、usable/fading，速度组件移除后仍可查询；按同碰撞空间与 source 排除查询，实体删除清理。正常复活与原实体重初始化清除运行时标志。
- 当前仍是按实体投影的出生 mask 查询层，不是原版共享位网格：重叠尸体按独立实体保留，不模拟删除一具尸体清掉其他实体同位 bit 的原始互清。未接入玩家死碰撞、完整尸体传送/移除规则、大型潜伏残留及客户端图形验收；默认 SpawnCol mask 不含 CORPSE 时不会额外阻挡出生。
- 验证：CorpseSpawnCollisionIntegrationTest（3）、ServerMonsterCorpseSystemTest（6）、MaggotSpawnExecutionTest（9）、FrogPresenceIntegrationTest（7）、ImpMountCollisionIntegrationTest（2），共 27 项通过，architectureRegression 通过；日志 `build/ai-corpse-spawn-validation.log`。未运行全量测试或客户端图形验收。

## 后续：小型潜伏 footprint 的中心清除与移动

- 来源：AiThink.cpp 的 FrogDemon 分支先 ResetMask 中心 NO_PATH/MONSTER，再设 SMALL_NO_PRESENCE；Path.cpp 的 PATH_SetUnitCollisionPattern 仅修改字段。D2Collision.cpp 的 COLLISION_SetUnitCollisionMask 在后续迁移时按新模式清旧十字、写新十字；Step.cpp 的普通移动循环在整数格未变化时跳过迁移写入。
- enterFrogHidden 现在记录进入前的小型 pattern 与中心格；出生查询保留四臂 MONSTER、清中心 MONSTER，不增加 NO_PATH/PET。格内位移不恢复中心，首次跨格移动后使用完整 MONSTER 十字，返回原格也不回滚。
- 使用现有移动接受入口记录跨格变化，查询也观察当前坐标；不会把潜伏单位重新加入行走占位。现有成功出水恢复 SMALL_UNIT_PRESENCE 后重新提供中心 NO_PATH，重复潜伏重新清中心。
- 边界：本批投影只支持进入前的小型 pattern（Size 1/2 或已恢复的小型 presence）。大型原始 pattern 的边角/NO_PATH 残留、原生无引用计数网格的跨单位互清、同格强制传送重写以及完整 CORPSE 层尚未模拟，不能视为完整 16 位碰撞矩阵验收。
- 验证：FrogPresenceIntegrationTest（7）、ImpMountCollisionIntegrationTest（2）、MaggotSpawnExecutionTest（9）、NativeMonsterSpawnSearchTest（10）、DynamicUnitCollisionSystemTest（12），共 40 项通过，architectureRegression 通过；日志 `build/ai-frog-marker-revalidation.log`。首轮新增路径断言误用无效 mover=-1，修正为有效实体后重跑通过；未运行全量测试或客户端图形验收。

## 后续：骑乘小恶魔保留占位

- D2MOO 调用链：`SkillDruid.cpp` 的 SrvDo129 调用 `sub_6FCBDFE0(...,1,0)`，经 `SUnit.cpp`、`PathMisc.cpp` 的 D2Common_10229 到 `Step.cpp` 的 sub_6FDAD5E0，使用现有 collision pattern/footprint 迁移网格；上坐骑只另设 ATTACHED、owner、目标位与特殊 AI16，不清 footprint。AiThink.cpp 的 AI16 初始化/思考也未将其切换为 no-presence。
- 因此移除宿主中“骑乘 imp 无占位”的错误推断。上下坐骑立即迁移动态占位，出生查询不必等下一帧；拒绝尸体、死亡生命周期或生命归零的坐骑。
- 重建索引保留相互 owner/身份有效的 imp 与塔/攻城兽的合法重叠，避免重建把骑乘者或坐骑挪走；不对一般怪物 owner 关系放宽重叠修复。
- 潜伏暂未改动：FrogDemon 的原版 AI 清中心 NO_PATH/MONSTER 后只更改 pattern 字段，PATH_SetUnitCollisionPattern 不重写网格。该过渡与移动后的十字投影不能混为静态幽灵规则，仍待独立对齐；完整尸体层和自然地图视觉验收也未完成。
- 验证：ImpMountCollisionIntegrationTest（2）、MaggotSpawnExecutionTest（9）、NativeMonsterSpawnSearchTest（10）、DynamicUnitCollisionSystemTest（12）、FrogPresenceIntegrationTest（5），共 38 项通过，architectureRegression 通过；日志 `build/ai-imp-mount-validation.log`。未运行全量测试与客户端图形验收。

## 后续：幽灵的 no-presence 出生标记

- 来源：D2MOO `D2Common/src/Path/Path.cpp` 的 `PATH_AllocDynamicPath` 为 BaseId=WRAITH1 设置 SMALL_NO_PRESENCE，保留 MONSTER footprint；`D2Collision.cpp` 的 `COLLISION_SetMaskWithPattern` 对该模式仅写十字形传入 mask，不写 NO_PATH/PET。
- 出生 mask 查询新增独立的幽灵 MONSTER 十字投影，读取实时位置，排除 source，沿用同碰撞空间判断；工厂在 ECS 订阅插入前登记，删除/移除速度组件后不再参与。死亡生命周期/尸体不投影活体 MONSTER，复活重新参与。
- 修正精确子代 reserve 不经 hasPresence 直接写行走索引的问题；幽灵登记不会短暂变成行走障碍。
- 本批只覆盖服务端分配逻辑明确的幽灵。鸟类的同模式出现在 `PATH_SetUnitAliveCollision(..., false)` 客户端恢复分支，不能直接作为服务端出生依据；潜伏/骑乘/环境小动物以及完整 CORPSE 层仍需分别核对。
- 验证：MaggotSpawnExecutionTest（9）、NativeMonsterSpawnSearchTest（10）、FrogPresenceIntegrationTest（5）、DynamicUnitCollisionSystemTest（12），共 36 项通过，architectureRegression 通过。首轮潜伏夹具缺少 townPortalRegistry 注册而初始化失败，补齐夹具后重跑通过；日志 `build/ai-wraith-spawn-revalidation.log`。未运行全量测试或客户端图形验收。

## 后续：出生检查的飞弹标记

- D2MOO `D2Game/src/MISSILES/Missiles.cpp` 依据 `nCollision` 设置 `COLLIDE_MISSILE=0x40`，不是依据 `CollideType`。
- `D2Common/src/Path/Path.cpp` 的 `PATH_AddCollisionFootprintForUnit` 对飞弹使用 WithSize；`Units.cpp` 从 Missiles.txt 读取 Size。Size 1 为点、2 为十字、3 为九宫格，0 不写标记（`D2Collision.cpp` 的 `COLLISION_SetMaskWithSize`）。
- 出生 mask 查询现读取服务端飞弹实时位置，按表格 Collision/Size 投影；排除客户端副本及纯表现子飞弹。工厂立即登记，避免同帧创建尚未进入 ECS 订阅的空窗；订阅移除清理登记。
- 不写入行走占位索引，不改变飞弹命中处理；no-presence、CORPSE 和其他特殊状态矩阵仍未补齐。
- 验证：隔离构建的 MaggotSpawnExecutionTest（7）与 NativeMonsterSpawnSearchTest（10）通过，architectureRegression 通过；日志 `build/ai-missile-spawn-validation.log`。未运行客户端图形验收。

## 后续：SpawnCol=1 特殊地板分支

- 本地 1.10F D2Game.dll 0x6FC68FEE–0x6FC69136 核实：普通 SpawnCol=1 从活动房间的原生 floor 数组取候选，材质必须含水位 0x2；触手链 ID 258–263、HellMeteor ID 153 则走普通周界搜索。
- 原始数组顺序、null 槽及 nFloors 均保留，不从渲染地板重建排序。RoomEx 新增只读访问当前活动房间入口；缺失原生数组时仍拒绝，不假造候选。
- 只 roll 一次，以低 32 位 unsigned % nFloors 取起点，零改为 1；终止于起点前一项，因此保留原版跳过该项的行为。缺数组、数量非正及数组容量不符不 roll；有效数组即使没有可用水地板仍 roll 一次。nFloors=1 时原版存在非法索引，安全实现消耗对应一次 roll 后拒绝。
- 中心为 `(room.tileOrigin + floor.localTile) * 5 + 3`；先检查中心 MONSTER 位，再按 (0,-3)、(3,0)、(0,3)、(-3,0) 四个泊位，以 size=2/mask=0x1C09 检查；成功时出生在地板中心，不是泊位位置。
- 四方向顺序与 mask 同时对照只读数据地址 0x6FD28BC8–0x6FD28BE8。候选选定后只分配一次；分配失败不重新搜索或额外消耗 RNG。
- 新增受控原生 floor/RNG/碰撞探针回归，以及真实 1.10F 怪物表 + 服务端工厂的特殊地板出生、NOXP 和出生 COF 模式测试。这不是自然地图或 DCC/GL 视觉验收。
- 尚未补齐的 missile/no-presence/完整 16 位碰撞标记、分配 flags=66、活动房间初始种子回退边界保持不变。触手/陨石普通分支的 0x1C0 mask 仍受这些标记层边界约束。

本批使用本地 1.10F 数据与隔离构建：NativeMonsterSpawnSearchTest（10）、MaggotSpawnExecutionTest（6）、RoomExAdjacencyTest（5）、NestSwarmIntegrationTest（3），共 24 项全部通过；`:core:architectureRegression` 同次执行成功。

## 依据与搜索规则

D2MOO `MonsterSpawn.cpp` 的 `D2GAME_SpawnNormalMonster_6FC68E30` 仍是 return nullptr 加注释反编译。
因此本轮另行只读反汇编本地 1.10F D2Game.dll，核对 0x6FC691DB–0x6FC69481：

- 精确出生半径为 0；普通范围搜索从 3 开始，每圈 +3，圈内最多检查 8r 次。
- 每圈先 roll 选横/纵边；半径大于零时再 roll，以低 32 位 unsigned 值 % r 取边内偏移；之后再 roll 两次选择 X/Y 符号。
- 半径零也消耗三次随机数，不是零次；先移动一步再检查。
- 转角改方向；初始方向并不随符号归一化，可能重复探测某些坐标。保留原顺序，不去重、不改成均匀随机周界。
- 普通分支候选受当前房间矩形约束，不能把相邻房间当作任意搜索扩展。
- NativeMonsterSpawnSearchTest 用固定起始种子核对访问序列、最终 low/high 状态、精确出生 RNG 消耗及失败尝试数。

`D2Dungeon.cpp::DUNGEON_AllocRoom` 用单独低种子初始化活动房间；`DrlgDrlg.cpp` 分配时从 RoomEx 种子 roll 得到该低种子。
RoomEx.runtimeSeed 首次访问时如已有活动房间，就共享其实时 seed 对象；对象人口和 AI 出生不会各用一份拷贝。
未建立活动房间时仍有旧人口快照回退；无 RoomEx 的旧/合成地图使用工厂私有的按 Zone 分离流。这两种回退不承诺原版初始种子或完整回放一致性。

## 碰撞与动作接入

- SpawnCol 分派按原版 mask：默认 0x3C01、1 为 0x01C0、2 为 0x3F11、3 为零；不再使用单位 walking/flying profile 替代出生策略。
- 普通分支按 size 检查点/十字/3×3；区分静态低位标记、物件/门引用、玩家/怪物足迹、NO_PATH/PET 中心或十字标记，以及落地物品。
- 精确出生也使用房间搜索入口，成功后预留同帧子代占位；出生动作的 COF mode 在分配时立即提交，不先以 NU 请求资源。
- SrvSt43 清除虫卵目标位、移除活体占位；刷新碰撞索引也不会把正在孵化的虫卵重新插回活体层。
- 客户端 AnimationStepper 补上通用 nativeMonster MonSeq，跟随服务器源帧的重复、回退和停留，不再独立按 COF 速度前进；序列结束恢复普通播放。
- COF 资源队列回归夹具补注册当前要求的 clientContext；不通过放宽生产注入要求绕过测试。

## 仍未完成，不能称作完整原版验收

- 初批曾拒绝 SpawnCol=1；特殊地板搜索与触手/陨石例外现已继续接入，见本文顶部后续记录。missile 标记层、部分 no-presence 单位标记及分配 flags=66 等仍需补齐。
- 现有宿主物件层与动态索引不是完整 16 位原生碰撞网格；本批只对已存在的各层按原生 mask 分类，未宣称所有碰撞矩阵/特殊状态组合完成。
- 孵化只替换活体占位，未增加完整 CORPSE 标记与所有 dead-collision 交互。
- 客户端回归使用真实 MPQ COF 和动画状态，不加载 DCC/GL 场景截图；全怪物、自然地图、资源异步切换下的视觉效果仍需实机验收。

## 验证

使用本地 1.10F 数据与隔离构建目录，44 项针对性测试全部通过：NativeMonsterSpawnSearchTest（4）、MaggotSpawnExecutionTest（5）、NativeMonsterSequenceIntegrationTest（2）、AnimationStepperTest（9）、Cof110fModeTransitionTest（1）、CofModeTransitionTest（4）、NestSwarmIntegrationTest（3）、RoomExAdjacencyTest（5）、NativeObjectPopulationResourceTest（10）、NativeContainerPopulationResourceTest（1）。

架构检查起初在字符串扫描正则发生 StackOverflowError；将互斥字符分支的重复改为等价 possessive repetition，未增加预算或放宽依赖规则，之后 `:core:architectureRegression` 三项全部通过。未运行全量测试或客户端 DCC/GL 场景验收。
