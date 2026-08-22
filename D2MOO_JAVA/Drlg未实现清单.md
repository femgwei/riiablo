# Drlg 模块未实现清单

## 一、数据结构（占位符或未实现）

### 1. 核心数据结构
- ✅ **D2DrlgGridStrc** - 已实现
- ✅ **D2ActiveRoom** (D2ActiveRoomStrc) - 活动房间结构，已实现
- ✅ **D2DrlgTileGrid** (D2DrlgTileGridStrc) - 瓦片网格结构，已实现
- ✅ **D2DrlgLogicalRoomInfo** (D2DrlgLogicalRoomInfoStrc) - 逻辑房间信息结构，已实现
- ✅ **D2Seed** (D2SeedStrc) - 种子结构，已实现

### 2. 房间相关数据结构
- ✅ **D2DrlgOutdoorRoomStrc** - 户外房间结构，已实现
- ✅ **D2DrlgPresetRoomStrc** - 预设房间结构，已实现
- ✅ **D2DrlgRoomTilesStrc** - 房间瓦片结构，已实现
- ✅ **D2DrlgTileDataStrc** - 瓦片数据结构，已实现
- ✅ **D2DrlgAnimTileGridStrc** - 动画瓦片网格结构，已实现

### 3. 其他数据结构
- ✅ **D2DrlgVertexStrc** - 顶点结构，已实现
- ✅ **D2DrlgOutdoorInfoStrc** - 户外信息结构，已实现
- ✅ **D2LvlWarpTxt** - 关卡传送点文本结构，已实现
- ✅ **D2RoomCoordListStrc** - 房间坐标列表结构，已实现
- ✅ **D2UnkOutdoorStrc** - 户外次要边界等回调用结构（已实现，含函数式接口）
- ✅ **D2MazeRecord** - 迷宫记录结构，已实现
- ✅ **D2C_PackedTileInformation** - 打包瓦片信息联合体（位域结构），已实现
- ✅ **D2DrlgMapStrc** - Drlg 地图结构，已实现
- ✅ **D2LevelFileListStrc** - 关卡文件列表结构，已实现
- ✅ **D2DrlgFileStrc** - Drlg 文件结构，已实现
- ✅ **D2DrlgOutDesertInitStrc** - 沙漠初始化结构，已实现
- ✅ **D2MapAIStrc** - 地图AI结构，已实现
- ✅ **D2MapAIPathPositionStrc** - 地图AI路径位置结构，已实现
- ✅ **D2DrlgCoords** - Drlg 坐标结构，已实现

## 二、外部依赖模块（未实现）

### 1. SEED 模块（随机数生成）
- ✅ 已实现（`com.d2moo.common.seed.Seed`）：InitSeed、RollLimitedRandomNumber、GetRandomValue、GetLowSeed/GetHighSeed、SetSeeds、RollPercentage 等，被 DrlgOutdoors、DrlgOutDesr、DrlgMaze 等使用

### 2. DATATBLS 模块（数据表查询）
- ⚠️ DATATBLS_GetLevelTypesTxtRecord（框架已实现，需要数据表加载）
- ⚠️ DATATBLS_GetLvlWarpTxtRecordFromLevelIdAndDirection（框架已实现，需要数据表加载）
- ⚠️ DATATBLS_GetLevelDefRecord（框架已实现，需要数据表加载）
- ⚠️ DATATBLS_GetLvlPrestTxtRecord（框架已实现，需要数据表加载）
- ✅ DATATBLS_GetMonStatsTxtRecordCount（已实现，返回占位符值）
- ✅ DATATBLS_GetSuperUniquesTxtRecordCount（已实现，返回占位符值）

### 3. D2CMP 模块（瓦片库管理）
- ⚠️ **部分桩已实现**（`com.d2moo.common.d2cmp.D2Cmp`）：LoadTileLibrarySlot 占位（创建占位哈希对象），GetTiles(ppTileLibraryHash, type, style, sequence, pTileList, size) 已实现并返回 0（安全），单瓦片 getTiles 返回 null；真实文件加载与瓦片查询仍待实现

### 4. DUNGEON 模块（房间和坐标管理）
- ⚠️ **部分桩已实现**（`com.d2moo.common.dungeon.Dungeon`）：GetRoomExFromRoom、GetDrlgRoomFromActiveRoom、isCoordinateInRoom、getRoomCenter、gameTileToSubtileCoords、allocRoom 等有框架或占位实现；RemoveRoomFromAct、CreateRoom、WorldToTile/TileToWorld 仍为 TODO/占位

