## DRLG 移植进度记录

### 已完成

- **按 DrlgType 分发关卡生成类型（与 D2MOO 一致）**
  - 所有关卡通过 `Levels.DrlgType` 区分：
    - `1` → 迷宫型（Maze）
    - `2` → 预制型（Preset）
    - `3` → 野外型（Outdoor）
  - Java 侧不再用“是否存在 LvlPrest 记录”来决定是否整图预制，避免把随机关卡当成整图 DS1 读导致乱图。

- **Act1 户外链接逻辑（gAct1WildernessDrlgLink）**
  - 在 `Act1MapBuilderD2MOD` 中实现了与 `D2MOO::sub_6FD823C0 / sub_6FD82050` 语义一致的逻辑：
    - 使用 `LevelLinkData.rand[4][15]` 记录每个迭代的随机方向 / 状态。
    - `checkNotOverlapping`：
      - 使用“允许贴边、不允许真正交叠”的矩形判断，对应 `DRLG_CheckNotOverlappingUsingManhattanDistance`。
      - 对 **BLOODMOOR / COLDPLAINS / BURIALGROUNDS / ROGUEENCAMPMENT** 的相对关系做与 D2MOO 一致的约束：
        - Burial Grounds 额外的 nRand2 约束已实现（`nRand2[k] == rand[0][k]` 的行优先映射）。
        - Rogue Encampment 使用 `dword_6FDD05C0` 64 项布尔查表，索引公式完全按原版实现。

  - **布局计算与重叠检测说明**
    - **放置顺序**（act1Links）：0=Stony Field（固定坐标）→ 1=Cold Plains（连 Stony）→ 2=Blood Moor（连 Cold Plains）→ 3=城镇（连 Blood Moor）→ 4=Burial Grounds（连 Cold Plains）。
    - **为何会有重叠**：不止 Town+BM 两个矩形，而是 5 个区域构成“树状”连接：Stony 为根，Cold Plains/Burial Grounds 挂在 Stony 上，Blood Moor 挂在 Cold Plains 上，Town 挂在 Blood Moor 上。每个子区域相对于父区域有 4 个随机方向（N/S/E/W），几何上可能出现：Town 与 Cold Plains 重叠、Burial Grounds 与 Blood Moor 重叠等。
    - **为何需要回溯**：当某次随机方向组合导致重叠或违反 TABLE 约束时，`checkNotOverlapping` 返回 false，算法回溯（counter--）并重置当前及后续的 rand，重新尝试另一组方向，直到找到无重叠且合法的布局。

- **Act2/Act4/Act5 户外链接的通用重叠检查**
  - `BaseMapBuilderD2MOD.checkNotOverlapping` 改为“允许贴边”版本，对齐 `DRLG_CheckNotOverlappingUsingManhattanDistance` 的含义。
  - `Act2MapBuilderD2MOD`、`Act4MapBuilderD2MOD`、`Act5MapBuilderD2MOD` 使用该通用检查函数。

- **LvlSub.txt 解析与加载**
  - 新增 `com.riiablo.codec.excel.LvlSub`，字段对齐：
    - `Name / Type / File / Expansion / BordType / GridSize / Dt1Mask / Prob0-4 / Trials0-4 / Max0-4`
    - `getByType(int type)` 用于按 `Type` 分组（对应 `Levels.SubType`）。
  - 在 `Files` 中注册并加载：
    - `public final LvlSub LvlSub;`
    - 构造函数中 `LvlSub = load(LvlSub.class);`

- **LvlSub.File + DS1 主地形块替换（8x8 房间级）**
  - 在 `applyLvlSubDs1Room` 中实现：
    - 优先从 `pickSubThemes` 的 subThemeMask 中选取带 `File` 的 LvlSub.Entry，用其 DS1 group 铺满 8x8 房间。
    - 兜底：当 subThemeMask 未选出任何带 File 的 entry 时，对 Act1 户外（Blood Moor ~ Tamoe Highland）使用 **LvlSub.Prob[SubTheme]** 做随机判定，通过则用第一个带 File 的 entry。
    - 密度由 LvlSub.txt 的 Prob 列控制，贴近 D2MOO 的 Prob 语义。

