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

## 第十四批：搜索结果、初始向量与房间标记（2026-10-16）

### 范围与原版证据

新增`D2MOO_JAVA/.../path/D2PathResult.java`及`D2PathResultTest`，复用Units测试的原版PE/哈希
读取辅助方法（仅放宽为包级可见）。本轮是非missile 10142结果阶段及初始向量状态投影，组合
`D2PathContinuation.State`，不实现请求gate、footprint移除/恢复、dispatcher或生产路径安装。
State构造默认值不是native allocation；适配器须保持currentPoint与precision高16位同步。

固定D2MOO`0c0bbd1e6bef9181a3da1c046523af622a925416`，本轮未fetch；读取Path.cpp的10142、
sub_6FDA8220/sub_6FDA9720及PathMisc.cpp向量归一化，结合本机1.10f D2Common静态反汇编。
DLL SHA256复核仍为`B9D1A27C6A35992932C7C47438D02E5269F878E899F6E614D507902CECD1BEAB`。

- `0x6FDA8CB6..8E09`：准备后先清index/count、写final，即使阻挡终点准备不允许搜索也执行。
  调用方须先恢复target footprint，再恢复owner footprint，随后才能调用finishSearch。
  count==0只清ACTIVE 0x20，不清index/velocity/vectors/previous/unk0x38；非零count先归一化，
  若全部耗尽则进入common reject，清index/count/ACTIVE，向量/velocity已由归一化清零。
  成功才更新room flag、previous=原target、unk0x38=0，无flag0x10且无targetUnit时target=末点，
  然后置ACTIVE，返回总count而非剩余点数。非零返回也不保证velocity非零或实际移动。
  `pendingUpdate`仅是unk0x38投影名，不声称已知原生字段语义。固定78槽短结果/失败保留stale tail。
- `0x6FDA86F1..8758`：更早的目标raw任一坐标轴0会直接返回，保留旧路径；不能把本层reject
  当所有失败的统一清理。该gate本轮未实现，也不在beginSearch中提前执行。
- `0x6FDAC790..C8ED`：只跳过精确fp16中心与当前precision完全相同的前缀，不按同一整数格去重。
  全耗尽清方向/速度向量及velocity并令index=count；其余使用既有D2PathStep方向表投影，
  vector*velocity保持int32回绕后算术右移8。flag0x200只反向facing，不反向向量；强制更新或
  预测下一整数格等于路点格且normalizeIfSamePoint时才更新facing。
- `0x6FDAC5E0..C6F5`：native lookup混合unsigned比较与signed乘除，本层限定unsigned精确轴差
  <=INT_MAX且127*minorDelta<=INT_MAX。域外可能非法索引，显式拒绝，不用long增强冒充原版。
- `0x6FDA9720..9762`：object/item直接改当前朝向；missile受flag0x40门槛；默认player/monster/
  tile/no-owner只在newDirection变化时改newDirection与diffDirection。64方向差表的低字节
  依次分段为0、1、4、8、-8、-4、-1，不立即推进player/monster当前朝向。
- `0x6FDA8220..8290`：先清bit1，再从第0点扫描至count，包含已消费前缀但不读stale tail；
  bounds为half-open，x+width/y+height保留int32回绕。**DLL与上游差异**：DLL在有点且room null
  时从8240跳8282置bit1；D2MOO外层if(room)只清bit1。本轮按DLL；该flag不拒绝路径，非1.13+ room gate。
- 原版方向表VA `0x6FDD2598`（文件offset `0x92598`），128项(x,y,angle)；turn表VA
  `0x6FDD1F88`，64项int32。新静态数据测试逐项核对既有directionVector及新facing输出。

### 第十四批验证与交付

新增25项测试通过；与Units一起定向52项/2类通过，合并**300项/31类通过，失败/错误/跳过0**。
覆盖零结果/全重复耗尽/精确坐标差、总count/零速度、stale tail、int32速度回绕、反向facing、
预测格门槛、previous/target/单位身份/flag0x10、room half-open/null/前缀/溢出边界、common reject
与朝向分段；另有实际Toward输出、首次失败状态保留后真实WallFollow输出与续算层组合。
这些组合仍是库状态测试，不是生产路径消费或动态碰撞验收。

新原版静态表oracle包含在25项内：先匹配SHA256、解析PE32 section，再核对128项方向表与64项
turn表；不是加载DLL执行函数。无D2_HOME/匹配文件时assumption显式跳过，本机执行、跳过0。
连同上一批两项原DLL测试，合并回归中三项静态oracle均执行；不是数百项原版动态差分。

- 定向XML：`core/build/test-results/upstreamRound4-round14-result`，日志`core/build/round14-result.log`。
- 合并XML：`core/build/test-results/upstreamRound4-round14-full`，日志`core/build/round14-full.log`。
- 隔离任务`:core:upstreamRound4Test -PupstreamRun=round14-full`，filters沿用第十三批，BUILD SUCCESSFUL。
- 未新增MPQ/TCP/GL、全图/多seed难度、benchmark或原版DLL动态差分；标准core:test未另跑。

另一电脑最小复验（原版路径改为当地目录，不依赖本机临时init）：

```powershell
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
$env:D2_TEST = $env:D2_HOME
.\gradlew.bat :core:test --tests '*D2PathResultTest' --tests '*D2PathUnitsTest' --tests '*D2PathContinuationTest' --tests '*D2TowardTest' --tests '*D2WallFollowTest' --console=plain
```

代码/测试`405e77494123dbbf91267c8a84280f5380dd31f2`已推送origin/master并核验远端一致。
仅提交上述三个代码/测试文件；共享工作区COF/对象交互/服务端等其他聊天修改保留、未纳入本轮提交。
四文档独立提交推送，最终SHA见git log。

**寻路仍未完成**，普通生产仍Map→AStarPathFinder。下一轮优先10142早期gate/城镇规则与
footprint事务/dispatcher；生产单位身份/元数据适配、nDist步进消费、实际pathType路径安装、
速度与动态碰撞消费仍需受控接入和实际地形验收。近期碰撞/房间/步进优化保持，不加1.13+ gate，
不自动普通exact→partial。火焰之河AI fallback未修/未重跑，其余真实覆盖继续单列。

## 第十五批：10142前置检查、城镇规则与占位编排（2026-10-16）

### 范围与静态证据

新增`D2PathRequest`与`D2PathRequestTest`，组合第十一至十四批独立层，补非missile 10142请求
编排。State接收稳定目标Unit、与Result共享bounds的起始房间图、已解析size/pattern/raw uint32
moveMask、unsigned byte speed/IDA初始F值，以及CanBeInTown结果。它不负责实体ID/有效期、
MonStats加载、真实active-room图转换或footprint写入；搜索仍是显式Effects.search接口，
不以owner类型猜pathType，不用coordinate A*替代所有原生类型。调用方必须保持身份/房间/元数据
在请求内稳定，并遵守前批currentPoint与precision高字同步约束；构造默认不是native allocation。

固定D2MOO`0c0bbd1e6bef9181a3da1c046523af622a925416`，本轮未fetch。读取Path.cpp完整10142、
town predicate、10222/10223及Monsters.cpp CanBeInTown；结合本机1.10f D2Common反汇编。
SHA256仍为`B9D1A27C6A35992932C7C47438D02E5269F878E899F6E614D507902CECD1BEAB`。

- `0x6FDA86F1..8758`先清16位collided mask，再读目标raw坐标；任一轴0/动态null path直接
  返回0，保留index/count/ACTIVE/target/final/previous。坐标目标的单轴0不受此早退限制。
- `0x6FDA8758..88E7`：源点仅两轴均0才common reject；否则快照nDistMax，做Units.prepare并
  写target。之后目标两轴0、源目标重合、任一轴差超±100才common reject。原static int32轴
  非零并不保证缩窄uint16后非零，初始raw gate和prepare后的零值gate不能合并。
  后续拒绝不回滚已写target，不更新final/previous；±100是逐轴包含100，不是欧氏半径。
- `0x6FDA88E7..89A0`与`0x6FD46620..466B7`：先在startRoom自身half-open bounds找目标，
  否则按DUNGEON_GetAdjacentRoomsList顺序找第一个包含点的非null room；上界加法保留int32。
  **DLL与上游差异**：缺startRoom在`0x6FDA8979..8992`触发assert/终止，D2MOO外层if只做
  普通失败。本层以IllegalStateException显式表达该不可满足前提，不伪造common reject清理。
- `0x6FDA89A0..8A3F`：只限制普通monster；CanBeInTown则跳过，其他owner类型不限制。
  初始targetRoom null时，先从startRoom找start+(target-start)/2的halfway，再从halfway找target；
  除法向零截断，不用floor或两坐标求和取半。fallback的room仅局部用于town判断，不写回
  PathInfo初始targetRoom。最终找不到仍允许，不引入1.13+非空targetRoom gate；allowInTown
  可覆盖最终town拒绝，但native判断先执行，不是跳过整个前置链。
- `0x6FDA56C0..5740`：CanBeInTown需有效MonStats记录，然后NPC/INTOWN或owner ISREVIVE
  任一成立。即使revived，缺记录也false；helper用已解析booleans，不凭NPC名称/经验判断。
- `0x6FDA8A3F..8A75`：快照搜索type，调用10223(owner,FALSE)且忽略返回值；目标有身份且
  sizeX非零、flag0x800时才调用10223(target,FALSE)，仅其true结果触发后续target恢复。
  footprint具体player/monster死亡模式、object碰撞资格、shape/mask/room写入仍由适配器实现，
  不能拿回调测试冒充10223所有分支已移植。
- `0x6FDA8A75..8D52`：移除后才查阻挡终点/调整；写final、清index/count；允许搜索时按快照
  type分派，返回int覆盖live count。调整失败也执行beginSearch，不分派。先10222(target)
  （仅移除返回true），再无条件10222(owner)，最后进入上一批finishSearch/向量计算。
  原targetRoom仍对应调整前目标，不因终点修正重新查房间；搜索失败本层不自动type15重试，
  重试属于10236续算层。native start未改断言由不可变Search.start及回调契约表达。

