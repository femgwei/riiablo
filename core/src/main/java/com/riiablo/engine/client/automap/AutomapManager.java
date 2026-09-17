package com.riiablo.engine.client.automap;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntMap;

import com.riiablo.codec.DC6;
import com.riiablo.codec.excel.AutoMap;
import com.riiablo.graphics.PaletteIndexedBatch;
import com.riiablo.map.Map;
import com.riiablo.map.DT1;
import com.riiablo.map.Orientation;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Levels;
import com.riiablo.util.DebugUtils;

/**
 * 小地图管理器
 * 负责管理和渲染游戏的小地图/自动地图功能
 * 
 * 功能包括:
 * - 地图瓷砖渲染
 * - 特殊图标显示（传送点、神殿、任务物品等）
 * - 实体标记（玩家、怪物、NPC等）
 * - 迷雾/探索区域系统
 * - 名称显示
 * 
 * 参考: D2MOD D2AutomapCellStrc/D2AutomapLayerStrc, OpenDiablo2 AutoMapRecord
 */
public class AutomapManager implements Disposable {
  private static final String TAG = "AutomapManager";
  
  // ==================== 显示模式 ====================
  
  /** 关闭模式 */
  public static final int MODE_OFF = 0;
  
  /** 覆盖模式（半透明叠加在游戏画面上） */
  public static final int MODE_OVERLAY = 1;
  
  /** 全屏模式（完全覆盖游戏画面） */
  public static final int MODE_FULL = 2;
  
  /** 小地图模式（右上角小窗口） */
  public static final int MODE_MINIMAP = 3;
  
  /** 模式总数 */
  public static final int MODE_COUNT = 4;
  
  // ==================== 显示选项 ====================
  
  /** 是否显示队友 */
  public boolean showPartyMembers = true;
  
  /** 是否显示名称 */
  public boolean showNames = false;

  /** Whether distant quest/entrance destinations are compressed into pointers. */
  public boolean showMinimapPointers = true;
  private float pointerThreshold = AutomapMarkerPolicy.POINTER_THRESHOLD;
  private float pointerRadius = AutomapMarkerPolicy.POINTER_RADIUS;
  
  /** 是否居中显示 */
  public boolean centered = true;
  
  /** 透明度 (0.0 - 1.0) */
  public float opacity = 0.7f;
  
  /** 缩放比例 */
  public float scale = 1.0f;
  
  // ==================== 颜色定义 ====================
  
  /** 墙壁/障碍物颜色 */
  public static final Color COLOR_WALL = new Color(0.56f, 0.38f, 0.25f, 1.0f);
  
  /** 地板/可行走区域颜色 */
  public static final Color COLOR_FLOOR = new Color(0.25f, 0.19f, 0.13f, 0.5f);
  
  /** 门颜色 */
  public static final Color COLOR_DOOR = new Color(0.8f, 0.6f, 0.2f, 1.0f);
  
  /** Native player Automap cross - light blue. */
  public static final Color COLOR_PLAYER = new Color(0.5f, 0.75f, 1.0f, 1.0f);
  
  /** 队友颜色 - 浅绿色 */
  public static final Color COLOR_PARTY = new Color(0.5f, 1.0f, 0.5f, 1.0f);
  
  /** 怪物颜色 - 红色 */
  public static final Color COLOR_MONSTER = new Color(1.0f, 0.0f, 0.0f, 1.0f);

  public static final Color COLOR_CORPSE = new Color(0.55f, 0.55f, 0.55f, 1.0f);
  public static final Color COLOR_MISSILE = new Color(1.0f, 0.35f, 0.1f, 1.0f);
  public static final Color COLOR_ITEM = new Color(1.0f, 1.0f, 1.0f, 1.0f);
  public static final Color COLOR_CHAMPION = new Color(0.25f, 0.55f, 1.0f, 1.0f);
  public static final Color COLOR_UNIQUE = new Color(1.0f, 0.72f, 0.12f, 1.0f);
  public static final Color COLOR_MINION = new Color(1.0f, 0.45f, 0.2f, 1.0f);
  public static final Color COLOR_BOSS = new Color(0.85f, 0.05f, 0.85f, 1.0f);
  public static final Color COLOR_QUEST = new Color(0.2f, 1.0f, 0.9f, 1.0f);
  public static final Color COLOR_ENTRANCE = new Color(0.65f, 0.35f, 1.0f, 1.0f);
  
  /** Native NPC Automap cross - white. */
  public static final Color COLOR_NPC = new Color(1.0f, 1.0f, 1.0f, 1.0f);
  
  /** 佣兵颜色 - 青色 */
  public static final Color COLOR_MERCENARY = new Color(0.0f, 1.0f, 1.0f, 1.0f);
  
  /** 传送点颜色 - 紫色 */
  public static final Color COLOR_WAYPOINT = new Color(0.7f, 0.3f, 1.0f, 1.0f);
  
  /** 神殿颜色 - 蓝色 */
  public static final Color COLOR_SHRINE = new Color(0.3f, 0.5f, 1.0f, 1.0f);
  
  /** 传送门颜色 - 橙色 */
  public static final Color COLOR_PORTAL = new Color(1.0f, 0.5f, 0.0f, 1.0f);
  
  /** 未探索区域颜色 - 黑色 */
  public static final Color COLOR_UNEXPLORED = new Color(0.0f, 0.0f, 0.0f, 0.9f);
  
  // ==================== 内部状态 ====================
  
  /** 当前显示模式 */
  private int currentMode = MODE_OFF;
  
  /** 各关卡的小地图图层 */
  private final IntMap<AutomapLayer> layers = new IntMap<>();
  /** Native per-layer metadata retained when an existing .ma file is loaded. */
  private final IntMap<Integer> nativeLayerUnknown = new IntMap<>();
  private final IntMap<Boolean> nativeCellsBuilt = new IntMap<>();
  
