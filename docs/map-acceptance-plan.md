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

2026-10-14 传送点/角色动画回归：`game.log` 中 9 次缺失传送点实体、8 次明确
传送失败全部指向痛苦之厅（level 123）。D2MOO 的 `DRLGMAZE_PlaceAct5TempleStuff`
要求先生成 Temple 基础迷宫，再按方向替换 Waypoint DS1；Java 分支此前把 level 123
留在通用分支，导致该特殊预设未导出。补回后固定 seed=127897359 的 A2–A5 30 个
Waypoint 目的地均能定位到原生 RoomEx，延迟房间物化和重复查找也有回归覆盖。
动画日志中的 1,932 条零速警告来自 City of Damned 的 `window1/window2` 静态对象，
不是角色；保留其合法零速并停止误报。另修复资源加载完成后角色 COF 不跟随权威速率
变化的问题，并覆盖零速暂停、恢复跑步、FREEZE 和 native Jab 控制。无 GL 测试仍不能
替代真机截图确认角色是否视觉卡住。

2026-10-14 A5 出城旁边出现不可攻击督军：game2.log 中 A5Q1 在玩家旁三格生成 entity=87，然后 RoomEntityTracking 将其归入 level=109。移除 ZoneChangeEvent 的玩家相对 Boss 补生成，只追踪原生 RoomEx 生成的申克。D2MOO A5Q1.cpp::ACT5Q1_OnSiegeBossActivated 处理既有的 SUPERUNIQUE_SIEGE_BOSS，不在出城事件中把 Boss 生到玩家旁边。新增真实资源唯一申克预设/野外归属检查及反复出城不得额外生成 Boss 的 ECS 回归。p4.png/game2.log 的文件时间 07:04 早于地板隐藏修复提交 92812e5a 的 07:06，黑块是否仍在修复后复现需新截图，不据旧图宣称已验证解决。

2026-10-15 附属物数量与 seam 复核：D2Game 的主题人口处理已补入对象生成，主题桶的网格生成不再被 ObjGrp 路径遗漏；对象审计仍分别记录 exported/created/runtime/population，不能把创建成功当作数量与原版相等。seed=1、25Hz 的 132 图对象巡游在修复版本仍需以类别计数对照原版，当前日志中的松动石头、桶、宝箱、瓦罐和 trapped-soul 数量仅是实现侧基线，尚未形成原版数量等价结论。

28→27 focused TCP 复核已真实走到 level 27 落点，但落点怪物收到 visibility delete 快照，`COMBAT_UNRESOLVED` 且 casts=0；因此该边仍 FAIL，不能报告为 seam 通过。探针已移除跨 seam 攻击的错误同等级限制，并收紧近距离 native-room 可见性回退；下一步需修复实体删除/重基线生命周期，再复测同一失败包。

2026-10-15 附属物复测：修正主题人口分派把 one-based theme id 与候选数量比较的错误；主题 2 的桶网格现在按 D2Game 表索引执行，并在主题成功时停止重复 ObjGrp 分派。新增主题桶回归测试和 `OBJECT_AUDIT themes=` 诊断字段。`NativeObjectPopulationResourceTest`、`NativeSeamNetworkVisibilityTest` 均通过。

seed=1、25Hz 的 A1–A2 对象加载巡游（6 个关卡，独立输出目录）全部 `OBJECT_LOAD_PASS`；每个已导出对象均 `created=exported`，资源/碰撞审计 failures=0。该批关卡真实 `themes=0`，所以桶数量没有因主题修复变化；松动石头、桶、宝箱、陷阱、瓦罐仍不能据此宣布达到原版数量，必须继续按 D2MOO 的 ObjGroup density budget 与原版生成统计逐类对照。提交已合并推送至 `origin/master`（当前 `632f0333`）。

随后以同一修复版本执行 seed=1、25Hz、Act I–V 132 图对象加载巡游；132/132 个 `OBJECT_AUDIT` 和 `OBJECT_LOAD_PASS` 通过，`failures=0`，最终 `SUMMARY ... failures=0 result=PASS`。实现侧类别基线为 loose rock 78、barrel/exploding barrel 336、large chest 175、urn 444、floor trap 129、trapped soul 69；这些仍是加载实现的计数，尚未与原版逐房间 roll 结果建立等价证明。