Java callback抛异常时finally仍尝试恢复已完成的target移除与owner；target add抛异常也尝试owner。
这是宿主安全增强，**不是原版异常/断言unwind或状态回滚**，异常不执行finishSearch/普通失败清理。
若remove回调部分写入后抛异常，内部恢复责任仍在适配器；不声称自动知道部分占位写入是否完成。
null请求返回0；missile与无有效owner投影显式不支持，不借此实现missile dispatcher。

### 第十五批验证与交付

首轮26项Request +25项Result定向通过。补充网格/恢复/续算夹具后，共享core/build的复跑在
compileTestJava阶段因其他任务同时写入、无法删除输出目录而失败，未执行JUnit，不算断言失败。
新增仓库脚本`docs/path-port-regression.init.gradle`，在各project求值前隔离buildDir到
`.gradle/path-port/<pathRun>/<module>`，编译、资源和报告均独立；不清理其他聊天输出。

独立构建首次扩大回归331项中1项新夹具失败：续算测试沿用final=(9,9)，距(10,10)仅1格，
WallFollow按既有原版<=2步早退返回空，与夹具预期有路径冲突。已读WallFollow入口及既有短线
测试确认；改用目标(20,10)的实际单位目标13→15重试场景，未修改请求实现或WallFollow内核。
首次失败日志`core/build/round15-isolated-full.log`；失败后重新执行同范围合并回归，
最终**331项/32类通过，失败/错误/跳过0**，含本轮新增31项。

覆盖raw零轴保留/晚期清理、static坐标缩窄、±100逐轴包含边界、准备目标写入不回滚、缺startRoom
断言、缺targetRoom/两跳town fallback/负向奇数中点截零、顺序邻接与half-open/int32边界、
MonStats/town资格组合、target占位三条件/返回值、owner恢复无条件、搜索前快照、调整失败仍写final、
原始room不随终点修正重查、count返回覆盖、Java异常恢复与不执行finishSearch边界、归一化耗尽，
以及真实Toward输出/续算转WallFollow库组合。内存D2CommonCollision pattern网格夹具实际移除
owner/target标记，在碰撞查询与Toward搜索时断言均不存在，结束后恢复两者且静态墙标记不变；
它使用人为配置footprint适配回调，不是10223全部单位形状/死亡/object资格分支或实际地图验收。

最终日志`core/build/round15-isolated-full-final.log`，XML
`.gradle/path-port/round15-full/core/test-results/pathPortTest`，BUILD SUCCESSFUL。
任务`:core:pathPortTest -PpathRun=round15-full`，filters沿用第十四批（path包自动包括本轮新类）。
三项已有原版静态表oracle也执行、无跳过；本轮未另跑标准core:test，没有新MPQ/TCP/GL、全图/
多seed难度、benchmark或原版DLL动态执行。静态表oracle只是原DLL数据读取，不是动态函数差分。

代码/测试/init `40d444c22683426922e90fd94cc0216a333899d9`已推送origin/master并核验远端SHA一致。
只提交本轮两个Java新文件与独立init脚本；其他聊天AI、对象声音/交互及其报告保留，未纳入本轮提交
或专项验收。共享master上的其他独立提交不回退；四交接文档另行提交推送，最终SHA见git log。

另一电脑最小复验（原版路径改为当地目录；独立构建脚本已纳入仓库）：

```powershell
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
$env:D2_TEST = $env:D2_HOME
.\gradlew.bat -I docs/path-port-regression.init.gradle :core:pathPortTest -PpathRun=round15-check --tests '*D2PathRequestTest' --tests '*D2PathResultTest' --tests '*D2PathUnitsTest' --tests '*D2PathContinuationTest' --tests '*D2TowardTest' --tests '*D2WallFollowTest' --console=plain
```

**寻路仍未完成**：本轮补请求的gate和占位调用编排，但实际10222/10223适配、原生pathType
dispatcher、生产身份/表格/房间图、nDist步进消费、路径安装/速度/动态碰撞消费仍待。
下一轮优先dispatcher与footprint适配，随后受控接通真实移动并验收消费、失败、恢复、续算。
普通生产仍Map→AStarPathFinder；近期碰撞/房间/步进不变，不加1.13+ gate，不自动普通exact→partial。
火焰之河AI fallback本轮未修/未重跑；其他聊天AI提交不能当本轮路径验收，最新状态须另查其报告。

## 第十六批：单位占位写入与请求网格适配（2026-10-16）

### 范围与静态证据

新增`D2MOO_JAVA/.../path/D2PathFootprint.java`、`D2PathFootprintTest`，在`D2PathUnits`
增加包级动态路径分配检查。移植10222/10223上层类型分派与资格、10338 mask和10410资格，
通过`RequestEffects`连接上一批请求编排与既有`D2CommonCollision`真实内存网格；search仍为
abstract，**不是完整原生dispatcher或生产接入**。坐标/sizeX沿用Units的已解析快照，
Objects字段与sizeY需调用方提供，不加载表格、不管理ECS身份/有效期。

固定D2MOO`0c0bbd1e6bef9181a3da1c046523af622a925416`，本轮未fetch，读取Path.cpp、
Units.cpp、ObjectsTbls.h及D2Collision.cpp对应实现。静态核对本机
`G:\BaiduNetdiskDownload\Diablo II 1.10F\D2Common.dll`，SHA256复核仍为
`B9D1A27C6A35992932C7C47438D02E5269F878E899F6E614D507902CECD1BEAB`。

- `0x6FDA8320..8449` Add：player/monster用dynamic pattern；object用sizeX/sizeY矩形；
  missile/item/tile用size，不混入pattern presence。Add不检查死亡mode或object hasCollision。
- `0x6FDA8450..85FB` Remove：player的0/17、monster的0/12且未force时false；object当前
  nHasCollision字节为0且未force时false；其余执行分支返回true。**true只是有资格执行，
  不保证room/grid存在、mask非零、shape受支持或网格实际改变**。同bit重叠仍按位清除，非引用计数。
- `0x6FDBDE10..DEB0` mask：object door优先，blocksVis→0x806，否则blockMissile→0x804，
  都不阻挡→0x400；非door先subClass&4→0x8000，否则blockMissile→0x404，否则0x400。
  不阻视/不阻弹door不是0x800。item→0x200、tile→1，动态单位读footprint字段。
- `0x6FDA9FC0..9FD5` #10177：null dynamic path返回0xFFFF，不是0；非null取+0x4C DWORD。
  保留raw32返回，进入uint16网格ABI前先截低16位，避免只有高16位非零也触发presence标记。
  player/monster的pattern需要已分配path，null显式拒绝；死亡Remove可在读取前返回false。
- `0x6FDBFB40..FB64` #10410：object按当前mode索引+0x120的八字节hasCollision，非object true。
  原始uint8的非零都成立，数组复制；adapter只接受0..7模式，不模拟读越界的相邻结构字段。
  Objects偏移：door +0x13A、blocksVis +0x13B、subClass +0x167、blockMissile +0x1B6。
- SetSizeXY `0x6FD443E0`与ResetSizeXY `0x6FD44140`的零尺寸处理不对称：Add仍构造退化box，
  Remove有正尺寸guard。既有Java helper把零尺寸视为no-op，因此本层零宽/零高object Add
  **显式拒绝**，不能用no-op冒充原版；Remove有资格时仍true且无写入。

本轮只移植**上层单位选择/资格/mask**，复用现有网格写入原语，不修改`D2CommonCollision`。
其矩形逐点路由/long bounds与原版递归box切分、溢出域及病态房间图策略仍需专项核对；
跨房pattern夹具不构成任意矩形房间图原生等价证明，也不保证set/reset与其他重叠单位组成事务回滚。

`RequestEffects`绑定一个稳定request和owner/target快照，校验owner类型/current tile/sizeX、
player/monster pattern与room bounds；target要求与request.target同一坐标快照对象。
占位使用各自UNITS_GetRoom，blocked使用owner room与request pattern及low16 moveMask。
只核对bounds，不证明两套房间图身份/邻接/metadata一致；调用方必须保持映射与快照稳定。
search仍由子类明确提供正确native入口，不能直接把纯Toward/WallFollow内核冒充完整dispatcher。
Java宿主异常恢复沿用上一批finally扩展，不声称原版异常unwind或部分网格写入原子性。

### 第十六批验证与交付

直接执行隔离合并回归，**358项/33类通过，失败/错误/跳过0**，含本轮新增27项。
覆盖pattern与presence、raw32→uint16、两类mode0..20×force、object八模式raw字节与复制、
door/vis/missile/subClass全部组合、偶数矩形bias、item/tile size0..4与missile size、空path、
missing room/grid、static int32与dynamic unsigned高字、跨邻房pattern、同bit按位清除与退化box。
RequestEffects夹具在真实内存网格移除owner/矩形target后运行Toward，验证恢复后向量；
另覆盖dead owner false仍add、target false不凭空恢复、无flag0x800保留阻挡、target独立room、
宿主search异常恢复与绑定快照拒绝。上述均为库夹具，不是生产实体/真实地图验收。

命令采用`docs/path-port-regression.init.gradle`与`:core:pathPortTest -PpathRun=round16-full`，
filters包括`com.d2moo.common.path.*`、`com.d2moo.common.collision.*`及上一批同范围运行时
寻路/碰撞/恢复/房间/玩家地面/对象/旋风/warp/native probe/cache/comparison/shadow/DiabRun回归。
日志`core/build/round16-full.log`，XML `.gradle/path-port/round16-full/core/test-results/pathPortTest`。
三项已有原DLL静态表oracle执行、无跳过；本轮没有新增DLL动态函数oracle，没有新MPQ/TCP/GL、
全图/多seed难度或性能验收，不以静态反汇编/静态表读取推断动态或性能等价。
测试完成后只移除未使用import，按极小修改约定未重复编译。

代码/测试`4d4112ef11e7c98d7aee3fa6438c914532cc56d9`已推送origin/master并核验远端SHA一致。
只提交本轮三个源码/测试文件；其他聊天NPC/商人修理等修改保留、未纳入本轮提交或专项验收。
四交接文档独立提交推送，最终SHA见git log。

另一电脑最小复验（原版路径改为当地目录）：

```powershell
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
$env:D2_TEST = $env:D2_HOME
.\gradlew.bat -I docs/path-port-regression.init.gradle :core:pathPortTest -PpathRun=round16-check --tests '*D2PathFootprintTest' --tests '*D2PathRequestTest' --tests '*D2PathResultTest' --tests '*D2PathUnitsTest' --tests 'com.d2moo.common.collision.*' --console=plain
```

