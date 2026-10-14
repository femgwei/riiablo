# 地图分层验收与故障闭环

## 原则

生成成功、巡图到达和客户端显示正确是不同证据，不再用一个 PASS 代替全部验收。旧巡图可直接进入地图，并把部分连续出口作为 Warp，只能证明部分加载和局部移动，不能证明真实出城或资源外观。

## 分层门槛

1. 生成：Levels 1–132（含附属子地图），记录 seed、难度、RoomEx、边界、Vis/Warp 和单位导出。未审查告警及非零资源失败计数阻止通过。
2. 资源安装：读取真实 MPQ/DT1，执行生产瓦片铺设，检查 failedResolve、双层 floor、可见入口和 hidden 标志。对象检查原始 id→Objects 物理行→Token/COF/DC6，不能仅检查数量。
3. 实体运行：激活每个 RoomEx，分别统计预期导出、已创建、延迟、失败的 NPC、怪物和对象；检查资源及碰撞。导出数量不等于已加载数量。
4. 生产 TCP 通行：输入→服务端校验→生产寻路/碰撞→移动→切区→客户端快照。不用位置包、关闭碰撞或删除怪物；允许真实战斗清障，死亡恢复不算成功。
5. 显示：有 GL 的客户端截图检查装饰、入口、出口及动画，与原版参照比较。无 GL 测试不能证明所有外观正确，仍需真机验收。

## 连接覆盖

- WALK_SEAM：原生连续边界必须双向步行；五个城镇出口 1→2、40→41、75→76、103→104、109→110 禁止 Warp，传送请求数为零。
- REAL_WARP：真实楼梯、洞穴、传送点，验证目标、落点、返回入口及交互范围。
- DETACHED_STAGE：仅初始化不连续测试段，不算连接验证。
- 报告同时记录 132 个地图节点和原生连接有向边；单一路线不等于全部出口通过，必须包括岔路、地下层、七墓及返回路径。
- 对角色体积、动态对象、门和怪物堵路设反例；记录边界前后坐标、位移和客户端等级，防止错误远距离落点。

2026-10-14 补充要求：`a953890e` 已补齐 Act I–IV 户外 seam 的资源回归。
完整巡游必须从 D2MOO 原生 `Vis > 0 / Warp=-1` 数据提取全部相邻边，逐条双向
真实 TCP 步行验证；不能只验证当前路线或仅从已安装成功的 seam 推导预期清单。
每条边允许初始化出发地图，目标地图必须步行到达，返回必须走同一连接；缺失原生
连接安装、返回证据、客户端等级或连续位移断言都应失败。此门槛覆盖原生相邻
walk seam；全部 REAL_WARP 有向边仍需另行扩展，不能称为所有类型的全有向边巡游。

## 矩阵与闭环

固定种子 1、2、3、12345、54321，加问题种子 127897359。先普通难度，再扩展噩梦/地狱；25Hz 正式验收，100Hz 仅诊断。每轮固定 Git 和资源版本，不混合修复前后结果。

失败包保存版本、资源来源、seed、难度、ACT/level、目标/坐标、RoomEx、瓦片类型/style/sequence/source DT1、对象原始 id/class/Token、碰撞、输入与快照及完整日志。先保存证据并补失败回归，再查 D2MOO；仍不清楚查 Phrozen Keep 或分析 1.10f 二进制。修复后先复测失败 seed，再跑固定矩阵；不得降低日志级别、放宽断言或传送绕路消除失败。

## 本轮落地

- `:core:mapResourceAcceptance`：A1–A5 所有生成 zone 的真实 DT1 安装与入口可见性审计（A1 城镇另走原生 DS1 导出），加城镇边界、古墓入口、A4 火盆专项回归。短矩阵参数：`-PmapAuditSeeds=1,2,3,12345,54321,127897359`。
- `MapTourWalkingTest`：生产移动校验和出口规划反例，修复 TCP 无条件拒绝跨 zone 的遗漏；仅原生 seam 放行，不放行普通相邻或重叠地图。
- 巡图 contract=2：城镇出口先到边界，再 TCP 步行跨界，不回退 Warp。独立日志审计拒绝旧 PASS、缺图、错误模式及未审查告警，保存 `log-issues.json`。
- `tools/test-map-acceptance-audit.ps1`：验证审计本身能拒绝故障样本。

## 尚未完成

全有向边巡游、双向 TCP 连续位移断言、全部运行时实体资源审计、三难度矩阵及 GL 自动截图对照仍待实施。单元测试不等于端到端通过。

