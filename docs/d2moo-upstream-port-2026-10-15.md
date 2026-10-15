# D2MOO 上游 1.10f 修正移植

## 参考基线

- 本机 D2MOO 检出：`8322494ed1f715ad51552f169df76cf600fabc71`（2025-03-01）。
- 审查远端：`0c0bbd1e6bef9181a3da1c046523af622a925416`（2026-10-13）。
  相对旧检出有158个提交，含合并提交，并非全为本月新增。
- 只 fetch 远端引用，没有切换本机 D2MOO 检出。
- 目标为1.10f。第一批读取上游原版地址/反汇编证据；第四批另静态核验本机
  D2Common.dll 的尺寸、heuristic、目标跟踪与加速度分支，详见第四批报告。

## 已移植

### Charged Bolt 的寿命比较顺序

依据 [8b424dc](https://github.com/ThePhrozenKeep/D2MOO/commit/8b424dcc47fb57b5af7ebbd36044e6923ec6cb93)，
1.10f `D2Game.0x6FD15E50` 使用 `cmp ebx,4Eh` 比较完整帧数：>=78则改为77，
之后才向路径距离传递字节参数。法师、Charged Bolt Sentry、Charged Strike 共用此策略。
修正了旧实现/测试/说明中300帧不受限制的错误，新增255/256边界。

刺客 `SkillAss.cpp::sub_6FCF6C10` 在该远端版本仍使用低字节比较；
由独立 `clawsOfThunderLifetimeFrames` 保留，不能随法师回调一并修改。
雷电爪第三层真实释放增加300帧夹具；Phoenix Strike完整整数策略不变。

### 鲜血石魔的分担目标

依据 [2bc0c20](https://github.com/ThePhrozenKeep/D2MOO/commit/2bc0c20e0778c8343da1bcadab43317feb224810)，
`EventFunc26_6FD0F5E0` 计算主人当前HP减去Param5分担量后，应写回主人，最低1HP，
不应覆盖石魔HP。传入伤害仍按原分担量减少，不按主人剩余HP重新限制分担量。

指定安装目录的原始 `Skills.txt` 与运行时均确认 `BloodGolem.Param5=10`。
此前“1.10f为0且复制主人生命”的说明有误，已订正。测试区分原表10%、显式0%、
显式50%及主人最低1HP；回调阶段的石魔HP保持原值，调用者随后扣除剩余伤害。

### DRLG 尚缺差异

依据 [b5b0888](https://github.com/ThePhrozenKeep/D2MOO/commit/b5b0888d75c42eedc02be492131064bbd9fbf210)
及 [1c7d34c](https://github.com/ThePhrozenKeep/D2MOO/commit/1c7d34c)：

- 桥梁候选行使用 `(random+i)%(height-2)+1`，只遍历内部行。
- A5路障上下边界沿X扫描使用width，左右边界继续使用height。
- 同一A5函数原版传入文件号 `2*(BloodyFoothills?1:0)-1`，Java原先在已经得到±1后
  又乘2；一并订正为1/-1，避免错误文件号及绕过-1的原生文件轮转。
- 泥路处理包含 `width+1` 列。
- 墙体链接不能复用地板链表。
- 状态引用查询在没有引用时返回-1；已有同级引用不再次执行初始化回调。
- 每条户外边重新清空packed值；已有方向位修正、房间降级及循环扫描予以保留。

新增不依赖游戏资源的回归，执行实际网格写入、链接插入及状态传播；仅预设尺寸
使用夹具。上游原版多seed全图一致性结果属于上游报告，不是riiablo本轮验收结果。

### Do085非法升级飞弹

依据 [9a701ba](https://github.com/ThePhrozenKeep/D2MOO/commit/9a701ba27047a9ee5132e31199b0ad215b46c9bb)，
`SrvDo085_UnholyBolt_ShamanFire` 的最终ID越界应返回失败。
Java解析失败返回null并终止该发射路径，不再静默回退基础missile。
回归覆盖正常萨满变体、缺失基础行、负数/超上界最终ID以及实际SkillDo不生成弹体。

## 验证

安装目录：`G:\BaiduNetdiskDownload\Diablo II 1.10F`。PowerShell：

```powershell
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
$env:D2_TEST = $env:D2_HOME
.\gradlew.bat :core:test `
  --tests com.riiablo.engine.server.MissileNativePolicyTest `
  --tests com.riiablo.engine.server.NecromancerGolemSideEffectTest `
  --tests com.riiablo.engine.server.NativeNecromancerSummonDataTest `
  --tests com.riiablo.engine.server.ShamanFireballAuditTest `
  --tests com.riiablo.engine.server.AssassinMartialArtsTest `
  --tests com.riiablo.engine.server.AssassinSkillSpecializationTest `
  --tests 'com.d2moo.common.drlg.*' `
  --tests 'com.riiablo.map.Act5*' `
  --tests 'com.riiablo.map.d2moo.Act5*' `
  --tests com.riiablo.map.Act1NativeOrthAnchorTest --console=plain
```

最终扩展回归221项通过（30个测试类），失败/错误/跳过均0；`BUILD SUCCESSFUL`。
其中包含6项新增DRLG回归、雷电爪300帧真实释放、原始Skills.txt分担参数校验，
以及A1锚点/A5布局、地形和资源相关测试。`git diff --check`通过。
未运行全项目测试、132关卡多seed/三难度矩阵或真实TCP/GL实机验收。

## 第二批：WarpDist 与 Quill Rat

### 普通怪群入口排除

依据 [427946c](https://github.com/ThePhrozenKeep/D2MOO/commit/427946c0a9fea4f608a795fbdb469cf78385eb16)
及 `MonsterRegion.cpp::sub_6FC66260`（1.10f `0x6FC66260`）：

- 检查 `dx*dx+dy*dy < Levels.WarpDist`；WarpDist 不再平方，等号允许落点。
- 使用关卡级 warp/waypoint **房间中心**，不是 UNIT_TILE 实际门口，也不把连续
  `Warp=-1` 接缝一律当成 warp。原生中心已是 subtiles。
- 另检查非 Ex 的 tile-index 11 出生点，原生两个 tile 坐标均 >0 才乘5；不加 Ex 的3。
  正值检查在兼容世界坐标投影前进行，允许投影后为负数。
- A2 通用桥/A3/A4/A5/Arcane 导出时保存不可变副本，DRLG 释放后仍能使用。
  拆分出生点“选坐标”与“激活房间”，保存元数据不额外初始化房间或推进人口 RNG。
- 只在 `addGroup` 普通怪群首领搜索启用，对应 `sub_6FC677D0` 的 a7=1。
  拒绝候选后继续重试，最多20次；跟随成员、派生小队和 DS1 预设 marker 不套过滤。
  原版预设 cases24/26直接调用 `sub_6FC6A030`，并非上述 a7=1 搜索。

边界：本批接入的是 A2–A5 native deferred normal population，未改写 A1 的独立人口路径、
暗金/AI 迁移调用者或整个逻辑区域选择器。现有 Java tile11 多候选选择仍是 seed 投影，
不是原版逐候选 RNG 消耗的完全复刻；元数据按导出时选择固定，不声称多次重选等价。

### 硬毛老鼠额外弹体

依据 [45b2f0c](https://github.com/ThePhrozenKeep/D2MOO/commit/45b2f0cbad5bd985a4d588052c45ac8b6fcfb053)
及 `MonsterMode.cpp::sub_6FC64790` / `Skills.cpp::sub_6FD11420`：

- 只限基本攻击、BaseId=quillrat1，且主弹体成功后才执行 aip3[难度] 次额外尝试。
- 每次攻击重新建立双字种子：low=`0x53454953`（SEIS），high=权威服务器 ECS entity ID。
  使用完整64位进位，不能用单字 NativeRng 代替；不消耗 monster.rngState。
- 初始X/Y偏移各+5，每弹体各掷一次，奇数时在**上次偏移**上翻转符号。
  额外弹体创建失败继续下一次，不补发/重掷；零目标坐标按原版拒绝该次。
- 不修改实时目标指针；额外弹体显式 level=1，并逐弹体保留 A2 伤害/AR 快照。
  主弹体改用 MonsterSkillBonus+1，佣兵主弹体使用佣兵自身等级。
- 原始1.10f MonStats.txt与运行时核对：quillrat1–8的三个难度 aip3 **全部0**。
  所以原版数据不增加弹数；非零参数、不同难度/单位ID、累积翻转和失败分支由夹具验证。

边界：ECS身份不是原版单位分配序列；当前攻击管线仍要求有效单位目标，未扩展原版
“无单位目标时取首个动态路径点”的攻击入口。未宣称完整 QuillRat AI 或定点弹道等价。

### 第二批验证与交付

代码提交 `1c55883e309f044ffa62b0a0afb155f79a079202` 已推送 `origin/master`，
远端分支 SHA 核对一致。另一轮 DiabPrison 的未提交文件及 Actioneer 独立 hunk 保留，
没有混入该提交；工作区因此并非全局干净。

最终定向回归 **102项 / 29类通过**，失败/错误/跳过均0，BUILD SUCCESSFUL。
含7项 Quill Rat、4项排除元数据、7项人口回归（其中3项既有），并覆盖萨满发弹、
Dungeon 出生点、A2–A5桥接、Arcane和原生Waypoint。
真实导出回归对每个原生关卡检查元数据存续，并核验存在的房间中心；
对 WarpDist=0 的关卡临时使用1单元测试阈值，finally恢复，不把未启用过滤误判为缺失坐标。
未强求每个ACT都有带warp标志的投影房间，未证明原生标志/人口布局全部等价。

本机另一个工作流同时运行 core:test，因此最终用临时 Gradle init 注册
`upstreamRound2Test`，独立保存29份XML于 `core/build/test-results/upstreamRound2Test`。
下列普通命令选择相同集合，供另一台电脑无并发时复验：

```powershell
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
$env:D2_TEST = $env:D2_HOME
.\gradlew.bat :core:test `
  --tests com.riiablo.map.NativeMonsterSpawnExclusionTest `
  --tests com.riiablo.map.NativeRoomMonsterPopulationResourceTest `
  --tests com.riiablo.engine.server.QuillRatMissileTest `
  --tests com.riiablo.engine.server.ShamanFireballAuditTest `
  --tests 'com.d2moo.common.dungeon.*' `
  --tests 'com.riiablo.map.d2moo.Act2*' --tests 'com.riiablo.map.d2moo.Act3*' `
  --tests 'com.riiablo.map.d2moo.Act4*' --tests 'com.riiablo.map.d2moo.Act5*' `
  --tests com.riiablo.map.Act2ArcaneSanctuaryLayoutTest `
  --tests com.riiablo.map.Act2ArcaneSanctuaryTopologyTest `
  --tests com.riiablo.map.NativeWaypointResourceTest --console=plain
```

第二批交付时额外扩展A1检查存在两项失败，**当时没有修复，也未在干净旧提交独立复现**：
`Act1D2MOOLayoutBridgeTest.fixedSeedAct1LayoutIsStableAndExportable` 的level5共享地板坐标
预期1/实际0；`mapBuilderProjectsNativeMonasteryWarpSlots` 的level26缺少端点。
第二批没有改写A1生成器；本轮专项调查和闭环见下方第三批，仍不能宣布全项目绿。
真实TCP、三难度/多seed人口矩阵和GL实机验证未执行。

## 第三批：A1 测试契约闭环（非生产移植）

修改前在当前工作区用独立测试任务复现了上述两项失败（2/2），未在干净历史
checkout 验证。调查结果是旧断言未同步既有消费语义；本批只修改测试，不改写生成器、
渲染器或实际出口行为，也不归作新的 D2MOO 生产功能移植。

### 地板分层与隐藏填充

对照上述远端基线的 `DrlgRoomTile.cpp::DRLGROOMTILE_InitTileDataFlags`：
非 shadow 的记录均保留 `(nWallLayer+1)<<14`，floor 不例外。
`DRLGROOMTILE_LoadTiles` 为缺失 DS1 floor 生成隐藏 style-30 填充记录。
riiablo 的 `b4c49c7d7` 已实现分层写入和填充过滤；`92812e5aa` 再保留原生 flags。
因此不能把合法 base/overlay 同坐标视为重复，也不能要求隐藏填充进入可见地板层。

- 固定种子 `0x171A6100`、普通难度：Dark Wood 实际导出5761条地板，全部写入，
  恰有1对 base/overlay；逐对检查原生层号1/2，同层重复严格为0。
- 原先失败遮挡的后续地下通道断言也已核对：5184条地板回调中，独立审计数出2011条
  隐藏 style-30 填充，3173条可见地板写入，边界裁剪0。总量精确相等，不使用容差。
- 保留其他关卡固定数量、边界裁剪、非法ID、越界、完整回调记账等断言。
  新增同层覆盖计数反例；确认普通隐藏地板及可见 style-30 不会被误当成空白填充丢弃。

### 修道院连续连接

对照 `DrlgOutPlace.cpp::sub_6FD82750`（1.10f `0x6FD82750`）：
`Vis!=0 && Warp==-1` 生成 orth 连续连接；Barracks court connector 同样使用 orth。
`nativeDiagnostics()[13]` 只数交互 Warp marker，不能用它要求整关必有交互端点。

测试改为 `mapBuilderProjectsNativeMonasteryWalkSeams`，逐边检查26↔27、27↔28：
从投影前保存的原生 walk edge 找到唯一 slot，核对目标 override、连续连接分类、
两端锚点存在、投影后的世界坐标相等，且锚点在两关共同边界交集中。
既有 `Act1NativeOrthAnchorTest` 继续覆盖四方向和无 orth 时不捏造入口的反例。

### 第三批验证

完整 A1 bridge、TileApplier、orth anchor 及 `com.d2moo.common.drlg.*` 共
**95项 / 18类通过**，失败/错误/跳过0。独立XML在
`core/build/test-results/act1Round3-full`，HTML在同名 reports/tests 目录。
修道院另跑7次：普通种子1/2/3/12345/54321，以及默认种子的难度1/2；
加上上述默认普通难度检查，覆盖6个种子、三难度，但不是完整种子×难度矩阵。
各轮报告独立保存于 `core/build/test-results/act1Round3Seam*`，不覆盖其他工作流报告。
本机临时 Gradle init 任务只用于隔离报告，不纳入仓库。

另一台电脑无并发时可直接复验95项集合：

```powershell
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
$env:D2_TEST = $env:D2_HOME
.\gradlew.bat :core:test `
  --tests com.riiablo.map.d2moo.Act1D2MOOLayoutBridgeTest `
  --tests com.riiablo.map.d2moo.D2MooTileApplierTest `
  --tests com.riiablo.map.Act1NativeOrthAnchorTest `
  --tests 'com.d2moo.common.drlg.*' --console=plain
```

未运行全项目、真实TCP通行、GL实机或多seed地板精确数量矩阵。
固定地板数量只验收上述默认种子/普通难度，不外推到其他种子或难度。
测试提交 `052a9730f61a700c43090762c69b9cae98f47074` 已推送 `origin/master`，
远端SHA核对一致。测试提交时未混入其他暂存变更；另一轮DiabPrison已独立提交。
本轮三份进度文档随后单独提交推送。

## 第四批：寻路碰撞比较与异常 size 回退

详细审查见 [1.10f 寻路碰撞对比](path-collision-upstream-comparison-2026-10-15.md)。
用户补充近期寻路优化同时参考本机 D2Client.dll、D2Common.dll 逆向；本轮按当前实际
调用链逐层比较，未否认或撤回近期原生碰撞优化，也未整包替换生产搜索器。
本轮沿用上述已 fetch 的远端基线，未再次 fetch，不声称覆盖当天全部上游新提交。

### 实施范围

- 按 `47687ceb404f99dae79da70cbb7757d4f5eb2fdf` 及本机1.10f `0x6FDA9250`：
  size<0 或 >=4 提前退为 ordinary SMALL_UNIT_PRESENCE，在 town/interact 转PET之前返回。
  修正动态网格写入/查询/诊断与move回滚；非法尺寸的town单位不再被玩家误忽略。
- 保留正常尺寸、size0兼容、静态地形和生成落点接口。size API 不等于 pattern API，
  不将本轮修正扩大到全部尺寸/出生逻辑。
- 新增3项修复回归，覆盖MIN/-1/4/MAX、移除、回滚、规划/实际移动一致性和合法big pet恢复。
  修改前独立报告3项全部失败，修改后通过。
- 新增2项差异特征测试：共享碰撞查询时 blocked coordinate goal 的 native partial
  与普通runtime exact差别；size1 static size查询与native presence pattern查询差别。
  这是记录当前合同差异，不要求将来永久保留差异，不是执行原版DLL对比。

### 对比结论

- 生产普通入口仍为 `Map → MapGraph → AStarPathFinder`；独立D2AStar/IDAStar/Step
  已移植但未直接接入这条链。原生候选pattern、门、飞行、NO_PATH/PET、死亡等近期优化已存在。
- D2MOO在直2/斜3、邻居/tie、200分配预算、partial、转向压缩、目标状态机和定点步进
  上更贴近原版；生产仍有欧氏A*、不同循环预算、额外平滑和恢复策略，不能只换heuristic。
- 静态目标已不周期重算；DiabRun已有opt-in partial；不重复移植。size1静态查询、
  原生逐步状态仍待专项接入。1.13+目标房间gate不适用于本项目1.10f。
- 没有benchmark；不能声称D2MOO一定更快。建议下一轮先利用现有独立移植做只读shadow
  差分，区分搜索规则与碰撞适配，之后按path type接入；本轮未实施shadow或速度积分替换。

### 第四批验证

显式 `D2_HOME/D2_TEST` 指向本机1.10F目录：边界修改前3失败，修复后扩展105项通过，
两项比较测试单独通过；最终合并 **107项 / 21类通过，失败/错误/跳过0**。
本机临时Gradle init注册 `upstreamRound4Test` 隔离报告，不纳入仓库。
独立XML目录分别为 `core/build/test-results/upstreamRound4-boundary-baseline`、
`upstreamRound4-movement`、`upstreamRound4-comparison`、`upstreamRound4-final`，
HTML在对应 `core/build/reports/tests` 目录。最终不是将多次运行重复累加后的数量。

另一台电脑无并发时，可用普通任务复验同一107项集合：

```powershell
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
$env:D2_TEST = $env:D2_HOME
.\gradlew.bat :core:test `
  --tests '*UnitCollisionGridTest' --tests '*DynamicUnitCollisionSystemTest' `
  --tests '*PathfinderTest' --tests '*PathfinderRoomBoundaryTest' `
  --tests '*PlayerGroundPathTest' --tests '*NativeSeamCollisionTest' `
  --tests '*ObjectCollisionUpdaterTest' --tests '*WhirlwindCollisionTest' `
  --tests '*WarpArrivalCollisionTest' --tests 'com.d2moo.common.path.*' `
  --tests 'com.d2moo.common.collision.*' --tests '*NativeTowardPathProbeTest' `
  --tests '*NativeAiPathCacheTest' --tests '*NativePathRuntimeComparisonTest' --console=plain
```

未运行全项目/TCP/GL/132图巡游/25Hz/多seed×三难度；未执行原版DLL动态差分或性能压测。
代码/测试提交 `776bd89c3175f8fc035a348d5e8dbd3b4e25314e` 已推送origin/master，远端SHA一致。
提交前无其他暂存变更，仅包含两份生产文件和三份测试；详细报告/进度另行提交。

## 第五批：只读寻路 shadow 差分

这是第四批建议的诊断接入，不是再次移植上游功能或切换生产路径。
新增 `NativePathShadow`，普通/partial入口在权威搜索完成后捕获有界碰撞快照，
独立重放生产A*与已有D2AStar；不改路径选择、正常碰撞、平滑、位置、速度、RNG。
同分/预算/partial等仍保持现有生产行为。默认关闭，仅显式采样时创建快照和重放。

捕获保留门规则、原生seam、重叠地图owning-zone和动态阻挡/忽略目标predicate。
生产重放输出先与实际原始点序核对，不一致或触及快照边界则标为不可比较；
结果包含exact/partial/failed、原始/压缩点序、查询/节点计数及非benchmark耗时。
原生参考只使用coordinate目标，不伪造targetUnit八点approach或完整原生状态机。
每边最多128格、默认每128请求采1次/最多16次尝试（上限128），异常不改变权威输出。

夹具观察：

- 空地 `(10,10)→(20,17)` 双方EXACT，但同方向压缩后生产6个转向点、参考2个；
  候选查询240/80，唯一/分配节点70/48。这是固定夹具，不外推实机性能或最终平滑观感。
- 被挡坐标的普通入口FAILED/参考PARTIAL，partial入口双方到`(19,19)`且压缩输出一致。
- 门夹具及原生seam双方EXACT且压缩输出一致；门上叠加普通对象仍阻挡。
- 36单位动态占位夹具生产预算内FAILED，参考仅PARTIAL，并非原生成功穿过密集人群。
  忽略目标、owner、飞行mask、重叠地图作用域另有回归。
- 覆盖关闭/采样封顶、禁用后查询数恢复、无RNG消耗、node/metrics不污染、输出不可变、
  变化输入/超大范围/有限边界标记、异常隔离、非A*入口不误标、同点native空flush合同。

运行方式与状态含义见 [详细报告后续章节](path-collision-upstream-comparison-2026-10-15.md#后续第五批只读-shadow-已接入尚未切换生产路径)。
另一台电脑可复验第四批107项集合，再增加 `--tests '*NativePathShadowTest'`；
跨接缝类本轮另增1项。报告采用临时init隔离，最终XML在
`core/build/test-results/upstreamRound4-round5-final`，不覆盖第四批历史报告。

最终 **129项 / 22类通过，失败/错误/跳过0**：上批107项+新类21项+接缝1项。
初轮15项、扩展128项均已通过，最终数量不重复累加中间运行。
本机另运行（不是原版客户端或GL窗口）：

```powershell
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
$env:D2_TEST = $env:D2_HOME
.\gradlew.bat :server:d2gs:headlessMovementIntent `
  -PpathShadow=true -PpathShadowEvery=1 -PpathShadowMaxSamples=4 --console=plain
```

真实TCP移动意图通过：seed12345，player117，`(132,37)→(135,37)`、flags9/size2，
shadow=COMPARED，双方EXACT且turns均`[(135,37)]`；候选查询各24、节点各15。
`sequence=1 observedTick=7 targetTick=8 acknowledgedTick=9 tickLag=1 wallMillis=74`。
这是单次ACK冒烟，不是关闭/开启配对性能测试；没有据此评价延迟改善。
完整运行输出在未提交的 `core/build/round5-shadow-tcp.log`，服务端正常退出，6114监听已释放。

代码/测试/启动开关提交 `08fa479b6e6d6f6802a162aa77d0c2cdc636646c` 已推送origin/master，
远端SHA核对一致；提交只含本轮6份代码/测试/Gradle文件，进度报告随后独立提交。

尚未开展全项目、GL/132图巡游、多seed×三难度或原版DLL动态逐帧对比；
非benchmark，尚未做native pattern替换/目标状态机/pathType切换。
下一步应扩展真实狭路/怪群/移动目标样本，先解释exact/partial合同和点序差异，再选择局部原生接入。

## 第六批：真实地图采样与差异分类

### 修改范围

`NativePathShadow`新增可选来源关卡过滤，JVM属性`riiablo.path.shadow.level`默认-1（全部），
D2GS JavaExec通过`-PpathShadowLevel=63`转发。按搜索碰撞作用域解析来源关卡，
未匹配的请求不消耗采样频率或额度，避免城镇用完野外的128次硬上限。
新增关卡/房间整数元数据（未知=-1）及障碍predicate有无，不保留Zone/Room引用；
房间读取用索引遍历，不干扰外层libGDX Array迭代器。

日志明确`stage=MAP_SEARCH_RAW`：采样发生在MapGraph搜索结束、生产房间后置gate、
视线平滑和Pathfind安装之前，不能把shadow成功都说成最终接受并移动。
`dynamicObstacle=true`只表示存在Obstacle回调，不证明每个样本都实际撞到单位。

新增`difference()`：不可比较为INCONCLUSIVE；可比较时优先OUTCOME（EXACT/PARTIAL/FAILED
不同），再ENDPOINT（同为非失败但终点不同），再TURNS（压缩点序不同），否则NONE。
这是机械分类，不自动认定原因、缺陷或哪一方更优；原有`differs()`布尔含义保持。
默认关闭、快照/采样上限、当前effective collision、coordinate目标模式均未改变；
没有接入生产原生搜索/目标状态机，也未修改近期DLL逆向优化。

### 定向回归

新增4项：来源过滤保留采样额度/owning-zone/元数据脱离、房间外层迭代器不受影响、
动态目标移动与新增阻挡后的独立快照、partial不同终点分类。既有用例补齐其余分类断言。
合并上一批集合，最终**133项/22类通过，失败/错误/跳过0**；报告：
`core/build/test-results/upstreamRound4-round6-final`。

实际使用第五批同一本机隔离init，以`-PupstreamRun=round6-final`运行以下过滤集合。
另一台电脑可直接运行仓库标准`:core:test`（结果在其标准报告目录，不依赖本机临时init）：

```powershell
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
$env:D2_TEST = $env:D2_HOME
.\gradlew.bat :core:test `
  --tests '*UnitCollisionGridTest' --tests '*DynamicUnitCollisionSystemTest' `
  --tests '*PathfinderTest' --tests '*PathfinderRoomBoundaryTest' `
  --tests '*PlayerGroundPathTest' --tests '*NativeSeamCollisionTest' `
  --tests '*ObjectCollisionUpdaterTest' --tests '*WhirlwindCollisionTest' `
  --tests '*WarpArrivalCollisionTest' --tests 'com.d2moo.common.path.*' `
  --tests 'com.d2moo.common.collision.*' --tests '*NativeTowardPathProbeTest' `
  --tests '*NativeAiPathCacheTest' --tests '*NativePathRuntimeComparisonTest' `
  --tests '*NativePathShadowTest' --console=plain
```

partial终点夹具：110格合成空地、(5,5)→被挡(95,95)、size1，双方PARTIAL，
runtime终点(94,95)，reference终点(44,44)，参考分配200节点。
这说明两者预算及partial选择差异具有实际输出影响，也反驳“原生一定更靠近目标”；
是合成特征回归，不是原版DLL动态执行或真实MPQ结果。

### 真实MPQ/TCP采样

均显式指定本机1.10F、seed12345、difficulty0、25Hz、每匹配请求采1次、最多128次。
下列两组为主统计，不合并先前单图预热的2次样本；不是多seed/多难度、全图或原版客户端验收。

蛆虫二/三层及古代通道63/64/65：整个任务通过，3图/4个walk计划/10次真实TCP击杀/0通行失败；
连续入口1次、测试staging跳转1次，不把非连续跳转算成步行连接证据。
AI gate通过，fallbackRecords=0、errors=0；观察模式仍不等于完整AI正确性oracle。
sourceLevel=63过滤，128次样本中92次COMPARED：45次EXACT/EXACT且压缩输出一致，
25次EXACT/EXACT但转向点不同，22次FAILED/FAILED且输出一致；36次快照外不判定。
真实清障和动态回调未关闭，未用位置包/清怪作弊恢复。

火焰之河107/108：通行子检查通过（2图/4个walk计划/1次连续入口/0次击杀/0通行失败），
**整个JavaExec任务失败**：AI gate报告fallbackRecords=148、errors=0，
`ai-summary.txt`为FAILED_OR_INTERRUPTED。日志可见GenericMonster的Megademon、DoomKnight、
OblivionKnight、FingerMage等fallback路径；本批不修AI分派，也不把该运行记为全通过。
sourceLevel=107过滤，109次样本中103次COMPARED：38次EXACT/EXACT且输出一致，
32次EXACT/EXACT但转向点不同，33次FAILED/PARTIAL；另6次快照外不判定。

两组合计237次：195次COMPARED（105 NONE、57 TURNS、33 OUTCOME），42次越界。
DIAGNOSTIC_ERROR、LIVE_REPLAY_DIFFERENCE、SNAPSHOT_LIMIT均0。
这些是请求次数，包含同一实体反复重算，不是237条独立路线或33个独立缺陷。
没有可比较的runtime FAILED/reference EXACT样本。

关键可复核样本：

- 蛆虫sample4/12/28/41，mover180、target117，来源(12587,1584)未变，
  目标依次(12572,1556)/(12565,1567)/(12557,1581)/(12561,1587)，均双方FAILED。
  证明观察到移动玩家的坐标请求，不证明原生目标预测/八点approach/重算定时器等价。
- 火焰之河sample1，megademon2/mover249、target117、size3、openDoors=true，
  (2840,1266)→(2830,1290)，sourceRoom3/targetRoom0。runtime FAILED，候选查询2408；
  reference PARTIAL，分配200节点，末点(2828,1288)，不是目标。
- sample89，同关卡(2819,788)→(2797,769)，两端room76，runtime FAILED，
  reference PARTIAL末点(2797,771)、分配200节点。同房也存在此输出合同差异。

这些参考partial从未装入生产；是否能通过怪物房间后置gate、是否会前进/反复追逐，
还需要按原版pathType和目标状态机做单独验收。原生压缩转向点少也不等于最终动画转弯少，
因为现实现随后还有平滑；查询/纳秒不是公平性能benchmark。

另一台电脑采样（输出目录必须不存在，复跑更换目录名）：

```powershell
.\gradlew.bat :server:d2gs:headlessMapTour '-PtourLevels=63,64,65' `
  -PtourSeed=12345 -PtourRate=25 -PtourWalkSeconds=20 `
  -PtourAiObserve -PtourAiObserveMillis=2000 `
  -PtourOutput=server/d2gs/build/round6-shadow-maggot-chain `
  -PpathShadow=true -PpathShadowEvery=1 -PpathShadowMaxSamples=128 `
  -PpathShadowLevel=63 --console=plain
```

火焰之河使用同组参数，将Levels改为`107,108`、Level改为`107`、
Output改为`server/d2gs/build/round6-shadow-river-retry`；保留AI gate，不绕过已发现的fallback失败。
采样频率和AI交互受运行时序影响，不要求重复请求数逐次完全相同。

本机完整日志（build下不提交）：`core/build/round6-shadow-maggot-chain.log`、
`core/build/round6-shadow-river-retry.log`；对应tourOutput内有map-tour/ai-events/ai-summary。
前置失败同样保留：首个蛆虫单图在正式连接前Connection refused、未采样（原因未定）；
重试单图通过。首个river在打包时失败，当时另有COF Gradle构建共用输出目录，
未进入采样；串行重试取得上述结果。没有通过改寻路/放松碰撞来消除失败。
两次主运行后服务端退出，6114无监听。

代码/测试/Gradle提交`033b2e9d94e26b7d0c60f9be530e38c1b6282adb`已推送origin/master，
远端SHA一致。未纳入另一轮未提交的`CofModeTransitionTest.java`及随后出现的
`CofLayerCacher.java`/`CofLayerLoader.java`修改；本轮回归不为这些COF改动背书。
本轮四份进度文档独立提交推送，最终SHA见git log，不宣称整个工作区clean。
本批沿用既有固定D2MOO/DLL审查基线，未重新fetch或新增DLL动态等价证明。

## 第七批：部分路径消费核对与恢复目标状态修复

### 原版调用关系与静态交叉核验

本轮读取D2MOO固定 `origin/master=0c0bbd1e6bef9181a3da1c046523af622a925416`，
未fetch，未改本地旧检出；不声称已检查当天全部上游更新。证据来自下列源码及本机
合法1.10F DLL的局部静态反汇编，不是运行原版DLL逐帧差分。

- `D2Common/src/Path/Path.cpp`：dispatch中type0/16是IDA*、1是A*、2/5/6/13是Toward、
  7是Straight、15是绕墙。`D2Common_10142`上游明确标BROKEN，不能独立当oracle。
  调用前保存final target，生成后返回点数；无flags0x10且无pTargetUnit时才以末点改target。
  单位目标身份独立于生成路径点，非空输出不保证exact到达。
- `AStar.cpp`：exhausted/budget可flush bestNode，压缩输出1..77点有效，0或>=78拒绝。
  这支持PARTIAL合同，但不支持把所有普通exact失败直接换成partial成功。
- `PathMisc.cpp`的`D2Common_10236`：非2/13/15按原type重算；2/13/15依参数选Toward2或
  TowardFinish13，失败再转15。保留同一dynamicpath/pTargetUnit。10226的内联检查会在
  路点耗尽且未到final goal时续算；player/monster目标位移X或Y大于5也参与判断，
  circle5/6有特例。不能简单解释为“点耗尽即已到达”。
- `D2Game/src/MONSTER/MonsterMode.cpp`：移动hint101默认TowardFinish13，AI可override；
  `sub_6FC65680`在2/7/9/13无点后转15。`PathWF.cpp`的type15是Bresenham/绕障/简化，
  可能给可用前缀，**不是A* fallback**。本轮没有把coordinate shadow扩写成全怪物dispatch。

本机DLL目录：`G:\BaiduNetdiskDownload\Diablo II 1.10F`。
使用`C:\Strawberry\c\bin\objdump.exe -d -Mintel`核验下列未重定位VA（非文件offset）：

- D2Common SHA256：`B9D1A27C6A35992932C7C47438D02E5269F878E899F6E614D507902CECD1BEAB`。
  `0x6FDA8CAA..8D11`保存final +18/+1A、清索引+24/点数+28、调用type表`0x6FDD1F40`；
  `0x6FDA8D7E..8DB9`保存prevtarget +14/+16，检查flags0x10及pTargetUnit +58，
  无单位目标才以末点改target +10/+12。
  `0x6FDAC8F0..C98F`核实2/13/15选择及失败转15；
  `0x6FDACA5C..CA93`核实无单位目标时耗尽但未到final的恢复；
  `0x6FDACAAF..CB2C`核实单位目标、位移>5及耗尽恢复。BX/BP保存当前坐标，并非零。
  type15入口为`0x6FDAC270`。
- D2Game SHA256：`2BD92FB25E3E4B8269930712620BA9DB00CDC1AA44569D87DC4ABA76553272B6`。
  `0x6FC65680..65767`：`0x6FC656ED`计算路径，`0x6FC65721..6575D`无点分支选择type，
  push0xF再计算。以上只支持局部调用关系，不宣称整个引擎动态等价。
- 本轮未新增D2Client分析，保留此前D2Client/D2Common逆向优化；不引入1.13+target-room gate。

### 实际缺陷与修复范围

`Pathfinder`过去只在public请求成功后补targetEntityId、lastTargetPosition和repathTimer。
内部动态恢复绕过该入口：直接恢复保留旧快照/timer；先停止再绕行会删除旧Pathfind，
重建组件的targetEntityId变成-1，导致追单位变成走到绕行坐标，之后目标移动/定时重算丢失。

现在在核心路径通过房间后置gate、平滑和fractional起点处理后，统一安装目标metadata，
删除public入口的重复赋值。绕行末点仍按原策略选择，单位身份独立保留；新坐标命令清空
旧实体跟踪。Running和模式速度加成继续由原恢复逻辑保留。

本轮不是原生搜索backend接入，不改变A*、碰撞pattern、门/飞行/动态owner、平滑、
房间前后置gate、步进或partial策略。普通路径仍要求exact，DiabRun显式partial仍走固定goal；
不移植原生distance budget/定点状态，也不把当前0.5秒timer/0.5格目标位移/0.25秒阻挡阈值
说成等价于原版5格判断。

### 第七批回归与另一电脑复验

新增`PathfinderRecoveryTargetTest`最终9项：直接恢复刷新快照/timer、stop/recreate保留单位、
绕行后目标移动和定时重算实际发起新查询、新坐标清除旧目标、全失败停止且partialQueries=0、
DiabRun保留技能goal不追旧单位、内部路径改追另一单位、raw path绕入非邻房被后置gate拒绝。
真实Artemis ECS与Pathfinder恢复/消费配scripted Mockito Map及mock动态服务，
不是实际地图搜索/碰撞、TCP或原版DLL执行。

修改前首6项中4失败/2通过；修复后最终9项全通过。合并定向回归153项/25类，
失败/错误/跳过均0，含现有DiabRunData/Integration测试；不是全项目验收。
本机通过隔离init任务`:core:upstreamRound4Test -PupstreamRun=round7-full`执行，
XML为`core/build/test-results/upstreamRound4-round7-full`，日志`core/build/round7-full.log`；
修前失败报告保留于`upstreamRound4-round7-before`及`core/build/round7-before.log`。
另一电脑可用标准任务复验同一过滤集合（标准任务本轮未另跑）：

```powershell
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
$env:D2_TEST = $env:D2_HOME
.\gradlew.bat :core:test `
  --tests '*UnitCollisionGridTest' --tests '*DynamicUnitCollisionSystemTest' `
  --tests '*PathfinderTest' --tests '*PathfinderRecoveryTargetTest' `
  --tests '*PathfinderRoomBoundaryTest' --tests '*PlayerGroundPathTest' `
  --tests '*NativeSeamCollisionTest' --tests '*ObjectCollisionUpdaterTest' `
  --tests '*WhirlwindCollisionTest' --tests '*WarpArrivalCollisionTest' `
  --tests 'com.d2moo.common.path.*' --tests 'com.d2moo.common.collision.*' `
  --tests '*NativeTowardPathProbeTest' --tests '*NativeAiPathCacheTest' `
  --tests '*NativePathRuntimeComparisonTest' --tests '*NativePathShadowTest' `
  --tests '*DiabRunDataTest' --tests '*DiabRunIntegrationTest' --console=plain
```

真实MPQ/TCP非回归复验：

```powershell
.\gradlew.bat :server:d2gs:headlessMapTour '-PtourLevels=63,64,65' `
  -PtourSeed=12345 -PtourRate=25 -PtourWalkSeconds=20 `
  -PtourAiObserve -PtourAiObserveMillis=2000 `
  -PtourOutput=server/d2gs/build/round7-recovery-maggot `
  -PpathShadow=true -PpathShadowEvery=1 -PpathShadowMaxSamples=128 `
  -PpathShadowLevel=63 --console=plain
```

63=Maggot Lair Level 2、64=Maggot Lair Level 3、65=Ancient Tunnels，即蛆虫二/三层及
古代通道，不是蛆虫三层连通检查。seed12345普通难度25Hz，3/3图、4个walk计划、
10次真实TCP击杀、0通行失败，连续入口1次/staging跳转1次，BUILD SUCCESSFUL。
AI观察run=COMPLETED、errors=0、fallbackRecords=0，warnings=178；
coverage=OBSERVED_RUNTIME_ONLY、oracle=NONE、correctness=NOT_ASSERTED，不是AI全分支验收。
`MONSTER_PATH_STALLED`日志0次，**没有实际触发本轮stop/recreate分支**，该分支证据来自上述
定向ECS回归。巡游只算真实地图非回归冒烟，不声称动态目标恢复已获TCP验收。

来源63的shadow128次：83 COMPARED（55 NONE、28 TURNS；60 EXACT/EXACT、23 FAILED/FAILED），
45 OUTSIDE_SNAPSHOT/INCONCLUSIVE；无诊断错误/live重放差异，没有FAILED/EXACT或FAILED/PARTIAL。
与第六批不同请求计数是运行时序结果，不能作为性能提升/缺陷减少证明。
完整日志`core/build/round7-recovery-maggot.log`及上述tourOutput内map-tour/ai-events/ai-summary，
build证据不提交。服务端已退出，6114无监听。

代码/测试`b0a1f4912a52f2ee4abf0ec4d78fab392d0003d3`已推送origin/master且远端SHA一致。
仅定向暂存自己的两个代码文件，另一轮COF修改未混入；共享工作区回归不为其背书。
四份进度文档独立提交推送，最终文档SHA见git log；不把他人修改当作本轮交付。
没有benchmark/GL/132图/多seed三难度/原版动态等价验收；火焰之河AI fallback本轮未修、未重跑。

## 第八批：绕行路点耗尽后的实体目标续算

### 原版依据与当前缺口

继续使用固定D2MOO `origin/master=0c0bbd1e6bef9181a3da1c046523af622a925416`，本轮未fetch。
`PathMisc.cpp::sub_6FD5DB70`在单位目标尚未进入nStepNum范围时，除目标位移判断外，
还检查路点索引是否耗尽及当前位置是否等于tFinalTargetCoord；未到final则调用10236续算。
10236还受native distance等状态控制，不能只移植耗尽检查便宣称完整原生行为等价。

重新核对本机1.10f D2Common.dll（目录及SHA256与第七批相同，已重算哈希一致）：
`0x6FDACAAF/AB3`先保存当前位置BX/BP；`0x6FDACB0E..CB16`比较+24索引与+28点数；
`0x6FDACB18..CB22`比较+18/+1A final与当前坐标；不等则`0x6FDACB24..CB27`
push1、push单位并调用`0x6FDAC8F0`。这是未重定位VA、局部静态证据，非原版动态执行。
上游10142仍标BROKEN，不单独当oracle；没有引入1.13+房间gate或5格阈值替换。

第七批已修复恢复后丢失targetEntityId/目标快照/timer，但当前process在定时器未到期时，
仍会把“所有绕行路点已走完”直接当作“命令完成”清除Pathfind，即使目标仍在范围外。
本轮仅修补这一局部：已有实体目标通过既有攻击/交互范围检查后，若无剩余路点、
当前位置已到当前路点（原0.1 epsilon）、且与live目标不同取整格，立即复用
`repathPreservingMovementIntent`尝试exact及既有动态绕行，失败照常停止。
新增DEBUG `[ENTITY_PATH_CONTINUE]`只记录尝试，不表示续算一定成功。

仍保留同一单位身份、Running和模式速度加成；已入攻击/交互范围优先停止，
未到末点/仍有后续路点不会因新条件提前续算。普通坐标命令及DiabRun没有实体目标，
不因此新增partial或重算；原房间前后置gate、搜索、碰撞、平滑、步进及timer/位移阈值不变。
本轮目标坐标来源是live实体，使用现有Map.round及范围合同，并非新增native
tFinalTargetCoord/nStepNum/distance budget模型，也不是原生搜索backend接入。

### 第八批测试与交付

`PathfinderRecoveryTargetTest`增加9项：耗尽后timer未到仍向单位发起查询、
direct仍阻挡时选择另一可用绕行点、全失败确有查询然后停止且不partial、
已入近战范围停止、未到末点不提前重算、到达中间点仍消费下一路点、
范围外静态交互目标续算、坐标命令耗尽不新增目标、DiabRun partial消费不隐式追单位。
为隔离该状态传递，scripted Map允许指定唯一可达绕行点(26,6)，目标为(30,10)，
测试直接设置actor到达末点再调用真实process；动态服务为mock，非真实逐步地形/TCP移动。

修前该类18项有4失败/14通过（新增9项中4失败/5通过）；修后18项全通过。
最终合并162项/25类通过，失败/错误/跳过0，过滤集合与第七批相同，含现有DiabRun集成测试。
本机隔离任务`:core:upstreamRound4Test -PupstreamRun=round8-full`，
XML `core/build/test-results/upstreamRound4-round8-full`、日志`core/build/round8-full.log`；
修前证据在`upstreamRound4-round8-before`/`core/build/round8-before.log`，
修后单类在`upstreamRound4-round8-recovery`/`core/build/round8-recovery.log`。
另一电脑先显式设置合法1.10F资源，可最小复验：

```powershell
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
$env:D2_TEST = $env:D2_HOME
.\gradlew.bat :core:test --tests '*PathfinderRecoveryTargetTest' --console=plain
```

完整复验使用第七批`:core:test`的相同filters；标准任务本轮未另跑，不依赖本机临时init文件。

真实MPQ/TCP命令与第七批相同，仅输出改为
`-PtourOutput=server/d2gs/build/round8-endpoint-maggot`；全日志
`core/build/round8-endpoint-maggot.log`，tourOutput内含map-tour/ai-events/ai-summary，均不提交。
普通难度seed12345、25Hz、63蛆虫二层/64蛆虫三层/65古代通道，3/3图、4个walk计划、
1连续入口+1 staging跳转、10次真实TCP击杀、0通行失败，BUILD SUCCESSFUL。
AI run=COMPLETED、warnings=178、errors=0、fallbackRecords=0；
coverage=OBSERVED_RUNTIME_ONLY、oracle=NONE、correctness=NOT_ASSERTED，不是完整AI正确性验收。
ENTITY_PATH_CONTINUE及MONSTER_PATH_STALLED日志均0，未触发新续算或stalled分支；
本次真实巡游仅为非回归冒烟，新分支证据来自scripted ECS定向回归。

来源63 shadow128次：65 COMPARED（55 NONE、10 TURNS；44 EXACT/EXACT、21 FAILED/FAILED），
63 OUTSIDE_SNAPSHOT/INCONCLUSIVE；无诊断错误/live重放差异，无FAILED/EXACT或FAILED/PARTIAL。
与前轮计数不同受运行时序影响，不据此宣称算法提升或缺陷减少；没有性能benchmark。
服务端退出，6114无监听。未修复/重跑火焰之河AI fallback；无GL/132图/多seed三难度或原版动态等价验收。

代码/测试`473bd6ba52d17fc8372c328f55f2f0da740d472f`已推送origin/master，远端SHA一致。
只定向暂存自己的两个代码文件；另一轮对象选择提交`13fb4568a`保持独立，非本轮移植/验收。
四份文档独立提交推送，最终文档SHA见git log。下一步仍需完整distance/final goal/续算状态
及实际pathType的受控接入，不能把局部目标恢复修复当作已经切换原生路径系统。

## 第九批：Toward 原始路点生成内核

### 寻路是否已完成

用户要求：若寻路未完成就继续，完成后才转其他D2MOO更新。核对结论是**未完成**。
此前恢复目标身份/耗尽续算已交付，但普通生产入口仍是Map→AStarPathFinder，
独立A*/IDA*库、布尔Toward探针及只读shadow并不等于原生路径系统已接入。
本轮继续补原版常用Toward分支，不转到无关更新，也不标记全部寻路完成。

### 本轮移植与原版依据

新增`D2MOO_JAVA/src/main/java/com/d2moo/common/path/D2Toward.java`，
返回不可变raw点列，支持PATH_Toward零方向偏移及sub_6FDAA720的非零偏移分支。
要求调用方已准备好坐标目标、初始归一化后速度仍非零，并提供稳定只读碰撞回调；
不在该内核里猜测targetUnit八点approach、flags或room/pattern/mask。

本轮已执行`git fetch origin`，D2MOO远端仍为
`0c0bbd1e6bef9181a3da1c046523af622a925416`，未重置本地旧检出。
核对`source/D2Common/src/Path/PathMisc.cpp`的sub_6FDAA720、sub_6FDAA880、
PATH_Toward_6FDAA9F0、PATH_RayTrace；`Path.cpp::PATH_SetNewDistance`将nDistMax限制在77。
本机1.10f D2Common.dll目录/哈希沿用第七批，静态复核：

- `0x6FDAA9F0..AA18`清索引/点数、非零+98偏移跳转`0x6FDAA720`。
- `0x6FDAAA19..AA81`初始射线和field_14容差；射线清晰或裁剪后已近目标时，
  可直接写一个点，不受局部nDistMax预算限制。
- `0x6FDAAA82..ABD9`保留射线可达前缀、三候选局部步进、禁止180度反转，
  `0x6FDAAB80..AB96`只在非原始起点时追加转向点，`0x6FDAABB9..ABD9`区分退出原因。
- `0x6FDAA720..A87B`的非零偏移分支跳过初始射线、旋转三候选并可写源点，
  与零偏移不同；本轮也保留uint16点坐标加法的回绕。
- 原始路点可以重复、仅含源点，或省略最后一次刚转向的步进点，即使试探坐标已到目标。
  不用“看起来更合理”的A*压缩/去重/补终点替代它；非空、可移动和末点等于请求坐标分开表示。

以上是局部静态证据及源码对照，非运行原版DLL。上游10142仍标BROKEN，
不能凭该内核或一个布尔结果宣称完整动态路径包装器已等价。
API拒绝非法距离/容差及非uint16坐标，而不是模拟任意损坏内存输入；
接受的distance为0..77、tolerance为0..255，方向旋转按低三位处理。
不支持在已有路径上累计BackupTurn点列、不做WallFollow15、单位目标准备、距离消费、
速度归一化或生产分派；调用方不能跳过这些状态便把raw点直接装进Pathfind。

本轮未修改生产Map/Pathfinder/碰撞/房间/平滑/步进，未替换Baal现有低成本布尔探针，
无游戏RNG变化，不引入1.13+目标房间gate，不改变普通exact或DiabRun partial策略。

### 第九批验证与交付

新增`D2TowardTest`16项，覆盖清晰/裁剪射线、源点与近距离容差、局部预算0/1/2、
受阻追加末点、射线前缀重复、最后转向输出省略、直线末点、±方向偏移、
零预算偏移、射线查询顺序/整轮回滚、三候选优先级、uint16回绕及反向拒绝、
不可变输出/非法参数，以及真实Java原生碰撞网格的pattern/mask权威性。

既有`NativeTowardPathProbeTest`新增1项4000组固定种子输入比较，预算0/1/2/14/77、
容差0..3、多种稳定碰撞密度，比较完整内核点列非空性/可移动性与既有零偏移布尔投影，
并比较射线裁剪终点/clear。coincident输入的raw源点与包装器不调用搜索的差别单独处理。
4000是单项JUnit内的输入数，不是4000项测试，也不是4000次原版DLL动态差分；
不证明完整路点序列、非零偏移、目标状态、flags或性能都与原版相同。

本机定向两类首轮通过，最终合并179项/26类通过，失败/错误/跳过0（第八批162+新增17）。
过滤集合与第七/八批相同，`com.d2moo.common.path.*`自动包括新类；
隔离任务`:core:upstreamRound4Test -PupstreamRun=round9-full`，XML
`core/build/test-results/upstreamRound4-round9-full`、日志`core/build/round9-full.log`。
单类阶段日志`core/build/round9-toward.log`/XML `upstreamRound4-round9-toward`是增加最后一项
原生碰撞网格回归之前的结果，以最终179项为准。另一电脑可最小复验：

```powershell
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
$env:D2_TEST = $env:D2_HOME
.\gradlew.bat :core:test --tests '*D2TowardTest' --tests '*NativeTowardPathProbeTest' --console=plain
```

完整标准任务复验采用第七批相同filters；标准任务本轮未另跑，不依赖本机临时init。
本轮没有新增MPQ/TCP/GL/全图/多seed难度、性能或原版动态等价验收，不沿用前轮巡游作新内核验收。
代码/测试`74c968123852915169b668838146c3f793cc09e9`已推送origin/master、远端SHA一致。
只暂存本轮三文件；四文档独立提交推送，最终文档SHA见git log。

### 核心寻路完成前仍需收口

1. 原生prepared target/unit身份、distance budget、final goal、路点索引及必要flags的消费/续算状态。
2. 按实际pathType适配Toward2/13、WallFollow15等，受控接入生产链，不整包替换普通A*或擅自升级partial。
3. 实际地形/动态碰撞下验收路径消费、失败、恢复和续算分支，保留既有1.10f碰撞/房间规则。

上述是核心生产链的未完成项；GL/全图/多seed三难度、性能及原版动态差分覆盖单列，
不能从一次冒烟外推。火焰之河AI fallback仍未修/未重跑，也不计作本轮Toward内核的效果。

## 第十批：WallFollow/type15 原生绕墙路点内核

### 实现范围

寻路仍未完成，按用户要求继续寻路。新增
`D2MOO_JAVA/src/main/java/com/d2moo/common/path/D2WallFollow.java`，实现已准备坐标目标后的
`PATH_ComputePathOrSlideAlongObstacles`及其双侧绕墙子路径；不是A*，不是生产Map替换。
API要求uint16坐标、distance 0..77及稳定碰撞回调；`hasTargetUnit`只实现nDistMax至少40，
不解析/持有单位身份，不执行单位目标准备、distance消费、final goal、flags或续算状态。
返回不可变raw前缀及原生simplify点列，coordinate reached单独表示；无游戏RNG或生产组件写入。

复用已有Bresenham和simplify，后者实现未改，只补充1.10f地址证据，纠正仅标1.13c的旧注释。
本轮未改生产碰撞、房间、平滑、步进和近期DLL逆向优化；不加1.13+目标房间gate，
不把普通exact失败自动升级partial，不替换Baal布尔Toward探针。

### 源码与本机1.10f证据

完整读取固定D2MOO`0c0bbd1e6bef9181a3da1c046523af622a925416`的
`source/D2Common/src/Path/PathWF.cpp`；本轮未fetch，不重置旧检出，不声称覆盖该引用以后的更新。
本机`G:\BaiduNetdiskDownload\Diablo II 1.10F\D2Common.dll`再次计算SHA256为
`B9D1A27C6A35992932C7C47438D02E5269F878E899F6E614D507902CECD1BEAB`。
使用objdump静态核对，未执行原版DLL：

- `0x6FDAC270`主入口；`0x6FDAC2E5..C308`单位目标距离提升40，
  `0x6FDAC330..C499`直线须完整容纳distance-1，否则失败，不裁剪为partial。
- `0x6FDAC4A4..C4BB`原始步进点数<=2直接返回0，连碰撞查询也不做。
  `0x6FDAC4C8..C529`遇阻传distance-index，回接使用更新后的index再自增，跳过一次查询。
  因此即便回调稳定，输出也可能含未检验的阻挡点；不能直接当另一套移动系统的安全路径。
- `0x6FDABAC0..BBF0`两个cache初始化及四方向表；WallFollow方向从北开始，不同于Toward。
  `0x6FDABC33..BD67`最多4候选；第四候选虽然设置dead-end调试标志，仍可成功。
- `0x6FDABD78..BE2E`按major轴接回原点列，`0x6FDABF4D..BFD5`原地替换并保留尾段。
  `0x6FDABE40..BE89`与对侧同步后反向走旧路即截断，避免闭环。
- `0x6FDABE8C..BEF0`预算比较为maxLength-originalTotal-1，任一侧finished即退出；
  syncIndex=0才轮换两侧。maxLength>80保护在本API的0..77入口不可达。
- `0x6FDAC0BE..C148`按signed int32平方距离择侧，同距选cache2；距离比全局原始start更远则返回0。
  复制best后返回的却是current计数，可能为0；本轮保留，不将其修正为best计数。
- `0x6FDAC170..C269`原生直线化，包括-2 delta标记，与现有Java helper一致。

尤其不能直接照搬D2MOO的empty-cache增强：上游无点时返回INT_MAX，并注明不等于原版。
DLL中cache1 base=esp+0x384、points=+0x3A8、finished=+0x3A4；cache2对应+0x3C/+0x60/+0x5C。
`0x6FDABFF3..C044`读取points[nPoints-1]，无点时实际读cache+0x20的finished DWORD，
即packed坐标(0,0)或(1,0)。`0x6FDABB19/BB5D`置0，`0x6FDABECD`置1。
Java显式建模该槽，不做越界数组访问；平方乘加保留int32回绕及有符号比较，不擅自改long。
测试使用target=(50006,10)区分空缓存原版负数距离与INT_MAX/long增强；不是任意损坏内存模拟。

### 第十批验证与交付

新增`D2WallFollowTest`20项，覆盖：

- 0/1/2步无查询、distance-1完整容纳、单位目标40下限、四主方向及斜向清晰线。
- 双侧查询顺序、四主方向回接、另一侧先回接、连续两处障碍和原尾段保留；
  回接跳过的点即便阻挡也不补查，原生raw与simplify分别断言。
- 预算只生成一侧点但返回另一侧0计数、空cache/int32溢出、同距选第二侧、第四候选可成功、
  四候选全阻立即停止、比全局start更远时拒绝、闭环、uint16回绕及X=0非扫描终止符。
- 真实Java原生内存碰撞网格的pattern/mask权威性、不可变输出/非法输入。
  另有单项5000组固定输入的终止/边界/确定性重放检查，覆盖近0/32768/65535坐标及稳定哈希碰撞场。

首轮17项通过；补齐三个用例并强化哈希场后最终20项通过，XML
`core/build/test-results/upstreamRound4-round10-wall-final`，日志`core/build/round10-wall-final.log`。
最终合并199项/27类通过，失败/错误/跳过0（第九批179+新增20），BUILD SUCCESSFUL。
路径/碰撞等filters沿用第九批，`com.d2moo.common.path.*`自动包含新类；隔离任务
`:core:upstreamRound4Test -PupstreamRun=round10-full`，XML
`core/build/test-results/upstreamRound4-round10-full`，日志`core/build/round10-full.log`。
5000是单个JUnit中的输入数，不是5000项测试或原版动态oracle；本轮没有新增MPQ/TCP/GL、
性能、全图/多seed难度验收，不沿用前轮巡游充当新内核验收。

另一电脑最小复验，不依赖本机临时init：

```powershell
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
$env:D2_TEST = $env:D2_HOME
.\gradlew.bat :core:test --tests '*D2WallFollowTest' --tests '*D2PathMiscTest' --console=plain
```

标准`:core:test`本轮未另跑；完整标准任务可采用第七/八/九批相同filters。
代码/测试`9d6ee2754952c0a215b616ee3aac70dc4da8514d`已推送origin/master、远端SHA一致。
只提交两个库文件及一个测试文件；另一轮Inferno/AnimStepper/FrozenHorror/AI arena修改未纳入。
四文档独立提交推送，最终文档SHA见git log。

### 下一步与完成门槛

Toward及WallFollow点生成内核已具备独立回归，但**寻路仍未完成**：普通生产仍Map→AStarPathFinder。
优先补prepared target/unit身份、distance/final goal/路点索引及flags续算状态模型，再按实际
pathType受控接入Toward2/13、WallFollow15等；不能把raw点非空直接转换成生产exact成功。
验收实际地形/动态碰撞的消费、失败、恢复和续算，保留近期1.10f房间/碰撞优化。
火焰之河AI fallback未修/未重跑；GL/全图/多seed三难度、性能及原版动态差分继续单列。

## 第十一批：阻挡终点准备及门目标偏移

### 范围与原版证据

继续未完成的寻路，本轮新增`D2MOO_JAVA/.../path/D2PathTarget.java`，补已解析坐标目标后的
`PATH_PreparePathTargetForPathUpdate`、玩家`PATH_FindValidTargetCoordsByMovingOrthogonally`，
以及独立`doorTarget`坐标偏移。准备结果是search/final候选，不是请求坐标、单位身份或previous快照；
`canSearch`只描述此准备阶段，不代表整个请求可移动、路径非空或exact到达。

固定D2MOO`0c0bbd1e6bef9181a3da1c046523af622a925416`，本轮未fetch。
读取Path.cpp的10142/UpdateTargetUnit/door/orthogonal分支、PathMisc.cpp的准备/碰撞候选及
PathUtil.cpp的单位坐标/提前量实现。10142上游仍标BROKEN，不能单独作为完整包装器oracle。
本机1.10f D2Common.dll再次核对SHA256仍为
`B9D1A27C6A35992932C7C47438D02E5269F878E899F6E614D507902CECD1BEAB`；objdump静态核验：

- `0x6FDA88BF..88E1`搜索前两轴差值均须<=100；本API要求调用方已通过该窗口。
  仍不实现房间/城镇/单位身份及零坐标/coincident等更早的完整请求gate，也不新增1.13+房间门槛。
- `0x6FDA8A75..8AB5`仅flag0x1000且目标碰撞时修正；无flag连目标格都不查询，目标清晰时
  不额外延伸玩家目标。missile mask0x40000在原包装器提前分流，本API显式拒绝该输入。
- `0x6FDA8B13..8B45`三个候选初始都在原目标；`0x6FDA8B4E..8C4E`按固定优先级累计，
  主方向以当前primary→source重算，另外两条保持初始方向；第三方向0xFF不查询，不是八向flood-fill。
  坐标加法按uint16缩窄后查询。方向/helper表`0x6FDAB6A0..B806`和`0x6FDD2288`也已核对。
- `0x6FDA8C75..8C90`先写selected到search target，再比较selected AX/CX与start；选中源点时
  返回失败但保留这次target写入。若primary在全阻时到达source，循环退出而未选点，则原target不改。
  **上游此处第二次比较的是candidates[0]，不是selected，与本机DLL不同**。本轮按DLL；
  有primary到源点被阻、secondary到(12,12)可行的夹具，区分两种行为。
- `0x6FDA8C94..8CA5`只对player调用`0x6FDA8FE0`延伸；9x9表位于`0x6FDD1DF8..1F3B`，
  X直接索引、Y转置索引，可能斜向，不是只能沿四轴。距离为两轴绝对差最大值，0或>=5不延伸。
- `0x6FDA907B..90AC`按初始表偏移逐步延伸到距离5，遇阻保留最后接受点。
  **DLL碰撞参数使用int32 edi/ebx累积值，仅成功时WORD写入缩窄**，不会每轮先缩窄再查询。
  因而近0/65535的查询可为-1、65536等；上游uint16 tCoords写法不可直接当边界oracle。
- `0x6FDA90C0..9182`门orientationIsOne沿Y、其他沿X，source轴坐标>=door时+2，否则-2，
  保存为uint16；native返回容差2。`doorTarget`仅返回坐标，该容差仍由后续单位目标包装器设置。

API要求uint16源/目标、两轴差<=100及稳定碰撞回调；pattern/mask/room/footprint由调用方负责。
不在内核里修改碰撞图或猜测动态实体类型；玩家int32延伸查询语义在回调接口和测试中保留。
输出不是碰撞安全路径，不应跳过完整包装器和既有生产后置验证直接安装Pathfind。
本轮未改生产Map/Pathfinder/近期碰撞、房间、平滑或步进，也没有游戏RNG变化。

### 第十一批验证与交付

新增`D2PathTargetTest`20项：flag门槛/清晰目标、三候选优先级、不可用第三方向、独立累计
及主方向重算、选中source与全阻失败不同写入语义、selected/primary分歧、coincident阶段边界、
玩家表延伸与阻挡停止/非玩家不延伸/斜向/距离5/100窗口、int32延伸与uint16候选两种回绕语义、
门方向/源侧/相等/边缘回绕、非法输入，以及Java原生内存碰撞网格pattern/mask权威性。
另含准备结果组合调用Toward/WallFollow库（非生产接入），并保留原请求坐标的夹具。
单项5000组固定输入覆盖近0/32768/65535，检查Manhattan查询上界、终止及确定性重放；
不是5000项JUnit或原版DLL动态差分。

首轮20项中19通过、1失败：边界夹具源点(1,10)对应的方向表没有第三方向，却假设存在west；
纠正为源点(0,10)，测试uint16第三候选回绕，未据此更改方向表或生产逻辑。
补充missile/窗口输入检查后最终定向20项通过。XML
`core/build/test-results/upstreamRound4-round11-target-final`，日志`core/build/round11-target-final.log`。
合并219项/28类通过，失败/错误/跳过0（第十批199+新增20），BUILD SUCCESSFUL。
隔离任务`:core:upstreamRound4Test -PupstreamRun=round11-full`，filters沿用第十批；XML
`core/build/test-results/upstreamRound4-round11-full`，日志`core/build/round11-full.log`。
本轮未新增MPQ/TCP/GL、全图/多seed难度、性能或原版动态等价验收。

另一电脑最小复验（无需本机临时init）：

```powershell
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
$env:D2_TEST = $env:D2_HOME
.\gradlew.bat :core:test --tests '*D2PathTargetTest' --tests '*D2PathMiscTest' --tests '*D2TowardTest' --tests '*D2WallFollowTest' --console=plain
```

标准`:core:test`本轮未另跑；完整标准任务仍可采用前几批相同filters。
代码/测试`d77c85d8330c69c3d406952e83a8af479bbf95a7`已推送origin/master且远端SHA一致。
本轮只提交两个新文件；共享工作区另一轮FrozenHorror/Inferno修改已独立提交`fc973fc50`，
不是本轮移植或AI专项验收。四文档独立提交推送，最终文档SHA见git log。

### 剩余门槛

**寻路仍未完成**，普通生产仍Map→AStarPathFinder。本轮仅实现坐标解析后的阻挡修正和门坐标偏移，
不能说完整单位目标准备已完成。仍需单位身份/动态静态位置解析、FOG LUT提前量/种类容差、
distance预算消费、final/previous/索引/flags续算模型，随后按实际pathType受控接入Toward2/13、
WallFollow15等并验收真实地形/动态碰撞的消费、失败、恢复及续算。不自动普通partial，不加1.13+ gate。
火焰之河AI fallback未修/未重跑；其余真实覆盖与benchmark/原版动态差分继续单列。

## 第十二批：距离字段与耗尽续算调度

### 范围与原版证据

继续未完成的寻路，新增`D2MOO_JAVA/.../path/D2PathContinuation.java`。这是动态路径相关字段的
独立可变投影，不是完整D2DynamicPath/10142包装器，也不接入生产Map或移动tick。
`decide`返回KEEP/STOP/REPLAN_COORDINATE/REPLAN_UNIT；`resume`移植10236；
`checkAndResume`组合两者，KEEP=1、STOP=0、重算保留搜索原始返回值。
KEEP与非零搜索返回值均不保证index<count，更不代替10226的active/velocity/碰撞/room门槛。

固定D2MOO`0c0bbd1e6bef9181a3da1c046523af622a925416`，本轮未fetch、不reset旧检出。
读取PathMisc.cpp的10236、sub_6FD5DB70、10226及Path.h字段定义，并结合Step.cpp消费片段定位
三个distance字段并非同一概念。本机1.10f D2Common.dll SHA256再次核对仍为
`B9D1A27C6A35992932C7C47438D02E5269F878E899F6E614D507902CECD1BEAB`。
objdump静态核验本轮实现的关键顺序：

- `0x6FDAA250..A25E`的10190仅写+0x90 nDist低字节；不改变+0x91 nDistMax或+0x94 nDistance。
  `0x6FDAA280..A2A4`的SetNewDistance先取输入低字节，再夹到77，同时写nDist/nDistMax；
  256输入得到0，不是77。`State`构造默认值只是零投影，不是native初始化流程。
- `0x6FDAA4E0..A51A`的PATH_SetDistance断言int在0..255后写+0x94；Java对非法输入抛异常。
  `0x6FDAA530..A565`把零扩展nDistance与addition作int32加法，再按符号夹0..255。
  例如1+INT_MAX先回绕为负数，结果0，不能用long饱和改成255。
  +0x93 nStepNum为单位目标停止距离；setter仅用于适配层赋已解析uint8，不宣称移植其目标容差计算。
- `0x6FDAC8F0..C91D`：无dynamicPath返回0；无flag0x10且owner为monster时，nDistance==0
  直接返回0，不刷新/置位/扣减/搜索。不是检查nDist或nDistMax。
- `0x6FDAC920..C93B`：先UNITROOM_RefreshUnit，再owner.flags|=DOUPDATE，再以-currentPointIdx
  调AddToDistance；扣到0仍继续本次搜索。flag0x10跳过上述monster门槛和全部三项副作用。
- `0x6FDAC940..C98F`：仅type2/13/15进入专用分支。a2非零直接赋type13并保留target；
  a2零赋type2、复制final到target。10142返回0则直接赋type15再调用一次；其他类型只调用一次。
  不是PATH_SetType，不重设type flags/dirOffset；第二次搜索保留第一次搜索对target/final/
  previous/索引/count/flags/预算等全部写入，不回滚到调用前快照，也不从头重新刷新/扣预算。
- `0x6FDACA41..CA56`：circle5/6且index>=count先返回TRUE，不再检查目标范围或final。
  `0x6FDACA5C..CA93`：无targetUnit时，仅耗尽且current!=final才按a2=FALSE续算。
- `0x6FDACA98..CABD`：单位距离10399<=零扩展nStepNum先返回FALSE；通过后才采样目标，
  包括object/item等静态目标。DLL在采样前保存current XY，本实现保留该快照顺序。
- `0x6FDACAC2..CB0C`：只有player/monster目标比较previous与采样坐标，任一轴绝对差>5
  才按a2=TRUE续算；恰好5不触发。uint16坐标先转普通整数相减，不是环形最短距离。
  `0x6FDACB0E..CB2C`：其余情况有剩余点或current==final则TRUE，否则按a2=TRUE续算。
  外层10226仍在此结果非零后检查index<count；不加入1.13的owner异步/flag过滤优化。

`Target`回调必须由后续适配层提供真实单位身份、D2Common_10399距离和sub_6FDAB940采样
（含适用时的FOG LUT提前量）。本轮不猜欧氏距离、不拿未提前的live点冒充原版采样。
目标观察要求稳定只读；refresh/search回调共享同一State以保留状态写入。所有坐标为已解析uint16，
索引/count为native int32；该类不管理坐标解析、目标身份有效期、碰撞事务或路点列表安装。
missile mask0x40000显式拒绝，避免把非导弹策略错误用于导弹路径。

### 第十二批验证与交付

新增`D2PathContinuationTest`29项，首轮全部通过。覆盖：circle耗尽优先及非耗尽范围检查、
无单位final/索引边界、单位距离停止优先/lazy采样/uint8门槛、移动单位两轴±5/±6、
采样与previous/target角色隔离、uint16非环形位移、静态目标仍采样但只在耗尽时续算、
到达final与位移重算优先级、null path/monster零预算/flag0x10/player零预算、
refresh→DOUPDATE→扣预算→搜索顺序、nDistance扣索引而非总点数、扣到0仍搜索、
type2/13/15与其他类型分派、失败保留状态/双失败无第三次调用、非零返回不改成boolean、
组合决策仍需外层路点门槛、各距离字段隔离、低字节先截/77上限、非法字节输入及int32回绕。
一项遍历256预算×79路点索引，检查预算消费与nDist/nDistMax独立；不是20224项JUnit或DLL动态oracle。

定向29项通过；XML `core/build/test-results/upstreamRound4-round12-state`，
日志`core/build/round12-state.log`。合并248项/29类通过（此前219+新增29），失败/错误/跳过0，
BUILD SUCCESSFUL；XML `core/build/test-results/upstreamRound4-round12-full`，
日志`core/build/round12-full.log`。本机隔离任务`:core:upstreamRound4Test -PupstreamRun=round12-full`，
filters沿用第十一批，含原生内核/碰撞、普通Pathfinder/恢复/房间、地面/动态碰撞及DiabRun测试。
没有新增MPQ/TCP/GL、全图/多seed难度、性能或原版DLL动态差分验收。

另一电脑最小复验（不依赖本机临时init，标准core:test本轮未另跑）：

```powershell
.\gradlew.bat :core:test --tests '*D2PathContinuationTest' --tests '*D2PathTargetTest' --tests '*D2TowardTest' --tests '*D2WallFollowTest' --console=plain
```

代码/测试`b09d4957179e7dc51779cfad800d3ea114f3d6f8`已推送origin/master且远端SHA一致。
只提交两个本轮新文件；另一聊天Inferno Sentry/AnimStepper/Assassin与AI报告保持独立，
未暂存或纳入本轮提交，不以本轮合并寻路回归代替其专项验收。四文档独立提交推送，最终SHA见git log。

### 剩余门槛

**寻路仍未完成**，普通生产仍Map→AStarPathFinder。此次仅有独立续算状态层；refresh与10142
仍是适配回调，不能声称已完成native动态路径包装器或生产消费。
仍需单位身份/动态静态坐标解析、10399距离、FOG LUT提前量/单位种类容差；nDist在步进尝试
阶段的消费、10142结果规范化/flags生命周期、路径安装/速度/碰撞事务及实际pathType受控接入。
下一轮优先补目标采样/距离与包装器剩余门槛，再验收真实地形/动态碰撞下的消费、失败、恢复与续算。
保留近期碰撞/房间/步进优化，不自动普通exact→partial或加入1.13+目标房间gate。
火焰之河AI fallback未修/未重跑，其余真实覆盖及benchmark/原版动态差分继续单列。

## 第十三批：单位目标坐标、FOG提前量与10399尺寸距离（2026-10-16）

### 范围与原版证据

继续未完成的寻路，新增`D2MOO_JAVA/.../path/D2PathUnits.java`及`D2PathUnitsTest`。
本轮实现已解析单位的独立稳定快照辅助层，不负责实体ID查找/有效期、各单位表格sizeX解析，
不等于完整10142包装器或生产backend。`observation`把真实算术距离/采样组合给上一批续算层；
`prepare`只做PATH_UpdateTargetUnit相应阶段，返回search前单位目标与path-info field_14容差，
不覆盖final/previous，也不把该容差当作持久nStepNum停止距离。

固定D2MOO`0c0bbd1e6bef9181a3da1c046523af622a925416`，本轮未fetch。
读取该ref的PathUtil.cpp、Path.cpp目标更新/10142、Units.cpp的10399/GetUnitSizeX及Fog声明；
本机objdump核验原版1.10f D2Common.dll与Fog.dll，而非凭经验换成通用几何距离或Math.sin近似。
原版SHA256：

- D2Common：`B9D1A27C6A35992932C7C47438D02E5269F878E899F6E614D507902CECD1BEAB`（与此前一致）。
- Fog：`C4F377CC056E0A5E465D4118D18F3D98E9E650496CF0B5AFEA559057793B0DBA`。

静态证据与实现边界：

- `0x6FDA9CB0/9CF0`读取dynamicPath精确坐标高16位且零扩展，不是有符号右移或四舍五入。
  `0x6FDAB98E..B9E8`对object/item/tile读staticPath+0x0C/+0x10的int32坐标，其余读动态路径，
  dynamicPath null时两轴0；sample写入uint16。`coordinates`保留静态原始int32以便距离使用。
- `0x6FDA9BF0..9C00`的GetDirection为path+0x64字节，null path返回0。
  `0x6FDAB8E5..B92C`及`0x6FDAB9EC..BA45`的owner speed读取+0x68字节，非移动velocity，
  角度为(8*direction-256*speed)&511。方向byte高两位在角度mask中消去，非先假设0..63。
  FOG结果乘speed，经`0x6FDC4FD0..4FF6`的__ftol向零截断后加到uint16坐标，未检查目标是否移动。
- 本机Fog #10083在`0x6FF51DF0`、#10084在`0x6FF51E10`，分别取sin表的(index+128)&511
  与index&511项；表位于VA `0x6FF742A8`，PE文件offset `0x242A8`。
  angle总是8的倍数，本轮只保存实际使用的64个stride8 float位模式，不复制完整二进制或游戏资源。
  原表有非理想对称项和约-8.74e-8的sin(pi)；不按镜像/理想三角函数重建位模式。
  float24×uint8的精确积可由double53完全表示，用double相乘再转int匹配x87不经float存储的乘积。
  本机补充扫描在这个byte输入域未发现理想sin或先存float使最终整数偏移不同，不把位差夸大成已证明的移动bug。
- `0x6FDA87AF..885C`初次准备目标：复制/缩窄目标位置；两轴均0则容差0并先返回，
  player/monster仅speed非零时加lead并容差1；object非门和item容差2且不加lead；门沿既有
  orientation/source侧±2并容差2；missile/tile等默认容差1且不加lead。
  `0x6FDAB9EC..BA4C`续算采样入口不同：只要speed非零，对任何目标类型都加lead，无两轴0早退。
  静态目标会把同一path指针按dynamicPath读取+0x64字节，本轮要求调用方显式提供原始别名字节；
  未提供时拒绝此lead，不伪造0或把原版行为静默改成“静态目标永远不提前”。
- `0x6FDC2CB0..2D8C`的10399读取两个单位的原始坐标和UNITS_GetUnitSizeX结果。
  任一轴差>=8或任一sizeX>=4时：各轴先减(size1>>>1)+(size2>>>1)，夹到0，再算min+2*max。
  否则按`0x6FDD3200..32FF`的8x8表查值；负项立即返回0，size任一为3仅减一次并夹0，
  size任一<=1仅加一次。不是无尺寸的PathMisc近似距离，也不是欧氏/平方距离。
  已核对该表与现有PathMisc短距离表数据一致，复用其raw+1/负项0投影还原raw，未修改原helper。
- `0x6FDC2D91..2E32`的尺寸分支/结果int32运算保留，最终距离不改long饱和；公开输入要求
  nonnegative resolved sizeX及可表示int32绝对值的坐标差，非法abs/越界表索引显式拒绝。
  不试图安全模拟损坏的原生内存或全uint32异常尺寸。

不在本层添加10142更早的raw坐标任一轴0 gate或更后的coincident/room/town/search gate，
因此prepare.tolerance=1不代表整个请求可搜索。后续完整包装器仍须保留这些不同阶段的顺序。
所有Unit/DynamicPath均为稳定只读快照；真实适配器需每次观察更新快照，不可永久缓存来冒充live单位。
本轮未改生产Map/Pathfinder、碰撞/房间/平滑/步进，也不引入RNG或1.13+房间门槛。

### 第十三批验证与交付

新增27项定向首轮通过，合并275项/30类通过（此前248+新增27），失败/错误/跳过0。
普通夹具覆盖unsigned fp16/静态int32缩窄/动态null、raw方向byte和speed奇偶角度/255边界、
向零取整/uint16回绕、FOG原位模式、无目标/种类容差/门方向、prepare与sample不同的类型/零值门槛、
静态lead未提供原字节时拒绝、10399负表项/size3及small修正/8与4边界/原始静态坐标/int32结果回绕、
距离符号/轴/尺寸顺序对称、续算范围优先/lazy采样与led位移、非法布局/输入，以及准备结果组合
调用阻挡终点准备与Toward内核（不安装生产Pathfind）。

两项额外证据包含在上述27项JUnit内，实际读取本机原版DLL，不提交DLL：

- 从PE32 section header解析VA到文件偏移，先匹配上述原版SHA256；FOG测试比较64个stride8
  float原始位模式，再按DLL静态表独立算65536组direction byte×speed byte及边界坐标lead结果。
- 直接读取D2Common的8x8表，对dx/dy=0..10、size1/size2=0..7的7744组距离分支独立核对。

这些是原版静态数据+已核实算术分支oracle，**不是加载DLL执行原函数的动态差分或65536项JUnit**。
若没有D2_HOME/原文件或哈希不匹配，两项用JUnit assumption明确跳过；本机两项均执行，跳过0。
定向XML `core/build/test-results/upstreamRound4-round13-units`，日志`core/build/round13-units.log`；
合并XML `core/build/test-results/upstreamRound4-round13-full`，日志`core/build/round13-full.log`，
BUILD SUCCESSFUL。隔离任务`:core:upstreamRound4Test -PupstreamRun=round13-full`，filters沿用第十二批。
没有新增MPQ/TCP/GL、全图/多seed难度、benchmark或原版DLL动态差分验收。

另一电脑最小复验（路径改为其本地原版1.10f目录，不依赖本机临时init）：

```powershell
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
$env:D2_TEST = $env:D2_HOME
.\gradlew.bat :core:test --tests '*D2PathUnitsTest' --tests '*D2PathContinuationTest' --tests '*D2PathTargetTest' --tests '*D2TowardTest' --tests '*D2WallFollowTest' --console=plain
```

标准core:test本轮未另跑。代码/测试`629176f444f1b3bf83d5c4b88ad6cc4302abad2a`已推送origin/master，
远端SHA一致。只提交两个本轮新文件；共享工作区NPC/Inferno独立提交不算本轮移植，
Megademon代码/测试/报告等新文件保留，未修改或纳入本轮提交。四文档独立提交推送，最终SHA见git log。

### 剩余门槛

**寻路仍未完成**，普通生产仍Map→AStarPathFinder。本轮补齐独立快照上的坐标/lead/10399算术与
单位目标种类容差，但实体身份/有效期、class/table sizeX等生产解析仍待；不据此宣称完整单位目标包装器完成。
下一步补10142其他gate/结果规范化/flags生命周期、nDist步进尝试消费，再把状态、实际pathType及
路径安装/速度/碰撞事务受控接入真实移动，并验收实际地形与动态碰撞的消费、失败、恢复和续算。
不自动普通exact→partial，不撤回近期碰撞/房间/步进优化，不加1.13+ gate。
火焰之河AI fallback未修/未重跑；其他真实覆盖/性能/原版动态差分继续单列。

## 待下一批处理

- 第一/二批以外的调用者与上述坐标/RNG/身份投影限制，仍需后续专项对照。
- 已核对1.10f部分路径消费、重算与生产房间后置gate，修复恢复目标身份及实体目标的耗尽续算；下一步按实际
  pathType设计受控接入。已补独立nDistance/final/索引续算投影与Unit快照/FOG采样/10399算术，
  仍需生产身份/元数据解析、nDist步进消费、10142其他gate/规范化及flags生命周期，并将状态与真实移动接通，
  不把partial当普通exact成功，越界样本仍需补证。
  攻击速度、目标链等其余修正继续专项对照；火焰之河AI fallback单列待办。
- 不移植1.11+充能技能查找门槛、1.13+目标房间门槛或Ubers行为。