**寻路仍未完成**：下一轮优先原生pathType dispatcher与必要入口状态副作用；生产身份/表格/
房间图映射、nDist步进消费、路径安装/速度/动态碰撞消费、矩形递归网格边界与真实地形验收仍待。
普通生产仍Map→AStarPathFinder；近期碰撞/房间/步进不变，不加1.13+ gate，不自动普通exact→partial。
火焰之河AI fallback本轮未修/未重跑；其他聊天AI更新与实际验收须查看其独立报告。

## 第十七批：类型生命周期与有状态Toward分派（2026-10-16）

### 范围与1.10f证据

新增`D2PathTypes`、`D2PathDispatch`及两个测试类；Continuation补previousPathType，Result补
previousVelocity与包级count0归一化入口，Toward抽出已完成ray后的共享尾部。完整18项类型表
只是元数据，本轮**仅接通2/3/5/6/13/15六个搜索类型**。`D2PathDispatch.Effects`组合既有
Request/Footprint与本轮分派，不再需要为这六类手写search回调，但仍不安装生产路径或消费移动tick。

固定D2MOO`0c0bbd1e6bef9181a3da1c046523af622a925416`未fetch，读取Path.cpp与PathMisc.cpp
相关入口、PathWF入口；结合本机原版D2Common反汇编和静态表。DLL SHA256复核仍为
`B9D1A27C6A35992932C7C47438D02E5269F878E899F6E614D507902CECD1BEAB`。

- `0x6FDD1F40`分派表、`0x6FDD2088`flags表、`0x6FDD20D0`方向偏移表各18项。
  2/5/6/13共用Toward，3经`0x6FDAB0B0..B0BE`清index后跳Toward，15为WallFollow；
  0/16为IDA，1为A*，7为Straight，8/11为两种knockback，9为Leap，12为BackupTurn。
  4/10/14/17空表项不进入普通非missile搜索。本轮不实现missile的独立dispatcher。
- `0x6FDAA0E0..A1DD` #10185：player SetType(2)在写前断言；new flags&0x2000且old flags
  无0x4000才保存旧type；new flags&0x8000且old无0x10000才保存velocity。再写type、
  `newFlags | (oldFlags & 0xFFF800FF)`、表中dirOffset。最后saved type为8/11时断言，
  写入不回滚；type4还断言nDistMax<78（现有byte setter已将可达投影值限制到77）。
  非表域type显式拒绝，不模拟原版越界读。已存在的10236仍直接赋type，不套此函数。
- `0x6FDAA1E0..A23A` #10186：要求owner存在，先按当前0x8000恢复velocity，然后player
  总是SetType(7)，其他owner仅当前0x2000时SetType(previous)。速度恢复与类型恢复gate独立，
  随后SetType断言也不回滚速度；嵌套knockback/Leap保持最初保存值。
- `0x6FDA8CCB..8D0E`按之前快照type间接调用，将返回值覆盖live count；不是按owner或目标
  类型猜分派。新dispatcher沿用该快照，使用live dirOffset，不因type5/6再自动写±2。
- `0x6FDAA9F0..ABE7` Toward入口：先清index/count；live offset非零直接走A720，不做
  初始ray或向量探测。零offset先写target到slot0，再调`0x6FDAABF0..AD01`。
  后者先断言owner player/monster、live type非4、目标两轴非零，再检查velocity；零velocity
  令ray endpoint回start，但后续仍可做Toward局部搜索/容差接受，不能直接返回空路径。
- `0x6FDAC790..C8ED`归一化**会在live count=0时读取slot0**；非重复点仍计算初始向量/facing。
  精确中心重复则清vectors/velocity并令index=count（此处仍0），不得伪造count1再归一化。
  Result新增仅包级的这个probe入口，普通normalizeVectors仍要求有效current waypoint。
  velocity非零且probe后仍非零才做一次ray；复用Toward尾部避免重复查询。失败时临时slot0及
  初始向量也可能保留，不借零count清空整个buffer。整数同格但fp16未居中不算精确重复。
- `0x6FDAC270` WallFollow入口读live nDistMax及目标身份的min40，不用PathInfo距离快照；
  只写返回前缀/返回count，不自行重置index/count或初始向量，由外层10142接回赋count。

本轮支持域要求稳定player/monster、uint16坐标、currentPoint与precision高字同步、距离0..77与
容差uint8，沿用既有向量非溢出域。native本就不安全的域不做猜测。collision回调必须是稳定只读
查询，局部点写入通过已有纯内核的最终前缀提交；不声称Java回调重入时的中间buffer可观察性，
或宿主回调部分执行后异常与native动态函数的写入时序等价。footprint的finally是宿主安全增强。
生产实体/表格/房间图映射与矩形递归网格原语边界沿用第十六批，未在本轮解决。

未接通的非空表项0/1/7/8/9/11/12/16抛显式UnsupportedOperationException；空表项显式断言异常。
它们不是普通无路可走、不是自动type15或coordinate A* fallback；对应纯算法已有不代表完整入口已接通。

### 第十七批验证与交付

最初定向67项/4类通过（Types10、Dispatch16、Result25、Toward16）；首轮合并384项通过。
复核后补live type4射线断言、offset零预算stale slot0、0x200反向facing三项，并重跑同范围。
最终**387项/35类通过，失败/错误/跳过0**，本轮新增29项（Types10、Dispatch19）。

覆盖全部18type×6旧flags保存门槛、嵌套类型/速度、写前/写后断言、两类恢复gate、类型别名与
非法域；stateful Toward初始query前状态、与纯内核逐次query/路点比较、零速度/精确中心/
整数同格未居中、失败stale slot/向量、方向偏移与预算、WallFollow live距离/身份min40和入口写入。
具体Effects夹具在真实内存网格移除/恢复owner并完成request归一化；另验证被包围时10236的
2→15重试均失败且恢复owner，以及unsupported入口异常时finally恢复。未据此声称真实地图验收。

新增一项可选本机DLL静态oracle，通过PE节头定位三张表，共54个DWORD逐项核对；
与此前三项合计四项原DLL静态数据oracle均执行、无跳过。它不是54项JUnit或DLL动态执行。
无匹配D2_HOME/哈希会显式跳过该oracle，不把缺原版文件当作已验证。
日志`core/build/round17-check.log`、首轮`core/build/round17-full.log`、最终
`core/build/round17-full-final.log`；XML `.gradle/path-port/round17-full/core/test-results/pathPortTest`。
隔离init与第十六批同范围filters，`:core:pathPortTest -PpathRun=round17-full`，BUILD SUCCESSFUL。
本轮无新MPQ/TCP/GL、全图/多seed难度、性能或原版DLL动态函数差分。

代码/测试`2cb53ea37d3f2d53b887133f07184572665edfc3`已推送origin/master并核验远端一致。
只提交本轮七个源码/测试文件，其他聊天物品/商人修改与DeathMaul测试不混入、不代为验收；
共享master上的其他提交保持。四交接文档另行提交推送，最终SHA见git log。

另一电脑最小复验（原版路径改为当地目录）：

```powershell
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
$env:D2_TEST = $env:D2_HOME
.\gradlew.bat -I docs/path-port-regression.init.gradle :core:pathPortTest -PpathRun=round17-check --tests '*D2PathTypesTest' --tests '*D2PathDispatchTest' --tests '*D2PathRequestTest' --tests '*D2PathFootprintTest' --tests '*D2PathResultTest' --tests '*D2TowardTest' --tests '*D2WallFollowTest' --tests '*D2PathContinuationTest' --console=plain
```

**寻路仍未完成**：下一轮优先Straight7与A*/IDA必要入口，再继续其他native类型和受控生产接入。
生产身份/元数据/房间图、nDist步进、真实路径安装/速度/动态碰撞消费、矩形递归网格边界与地形验收
仍待。普通生产仍Map→AStarPathFinder，近期碰撞/房间/步进保持，不加1.13+ gate或普通自动partial。
火焰之河AI fallback本轮未修/未重跑，其他聊天进度与验收单列。

## 第十八批：原生A*与Straight入口（2026-10-16）

本轮不切换生产。`D2PathDispatch`从2/3/5/6/13/15扩为**1/2/3/5/6/7/13/15**，
具体Effects可组合既有Request与真实内存footprint；不是生产ECS适配或移动消费安装。

### 第十八批证据与入口语义

D2MOO固定`origin/master=0c0bbd1e6bef9181a3da1c046523af622a925416`，本轮未fetch，读取
`Path/PathMisc.cpp`、`Path/AStar.cpp`及`Path/IDAStar.cpp`。重新计算本机
`G:\BaiduNetdiskDownload\Diablo II 1.10F\D2Common.dll` SHA256仍为
`B9D1A27C6A35992932C7C47438D02E5269F878E899F6E614D507902CECD1BEAB`。
以下为objdump静态反汇编，不是原版DLL动态函数差分。

- Straight7 `0x6FDAB130..B1D9`先清index/count后调用Toward。返回非零且最后点的近似距离
  <=uint8容差、最后点!=原始start，才直接返回。否则用原始start/target计算
  `dx*dx+dy*dy<=18*18`；边界324包含，`imul/add/jg`是signed int32运算，保留溢出回绕，
  不是long距离，也不是按Toward末点测半径。正常Request的±100窗口避免此病态大差值域。
- 在半径内调用A*，非零直接返回；A*失败且第一次Toward非零才**再次执行Toward**，保留第二次
  查询/状态副作用，不缓存恢复旧点。首次Toward零、半径外均不重算。A*可能返回best partial，
  Straight不另要求精确到达；这不等于授权普通生产exact→partial兜底。
- A* `0x6FDA69E0..6D01`的targetUnit gate按顺序检查目标周围
  `(-2,-2),(-2,+2),(+2,-2),(+2,+2),(-2,0),(0,-2),(+2,0),(0,+2)`，首个空点即停。
  偏移查询是int32，坐标临近0/65535时也不截回uint16；没有live targetUnit则不查这八点。