2026-10-14 用户授权瓦片告警修复后恢复长巡游；恢复前应完成资源矩阵与短城镇 TCP 双向回归。真机问题未闭环前，不报告“全 ACT 无错”。

## 短 TCP 双向入口

执行 `:server:d2gs:headlessTownSeams -PtourSeed=127897359 -PtourOutput=server/d2gs/build/map-town-tcp/新目录`，默认只走五个城镇出口并返回；可用 `-PtourLevels=109,110` 定位单个城镇。输出目录已有日志时拒绝覆盖。它是局部诊断，不是 132 图全覆盖。

每段仅在城镇初始化；野外必须靠真实 TCP 移动到达，再靠同一路径返回。闭门先进行真实协议交互，失败不改用传送或野外 staging。`Assert-TownRoundTripEvidence` 独立检查每个方向恰好一条步行证据、零 Warp 请求及无野外 staging；完整日志仍需 `Get-MapLogIssues`，通行成功不免除 AI/资源告警。

运行后执行 `tools/audit-town-seams.ps1 -Directory 输出目录 -Console 完整控制台日志`；单城测试加 `-Towns 109`。脚本先保存 `log-issues.json`，再独立验收通行证据及告警，任一层失败则退出失败。

新增步行切区事件回归：出城和返回各通知一次，并验证角色城镇/野外动作模式，单纯换 RoomEx 不重复通知。城门编号回归依据 D2MOO `ObjectsIds.h` 的 class 449 及 `ObjMode.cpp::OBJECTS_OperateFunction61_HarrogathMainGate`，同时检查真实 1.10f Objects 表的 Token=2v / OperateFn=61；Expansion 元数据行不得占用原生对象编号。

2026-10-14 短回归结果：seed=127897359、25Hz，五城十个方向真实 TCP 步行通过；181 项地图回归、4 项资源审计、10 项服务端测试通过。完整日志仍有 33 条未解决告警，独立审计判为失败，不宣称无错：优先查 A3 丛林尺寸警告，再依据 D2MOO 补齐 ThornHulk/FetishBlowgun、DoomKnight/VileMother/Regurgitator、CatapultSpotter/NpcBarb/Overseer 的缺失 AI；同时追查 Attributes 空 base 重置及两条生成位置调整。五种子短通行矩阵、连续位移断言与真机显示验证仍待完成。

2026-10-14 瓦片告警修复：按 D2MOO `DrlgOutPlace.cpp::sub_6FD83970` 的 unsigned nTemp 修正四个符号校正右移，侧向丛林偏移不再多出两格。182 项地图回归通过；A1–A5 六种子真实资源审计通过，failedResolve 均为零，未复现 Invalid style 或丛林尺寸 WARNING。A1 预设关卡另检查真实原生 DS1 导出，不跳过城镇/地下地图。按用户新授权恢复五种子 25Hz 长巡游，使用隔离的提交版本避免混入另一任务工作区；严格总验收仍会因非瓦片未解决告警失败。

2026-10-14 真机 A5 蓝色碰撞网格：原生 floor 的 MAPTILE_HIDDEN 标记在 D2MooTileApplier → TileGrid → 渲染安装过程中丢失，导致 Expansion/town/collision.dt1 的辅助图形被绘制。保留两层地板原生 flags，安装时不绘制隐藏地板，同时保留 bRevealHidden 的 0x200 语义及原生碰撞数组。依据 D2MOO DrlgRoomTile.cpp::DRLGROOMTILE_InitTileDataFlags。新增 Act5TownHiddenFloorResourceTest，以真实 1.10f 资源断言 collision.dt1 的隐藏标记、未安装到可见层和碰撞仍存在；纳入 mapResourceAcceptance。无 GL 测试不能替代用户真机截图确认。

该修复验证：55 项定向测试、184 项地图回归、6 项资源审计方法（A1–A5 六种子、792 个地图实例）通过。

2026-10-14 A5 出城旁边出现不可攻击督军：game2.log 中 A5Q1 在玩家旁三格生成 entity=87，然后 RoomEntityTracking 将其归入 level=109。移除 ZoneChangeEvent 的玩家相对 Boss 补生成，只追踪原生 RoomEx 生成的申克。D2MOO A5Q1.cpp::ACT5Q1_OnSiegeBossActivated 处理既有的 SUPERUNIQUE_SIEGE_BOSS，不在出城事件中把 Boss 生到玩家旁边。新增真实资源唯一申克预设/野外归属检查及反复出城不得额外生成 Boss 的 ECS 回归。p4.png/game2.log 的文件时间 07:04 早于地板隐藏修复提交 92812e5a 的 07:06，黑块是否仍在修复后复现需新截图，不据旧图宣称已验证解决。
