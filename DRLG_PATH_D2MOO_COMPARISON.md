# D2MOO 路径系统与 riiablo 实现对比

## D2MOO 路径实现概要

### 1. 调用时机与流程

- **DRLGOUTDOORS_SpawnAct1DirtPaths**（DrlgOutdoors.cpp:990）：在 `DRLGOUTWILD_InitAct1OutdoorLevel` 中调用，先于房间构建。
- **DRLG_OUTDOORS_GenerateDirtPath**（DrlgOutdoors.cpp:902）：在 **DRLGOUTPLACE_InitOutdoorRoomGrids** 中，对**每个 outdoor room** 单独调用。
- 路径按房间绘制：每个房间有自己的 `pDirtPathGrid`、`pFloorGrid`，在房间本地坐标下工作。

### 2. 关键数据结构

- **pDirtPathGrid**：路径掩码，用 `sub_6FD75F60` 将顶点连线写入。
- **pFloorGrid**：默认 0x40002，路径处写入 `(v19<<8)|0x82`。
- **byte_6FDCF958**：根据 3x3 邻居路径连通性，选择路径瓦片类型。

### 3. 与 riiablo 的主要差异

| 方面 | D2MOO | riiablo |
|------|-------|---------|
| 路径瓦片 | 用 FloorGrid 标志 `(v19<<8)|0x82`，渲染时由 LvlSub 换算出具体瓦片 | 直接用 tile ID 写入 TileGrid |
| 瓦片来源 | 由 byte_6FDCF958 查表，再经 LvlSub 选路径瓦片 | 从兄弟 zone 的 grid/floorLayer 取任意地板瓦片 |
| 坐标系统 | 按房间，每房间独立 grid | 按关卡共享 grid，zone 尺寸可能与 grid 不一致 |
| 调用时机 | 房间初始化阶段，每房间一次 | 所有 zone 生成完后，集中生成路径 |

### 4. 已实施的修复（与 D2MOO 对齐）

1. **findPathFloorId 增强 fallback**：当 grid/兄弟 zone/floorLayer 都找不到有效 id 时，尝试 `Orientation.FLOOR` + mainIndex 2~25（对应 D2MOO byte_6FDCF958 的路径瓦片索引范围）。
2. **移除 generateTestPathOnBloodMoor**：避免与 generatePathsOnTileGrid 的路径绘制冲突。
3. **Blood Moor 应用计数日志**：`applyTileGridToZone` 对 BM 始终打印 `applied` 数量，便于诊断。