- 邻居构造`0x6FDA6D50..720F`写uint16，节点h/g/f字段为signed int16赋值和比较。
  `0x6FDA7320..738B`先符号扩展f再排序，同分新点前插；pending降分不重排、visited传播
  strict lower、200节点和best partial选择沿用原逻辑，不趁本轮换成更常规但不同的A*。
  新增`D2AStar.findNativePath`专供原生入口；旧`findPath`仍保留通用int坐标语义和既有shadow
  比较。native模式超出200项传播栈域显式异常，不模拟DLL内存破坏。
- flush实际为`0x6FDA78A0..7965`（由入口调用确认），`0x6FDA72D0`是visited lookup。
  现有`compressAStarPath`已按方向压缩、排除start，count<1或>=78不写，一点路径允许。
  A*入口只写成功prefix、返回count，不改live index/count/vectors，失败不清旧槽/尾部。
  Straight成功A*的返回count可能不同于仍保留的Toward live count，外层Request才接回赋值，
  恢复target/owner占位后再按实际首点归一化；不能在A*内部提早改这段生命周期。

adapter沿用第十七批稳定player/monster、uint16/precision同步、distance0..77/toleranceuint8域。
这不代表原版A*在所有其他owner域都会断言，只是本adapter当前支持域更窄。碰撞回调要求稳定
只读，不把测试中用于观察第二次调用的状态夹具当作动态碰撞生产支持；宿主异常/重入中间状态
与原版内存副作用等价性不在本轮范围。

### IDA留待下一轮：已纠正分支初读，未修改代码

不能直接把现有纯`D2IDAStar`接到type0/16：原生wrapper还涉及容差uint8倍增、房间边界构造、
类型相关cutoff与type16单位RNG，现有通用API不覆盖完整入口。room Y/height赋值顺序也需
对照DLL，不能照搬可疑反编译顺序或凭经验修正。

额外复核`0x6FDA7F1C..7FD6`发现三者差异：

- DLL将short新g符号扩展后，`cmp cached,new; jae 0x6FDA7FCF`；**7FCF恢复寄存器后落到
  7FD3写cache/继续评估**，不是跳过。非零cache仅在unsigned cached>=new时接受，包括相等。
- 固定上游源码写成`new>=cached`，方向相反；不能仅因来自D2MOO就直接移植这个条件。
- 现Java为unsigned `new<cached`，短路方向与DLL一致，但不接受相等，还存在通用int/饱和加法
  与原生word算术等域差异。当前不改纯库/不接dispatcher，不声称本轮修复IDA。

本轮早期曾把`jae`误当成跳过；沿两条跳转追到7FD3后已纠正，后续必须以上述完整控制流为准。

### 第十八批验证与交付

新增`D2PathStraightTest` **23项**。定向Straight/Dispatch/AStar/Toward共**62项/4类通过**；
最终与第十七批同范围合并**410项/36类通过，失败/错误/跳过均0**，BUILD SUCCESSFUL。
四项本机原DLL静态数据oracle执行；未增加/执行原版动态函数oracle。

专项覆盖A*前缀/stale buffer/live状态、targetUnit approach顺序与short circuit/int32边界、
uint16邻居回绕、signed16 h/f导致不同分支、1,000组普通输入逐query/路点/节点数/best point
比较（在一项JUnit内）；Straight容差非精确点/source排除、18/19与对角半径、int32溢出、
失败重算/不重算、live offset、返回count与live count区别、partial与非自动type15续算。
真实内存网格组合type1/7移除/恢复owner和target，完成绕墙路径与外层归一化，不是实机地形验收。

首次定向6项夹具预期错误：Toward一步预算尾点省略、local过程不逐步重测容差，以及A*首压缩
点不保证立即转弯。调整预算/障碍位置与断言后通过，未为迁就测试改动Toward或A*内核。
早期编译也遇到其他聊天物品接口/测试正在修改的错误；没有改动那部分文件。init新增可选
`-PpathTestScope=path`，筛选本轮36类及RiiabloTest的测试源码，默认编译不变；主源码仍完整
重新编译。临时跳过core编译的诊断尝试没有形成验收，最终通过记录未跳过主源码编译。

日志：`core/build/round18-check-selected.log`（首次6项夹具失败）、
`core/build/round18-check-selected-final.log`（62项通过）、`core/build/round18-full.log`。
XML：`.gradle/path-port/round18-full/core/test-results/pathPortTest`。
代码/测试/init **`26952a9d94f449d9241e1d47870c20edd40cf592`**已推送origin/master并核验远端一致。
只提交本轮五个路径；其他聊天物品/门/DeathMaul修改及其提交保持，不代为验收。
四交接文档另行提交推送，最终SHA见git log。

另一电脑最小复验（安装目录按当地调整；无需上述临时init）：

```powershell
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
$env:D2_TEST = $env:D2_HOME
.\gradlew.bat -I docs/path-port-regression.init.gradle :core:pathPortTest -PpathRun=round18-check -PpathTestScope=path --tests '*D2PathStraightTest' --tests '*D2PathDispatchTest' --tests '*D2AStarTest' --tests '*D2TowardTest' --console=plain
```

全范围可用同一命令改`-PpathRun=round18-full`，删除四个`--tests`筛选；`pathTestScope=path`
只编译上述寻路回归集合。无匹配原DLL时静态oracle显式跳过，不得据此声称原版验证通过。
本轮无新MPQ/TCP/GL、全图多seed难度、性能或原DLL动态函数差分。

**寻路仍未完成**：下一轮优先IDA入口/相等分支，再补8/9/11/12。生产身份/元数据/房间图、
nDist与移动消费安装、矩形递归网格及地形验收仍待；普通生产仍Map→AStarPathFinder。
保持近期碰撞/房间/步进，不加1.13+ gate或普通自动partial。其他AI与火焰之河fallback进度单列。

## 第十九批：确定性IDA type0入口与跨轮缓存（2026-10-16）

新增`D2IDAStar.findNativePath`原生模式，**不改变旧findPath通用API行为**。room-aware
`D2PathDispatch.compute(Search,...)`现在支持0/1/2/3/5/6/7/13/15；不带room的直接重载仍
显式拒绝0，不能凭坐标猜target room。type16仍unsupported，不以确定性搜索替代单位RNG。
普通生产保持Map→AStarPathFinder，尚未安装ECS原生搜索/移动后端。

### 第十九批证据与行为

固定D2MOO`origin/master=0c0bbd1e6bef9181a3da1c046523af622a925416`未fetch，读取
`Path/IDAStar.cpp`。重新计算本机1.10f D2Common.dll SHA256仍为
`B9D1A27C6A35992932C7C47438D02E5269F878E899F6E614D507902CECD1BEAB`；使用objdump
核对`0x6FDA7970..821B`，为静态控制流/表数据证据，不是DLL动态函数差分。

- `797F..7992`将PathInfo field14按uint8倍增。Search保留raw不可变快照，调用内核前用
  `(tolerance*2)&255`投影；不将这个临时字段当作持久nStepNum/停止距离改写。
- `79AC..7A4A`按start/target room身份选择分支：target null或同room用start尺寸；其他房间
  分别按坐标先后选择方向端点差。它**不是一般的max-right/max-bottom union**，小房间包含在
  start内时也可能缩小范围。某轴算出尺寸等于start原尺寸才该轴origin-10、size+20。
- `7A9F..7B0F`构造uint16 min/max/size和stride=width+6、offset=3-origin。
  Y最大值实际为Y+height，上游读未赋值nCoord[2].Y的顺序不照搬。支持域仅有序非回绕room，
  用long验证后投影，**不是替DLL定义整数溢出行为**。
- `7A78..7A9D`只在入口清一次`(width+6)*(height+6)`个cache DWORD；`7C31..7C79`轮间只
  复制起始节点、重置节点分配计数，不清坐标cache。native模式使用实际stride/offset数组，
  blocked=1也跨轮保留。旧纯库的每轮clear仍只属于旧findPath通用模式。
- `7F23..7FD6`非零cache按unsigned cached>=sign-extended short new g接受，**相等也继续**；
  原版jae落到7FCF后进入7FD3更新，不是跳过。上游源码反向条件不采用。
  在900节点安全域内g不会到short溢出，blocked=1始终小于合法新g，不重复查询碰撞。
- `7DE4..7DE8`邻居uint16；root/child h/g/f按signed16赋值和比较。`7C1F..7C2D`type0
  ceiling是max(signed root h, unsigned initialFScore byte)，不是上游源码的恒假自比较。
  初始cutoff必试一次；失败后EBX按**int32**加5，下一轮仅cutoff<ceiling才执行。
- `817E..8188`新建和复用的child都从**父节点当前方向+当前sequence首值**起算，不是累加
  child上次方向或新节点的零值；本轮仅native模式修正。`7ED6..7EEE`等父回退路径会先前移
  sequence再决定根耗尽，row0 index8会读取连续表中的row1[0]，仍在核对过的8×8表内。
  native模式按连续表寻址，不能在这次无后续搜索的读取上制造Java数组越界。
- 保留10000次visit/轮、900节点、起始root评估计数-3、子节点五候选、inclusive bounds，
  进入越界节点后可作为terminal返回；不是邻居预先拒绝。h<threshold为严格比较，且只在进入
  child后检查，不在root上用容差早退。type0不加A*的八点target-unit approach gate。
- `7C85..7D32`只写有效压缩prefix，排除start，<=1或>=78点拒绝且不清stale slots。
  内核与dispatcher不赋live index/count/vectors；外层Request接返回count，先恢复target/owner
  再最终归一化。type0续算失败不自动改15，不能用A*/Toward兜底其一点flush拒绝。

安全域额外限制：原版只断言未padding面积<=50000，随后清padding面积可能越过固定50000
cache并破坏内存。Java要求padding后也<=50000，越界抛显式异常；word邻居绕回导致cache地址
超出受支持内存时也显式拒绝，不用无界Map掩盖原版越界。Request finally仍恢复已移除占位，
它是宿主安全增强，不是原版断言/内存损坏的动态等价。支持域不是所有native room布局。

Result节点数、iterations/finalCutoff等是宿主诊断，不是DLL context内存快照：成功报告尝试的
cutoff，失败耗尽ceiling时保留循环推进后的值；不把原版成功后节点计数归1当作Java诊断值。
稳定碰撞/元数据及完整room图仍为调用方责任；没有生产身份映射或回调重入写入时序保证。