### 5. 内存池管理系统
- ⚠️ **API 已模拟实现**（`D2Pool` / `D2MemoryPool`），对应 C++ 的 `D2_CALLOC_STRC_POOL`、`D2_CALLOC_POOL`、`D2_FREE_POOL` 等。
- Java 有 GC，无法实现 C++ 那种“预分配一大块、按需切分、显式归还复用”的真内存池；当前实现每次分配仍是 `new`，仅做 API 兼容与可选追踪/统计，实际内存由 GC 管理。

## 三、函数实现不完整（标记为 TODO）

### DrlgActivate 模块
- ✅ 大部分函数已实现，依赖其他模块的函数已标注

### DrlgDrlg 模块
- ⚠️ 部分函数依赖外部模块（D2CMP, DATATBLS, DUNGEON, SEED）

### DrlgDrlgRoom 模块
- ✅ 核心函数已实现

### DrlgRoomTile 模块
- ✅ 核心函数已实现（initRoomGrids, addRoomMapTiles, loadDT1FilesForRoom, freeRoom, freeTileGrid, allocTileGrid, allocTileData, countAllTileTypes, countWallWarpTiles, getNumberOfShadowsFromRoom）
- ✅ 瓦片数据处理函数已实现（getTileCache, initTileShadow, initTileData, initFloorTileData, initWallTileData, initShadowTileData, initTileDataDefaults, initializeTileDataFlags, reallocRoofTileGrid）
- ✅ loadInitRoomTiles - 已完整实现加载并初始化房间瓦片的核心函数
- ✅ 预设单位和传送门函数已实现（addTilePresetUnits, addWarp, loadWallWarpTiles, loadFloorWarpTiles）
- ✅ 链接瓦片管理函数已实现（getLinkedTileData, addLinkedTileData, linkedTileDataManager, getCreateLinkedTileData）
- ⚠️ 部分函数依赖外部模块（D2CMP_10078_GetTileStyle, D2CMP_10082_GetTileSequence, D2Common_COLLISION_FirstFn_6FD41000）
- ⚠️ 依赖 D2CMP, DATATBLS, DUNGEON 模块

### DrlgPreset 模块
- ✅ 核心函数已实现（addPresetUnitToDrlgMap, initPresetRoomGrids, addPresetRoomMapTiles, loadDrlgFile, freeDrlgFile）
- ✅ 超级唯一怪物处理逻辑已完善
- ⚠️ 依赖 DS1 文件解析（DRLGPRESET_ParseDS1File）
- ✅ DRLGROOMTILE_CountAllTileTypes、DRLGROOMTILE_CountWallWarpTiles 已实现并接入

### DrlgDrlgWarp 模块
- ✅ 核心函数已实现（getDestinationRoom, toggleRoomTilesEnableFlag, getWarpDestinationFromArray, getLvlWarpTxtRecordFromWarpIdAndDirection, getWarpIdArrayFromLevelId, getWaypointRoomExFromLevel）
- ✅ 依赖 D2LvlWarpTxt, DATATBLS（已实现）

### DrlgDrlgGrid 模块
- ✅ 核心函数已完整实现（fillNewCellFlags, alterEdgeGridFlags, alterAllGridFlags）
- ✅ 网格操作函数已优化

### DrlgDrlgLogic 模块
- ✅ 核心函数已实现（freeDrlgCoordList, initializeDrlgCoordList, allocCoordLists, assignCoordListsForGrids, setCoordListForTiles, sub_6FD77110, getRoomCoordListIndex, getRoomCoordList）
- ✅ 依赖 D2DrlgLogicalRoomInfo, D2RoomCoordListStrc（已实现）

### DrlgDrlgVer 模块
- ✅ 核心函数已实现（allocVertex, createVertices, freeVertices, getCoordDiff）
- ✅ 依赖 D2DrlgVertexStrc（已实现）

### DrlgDrlgAnim 模块
- ✅ 核心函数已实现（initCache, testLoadAnimatedRoomTiles, animateTiles, allocAnimationTileGrids, allocAnimationTileGrid, updateFrameInAdjacentRooms）
- ✅ 依赖 D2DrlgTileDataStrc, D2DrlgAnimTileGridStrc（已实现）
- ⚠️ 部分函数依赖 D2CMP 模块（initCache 需要 D2CMP 获取瓦片数据）

