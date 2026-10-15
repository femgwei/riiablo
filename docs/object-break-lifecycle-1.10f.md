# 1.10f 一次性容器：损坏帧与声音

日期：2026-10-16。

## 原版证据

- D2MOO `D2Game/src/OBJECTS/ObjMode.cpp`：
  `OBJECTS_OperateFunction03_Urn_Basket_Jar`、`05_Barrel`、`07_ExplodingBarrel`
  仅接受 NU，切换 OP、取消 targetable、安排 ENDANIM；不会恢复为完整容器。
  普通瓦罐/木桶按概率调用掉落，爆炸桶走范围伤害和连锁引爆，不保证掉落物品。
- D2Common `src/Units/Units.cpp:UNITS_SetAnimStartFrame`（由 `UNITS_ChangeAnimMode` 调用）的 UNIT_OBJECT 分支：
  `FrameCnt[mode]`、`Start[mode]` 和 `FrameDelta[mode]` 各有独立职责。
- 本机合法 1.10f MPQ：对象 7 的 ON COF 有 10 帧，但
  `FrameCnt2=1, Start2=9`；对象 4/9/52 的 ON COF 有 9 帧，
  `FrameCnt2=1, Start2=8`。ON 必须显示源图的末帧，不能用 `[0, FrameCnt2)`
  截断后再将 Start2 钳到 0。对象 94/95/208/209/362 则有独立的一帧 ON COF。
  额外核对非零起始帧：TowerTome 的 OP 为 Start=1/FrameCnt=3/COF=4，
  Casket 50/51 为 1/4/5，火焰 160..162 的 ON 为 10/26/37；新范围保留独立起点与播放帧数。
- 本机 `D2Client.dll` SHA256：
  `94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282`。
  VA `0x6FB50683` 按 classId 查 `0x6FB957F0` 的声音表指针，
  `0x6FB5069F` 按 mode 取 soundId，`0x6FB506AE` 调播放函数。
  OP 槽为表记录的 `+4`：对象 7→2645（`object_wood_break_1`），
  11→2534（`object_barrel_explode`），4/9/52/94/95→2636
  （`object_urn_break_1`），208/209→2535（`object_basket_1`），
  362→2570（`object_dungeonbasket`）。声音组继续由 Sounds.txt 随机选变体。

## 修复范围

- 源帧区间按 `[Start, min(COF帧数, Start + FrameCnt))` 设置，并从该区间起点播放。
- CofManager 在分发模式事件前更新 Object.mode，避免 COF 解析读取上一模式。
  ObjectP 同步实时 OP/ON；地图 NativeObjectState 保留用于房间重建的最终 ON。
- 普通容器接入现有 OBJECT_INTERACTION 服务端请求，复用同层、距离、交互资格校验，
  由服务端处理一次性状态、掉落和陷阱。缺少持久化组件时也拒绝 OP/ON 或已有 Sequence 的重复操作。
- 客户端进入 OP 时播放一次对应的空间音效；同模式 restart 和恢复 ON 不重播破坏声。
- `ObjectBreakPresentationTest` 核验真实 MPQ 帧范围、声音组资源，以及 ECS 一次性事件、
  实时/持久化模式和重建状态；实机听感、完整点击画面及双客户端交互仍需验收。

## 验证

- 使用 `build/object-break-tests.init.gradle`（本地忽略的构建配置）运行定向测试，
  将测试报告和二进制测试结果隔离，避免与其他任务争用 test-results：7 类、27 项测试通过，失败/错误 0。
  包括新增 `ObjectBreakPresentationTest` 的 3 项真实 MPQ/ECS 检查，以及
  `CofLayerLoaderReleaseTest`、`CofModeTransitionTest`、`ClientObjectSnapshotStateTest`、
  `NativeObjectOperateTableTest`、`NativeTrappedContainerIntegrationTest`、
  `ObjectInitializerNativeStateTest`。
- `:server:d2gs:compileJava` 通过。
- `:desktop:offscreenCamp` 使用本机 1.10f MPQ 加载生产客户端：
  `[OFFSCREEN_CAMP] result=PASS act=1 player=1293 frames=3`，BUILD SUCCESSFUL。
  本地报告 `build/visual-tests-object-break/rogue-encampment-manifest.txt`。
- 另一台电脑验收：各点击一个木桶、爆炸桶、瓦罐和竹篓，确认破坏声只播一次、
  动画结束后保持损坏状态且不能再次触发；离开并重进房间、另一客户端观察同一对象。
  本轮不把启动烟测或声音资源存在视为实机听感/完整交互验收。