  /** 当前激活的图层ID */
  private int activeLayerId = -1;
  
  /** AutoMap.txt 数据（可选，用于高级渲染） */
  private AutoMap automapData;
  
  /** 小地图图标精灵（MaxiMap(s).dc6） */
  private DC6 iconSprite;
  
  /** 小地图瓷砖渲染器 */
  private AutomapTileRenderer tileRenderer;

  private int nativeTerrainDrawCount;
  private String lastNativeRenderDiagnostic;
  private int nativeEntityDrawCount;
  private int geometricFallbackDrawCount;
  private final IntMap<Boolean> renderedNativeLayers = new IntMap<>();
  /** Marker instances whose native DC6 cell was actually rendered this frame. */
  private final Array<EntityMarker> nativeRenderedMarkers = new Array<>();
  /** Marker instances replaced by an external d2hackmap blob icon this frame. */
  private final Array<EntityMarker> hackMapRenderedMarkers = new Array<>();
  private final HackMapIconRenderer hackMapIcons = new HackMapIconRenderer();
  
  /** 小地图偏移 */
  private float offsetX = 0;
  private float offsetY = 0;
  
  /** 临时向量 */
  private final Vector2 tmpVec = new Vector2();
  private final Vector2 pointerSource = new Vector2();
  private final Vector2 pointerTarget = new Vector2();
  
  // ==================== 实体标记 ====================
  
  /** 待渲染的实体标记列表 */
  private final Array<EntityMarker> entityMarkers = new Array<>();
  
  /**
   * 实体标记数据
   */
  public static class EntityMarker {
    public int entityId;
    public int type;
    public float worldX;
    public float worldY;
    public String name;
    public Color color;
    public float size;
    /** Native MaxiMap.dc6 frame; -1 means use geometric fallback. */
    public int nativeCell = -1;
    
    public EntityMarker() {}
    
    public EntityMarker set(int entityId, int type, float worldX, float worldY, 
                           String name, Color color, float size) {
      this.entityId = entityId;
      this.type = type;
      this.worldX = worldX;
      this.worldY = worldY;
      this.name = name;
      this.color = color;
      this.size = size;
      this.nativeCell = -1;
      return this;
    }
  }
  
  // ==================== 构造与初始化 ====================
  
  public AutomapManager() {
    // 初始化默认设置
    tileRenderer = new AutomapTileRenderer();
  }
  
  /**
   * 初始化渲染器
   */
  public void init() {
    tileRenderer.init();
  }
  
  /**
   * 获取瓷砖渲染器
   */
  public AutomapTileRenderer getTileRenderer() {
    return tileRenderer;
  }

  /** Enables external d2hackmap blobs only when a compatible Plugin directory exists. */
  public void setHackMapEnabled(boolean enabled, com.badlogic.gdx.files.FileHandle d2Home) {
    hackMapIcons.setEnabled(enabled, d2Home);
  }

  public boolean isHackMapEnabled() {
    return hackMapIcons.isActive();
  }

  public int getHackMapIconCount() {
    return hackMapIcons.getLoadedIconCount();
  }
  
  /**
   * 加载 AutoMap.txt 数据
   */
  public void loadAutomapData(AutoMap data) {
    this.automapData = data;
    if (data != null) {
      Gdx.app.log(TAG, "Loaded " + data.size() + " automap records");
      tileRenderer.loadAutomapData(data);
    }
  }
  
  /**
   * 加载小地图图标精灵
   */
  public void loadIconSprite(DC6 sprite) {
    this.iconSprite = sprite;
    if (sprite != null) {
      Gdx.app.log(TAG, "Loaded automap icon sprite");
    }
  }
  
  // ==================== 模式控制 ====================
  
  /**
   * 获取当前显示模式
   */
  public int getMode() {
    return currentMode;
  }
  
  /**
   * 设置显示模式
   */
  public void setMode(int mode) {
    if (mode < 0 || mode >= MODE_COUNT) {
      mode = MODE_OFF;
    }
    this.currentMode = mode;
  }
  
  /**
   * 切换到下一个显示模式
   * 循环: OFF -> OVERLAY -> FULL -> MINIMAP -> OFF
   */
  public void toggleMode() {
    currentMode = (currentMode + 1) % MODE_COUNT;
  }
  
  /**
   * 检查小地图是否可见
   */
  public boolean isVisible() {
    return currentMode != MODE_OFF;
  }
  
  // ==================== 图层管理 ====================
  
  /**
   * 获取或创建指定关卡的图层
   */
  public AutomapLayer getOrCreateLayer(int levelId) {
    AutomapLayer layer = layers.get(levelId);
    if (layer == null) {
      layer = new AutomapLayer(levelId);
      layers.put(levelId, layer);
    }
    return layer;
  }
  
  /**
   * 获取指定关卡的图层
   */
  public AutomapLayer getLayer(int levelId) {
    return layers.get(levelId);
  }
  
  /**
   * 设置当前激活的图层
   */
  public void setActiveLayer(int levelId) {
    this.activeLayerId = levelId;
    // 确保图层存在
    getOrCreateLayer(levelId);
  }
  
  /**
   * 获取当前激活的图层
   */
  public AutomapLayer getActiveLayer() {
    if (activeLayerId < 0) return null;
    return layers.get(activeLayerId);
  }
  
  // ==================== 探索更新 ====================
  
  /**
   * 更新玩家位置，刷新探索区域
   * 
   * @param levelId 当前关卡ID
   * @param playerX 玩家X坐标（子瓷砖）
   * @param playerY 玩家Y坐标（子瓷砖）
   */
  public void updatePlayerPosition(int levelId, int playerX, int playerY) {
    if (activeLayerId != levelId) {
      setActiveLayer(levelId);
    }
    
    AutomapLayer layer = getActiveLayer();
    if (layer != null) {
      layer.updateExploration(playerX, playerY);
    }
  }