### DrlgMaze 模块
- ✅ 基础函数已实现（getRandomRoomExFromLevel, resetMazeRecord, initLevelData）
- ⚠️ 复杂算法函数待实现（generateLevel, pickRoomPreset, buildBasicMaze, linkMazeRooms, mergeMazeRooms, fillBlankMazeSpaces, addAdjacentMazeRoom 等）
- ⚠️ 依赖 D2MazeRecord, SEED 模块（已实现）

### DrlgOutdoors 模块
- ⚠️ 依赖 D2DrlgOutdoorInfoStrc
- ⚠️ 大部分函数只是框架，需要完整实现

### DrlgOutRoom 模块
- ✅ 所有函数已实现（allocDrlgOutdoorRoom, freeDrlgOutdoorRoom, freeDrlgOutdoorRoomData, initializeDrlgOutdoorRoom）
- ✅ 关卡链接函数已实现并修复（linkLevelsByLevelCoords, linkLevelsByLevelDef, linkLevelsByOffsetCoords）
- ✅ **模块完成度：100%**
- ⚠️ 依赖 D2DrlgOutdoorRoomStrc

### DrlgOutPlace 模块
- ✅ 关键函数已完整实现（setOutGridLinkFlags, placeAct1245OutdoorBorders）
- ✅ GetOutLinkVisFlag、D2DrlgOutdoorPackedGrid2InfoStrc、DRLGVER_GetCoordDiff 已实现
- ✅ createLevelConnections 及其所有依赖函数已完整实现
  - sub_6FD823C0（处理链接数据）
  - sub_6FD826D0（基于重叠检查的关卡链接）
  - sub_6FD82750（户外关卡链接）
  - 所有链接函数（sub_6FD81330, sub_6FD81380, sub_6FD81530, sub_6FD81720, sub_6FD81950, sub_6FD81AD0, sub_6FD81B30, sub_6FD81BF0, sub_6FD81CA0, sub_6FD82050, sub_6FD82130, linkAct2Outdoors, linkAct2Canyon, linkAct4Outdoors, linkAct4ChaosSanctum, sub_6FD82360）
  - 坐标计算函数（sub_6FD81430, sub_6FD815E0）
  - 所有常量数组（gAct1WildernessDrlgLink 等）
- ✅ sub_6FD82360 - 已完整实现标志设置逻辑
- ✅ buildKurast - 已完整实现
- ✅ initAct3OutdoorLevel - 已完整实现
- ✅ createOutdoorRoomEx - 已完整实现
- ✅ sub_6FD82750 中的 DRLGROOM_AddOrth 调用 - 已实现
- ✅ sub_6FD83970 - 已完整实现设置丛林坐标
- ✅ 预设关卡方向设置（ROGUEENCAMPMENT、LUTGHOLEIN）- 已实现
- ✅ BLACKMARSH 特殊逻辑 - 已完整实现
- ✅ DRLG_GenerateJungles - 已实现简化版本（基本结构、重叠检测、链接、排序），完整版本需要多个辅助函数（GenerateJunglesAttachPoints、JungleComputeConnexity、JungleUpdateAttachPointsDirections、JungleNormalizeLevelPresetId）
- ✅ initOutdoorRoomGrids - 已完整实现户外房间网格初始化（包括传送点/神殿替换、地板标志设置等）

### DrlgOutJung 模块
- ✅ buildJungle - 已完整实现
- ✅ buildLowerKurast - 已完整实现
- ✅ buildKurastBazaar - 已完整实现
- ✅ buildUpperKurast - 已完整实现
- ✅ spawnRandomPreset - 已完整实现

### DrlgOutDesr 模块
- ✅ 核心函数已实现（initAct2OutdoorLevel, placePresetVariants, placeCliffs, placeBorders, addExits 等）
- ✅ DRLGOUTDOORS_SpawnAct12Shrines、SpawnAct12Waypoint、AddAct124SecondaryBorder 已实现，DrlgOutDesr 中已接通调用

### DrlgOutdoors 模块
- ✅ generateLevel - 已完整实现户外关卡生成逻辑
- ✅ initAct4OutdoorLevel - 已完整实现 Act4 户外关卡初始化
- ✅ spawnOutdoorLevelPreset - 已完整实现户外关卡预设生成
- ✅ freeOutdoorInfo - 已完整实现释放户外信息逻辑
- ✅ spawnPresetFarAway - 已完整实现在远离指定坐标的位置生成预设
- ✅ spawnRandomOutdoorDS1 - 已完整实现随机生成户外 DS1 预设