2026-10-15 数量生成复核：当前 A2 默认使用 `Act2MapBuilderD2MOD`，已生成户外与迷宫；“只铺 Lut Gholein 城镇”描述的是旧备用实现，不能作为现版本数量偏少的根因。`ObjGrp/ObjPrb` 位于 Levels.txt；DrlgType 1（迷宫）同样需要人口生成，不能只在类型 3（户外）执行。物件 `density * (subtileArea >> 7) >> 8` 的两次右移与 D2MOO 一致，不能通过删除除数或直接乘十修复。

本次确认并修复的差异：A2–A5 普通怪群原先以固定格点加一次抖动落点，碰墙就永久丢弃成员；改为房间内最多二十次 leader 搜索及附近成员搜索，并保留 pack/party minion 关系。补入此前遗漏的 PartyMin/PartyMax 伴随怪。随机人口跳过 POPULATION_ZERO 房间。Levels.MonUMax 的注解此前误读 MonUMin 列，已改回独立最大值列。Objects PopulateFn1 的外围间距检查此前错误使用完整 PLACEMENT 掩码；按原版改为外围 Size+7 检查 0xC01、本体检查 PLACEMENT，并恢复对应边界条件。

A5 普通难度部分户外图 MonDen=0，依赖 DS1 MonPlace `place_impgroup/place_miniongroup`。此前桥接将这些指令回退为低编号单只怪物；现保留真实类型与 MinGrp/MaxGrp 群组预算，在实际地形碰撞安装后展开。其它尚未处理的 `place_*` 指令不得回退成无关怪物。

新增 NativePopulationAuditResourceTest，使用真实 MPQ 地形区分 rooms/eligible/roomArea、各候选组零预算数、preset/random 物件及 preset/pending 怪物数，覆盖 A1–A5 十六个代表场景；A1 的该测试仅统计预设怪物，不替代它独立的普通怪物生成器。零预算是逐候选项诊断，未按 PROB/ObjPrb 加权，不能当作漏放总数。新增外围 PRESET、完全阻塞、怪群重试、party、MonUMax 和 A5 群组专项回归，纳入 mapResourceAcceptance。

数量验收仍未闭环：A1 普通宝箱偏少未确认十倍差异；需继续将 ObjGroup 概率、预设/LvlSub、放置拒绝和运行时实体逐类对照。A2–A5 随机精英包、逻辑 coord-list 分区、原版 RNG/落点搜索和三难度人口尚未建立完整等价。待生成记录增加不能代替 TCP 客户端可见与全有向边步行验收。

本轮六种子（1/2/3/12345/54321/127897359）真实资源与人口检查 26 项通过、failures/errors=0。seed=1 的死亡之殿一层（56）待生成怪物由基线 33 增至 458，世界之石要塞一层（128）40→121；前者随机物件 4→7、塔拉夏古墓（66）22→26。血腥荒地（110）检测到 104 个正常群组标记，最终 pending=444；旧基线 283 混入错误编号单体，不能直接视为有效怪物同比。血腥荒地随机物件仍为 73。A1 鲜血荒地（2）随机物件仍为 14、冰冷之原（3）26，均是所有随机物件合计而非仅宝箱数；抽样未发现 externalEntity=false 的容器被遗漏。该测试只证明修复有实质效果及资源安装/一次性约束，未证明原版数量等价。

地图回归 192 项通过。基于包含并行 AI 修复的 `df26608b`，seed=1、25Hz、132 图对象加载巡游再次完成：132 个 OBJECT_AUDIT、132 个 OBJECT_LOAD_PASS，failures=0；目录 `server/d2gs/build/map-tour/population-fix-20261015`，SUMMARY 明确为 OBJECT_LOAD_ONLY，walks/transitions=0。运行时生成时序与独立资源测试不同，计数不应混用：关卡 56 的 pendingMonsters=357 / spawnedMonsters=216，110 为 444 / 18，128 为 126 / 36；spawned 仍取决于房间激活，此次没有全房间怪物客户端可见断言。A1 关卡 2 的运行时物件 exported=created=runtime=23，其中 population=13。该结果不得冒充全有向边巡游或与原版数量等价。