  /** Restores explored cells from a native difficulty-specific .ma file. */
  public void loadNativeAutomap(AutomapExplorationStore.MaFile file) {
    if (file == null || Riiablo.files == null || Riiablo.files.Levels == null) return;
    for (Levels.Entry level : Riiablo.files.Levels) {
      int index = level.Layer - 1;
      if (index < 0 || index >= file.layers.length || file.layers[index] == null) continue;
      AutomapExplorationStore.Layer nativeLayer = file.layers[index];
      nativeLayerUnknown.put(level.Id, nativeLayer.unknown);
      AutomapLayer layer = getOrCreateLayer(level.Id);
      restoreNativeCells(layer, nativeLayer.floors);
      restoreNativeCells(layer, nativeLayer.walls);
      restoreNativeCells(layer, nativeLayer.objects);
      restoreNativeCells(layer, nativeLayer.extras);
    }
  }

  /** Converts current runtime cells to the native .ma representation. */
  public AutomapExplorationStore.MaFile createNativeAutomap() {
    return createNativeAutomap(null);
  }

  /**
   * Merges generated runtime layers into an existing native file. Layers that
   * were not generated in this session must be retained byte-for-byte at the
   * cell-record level instead of being erased on exit.
   */
  public AutomapExplorationStore.MaFile createNativeAutomap(
      AutomapExplorationStore.MaFile existing) {
    AutomapExplorationStore.MaFile result = copyNativeAutomap(existing);
    if (Riiablo.files == null || Riiablo.files.Levels == null) return result;
    for (IntMap.Entry<Boolean> built : nativeCellsBuilt) {
      if (!Boolean.TRUE.equals(built.value)) continue;
      AutomapLayer source = layers.get(built.key);
      if (source == null) continue;
      Levels.Entry level = Riiablo.files.Levels.get(built.key);
      if (level == null || level.Layer < 1 || level.Layer > result.layers.length) continue;
      AutomapExplorationStore.Layer nativeLayer = result.layers[level.Layer - 1];
      if (nativeLayer == null) nativeLayer = new AutomapExplorationStore.Layer();
      Integer unknown = nativeLayerUnknown.get(built.key);
      if (unknown != null) nativeLayer.unknown = unknown;
      copyNativeCells(source.floors, source, nativeLayer.floors);
      copyNativeCells(source.roads, source, nativeLayer.floors);
      copyNativeCells(source.walls, source, nativeLayer.walls);
      copyNativeCells(source.objects, source, nativeLayer.objects);
      copyNativeCells(source.extras, source, nativeLayer.extras);
      result.layers[level.Layer - 1] = nativeLayer;
    }
    return result;
  }

  private static AutomapExplorationStore.MaFile copyNativeAutomap(
      AutomapExplorationStore.MaFile source) {
    AutomapExplorationStore.MaFile result = new AutomapExplorationStore.MaFile();
    if (source == null) return result;
    for (int i = 0; i < source.layers.length; i++) {
      AutomapExplorationStore.Layer layer = source.layers[i];
      if (layer == null) continue;
      AutomapExplorationStore.Layer copy = new AutomapExplorationStore.Layer();
      copy.unknown = layer.unknown;
      copy.floors.addAll(layer.floors);
      copy.walls.addAll(layer.walls);
      copy.objects.addAll(layer.objects);
      copy.extras.addAll(layer.extras);
      result.layers[i] = copy;
    }
    return result;
  }

  private static void restoreNativeCells(AutomapLayer target,
      Array<AutomapExplorationStore.Cell> cells) {
    for (AutomapExplorationStore.Cell cell : cells) {
      int tx = cell.x / 8;
      int ty = cell.y / 4;
      int sum = tx + ty;
      int diff = tx - ty;
      if (((sum + diff) & 1) != 0) continue;
      int worldX = ((sum + diff) / 2) * DT1.Tile.SUBTILE_SIZE + DT1.Tile.SUBTILE_SIZE / 2;
      int worldY = ((sum - diff) / 2) * DT1.Tile.SUBTILE_SIZE + DT1.Tile.SUBTILE_SIZE / 2;
      target.revealRect(worldX, worldY, 1, 1);
    }
  }

  private static void copyNativeCells(Array<AutomapCell> source, AutomapLayer layer,
      Array<AutomapExplorationStore.Cell> target) {
    for (AutomapCell cell : source) {
      if (!layer.isExplored(cell.xPixel, cell.yPixel)) continue;
      int tx = Math.floorDiv(cell.xPixel, DT1.Tile.SUBTILE_SIZE);
      int ty = Math.floorDiv(cell.yPixel, DT1.Tile.SUBTILE_SIZE);
      addNativeCell(target, cell.cellNo, (short) (8 * (tx - ty)),
          (short) (4 * (tx + ty)));
    }
  }

  private static void addNativeCell(Array<AutomapExplorationStore.Cell> target, int cellNo,
      short x, short y) {
    for (int i = 0, n = target.size; i < n; i++) {
      AutomapExplorationStore.Cell existing = target.get(i);
      if (existing.cellNo == cellNo && existing.x == x && existing.y == y) return;
    }
    target.add(new AutomapExplorationStore.Cell(cellNo, x, y));
  }

