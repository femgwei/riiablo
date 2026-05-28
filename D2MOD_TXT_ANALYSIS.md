# D2MOD 与 riiablo 地面渲染差异分析（txt 读取与处理逻辑）

## 1. D2MOD 使用的 txt/bin 数据源

| 数据 | D2MOD 来源 | riiablo 对应 |
|------|------------|--------------|
| **LevelDef** | leveldefs bin: SubType, SubTheme, SubWaypoint, SubShrine, SizeX[diff], SizeY[diff], OffsetX, OffsetY | Levels.txt: SubType, SubTheme; SizeX/SizeY 从 Levels.txt |
| **LvlSub** | lvlsub: Type, File, BordType, GridSize, Dt1Mask, **Prob[0-4], Trials[0-4], Max[0-4]** 按 SubTheme 索引 | LvlSub: Type, File, Prob[], Trials[], Max[] |
| **LvlPrest** | lvlprest: Def, LevelId, File[1-6], Dt1Mask | LvlPrest |
| **LvlTypes** | lvltypes: File[1-32], Act | LvlTypes |

## 2. 关键差异：FloorGrid 存的是什么

### D2MOD
- **pFloorGrid 存 FLAGS**，不直接存 tile ID
- 默认值 `0x40002`
- 路径处 `(v19<<8)|0x82`，由 byte_6FDCF958 查表得 v19
- 渲染时根据 flags 通过 LvlSub 换算出具体瓦片
- 匹配检查用 `nFlags & 0x3F0FF00`（tile type 位段）、`nFlags & 2`（floor 位）

### riiablo
- **TileGrid.floorIds 直接存 tile ID**（int）
- 从 dt1s.get(id) 取 DT1.Tile 写入 zone.floorLayer
- 若 id 在 zone.dt1s 中不存在，渲染会失败或显示错误

## 3. 可能的 txt 处理问题

### 3.1 SubType/SubTheme 来源
- **D2MOD**: 从 `leveldefs` 的 `dwSubType`, `dwSubTheme` 读取
- **riiablo**: 从 `Levels.Entry.SubType`, `Levels.Entry.SubTheme`
- 需确认：Levels.txt 和 leveldefs 结构是否一致（有些项目合并了）

### 3.2 LvlSub.Prob 索引
- **D2MOD**: `pLvlSubTxtRecord->nProb[nSubTheme]`，SubTheme 通常 0-4
- **riiablo**: `entry.Prob[subTheme]`，需保证 `entry.Prob` 长度 ≥ 5
- 若 `subTheme` 越界或 Prob 未正确加载，pickSubThemes 结果会错误 → 瓦片选择异常

### 3.3 DRLGTILESUB_DoSubstitutions 中的 nMax
- **D2MOD**: `pLvlSubTxtRecord->nMax[pOutdoorLevel->nSubTheme]` — 按 **nSubTheme** 索引
- **riiablo**: 需核对 `nMax` 的索引是否为 SubTheme

### 3.4 DS1 floorLine/floorLen
- DS1 floor 数组索引：`floorLine = width * numFloors`，`floorIndex = y*floorLine + x*numFloors + layer`
- replaceSubPreset 中若 floorLine/floorIndex 算错 → 读到错误 cell → 纹理错位或重复

## 4. “一块 tile 纹理反复重复” 的可能原因

1. **selectTerrainTile / selectTerrainTileFromLvlSub 总是返回同一 tile**：Prob 或随机逻辑问题
2. **DS1 group 的 floor 区域整个 8x8 都是一种瓦片**：DS1 设计如此
3. **LvlSub.getByType 返回的 entry 顺序或过滤与 D2MOD 不一致**：选错 entry
4. **subThemeMask 全 0**：pickSubThemes 失败，fallback 到 selectTerrainTile，若 dt1Mask 单一则易得同一 tile

## 5. “纹理错位” 的可能原因

1. **grid ↔ zone 坐标偏移**：zone.tx/ty 非 0 时，applyTileGridToZone 的 (x,y) 若按 grid 原点写，zone 原点不一致会错位
2. **TileGrid 与 zone 尺寸不一致**：grid 80x80，zone 56x96，copy 时 min 区域外的 zone 区域未覆盖
3. **Zone.index 与 TileGrid 行列约定**：`floorIds[y][x]` 和 `Zone.index(tilesX, x, y)` 的 x/y 顺序是否一致