### 第十九批验证与交付

新增**24项**：`D2NativeIDAStarTest`15项、`D2PathIdaDispatchTest`9项。
定向五类73项通过后，再补一项type0续算不转15；最终同范围合并
**434项/38类通过，失败/错误/跳过均0**，BUILD SUCCESSFUL。

一项独立递归contour oracle对200组固定seed小域比较逐次碰撞query、terminal/best point、
轮数/cutoff、节点分配和压缩前缀。它用递归候选枚举而非生产可变节点/backtrack实现，但复用
已核对方向原语与公共压缩helper；不是200项JUnit，也不是DLL动态oracle。
新增一项读取匹配DLL的8×8 sequence/8×2 offsets并逐项比实际Java私有表；与此前四项共五项
静态数据oracle执行、无跳过。无匹配文件时静态oracle会显式跳过，不能报告已验证原版。

包含equal-cache跨轮重访/碰撞只查一次、child复用、short h提前threshold、inclusive边界、
900节点、signed root score与int32 cutoff推进、room身份/方向端点/±10、padding/word回绕拒绝；
Search快照type与live offset、无room/16显式拒绝、临时容差与持久step分离、prefix/live count
生命周期、续算不fallback，以及真实内存网格owner/target双占位恢复和外层detour向量归一化。
正常请求仍沿用±100/非missile player-monster等既有adapter支持域。

首次定向暴露root回退row0 index8 Java越界，已按原版连续表读取修正；另有节点上限夹具
padding面积过大、失败cutoff诊断与跨房夹具ceiling低于绕行所需的预期问题，调整后通过，
没有放宽内存安全边界或强迫IDA返回路径。日志`core/build/round19-check.log`、
`round19-check-second.log`保留失败证据，`round19-check-final.log`为73项通过；最终
`core/build/round19-full.log`，XML `.gradle/path-port/round19-full/core/test-results/pathPortTest`。

代码/测试**`a5a25972d994e238b4e33817a9e582a8be62f459`**已推送origin/master且远端一致，
只提交本轮四个源码/测试文件。其他聊天DeathMaul/门修改与其提交保持，不混入本轮提交或
专项验收；四交接文档另行提交推送。沿用独立build目录与可选pathTestScope测试源码隔离，
没有跳过主源码编译。另一电脑复验（目录按当地修改）：

```powershell
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
$env:D2_TEST = $env:D2_HOME
.\gradlew.bat -I docs/path-port-regression.init.gradle :core:pathPortTest -PpathRun=round19-check -PpathTestScope=path --tests '*D2NativeIDAStarTest' --tests '*D2PathIdaDispatchTest' --tests '*D2IDAStarTest' --tests '*D2PathDispatchTest' --tests '*D2PathStraightTest' --console=plain
```

该定向命令在最终代码应执行74项；上述73项是补最后一项前的实际记录。
全范围将pathRun改round19-full并删除五个--tests筛选（本轮实际运行命令），应覆盖434项/38类。
无新增地形/MPQ验收、TCP/GL实机、多seed难度全图、性能或原版DLL动态函数差分。

**寻路仍未完成**：下一轮优先type16单位RNG/随机方向消费与专用cutoff，再补8/9/11/12。
生产身份/元数据/room图、nDist与移动消费安装、矩形递归网格及地形验收仍待。
普通生产仍Map→AStarPathFinder，保持近期碰撞/房间/步进，不加1.13+ gate或普通自动partial。

## 第二十批：type16随机IDA与单位RNG（2026-10-16）

`D2IDAStar.findNativeStreamPath`补上type16随机模式；`D2PathRequest.State`新增可选live owner
`D2Seed`引用与构造器重载，保留旧调用。Search现在支持0/1/2/3/5/6/7/13/15/16，16必须有seed，
0/16必须有room-aware Search；裸坐标重载均显式拒绝。调用方必须投影真实owner seed，不能用
临时seed、克隆、请求间重置或目标单位seed代替。普通生产仍Map→AStarPathFinder。

### 第二十批证据与行为

继续固定D2MOO`origin/master=0c0bbd1e6bef9181a3da1c046523af622a925416`，未fetch，复读
`Path/IDAStar.cpp`。本机`G:\BaiduNetdiskDownload\Diablo II 1.10F\D2Common.dll`重新计算SHA256仍为
`B9D1A27C6A35992932C7C47438D02E5269F878E899F6E614D507902CECD1BEAB`。objdump静态核对：

- `6FDA7B02..7B34`仅PathInfo快照type==16开启随机模式，seed指向dynamicPath.ownerUnit+0x20。
  不按live type或owner/target身份猜类型；16的表名MISSILE_STREAM不授权放宽既有missile gate。
- `7BFB..7C19`初始cutoff为`(short)(signedRootH + signedRootH/2)`，除法向零截断；ceiling取它
  与uint8 initialFScore的最大值。root节点h/f仍原始signed16 h，不替换成1.5h。共享search拆开
  root heuristic与初始cutoff；type0不变。后续cutoff仍int32+5，不跟随上游局部short类型回绕。
- `8136..818B`进入child先roll一次，再以parent当前方向+sequence首值+随机偏移起算，之后才查
  threshold/回到exact检查。因此即便立即terminal或一点flush拒绝，也已消费seed。
- `81C0..821B`和reject内联路径每次方向推进都roll一次，偏移取新lowSeed&31索引；复用已核对的
  `Seed.rollRandomNumber`，不更改全局RNG算法。`7FAC..7FC8`等父回退路径先推进再增加评估计数，
  即便根/父就此耗尽、不再查询碰撞，仍消费RNG；不能把这次调用当无用操作删掉。
- 32个DWORD随机偏移表`6FDD1CE0..1D5F`逐项保留，最后两项为-1、1，非简单五项周期。
  root设置方向和每轮重建root不roll；cache和live seed都跨cutoff轮保留，后续请求继续同一seed。
- 固定seed(low=1,high=0)、start(15,15)到(20,15)、全阻挡且ceiling=0：候选方向为
  `0,7,3,0,3,2,4,5`，只有六次实际碰撞查询、七次roll，最终seed=`0x5A0CC847EEA0F793`。
  ceiling=21时执行两轮，14次roll、最终`0x2AED0D8050155D6D`，缓存不重查阻挡点。

仍保留第十九批有序非回绕room、padding后<=50000、cache地址安全域；不模拟原版内存破坏。
Java回调异常通过Request finally恢复完成移除的占位，但不回滚已消费seed，不声称原版异常
展开语义等价。无seed/非法输入/超大padding在roll前拒绝；正常失败不冒充无副作用。
只写有效prefix并返回count，count/index/vectors由外层接回和恢复占位后归一化；无A*/Toward兜底。

### 第二十批验证与交付

新增19项：`D2NativeIDAStreamTest`12项，`D2PathIdaDispatchTest`新增7项（现16项）。
定向62项/4类通过；全范围**453项/39类通过，失败/错误/跳过均0**。新增随机表静态数据oracle，
与此前五项合计六项执行，无跳过；这是匹配原DLL表数据核对，**不是动态执行DLL函数**。

一项独立递归oracle用300组固定随机小域，逐query、终点、contour/cutoff、节点数、压缩prefix
和最终64位seed比较；显式统计覆盖父耗尽的未使用推进与多contour，不能省略其RNG消费。
它独立实现递归枚举和RNG算术，复用已核对公共方向/压缩helper，不是300项JUnit或DLL动态差分。
其他测试覆盖exact/threshold前消费、flush拒绝、signed h/向零除法/short初始cutoff/int32增量、
缺seed/不安全域早拒绝、跨请求seed连续、异常不回滚、Search快照与live type分离、type0零消费、
临时容差倍增、prefix/live字段隔离，以及真实网格双占位恢复。既有非missile/±100支持域不变。

首轮定向60项中一项失败：复用type0网格测试错误要求随机路径第一段Y非零，而随机路径可以
先水平走再绕障碍。修正为检查向量已归一化，同时保留type0原非零断言；未改算法迎合夹具。
另补两项后定向62项通过。日志`core/build/round20-check.log`保留失败，
`round20-check-final.log`为定向通过；全范围`round20-full.log`通过，最终断言调整后复跑日志
`round20-full-final.log`；XML `.gradle/path-port/round20-full/core/test-results/pathPortTest`。
沿用隔离输出与`pathTestScope=path`，主源码仍完整编译；其他聊天NPC/AI修改不混入本轮提交。

代码/测试`bd2fdc2dd5ddbf45845a2e633904036914244831`已推送origin/master，仅本轮五文件。
最终`round20-full-final.log`复跑亦为453项/39类、失败/错误/跳过0；四交接文档另行提交。

另机复验（原版目录按实际修改）：

```powershell
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
$env:D2_TEST = $env:D2_HOME
.\gradlew.bat -I docs/path-port-regression.init.gradle :core:pathPortTest -PpathRun=round20-check -PpathTestScope=path --tests '*D2NativeIDAStarTest' --tests '*D2NativeIDAStreamTest' --tests '*D2PathIdaDispatchTest' --tests '*D2PathDispatchTest' --console=plain
```

全范围改`pathRun=round20-full`并去掉四个`--tests`。无匹配DLL时静态oracle会显式跳过，不能
据此报告原版验证通过。本轮无新地形/MPQ/TCP/GL/性能或DLL动态函数验收。

**寻路仍未完成**：下一轮优先8/9/11/12入口；生产身份/元数据/room图、nDist与移动消费安装、
矩形递归网格与地形验收仍待。生产仍A*，近期优化保持，不加1.13+ gate或普通自动partial。

## 第二十一批：type8/11击退入口（2026-10-16）

`D2PathDispatch`新增服务端8与客户端11；裸坐标和Search两种入口均可用，生产仍
Map→AStarPathFinder。`Target.coordinates()`新增默认显式拒绝的raw观察接口，已有自定义
Target无需修改即可继续旧调用；`D2PathUnits.observation`提供UNITS_GetX/YPosition投影。
服务端必须读raw坐标，不能把带FOG lead或已uint16截断的samplePoint替代它。