  /** Updates exploration from native RoomEx activation state when available. */
  public void updatePlayerPosition(int levelId, int playerX, int playerY, Map map) {
    if (activeLayerId != levelId) setActiveLayer(levelId);
    AutomapLayer layer = getActiveLayer();
    if (layer == null || map == null) {
      if (layer != null) layer.updateExploration(playerX, playerY);
      return;
    }
    Map.Zone zone = map.getZone(playerX, playerY);
    // Native D2 towns are fully revealed from the moment the level opens;
    // exploration fog only applies to outdoor/dungeon levels.  Reveal the
    // whole town footprint before building cells so its NPCs, waypoints and
    // exits are visible immediately.
    if (zone != null && zone.isTown()) {
      layer.revealRect(zone.x(), zone.y(), zone.width(), zone.height());
    } else {
      layer.updateRoomExploration(zone, playerX, playerY);
    }
    if (zone != null && !nativeCellsBuilt.containsKey(levelId)) {
      String nativeName = zone.automapLevelName();
      if (nativeName != null) {
        rebuildNativeCells(zone, nativeName, levelId);
        nativeCellsBuilt.put(levelId, Boolean.TRUE);
      }
    }
  }

  /**
   * Builds native DC6-backed cells from a generated Zone.  Coordinates stored
   * in AutomapCell are world subtiles; the renderer's automap camera projects
   * them just like entity markers.  Call once after a zone is generated (or
   * again after changing level/seed).
   */
  public int rebuildNativeCells(Map.Zone zone, String automapLevelName, long automapSeed) {
    if (zone == null || automapLevelName == null || tileRenderer == null) return 0;
    AutomapLayer layer = getOrCreateLayer(zone.levelId());
    layer.clearCells();
    // The native outdoor automap is line/road based.  Rendering every DT1
    // orientation-0 tile paints the entire wilderness (and town) as a solid
    // floor, unlike D2 where only roads, boundaries, and markers are shown.
    // Keep floor cells available for indoor maps, but suppress them outdoors.
    layer.renderFloorCells = zone.level != null && zone.level.IsInside;
    int added = 0;
    int tileCount = 0;
    int lookupMisses = 0;
    String firstTile = null;
    String firstTileName = null;
    int firstTileStyle = 0;
    int firstTileSequence = 0;
    int minTx = AutomapProjection.tileIndex(zone.x());
    int minTy = AutomapProjection.tileIndex(zone.y());
    int maxTx = AutomapProjection.tileEndExclusive(zone.x() + zone.width());
    int maxTy = AutomapProjection.tileEndExclusive(zone.y() + zone.height());
    for (int ty = minTy; ty < maxTy; ty++) {
      for (int tx = minTx; tx < maxTx; tx++) {
        int worldX = tx * DT1.Tile.SUBTILE_SIZE + DT1.Tile.SUBTILE_SIZE / 2;
        int worldY = ty * DT1.Tile.SUBTILE_SIZE + DT1.Tile.SUBTILE_SIZE / 2;
        for (int l = 0; l < Map.MAX_LAYERS; l++) {
          DT1.Tile tile = zone.get(l, tx, ty);
          if (tile == null) continue;
          tileCount++;
          String tileName = AutomapTileRenderer.tileNameForOrientation(tile.orientation);
          if (tileName == null) continue;
          if (firstTile == null) {
            firstTile = tileName + "/" + tile.mainIndex + "/" + tile.subIndex
                + " orientation=" + tile.orientation;
            firstTileName = tileName;
            firstTileStyle = tile.mainIndex;
            firstTileSequence = tile.subIndex;
          }
          long cellSeed = automapSeed ^ (worldX * 31L + worldY);
          int cell = tileRenderer.getAutomapCellId(automapLevelName, tileName,
              tile.mainIndex, tile.subIndex, cellSeed);
          if (cell < 0) {
            lookupMisses++;
            continue;
          }
          if (Orientation.isFloor(tile.orientation)) {
            if (layer.renderFloorCells) {
              layer.addFloor(cell, worldX, worldY);
            } else {
              // Outdoor TileGrid coordinates are local to the zone.  Only
              // cells marked by the native dirt-path topology are rendered;
              // generic wilderness floor cells remain invisible.
              com.riiablo.drlg.TileGrid grid = zone.nativeTileGrid();
              int localTx = tx - Math.floorDiv(zone.x(), com.riiablo.map.DT1.Tile.SUBTILE_SIZE);
              int localTy = ty - Math.floorDiv(zone.y(), com.riiablo.map.DT1.Tile.SUBTILE_SIZE);
              if (grid != null && grid.inBounds(localTx, localTy)
                  && grid.dirtPathFlags[localTy][localTx]) {
                layer.addRoad(cell, worldX, worldY);
              } else {
                // The broad any-style compatibility fallback is useful for
                // incomplete Jungle/Kurast tables, but on Act I floor style 0
                // it turns ordinary grass into a path. Only an exact native
                // AutoMap.txt match may contribute a non-road outdoor floor,
                // and cells 0..3 remain reserved for DirtPathGrid roads.
                int exactCell = tileRenderer.getExactAutomapCellId(automapLevelName,
                    tileName, tile.mainIndex, tile.subIndex, cellSeed);
                if (AutomapTileRenderer.isOutdoorFloorFeature(exactCell)) {
                  layer.addFloor(exactCell, worldX, worldY);
                }
              }
            }
          }
          else if (Orientation.isWall(tile.orientation)) layer.addWall(cell, worldX, worldY);
          else layer.addObject(cell, worldX, worldY);
          added++;
        }
      }
    }
    if (Gdx.app != null) {
      Gdx.app.log(TAG, String.format(
          "[AUTOMAP_NATIVE] level=%d name=%s tiles=%d cells=%d lookupMisses=%d first=%s",
          zone.levelId(), automapLevelName, tileCount, added, lookupMisses,
          firstTile == null ? "none" : firstTile));
      if (added == 0 && firstTile != null) {
        Gdx.app.log(TAG, "[AUTOMAP_LOOKUP] "
            + tileRenderer.diagnostic(automapLevelName, firstTileName,
                firstTileStyle, firstTileSequence));
      }
    }
    return added;
  }
  
  // ==================== 实体标记 ====================
  