### DrlgOutSiege 模块
- ✅ 基础函数已实现（addAct5SecondaryBorder）
- ⚠️ 复杂生成函数待实现（initAct5OutdoorLevel, placeCaves, placeBarricadeEntrancesAndExits, placeSpecialPresets, placePrisons, connectBarricadeAndSiege）

### DrlgOutWild 模块
- ✅ 基础函数已实现（getBridgeCoords）
- ⚠️ 复杂生成函数待实现（initAct1OutdoorLevel, testSpawnRiver, spawnRiver, spawnCliffCaves, spawnTownTransitionsAndCaves, spawnSpecialPresets, spawnCottage）
- ⚠️ 依赖 D2DrlgOutdoorInfoStrc（已实现）

### DrlgTileSub 模块
- ✅ addSecondaryBorder - 已完整实现，包括循环遍历逻辑修复
- ✅ pickSubThemes - 已完整实现子主题选择逻辑
- ✅ doSubstitutions - 已完整实现替换执行逻辑（包括 D2UnkOutdoorStrc2、sub_6FD8B010、sub_6FD8B130、sub_6FD8ACE0、sub_6FD8AA80）
- ⚠️ 部分函数依赖外部模块（DRLGROOMTILE_ReallocRoofTileGrid、DRLGROOMTILE_InitTileShadow、DRLGROOM_AllocPresetUnit、DUNGEON_GameTileToSubtileCoords）
- ⚠️ 其他函数大部分已实现或为框架

## 四、优先级建议

### 高优先级（基础支持）
1. **D2Seed** - 随机数生成，被大量模块依赖
2. **内存池管理系统** - 被所有模块使用
3. **D2DrlgTileGrid** - 瓦片网格，核心数据结构
4. **D2DrlgRoomTilesStrc** - 房间瓦片统计，被多个模块使用
5. **D2DrlgTileDataStrc** - 瓦片数据，核心数据结构

### 中优先级（功能支持）
1. **D2DrlgVertexStrc** - 顶点结构，用于网格操作
2. **D2DrlgOutdoorRoomStrc** - 户外房间结构
3. **D2DrlgPresetRoomStrc** - 预设房间结构
4. **D2DrlgLogicalRoomInfo** - 逻辑房间信息
5. **D2C_PackedTileInformation** - 打包瓦片信息（位域处理）

### 低优先级（特定功能）
1. **D2ActiveRoom** - 活动房间（可能在其他模块中定义）
2. **D2DrlgAnimTileGridStrc** - 动画瓦片网格
3. **D2LvlWarpTxt** - 传送点文本
4. **D2RoomCoordListStrc** - 坐标列表
5. **其他特定用途的结构**

### 外部模块优先级
1. **SEED 模块** - 最高优先级，被大量使用
2. **内存池管理系统** - 最高优先级，所有模块都需要
3. **DATATBLS 模块** - 高优先级，数据查询
4. **D2CMP 模块** - 高优先级，瓦片库管理
5. **DUNGEON 模块** - 中优先级，房间管理

## 五、统计

- **数据结构总数**：约 25+ 个
- **已实现数据结构**：约 20+ 个（包括 D2DrlgGridStrc, D2ActiveRoom, D2DrlgTileGrid, D2DrlgLogicalRoomInfo, D2Seed, D2DrlgOutdoorRoomStrc, D2DrlgPresetRoomStrc, D2DrlgRoomTilesStrc, D2DrlgTileDataStrc, D2DrlgAnimTileGridStrc, D2DrlgVertexStrc, D2DrlgOutdoorInfoStrc, D2LvlWarpTxt, D2RoomCoordListStrc, D2MazeRecord, D2C_PackedTileInformation, D2DrlgMapStrc, D2LevelFileListStrc, D2DrlgFileStrc, D2DrlgOutDesertInitStrc, D2MapAIStrc, D2MapAIPathPositionStrc, D2DrlgCoords 等）
- **未实现数据结构**：0 个（D2UnkOutdoorStrc 已实现）

- **外部模块总数**：5 个
- **已实现外部模块**：部分实现（SEED 模块已实现，DATATBLS 模块框架已实现）
- **未实现外部模块**：D2CMP, DUNGEON 模块（部分函数已实现）

- **函数实现完整度**：约 75-85%（框架已实现，核心函数已实现，部分依赖外部模块和数据结构）