## 6. 路径瓦片选择：D2MOD vs riiablo（findPathFloorId / id<=0）

### 6.1 D2MOD 路径瓦片流程

D2MOD **不读任何 txt** 来选择路径瓦片，完全在代码内完成：

1. **`DRLG_OUTDOORS_GenerateDirtPath`**（DrlgOutdoors.cpp）：
   - 先用 `pDirtPathGrid` 标记路径经过的格子
   - 遍历每个 3x3 邻域，用 `byte_6FDCF958` 查表得 `v19`（0x00~0x2E）
   - 当 `v19 != 0` 时，写 FloorGrid：`(v19<<8)|0x82`
   - `byte_6FDCF958` 是**硬编码表**，根据路径连通性模式 → 土路瓦片类型索引

2. **`DRLGROOMTILE_LoadInitRoomTiles`**：从 FloorGrid 读 packed flags，调用 `GetTileCache(TILETYPE_FLOOR, packedValue)`

3. **`DRLGROOMTILE_GetTileCache`**（DrlgRoomTile.cpp）：
   - 从 packed 解出 `nStyle`（= v19）、`nSequence`（来自 0x82 等低字节）
   - `D2CMP_10088_GetTiles(pTiles, TILETYPE_FLOOR, nStyle, nSequence, ...)` 在已加载 DT1 中查瓦片
   - 若查不到：fallback `GetTiles(..., TILETYPE_WALL_LEFT_EXIT, 0, 0, ...)` 取 `ppTileLibraryEntries[0]`

4. **`pTiles` 来源**：`DRLGROOMTILE_LoadDT1FilesForRoom` 读 **LvlTypes.txt**（`LevelType` → `szFile[1-32]`），按 Dt1Mask 加载 DT1。LvlTypes 只提供 DT1 文件列表，**不提供“路径用哪个 tile”的配置**。

5. **D2MOD 无 “id<=0” 概念**：它用 style/sequence 查 DT1，查到的是 `D2TileLibraryEntryStrc`，不关心数字 id。`id=0`（orientation=0, main=0, sub=0）在 DT1 里是合法瓦片。

### 6.2 riiablo 的 findPathFloorId

- 优先：`OutdoorFeatures.getPathTileIdForZone` — 从已放置的 path/dirt **preset 的 DS1** 取地板瓦片 id
- 备选：grid、兄弟 zone、floorLayer、Act1 户外 fallback
- **明确排除 id<=0**：是 riiablo 自定义策略，因 id=0 常为默认草，路径不易区分

### 6.3 与 D2MOD 的一致性

| 维度 | D2MOD | riiablo |
|------|-------|---------|
| 路径瓦片来源 | style/sequence → LvlTypes 的 DT1 | path preset DS1 / grid / fallback |
| 是否读 txt 选路径瓦片 | **否**，仅 LvlTypes 提供 DT1 路径 | **否**，getPathTileId 从 preset 取 |
| id<=0 过滤 | 无此概念 | 有，避免用草地瓦片当路径 |
| 路径瓦片多样性 | 由 byte_6FDCF958 决定 46 种 pattern | 由 path preset 的 DS1 内容决定 |

**结论**：`findPathFloorId` 的 “id<=0 不返回” 与 D2MOD 无直接对应——D2MOD 根本不用 tile id 选路径。riiablo 排除 id=0 是合理的启发式，问题更可能在：path preset 未正确放置、DS1 无有效 floor、或 zone.dt1s 不包含 path 用到的 DT1。

### 6.4 riiablo 已移植 D2MOD 路径逻辑（2025-01 更新）

已实现：

- `D2MOD_PATH_TILE_TABLE`（byte_6FDCF958）查表
- `TileGrid.dirtPathFlags`（DirtPathGrid 等价）
- `drawPathSegmentOnZone`：只标记 dirtPathFlags
- `generateDirtPathFromGrid`：按 3x3 邻域连通性查表得 v19，写 `(v19<<8)|0x82` 对应瓦片到 floorIds
- `resolvePackedPathToTileId`：style/sequence → `dt1s.get(Orientation.FLOOR, main, sub)` 查表
- 邻域位序与 D2MOD MapOffsetToBoxIndex 一致