  /**
   * 清除所有实体标记
   */
  public void clearEntityMarkers() {
    entityMarkers.clear();
    nativeRenderedMarkers.clear();
    hackMapRenderedMarkers.clear();
    nativeEntityDrawCount = 0;
    geometricFallbackDrawCount = 0;
  }

  public int getNativeTerrainDrawCount() { return nativeTerrainDrawCount; }
  public int getNativeEntityDrawCount() { return nativeEntityDrawCount; }
  public int getGeometricFallbackDrawCount() { return geometricFallbackDrawCount; }

  /** Keeps compressed destinations inside the current full/mini Automap viewport. */
  public void setPointerRadius(float radius) {
    pointerRadius = Math.max(16f, radius);
    pointerThreshold = pointerRadius * 1.25f;
  }

  /** Number of markers currently collected for the active Automap frame. */
  public int getEntityMarkerCount() {
    return entityMarkers.size;
  }

  /** Returns a marker for diagnostics/tests; callers must not mutate it. */
  public EntityMarker getEntityMarker(int index) {
    return entityMarkers.get(index);
  }

  /**
   * Returns true when terrain already supplies the same native cell close to
   * an entity anchor. Native D2 represents waypoints and the town stash in
   * the generated Automap cells, so drawing their runtime object cell again
   * produces a visible double image.
   */
  public boolean hasNearbyTerrainCell(int levelId, int cellNo, float worldX, float worldY,
      float maxAutomapDistance) {
    if (cellNo < 0 || maxAutomapDistance < 0f) return false;
    AutomapLayer layer = layers.get(levelId);
    if (layer == null) return false;
    float maxDistance2 = maxAutomapDistance * maxAutomapDistance;
    return hasNearbyTerrainCell(layer.floors, cellNo, worldX, worldY, maxDistance2)
        || hasNearbyTerrainCell(layer.roads, cellNo, worldX, worldY, maxDistance2)
        || hasNearbyTerrainCell(layer.walls, cellNo, worldX, worldY, maxDistance2)
        || hasNearbyTerrainCell(layer.objects, cellNo, worldX, worldY, maxDistance2)
        || hasNearbyTerrainCell(layer.extras, cellNo, worldX, worldY, maxDistance2);
  }

  private static boolean hasNearbyTerrainCell(Array<AutomapCell> cells, int cellNo,
      float worldX, float worldY, float maxDistance2) {
    for (int i = 0, size = cells.size; i < size; i++) {
      AutomapCell cell = cells.get(i);
      if (cell.cellNo != cellNo) continue;
      float dx = ((cell.xPixel - worldX) - (cell.yPixel - worldY))
          * AutomapProjection.PIXELS_PER_SUBTILE_X;
      float dy = -((cell.xPixel - worldX) + (cell.yPixel - worldY))
          * AutomapProjection.PIXELS_PER_SUBTILE_Y;
      if (dx * dx + dy * dy <= maxDistance2) return true;
    }
    return false;
  }
  
  /**
   * 添加实体标记
   */
  public void addEntityMarker(int entityId, int type, float worldX, float worldY,
                              String name, Color color, float size) {
    // A network/entity refresh may first add a geometric marker and then add
    // its native DC6 marker. Update the existing entry instead of drawing two
    // markers for the same entity.
    if (entityId >= 0) {
      for (int i = 0, n = entityMarkers.size; i < n; i++) {
        EntityMarker existing = entityMarkers.get(i);
        if (existing.entityId == entityId) {
          int nativeCell = existing.nativeCell;
          existing.set(entityId, type, worldX, worldY, name, color, size);
          existing.nativeCell = nativeCell;
          return;
        }
      }
    }
    EntityMarker marker = new EntityMarker();
    marker.set(entityId, type, worldX, worldY, name, color, size);
    entityMarkers.add(marker);
  }

  /** Adds an entity marker with a native MaxiMap.dc6 frame. */
  public void addNativeEntityMarker(int entityId, int type, float worldX, float worldY,
                                    String name, Color color, float size, int nativeCell) {
    addEntityMarker(entityId, type, worldX, worldY, name, color, size);
    // addEntityMarker updates an existing id in place; peek() could assign the
    // cell to an unrelated marker when duplicate refreshes are received.
    for (int i = 0, n = entityMarkers.size; i < n; i++) {
      EntityMarker marker = entityMarkers.get(i);
      if (marker.entityId == entityId) {
        marker.nativeCell = nativeCell;
        return;
      }
    }
  }
  
  /**
   * 添加玩家标记
   */
  public void addPlayerMarker(int entityId, float worldX, float worldY, String name) {
    addEntityMarker(entityId, AutomapIconType.PLAYER, worldX, worldY, 
                   name, COLOR_PLAYER, 8);
  }
  
  /**
   * 添加队友标记
   */
  public void addPartyMarker(int entityId, float worldX, float worldY, String name) {
    if (showPartyMembers) {
      addEntityMarker(entityId, AutomapIconType.PARTY_MEMBER, worldX, worldY,
                     name, COLOR_PARTY, 6);
    }
  }
  
  /**
   * 添加怪物标记
   */
  public void addMonsterMarker(int entityId, float worldX, float worldY, String name) {
    addEntityMarker(entityId, AutomapIconType.MONSTER, worldX, worldY,
                   name, COLOR_MONSTER, 4);
  }
  
  /**
   * 添加NPC标记
   */
  public void addNpcMarker(int entityId, float worldX, float worldY, String name) {
    addEntityMarker(entityId, AutomapIconType.NPC, worldX, worldY,
                   name, COLOR_NPC, 5);
  }
  
  /**
   * 添加传送点标记
   */
  public void addWaypointMarker(float worldX, float worldY) {
    addEntityMarker(-1, AutomapIconType.WAYPOINT, worldX, worldY,
                   null, COLOR_WAYPOINT, 6);
  }
  