证据继续固定D2MOO `origin/master=0c0bbd1e6bef9181a3da1c046523af622a925416`，未fetch，
读取PathMisc.cpp与Path.cpp表。本机1.10f D2Common.dll SHA256仍为
`B9D1A27C6A35992932C7C47438D02E5269F878E899F6E614D507902CECD1BEAB`。
objdump静态核对`6FDAB240..B3B0`、`6FDAABF0..AD01`及`6FDA9E90..9EB1`：

- server `B27D..B286`读取live unsigned nDist，half=(nDist>>1)+1，非nDistMax/search预算。
  存在live target时读取其raw位置，以max(abs(dx),abs(dy))缩放远离目标的方向；int32乘法
  回绕与有符号向零除法，最终端点按uint16写入。重合目标不改用search target。
- `B348..B35B`先写slot0候选，再直接调用AD10射线，不做velocity/向量探测，不清index。
  `B379`调用#10170，写targetPoint并清targetUnit；即便阻挡使终点等于start也先执行。
  零结果保留候选slot0和原count；非零才回写实际终点和count=1。final/previous target不在此写。
- client `B240..B261`清index、写slot0、调用ABF0，始终返回1，不直接赋count或清targetUnit。
  ABF0先断言live type!=4且目标两轴非零；velocity为零时slot0改start且不查询；否则按live
  count归一化向量，再决定是否调用射线。保留断言前slot/index写入，不伪造count=1。
- 复用已核对D2Toward.trace射线与D2PathResult归一化，不增加A*回退。原有坐标同步、
  nonmissile player/monster、安全方向查表域继续限制；abs导致负divisor的异常整数域显式拒绝。
  Java回调异常保留已发生写入，由Request finally恢复已移除占位，不声称原版异常展开等价。

新增13项：`D2PathKnockbackTest`12项、真实网格Request双入口组合1项；旧unsupported测试移除8/11。
定向59项/3类通过，失败/错误/跳过0，日志`core/build/round21-check-final.log`。
覆盖raw与lead分离、live nDist、零速server射线、负分量向零除法、255距离与uint16回绕、
即时阻挡/重合/零轴、client向量与count副作用、异常写入及真实占位恢复。
本轮是源码与DLL静态控制流核对及Java回归，不是DLL动态函数差分或实机地图验收。
全范围466项/40类通过，失败/错误/跳过0，六项原DLL静态数据oracle执行。
日志`core/build/round21-full.log`；XML `.gradle/path-port/round21-full/core/test-results/pathPortTest`。
生产仍A*；下一批优先type9/12，生产映射/消费、矩形递归网格与实机地图验收继续待办。

## 第二十二批：type9/12 Leap与BackupTurn（2026-10-16）

`D2PathDispatch`接通9/12，现支持全部非空表项（0/1/2/3/5/6/7/8/9/11/12/13/15/16）；
0/16仍需room-aware Search，16另需live owner seed。空表项4/10/14/17仍明确拒绝。
生产仍Map→AStarPathFinder，没有安装原生ECS移动消费。

证据固定D2MOO `origin/master=0c0bbd1e6bef9181a3da1c046523af622a925416`，未fetch，
读取PathMisc.cpp的A720、BackupTurn、Leap及三方向碰撞helper。
本机1.10f D2Common.dll SHA256仍为
`B9D1A27C6A35992932C7C47438D02E5269F878E899F6E614D507902CECD1BEAB`，objdump核对：

- `B1E0..B232` Leap清index、将目标同时写局部终点和slot0，再调用ABF0速度/向量探测。
  返回终点等于start时返回0，slot0保留原目标且不直接改count；成功才回写终点与count=1。
  因为局部终点不是slot0别名，其失败行为不同于type11。断言、零速与向量归一化复用既有语义。
- `B0C0..B122` BackupTurn只清index，不清count；先offset=-4调用A720，返回非零才试-2，
  仅-2未追加时再试+2；正常返回重设-4。几何起点每次仍为Search.start，不是上一段终点。
  容差/velocity不参与A720，live type不被快照分派改写。
- `A720..A87B`每次从live count追加，首次转向也写source，若末步新转向则省略最终点；
  阻挡/反向退出可追加上一完成点。路径buffer逐点写，live count只在正常返回提交。
  新增独立appendLocal编排，保留回调异常前prefix与临时offset，不将异常当正常返回重置。
- 上游A880源码看似int32候选相加，但DLL `A8DF..A8ED`用word add并AND 0xffff后查询。
  本轮最初推断为raw查询，复读调用点后纠正，复用既有firstFreeDirection的uint16候选。
  78点容量越界显式拒绝，不模拟原生写坏内存；之前已写prefix保留。

新增`D2PathLeapBackupTest`15项，含200组固定随机碰撞图与既有纯offset内核逐query/prefix
组合对照（是一项JUnit，不是独立算法或DLL动态oracle）。扩展真实网格Request组合覆盖9/12，
验证占位恢复与最终向量；不增加实机/MPQ/性能或DLL动态函数验收。
首轮44项两处失败来自旧unsupported夹具与旧正向向量断言，改用空表项17和后退负向向量；
最终定向47项/3类通过，失败/错误/跳过0，日志`core/build/round22-check-final.log`。
全范围481项/41类通过，失败/错误/跳过0，六项原DLL静态数据oracle执行。
日志`core/build/round22-full.log`，XML `.gradle/path-port/round22-full/core/test-results/pathPortTest`。
下一工作包优先矩形碰撞网格、跨房间递归及边界证据；生产身份/元数据/room/RNG映射、
nDist与移动消费接入、真实地图验收及性能切换仍待，不能宣称寻路完成。

## 第二十三批：矩形碰撞网格分区与跨房间递归（2026-10-16）

`D2CommonCollision`矩形read/set/reset改为原生分区路由，替换每个格子都从最初room hint
寻房的实现。SizeXY、big size及big pattern共用此路径；点与small cross路径保持现状。
本轮没有切换生产搜索器，普通生产仍Map→AStarPathFinder。

证据固定D2MOO `origin/master=0c0bbd1e6bef9181a3da1c046523af622a925416`，未fetch，
读取`source/D2Common/src/D2Collision.cpp`的Create/AdaptBoundingBox、矩形mask及set/reset递归。
本机1.10f D2Common.dll SHA256仍为
`B9D1A27C6A35992932C7C47438D02E5269F878E899F6E614D507902CECD1BEAB`，objdump静态核对：

- `41BE0..41C98`先拒绝倒置box；以所选grid的半开右/上界切分，slot0为本地矩形，
  slot1先放完整高度的右侧余区，若越过上界则追加裁窄至本地right的上侧余区。
  右上角因此属于右侧子树，不能重复划入上侧。box坐标本身为inclusive右/上界。
- `41CA0..41DD2`先按left/bottom定位room，然后本地扫描，再按数组顺序递归；每个子区的
  hint是刚解析的room，非最初root hint。链式A→B→C可以越过root near-list到C；反之不能
  由root的独立邻居绕过B的near-list。房间缺失/无grid返回0x27，不再与请求mask相与。
- `41B40..41BD2`本地按行OR所有flags&mask，不因为已有非零值而提前退出。
  即使mask=0，缺房子树仍贡献0x27；已读本地高位不能被缺房结果覆盖。
- `43580..436E3`置位、`43CE0..43E57`清位使用相同分区顺序；缺房子树直接跳过。
  左下角缺房时整块不访问，即使块内其他格子可由root直接找到房间；已完成的本地写入
  在后续子区缺失时保留，且兄弟上侧子区仍继续。不是逐格独立路由，也不是全块事务回滚。
- `44910..4493F`确认偶数尺寸向左/下偏置与inclusive末端，`418CB..418DB`确认read在
  两轴signed size<=1时走point gate。保持既有零/负尺寸入口策略，不模拟unsigned超大尺寸。

用显式DFS栈保留原生本地→右→上的访问顺序，避免Java调用栈在长链上溢出，不改变near-room
顺序或主动遍历整个房间图。房间投影仍以D2ActiveRoom.coords表示grid坐标；需正尺寸、
grid尺寸匹配且非null flags/row-offsets。溢出的room右/上界或BoundingBox端点显式拒绝，
不模拟原DLL危险指针/整数回绕域。拒绝后不回滚已完成的其他块写入。

新增`D2CollisionRectangleTest`15项；定向71项/5类通过，失败/错误/跳过0，日志
`core/build/round23-check.log`。覆盖缺anchor、缺grid、mask0、部分读写、DFS顺序、hint切换、
big pattern/size、奇偶边界、2,000房间长链，以及150组平铺图逐格read/set/reset独立结果对照。
150组包含于一项JUnit，非150项测试或DLL动态函数差分；本轮没有实机地形/性能验收。
全范围496项/42类通过，失败/错误/跳过0，六项原DLL静态数据oracle执行。
日志`core/build/round23-full.log`；XML `.gradle/path-port/round23-full/core/test-results/pathPortTest`。

该工作包尚未全收口：small cross按已解析中心room分派、mask与Any短路入口的区别、特殊
窄房间/边界及grid投影进一步核对仍待。下一轮优先完成这些原语，不直接跳到生产整体切换。
后续生产身份/元数据/room/RNG映射、nDist移动消费、真实地图及性能切换验收仍待。

## 第二十四批：small cross与Any碰撞入口（2026-10-16）

`D2CommonCollision`补small cross中心room路由与原生mask/Any分离，新增
`checkAnyCollisionWithPattern`，`D2PathFootprint.RequestEffects.blocked`改用#10122入口。
这是现有投影adapter的改动，没有安装生产ECS原生寻路消费；普通生产仍Map→AStarPathFinder。

证据固定D2MOO `origin/master=0c0bbd1e6bef9181a3da1c046523af622a925416`，未fetch，
读取D2Collision.cpp的42000、42A30、428D0、432A0/436F0与Reset对应函数。
本机1.10f D2Common.dll SHA256仍为
`B9D1A27C6A35992932C7C47438D02E5269F878E899F6E614D507902CECD1BEAB`，objdump静态核对：

- `42000..42129/4261E..4262E`先按中心坐标解析room。中心缺房/缺grid或mask指针为空时，
  mask入口返回请求mask低16位，非point/rectangle的0x27。中心有效才按四个边界位选分支。
  opposite edge组合无case：1格宽或高的房间直接返回mask，不把五点逐格OR当作原生行为。
