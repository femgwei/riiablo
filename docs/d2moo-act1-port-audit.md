# D2MOO Act 1 地图移植差异审计

审计基线：D2MOO `D2Common/src/Drlg`（Diablo II 1.10f 逆向重实现）与本仓库
`D2MOO_JAVA`、riiablo `Act1MapBuilder` 导出链。审计重点是当前最小可玩闭环会经过的：

```text
DRLG_AllocDrlg
  -> level connections / D2DrlgOrth
  -> DRLGOUTDOORS_GenerateLevel
  -> DRLGVER_CreateVertices
  -> DRLGOUTWILD_InitAct1OutdoorLevel
  -> RoomEx / RoomTile / TileSub
  -> DrlgExport
  -> riiablo TileGrid / Zone
```

## 结论

当前代码适合在现有基础上修正，不建议重写。数据结构、文件边界、绝大多数函数签名和
riiablo 导出层都已经存在；真正的问题是若干上游语义错误和被截断的调用链。重写会丢失
已经验证过的 DS1 尾部兼容、RoomEx footprint、DT1 依赖、旋转网格和图层导出修复。

但当前版本不能称为“一比一移植”。最关键的差异是：

1. `D2LvlPrestIds` 的 Act 1 ID 在 border 之后整体错误。bridge 原版为 28，Java 为 170；
   cave/DOE 原版为 51/52，Java 为 192/191；cliff cave 原版为 24/25，Java 为 194/193。
   接回生成函数后会索引错误的 `LvlPrest.txt` 记录，是地图错乱的直接原因。
2. `DRLGVER_CreateVertices` 被替换成矩形近似，并省略了 `pRoomData` 的关卡连接区间拼接。
   连接顶点 flags、出口边界和后续路径拓扑因此丢失。
3. `DrlgOutWild.initAct1OutdoorLevel` 在第三个 secondary border 后提前结束，河流、洞穴、
   transition、第四边界、waypoint、shrine 和 special presets 都没有进入主链。
4. Java 曾无条件把所有 Act 1 顶点写成自创的 1/2/3/4 方向；原版 alternate direction 是
   west/north/east/south = 0/1/2/3，并且 Blood Moor、Cold Plains、Burial Grounds 必须保留
   `CreateVertices` 的初始方向。
5. `D2C_LvlSubIds` 是 0-based。Act 1 四类 border 应为 0/1/2/3，旧 Java 从 1 开始，
   跳过 cliffs 并把最后一次调用传给了错误的 sub-record。
6. `DrlgOutPlace` 用 `nAct == 1` 判断 Act 1，但 `getActNoFromLevelId` 返回 0-based；该分支
   实际在 Act 2 执行。
7. `getBridgeCoords` 不是原版桥查询：旧 Java 遍历全图并测试 Act 3 bridge 的可放置性；
   原版只查询中央河列中已放置的 Act 1 bridge（preset 28、picked file 1）。

## 本轮修正状态

- **FIXED** — Act 1 所有已声明 `LVLPREST_*` 常量与 D2MOO 枚举逐名核对并校正。
- **FIXED** — `DRLGVER_CreateVertices` 恢复原顶点顺序、inclusive extent、四方向
  `D2DrlgOrth` 区间拼接、level-link/preset flags 和 level-local 坐标语义。
- **FIXED** — tile-to-grid 缩放后的重复顶点合并包含 ring 尾/头；旧保护条件会漏掉闭环
  重复点并留下零长度边。
- **FIXED** — Act 1 特殊 cliff direction 处理以及 `sub_6FD85300`、`sub_6FD85350` 语义。
- **FIXED** — Act 1 wilderness 与 Moo Moo Farm 的四阶段 secondary border 范围和顺序。
- **FIXED** — river、cliff cave、fallback cave、town transition、waypoint、shrines、
  special presets 恢复到原主调用链。
- **FIXED** — Act 编号判断与 Act 1 bridge lookup。
- **VERIFIED** — 问题日志 seed `0x171A6100` 连续生成摘要一致；Blood Moor、Cold Plains、Stony Field 均可
  建立 RoomEx、导出 floor/wall，且无越界、重复 floor position 或无效 tile id。

## 尚未完成的原版能力

### Act 1 原生 dirt-path 拓扑 — MISSING

Java 只有名为 `generateDirtPath` 的 per-room 直线近似，而且此前因 Act 判断错误、
`pDirtPathGrid` 未按原版初始化，实际没有恢复原道路。原版还需要：

```text
DRLGOUTDOORS_SpawnAct1DirtPaths
DRLGOUTDOORS_CalculatePathCoordinates
sub_6FD7F5B0          (hub / path graph preparation)
sub_6FD80750          (path search)
sub_6FD7F810          (path chain conversion)
DRLG_OUTDOORS_GenerateDirtPath (3x3 neighbor mask -> floor flags)
```

本轮没有把直线近似伪装成原版 A*；运行日志会明确输出
`native dirt-path topology pending`。riiablo 的 town seam repair 暂时仍是出城可玩的兼容层。
这是下一阶段应优先移植的模块。

### RoomTile / TileSub — PARTIAL

主流程可输出 floor/wall，但日志仍有 `GetTileCache: nSize is 0`，部分 shadow、warp、animated
tile 和 sub-theme 行为仍是占位或简化实现。它们影响细节、碰撞与对象，不应与本轮已经修复
的 outdoor topology 混在一次重写中。

### Server — PARTIAL

Server 目前消费 riiablo 的扁平化 `TileGrid + Zone`，没有完整保留 D2MOO RoomEx 激活、邻接、
warp 与房间生命周期语义。应先稳定同 seed 的客户端地图，再让 server 直接消费同一份 DRLG
结果或可序列化快照，避免客户端和服务端各自生成一套近似地图。

### 其他 Act — HIGH RISK / OUT OF SCOPE

本次枚举审计同时发现 Java 中大量 Act 2–5 `LVLPREST_*` 和部分 `LEVEL_*` 常量仍与原版
错位。本轮只校正当前 Act 1 可玩链，避免未经测试地改变其他 Act；移植其他 Act 前必须先做
同样的逐名枚举校验。

## 验证命令

```powershell
.\gradlew.bat :D2MOO_JAVA:build
.\gradlew.bat :core:test --tests com.d2moo.common.drlg.D2MooAct1NativeParityTest
.\gradlew.bat :core:test --tests com.riiablo.map.d2moo.Act1D2MOOLayoutBridgeTest
.\gradlew.bat :core:test --tests com.riiablo.map.Act1MapBuilderD2MooLayersTest
```

跨机器验证时重点比较以下日志：`DRLG_OUTWILD vertices`、每个 `grid2 stage`、最终
RoomEx/preset room 数量、export floor/wall/shadow 数量，以及 `native dirt-path topology pending`。