  /**
   * 添加神殿标记
   */
  public void addShrineMarker(float worldX, float worldY) {
    addEntityMarker(-1, AutomapIconType.SHRINE, worldX, worldY,
                   null, COLOR_SHRINE, 5);
  }
  
  /**
   * 添加传送门标记
   */
  public void addPortalMarker(int entityId, float worldX, float worldY, String owner) {
    addEntityMarker(entityId, AutomapIconType.PORTAL, worldX, worldY,
                   owner, COLOR_PORTAL, 5);
  }
  
  // ==================== 渲染 ====================
  
  /**
   * 渲染小地图（使用 ShapeRenderer）
   * 
   * @param shapes ShapeRenderer 实例
   * @param map 地图实例
   * @param viewStartX 视图起始X（瓷砖坐标）
   * @param viewStartY 视图起始Y（瓷砖坐标）
   * @param viewWidth 视图宽度（瓷砖数）
   * @param viewHeight 视图高度（瓷砖数）
   * @param screenCenterX 屏幕中心X
   * @param screenCenterY 屏幕中心Y
   */
  public void render(ShapeRenderer shapes, Map map, 
                    int viewStartX, int viewStartY, int viewWidth, int viewHeight,
                    float screenCenterX, float screenCenterY) {
    if (!isVisible()) return;
    
    AutomapLayer layer = getActiveLayer();
    float alpha = opacity;
    
    // 根据模式调整透明度
    if (currentMode == MODE_OVERLAY) {
      alpha *= 0.6f;
    } else if (currentMode == MODE_MINIMAP) {
      alpha *= 0.8f;
    }
    
    ShapeRenderer.ShapeType prevType = shapes.getCurrentType();
    shapes.set(ShapeRenderer.ShapeType.Filled);
    
    // 渲染实体标记
    renderEntityMarkers(shapes, alpha);
    
    shapes.set(prevType);
  }
  