- `42A30..42B57`中心缺失或窄房间default返回1，即便mask=0；正常内部Any依次检查
  center/left/right/bottom/top并立即短路。边界先查所有本地有效臂，再从已解析中心room
  查询外侧臂；左下角外侧顺序bottom→left（`42C63..42CF6`），其余角按right/bottom、
  left/top、right/top。缺外侧房间直接true，不把缺房标志再与mask相与。
- `428D0..42A1E`矩形Any复用原生分区：`429D5`先调用完整本地mask scanner，`429DA`才
  检查返回低16位；本地碰撞则不递归右/上子树，孩子遇碰撞也停止后续兄弟。不是每格短路，
  也不是先把整棵矩形树OR完再转boolean。沿用第二十三批显式DFS栈与安全域。
- #10122的pattern NONE保留raw point mask返回值，其他受支持pattern返回0/1；未知pattern
  返回1。point mask指针为空补返回0x27，point set/reset则跳过写入，与原版指针gate一致。
- cross置位/清位经原source hint逐点寻房，其语义与中心room解析后的cross读取不同；保留
  `432A0/436F0`及Reset对应调用编排，不把变更后的读路由套给写入/附加presence臂。

新增`D2CollisionAnyCrossTest`15项；最终定向82项/5类通过，失败/错误/跳过0，日志
`core/build/round24-check-final.log`，XML `.gradle/path-port/round24-check/core/test-results/pathPortTest`。
覆盖缺中心、缺mask指针、窄房间default、九种正常边界、中心hint切换、原hint逐点写入、
Any中心/臂访问顺序及矩形整块扫描后子树短路。200组平铺随机图将各pattern mask/Any结果
与独立平面格子OR比较，含于一项JUnit，非200项测试或DLL动态执行。
全范围511项/43类通过，失败/错误/跳过0，六项原DLL静态数据oracle执行。
日志`core/build/round24-full.log`；XML `.gradle/path-port/round24-full/core/test-results/pathPortTest`。

矩形/cross/Any在当前有效非回绕grid投影域的本轮边界已覆盖；不宣称任意坏指针、整数回绕、
原生房间生命周期或全部碰撞API等价。下一包优先真实实体身份、单位元数据、room图和共享RNG
的生产映射；随后才安装nDist/索引推进/耗尽续算/类型恢复消费，保持受控接入，不整体切换。
真实地图/技能移动、性能及另一台电脑实机验收继续待办。

## 第二十五批：真实RoomEx有序跨层路径诊断投影（2026-10-16）

- 检查真实地图桥发现本层adjacentRoomIds会过滤自身和跨层链接；新增独立nullable Long[]导出，
  按原pRoomsNear有效范围保留自身、重复和null槽，键由levelId与该level链表room序号组成。
  旧AI/可见性邻接列表行为不变，返回数组防御复制。
- NativePathRoomGraph接受真实Zone集合，两阶段投影保证跨层及循环链接共享同一Room/bounds对象。
  未加载Zone/无法解析的链接保持null，不推断几何邻接、不做BFS；拒绝未导出拓扑及重复level/room键。
  快照需在加载/卸载后重建；nearby列表不可修改，但D2PathRequest.Room本身仍属可变投影API。
- 依据固定D2MOO origin/master的D2Dungeon.cpp：DUNGEON_GetAdjacentRoomsListFromRoom直接返回
  D2ActiveRoom.nNumRooms与ppRoomList。这明确了剩余边界：RoomEx图不是live active-room图，
  本批仅新增诊断适配入口，不称已完成生产映射或动态DLL差分。本批没有新增DLL算法证据。
- 检查NativeRng发现为31位LCG，不能替代D2Seed；没有改Monster.rngState或生产随机算法。
  实体生命周期身份、原生fp16、共享owner seed及实际路径消费仍待，生产仍Map→AStarPathFinder。
- 新增NativePathRoomGraphTest四项：跨层同ID/有序自身重复null、缺Zone及快照稳定性、
  缺导出/重复键拒绝、count截断与非传递/非几何寻房。首轮定向3项通过后补充第四项并进入全回归。
- 完整回归515项/44类通过，失败/错误/跳过0；日志`core/build/round25-full.log`，
  XML `.gradle/path-port/round25-full/core/test-results/pathPortTest`。

## 第二十六批：live Act房间身份及请求失效校验（2026-10-16）

- 固定D2MOO origin/master `0c0bbd1e6bef9181a3da1c046523af622a925416`，核对
  `source/D2Common/src/D2Dungeon.cpp`的#10035、AllocRoom、#10076 RemoveRoomFromAct、
  #10082 IsRoomInTown。分配调用ReorderNearRoomList压缩活动项；删除从near-list尾部换位。
  所以RoomEx有序导出仍不能当作实际活动列表。本批不新增DLL算法证据，不是动态DLL执行。
- D2PathActiveRooms从真实Act链两阶段捕获：IdentityHashMap保留活动房间对象身份，
  直接复制raw subtile坐标及native town谓词，按nNumRooms捕获实际活动near-list前缀。
  保留自身/重复/null；忽略未使用容量，拒绝跨Act/未捕获链接、错误backlink/ownership及循环链。
  不按roomId、坐标或level/ordinal合并对象；释放后同ID重新分配必须重新捕获。
- requireCurrent校验当前链顺序/长度、ownership/backlink、bounds、levelId/town、grid对象引用、
  活动邻接数量/顺序/身份和投影nearby列表引用。grid位与seed可正常变化；不克隆、不消费seed。
  这是串行game-thread请求局部一致性检查，不是历史版本计数、并发锁或碰撞数据快照。
- RequestEffects和Dispatch.Effects新增带activeRooms重载；绑定时要求startRoom为owner房间
  同一投影对象，拒绝边界相同的伪房间；footprint操作/blocked及concrete search入口检查失效。
  旧三参数调用保留；创建这些对象不会安装ECS生产后端。每次检查遍历捕获图，未作性能优势声明。
- 新增D2PathActiveRoomsTest十二项，使用真实Dungeon.allocRoom/removeRoomFromAct验证分配压缩、
  删除尾换位与同ID重分配、跨层town、raw subtile、顺序/空槽/容量尾部、图失效/不失效边界、
  外来链接/ownership/cycle拒绝、空Act、同bounds伪身份、失效前不改footprint，
  以及Request→Dispatch→Footprint跨房间搜索和footprint恢复。
- 首轮一项测试误用不写碰撞的pattern0，改为pattern1并补写入前置断言；另一次构建遇到
  生成class文件缺失，换独立输出并强制编译后通过。定向59项/3类通过；随后加强删除尾换位断言。
  最终完整527项/45类通过，失败/错误/跳过0；日志`core/build/round26-full.log`，
  XML `.gradle/path-port/round26-full/core/test-results/pathPortTest`。
- common层活动图已可组合，ECS/Zone运行期Act桥仍未接通；真实单位生命周期身份、fp16坐标、
  live owner D2Seed及路径状态消费/实图/性能/另机验收仍待。生产保持Map→AStarPathFinder。

## 第二十七批：生产实体生命周期身份与旧目标ID复用防护（2026-10-16）

- 检查Act1MapBuilderD2MOD发现导出末尾freeDrlg并releaseDataTables，当前不能把生成期Act引用
  直接挂到Zone当作运行期上下文。本轮转为推进另一个已列出的前置包：真实实体生命周期身份。
- 固定D2MOO origin/master `0c0bbd1e6bef9181a3da1c046523af622a925416`，核对
  `source/D2Common/include/Path/Path.h`：pTargetUnit(+0x58)与dwTargetType(+0x5C)/dwTargetId(+0x60)
  独立存在。host token用于避免ECS数字ID重用造成别名，不声称是原生内存指针或分配算法移植；
  本批未新增DLL算法证据，也不是动态DLL差分执行。
- 新增Transient NativePathIdentity：公共EntityFactory.createEntity的实际birth边界分配token。
  token只保存World与entityId及自身对象身份，无全局计数器；组件reset清空引用，旧token不因池化重用复活。
  isCurrent遵循ECS正常删除flush边界；死亡/原地复活保留实体身份。不改NativeRng或Monster.rngState。
- Pathfind.targetIdentity保存目标token，每次成功装路径均替换（含内部detour/recovery）；
  坐标命令及reset清空。Pathfinder在读取旧目标或动态阻塞恢复前校验token，失效就停止当前路径，
  不对同ID替代实体发起新的搜索。工厂之外未提供身份的legacy实体保留unknown/null兼容行为。
  仅保护已绑定的路径追踪，不宣称所有Target组件、交互/AI/网络请求已完成生命周期防护。
- 新增NativePathIdentityTest六项及PathfinderRecoveryTargetTest四项，覆盖跨World、真实删除/ID复用、
  组件池化、death/revive、移除/重新分配、unknown、detour保留、坐标/换目标清理、旧身份停止前不查询。
  真实ServerEntityFactory玩家创建测试补token断言，加入path编译范围；其旧fixture缺少
  townPortalRegistry导致注入失败，补注册该依赖，不修改生产WarpInteractor。
- 修复fixture后定向29项/3类通过；完整538项/47类通过，失败/错误/跳过0。
  完整统计含新增10项及新纳入的既有工厂1项；日志`core/build/round27-full.log`，
  XML `.gradle/path-port/round27-full/core/test-results/pathPortTest`。
- 下一步仍需将token接入D2PathUnits/Continuation的live target观察；原生类型/元数据、fp16、
  live owner D2Seed、运行期Act/Zone桥、移动状态消费、实图/性能/另机验收仍待。生产仍Map→AStarPathFinder。

## 第二十八批：ECS生命周期与原生目标观察一致性（2026-10-16）

- 延续固定D2MOO `0c0bbd1e6bef9181a3da1c046523af622a925416`：Path.h的pTargetUnit是独立身份字段。
  本轮补host适配契约：原先Request.State仅比较target是否为空，无法发现continuation观察与request
  传入了两个不同单位快照。没有新增DLL算法证据，不是动态DLL差分或完整实体元数据移植。
- D2PathUnits.Observation统一owner/target/speed快照，实现原有Target接口，距离/采样/raw坐标仍调用
  既有#10399、FOG lead与UNITS坐标投影；每次调用先执行只读host guard。旧三参数observation接口保留。
  创建观察不提前读取静态path方向，stop-distance先返回的原生惰性采样顺序不变。