- **路径生成系统（TileGrid 级别）**
  - 在 `Act1MapBuilderD2MOD` 中实现了 `generatePathsOnTileGrid(Map map, int seed)` 方法：
    - **路径顶点系统**：从城镇和特殊关卡创建路径起点
      - 城镇（Rogue Encampment）：在出口位置创建顶点
      - 特殊关卡（Blood Moor、Den of Evil 等）：在关卡中心或入口位置创建顶点
    - **路径坐标计算**：连接路径顶点，计算路径路径
      - 同一 zone 内使用直线连接
      - 跨 zone 连接使用边界连接点
      - 当前连接：城镇 → Blood Moor → Cold Plains / Den of Evil
    - **路径网格生成**：在 TileGrid 上绘制路径
      - 使用路径方向和连接信息
      - 路径宽度为 3 tiles，使用圆形扩展
      - 自动查找路径地板 ID（优先使用已有地板）
      - **跨 zone 修复**：`drawPathSegment` 在跨 zone 时同时在 start 和 end 的 grid 上绘制（原逻辑只画在 start.zone，导致 BM 无路径）
  - 已集成到 `Map.generate()` 流程中，在所有 zone 生成完成后执行
  - 使用 D2MOD 时**始终**将 TileGrid 应用到 Blood Moor（原依赖 RenderFromTileGrid 默认为 false 导致路径不显示）
  - 参考 D2MOO: `DRLGOUTDOORS_SpawnAct1DirtPaths`

### 正在进行 / TODO

- **用 LvlSub 驱动 Act1 野外边界 / 细节生成**
  - 目标：逐步用 `LvlSub + LvlPrest` 替代 `OutdoorFeatures` 中针对 Act1 的硬编码逻辑，使：
    - 边界块（cliffs / middle / corner / border）的类型与出现频率尽可能接近 D2MOO。
    - dirt path / extra rocks / ponds 等细节由 `LvlSub.Prob / Max / Trials` 驱动。
  - 当前状态：
    - **主地形**：`applyLvlSubDs1Room` 已用 LvlSub.File + DS1 铺 8x8 房间，兜底时按 `Prob[SubTheme]` 控制密度。
    - **边界**：`placeBorders` / `placeBordersFromLvlSub` 已用 LvlSub（BordType>=0）+ LvlPrest 驱动，Act1-5 户外关卡统一走该逻辑。
    - `OutdoorFeatures.placePaths / placeShrines / placeWaypoint` 使用启发式查找 `LvlPrest`（通过文件名包含 "shrine"/"wp"/"path"），未接入 `LvlSub`。

- **路径生成系统优化（进行中）**
  - 当前实现使用简化的直线连接和边界连接
  - 待优化：
    - 使用更智能的路径查找算法（A* 或类似）
    - 改进跨 zone 路径连接逻辑，考虑 zone 的实际位置关系
    - 支持更多路径连接（如 Cold Plains → Stony Field → Dark Wood）
    - 使用 LvlSub 数据驱动路径类型和样式选择

- **完整 DRLGOUTDOORS 管线移植（长期目标）**
  - 在 Java 侧建立与 `D2DrlgStrc / D2DrlgLevelStrc / D2DrlgOutdoorInfoStrc / D2DrlgTileGridStrc` 对应的结构。
  - 迁移 `DRLGOUTDOORS_GenerateLevel`、`DRLGOUTWILD_*`、`DRLGOUTPLACE_*` 等函数：
    - 使用 `LvlSub / LvlPrest / Levels` 直接操作 grid / tile。
    - 最终由 DRLG 结果驱动 `Map.Zone` 的 `tiles[] / flags[]`，而不是仅靠 DS1 preset。
  - 目前已在 MapBuilder 层做了“关卡级别 / 链接级别”的对齐，Tile 级 DRLG 部分接入（路径生成）。

### 城镇出口方向与预设选择（已做）

- **城镇预设选择：selectIndex = townDir**
  - LvlPrest 顺序：fileId[0..3] = TownN1, TownE1, TownS1, TownW1（出口朝北/东/南/西）。
  - 城镇在 Blood Moor 的西(townDir=1)时需出口朝东→TownE1=fileId[1]；即 fileId[townDir] 即为正确预设。
  - 曾误用 `(townDir+2)%4` 导致选到 TownW1 而非 TownE1，已恢复为 `selectIndex = townDir`。

### 城镇出口 Warp 修正（已做）

- **Warp 目标覆盖**
  - `Map.addWarpDestinationOverride(levelId, mainIndex, dstLevelId)`：在 D2MOD 生成后，将 Rogue Encampment(1) 出口 mainIndex=5 覆盖为 Blood Moor(2)。
  - `Map.generate(act)` 中：遍历 Levels 的 Vis，将指向 Cold Plains 的项写入 override；并兜底 `addWarpDestinationOverride(1, 5, 2)`。
  - `Act1MapBuilderD2MOD` 尾部：`map.addWarpDestinationOverride(LEVEL_ROGUEENCAMPMENT, 5, LEVEL_BLOODMOOR)`。