  /**
   * 渲染实体标记
   */
  private void renderEntityMarkers(ShapeRenderer shapes, float alpha) {
    boolean hasPointerSource = false;
    for (int i = 0, size = entityMarkers.size; i < size; i++) {
      EntityMarker marker = entityMarkers.get(i);
      if (marker.type == AutomapIconType.PLAYER) {
        AutomapProjection.worldToAutomap(marker.worldX, marker.worldY, pointerSource);
        hasPointerSource = true;
        break;
      }
    }
    for (int i = 0, size = entityMarkers.size; i < size; i++) {
      EntityMarker marker = entityMarkers.get(i);
      AutomapProjection.worldToAutomap(marker.worldX, marker.worldY, tmpVec);
      boolean distantPointer = AutomapMarkerPolicy.isPointerTarget(marker.type)
          && AutomapMarkerPolicy.projectPointer(hasPointerSource ? pointerSource : null,
              tmpVec, showMinimapPointers, pointerThreshold, pointerRadius, pointerTarget);
      // HackMap blobs already encode both the marker shape and its rank color.
      if (hackMapRenderedMarkers.contains(marker, true) && !distantPointer) continue;
      // Only suppress the geometric fallback after a native cell was
      // successfully drawn.  A valid-looking frame can still be absent from
      // a reduced MaxiMap.dc6 export; in that case the player/NPC must remain
      // visible through the fallback marker.
      if (nativeRenderedMarkers.contains(marker, true) && !distantPointer
          && !AutomapMarkerPolicy.requiresColorOverlay(marker.type)) continue;
      float markerX = distantPointer ? pointerTarget.x : tmpVec.x;
      float markerY = distantPointer ? pointerTarget.y : tmpVec.y;
      
      Color color = marker.color;
      shapes.setColor(color.r, color.g, color.b, alpha);
      
      // 根据类型绘制不同形状
      switch (marker.type) {
        case AutomapIconType.PLAYER:
          drawNativeCross(shapes, markerX, markerY);
          break;
          
        case AutomapIconType.PARTY_MEMBER:
          // 队友用较小的圆点
          shapes.circle(markerX, markerY, marker.size);
          break;
          
        case AutomapIconType.MONSTER:
        case AutomapIconType.CHAMPION:
        case AutomapIconType.UNIQUE:
        case AutomapIconType.MINION:
        case AutomapIconType.BOSS:
          // 怪物用小方块
          float halfSize = marker.size / 2;
          shapes.rect(markerX - halfSize, markerY - halfSize,
                     marker.size, marker.size);
          break;

        case AutomapIconType.CORPSE:
          shapes.line(markerX - marker.size, markerY - marker.size,
              markerX + marker.size, markerY + marker.size);
          shapes.line(markerX - marker.size, markerY + marker.size,
              markerX + marker.size, markerY - marker.size);
          break;

        case AutomapIconType.MISSILE:
          shapes.triangle(markerX, markerY + marker.size,
              markerX - marker.size, markerY - marker.size,
              markerX + marker.size, markerY - marker.size);
          break;

        case AutomapIconType.ITEM:
          DebugUtils.drawDiamond(shapes, markerX, markerY,
              (int) (marker.size * 2), (int) marker.size);
          break;

        case AutomapIconType.QUEST:
        case AutomapIconType.ENTRANCE:
        case AutomapIconType.EXIT:
          DebugUtils.drawDiamond(shapes, markerX, markerY,
              (int) (marker.size * 2), (int) marker.size);
          if (distantPointer) {
            float dx = markerX - pointerSource.x;
            float dy = markerY - pointerSource.y;
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length > 0f) {
              dx /= length;
              dy /= length;
              shapes.rectLine(markerX - dx * 18f, markerY - dy * 18f,
                  markerX - dx * 5f, markerY - dy * 5f, 2f);
            }
          }
          break;
          
        case AutomapIconType.NPC:
          drawNativeCross(shapes, markerX, markerY);
          break;
          
        case AutomapIconType.WAYPOINT:
          // 传送点用菱形
          DebugUtils.drawDiamond(shapes, markerX, markerY,
                                (int)(marker.size * 2), (int)marker.size);
          break;
          
        case AutomapIconType.SHRINE:
          // 神殿用三角形
          shapes.triangle(
            markerX, markerY + marker.size,
            markerX - marker.size, markerY - marker.size,
            markerX + marker.size, markerY - marker.size
          );
          break;
          
        case AutomapIconType.PORTAL:
          // 传送门用圆环
          shapes.circle(markerX, markerY, marker.size);
          shapes.setColor(0, 0, 0, alpha * 0.5f);
          shapes.circle(markerX, markerY, marker.size * 0.5f);
          break;
          
        default:
          // 默认用圆点
          shapes.circle(markerX, markerY, marker.size);
          break;
      }
    }
  }
  
  /**
   * 渲染实体名称（需要 SpriteBatch）
   */
  public void renderNames(SpriteBatch batch, BitmapFont font) {
    if (!isVisible() || !showNames) return;
    Color previousColor = font.getColor();
    float previousR = previousColor.r;
    float previousG = previousColor.g;
    float previousB = previousColor.b;
    float previousA = previousColor.a;
    for (int i = 0, size = entityMarkers.size; i < size; i++) {
      EntityMarker marker = entityMarkers.get(i);
      if (!AutomapMarkerPolicy.shouldDisplayName(marker.type)) continue;
      if (marker.name == null || marker.name.isEmpty()) continue;
      AutomapProjection.worldToAutomap(marker.worldX, marker.worldY, tmpVec);
      font.setColor(marker.color.r, marker.color.g, marker.color.b, opacity);
      font.draw(batch, marker.name, tmpVec.x, tmpVec.y + marker.size + 12);
    }
    font.setColor(previousR, previousG, previousB, previousA);
  }

  /** D2CLIENT draws the four-armed player/NPC glyph as vectors. */
  private static void drawNativeCross(ShapeRenderer shapes, float x, float y) {
    final float armX = 6f;
    final float armY = 3f;
    final float hookX = 2f;
    final float hookY = 2f;
    // Each diagonal arm ends in a short fork, producing the curled X-shaped
    // marker visible in the original renderer rather than a plain plus sign.
    shapes.line(x, y, x + armX, y + armY);
    shapes.line(x, y, x - armX, y + armY);
    shapes.line(x, y, x + armX, y - armY);
    shapes.line(x, y, x - armX, y - armY);
    shapes.line(x + armX, y + armY, x + armX - hookX, y + armY + hookY);
    shapes.line(x + armX, y + armY, x + armX + hookX, y + armY - hookY);
    shapes.line(x - armX, y + armY, x - armX + hookX, y + armY + hookY);
    shapes.line(x - armX, y + armY, x - armX - hookX, y + armY - hookY);
    shapes.line(x + armX, y - armY, x + armX - hookX, y - armY - hookY);
    shapes.line(x + armX, y - armY, x + armX + hookX, y - armY + hookY);
    shapes.line(x - armX, y - armY, x - armX + hookX, y - armY - hookY);
    shapes.line(x - armX, y - armY, x - armX - hookX, y - armY + hookY);
  }
  
  // ==================== DC6 精灵渲染 ====================
  
  /**
   * 使用 DC6 精灵渲染小地图瓷砖
   * 这是更接近原版 D2 的渲染方式
   * 
   * @param batch 调色板索引批处理器
   * @param map 地图实例
   * @param viewStartX 视图起始X（瓷砖坐标）
   * @param viewStartY 视图起始Y（瓷砖坐标）
   * @param viewWidth 视图宽度（瓷砖数）
   * @param viewHeight 视图高度（瓷砖数）
   * @param screenCenterX 屏幕中心X
   * @param screenCenterY 屏幕中心Y
   */
  public int renderWithSprites(PaletteIndexedBatch batch, Map map,
                                int viewStartX, int viewStartY, 
                                int viewWidth, int viewHeight,
                                float screenCenterX, float screenCenterY) {
    nativeTerrainDrawCount = 0;
    if (!isVisible() || !tileRenderer.hasSprite()) return 0;
    
    float alpha = opacity;
    
    // 设置透明度
    batch.setColor(1f, 1f, 1f, alpha);

    // Draw every generated zone belonging to the current map. Town and its
    // adjacent wilderness are separate native layers, but D2 keeps both at
    // the same brightness while crossing their boundary.
    if (map != null) {
      renderedNativeLayers.clear();
      StringBuilder diagnostic = new StringBuilder(160);
      diagnostic.append("alpha=").append(alpha).append(" zones=");
      for (Map.Zone zone : map.getZones()) {
        int levelId = zone.levelId();
        if (renderedNativeLayers.containsKey(levelId)
            || !nativeCellsBuilt.containsKey(levelId)) continue;
        renderedNativeLayers.put(levelId, Boolean.TRUE);
        AutomapLayer layer = layers.get(levelId);
        diagnostic.append(levelId).append('(')
            .append(layer == null ? 0 : layer.floors.size).append('/')
            .append(layer == null ? 0 : layer.roads.size).append('/')
            .append(layer == null ? 0 : layer.walls.size).append('/')
            .append(layer == null ? 0 : layer.objects.size).append('/')
            .append(layer == null ? 0 : layer.extras.size).append(';')
            .append(layer == null ? 0 : layer.getExploredCount()).append(')');
        renderNativeLayer(batch, layers.get(levelId), alpha);
      }
      String diagnosticText = diagnostic.toString();
      if (!diagnosticText.equals(lastNativeRenderDiagnostic) && Gdx.app != null) {
        Gdx.app.debug(TAG, "[AUTOMAP_NATIVE_RENDER] " + diagnosticText);
        lastNativeRenderDiagnostic = diagnosticText;
      }
    } else {
      renderNativeLayer(batch, getActiveLayer(), alpha);
    }
    
    // 恢复颜色
    batch.setColor(1f, 1f, 1f, 1f);
    return nativeTerrainDrawCount;
  }

  private void renderNativeLayer(PaletteIndexedBatch batch, AutomapLayer layer, float alpha) {
    if (layer == null) return;
    // Outdoor floors contain only explicitly retained features (rivers and
    // bridges); indoor/town floors additionally contain their path cells.
    nativeTerrainDrawCount += renderNativeCells(batch, layer.floors, layer, alpha);
    nativeTerrainDrawCount += renderNativeCells(batch, layer.roads, layer, alpha);
    nativeTerrainDrawCount += renderNativeCells(batch, layer.walls, layer, alpha);
    nativeTerrainDrawCount += renderNativeCells(batch, layer.objects, layer, alpha);
    nativeTerrainDrawCount += renderNativeCells(batch, layer.extras, layer, alpha);
  }

  /**
   * Draws native entity cells in a separate SpriteBatch phase. The caller must
   * invoke this after ending ShapeRenderer and before beginning it again.
   */
  public int renderNativeEntitySprites(PaletteIndexedBatch batch, float alpha) {
    if (batch == null) return 0;
    int drawn = 0;
    batch.setColor(1f, 1f, 1f, alpha);
    for (int i = 0, size = entityMarkers.size; i < size; i++) {
      EntityMarker marker = entityMarkers.get(i);
      if (hackMapIcons.render(batch, marker, alpha)) {
        hackMapRenderedMarkers.add(marker);
        drawn++;
        continue;
      }
      if (!AutomapEntityCells.hasCell(marker.nativeCell)) {
        geometricFallbackDrawCount++;
        continue;
      }
      try {
        if (tileRenderer != null && tileRenderer.hasSprite()
            && renderProjectedTile(batch, marker.nativeCell, marker.worldX, marker.worldY)) {
          nativeRenderedMarkers.add(marker);
          drawn++;
        } else {
          geometricFallbackDrawCount++;
        }
      } catch (RuntimeException ignored) {
        // Invalid/missing DC6 frame falls back to the geometric marker.
        geometricFallbackDrawCount++;
      }
    }
    batch.setColor(1f, 1f, 1f, 1f);
    nativeEntityDrawCount = drawn;
    return drawn;
  }

  private int renderNativeCells(PaletteIndexedBatch batch, Array<AutomapCell> cells,
      AutomapLayer layer, float alpha) {
    int drawn = 0;
    for (int i = 0, size = cells.size; i < size; i++) {
      AutomapCell cell = cells.get(i);
      if (cell.cellNo >= 0 && layer.isExplored(cell.xPixel, cell.yPixel)) {
        if (renderProjectedTile(batch, cell.cellNo, cell.xPixel, cell.yPixel)) drawn++;
      }
    }
    return drawn;
  }

  /** Keeps exploration in world coordinates while drawing in isometric screen space. */
  private boolean renderProjectedTile(PaletteIndexedBatch batch, int cellNo,
                                      float worldX, float worldY) {
    AutomapProjection.worldToAutomap(worldX, worldY, tmpVec);
    return tileRenderer.renderTile(batch, cellNo, tmpVec.x, tmpVec.y);
  }
  
  /**
   * 渲染特殊图标（传送点、神殿等）
   * 
   * @param batch 调色板索引批处理器
   * @param iconType 图标类型
   * @param screenX 屏幕X坐标
   * @param screenY 屏幕Y坐标
   */
  public void renderSpecialIcon(PaletteIndexedBatch batch, int iconType, 
                                float screenX, float screenY) {
    if (!tileRenderer.hasSprite()) return;
    
    tileRenderer.renderIcon(batch, iconType, screenX, screenY);
  }
  
  /**
   * 检查是否可以使用精灵渲染
   */
  public boolean canUseSprites() {
    return tileRenderer != null && tileRenderer.hasSprite();
  }
  
  // ==================== 偏移控制 ====================
  
  /**
   * 设置小地图偏移
   */
  public void setOffset(float x, float y) {
    this.offsetX = x;
    this.offsetY = y;
  }
  
  /**
   * 移动小地图
   */
  public void pan(float dx, float dy) {
    this.offsetX += dx;
    this.offsetY += dy;
  }
  
  /**
   * 重置偏移到中心
   */
  public void centerOnPlayer() {
    this.offsetX = 0;
    this.offsetY = 0;
  }
  
  // ==================== 缩放控制 ====================
  
  /**
   * 设置缩放比例
   */
  public void setScale(float scale) {
    this.scale = Math.max(0.5f, Math.min(2.0f, scale));
  }
  
  /**
   * 放大
   */
  public void zoomIn() {
    setScale(scale * 1.1f);
  }
  
  /**
   * 缩小
   */
  public void zoomOut() {
    setScale(scale / 1.1f);
  }
  
  // ==================== 资源清理 ====================
  
  @Override
  public void dispose() {
    layers.clear();
    nativeLayerUnknown.clear();
    nativeCellsBuilt.clear();
    renderedNativeLayers.clear();
    entityMarkers.clear();
    automapData = null;
    iconSprite = null;
    hackMapIcons.dispose();
    if (tileRenderer != null) {
      tileRenderer.dispose();
      tileRenderer = null;
    }
  }
  
  /**
   * 清除指定关卡的图层数据
   */
  public void clearLayer(int levelId) {
    AutomapLayer layer = layers.get(levelId);
    if (layer != null) {
      layer.clear();
    }
    nativeCellsBuilt.remove(levelId);
  }
  
  /**
   * 清除所有图层数据
   */
  public void clearAllLayers() {
    for (AutomapLayer layer : layers.values()) {
      layer.clear();
    }
    layers.clear();
    nativeLayerUnknown.clear();
    nativeCellsBuilt.clear();
    activeLayerId = -1;
  }
}