### 6.5 路径点计算：riiablo vs D2MOD（为何城镇里是直线）

#### riiablo 当前实现

1. **createPathVertices**：顶点来自 zone 中心或固定边
   - 城镇：`(gridWidth/2, gridHeight-1)` 底部中心
   - Blood Moor：`(gridWidth/2, 0)` 顶部中心
   - Cold Plains / Den of Evil：`(gridWidth/2, gridHeight/2)` 中心

2. **createPathSegment**：
   - 同 zone：`createStraightPath` — 直线插值 (start → end)
   - 跨 zone：`createCrossZonePath` — 因 `startBoundaryX/Y == start.tileX/Y`，`steps1=0`，退化为直线插值

3. **问题**：跨 zone 时 `start.tileX/Y` 与 `end.tileX/Y` 分属**不同 zone 的本地坐标系**
   - Town 的 (40, 79) 和 BM 的 (28, 0) 线性插值无意义
   - 在 Town 的 grid 上画 (40,79)→(28,0) 会得到从底部中心到左下角的斜线 → **城镇里看到的直线**

#### D2MOD 的实现（DrlgOutdoors.cpp）

1. **顶点来源**（`DRLGOUTDOORS_SpawnAct1DirtPaths`）：
   - **城镇**：根据出口方向固定偏移，如 WEST:(+59,+19), NORTH:(+29,+35), EAST:(+4,+22), SOUTH:(+29,+3)
   - **户外 grid**：遍历 `pGrid[0]`，仅当 `nGrid0Entry` ∈ {4,5,6,7,24,25,28,51,52} 且满足条件时创建顶点，位置 `(8*i+3, 8*j+3)`，即 **warp/出口预设位置**

2. **路径段**：`pPathStarts[i]` 为顶点链表，`pVertex->pNext` 连接相邻顶点；`sub_6FD7F810` 根据拓扑构建连接

3. **画线**：`sub_6FD75F60` 在 **全局坐标** 下对 `(pVertex, pVertex->pNext)` 做 Bresenham，再通过 `tDrlgCoord` 转换到每个 room 的 DirtPathGrid

#### 核心差异（已修复 2025-01）

| 维度 | riiablo 原 | D2MOD | riiablo 现 |
|------|------------|-------|------------|
| 城镇顶点 | zone 中心/边 | 固定偏移 (59,19) 等 | TOWN_OFFSETS[dir] ✓ |
| 方向来源 | 布局推断 | pRoomData->nDirection | zone.townExitDirection ✓ |
| BM 顶点 | zone 边中心 | grid nGrid0Entry | grid 格 (8*i+3, 8*j+3) ✓ |
| 路径调整 | 无 | CalculatePathCoordinates | calculatePathCoordinates ✓ |
| Hub | 无 | sub_6FD7F5B0 | BM 边格中心 ✓ |
| 路径形状 | Bresenham 直线 | A* 寻路避 preset | pathfindGrid A* ✓ |
| 坐标 | 混合 | 全局 tile/subtile | 全局 subtile ✓ |

**结论**：riiablo 已按 D2MOD 完全对齐路径生成逻辑。

## 7. 建议的调试步骤

1. 打开 `DEBUG_GROUND_MAP`，观察 `[GroundDebug]` 日志
2. 检查 `zone.tx`, `zone.ty` 是否为 0；若 BM 的 zone 有偏移，需在 apply 时加偏移
3. 检查 `idHistogram`：若只有 1–2 种 id，说明选瓦片过于集中
4. 检查 `failedResolve`：若很大，说明 grid 中的 id 在 zone.dt1s 中找不到
5. 对照 D2MOD 的 leveldefs vs Levels.txt，确认 SubType/SubTheme 是否一致
6. 路径：确认 `getPathTileIdForZone` 是否找到 path preset，且 preset 的 DS1 floor 在 zone.dt1s 中可解析