2026-10-15 A2 古墓绿色区域：D2Common 会把 `MAPTILE_HIDDEN` 的墙体记录保留在原生碰撞网格，但不提交其 `InvisWal.dt1`/辅助墙图形。`applyTileGridLayers` 原先只过滤隐藏入口标记，仍把普通隐藏墙安装到可见 wall layer，导致古墓内出现绿色辅助块。现已按 `bHidden`/`bRevealHidden` 规则过滤隐藏墙图形，碰撞数据继续保留；新增 `Act2TombHiddenWallResourceTest` 覆盖 level 55–72 的真实 MPQ 资源。

2026-10-15 普通宝箱全幕调查（范围为 A1–A5）：已阅读 Phrozen Keep 的
[Levels.txt 指南（1.10–1.14）](https://mail.d2mods.info/forum/kb/viewarticle?a=301)
及 [ObjGroup.txt 指南（1.10–1.14）](https://d2mods.info/forum/kb/viewarticle?a=394)。
前者明确 ObjGrp/ObjPrb 属于 Levels.txt；后者说明组内 PROB 控制选择概率，
DENSITY 的效果取决于 PopulateFn，不能超过 125。旧的
[1.09 指南](https://d2mods.info/forum/kb/viewarticle?a=241)把密度解释为物件间距，
不能据此替换 1.10f 的数量算法。论坛资料没有提供所有地图的固定普通箱数量基准。

新增三项真实 MPQ 二进制交叉验证，直接读取安装包里的原版编译表，而非只比较
Java 两处公式：objgroup.bin（0x34/行）的全组 ID/DENSITY/PROB/SHRINES/WELLS，
objects.bin（0x1C0/行）的 SizeX/SizeY/InitFn/PopulateFn/OperateFn/HasCollision，
levels.bin（0x220/行）的 Id/ObjGrp/ObjPrb/Themes，均与当前加载结果逐行一致。
未发现分组编号偏移、概率列读错或密度少一个数量级的问题。

NativePopulationAuditResourceTest 从十六图抽样扩展为全部 127 个非城镇关卡，
六种子覆盖 762 个地图实例。新增 CHEST_AUDIT / ACT_CHEST_AUDIT，区分预设和随机
普通箱，并保留各 Objects class id。普通箱口径为 InitFn=3 且 Name 含 chest，
包括 A2 Chest3、A5 chestR/woodchest/burialchest；排除任务箱、特殊闪光箱、
桶、瓦罐、棺材及其他掉落容器。因此 A4 在此口径为零，不代表 A4 没有掉落物件，
也不能把该分类结果当作 A4 生成失败。

普通难度六种子的每幕随机普通箱均值：A1=305（范围 290–322）、A2=108（98–118）、
A3=122.33（96–136）、A5=94.67（68–118）；预设普通箱均值另为 14、18.83、22.17、
15.83。D2MOO Objects.cpp::OBJECTS_PopulateFn3_CommonObjects / OBJECTS_CreateObject
要求每个密度名额最多五次落点，外围 Size+6 使用 PLACEMENT 检查，当前实现相同。
审计新增静态碰撞前缀和，穷举房间候选落点，再按五次独立尝试估算地形影响。
对应四幕的碰撞前名额均值约 457.28/258.25/259.99/279.29，静态地形估算约
327.99/115.34/128.91/93.22，与实际生成数量接近。此估算忽略主题替代、单位占位
和原版 RNG 相关性，不是原版游戏实测，也不是严格数量断言。

本轮验证：8 项物件资源测试及 1 项六种子全地图人口审计通过，failures/errors=0。
证据位于隔离区 core/build/reports/population-20261015/chest-terrain-six-seed.xml
和 chest-final-tables.log。当前结论是实现侧数量与当前地形和原版放置规则基本相符，
尚未证实或排除与原版游戏实际数量的差距；未改生成概率或人为加倍物件。下一层证据
仍需同版本原版游戏的逐关卡/逐类别计数，并核对地形与房间人口标志、预设/LvlSub、
原版 RNG/激活顺序及 TCP 客户端可见数量。不得将本轮通过写成“全幕普通箱数量已修复”
或“已与原版等量”。全有向边步行和 GL 外观验收仍独立待办。

2026-10-15 桶/瓦罐专项继续调查：发现 `DrlgMaze.materializeBasicPresetMaps` 只为临时
迷宫房间绑定 DS1，漏掉 D2MOO `DRLGMAZE_RollBasicPresets` 末尾的
`DRLGPRESET_BuildArea(..., width <= 12 && height <= 12)`。原版大于 12×12 地图格
的预设需要按 8×8 地图格拆分 RoomEx；A2 墓穴的 16×16 预设原先整个作为一个
80×80 子格房间导出，实际应拆为四个 40×40 子格房间。ObjGrp 命中、Fn1 首簇的
12 次落点搜索、主题/人口上限都以 RoomEx 为单位，因此仅面积预算正确并不足以
证明生成正确。前一轮基于当前房间的普通箱估算不能排除这个上游错误。

现已补接大型预设的 BuildArea，移除临时大房间，并按原版将已初始化的 orth 链接
转交给返回的拆分房间；小于等于 12×12 的房间保留原路径。没有修改 ObjPrb、DENSITY
或人为倍增数量。`NativeContainerPopulationResourceTest` 单列桶（class 7/11）与
瓦罐（4/9/52/94/95）的预设/随机记录、组配置、房间尺寸和预算，覆盖 A1/A2 全图，
并断言 level 55–61、66–72 的 16×16 墓穴预设 RoomEx 不再超过 40×40 子格，
不强制拆分保持单房间的蛆虫巢穴、古代通道等小于等于 12×12 预设。原版 objects.bin 核对新增
XSpace/YSpace 两列（按 BYTE 语义比较），目标类别与安装包一致。

同一专项审计顺序、普通难度六种子（1/2/3/12345/54321/127897359）修前/修后随机
瓦罐总数分别为 453/1067、611/935、498/1112、533/1120、546/1028、471/902；
均值 518.67→1027.33。seed=1 的死亡之殿一层（56）0→21、利爪蝮蛇神殿一层（58）0→78，
塔拉夏古墓 66 为 33→77、67 为 73→201；对应房间数 6→24、12→48、7→28、
19→76。以上只计随机瓦罐，不是普通箱或所有物件合计，也不是原版实测等量结论。

A1 桶的 Fn4 入口、密度公式、原版每房间最多 8 件、成簇搜索和真实间距字段已核对，
暂未发现该类别自身漏接。专项六种子随机桶（含爆炸桶）修前 331/303/350/338/377/391，
修后 329/302/343/327/388/350；变化受共享人口 RNG 及前序地图拆分影响，不能当作
A1 桶数量修复效果，A1 偏少问题仍不能关闭。证据在隔离区
`core/build/reports/population-20261015/container-before.xml` 与
`container-after-initial.xml`；其中探索性的 selected/validRootPositions 字段为旁路
重放统计，不完整模拟圣坛/井对 room seed 的消耗，不作为结论，最终审计已移除这些字段。

最终验证：地图回归 192 项通过；修正断言范围后的六种子物件专项及原版表核对 9 项
通过；单种子资源安装、物件资源巡检、古墓隐藏墙 6 项通过，均 failures/errors=0。
一次整批资源测试曾因 Windows 分页文件不足（mmap/G1，errno=1455）中断，不能记为
通过；上述结果来自分批串行重跑。最终 seed=1、25Hz 的 132 关卡运行时对象检查
全部通过，132 个 OBJECT_AUDIT/OBJECT_LOAD_PASS，failures=0；日志目录
`server/d2gs/build/map-tour/container-room-split-20261015`。SUMMARY 为 OBJECT_LOAD_ONLY、
walks/transitions=0，仍不构成全有向边 TCP 步行或 GL 外观验收。

2026-10-15 全幕/野外容器继续核查：大型迷宫预设拆分是共用修复。对照修前全图审计，
seed=1 的 A3 蜘蛛洞窟/巢穴（84/85）、沼泽地洞与剥皮地窖一二层（86–89）房间数均为
修前四倍；A4 火焰之河（107）32→288；A5 水晶通道/冰河/冰河路径（113/115/118）
13/14/16→52/56/64，世界之石要塞一至三层（128–130）10→40。A1–A5 的户外随机图
原本已按小房间导出，不能把这次室内拆分说成野外数量修复，也不能按房间倍数推断物件倍数。

NativeContainerPopulationResourceTest 扩展为五幕全部非城镇关卡、六种子 762 个地图实例，
其中 DrlgType=3 为 180 个实例。安装真实 DT1 头部/碰撞后统计，每个 class 分开记录
预设、随机、来源文件、external/spawned、ObjGrp 配置、房间标志及面积名额；分类包括
初始可选的 SubClass=8 和桶/瓦罐/翻石操作函数，不将 Dummy 标记计为可交互容器。
加入跨幕大型迷宫房间上限、可掉落预设不能被 externalEntity=false 过滤、人口只运行一次
的断言。扫描真实 LvlSub 的 34 个模板文件、26 个静态单位，当前口径无可交互掉落容器；
762 个地图实例中也未发现已导出的目标容器被 externalEntity=false 过滤。此结果不能证明
所有预设选择/放置数量均与原版相同。测试只需真实头部/碰撞，关闭并恢复 DT1 像素解码，
避免六种子反复加载时累积本机像素内存；不属于 GL 渲染验收。

另发现并修正 Fn1 的随机序列偏差：Java 在不支持的类别分支直接返回，漏掉了原版在类别
判断之前执行的 chance 掷骰。已将掷骰移到类别分派之前，保留原版不支持类别时不生成的
行为。真实组 106 的 16 次跳过后，后续普通箱首次落点以原版种子手算轨迹断言为 (22,19)。
这会改变后续共享 object-control seed 下的数量/落点，不能称为普遍增量修复。

A5 的组 106（443–445）、109（467–471）、129（518）虽然配置 Fn1，但 D2MOO
Objects.cpp::OBJECTS_PopulateFn1 仅接受 3/79/1/4/89/208/209，其他类别返回。
本机 Game.exe（FileVersion=1.14.0.64，SHA256=
C99E3067E6A7EEF7800680AC6BED2BEB1A50CC285933166BCD7FBE4F394446A6）反汇编也确认：
0x55A7C2–0x55A7EE 先掷 chance，0x55A7FD–0x55A882 仅比较上述类别，其余跳至
0x55A7F2 返回零。这个二进制版本不是 1.10f，作为对 D2MOO 参考实现的补充核对，不能
替代同版本原版实测。未擅自放开 A5 随机罐；其预设罐仍存在，例如 seed=1 的 111 为
443/444/445 各 5/10/9 件、112 为 8/4/6 件。

修正后的 seed=1 野外审计示例：鲜血荒地普通箱（139/140/141）4/1/1，松动石头/巨石
（174/175）3/3，另有 hidden stash（155）1；碎石荒地随机瓦罐（4/9/52/94/95）
8/3/5/7/6；蜘蛛森林随机篮（208/209）10/6；血腥丘陵随机桶（418/419）14/18。
这些是独立资源审计的待创建记录，不能等同运行时/客户端可见数或原版数量基准。
专项六种子全图与表数据回归共 10 项通过，failures/errors=0；证据在隔离区
core/build/reports/population-20261015/allact-containers-final.xml 和
allact-container-tables-final.xml。全幕偏少问题仍待原版同条件逐类计数、运行时/可见性核对，
全有向边双向 TCP 步行与 GL 外观验收继续独立保留。

随后基于 da163a11（已包含并行 AI 提交 492585ed）完成 seed=1、25Hz、132 关运行时
对象加载检查，目录 server/d2gs/build/map-tour/allact-container-roll-20261015。
132 个 OBJECT_AUDIT/OBJECT_LOAD_PASS 均 failures=0，目标可交互类别的已创建记录逐
level/class 核对 ECS 实体数量无缺失、无被跳过记录。野外运行时示例（与上述独立审计
时序不同，不能混算）：鲜血荒地箱 139/140/141 各 4/3/1、松动石头/巨石 174/175 各
2/1；碎石荒地瓦罐 4/9/52/94/95 各 5/3/4/7/9；蜘蛛森林篮 208/209 各 14/10；
血腥丘陵桶 418/419 各 12/12；冰冻高地预设罐 443/444/445 各 5/10/9。这些记录的
创建数量与逐类运行时实体数量一致，未发现目标物件的资源/位置/碰撞创建错误。

严格日志审计不能记为整轮通过：Get-MapLogIssues 保留 1325 条待处理警告，其中
DynamicUnitCollisionSystem 821、AI fallback 477、Actioneer 17、AnimDataResolver 10，
详见同目录 log-issues.json；未新增 allowlist 或降低日志级别。对象检查结果不豁免这些
AI/技能/动画及怪物移位问题。SUMMARY 为 OBJECT_LOAD_ONLY，walks/transitions=0；
本轮未执行全有向边步行，也未证明客户端画面或原版数量等价。