- NativePathUnitBinding在实际World中要求已知owner/target token，读数据前/后均检验同一生命周期。
  Reader按操作各读一次不可变原生Unit（自目标仅读一次），observe之后数据冻结，移动后需重新捕获。
  保存请求可显式携带原token，不能按旧数字ID重绑定新单位；无身份不自动创建，未捕获数据不补默认值。
  Reader必须提供已核实的native type/size/raw fp16或静态int32/方向，尚无自动生产元数据解析或浮点转换。
- Request.State对标准Observation核对同一target Unit对象及speed；compute在collidedMask/路径写入与
  footprint调用前要求仍为原观察对象且guard通过。标准纯快照使用no-op guard；任意legacy自定义Target
  继续遵循原调用方契约，不能声称它也已验证。State/Observation是按操作构建，不跨重选目标复用。
- 新增NativePathUnitBindingTest十三项及RequestTest两项：操作快照/重新捕获、raw静态和dynamic高字、
  self-target、unknown拒绝、owner/target替换前后校验、真实删除与ID复用、显式保存token拒绝替代实体、
  惰性静态方向、无数据/非法speed、过期请求写入前拒绝，以及真实footprint+Dispatch编排恢复。
  Request另验证同坐标不同Unit对象、speed不一致及构造后替换观察均拒绝。
- 首轮定向98项/4类通过，随后补raw高字与保存token两项边界。首个全范围构建因输出class文件缺失
  在core编译阶段中断，改独立输出并使用no-daemon/max-workers=1强制重编译重跑。
- 最终完整553项/48类通过，失败/错误/跳过0；日志`core/build/round28-full-final.log`，
  XML `.gradle/path-port/round28-full-final/core/test-results/pathPortTest`。
- 尚未安装ECS原生移动消费；运行期Act/Zone桥、原生元数据/fp16来源、shared owner D2Seed、
  状态推进/实图/性能/另机验收仍待。生产仍Map→AStarPathFinder。

## 第二十九批：实际ECS单位类型和尺寸来源

- 新增NativePathUnitMetadata.read，读取World中Class与Monster.monstats2、Object.base、Missile.missile，
  返回不可变type/sizeX/sizeY；PLR/MON/OBJ/MIS/ITM显式映射原生0/1/2/3/4。
- 固定上游origin/master 0c0bbd1e6bef9181a3da1c046523af622a925416的Units.cpp #10336/#10337：
  玩家2×2、物品1×1；怪物/对象分轴，导弹用同一Size。对象尺寸保留int32，不用ECS Size组件。
- 缺失宿主表行拒绝，区别于原生查表确认无记录的零尺寸；WRP混合tile warp/portal，不能直接映射。
- 5项新测试验证真实World组件、原生类型映射、分轴/大尺寸、快照、缺失/删除/歧义拒绝。
  完整隔离回归558项/49类通过，失败/错误/跳过0；core/build/round29-full.log，
  XML位于.gradle/path-port/round29-full/core/test-results/pathPortTest。
- 仅接通类型和尺寸来源；完整Reader的raw fp16/静态路径方向、door/town等其余元数据仍待。
  未切换生产Map→AStarPathFinder；运行期Act/Zone、shared seed、实图/性能验收仍待。
  无新增DLL算法证据或动态DLL执行。

## 第三十批：真实对象door元数据与原始坐标组合

- NativePathUnitMetadata按固定上游0c0bbd1的#10392/UNITS_CheckIfObjectOrientationIs1读取
  Objects.IsDoor/Orientation==1；Orientation验证uint8域，非零不等于1。宿主IsDoor为boolean，
  此处只声称谓词，不声称保留DLL原始字节。
- dynamicUnit/staticUnit将元数据与显式raw DynamicPath或静态int32组成D2PathUnits.Unit，
  自动选择对象door分支；静态lead可显式携带path+0x64字节，缺失不猜零。
- 6项新测试覆盖两侧door偏移、0/1/2/255方向、普通对象、快照、原始坐标/缺失方向、动态空路径、
  非法字节、Reader组合及目标删除。完整564项/49类通过，失败/错误/跳过0。
  日志core/build/round30-full.log；XML .gradle/path-port/round30-full/core/test-results/pathPortTest。
- 尚无raw坐标自动来源，碰撞原始字节/其余元数据、Act/Zone/shared seed仍待；生产仍A*。
  未新增DLL算法证据或动态DLL执行，未宣称完整Reader、迁移完成或性能提升。

## 第三十一批：真实MonStats城镇许可元数据

- NativePathUnitMetadata捕获Monster.monstats的npc/inTown及表行已提供状态；
  ownerCanBeInTown(nativeIsRevive)供Request.State使用，非怪物无该城镇限制。
- 固定上游0c0bbd1的MONSTERS_CanBeInTown要求有效MonStats且npc/inTown/UNITFLAG_ISREVIVE之一成立。
  宿主表行缺失是unknown，调用时拒绝，包括显式ISREVIVE=true时，不冒充原生查表null结果。
- 原生flag仍由调用方明确提供；resurrected/playerRevive/所有权/MonStats2.revive均不作为替代。
  原生ISREVIVE亦用于部分宠物/佣兵，自动生命周期flag桥仍待。
- 新增4项测试：完整字段组合、复活来源不推定flag、缺失表行/冻结快照、非怪物分支。
  完整568项/49类通过，失败/错误/跳过0；core/build/round31-full.log，
  XML .gradle/path-port/round31-full/core/test-results/pathPortTest。
- 生产仍Map→AStarPathFinder；raw坐标/碰撞字节/Act与Zone/shared seed仍待，无新增DLL算法证据
  或动态DLL执行，不宣称完整城镇适配、完整Reader或迁移完成。

## 第三十二批：真实对象表到footprint的语义适配

- NativePathObjectFootprint读取World中的Object.mode及Objects表字段，复用NativePathUnitMetadata
  尺寸/door解析，与显式raw静态int32坐标、实际D2ActiveRoom及可选方向组成D2PathFootprint.Unit。
- 固定上游0c0bbd1的#10338/#10410依据：door/vis/missile/SubClass选择掩码，当前mode索引碰撞值。
  宿主boolean只能提供非零语义，按0/1编码，不声称无损DLL原始字节。SubClass验证uint8，
  HasCollision要求8项，Object.mode先unsigned解释再限制0..7；缺失不补默认值。
- 6项测试覆盖掩码优先级、八模式、快照/尺寸冻结、非法数据、raw坐标/显式方向与真实网格往返。
  完整574项/50类通过，失败/错误/跳过0；core/build/round32-full.log，
  XML .gradle/path-port/round32-full/core/test-results/pathPortTest。
- 本入口不自行绑定身份；返回coordinates须与本操作生命周期观察共用。真实room可保留null，
  不推断RoomEx。raw坐标自动来源、动态footprint字段、Act/Zone/shared seed仍待，生产仍A*。
  沿用既有网格与原生算法，无新增DLL算法证据或动态DLL执行。

## 第三十三批：footprint与生命周期观察单次捕获

- NativePathUnitBinding.observeFootprints(speed, FootprintReader)按操作读取exact owner/target
  footprint，直接以其coordinates构造带原token guard的Observation，返回FootprintObservation。
  自目标只读一次；不调用构造时的坐标Reader，旧observe接口兼容保留。
- 前后身份检查及后续Observation/requireCurrent拒绝替代实体；返回exact footprints供effects使用，
  不再要求调用方独立读一次坐标。Reader仍负责正确原生数据，身份有效不等于元数据/位置自动刷新。
- 新增4项：exact坐标共用与冻结、自目标单读、读取前后身份替换、非法输入与后续替换。
  完整578项/50类通过，失败/错误/跳过0；core/build/round33-full.log，
  XML .gradle/path-port/round33-full/core/test-results/pathPortTest。
- 未自动安装effects，未拦截直接静态footprint调用；操作间变化须重捕获。raw坐标自动来源、
  动态footprint字段、Act/Zone/shared seed仍待；生产仍Map→AStarPathFinder。
  本批为host适配，无新增DLL算法证据或动态DLL执行。

## 第三十四批：effects owner快照与生命周期复检

- Request.State增加包内owner观察验证，RequestEffects构造与每次网格操作前调用：
  标准Observation要求exact owner.coordinates及原观察对象仍安装，执行guard校验生命周期。
- 修复仅比较owner类型/坐标/尺寸可混入另一份快照的host缺口；target原exact检查保留。
  直接调用removeOwner/removeTarget/addOwner/addTarget/blocked亦先校验，不只依赖compute。
- 新增3项：同坐标替代owner拒绝、失效guard下全部五个效果拒绝且网格不变、替换观察拒绝。
  完整581项/50类通过，失败/错误/跳过0；core/build/round34-full.log，
  XML .gradle/path-port/round34-full/core/test-results/pathPortTest。
- legacy自定义Target契约保留；同一Unit之外的mode/mask等字段仍需调用方正确捕获，
  不拦截直接静态footprint或自定义search。生产仍A*，raw坐标/动态字段/Act与Zone/shared seed仍待。
  本批仅host一致性验证，无新增DLL算法证据或动态DLL执行。

## 待下一批处理

- 第一/二批以外的调用者与上述坐标/RNG/身份投影限制，仍需后续专项对照。
- 已核对1.10f部分路径消费、重算与生产房间后置gate，修复恢复目标身份及实体目标的耗尽续算；下一步按实际
  pathType设计受控接入。已补独立nDistance/final/索引续算投影与Unit快照/FOG采样/10399算术，
  已补独立10142结果规范化/flags生命周期与请求gate/城镇规则/footprint调用编排；仍需生产身份/
  元数据/房间图适配、nDist步进消费与原生搜索dispatcher剩余类型。已补footprint上层选择与真实
  内存网格adapter、第十七批类型生命周期及第二十批0/1/2/3/5/6/7/13/15/16有状态分派（0/16需Search
  room，16另需live owner seed）；下一步优先8/9/11/12。矩形递归网格原语边界仍需专项核对；随后将状态与真实移动接通，
  不把partial当普通exact成功，越界样本仍需补证。
  攻击速度、目标链等其余修正继续专项对照；火焰之河AI fallback单列待办。
- 不移植1.11+充能技能查找门槛、1.13+目标房间门槛或Ubers行为。