- **ServerEntityFactory.createWarp 兜底**
  - 若 override 未生效（单机用 ClientEntityFactory→ServerEntityFactory，map 注入一致），在创建 warp 实体时：若当前关卡为 Rogue Encampment(levelId==1) 且算出的目标为 Cold Plains(dst==3)，强制改为 Blood Moor(2)。不限制 mainIndex，以兼容预设里出口 tile 的 mainIndex 与 VIS_5_42 不同的情况。

### 当前问题与待完善（用户反馈）

- **地图不连贯 / 断层**
  - **可能原因 1**：`applyTileGridToZone` 当前仅对 **Blood Moor**（level.Id==2）生效，Cold Plains、Den of Evil 等仍完全由 generator 生成，未使用 TileGrid。
  - **可能原因 2**：各 zone 为独立生成，跨 zone 边界处的坐标/接缝未做对齐处理，容易出现视觉断层。
  - **可能原因 3**：路径生成（generatePathsOnTileGrid）只在 TileGrid 上画线。已修复：跨 zone 时在 end zone 的 grid 上也绘制；使用 D2MOD 时始终 applyTileGridToZone 到 Blood Moor。

- **地图边界未生成**
  - **可能原因 1**：`placeBordersFromLvlSub` 依赖 `pickBorderPresetFromLvlSub` 在 LvlPrest 中查找与 LvlSub.File 匹配的 DS1；若 LvlPrest 的 LevelId 过滤过严（`preset.LevelId != 0 && preset.LevelId != zone.level.Id`）或路径格式不一致，会返回 null，导致边界位为空。
  - **可能原因 2**：LvlSub 中对应 Level 的 SubType 可能没有 BordType>=0 的条目（如 Act1 野外 Type=6 可能全是 BordType=-1 的细节，没有边界专用条）。
  - **可能原因 3**：渲染流程是否使用 presets 的 wall/cliff 层尚需确认；若只渲染 floor，边界 preset 的墙体可能不会显示。

- **建议排查顺序**
  1. 在 `placeBordersFromLvlSub` 中加调试日志，确认是否进入、borderSubs 是否非空、pickBorderPresetFromLvlSub 是否返回非 null。
  2. 检查 LvlSub.txt 中 Act1 野外对应 SubType 是否存在 BordType>=0 的 entry。
  3. 评估是否将 `applyTileGridToZone` 扩展到所有 Act1 户外 zone（Cold Plains 等），使 TileGrid 路径与主地形一致。

### 移植步骤回顾（D2MOD → riiablo）

1. **已做**：按 D2MOD 的 DrlgType / 户外链接 / LvlSub / 路径生成等，在 `Act1MapBuilderD2MOD`、`BaseMapBuilderD2MOD` 中实现并接入 `Map.generate(act)`。
2. **已做**：Warp 目标覆盖：`addWarpDestinationOverride` + `getWarpDestinationOverride`，在 `ServerEntityFactory.createWarp` 中优先用 override，并兜底“Rogue Enc 任意出口若指向 Cold Plains 则改为 Blood Moor”。
3. **未做 / 待完善**：见下方「还未完成的步骤」。

### 还未完成的步骤

- **城镇出口仍到 Cold Plains（单机）**
  - 若在应用上述 fallback 后仍到 Cold Plains，需排查：
    1. 单机使用的 factory 为 `ClientEntityFactory`（继承 `ServerEntityFactory`），`createWarp` 会走 `super.createWarp`，故 fallback 应会执行。
    2. 确认城镇 zone 的 warp 是否由 `MapManager.createWarps(zone)` 通过 `zone.specials` 创建；若出口 warp 来自预设 DS1 且 tile id/mainIndex 与预期不同，fallback 已放宽为「levelId==1 且 dst==3 即改 2」。
    3. 若仍不对，可在 `ServerEntityFactory.createWarp` 中临时打日志：`zone.level.Id`、`mainIndex`、`dst`（override 前后），确认是否进入 fallback 以及最终写入 warp 的 `dstLevel`。
- **LvlSub 驱动边界/细节**、**路径优化**、**完整 DRLGOUTDOORS 管线**：见上文 TODO 与「当前问题与待完善」。

### 使用说明（给未来的自己 / 助手）

- 如果需要“接着上次往下做”：
  - 先看本文件确认进度；
  - 再看以下关键类的当前实现：
    - `Act1MapBuilderD2MOD`（特别是 `placeTown / placeBloodMoor / checkNotOverlapping / generate` 尾部的高级功能）
    - `BaseMapBuilderD2MOD`（`calculateCoordOffset* / checkNotOverlapping / createZoneWithGenerator`）
    - `OutdoorFeatures`（`placeBorders / placePaths / placeWaypoint / placeShrines`）
    - `LvlSub` 与 `Files` 的加载部分。
  - 按 TODO 小节中的条目，选择一个局部（例如“使用 LvlSub 精细挑选 Act1 边界预制体”）继续实现。

