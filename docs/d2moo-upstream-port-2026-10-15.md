# D2MOO 上游 1.10f 修正移植

## 参考基线

- 本机 D2MOO 检出：`8322494ed1f715ad51552f169df76cf600fabc71`（2025-03-01）。
- 审查远端：`0c0bbd1e6bef9181a3da1c046523af622a925416`（2026-10-13）。
  相对旧检出有158个提交，含合并提交，并非全为本月新增。
- 只 fetch 远端引用，没有切换本机 D2MOO 检出。
- 目标为1.10f。本轮读取上游原版地址/反汇编证据，未独立反汇编本地DLL。

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

额外扩展A1检查存在两项失败，**没有修复，也未在干净旧提交独立复现**，不能宣布全项目绿：
`Act1D2MOOLayoutBridgeTest.fixedSeedAct1LayoutIsStableAndExportable` 的level5共享地板坐标
预期1/实际0；`mapBuilderProjectsNativeMonasteryWarpSlots` 的level26缺少端点。
本批没有改写A1生成器；后续应专项核对断言基线/原生端点，不靠放宽断言处理。
真实TCP、三难度/多seed人口矩阵和GL实机验证未执行。

## 待下一批处理

- 第一/二批以外的调用者与上述坐标/RNG/身份投影限制，仍需后续专项对照。
- 碰撞、寻路、攻击速度、目标链等本月修正继续专项对照，不视为已移植。
- 不移植1.11+充能技能查找门槛、1.13+目标房间门槛或Ubers行为。
