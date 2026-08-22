 package com.riiablo.map;

import com.artemis.annotations.Wire;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;

import java.util.Arrays;
import java.util.HashMap;

import com.d2moo.common.drlg.DrlgDrlg;
import com.d2moo.common.drlg.DrlgExport;
import com.d2moo.common.drlg.D2DrlgStrc;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.LvlPrest;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.EntityFactory;
import com.riiablo.drlg.DrlgContext;
import com.riiablo.drlg.DrlgLevel;
import com.riiablo.drlg.DrlgGrid;
import com.riiablo.drlg.TileGrid;
import com.riiablo.codec.excel.LvlSub;
import com.riiablo.map.Map.Preset;
import com.riiablo.map.Map.Zone;
import com.riiablo.map.Orientation;
import com.riiablo.map.d2moo.Act1D2MOOLayoutBridge;
import com.riiablo.map.d2moo.Act1D2MOOLayoutBridge.Act1LayoutResult;
import com.riiablo.map.d2moo.Act1D2MOOLayoutBridge.LayoutAndDrlg;
import com.riiablo.map.d2moo.D2MooTileApplier;

/**
 * Act1 地图生成器 - 完全复刻 D2MOD 实现
 * 
 * 参考 D2MOD:
 * - source/D2Common/src/Drlg/DrlgOutPlace.cpp
 * - source/D2Common/src/Drlg/DrlgOutWild.cpp
 * - source/D2Common/src/Drlg/DrlgOutdoors.cpp
 */
public enum Act1MapBuilderD2MOD implements MapBuilder {
  INSTANCE;

  private static final String TAG = "Act1MapBuilderD2MOD";
  private static final boolean DEBUG = true;
  private static final boolean DEBUG_BUILD = DEBUG; // D2MOO 布局调试日志
  // 是否打印 replaceSubPreset 的逐 TileGrid 单元更新日志（默认关闭，只保留汇总行）
  private static final boolean DEBUG_REPLACE_SUB_PRESET_TILES = false;
  // 是否高亮显示所有由 LvlSub.File + DS1 填充的 8x8 房间（调试用）
  // 开启后，这些房间会在 TileGrid 渲染中被覆盖为一种非常显眼的测试地板。
  private static final boolean DEBUG_HIGHLIGHT_DS1_ROOMS = false;
  // 地面/路径调试：打印 grid↔zone 坐标、瓦片 ID 分布、解析失败等（用于诊断纹理错位、重复纹理）
  private static final boolean DEBUG_GROUND_MAP = true;
  // D2MOD: gAct1WildernessDrlgLink 数组
  // 定义 Act1 野外区域的连接关系（具体数值会在运行时根据 Levels.txt 校正）
  private static final int LEVEL_ROGUEENCAMPMENT = 1;
  private static final int LEVEL_BLOODMOOR       = 2;
  private static final int LEVEL_COLDPLAINS      = 3;
  private static final int LEVEL_STONYFIELD      = 4;
  // 注意：D2MOO 的 LEVEL_BURIALGROUNDS 与 riiablo 的 Levels.Id 可能不同，这里只是默认值
  private static final int LEVEL_BURIALGROUNDS   = 22;
  /** 由 D2MOO_JAVA export 填满 TileGrid 的关卡 ID，这些关卡跳过本地 generateOutdoorRoom */
  private final IntSet levelsFilledByExport = new IntSet();
  /** D2MOO 实际生成房间所引用的 DT1 mask（包含 outdoor preset/LvlSub 扩展位）。 */
  private final IntMap<Integer> d2MooDt1Masks = new IntMap<>();
  private static final int LEVEL_DARKWOOD = 5;
  private static final int LEVEL_BLACKMARSH = 6;
  private static final int LEVEL_TAMOEHIGHLAND = 7;
  private static final int LEVEL_MOOMOOFARM = 44;
  private static final int LEVEL_MONASTERYGATE = 31;
  private static final int LEVEL_DENOFEVIL = 8;

  /**
   * D2MOD: byte_6FDCF958 - 路径连通性模式 → 土路瓦片类型索引。
   * 根据 3x3 邻域中 8 个邻居的路径标志，查表得 v19，用于 FloorGrid (v19<<8)|0x82。
   * 来源：DrlgOutdoors.cpp DRLG_OUTDOORS_GenerateDirtPath
   */
  private static final byte[] D2MOD_PATH_TILE_TABLE = {
    0x00, 0x00, 0x10, 0x10, 0x00, 0x00, 0x10, 0x10,
    0x0E, 0x0E, 0x06, 0x13, 0x0E, 0x0E, 0x06, 0x13,
    0x0F, 0x0F, 0x05, 0x05, 0x0F, 0x0F, 0x15, 0x15,
    0x08, 0x08, 0x0A, 0x26, 0x08, 0x08, 0x28, 0x14,
    0x00, 0x00, 0x10, 0x10, 0x00, 0x00, 0x10, 0x10,
    0x0E, 0x0E, 0x06, 0x13, 0x0E, 0x0E, 0x06, 0x13,
    0x0F, 0x0F, 0x05, 0x05, 0x0F, 0x0F, 0x15, 0x15,
    0x08, 0x08, 0x0A, 0x26, 0x08, 0x08, 0x28, 0x14,
    0x0D, 0x0D, 0x07, 0x07, 0x0D, 0x0D, 0x0D, 0x07,
    0x04, 0x04, 0x0B, 0x25, 0x04, 0x04, 0x0B, 0x2B,
    0x03, 0x03, 0x0C, 0x0C, 0x03, 0x03, 0x27, 0x27,
    0x09, 0x09, 0x02, 0x2B, 0x09, 0x09, 0x2C, 0x1A,
    0x0D, 0x0D, 0x07, 0x07, 0x0D, 0x0D, 0x0D, 0x07,
    0x17, 0x17, 0x29, 0x11, 0x17, 0x17, 0x29, 0x11,
    0x03, 0x03, 0x0C, 0x0C, 0x03, 0x03, 0x27, 0x27,
    0x2A, 0x2A, 0x2E, 0x2A, 0x2A, 0x2A, 0x21, 0x1F,
    0x00, 0x00, 0x10, 0x10, 0x00, 0x00, 0x10, 0x10,
    0x0E, 0x0E, 0x06, 0x13, 0x0E, 0x0E, 0x06, 0x13,
    0x0F, 0x0F, 0x05, 0x05, 0x0F, 0x0F, 0x15, 0x15,
    0x08, 0x08, 0x0A, 0x26, 0x08, 0x08, 0x23, 0x14,
    0x00, 0x00, 0x10, 0x10, 0x00, 0x00, 0x10, 0x10,
    0x0E, 0x0E, 0x06, 0x13, 0x0E, 0x0E, 0x06, 0x13,
    0x0F, 0x0F, 0x05, 0x05, 0x0F, 0x0F, 0x15, 0x15,
    0x08, 0x08, 0x0A, 0x26, 0x08, 0x08, 0x28, 0x14,
    0x0D, 0x0D, 0x07, 0x07, 0x0D, 0x0D, 0x0D, 0x07,
    0x04, 0x04, 0x0B, 0x25, 0x04, 0x04, 0x0B, 0x25,
    0x12, 0x12, 0x23, 0x23, 0x12, 0x12, 0x16, 0x16,
    0x24, 0x24, 0x2D, 0x22, 0x24, 0x24, 0x1C, 0x1D,
    0x0D, 0x0D, 0x07, 0x07, 0x0D, 0x0D, 0x0D, 0x07,
    0x17, 0x17, 0x29, 0x11, 0x17, 0x17, 0x29, 0x11,
    0x12, 0x12, 0x23, 0x23, 0x12, 0x12, 0x16, 0x16,
    0x18, 0x18, 0x19, 0x20, 0x18, 0x18, 0x1E, 0x01
  };

  // 布局优先由 D2MOO_JAVA 生成；失败时使用 createFallbackLayout

  /**
   * 当 D2MOO_JAVA getLayout 失败（如 DT1 路径不可用）时，用 Levels.txt 尺寸拼一个简单布局，
   * 保证至少能创建城镇等 zone，避免无 zone 导致 GameScreen NPE。
   */
  private static Act1LayoutResult createFallbackLayout(int diff, int burialGroundsId) {
    Levels.Entry stony = Riiablo.files.Levels.get(LEVEL_STONYFIELD);
    Levels.Entry cold = Riiablo.files.Levels.get(LEVEL_COLDPLAINS);
    Levels.Entry blood = Riiablo.files.Levels.get(LEVEL_BLOODMOOR);
    Levels.Entry town = Riiablo.files.Levels.get(LEVEL_ROGUEENCAMPMENT);
    Levels.Entry burial = Riiablo.files.Levels.get(burialGroundsId);
    if (stony == null || cold == null || blood == null || town == null || burial == null) return null;

    int sw = stony.SizeX[diff], sh = stony.SizeY[diff];
    int cw = cold.SizeX[diff], ch = cold.SizeY[diff];
    int bw = 56, bh = 96; // Blood Moor 固定 56x96
    int tw = town.SizeX[diff], th = town.SizeY[diff];
    int burw = burial.SizeX[diff], burh = burial.SizeY[diff];

    Act1LayoutResult r = new Act1LayoutResult();
    r.levelIds[0] = LEVEL_STONYFIELD;
    r.levelIds[1] = LEVEL_COLDPLAINS;
    r.levelIds[2] = LEVEL_BLOODMOOR;
    r.levelIds[3] = LEVEL_ROGUEENCAMPMENT;
    r.levelIds[4] = burialGroundsId;

    // 布局: Stony-Cold 在东侧，Blood 接 Cold 西侧，Town 在 Blood 西侧，Burial 在 Cold 北侧
    r.coords[0][0] = bw + cw;  r.coords[0][1] = 0;       r.coords[0][2] = sw;   r.coords[0][3] = sh;   // Stony
    r.coords[1][0] = bw;       r.coords[1][1] = 0;       r.coords[1][2] = cw;   r.coords[1][3] = ch;   // Cold
    r.coords[2][0] = 0;        r.coords[2][1] = 0;       r.coords[2][2] = bw;  r.coords[2][3] = bh;   // Blood
    r.coords[3][0] = -tw;      r.coords[3][1] = 0;       r.coords[3][2] = tw;   r.coords[3][3] = th;   // Town 左邻 Blood
    r.coords[4][0] = bw;       r.coords[4][1] = ch;      r.coords[4][2] = burw; r.coords[4][3] = burh; // Burial 北邻 Cold
    r.townDirection = 3; // 出口朝东(Blood)
    return r;
  }

  @Wire(name = "factory")
  protected EntityFactory factory;

  @Wire(name = "client.socket", failOnNull = false)
  protected com.badlogic.gdx.net.Socket socket;

  // 简化版 DRLG 上下文与 Level 映射，仅用于调试和后续 DRLGOUTDOORS 移植。
  private DrlgContext drlgContext;
  private IntMap<DrlgLevel> drlgLevels = new IntMap<>();

  // LvlSub DS1 文件缓存（对应 D2MOO 的 DRLGTILESUB_InitializeDrlgFile）
  // Key: LvlSub.Entry 的 File 路径，Value: 加载的 DS1 对象
  private IntMap<DS1> lvlSubDs1Cache = new IntMap<>();

  /**
   * DS1 子预设放置计数（按 LvlSub.File 分组），用于粗略模拟 LvlSub.Max 限制，降低重复块密度。
   * 注意：这是对 D2MOO/D2MOO_JAVA `DrlgTileSub` 流程的“简化约束”，不是完整移植。
   */
  private final HashMap<String, Integer> lvlSubDs1PlacedCounts = new HashMap<>();

  /** 路径生成时缓存的 map，用于 findPathFloorId 从兄弟 zone 获取有效 floor id */
  private Map pathGenMap;

  // 实际运行时的 Burial Grounds 关卡 ID（从 Levels.txt 推导），用于避免与 D2MOO 的枚举常量不一致
  private int burialGroundsId = LEVEL_BURIALGROUNDS;

  @Override
  public void generate(Map map, int seed, int diff) {
    if (DEBUG_BUILD) {
      Gdx.app.debug(TAG, "=== Act1 Map Generation Started ===");
      Gdx.app.debug(TAG, String.format("Seed: 0x%08X (%d), Difficulty: %d", seed, seed, diff));
    }
    drlgContext = new DrlgContext(seed, diff, 0);
    drlgLevels.clear();
    levelsFilledByExport.clear();
    d2MooDt1Masks.clear();
    lvlSubDs1PlacedCounts.clear();
    MathUtils.random.setSeed(seed);

    // Burial Grounds 的关卡 ID（从 Levels.txt 解析）
    for (Levels.Entry e : Riiablo.files.Levels) {
      if (e != null && "Burial Grounds".equals(e.LevelName)) {
        burialGroundsId = e.Id;
        break;
      }
    }

    // 完全采用 D2MOO_JAVA createLevelConnections 生成布局；失败时使用 Levels.txt 的简单回退布局
    LayoutAndDrlg layoutAndDrlg = Act1D2MOOLayoutBridge.getLayoutAndDrlg(seed, diff, burialGroundsId);
    Act1LayoutResult result;
    D2DrlgStrc drlg;
    if (layoutAndDrlg == null) {
      Gdx.app.log(TAG, "WARN: D2MOO_JAVA getLayout failed (e.g. DT1 file path), using fallback layout from Levels.txt");
      result = createFallbackLayout(diff, burialGroundsId);
      if (result == null) {
        Gdx.app.error(TAG, "Fallback layout failed, cannot generate Act1 map");
        return;
      }
      drlg = null;
    } else {
      result = layoutAndDrlg.result;
      drlg = layoutAndDrlg.drlg;
    }

    if (DEBUG_BUILD) {
      Gdx.app.debug(TAG, String.format("=== D2MOO_JAVA Act1 Layout (seed=0x%08X, diff=%d) ===", seed, diff));
      for (int i = 0; i < 5; i++) {
        Levels.Entry lev = Riiablo.files.Levels.get(result.levelIds[i]);
        Gdx.app.debug(TAG, String.format("  Zone[%d]: %s (id=%d) at (%d,%d) size %dx%d",
            i, lev != null ? lev.LevelName : "?", result.levelIds[i],
            result.coords[i][0], result.coords[i][1], result.coords[i][2], result.coords[i][3]));
      }
      Gdx.app.debug(TAG, String.format("  Town direction: %d", result.townDirection));
    }

    // DrlgLevel 初始化
    final int NUM_ZONES = 5;
    for (int i = 0; i < NUM_ZONES; i++) {
      Levels.Entry level = Riiablo.files.Levels.get(result.levelIds[i]);
      if (level != null && !drlgLevels.containsKey(level.Id)) {
        int w = result.coords[i][2], h = result.coords[i][3];
        // Every linked outdoor level may be rotated or resized for this seed;
        // the export target must match D2MOO's actual level coordinates, not
        // the static Levels.txt dimensions.
        DrlgLevel drlgLevel = new DrlgLevel(level, diff, w, h);
        drlgLevels.put(level.Id, drlgLevel);
      }
    }

    final int townIndex = 3; // ROGUEENCAMPMENT 固定为索引 3
    int offsetX = -result.coords[townIndex][0];
    int offsetY = -result.coords[townIndex][1];
    if (DEBUG_BUILD) {
      Gdx.app.debug(TAG, String.format("Town offset: (%d, %d)", offsetX, offsetY));
    }

    // 创建区域：先创建城镇
    Zone townZone = null;
    for (int i = 0; i < NUM_ZONES; i++) {
      if (result.levelIds[i] == LEVEL_ROGUEENCAMPMENT) {
        Levels.Entry level = Riiablo.files.Levels.get(result.levelIds[i]);
        final int drlgType = level.DrlgType;
        final boolean isPresetLevel = drlgType == 2; // 2 == DRLGTYPE_PRESET

        // 查找与 level 对应的 preset（仅当 DrlgType 为 PRESET 时才整图使用）
        LvlPrest.Entry preset = null;
        if (isPresetLevel) {
          for (LvlPrest.Entry p : Riiablo.files.LvlPrest) {
            if (p.LevelId == result.levelIds[i]) {
              preset = p;
              break;
            }
          }
        }

        int townSelectIndex = 0;
        int townD2Dir = 0;
        if (!isPresetLevel || preset == null) {
          // 理论上城镇应当是 PRESET；若数据不一致，则退回到随机生成，防止读图错乱
          int gridSizeX = OutdoorGrid.GRID_SIZE_TILES;
          int gridSizeY = OutdoorGrid.GRID_SIZE_TILES;
          int tilesX = level.SizeX[diff];
          int tilesY = level.SizeY[diff];
          int gridsX = tilesX / gridSizeX;
          int gridsY = tilesY / gridSizeY;
          townZone = map.addZone(level, gridSizeX, gridSizeY, gridsX, gridsY);
        } else {
          int fileId[] = new int[6];
          int numFiles = Preset.getPresets(preset, fileId);
          if (numFiles == 0) {
            Gdx.app.error(TAG, "No valid presets found for level " + level.LevelName);
            break; // 创建失败，退出循环
          }

          // 城镇预设选择：
          // D2MOO_JAVA 提供的 preset.nDirection 在不同实现/调用点上容易产生“入口/出口语义”的歧义。
          // 为了保证几何一致性，这里直接使用布局坐标推导“城镇出口指向 Blood Moor 的方向”，
          // 然后用该方向选择城镇预设变体，并设置 townExitDirection。
          final int rawD2Dir = result.townDirection; // 仍保留用于调试对照

          // 以布局坐标推导出口方向：比较 Town 与 Blood Moor 的中心点相对位置（tile 单位）
          final int bloodIndex = 2; // Act1LayoutResult: [2] == Blood Moor
          final int townIndex2 = 3; // Act1LayoutResult: [3] == Rogue Encampment
          int townCx = result.coords[townIndex2][0] + result.coords[townIndex2][2] / 2;
          int townCy = result.coords[townIndex2][1] + result.coords[townIndex2][3] / 2;
          int bloodCx = result.coords[bloodIndex][0] + result.coords[bloodIndex][2] / 2;
          int bloodCy = result.coords[bloodIndex][1] + result.coords[bloodIndex][3] / 2;
          int dx = bloodCx - townCx;
          int dy = bloodCy - townCy;
          final int exitD2Dir;
          if (Math.abs(dx) >= Math.abs(dy)) {
            exitD2Dir = dx >= 0 ? 1 : 3; // E/W
          } else {
            exitD2Dir = dy >= 0 ? 2 : 4; // S/N（注意：D2 坐标系 Y 正向为“南”）
          }

          townD2Dir = exitD2Dir;
          final int townFileVariantIndex;
          switch (exitD2Dir) {
            case 4: townFileVariantIndex = 0; break; // N
            case 1: townFileVariantIndex = 1; break; // E
            case 2: townFileVariantIndex = 2; break; // S
            case 3: townFileVariantIndex = 3; break; // W
            default: townFileVariantIndex = 0; break;
          }
          townSelectIndex = townFileVariantIndex;
          int selectIndex = townFileVariantIndex % Math.max(1, numFiles);
          int select = fileId[selectIndex];

          if (select < 0 || select >= preset.File.length || 
              preset.File[select] == null || 
              preset.File[select].isEmpty() || 
              preset.File[select].charAt(0) == '0') {
            Gdx.app.error(TAG, "Invalid file index " + select + " for level " + level.LevelName);
            break;
          }

          townZone = map.addZone(level, preset, select);
        }

        int finalX = (result.coords[i][0] + offsetX) * DT1.Tile.SUBTILE_SIZE;
        int finalY = (result.coords[i][1] + offsetY) * DT1.Tile.SUBTILE_SIZE;
        townZone.setPosition(finalX, finalY);
        townZone.town = true;
        // townExitDirection 用于路径/出口逻辑，统一成本工程的 ALTDIR：0=WEST,1=NORTH,2=EAST,3=SOUTH
        // D2MOO nDirection: 1=E,2=S,3=W,4=N
        switch (townD2Dir) {
          case 1: townZone.townExitDirection = ALTDIR_EAST;  break;
          case 2: townZone.townExitDirection = ALTDIR_SOUTH; break;
          case 3: townZone.townExitDirection = ALTDIR_WEST;  break;
          case 4: townZone.townExitDirection = ALTDIR_NORTH; break;
          default: townZone.townExitDirection = ALTDIR_EAST; break; // 合理默认：面向 Blood Moor
        }

        if (DEBUG_BUILD) {
          Gdx.app.debug(TAG, String.format("Placed town %s (id=%d) at (%d, %d)", level.LevelName, result.levelIds[i], finalX, finalY));
        }
        break; // 成功创建城镇后退出循环
      }
    }
    
    // 创建其他区域（跳过城镇）
    for (int i = 0; i < NUM_ZONES; i++) {
      if (result.levelIds[i] == LEVEL_ROGUEENCAMPMENT) continue;
      Levels.Entry level = Riiablo.files.Levels.get(result.levelIds[i]);
      
      // 查找与 level 对应的 preset（LvlPrest.LevelId == level.id）
      LvlPrest.Entry preset = null;
      for (LvlPrest.Entry p : Riiablo.files.LvlPrest) {
        if (p.LevelId == result.levelIds[i]) {
          preset = p;
          break;
        }
      }
      
      if (preset == null && DEBUG_BUILD) {
        Gdx.app.debug(TAG, "No preset found for level " + level.LevelName + " (id=" + result.levelIds[i] + ")");
        // 查找是否有其他preset的LevelId匹配
        boolean foundAny = false;
        for (LvlPrest.Entry p : Riiablo.files.LvlPrest) {
          if (p.LevelId == result.levelIds[i]) {
            if (!foundAny) {
              Gdx.app.debug(TAG, "  Searching for preset with LevelId=" + result.levelIds[i] + "...");
              foundAny = true;
            }
            Gdx.app.debug(TAG, "  Found preset: id=" + p.Def + ", LevelId=" + p.LevelId);
          }
        }
        if (!foundAny) {
          Gdx.app.debug(TAG, "  No preset with LevelId=" + result.levelIds[i] + " exists in LvlPrest data");
        }
      }
      
      Zone zone;
      final int drlgType = level.DrlgType;
      final boolean isPresetLevel = drlgType == 2; // 2 == DRLGTYPE_PRESET

      if (preset != null && isPresetLevel) {
        // 找到了对应的 preset，使用它
        int fileId[] = new int[6];
        int numFiles = Preset.getPresets(preset, fileId);
        if (numFiles == 0) {
          Gdx.app.error(TAG, "No valid presets found for level " + level.LevelName);
          continue;
        }
        int selectIndex = MathUtils.random(numFiles - 1);
        int select = fileId[selectIndex]; // 使用实际的文件索引
        
        // 验证文件路径不为 "0" 或空
        if (select < 0 || select >= preset.File.length || 
            preset.File[select] == null || 
            preset.File[select].isEmpty() || 
            preset.File[select].charAt(0) == '0') {
          Gdx.app.error(TAG, "Invalid file index " + select + " for level " + level.LevelName + ", preset file: " + (select < preset.File.length ? preset.File[select] : "out of range"));
          continue;
        }
        
        zone = map.addZone(level, preset, select);
      } else {
        // 找不到对应的 preset，不设置 preset，让 generator 生成地形
        // 使用 8x8 网格系统（参考 D2MOD: DRLGGRID）
        int gridSizeX = OutdoorGrid.GRID_SIZE_TILES;  // 每个网格 8 tiles
        int gridSizeY = OutdoorGrid.GRID_SIZE_TILES;  // 每个网格 8 tiles
        
        // D2MOO 布局已包含各区域尺寸
        int tilesX = result.coords[i][2];
        int tilesY = result.coords[i][3];
        int gridsX = tilesX / gridSizeX;  // 例如：80 / 8 = 10 或 96 / 8 = 12
        int gridsY = tilesY / gridSizeY;  // 例如：80 / 8 = 10 或 56 / 8 = 7
        
        // 使用带网格数量的 addZone 方法
        zone = map.addZone(level, gridSizeX, gridSizeY, gridsX, gridsY);
        if (DEBUG_BUILD) {
          Gdx.app.debug(TAG, String.format("No preset found for level %s, using generator to create terrain (%dx%d grids, size=%dx%d tiles)", 
              level.LevelName, gridsX, gridsY, tilesX, tilesY));
        }
      }
      int finalX = (result.coords[i][0] + offsetX) * DT1.Tile.SUBTILE_SIZE;
      int finalY = (result.coords[i][1] + offsetY) * DT1.Tile.SUBTILE_SIZE;
      zone.setPosition(finalX, finalY);
      
      // 明确设置为 false，因为城镇已经在第一个循环中创建并标记了
      // 这个循环只处理非城镇区域
      zone.town = false;
      
      if (DEBUG_BUILD) {
        // 计算坐标范围（sub-tile 单位）
        int minX = zone.x;
        int maxX = zone.x + zone.width;
        int minY = zone.y;
        int maxY = zone.y + zone.height;
        Gdx.app.debug(TAG, String.format("Placed %s (id=%d) at (%d, %d)", 
            level.LevelName, result.levelIds[i], finalX, finalY));
        Gdx.app.debug(TAG, String.format("  Zone bounds: X[%d, %d] (width=%d), Y[%d, %d] (height=%d)", 
            minX, maxX, zone.width, minY, maxY, zone.height));
      }
      
      // 为野外区域设置怪物生成器
      // 将 socket 和 seed 作为 final 变量传递给匿名类，确保能正确访问
      final com.badlogic.gdx.net.Socket finalSocket = socket;
      final int finalSeed = seed;
      zone.generator = new Zone.Generator() {
        final float SPAWN_MULT = 2f;
        MonStats.Entry[] monsters;

        @Override
        public void init(Zone zone) {
          int prob = 0;
          int numMon = zone.level.NumMon;
          if (numMon <= 0) {
            monsters = new MonStats.Entry[0];
            return;
          }
          MonStats.Entry[] monstats = new MonStats.Entry[numMon];
          for (int j = 0; j < numMon; j++) {
            String mon = zone.level.mon[j];
            if (mon == null || mon.isEmpty()) continue;
            monstats[j] = Riiablo.files.monstats.get(mon);
            if (monstats[j] != null) {
              prob += monstats[j].Rarity;
            }
          }

          if (prob <= 0) {
            monsters = new MonStats.Entry[0];
            return;
          }

          monsters = new MonStats.Entry[prob];
          prob = 0;
          for (MonStats.Entry entry : monstats) {
            if (entry != null) {
              for (int j = 0; j < entry.Rarity; j++) {
                monsters[prob++] = entry;
              }
            }
          }
        }

        @Override
        public void generate(Zone zone, DT1s dt1s, int tx, int ty) {
          // tx, ty 是当前 grid 的起始坐标（tile 单位）
          // 参考 D2MOD: DRLGOUTDOORS_GenerateLevel
          // 使用 8x8 网格系统精细控制地形生成
          
          if (DEBUG_BUILD && dt1s == null) {
            Gdx.app.error(TAG, String.format("generate: dt1s is null for zone %s (LevelType=%d)", 
                zone.level.LevelName, zone.level.LevelType));
          }
          
          final int startTx = tx;
          final int startTy = ty;
          final int gridSize = OutdoorGrid.GRID_SIZE_TILES;
          
          // 计算当前 grid 在 zone 中的网格坐标
          int gridX = toLocalGridCoordinate(tx, gridSize);
          int gridY = toLocalGridCoordinate(ty, gridSize);
          
          // 获取 Dt1Mask（根据 Level 的 Act 和 LevelType）
          int dt1Mask = OutdoorGrid.getDt1MaskForLevel(zone.level);
          
          if (DEBUG_BUILD && dt1s != null && dt1s.tiles.size == 0) {
            Gdx.app.error(TAG, String.format("generate: dt1s is empty for zone %s (LevelType=%d, dt1Mask=0x%X)", 
                zone.level.LevelName, zone.level.LevelType, dt1Mask));
          }
          
          // 找到对应的 DrlgLevel / DrlgGrid / TileGrid（用于 DRLG 生成流程）
          DrlgLevel drlgLevel = drlgLevels.get(zone.level.Id);
          DrlgGrid drlgGrid = drlgLevel != null ? drlgLevel.drlgGrid : null;
          TileGrid grid = drlgLevel != null ? drlgLevel.grid : null;
          boolean renderD2MooExport = levelsFilledByExport.contains(zone.level.Id);
          
          // D2MOD: DRLGOUTPLACE_CreateOutdoorRoomEx 等价逻辑
          // 检查当前 8x8 网格单元是否有 preset
          boolean hasPreset = false;
          if (drlgGrid != null && drlgGrid.inBounds(gridX, gridY)) {
            DrlgGrid.PackedGrid2Info grid2Info = drlgGrid.grid2Info[gridY][gridX];
            hasPreset = grid2Info.bHasPickedFile;
          }
          
          if (DEBUG_BUILD) {
            Gdx.app.debug(TAG, String.format("DRLG generate: zone=%s, grid=(%d,%d), hasPreset=%s, drlgGrid=%s, inBounds=%s",
                zone.level.LevelName, gridX, gridY, hasPreset, 
                drlgGrid != null ? "not null" : "null",
                drlgGrid != null && drlgGrid.inBounds(gridX, gridY)));
          }
          
          // 如果没有 preset，生成随机房间（对应 DRLGOUTPLACE_CreateOutdoorRoomEx）
          // 若该关卡已由 D2MOO_JAVA export 填满 TileGrid，则跳过本地 generateOutdoorRoom
          if (!hasPreset && !renderD2MooExport) {
            // D2MOD: DRLGTILESUB_PickSubThemes - 根据 SubType/SubTheme 选择子主题掩码
            int subThemeMask = pickSubThemes(zone, drlgLevel, finalSeed, gridX, gridY);
            
            if (DEBUG_BUILD && drlgLevel != null) {
              Gdx.app.debug(TAG, String.format("DRLG generate: zone=%s, grid=(%d,%d), subType=%d, subTheme=%d, mask=0x%X",
                  zone.level.LevelName, gridX, gridY, drlgLevel.subType, 
                  drlgLevel.levelsEntry.SubTheme, subThemeMask));
            }
            
            // 根据子主题掩码和 LvlSub 生成地板瓦片
            generateOutdoorRoom(zone, drlgLevel, drlgGrid, gridX, gridY, 
                startTx, startTy, gridSize, dt1s, dt1Mask, subThemeMask, finalSeed);
          } else if (DEBUG_BUILD) {
            Gdx.app.debug(TAG, String.format("DRLG generate: zone=%s, grid=(%d,%d), SKIPPED generateOutdoorRoom (hasPreset=true)",
                zone.level.LevelName, gridX, gridY));
          }

          // 遍历当前 8x8 网格内的所有 tile（用于怪物生成和影子记录）
          for (int x = 0; x < gridSize; x++) {
            for (int y = 0; y < gridSize; y++) {
              int currentTx = startTx + x;
              int currentTy = startTy + y;
              
              // Zone.generate passes grid offsets in zone-local tile units.
              // zone.tx/ty are world tile origins and must not participate in
              // array indexing here, especially for non-origin outdoor zones.
              int tileIndex = toLocalTileIndex(
                  currentTx, currentTy, zone.tilesX, zone.tilesY);
              if (tileIndex < 0) continue;

              int tileX = currentTx;
              int tileY = currentTy;
              int exportedId = -1;
              DT1.Tile[] floorLayer = zone.getLayer(Map.FLOOR_OFFSET);

              // D2MOO export happens before Zone.generate(). Resolve its tile
              // first so the compatibility terrain generator cannot silently
              // overwrite the exported grid.
              if (renderD2MooExport
                  && grid != null && grid.inBounds(tileX, tileY)) {
                exportedId = grid.floorIds[tileY][tileX];
                if (exportedId != -1 && dt1s != null) {
                  DT1.Tile exportedTile = dt1s.get(exportedId);
                  if (exportedTile != null) {
                    floorLayer[tileIndex] = exportedTile;
                  }
                }
              }

              // D2MOO outdoor levels are irregular RoomEx footprints inside
              // a rectangular level bounding box. A -1 exported ID means no
              // room exists here and must remain empty, not fallback terrain.
              if (floorLayer[tileIndex] == null
                  && (!renderD2MooExport || exportedId != -1)) {
                DT1.Tile tile = selectTerrainTile(dt1s, gridX, gridY, x, y, dt1Mask);
                if (tile != null) {
                  floorLayer[tileIndex] = tile;
                } else {
                  floorLayer[tileIndex] = dt1s.get(0, 0, 0);
                }
              }

              // Compatibility generation owns non-exported grids. Accepted
              // D2MOO grids retain both their IDs and their empty footprint.
              if (!renderD2MooExport && grid != null && grid.inBounds(tileX, tileY)) {
                if (grid.floorIds[tileY][tileX] == -1) {
                  DT1.Tile floor = floorLayer[tileIndex];
                  int id = floor != null ? floor.id : -1;
                  grid.floorIds[tileY][tileX] = id;
                }
              }

              // Do not spawn compatibility monsters in the rectangular area
              // outside D2MOO's generated RoomEx footprint.
              if (renderD2MooExport
                  && (grid == null || !grid.inBounds(tileX, tileY)
                      || !grid.exportedFloorCells[tileY][tileX])) {
                continue;
              }
              
              // 生成怪物（仅在客户端）
              if (finalSocket == null && zone.map.factory != null && monsters != null && monsters.length > 0) {
                if (MathUtils.randomBoolean(SPAWN_MULT * zone.level.MonDen[zone.diff] / 100000f)) {
                  int idx = MathUtils.random(monsters.length - 1);
                  MonStats.Entry monster = monsters[idx];
                  if (monster == null) continue;
                  int count = monster.MinGrp == monster.MaxGrp
                      ? monster.MaxGrp
                      : MathUtils.random(monster.MinGrp, monster.MaxGrp);
                  for (int j = 0; j < count; j++) {
                    float px = zone.getGlobalX(currentTx * DT1.Tile.SUBTILE_SIZE) + MathUtils.random(-2f, 2f);
                    float py = zone.getGlobalY(currentTy * DT1.Tile.SUBTILE_SIZE) + MathUtils.random(-2f, 2f);
                    zone.map.factory.createMonster(monster, px, py);
                  }
                }
              }
            }
          }
        }
        
        /**
         * D2MOD: DRLGTILESUB_PickSubThemes - 根据 SubType/SubTheme 选择子主题掩码
         * 
         * 对应 D2MOO 的 DRLGTILESUB_PickSubThemes 函数。
         * 根据 Levels.txt 的 SubType/SubTheme 和 LvlSub.txt 的 Prob 来选择子主题掩码。
         */
        private int pickSubThemes(Zone zone, DrlgLevel drlgLevel, int seed, int gridX, int gridY) {
          if (drlgLevel == null || drlgLevel.subType == -1) {
            if (DEBUG_BUILD) {
              Gdx.app.debug(TAG, String.format("pickSubThemes: drlgLevel=null or subType=-1 for %s", zone.level.LevelName));
            }
            return 0; // 无子类型，返回空掩码
          }
          
          int subType = drlgLevel.subType;
          int subTheme = drlgLevel.levelsEntry.SubTheme; // 从 Levels.txt 读取
          
          if (subType == -1 || subTheme == -1) {
            if (DEBUG_BUILD) {
              Gdx.app.debug(TAG, String.format("pickSubThemes: subType=%d or subTheme=%d invalid for %s", 
                  subType, subTheme, zone.level.LevelName));
            }
            return 0;
          }
          
          // 获取该 SubType 的所有 LvlSub 记录
          LvlSub.Entry[] lvlSubEntries = Riiablo.files.LvlSub.getByType(subType);
          if (lvlSubEntries == null || lvlSubEntries.length == 0) {
            if (DEBUG_BUILD) {
              Gdx.app.debug(TAG, String.format("pickSubThemes: no LvlSub entries found for subType=%d in %s", 
                  subType, zone.level.LevelName));
            }
            return 0;
          }
          
          // 使用 seed 初始化随机数生成器（确保可重现）
          MathUtils.random.setSeed(seed + zone.level.Id * 1000 + gridX * 100 + gridY);
          
          int mask = 0;
          int counter = 0;
          
          // 遍历所有该 SubType 的 LvlSub 记录
          for (LvlSub.Entry entry : lvlSubEntries) {
            if (entry == null || entry.Type != subType) {
              continue;
            }
            
            // 检查 Prob[subTheme] 是否满足（对应 D2MOO 的 SEED_RollRandomNumber % 100 < Prob）
            if (subTheme >= 0 && subTheme < entry.Prob.length) {
              int prob = entry.Prob[subTheme];
              int roll = Math.abs(MathUtils.random.nextInt() % 100);
              if (prob > 0 && roll < prob) {
                mask |= (1 << counter);
                if (DEBUG_BUILD && counter < 5) { // 只打印前 5 个，避免日志过多
                  Gdx.app.debug(TAG, String.format("pickSubThemes: entry[%d] %s selected (prob=%d, roll=%d)", 
                      counter, entry.Name, prob, roll));
                }
              }
            }
            
            counter++;
          }
          
          if (DEBUG_BUILD && mask != 0) {
            Gdx.app.debug(TAG, String.format("pickSubThemes: %s subType=%d, subTheme=%d, mask=0x%X (%d entries checked)", 
                zone.level.LevelName, subType, subTheme, mask, counter));
          }
          
          return mask;
        }
        
        /**
         * D2MOD: DRLGOUTPLACE_CreateOutdoorRoomEx 等价逻辑 - 生成 8x8 随机房间
         * 
         * 根据子主题掩码和 LvlSub 生成地板瓦片。
         */
        private void generateOutdoorRoom(Zone zone, DrlgLevel drlgLevel, DrlgGrid drlgGrid,
            int gridX, int gridY, int startTx, int startTy, int gridSize,
            DT1s dt1s, int dt1Mask, int subThemeMask, int seed) {
          
          if (drlgLevel == null || drlgLevel.subType == -1) {
            // 无子类型，使用默认生成逻辑
            return;
          }
          
          int subType = drlgLevel.subType;
          LvlSub.Entry[] lvlSubEntries = Riiablo.files.LvlSub.getByType(subType);
          
          if (lvlSubEntries == null || lvlSubEntries.length == 0) {
            return;
          }

          // 这里不再在 room 级别直接应用 DS1 子预设，统一交给后处理或基础 DT1 生成。
          boolean filledByDs1 = false;
          
          // 使用网格坐标生成稳定的随机种子
          int roomSeed = (zone.level.Id * 10000 + gridX * 100 + gridY) ^ seed;
          MathUtils.random.setSeed(roomSeed);
          
          // 遍历当前 8x8 网格内的所有 tile（注意：这里的坐标全部使用 Zone 内部的 tile 坐标 0..tilesX/tilesY，
          // 不要和 zone.tx/ty 的 subtile 世界坐标混用）
          for (int x = 0; x < gridSize; x++) {
            for (int y = 0; y < gridSize; y++) {
              int currentTx = startTx + x;
              int currentTy = startTy + y;
              
              // 检查是否在 zone 范围内（Zone 内部 tile 坐标）
              if (currentTx < 0 || currentTx >= zone.tilesX ||
                  currentTy < 0 || currentTy >= zone.tilesY) {
                continue;
              }
              
              // 这里的 currentTx/currentTy 已经是 Zone 内部 tile 坐标，直接用于索引
              int tileIndex = Zone.index(zone.tilesX, currentTx, currentTy);
              
              // 如果地板还没有生成，根据 LvlSub 选择瓦片
              if (zone.getLayer(Map.FLOOR_OFFSET)[tileIndex] == null) {
                DT1.Tile tile = selectTerrainTileFromLvlSub(dt1s, lvlSubEntries, subThemeMask, 
                    gridX, gridY, x, y, dt1Mask);
                if (tile != null) {
                  zone.getLayer(Map.FLOOR_OFFSET)[tileIndex] = tile;
                } else {
                  // 回退到默认逻辑
                  tile = selectTerrainTile(dt1s, gridX, gridY, x, y, dt1Mask);
                  if (tile != null) {
                    zone.getLayer(Map.FLOOR_OFFSET)[tileIndex] = tile;
                  } else {
                    zone.getLayer(Map.FLOOR_OFFSET)[tileIndex] = dt1s.get(0, 0, 0);
                  }
                }
              }
              
              // 将生成的地板信息写入 TileGrid（用于调试和后续 DRLGOUTDOORS 移植）
              // 注意：TileGrid 的尺寸可能和 zone 的实际尺寸不一致（如 Blood Moor 动态调整）
              // 无论瓦片是新生成的还是已存在的，都写入 TileGrid（确保 TileGrid 记录完整）
              if (drlgLevel != null && drlgLevel.grid != null) {
                // TileGrid 的坐标系统按 Levels.txt 的 tilesX/tilesY 来初始化，
                // 和 Zone 一样使用“内部 tile 坐标”0..tilesX/tilesY，不再减去 zone.tx/ty（那是 subtile 世界坐标）
                int tileX = currentTx;
                int tileY = currentTy;
                TileGrid grid = drlgLevel.grid;
                // 检查坐标是否在 TileGrid 范围内（可能小于 zone 的实际尺寸）
                if (tileX >= 0 && tileX < grid.width && tileY >= 0 && tileY < grid.height) {
                  DT1.Tile floor = (DT1.Tile) zone.getLayer(Map.FLOOR_OFFSET)[tileIndex];
                  int id = floor != null ? floor.id : -1;
                  grid.floorIds[tileY][tileX] = id;
                  
                  // 调试：记录前几个写入的坐标
                  if (DEBUG_BUILD && (tileX < 5 && tileY < 5)) {
                    Gdx.app.debug(TAG, String.format("generateOutdoorRoom: wrote tile id=%d at TileGrid[%d][%d] (zone relative: %d,%d, global: %d,%d)",
                        id, tileY, tileX, tileX, tileY, currentTx, currentTy));
                  }
                } else if (DEBUG_BUILD && (tileX < 10 && tileY < 10)) {
                  // 调试：记录超出范围的坐标
                  Gdx.app.debug(TAG, String.format("generateOutdoorRoom: tile out of TileGrid bounds: TileGrid[%d][%d] (zone relative: %d,%d, global: %d,%d, TileGrid size: %dx%d, zone size: %dx%d)",
                      tileY, tileX, tileX, tileY, currentTx, currentTy, grid.width, grid.height, zone.tilesX, zone.tilesY));
                }
              }
            }
          }
          
          if (DEBUG_BUILD && drlgLevel != null) {
            int tilesGenerated = 0;
            int tilesWrittenToGrid = 0;
            TileGrid grid = drlgLevel.grid;
            for (int x = 0; x < gridSize; x++) {
              for (int y = 0; y < gridSize; y++) {
                int currentTx = startTx + x;
                int currentTy = startTy + y;
                // 同样，这里完全使用 Zone 内部 tile 坐标，而不是 world/subtile 坐标
                if (currentTx >= 0 && currentTx < zone.tilesX &&
                    currentTy >= 0 && currentTy < zone.tilesY) {
                  int tileIndex = Zone.index(zone.tilesX, currentTx, currentTy);
                  if (zone.getLayer(Map.FLOOR_OFFSET)[tileIndex] != null) {
                    tilesGenerated++;
                    
                    // 检查是否写入了 TileGrid
                    int tileX = currentTx;
                    int tileY = currentTy;
                    if (tileX >= 0 && tileX < grid.width && tileY >= 0 && tileY < grid.height) {
                      if (grid.floorIds[tileY][tileX] != -1) {
                        tilesWrittenToGrid++;
                      }
                    }
                  }
                }
              }
            }
            if (tilesGenerated > 0) {
              Gdx.app.debug(TAG, String.format("generateOutdoorRoom: %s grid(%d,%d) generated %d tiles, written %d to TileGrid, mask=0x%X, TileGrid size=%dx%d, zone size=%dx%d",
                  zone.level.LevelName, gridX, gridY, tilesGenerated, tilesWrittenToGrid, subThemeMask,
                  grid.width, grid.height, zone.tilesX, zone.tilesY));
            }
            // 地面调试：首个 BM grid 打印坐标映射
            if (DEBUG_GROUND_MAP && zone.level.Id == LEVEL_BLOODMOOR && gridX == 0 && gridY == 0) {
              Gdx.app.log(TAG, String.format("[GroundDebug] BM grid(0,0): startTx=%d startTy=%d zone.tiles=%dx%d zone.tx=%d ty=%d TileGrid=%dx%d",
                  startTx, startTy, zone.tilesX, zone.tilesY, zone.tx, zone.ty, grid.width, grid.height));
            }
          }
        }
        
        /**
         * 使用 LvlSub.File 对应的 DS1 group，尝试直接用 DS1 floor 图案铺满当前 8x8 房间。
         *
         * 简化版规则：
         * - 仅在 Act1 野外（Blood Moor ~ Tamoe Highland）尝试；
         * - 仅使用第一个同时满足：
         *   - 在 subThemeMask 中被激活；
         *   - File 非空且 DS1 能成功加载；
         *   - 存在 width/height 均不超过 gridSize 的 group；
         * - group 左上角对齐当前 8x8 房间的 startTx/startTy，多余部分暂不使用。
         *
         * 返回值：若至少写入一个 tile，则返回 true。
         */
        private boolean applyLvlSubDs1Room(Zone zone, DrlgLevel drlgLevel,
            int gridX, int gridY, int startTx, int startTy, int gridSize,
            DT1s dt1s, LvlSub.Entry[] lvlSubEntries, int subThemeMask, int seed) {

          if (dt1s == null || lvlSubEntries == null || lvlSubEntries.length == 0) {
            return false;
          }

          // 1. 从 subThemeMask 中收集“激活且带 File”的 LvlSub 条目，并用稳定种子随机挑一个。
          //    之前“只取第一个”会导致大片 8x8 房间重复同一 DS1 图案。
          LvlSub.Entry chosenEntry = null;
          int maxBits = Math.min(lvlSubEntries.length, 32);
          int roomSeed = (zone.level.Id * 10000 + gridX * 100 + gridY) ^ seed;
          java.util.ArrayList<LvlSub.Entry> candidates = new java.util.ArrayList<>();
          for (int i = 0; i < maxBits; i++) {
            if ((subThemeMask & (1 << i)) == 0) continue;
            LvlSub.Entry e = lvlSubEntries[i];
            if (e == null || e.File == null || e.File.isEmpty()) continue;
            candidates.add(e);
          }
          if (!candidates.isEmpty()) {
            MathUtils.random.setSeed(roomSeed);
            int idx = Math.abs(MathUtils.random.nextInt()) % candidates.size();
            chosenEntry = candidates.get(idx);
          }

          // 1b. 兜底逻辑：如果 subThemeMask 没有选出带 File 的条目，对 Act1 户外尝试用 Prob 决定是否使用 DS1。
          //     按 LvlSub.Prob[SubTheme] 做随机判定，避免 DS1 房间过密，贴近 D2MOO 的 Prob/Trials/Max 语义。
          if (chosenEntry == null
              && zone.level.Act == 0
              && zone.level.Id >= LEVEL_BLOODMOOR
              && zone.level.Id <= LEVEL_TAMOEHIGHLAND) {
            LvlSub.Entry fallbackCandidate = null;
            for (LvlSub.Entry e : lvlSubEntries) {
              if (e == null || e.File == null || e.File.isEmpty()) continue;
              fallbackCandidate = e;
              break;
            }
            if (fallbackCandidate != null) {
              int subTheme = drlgLevel != null && drlgLevel.levelsEntry != null
                  ? Math.min(Math.max(0, drlgLevel.levelsEntry.SubTheme), 4)
                  : 0;
              int prob = fallbackCandidate.Prob != null && subTheme < fallbackCandidate.Prob.length
                  ? fallbackCandidate.Prob[subTheme]
                  : 0;
              if (prob > 0) {
                MathUtils.random.setSeed(roomSeed);
                int roll = Math.abs(MathUtils.random.nextInt() % 100);
                if (roll < prob) {
                  chosenEntry = fallbackCandidate;
                  if (DEBUG_BUILD) {
                    Gdx.app.debug(TAG, String.format(
                        "applyLvlSubDs1Room: fallback LvlSub %s (Prob=%d, roll=%d) for %s grid(%d,%d)",
                        fallbackCandidate.Name, prob, roll, zone.level.LevelName, gridX, gridY));
                  }
                }
              }
            }
          }

          if (chosenEntry == null) {
            return false;
          }

          // 1c. 密度控制（简化版）：用 Prob/Max 约束 DS1 房间出现频率，避免整张图充满同类块导致“重复感”极强。
          // D2MOO/D2MOO_JAVA 完整实现见：DrlgTileSub.cpp / DrlgTileSub.java（含 Trials/坐标洗牌/replaceSubPreset）。
          int subTheme = drlgLevel != null && drlgLevel.levelsEntry != null
              ? Math.min(Math.max(0, drlgLevel.levelsEntry.SubTheme), 4)
              : 0;
          int prob = chosenEntry.Prob != null && subTheme < chosenEntry.Prob.length ? chosenEntry.Prob[subTheme] : 0;
          int max = chosenEntry.Max != null && subTheme < chosenEntry.Max.length ? chosenEntry.Max[subTheme] : 0;
          String countKey = (chosenEntry.File != null ? chosenEntry.File : "") + "#" + (chosenEntry.Name != null ? chosenEntry.Name : "");
          if (max > 0) {
            Integer placed = lvlSubDs1PlacedCounts.get(countKey);
            if (placed != null && placed >= max) {
              return false;
            }
          }
          if (prob > 0) {
            int densitySeed = (zone.level.Id * 10000 + gridX * 100 + gridY) ^ seed;
            MathUtils.random.setSeed(densitySeed);
            int roll = Math.abs(MathUtils.random.nextInt() % 100);
            if (roll >= prob) {
              return false;
            }
          }

          // 2. 加载 DS1，并挑一个适合当前 8x8 房间尺寸的 group
          DS1 ds1 = Act1MapBuilderD2MOD.this.initializeLvlSubDs1(chosenEntry);
          if (ds1 == null || ds1.groups == null || ds1.groups.length == 0) {
            return false;
          }

          // 2b. 选择一个 group（偏向更大 area，但不固定取最大），避免同一种 group 过度重复
          DS1.Group group = null;
          int totalWeight = 0;
          java.util.ArrayList<DS1.Group> groupCandidates = new java.util.ArrayList<>();
          java.util.ArrayList<Integer> weights = new java.util.ArrayList<>();
          for (DS1.Group g : ds1.groups) {
            if (g == null) continue;
            if (g.width <= gridSize && g.height <= gridSize) {
              int area = Math.max(1, g.width * g.height);
              groupCandidates.add(g);
              weights.add(area);
              totalWeight += area;
            }
          }
          if (!groupCandidates.isEmpty()) {
            MathUtils.random.setSeed(roomSeed ^ (chosenEntry.Name != null ? chosenEntry.Name.hashCode() : 0));
            int r = Math.abs(MathUtils.random.nextInt()) % Math.max(1, totalWeight);
            int acc = 0;
            for (int i = 0; i < groupCandidates.size(); i++) {
              acc += weights.get(i);
              if (r < acc) {
                group = groupCandidates.get(i);
                break;
              }
            }
            if (group == null) group = groupCandidates.get(0);
          }

          if (group == null) {
            return false;
          }

          DT1.Tile[] floorLayer = zone.getLayer(Map.FLOOR_OFFSET);
          if (floorLayer == null) {
            return false;
          }

          int tilesWritten = 0;

          if (DEBUG_BUILD) {
            Gdx.app.debug(TAG, String.format(
                "applyLvlSubDs1Room: trying LvlSub %s (Type=%d, File=%s) DS1 group[%d,%d,%dx%d] for %s grid(%d,%d)",
                chosenEntry.Name, chosenEntry.Type, chosenEntry.File,
                group.x, group.y, group.width, group.height,
                zone.level.LevelName, gridX, gridY));
          }

          // 3. 放置方式：必须覆盖整个 8x8 房间。
          // 若只盖 stamp，会导致剩余 tile 走随机地形填充，从而出现“不相配的随机块/重复块”拼贴噪声。
          // 这里采用 DS1 group 的 pattern 通过取模平铺到整个 8x8（仍是简化版，但至少连贯）。
          for (int localY = 0; localY < gridSize; localY++) {
            for (int localX = 0; localX < gridSize; localX++) {
              int tileX = startTx + localX;
              int tileY = startTy + localY;

              // 检查是否在 zone 范围内
              if (tileX < 0 || tileX >= zone.tilesX || tileY < 0 || tileY >= zone.tilesY) {
                continue;
              }

              int tileIndex = Zone.index(zone.tilesX, tileX, tileY);

        // 从 DS1 中取对应的 floor cell
              if (ds1.numFloors <= 0 || ds1.floors == null) {
                continue;
              }

              int ds1X = group.x + (localX % group.width);
              int ds1Y = group.y + (localY % group.height);
              if (ds1X < 0 || ds1X >= ds1.width || ds1Y < 0 || ds1Y >= ds1.height) {
                continue;
              }

              int floorLine = ds1.width * ds1.numFloors;
              int floorIndex = ds1Y * floorLine + ds1X * ds1.numFloors + 0; // 只用第一层 floor
              if (floorIndex < 0 || floorIndex >= ds1.floorLen) {
                continue;
              }

              DS1.Cell floorCell = ds1.floors[floorIndex];
              if (floorCell == null || floorCell.mainIndex < 0 || floorCell.subIndex < 0) {
                continue;
              }

        // 根据 mainIndex/subIndex 从 zone.dt1s 里取出真正的 DT1.Tile
        DT1.Tile replacement = dt1s.get(Orientation.FLOOR, floorCell.mainIndex, floorCell.subIndex);
        if (replacement == null) {
          continue;
        }

        // 普通模式：直接写入 DS1 的真实地板
        // 调试高亮模式：仍然让 zone 层使用真实地板，但 TileGrid 里写入“测试 tile”，
        // 这样开启 RenderFromTileGrid 时，这些 8x8 房间会被一眼看出来。
        floorLayer[tileIndex] = replacement;
        tilesWritten++;

        // 同步到 TileGrid，便于后续从 TileGrid 渲染
        if (drlgLevel != null && drlgLevel.grid != null) {
          TileGrid grid = drlgLevel.grid;
          if (tileX >= 0 && tileX < grid.width && tileY >= 0 && tileY < grid.height) {
            if (DEBUG_HIGHLIGHT_DS1_ROOMS) {
              // 使用“棋盘格”高亮：在 TileGrid 中写入两种不同的 tileId，形成非常明显的人造图案。
              // 这样无论原始 DS1 图案如何，开启 RenderFromTileGrid 后都能一眼看出哪些 8x8 房间来自 DS1。
              DT1.Tile debugA = dt1s.get(Orientation.FLOOR, 18, 0);
              DT1.Tile debugB = dt1s.get(Orientation.FLOOR, 18, 1);
              int idA = debugA != null ? debugA.id : replacement.id;
              int idB = debugB != null ? debugB.id : replacement.id;
              // 以 localX/localY 形成棋盘格
              boolean useA = ((localX + localY) & 1) == 0;
              grid.floorIds[tileY][tileX] = useA ? idA : idB;
            } else {
              grid.floorIds[tileY][tileX] = replacement.id;
            }
          }
        }
            }
          }

          if (DEBUG_BUILD && tilesWritten > 0) {
            Gdx.app.debug(TAG, String.format(
                "applyLvlSubDs1Room: filled %d tiles in %s grid(%d,%d) using LvlSub %s DS1 %s group[%d,%d,%dx%d]",
                tilesWritten,
                zone.level.LevelName, gridX, gridY,
                chosenEntry.Name, chosenEntry.File,
                group.x, group.y, group.width, group.height));
          }

          if (tilesWritten > 0) {
            lvlSubDs1PlacedCounts.put(countKey, (lvlSubDs1PlacedCounts.get(countKey) == null ? 1 : lvlSubDs1PlacedCounts.get(countKey) + 1));
          }
          return tilesWritten > 0;
        }

        /**
         * 根据 LvlSub 选择地形瓦片（简化但“有肉眼可见效果”的版本）。
         *
         * 设计目标：
         * - 不解析 LvlSub.File 对应的 ds1，只用现有 dt1 集合；
         * - 让不同的 SubTheme / LvlSub.Entry 至少在“选哪块地板”上产生可见差异；
         * - 如果没有合适的候选，则回退到旧的随机逻辑（由调用方处理）。
         *
         * 具体做法：
         * 1）从 subThemeMask 中提取本格启用的 LvlSub.Entry，并用 Dt1Mask 粗略过滤；
         * 2）基于 (gridX,gridY,x,y,subThemeMask) 生成一个稳定种子，从激活的 entry 里选一个；
         * 3）在当前 dt1 集合的 floor tiles 中，再用该 entry.Dt1Mask 做一次 AND 过滤；
         * 4）从过滤后的 floor tiles 里随机挑一个返回。
         */
        private DT1.Tile selectTerrainTileFromLvlSub(DT1s dt1s, LvlSub.Entry[] lvlSubEntries,
            int subThemeMask, int gridX, int gridY, int x, int y, int dt1Mask) {

          if (dt1s == null || lvlSubEntries == null || lvlSubEntries.length == 0) {
            return null;
          }

          // 1. 收集当前格子“激活”的 LvlSub 记录
          com.badlogic.gdx.utils.Array<LvlSub.Entry> activeEntries = new com.badlogic.gdx.utils.Array<>();
          for (int i = 0; i < lvlSubEntries.length && i < 32; i++) {
            if ((subThemeMask & (1 << i)) == 0) continue;
            LvlSub.Entry entry = lvlSubEntries[i];
            if (entry == null) continue;

            // 如果 LvlSub.Dt1Mask 为 0，表示不过滤；否则要求和当前 dt1Mask 有交集
            if (entry.Dt1Mask != 0 && (entry.Dt1Mask & dt1Mask) == 0) continue;

            activeEntries.add(entry);
          }

          if (activeEntries.size == 0) {
            return null; // 没有任何激活的 LvlSub 记录，交给默认逻辑
          }

          // 2. 用“房间级别”的信息生成稳定种子，从激活列表中挑一个 entry
          //    注意：这里刻意不引入 (x,y)，让同一 8x8 房间内部使用同一组 LvlSub 规则，
          //    这样画面上会呈现成块的子主题，而不是每个 tile 都完全随机。
          int themeSeed = (gridX * 31 + gridY) * 131
                        + (subThemeMask * 7)
                        + dt1Mask * 3;
          MathUtils.random.setSeed(themeSeed);
          LvlSub.Entry chosen = activeEntries.get(MathUtils.random(activeEntries.size - 1));

          // 3. 从 dt1s 中收集 floor tiles，并用 chosen.Dt1Mask 粗略过滤
          //    同时为“同一主题下的小范围多样性”预留多个候选，而不是只用一块。
          com.badlogic.gdx.utils.Array<Integer> floorTileIds = new com.badlogic.gdx.utils.Array<>();
          for (com.badlogic.gdx.utils.IntMap.Entry<com.badlogic.gdx.utils.Array<DT1.Tile>> e : dt1s.tiles.entries()) {
            int id = e.key;
            int orientation = DT1.Tile.Index.orientation(id);
            if (orientation != 0) continue; // 只考虑 floor tiles

            com.badlogic.gdx.utils.Array<DT1.Tile> tileArray = e.value;
            if (tileArray == null || tileArray.size == 0) continue;

            // 如果 chosen.Dt1Mask 为 0，则不过滤；否则要求 (id & chosen.Dt1Mask) != 0
            if (chosen.Dt1Mask != 0 && (id & chosen.Dt1Mask) == 0) {
              continue;
            }

            floorTileIds.add(id);
          }

          if (floorTileIds.size == 0) {
            return null; // 过滤后没有可用 floor tile，交给默认逻辑
          }

          // 4. 在房间级别上，选出一个“小集合”的候选 tile，供 8x8 内轻微随机使用
          //    - 保证同一房间内 tile 来自同一主题/掩码；
          //    - 但不会全部都是同一块，避免“纯色大砖块”的感觉。
          MathUtils.random.setSeed(themeSeed * 1103515245 + 12345);
          final int MAX_VARIANTS = 3; // 同一房间内最多使用 3 种基础 tile
          int variantsCount = Math.min(MAX_VARIANTS, floorTileIds.size);
          com.badlogic.gdx.utils.IntArray variants = new com.badlogic.gdx.utils.IntArray(false, variantsCount);
          for (int i = 0; i < variantsCount; i++) {
            int idx = MathUtils.random(floorTileIds.size - 1);
            variants.add(floorTileIds.get(idx));
          }

          // 5. 不要在 tile 级别引入 (x,y) 抖动。
          // 这会把 8x8 内的地板变成“噪声拼贴”，邻接块很难看起来“相配”。
          // 先保证每个 8x8 房间只用 1 个基础 tile（更接近 D2 的“按簇替换”观感），后续再补齐真正的边界/替换规则。
          MathUtils.random.setSeed(themeSeed ^ 0x5bd1e995);
          int selectedId = variants.get(MathUtils.random(variants.size - 1));
          DT1.Tile tile = dt1s.get(selectedId);

          return tile;
        }
        
        /**
         * 根据网格位置和 Dt1Mask 选择地形瓦片
         * 参考 D2MOD: DRLGOUTPLACE_CreateOutdoorRoomEx
         */
        private DT1.Tile selectTerrainTile(DT1s dt1s, int gridX, int gridY, int x, int y, int dt1Mask) {
          if (dt1s == null) {
            if (DEBUG_BUILD) {
              Gdx.app.debug(TAG, "selectTerrainTile: dt1s is null!");
            }
            return null;
          }
          
          // 使用网格坐标和局部坐标生成伪随机索引
          // 这样可以确保相同位置总是生成相同的地形
          int seed = (gridX * 31 + gridY) * 17 + (x * 7 + y);
          MathUtils.random.setSeed(seed);
          
          // 收集所有可用的 floor tiles (orientation = 0)
          Array<Integer> floorTileIds = new Array<>();
          for (IntMap.Entry<Array<DT1.Tile>> entry : dt1s.tiles.entries()) {
            int id = entry.key;
            int orientation = DT1.Tile.Index.orientation(id);
            if (orientation == 0) { // Floor tile
              Array<DT1.Tile> tileArray = entry.value;
              if (tileArray != null && tileArray.size > 0) {
                floorTileIds.add(id);
              }
            }
          }
          
          // 从所有可用的 floor tiles 中随机选择一个
          if (floorTileIds.size > 0) {
            int randomIndex = MathUtils.random(floorTileIds.size - 1);
            int selectedId = floorTileIds.get(randomIndex);
            DT1.Tile tile = dt1s.get(selectedId);
            if (tile != null) {
              return tile;
            }
          }
          
          // 如果找不到任何 floor tile，回退到默认值
          DT1.Tile tile = dt1s.get(0, 0, 0);
          if (tile == null && DEBUG_BUILD) {
            int totalTiles = 0;
            int floorTileCount = 0;
            for (IntMap.Entry<Array<DT1.Tile>> entry : dt1s.tiles.entries()) {
              Array<DT1.Tile> tileArray = entry.value;
              if (tileArray != null) {
                totalTiles += tileArray.size;
                int orientation = DT1.Tile.Index.orientation(entry.key);
                if (orientation == 0) floorTileCount++;
              }
            }
            Gdx.app.error(TAG, String.format("selectTerrainTile: No floor tile found! dt1s has %d tile types (%d floor types), %d total tiles", 
                dt1s.tiles.size, floorTileCount, totalTiles));
          }
          
          return tile;
        }
        
        // 注意：addSecondaryBorder 和相关方法已移到外部作为实例方法
        // 见下面的 addSecondaryBorder, initializeLvlSubDs1, testReplaceSubPreset, replaceSubPreset 方法
      };
    }

    // D2MOO_JAVA TileGrid 导出：若某关卡由 D2MOO 生成了瓦片则跳过本地 generateOutdoorRoom
    if (drlg != null) {
      D2MooTileApplier applier = new D2MooTileApplier();
      for (IntMap.Entry<DrlgLevel> e : drlgLevels) {
        if (e.value.grid != null && e.key >= LEVEL_BLOODMOOR && e.key <= LEVEL_TAMOEHIGHLAND) {
          applier.putGrid(e.key, e.value.grid);
        }
      }
      int[] outdoorLevelIds = { LEVEL_BLOODMOOR, LEVEL_COLDPLAINS, LEVEL_STONYFIELD };
      for (int levelId : outdoorLevelIds) {
        applier.resetLastExportedFloorCount();
        int n = DrlgExport.exportLevelTiles(drlg, levelId, applier);
        int exportedDt1Mask = DrlgExport.collectLevelDt1Mask(drlg, levelId);
        d2MooDt1Masks.put(levelId, exportedDt1Mask);
        int written = applier.getLastExportedFloorCount();
        boolean qualityPassed = written > 0
            && applier.getUniqueFloorIdCount() > 1
            && applier.getZeroTileIdCount() < written
            && applier.getExportedWallCount() > 0
            && applier.getUniqueWallIdCount() > 0
            && applier.getInvalidTileCount() == 0
            && applier.getOutOfBoundsCount() == 0
            && applier.getWallLayerOverflowCount() == 0
            && applier.getNonWallOrientationCount() == 0;
        boolean renderExportedFloors = Boolean.getBoolean("riiablo.drlg.renderExportedFloors");
        boolean acceptedForRendering = renderExportedFloors && qualityPassed;
        if (acceptedForRendering) {
          levelsFilledByExport.add(levelId);
        } else {
          // A rejected export must not poison any layer of the local fallback grid.
          // Rendering remains opt-in until D2MOO produces a complete tile set
          // and the exported wall/shadow layers are applied to Zone.
          DrlgLevel exportedLevel = drlgLevels.get(levelId);
          if (exportedLevel != null && exportedLevel.grid != null) {
            exportedLevel.grid.clearExportedTileIds();
          }
        }
        Gdx.app.log(TAG, String.format(
            "D2MOO_JAVA export: levelId=%d attemptedFloor=%d callbacks=%d writtenFloor=%d "
                + "ignoredLayer=%d missingGrid=%d outOfBounds=%d invalidTile=%d "
                + "wall=%d shadow=%d dt1Mask=0x%X qualityPassed=%s renderEnabled=%s acceptedForRendering=%s",
            levelId, n, applier.getCallbackCount(), written,
            applier.getIgnoredLayerCount(), applier.getMissingGridCount(),
            applier.getOutOfBoundsCount(), applier.getInvalidTileCount(),
            applier.getExportedWallCount(), applier.getExportedShadowCount(), exportedDt1Mask,
            qualityPassed, renderExportedFloors, acceptedForRendering)
            + String.format(" duplicatePosition=%d duplicateShadow=%d wallOverflow=%d"
                + " nonFloorOrientation=%d nonWallOrientation=%d nonShadowOrientation=%d"
                + " zeroTileId=%d uniqueFloorIds=%d uniqueWallIds=%d uniqueShadowIds=%d",
            applier.getDuplicatePositionCount(), applier.getDuplicateShadowCount(),
            applier.getWallLayerOverflowCount(),
            applier.getNonFloorOrientationCount(), applier.getNonWallOrientationCount(),
            applier.getNonShadowOrientationCount(), applier.getZeroTileIdCount(),
            applier.getUniqueFloorIdCount(), applier.getUniqueWallIdCount(),
            applier.getUniqueShadowIdCount()));
      }
      DrlgDrlg.freeDrlg(drlg);
      Act1D2MOOLayoutBridge.releaseDataTables();
    }

    // 打印所有区域的坐标范围总结，检查是否有重叠
    if (DEBUG_BUILD) {
      Gdx.app.debug(TAG, "=== Zone Bounds Summary ===");
      for (Zone zone : map.zones) {
        int minX = zone.x;
        int maxX = zone.x + zone.width;
        int minY = zone.y;
        int maxY = zone.y + zone.height;
        Gdx.app.debug(TAG, String.format("%s (id=%d): X[%d, %d] Y[%d, %d] (size: %dx%d)", 
            zone.level.LevelName, zone.level.Id, minX, maxX, minY, maxY, zone.width, zone.height));
      }
      
      // 检查重叠和连接性
      Gdx.app.debug(TAG, "=== Overlap & Connectivity Check ===");
      boolean hasOverlap = false;
      boolean hasPartialOverlap = false;
      
      for (int i = 0; i < map.zones.size; i++) {
        Zone zone1 = map.zones.get(i);
        int minX1 = zone1.x;
        int maxX1 = zone1.x + zone1.width;
        int minY1 = zone1.y;
        int maxY1 = zone1.y + zone1.height;
        
        for (int j = i + 1; j < map.zones.size; j++) {
          Zone zone2 = map.zones.get(j);
          int minX2 = zone2.x;
          int maxX2 = zone2.x + zone2.width;
          int minY2 = zone2.y;
          int maxY2 = zone2.y + zone2.height;
          
          // 检查是否有重叠：两个矩形在 X 和 Y 轴上都重叠
          boolean overlapX = !(maxX1 <= minX2 || maxX2 <= minX1);
          boolean overlapY = !(maxY1 <= minY2 || maxY2 <= minY1);
          
          // 完全重叠（X 和 Y 都重叠）
          if (overlapX && overlapY) {
            hasOverlap = true;
            Gdx.app.error(TAG, String.format("FULL OVERLAP DETECTED: %s (id=%d) overlaps with %s (id=%d)", 
                zone1.level.LevelName, zone1.level.Id, zone2.level.LevelName, zone2.level.Id));
            Gdx.app.error(TAG, String.format("  Zone1: X[%d, %d] Y[%d, %d]", minX1, maxX1, minY1, maxY1));
            Gdx.app.error(TAG, String.format("  Zone2: X[%d, %d] Y[%d, %d]", minX2, maxX2, minY2, maxY2));
          }
          // 部分重叠（只在 X 或 Y 轴重叠）
          else if (overlapX || overlapY) {
            hasPartialOverlap = true;
            String axis = overlapX ? "X" : "Y";
            Gdx.app.error(TAG, String.format("PARTIAL OVERLAP (%s-axis): %s (id=%d) overlaps with %s (id=%d)", 
                axis, zone1.level.LevelName, zone1.level.Id, zone2.level.LevelName, zone2.level.Id));
            Gdx.app.error(TAG, String.format("  Zone1: X[%d, %d] Y[%d, %d]", minX1, maxX1, minY1, maxY1));
            Gdx.app.error(TAG, String.format("  Zone2: X[%d, %d] Y[%d, %d]", minX2, maxX2, minY2, maxY2));
          }
          // 检查是否相邻（边界相接）
          else {
            boolean adjacentX = (maxX1 == minX2 || maxX2 == minX1) && !(maxY1 <= minY2 || maxY2 <= minY1);
            boolean adjacentY = (maxY1 == minY2 || maxY2 == minY1) && !(maxX1 <= minX2 || maxX2 <= minX1);
            boolean adjacentCorner = (maxX1 == minX2 || maxX2 == minX1) && (maxY1 == minY2 || maxY2 == minY1);
            
            if (adjacentX || adjacentY || adjacentCorner) {
              String connection = adjacentCorner ? "corner" : (adjacentX ? "X-axis" : "Y-axis");
              Gdx.app.debug(TAG, String.format("ADJACENT (%s): %s (id=%d) connects to %s (id=%d)", 
                  connection, zone1.level.LevelName, zone1.level.Id, zone2.level.LevelName, zone2.level.Id));
            }
          }
        }
      }
      
      if (!hasOverlap && !hasPartialOverlap) {
        Gdx.app.debug(TAG, "No overlaps detected - all zones are properly separated");
      } else if (hasPartialOverlap && !hasOverlap) {
        Gdx.app.debug(TAG, "Partial overlaps detected (single axis) - zones may need adjustment");
      }
      Gdx.app.debug(TAG, "=== End Zone Bounds Summary ===");
    }
    
    // 设置城镇和野外区域的入口连接
    // 注意：warp 目标覆盖必须无条件设置，否则 createWarps 时会用 Levels.txt 的 Vis（可能指向 Cold Plains）
    // VIS_5_42 的 mainIndex=5，覆盖为指向 Blood Moor（LEVEL_BLOODMOOR=2）
    map.addWarpDestinationOverride(LEVEL_ROGUEENCAMPMENT, 5, LEVEL_BLOODMOOR);
    
    Zone bloodMoorZone = null;
    for (Zone zone : map.zones) {
      if (!zone.town && zone.level.Id == LEVEL_BLOODMOOR) {
        bloodMoorZone = zone;
        break;
      }
    }
    
    if (townZone != null && bloodMoorZone != null) {
      townZone.setWarp(Map.ID.VIS_5_42, Map.ID.VIS_0_03);
      bloodMoorZone.setWarp(Map.ID.VIS_0_03, Map.ID.VIS_5_42);
      if (DEBUG_BUILD) {
        Gdx.app.debug(TAG, "Set warp connection between town and Blood Moor");
      }
    }

    // 添加高级功能：边界、路径、传送点、神殿等
    // 参考 D2MOD: DRLGOUTWILD_InitAct1OutdoorLevel
    if (DEBUG_BUILD) {
      Gdx.app.debug(TAG, "=== Placing Advanced Features ===");
    }
    for (Zone zone : map.zones) {
      if (!zone.town && !levelsFilledByExport.contains(zone.level.Id)) {
        if (DEBUG_BUILD) {
          Gdx.app.debug(TAG, String.format("Processing features for: %s (id=%d)", 
              zone.level.LevelName, zone.level.Id));
        }
        
        // 放置边界（Act1 使用 LvlSub/LvlPrest 驱动的实现）
        OutdoorFeatures.placeBordersAct1(zone, seed);

        // 使用 LvlSub 细节块（Swamp / Stone / Puddles / Trees 等）丰富野外
        if (zone.level.Id >= LEVEL_BLOODMOOR && zone.level.Id <= LEVEL_TAMOEHIGHLAND) {
          OutdoorFeatures.placeWildDetailsAct1(zone, seed);
        }
        
        // 放置路径（仅对特定区域）
        if (zone.level.Id >= LEVEL_BLOODMOOR && zone.level.Id <= LEVEL_TAMOEHIGHLAND) {
          if (DEBUG_BUILD) {
            Gdx.app.debug(TAG, String.format("  → Placing paths in %s", zone.level.LevelName));
          }
          OutdoorFeatures.placePaths(zone, seed);
        }
        
        // 放置传送点（仅对特定区域）
        if (zone.level.Id >= LEVEL_COLDPLAINS && zone.level.Id <= LEVEL_BLACKMARSH) {
          if (DEBUG_BUILD) {
            Gdx.app.debug(TAG, String.format("  → Placing waypoint in %s", zone.level.LevelName));
          }
          OutdoorFeatures.placeWaypoint(zone, seed);
        }
        
        // 放置神殿（5个）
        if (zone.level.Id >= LEVEL_BLOODMOOR && zone.level.Id <= LEVEL_TAMOEHIGHLAND) {
          if (DEBUG_BUILD) {
            Gdx.app.debug(TAG, String.format("  → Placing 5 shrines in %s", zone.level.LevelName));
          }
          OutdoorFeatures.placeShrines(zone, seed, 5);
        }
      }
    }
    
    // D2MOD: DRLGTILESUB_AddSecondaryBorder - 将在 Map.generate() 完成后统一调用
    // 注意：addSecondaryBorder 现在在 Map.generate() 完成后调用，确保所有 zone.generate() 都已经完成
    // 这样可以避免 applyTileGridToZone 覆盖 addSecondaryBorder 的更改
    if (DEBUG_BUILD) {
      Gdx.app.debug(TAG, "=== Act1 Map Generation Completed ===");
      Gdx.app.debug(TAG, "NOTE: addSecondaryBorder will be called after Map.generate() completes");
    }
  }

  static int toLocalGridCoordinate(int localTileCoordinate, int gridSize) {
    if (localTileCoordinate < 0 || gridSize <= 0) return -1;
    return localTileCoordinate / gridSize;
  }

  static int toLocalTileIndex(int localTileX, int localTileY, int zoneTilesX, int zoneTilesY) {
    if (localTileX < 0 || localTileX >= zoneTilesX
        || localTileY < 0 || localTileY >= zoneTilesY) return -1;
    return Zone.index(zoneTilesX, localTileX, localTileY);
  }

  /**
   * 在所有 zone 生成完成后统一调用 addSecondaryBorder
   * 这个方法应该在 Map.generate() 完成后调用，确保所有 zone.generate() 都已经完成
   */
  public void applySecondaryBordersAfterZoneGeneration(Map map, int seed) {
    if (DEBUG_BUILD) {
      Gdx.app.debug(TAG, "=== Applying LvlSub Secondary Borders After Zone Generation ===");
    }
    
    // 先按 DrlgTileSub 做“子预设替换”（最小可用：仅 Act1 wilderness/Blood Moor）
    applyTileSubstitutionsAfterZoneGeneration(map, seed);

    // 在所有 zone 生成完成后，统一调用 addSecondaryBorder
    for (Zone zone : map.zones) {
      // D2MOO already placed Act1 primary and secondary border presets before
      // RoomEx export. Running the local approximation again corrupts that
      // footprint and can reintroduce tiles into intentional void cells.
      if (!zone.town && !levelsFilledByExport.contains(zone.level.Id)) {
        DrlgLevel drlgLevel = drlgLevels.get(zone.level.Id);
        if (drlgLevel != null && drlgLevel.subType != -1) {
          DrlgGrid drlgGrid = drlgLevel.drlgGrid;
          addSecondaryBorder(zone, drlgLevel, drlgGrid, seed);
        }
      }
    }
    
    if (DEBUG_BUILD) {
      Gdx.app.debug(TAG, "=== LvlSub Secondary Borders Applied After Zone Generation ===");
    }
  }

  /**
   * D2MOO_JAVA: DrlgTileSub（DrlgTileSub.cpp）的最小可用迁移入口。
   *
   * 目标：
   * - 在整张 zone 生成完成后，基于 LvlSub 的 DS1 substitution groups 执行若干次“按簇替换”
   * - 使用现有的 {@link #testReplaceSubPreset} / {@link #replaceSubPreset} 实现替换逻辑
   *
   * 范围（最小可用）：
   * - 仅 Act1 wilderness（Blood Moor）
   */
  private void applyTileSubstitutionsAfterZoneGeneration(Map map, int seed) {
    // 暂时禁用 TileSub 子预设替换流程，先恢复稳定的基础地形生成，
    // 避免当前未完全对齐的逻辑引入“随机拼贴”和不自然过渡。
    // 后续将在完全按 D2MOD / D2MOO_JAVA 对齐后再重新启用。
    if (true) return;

    for (Zone zone : map.zones) {
      if (zone == null || zone.town || zone.level == null) continue;
      if (zone.level.Id != LEVEL_BLOODMOOR) continue; // 先只做 Blood Moor，避免影响其他 zone

      DrlgLevel drlgLevel = drlgLevels.get(zone.level.Id);
      if (drlgLevel == null || drlgLevel.subType == -1) continue;
      int subType = drlgLevel.subType;
      int subTheme = drlgLevel.levelsEntry != null ? Math.min(Math.max(0, drlgLevel.levelsEntry.SubTheme), 4) : 0;

      LvlSub.Entry[] lvlSubEntries = Riiablo.files.LvlSub.getByType(subType);
      if (lvlSubEntries == null || lvlSubEntries.length == 0) continue;

      DrlgGrid drlgGrid = drlgLevel.drlgGrid;

      // 每个 LvlSub 记录分别做试验，尽量贴近 DrlgTileSub 的“有限次尝试”
      for (int entryIndex = 0; entryIndex < lvlSubEntries.length; entryIndex++) {
        LvlSub.Entry lvlSubEntry = lvlSubEntries[entryIndex];
        if (lvlSubEntry == null) continue;
        if (lvlSubEntry.Type != subType) continue;
        if (lvlSubEntry.File == null || lvlSubEntry.File.isEmpty()) continue;

        int prob = (lvlSubEntry.Prob != null && subTheme < lvlSubEntry.Prob.length) ? lvlSubEntry.Prob[subTheme] : 0;
        int trials = (lvlSubEntry.Trials != null && subTheme < lvlSubEntry.Trials.length) ? lvlSubEntry.Trials[subTheme] : 0;
        int max = (lvlSubEntry.Max != null && subTheme < lvlSubEntry.Max.length) ? lvlSubEntry.Max[subTheme] : 0;
        if (trials <= 0 || max == 0) continue;

        DS1 ds1 = initializeLvlSubDs1(lvlSubEntry);
        if (ds1 == null || ds1.groups == null || ds1.groups.length == 0) continue;

        // 生成可放置点（grid 坐标系），并打乱
        // 注意：这里的 gridSize 语义按 DrlgTileSub 的“dwGridSize”（簇步长）理解。
        // 为了最小可用（并避免 replaceSubPreset 造成稀疏点阵），先强制使用 1 tile 粒度。
        final int gridSize = 1;
        int zoneGridWidth = zone.tilesX / gridSize;
        int zoneGridHeight = zone.tilesY / gridSize;

        // 预先构造坐标池（每个 entry 一份，避免每次试验 O(n) 重建）
        int nArea = zoneGridWidth * zoneGridHeight;
        if (nArea <= 0) continue;
        int[] coordX = new int[nArea];
        int[] coordY = new int[nArea];
        for (int i = 0; i < nArea; i++) {
          coordX[i] = i % zoneGridWidth;
          coordY[i] = i / zoneGridWidth;
        }
        MathUtils.random.setSeed(seed ^ (zone.level.Id * 10007) ^ (entryIndex * 1009));
        for (int i = 0; i < nArea; i++) {
          int a = Math.abs(MathUtils.random.nextInt()) % nArea;
          int b = Math.abs(MathUtils.random.nextInt()) % nArea;
          int tx = coordX[a], ty = coordY[a];
          coordX[a] = coordX[b]; coordY[a] = coordY[b];
          coordX[b] = tx;        coordY[b] = ty;
        }

        int successful = 0;
        int attempts = 0;
        int maxReplacements = max > 0 ? max : 1;

        // 每次成功替换后仍继续尝试，直到达到 Max 或耗尽 Trials
        // DrlgTileSub 里 Prob/Trials/Max 的组合更复杂；这里先做“门控 + 有限次尝试 + Max 上限”
        int coordIdx = 0;
        while (attempts < trials && successful < maxReplacements && coordIdx < nArea) {
          attempts++;

          if (prob > 0) {
            int rollSeed = (seed * 31 + zone.level.Id * 131 + attempts * 17) ^ (entryIndex * 997);
            MathUtils.random.setSeed(rollSeed);
            int roll = Math.abs(MathUtils.random.nextInt() % 100);
            if (roll >= prob) {
              continue;
            }
          }

          // 选择 group（偏向更大，但随机）
          MathUtils.random.setSeed(seed ^ (entryIndex * 19937) ^ (attempts * 37));
          DS1.Group substGroup = ds1.groups[Math.abs(MathUtils.random.nextInt()) % ds1.groups.length];
          if (substGroup == null) continue;

          // 取下一个候选坐标（grid 单位）
          int gx = coordX[coordIdx];
          int gy = coordY[coordIdx];
          coordIdx++;

          if (testReplaceSubPreset(zone, drlgLevel, drlgGrid, gx, gy, substGroup, ds1, lvlSubEntry, gridSize)) {
            // 最小可用：不做 groupOffset 扫描，直接 0
            replaceSubPreset(zone, drlgLevel, drlgGrid, gx, gy, substGroup, ds1, lvlSubEntry, gridSize, 0);
            successful++;
          }
        }
      }
    }
  }
  
  /**
   * D2MOD: DRLGTILESUB_AddSecondaryBorder - 添加次要边界和细节替换
   * 
   * 参考 D2MOO: DRLGTILESUB_AddSecondaryBorder
   * 遍历所有匹配的 LvlSub 记录，加载它们的 DS1 文件，然后尝试替换预设块
   * 
   * 注意：这个方法应该在所有 8x8 网格生成完成后统一调用，而不是每个网格都调用
   */
  private void addSecondaryBorder(Zone zone, DrlgLevel drlgLevel, DrlgGrid drlgGrid, int seed) {
    
    // 减少调试日志，避免性能问题
    // if (DEBUG_BUILD) {
    //   Gdx.app.debug(TAG, String.format("addSecondaryBorder: zone=%s (id=%d), drlgLevel=%s, subType=%d",
    //       zone.level.LevelName, zone.level.Id, 
    //       drlgLevel != null ? "not null" : "null",
    //       drlgLevel != null ? drlgLevel.subType : -1));
    // }
    
    if (drlgLevel == null || drlgLevel.subType == -1) {
      // 减少调试日志，避免性能问题
      // if (DEBUG_BUILD) {
      //   Gdx.app.debug(TAG, String.format("addSecondaryBorder: skipped zone %s (drlgLevel=%s, subType=%d)",
      //       zone.level.LevelName, drlgLevel != null ? "not null" : "null",
      //       drlgLevel != null ? drlgLevel.subType : -1));
      // }
      return;
    }
    
    int subType = drlgLevel.subType;
    LvlSub.Entry[] lvlSubEntries = Riiablo.files.LvlSub.getByType(subType);
    
    // 减少调试日志，避免性能问题
    // if (DEBUG_BUILD) {
    //   Gdx.app.debug(TAG, String.format("addSecondaryBorder: zone=%s, subType=%d, found %d LvlSub entries",
    //       zone.level.LevelName, subType, lvlSubEntries != null ? lvlSubEntries.length : 0));
    // }
    
    if (lvlSubEntries == null || lvlSubEntries.length == 0) {
      // 减少调试日志，避免性能问题
      // if (DEBUG_BUILD) {
      //   Gdx.app.debug(TAG, String.format("addSecondaryBorder: no LvlSub entries found for subType=%d", subType));
      // }
      return;
    }
    
    // 遍历所有匹配的 LvlSub 记录（对应 D2MOO 的 while (pLvlSubTxtRecord->dwType == a1->nLvlSubId)）
    for (LvlSub.Entry lvlSubEntry : lvlSubEntries) {
      if (lvlSubEntry == null || lvlSubEntry.Type != subType) {
        continue;
      }
      
      // 加载或获取缓存的 DS1 文件（对应 DRLGTILESUB_InitializeDrlgFile）
      DS1 lvlSubDs1 = initializeLvlSubDs1(lvlSubEntry);
      if (lvlSubDs1 == null || lvlSubDs1.numGroups == 0) {
        continue;
      }
      
      // 检查是否有 substitution groups（对应 pLvlSubTxtRecord->pDrlgFile->nSubstGroups > 0）
      // 注意：DS1 的 groups 对应 D2MOO 的 substitution groups
      if (lvlSubDs1.groups == null || lvlSubDs1.groups.length == 0) {
        if (DEBUG_BUILD) {
          Gdx.app.debug(TAG, String.format("addSecondaryBorder: LvlSub %s has no groups in DS1 file %s",
              lvlSubEntry.Name, lvlSubEntry.File));
        }
        continue;
      }
      
      // 选择 substitution group（对应 D2MOO 的 nRand 计算）
      int nRand = 0;
      if (lvlSubEntry.BordType == 0) {
        // 随机选择一个 group（使用 zone 的 level ID 作为随机种子的一部分）
        MathUtils.random.setSeed(seed + zone.level.Id * 1000);
        nRand = Math.abs(MathUtils.random.nextInt()) % lvlSubDs1.groups.length;
      } else {
        // BordType != 0 时使用第一个 group
        nRand = 0;
      }
      
      // 遍历所有 substitution groups（对应 D2MOO 的 for (int j = 0; j < pLvlSubTxtRecord->pDrlgFile->nSubstGroups; ++j)）
      for (int j = 0; j < lvlSubDs1.groups.length; j++) {
        DS1.Group substGroup = lvlSubDs1.groups[(nRand + j) % lvlSubDs1.groups.length];
        
        // 检查是否为野外区域（对应 D2MOO 的 bWilderness 判断）
        boolean bWilderness = (zone.level.Id >= LEVEL_BLOODMOOR && zone.level.Id <= LEVEL_TAMOEHIGHLAND);
        
        // 计算可放置区域（对应 D2MOO 的 nWidth/nHeight 计算）
        int gridSize = lvlSubEntry.GridSize > 0 ? lvlSubEntry.GridSize : 1;
        int nOffset = (subType == 1 && bWilderness) ? -1 : 1; // 简化：假设某些条件下需要偏移
        
        // 计算可放置的宽度和高度（以 gridSize 为单位）
        // 注意：这里使用整个 zone 的尺寸，而不是单个网格
        int zoneGridWidth = zone.tilesX / gridSize;
        int zoneGridHeight = zone.tilesY / gridSize;
        int nWidth = nOffset + zoneGridWidth - substGroup.width;
        int nHeight = 1 + zoneGridHeight - substGroup.height;
        
        int nArea = nWidth * nHeight;
        if (nArea <= 0) {
          continue;
        }
        
        // 检查是否为小野外区域（对应 D2MOO 的 bSmallWilderness）
        boolean bSmallWilderness = (subType == 1 && bWilderness && nWidth < 6 && nHeight < 6);
        
        // 生成随机坐标列表（对应 D2MOO 的 pCoord 数组和随机交换）
        int[] coordX = new int[nArea];
        int[] coordY = new int[nArea];
        for (int i = 0; i < nArea; i++) {
          coordX[i] = i % nWidth;
          coordY[i] = i / nWidth;
        }
        
        // 随机打乱坐标（对应 D2MOO 的 Fisher-Yates shuffle）
        MathUtils.random.setSeed(seed + zone.level.Id * 10000 + j * 100);
        for (int i = 0; i < nArea; i++) {
          int nRand1 = Math.abs(MathUtils.random.nextInt()) % nArea;
          int nRand2 = Math.abs(MathUtils.random.nextInt()) % nArea;
          // 交换
          int tempX = coordX[nRand1];
          int tempY = coordY[nRand1];
          coordX[nRand1] = coordX[nRand2];
          coordY[nRand1] = coordY[nRand2];
          coordX[nRand2] = tempX;
          coordY[nRand2] = tempY;
        }
        
        // 尝试替换预设块（对应 D2MOO 的 DRLGTILESUB_TestReplaceSubPreset 和 DRLGTILESUB_ReplaceSubPreset）
        // 性能优化：限制尝试次数，避免在大型 zone 上遍历所有位置
        // 使用 LvlSub 的 Trials[0] 或 Max[0] 来限制尝试次数
        int maxTrials = nArea;
        if (lvlSubEntry.Trials != null && lvlSubEntry.Trials.length > 0 && lvlSubEntry.Trials[0] > 0) {
          maxTrials = Math.min(nArea, lvlSubEntry.Trials[0]);
        } else if (lvlSubEntry.Max != null && lvlSubEntry.Max.length > 0 && lvlSubEntry.Max[0] > 0) {
          maxTrials = Math.min(nArea, lvlSubEntry.Max[0] * 10); // Max 是数量，乘以 10 作为尝试次数上限
        } else {
          // 默认限制：对于大型 zone，最多尝试 200 次
          maxTrials = Math.min(nArea, 200);
        }
        
        boolean bBreak = false;
        int successfulReplacements = 0;
        int maxReplacements = (lvlSubEntry.Max != null && lvlSubEntry.Max.length > 0 && lvlSubEntry.Max[0] > 0) 
            ? lvlSubEntry.Max[0] : 1; // 默认最多替换 1 次
        
        for (int i = 0; i < nArea && successfulReplacements < maxReplacements; i++) {
          int nX = coordX[i];
          int nY = coordY[i];
          
          // 跳过小野外区域的中心点（对应 D2MOO 的 if (!bSmallWilderness || nX != 2 || nY != 2)）
          if (bSmallWilderness && nX == 2 && nY == 2) {
            continue;
          }
          
          // 性能优化：限制尝试次数
          if (i >= maxTrials) {
            break;
          }
          
          // 测试是否可以替换（对应 DRLGTILESUB_TestReplaceSubPreset）
          if (testReplaceSubPreset(zone, drlgLevel, drlgGrid, nX, nY, substGroup, lvlSubDs1, lvlSubEntry, gridSize)) {
            // 执行替换（对应 DRLGTILESUB_ReplaceSubPreset）
            int groupOffset = Math.abs(MathUtils.random.nextInt()) % (substGroup.width + 1);
            replaceSubPreset(zone, drlgLevel, drlgGrid, nX, nY, substGroup, lvlSubDs1, lvlSubEntry, gridSize, groupOffset);
            successfulReplacements++;
            
            if (lvlSubEntry.BordType == 0) {
              // BordType == 0: 找到一个替换后立即退出
              bBreak = true;
              break;
            } else if (lvlSubEntry.BordType == 1) {
              // BordType == 1: 找到一个替换后退出当前 group 循环
              break;
            }
            // BordType == 2 或其他: 继续尝试，直到达到 maxReplacements
          }
        }
        
        if (bBreak) {
          break;
        }
      }
    }
  }
  
  /**
   * 加载或获取缓存的 LvlSub DS1 文件（对应 D2MOO 的 DRLGTILESUB_InitializeDrlgFile）
   */
  private DS1 initializeLvlSubDs1(LvlSub.Entry entry) {
    if (entry == null || entry.File == null || entry.File.isEmpty()) {
      return null;
    }
    
    // 使用 File 路径作为缓存 key
    int cacheKey = entry.File.hashCode();
    DS1 cached = lvlSubDs1Cache.get(cacheKey);
    if (cached != null) {
      return cached;
    }
    
    // 加载 DS1 文件
    try {
      com.badlogic.gdx.files.FileHandle fileHandle = Riiablo.mpqs.resolve("data\\global\\tiles\\" + entry.File);
      if (!fileHandle.exists()) {
        if (DEBUG_BUILD) {
          Gdx.app.debug(TAG, String.format("initializeLvlSubDs1: DS1 file not found: %s", entry.File));
        }
        return null;
      }
      
      DS1 ds1 = DS1.loadFromFile(fileHandle);
      lvlSubDs1Cache.put(cacheKey, ds1);
      
      if (DEBUG_BUILD) {
        Gdx.app.debug(TAG, String.format("initializeLvlSubDs1: loaded DS1 %s (%dx%d, %d groups)",
            entry.File, ds1.width, ds1.height, ds1.numGroups));
      }
      
      return ds1;
    } catch (Exception e) {
      if (DEBUG_BUILD) {
        Gdx.app.error(TAG, String.format("initializeLvlSubDs1: failed to load DS1 %s", entry.File), e);
      }
      return null;
    }
  }
  
  /**
   * D2MOD: DRLGTILESUB_TestReplaceSubPreset - 测试是否可以替换预设块
   * 
   * 检查指定位置是否满足替换条件（检查 floor/wall flags 等）
   */
  private boolean testReplaceSubPreset(Zone zone, DrlgLevel drlgLevel, DrlgGrid drlgGrid,
      int gridX, int gridY, DS1.Group substGroup, DS1 lvlSubDs1, LvlSub.Entry lvlSubEntry, int gridSize) {
    
    // gridX/gridY 是“grid 坐标”（对应 D2MOO 的 nX/nY），需要转换为 tile 坐标。
    // 对齐到 gridSize 边界（对应 v9 = a1 - a1 % dwGridSize），这里 a1/a2 是 grid 坐标。
    int alignedGX = gridSize > 0 ? (gridX - (gridX % gridSize)) : gridX;
    int alignedGY = gridSize > 0 ? (gridY - (gridY % gridSize)) : gridY;
    int baseTileX = alignedGX * gridSize;
    int baseTileY = alignedGY * gridSize;
    
    // 检查 substitution group 覆盖的区域是否满足替换条件
    // 简化版：检查该区域是否已经有预设（如果有预设，通常不替换）
    if (drlgGrid != null && gridSize > 0 && drlgGrid.inBounds(alignedGX / gridSize, alignedGY / gridSize)) {
      DrlgGrid.PackedGrid2Info grid2Info = drlgGrid.grid2Info[alignedGY / gridSize][alignedGX / gridSize];
      if (grid2Info.bHasPickedFile) {
        // 已有预设，通常不替换（但可以根据 LvlSub 的规则决定）
        return false;
      }
    }
    
    // 简化版：检查该区域是否在 zone 范围内且为空
    // 完整实现需要检查 DS1 中的 floor/wall flags 是否匹配
    if (baseTileX < 0 || baseTileX >= zone.tilesX || baseTileY < 0 || baseTileY >= zone.tilesY) {
      return false;
    }
    
    // 检查覆盖区域是否都在 zone 范围内
    int groupWidthTiles = substGroup.width * gridSize;
    int groupHeightTiles = substGroup.height * gridSize;
    if (baseTileX + groupWidthTiles > zone.tilesX || baseTileY + groupHeightTiles > zone.tilesY) {
      return false;
    }
    
    // 简化版：检查该区域是否有地板（如果有地板，可以替换）
    // 完整实现需要检查 DS1 中的 floor/wall flags 是否与当前区域匹配
    int tileIndex = Zone.index(zone.tilesX, baseTileX, baseTileY);
    DT1.Tile[] floorLayer = zone.getLayer(Map.FLOOR_OFFSET);
    if (floorLayer == null || floorLayer[tileIndex] == null) {
      return false; // 没有地板，不能替换
    }
    
    return true;
  }
  
  /**
   * D2MOD: DRLGTILESUB_ReplaceSubPreset - 替换预设块
   * 
   * 根据 LvlSub DS1 文件中的 floor/wall flags 替换指定区域的瓦片
   */
  private void replaceSubPreset(Zone zone, DrlgLevel drlgLevel, DrlgGrid drlgGrid,
      int gridX, int gridY, DS1.Group substGroup, DS1 lvlSubDs1, LvlSub.Entry lvlSubEntry,
      int gridSize, int groupOffset) {
    
    // gridX/gridY 是 grid 坐标，替换时要转换为 tile 坐标
    int alignedGX = gridSize > 0 ? (gridX - (gridX % gridSize)) : gridX;
    int alignedGY = gridSize > 0 ? (gridY - (gridY % gridSize)) : gridY;
    int baseTileX = alignedGX * gridSize;
    int baseTileY = alignedGY * gridSize;
    
    // 计算 DS1 中的起始位置（对应 D2MOO 的 a6 参数）
    int ds1StartX = substGroup.x + groupOffset;
    int ds1StartY = substGroup.y;
    
    // 获取 zone 的 DT1s（用于查找瓦片）
    DT1s dt1s = zone.dt1s;
    if (dt1s == null) {
      if (DEBUG_BUILD && DEBUG_REPLACE_SUB_PRESET_TILES) {
        Gdx.app.debug(TAG, String.format("replaceSubPreset: zone %s has no dt1s", zone.level.LevelName));
      }
      return;
    }
    
    int tilesReplaced = 0;
    
    // 遍历 substitution group 覆盖的区域
    for (int j = 0; j < substGroup.height; j++) {
      for (int i = 0; i < substGroup.width; i++) {
        int tileX = baseTileX + i * gridSize;
        int tileY = baseTileY + j * gridSize;
        
        // 检查是否在 zone 范围内
        if (tileX < 0 || tileX >= zone.tilesX || tileY < 0 || tileY >= zone.tilesY) {
          continue;
        }
        
        int tileIndex = Zone.index(zone.tilesX, tileX, tileY);
        
        // 从 DS1 中读取对应位置的 floor/wall 信息
        int ds1X = ds1StartX + i;
        int ds1Y = ds1StartY + j;
        
        // 检查 DS1 坐标是否有效
        if (ds1X < 0 || ds1X >= lvlSubDs1.width || ds1Y < 0 || ds1Y >= lvlSubDs1.height) {
          continue;
        }
        
        // 从 DS1 的 floor 层读取瓦片（简化版：只读取第一层 floor）
        // 完整实现需要解析 DS1 的 floor/wall flags 并应用替换规则
        if (lvlSubDs1.numFloors > 0 && lvlSubDs1.floors != null) {
          // DS1 的 floors 数组是一维数组，通过坐标计算索引
          // floorLine = width * numFloors
          // 索引 = y * floorLine + x * numFloors + layerIndex
          int floorLine = lvlSubDs1.width * lvlSubDs1.numFloors;
          int floorIndex = ds1Y * floorLine + ds1X * lvlSubDs1.numFloors + 0; // 使用第一层 floor
          
          if (floorIndex >= 0 && floorIndex < lvlSubDs1.floorLen) {
            DS1.Cell floorCell = lvlSubDs1.floors[floorIndex];
            if (floorCell != null && floorCell.mainIndex >= 0 && floorCell.subIndex >= 0) {
              // 根据 floorCell 的 mainIndex/subIndex 获取 DT1 Tile
              // 使用 Orientation.FLOOR (0) 作为 orientation
              DT1.Tile replacementTile = dt1s.get(Orientation.FLOOR, floorCell.mainIndex, floorCell.subIndex);
              
              if (replacementTile != null) {
                // 应用替换：将瓦片写入 zone 的 floor 层
                DT1.Tile[] floorLayer = zone.getLayer(Map.FLOOR_OFFSET);
                if (floorLayer != null) {
                  floorLayer[tileIndex] = replacementTile;
                  tilesReplaced++;
                  
                  // 同时更新 TileGrid（如果存在）- 这样 applyTileGridToZone 会应用这些更改
                  if (drlgLevel != null && drlgLevel.grid != null) {
                    TileGrid grid = drlgLevel.grid;
                    // 注意：TileGrid 的坐标是 tile 单位，而 zone 的坐标也是 tile 单位
                    // 但需要确保坐标在 TileGrid 范围内
                    if (tileX >= 0 && tileX < grid.width && tileY >= 0 && tileY < grid.height) {
                      // 使用 tile ID 作为 floor ID
                      int tileId = replacementTile.id;
                      // 注意：tileId 可能为 0，这是有效的（某些 tile 的 ID 就是 0）
                      // 但如果 tileId 为 0 且 mainIndex/subIndex 不为 0，可能是获取 tile 失败
                      if (tileId == 0 && (floorCell.mainIndex != 0 || floorCell.subIndex != 0)) {
                        // 可能是获取 tile 失败，但 tileId 恰好为 0，这种情况需要特殊处理
                        // 暂时跳过，不更新 TileGrid
                        if (DEBUG_BUILD && DEBUG_REPLACE_SUB_PRESET_TILES && tilesReplaced <= 5) {
                          Gdx.app.debug(TAG, String.format("replaceSubPreset: WARNING tileId=0 for mainIndex=%d, subIndex=%d at zone[%d,%d]",
                              floorCell.mainIndex, floorCell.subIndex, tileX, tileY));
                        }
                      } else {
                        grid.floorIds[tileY][tileX] = tileId;
                        
                        if (DEBUG_REPLACE_SUB_PRESET_TILES && DEBUG_REPLACE_SUB_PRESET_TILES && tilesReplaced <= 5) {
                          Gdx.app.debug(TAG, String.format("replaceSubPreset: updated TileGrid[%d,%d] = %d (mainIndex=%d, subIndex=%d)",
                              tileY, tileX, tileId, floorCell.mainIndex, floorCell.subIndex));
                        }
                      }
                    }
                  }
                }
              } else if (DEBUG_BUILD && DEBUG_REPLACE_SUB_PRESET_TILES && tilesReplaced <= 5) {
                // 只记录前几个失败的案例，避免日志过多
                Gdx.app.debug(TAG, String.format("replaceSubPreset: no tile found for mainIndex=%d, subIndex=%d at zone[%d,%d]",
                    floorCell.mainIndex, floorCell.subIndex, tileX, tileY));
              }
            }
          }
        }
      }
    }
    
    if (DEBUG_BUILD && DEBUG_REPLACE_SUB_PRESET_TILES && tilesReplaced > 0) {
      Gdx.app.debug(TAG, String.format("replaceSubPreset: replaced %d tiles at grid[%d,%d] using LvlSub %s group[%d,%d,%dx%d]",
          tilesReplaced, gridX, gridY, lvlSubEntry.Name, substGroup.x, substGroup.y, substGroup.width, substGroup.height));
    }
  }

  /**
   * 调试用：按 sampleStep 采样输出指定关卡的 TileGrid（只看 DRLG 影子数据，不看 Zone）。
   *
   * 建议：
   * - 先在游戏里跑一圈，让对应 level 的区域被生成（Zone.generate 被调用，影子写入完成）；
   * - 然后通过调试代码或命令调用：
   *     Act1MapBuilderD2MOD.INSTANCE.debugDumpTileGrid(LEVEL_BLOODMOOR, 4);
   */
  public void debugDumpTileGrid(int levelId, int sampleStep) {
    if (!DEBUG_BUILD) return;
    if (sampleStep <= 0) sampleStep = 1;

    DrlgLevel drlgLevel = drlgLevels.get(levelId);
    if (drlgLevel == null) {
      Gdx.app.debug(TAG, "debugDumpTileGrid: DrlgLevel not found for levelId=" + levelId);
      return;
    }

    TileGrid grid = drlgLevel.grid;
    if (grid == null) {
      Gdx.app.debug(TAG, "debugDumpTileGrid: TileGrid is null for levelId=" + levelId);
      return;
    }

    Gdx.app.debug(TAG, String.format(
        "=== TileGrid Dump === levelId=%d, name=%s, size=%dx%d, step=%d",
        drlgLevel.levelId,
        drlgLevel.levelsEntry != null ? drlgLevel.levelsEntry.LevelName : "UNKNOWN",
        grid.width, grid.height,
        sampleStep));

    int nonEmpty = 0;
    int totalChecked = 0;
    for (int y = 0; y < grid.height; y += sampleStep) {
      StringBuilder row = new StringBuilder(256);
      for (int x = 0; x < grid.width; x += sampleStep) {
        totalChecked++;
        int id = grid.floorIds[y][x];
        if (id != -1) nonEmpty++;
        // -1 用 '.' 表示，其它直接输出 id（压成宽度 4，方便对齐）
        if (id == -1) {
          row.append("   .");
        } else {
          row.append(String.format("%4d", id));
        }
      }
      Gdx.app.debug(TAG, String.format("y=%3d: %s", y, row.toString()));
    }

    Gdx.app.debug(TAG, String.format(
        "=== TileGrid Dump Done === levelId=%d, nonEmptyTiles(sampled)=%d/%d, grid size=%dx%d",
        drlgLevel.levelId, nonEmpty, totalChecked, grid.width, grid.height));
    
    // 额外检查：统计整个 TileGrid 中非空瓦片的总数（不采样）
    int totalNonEmpty = 0;
    for (int y = 0; y < grid.height; y++) {
      for (int x = 0; x < grid.width; x++) {
        if (grid.floorIds[y][x] != -1) {
          totalNonEmpty++;
        }
      }
    }
    if (totalNonEmpty > 0) {
      Gdx.app.debug(TAG, String.format("TileGrid total non-empty tiles: %d/%d (%.1f%%)",
          totalNonEmpty, grid.width * grid.height, 
          100.0 * totalNonEmpty / (grid.width * grid.height)));
    }
  }

  /**
   * 调试用：对比指定 Zone 的地板层与对应 TileGrid 中的影子写入值。
   *
   * 注意：
   * - 需要在 Zone.generate 之后调用（例如角色已经跑进对应区域）；
   * - 目前 TileGrid 只在我们自己的 generator 中写入，不包含 LvlSub / 边界 / 路径等后处理修改；
   *   所以这些后处理如果改动了地板，会被统计为“不一致”，属于预期现象。
   */
  public void debugCompareTileGridWithZone(Zone zone, int sampleStep) {
    if (!DEBUG_BUILD) return;
    if (zone == null) {
      Gdx.app.debug(TAG, "debugCompareTileGridWithZone: zone is null");
      return;
    }
    if (sampleStep <= 0) sampleStep = 1;

    DrlgLevel drlgLevel = drlgLevels.get(zone.level.Id);
    if (drlgLevel == null) {
      Gdx.app.debug(TAG, String.format(
          "debugCompareTileGridWithZone: no DrlgLevel for zone %s (id=%d)",
          zone.level.LevelName, zone.level.Id));
      return;
    }

    TileGrid grid = drlgLevel.grid;
    if (grid == null) {
      Gdx.app.debug(TAG, String.format(
          "debugCompareTileGridWithZone: TileGrid is null for zone %s (id=%d)",
          zone.level.LevelName, zone.level.Id));
      return;
    }

    DT1.Tile[] floorLayer = zone.getLayer(Map.FLOOR_OFFSET);
    if (floorLayer == null) {
      Gdx.app.debug(TAG, String.format(
          "debugCompareTileGridWithZone: floor layer is null for zone %s (id=%d)",
          zone.level.LevelName, zone.level.Id));
      return;
    }

    int width = Math.min(grid.width, zone.tilesX);
    int height = Math.min(grid.height, zone.tilesY);

    Gdx.app.debug(TAG, String.format(
        "=== TileGrid vs Zone Floor === zone=%s(id=%d), grid=%dx%d, zoneTiles=%dx%d, step=%d",
        zone.level.LevelName, zone.level.Id,
        grid.width, grid.height,
        zone.tilesX, zone.tilesY,
        sampleStep));

    int checked = 0;
    int mismatches = 0;
    int loggedMismatches = 0;
    final int MAX_LOG_MISMATCHES = 32;

    for (int y = 0; y < height; y += sampleStep) {
      for (int x = 0; x < width; x += sampleStep) {
        int gridId = grid.floorIds[y][x];

        int tileIndex = Zone.index(zone.tilesX, x, y);
        int floorId = -1;
        if (tileIndex >= 0 && tileIndex < floorLayer.length) {
          DT1.Tile floor = floorLayer[tileIndex];
          if (floor != null) {
            floorId = floor.id;
          }
        }

        checked++;
        if (gridId != floorId) {
          mismatches++;
          if (loggedMismatches < MAX_LOG_MISMATCHES) {
            Gdx.app.debug(TAG, String.format(
                "  MISMATCH at (%d,%d): gridId=%d, floorId=%d (tileIndex=%d)",
                x, y, gridId, floorId, tileIndex));
            loggedMismatches++;
          }
        }
      }
    }

    Gdx.app.debug(TAG, String.format(
        "=== Compare Done === checked=%d, mismatches=%d (logged %d)",
        checked, mismatches, loggedMismatches));
  }

  /**
   * 实验性：在 Blood Moor 的 TileGrid 上生成一条简易“道路”。
   *
   * 当前逻辑很简单，只是为了验证：
   * - 我们可以在 TileGrid 上改写一条连贯的带状区域；
   * - 再通过 RenderFromTileGrid 开关，让这条路径真实出现在画面上。
   */
  private void generateTestPathOnBloodMoor() {
    DrlgLevel drlgLevel = drlgLevels.get(LEVEL_BLOODMOOR);
    if (drlgLevel == null || drlgLevel.grid == null) return;

    TileGrid grid = drlgLevel.grid;
    int width = grid.width;
    int height = grid.height;
    if (width <= 0 || height <= 0) return;

    // 1) 找一个已有的地板 ID 作为“道路地板”的样式
    int pathId = -1;
    outer:
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int id = grid.floorIds[y][x];
        if (id != -1) {
          pathId = id;
          break outer;
        }
      }
    }
    if (pathId == -1) {
      if (DEBUG_BUILD) {
        Gdx.app.debug(TAG, "generateTestPathOnBloodMoor: no existing floor id found, skip path generation");
      }
      return;
    }

    // 2) 在地图中部画一条横向“道路”，宽度 3 tiles（中心行 ±1）
    int centerY = height / 2;
    int pathHalfWidth = 1; // 总宽度 2*1+1 = 3

    for (int x = 0; x < width; x++) {
      for (int dy = -pathHalfWidth; dy <= pathHalfWidth; dy++) {
        int y = centerY + dy;
        if (y < 0 || y >= height) continue;
        grid.floorIds[y][x] = pathId;
      }
    }

    if (DEBUG_BUILD) {
      Gdx.app.debug(TAG, String.format(
          "generateTestPathOnBloodMoor: path generated at y=%d (width=%d, height=%d, pathId=%d)",
          centerY, width, height, pathId));
    }
  }

  /**
   * 将指定 Zone 的地板层用对应 TileGrid 中的 ID 重新构建。
   *
   * 仅覆盖 TileGrid 中非 -1 的格子，其它保持原有地板不变。
   */
  public boolean hasD2MooExport(int levelId) {
    return levelsFilledByExport.contains(levelId);
  }

  /** DT1 mask used by all D2MOO rooms exported for this level. */
  public int getD2MooDt1Mask(int levelId) {
    return d2MooDt1Masks.get(levelId, 0);
  }

  public void applyTileGridToZone(Zone zone) {
    if (zone == null || zone.level == null) return;

    DrlgLevel drlgLevel = drlgLevels.get(zone.level.Id);
    if (drlgLevel == null || drlgLevel.grid == null) {
      if (DEBUG_BUILD) {
        Gdx.app.debug(TAG, String.format(
            "applyTileGridToZone: no DrlgLevel/TileGrid for zone %s (id=%d)",
            zone.level.LevelName, zone.level.Id));
      }
      return;
    }

    TileGrid grid = drlgLevel.grid;
    if (zone.getLayer(Map.FLOOR_OFFSET) == null) {
      if (DEBUG_BUILD) {
        Gdx.app.debug(TAG, String.format(
            "applyTileGridToZone: floor layer is null for zone %s (id=%d)",
            zone.level.LevelName, zone.level.Id));
      }
      return;
    }

    int width = Math.min(grid.width, zone.tilesX);
    int height = Math.min(grid.height, zone.tilesY);
    IntMap<Integer> idHistogram = DEBUG_GROUND_MAP && zone.level.Id == LEVEL_BLOODMOOR
        ? new IntMap<>() : null;
    LayerApplyCounts counts = applyTileGridLayers(
        grid, zone.dt1s, zone.tiles, zone.tilesX, width, height, idHistogram);
    CollisionApplyCounts collisionCounts = new CollisionApplyCounts();
    if (counts.floors > 0 || counts.walls > 0) {
      TileGrid exportedFootprint = levelsFilledByExport.contains(zone.level.Id) ? grid : null;
      collisionCounts = rebuildTileCollisionFlags(
          exportedFootprint, zone.tiles, zone.dt1s, zone.flags,
          zone.tilesX, zone.tilesY, width, height);
    }

    // Blood Moor 地面调试：grid/zone 尺寸、坐标、ID 分布、解析失败数
    if (DEBUG_GROUND_MAP && zone.level.Id == LEVEL_BLOODMOOR) {
      Gdx.app.log(TAG, String.format(
          "[GroundDebug] BM applyTileGrid: grid=%dx%d zone=%dx%d copy=%dx%d zone.tx=%d ty=%d "
              + "applied=%d failedFloor=%d failedWall=%d failedShadow=%d",
          grid.width, grid.height, zone.tilesX, zone.tilesY, width, height,
          zone.tx, zone.ty, counts.floors, counts.failedFloors,
          counts.failedWalls, counts.failedShadows));
      if (idHistogram != null && idHistogram.size > 0) {
        StringBuilder sb = new StringBuilder("[GroundDebug] BM tile IDs: ");
        int n = 0;
        for (IntMap.Entry<Integer> e : idHistogram.entries()) {
          if (n++ >= 8) { sb.append("..."); break; }
          sb.append(String.format("id=%d(x%d) ", e.key, e.value));
        }
        Gdx.app.log(TAG, sb.toString());
      }
    }
    // D2MOO-exported levels always emit one compact summary. This makes
    // parser/DT1-mask mismatches visible without enabling per-tile logging.
    if (levelsFilledByExport.contains(zone.level.Id)) {
      Gdx.app.log(TAG, String.format(
          "D2MOO apply: level=%s(%d) grid=%dx%d zone=%dx%d floor=%d wall=%d shadow=%d "
              + "failedFloor=%d failedWall=%d failedShadow=%d voidTiles=%d "
              + "collisionTiles=%d blockedSubtiles=%d",
          zone.level.LevelName, zone.level.Id, grid.width, grid.height,
          zone.tilesX, zone.tilesY, counts.floors, counts.walls, counts.shadows,
          counts.failedFloors, counts.failedWalls, counts.failedShadows, collisionCounts.voidTiles,
          collisionCounts.tiles, collisionCounts.blockedSubtiles));
      if (counts.failedResolve > 0) {
        Gdx.app.log(TAG, "D2MOO unresolved IDs: floor=" + formatTileIds(counts.failedFloorIds)
            + " wall=" + formatTileIds(counts.failedWallIds)
            + " shadow=" + formatTileIds(counts.failedShadowIds));
      }
    } else if (DEBUG_BUILD) {
      Gdx.app.debug(TAG, String.format(
          "applyTileGridToZone: applied floor=%d wall=%d shadow=%d from TileGrid to zone %s (id=%d)",
          counts.floors, counts.walls, counts.shadows, zone.level.LevelName, zone.level.Id));
    }
  }

  static LayerApplyCounts applyTileGridLayers(TileGrid grid, DT1s dt1s, DT1.Tile[][] layers,
      int zoneTilesX, int width, int height, IntMap<Integer> floorHistogram) {
    LayerApplyCounts counts = new LayerApplyCounts();
    if (grid == null || dt1s == null || layers == null || zoneTilesX <= 0
        || layers.length < Map.MAX_LAYERS || layers[Map.FLOOR_OFFSET] == null) return counts;
    int layerSize = layers[Map.FLOOR_OFFSET].length;
    width = Math.max(0, Math.min(width, Math.min(grid.width, zoneTilesX)));
    height = Math.max(0, Math.min(height, Math.min(grid.height, layerSize / zoneTilesX)));

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int tileIndex = Zone.index(zoneTilesX, x, y);
        if (tileIndex < 0 || tileIndex >= layerSize) continue;
        int floorId = grid.floorIds[y][x];
        if (floorId != -1) {
          DT1.Tile tile = dt1s.get(floorId);
          if (tile != null) {
            layers[Map.FLOOR_OFFSET][tileIndex] = tile;
            counts.floors++;
            if (floorHistogram != null) {
              floorHistogram.put(floorId, floorHistogram.get(floorId, 0) + 1);
            }
          } else {
            counts.failedResolve++;
            counts.failedFloors++;
            counts.failedFloorIds.add(floorId);
          }
        }

        for (int slot = 0; slot < TileGrid.MAX_WALL_LAYERS && slot < Map.MAX_WALLS; slot++) {
          int wallId = grid.wallIds[slot][y][x];
          if (wallId == -1) continue;
          DT1.Tile tile = dt1s.get(wallId);
          if (tile == null) {
            counts.failedResolve++;
            counts.failedWalls++;
            counts.failedWallIds.add(wallId);
            continue;
          }
          int layer = Map.WALL_OFFSET + slot;
          if (layers[layer] == null) layers[layer] = Zone.obtainTileArray(layerSize);
          if (tileIndex >= layers[layer].length) continue;
          layers[layer][tileIndex] = tile;
          counts.walls++;
        }

        int shadowId = grid.shadowIds[y][x];
        if (shadowId != -1) {
          DT1.Tile tile = dt1s.get(shadowId);
          if (tile != null) {
            if (layers[Map.SHADOW_OFFSET] == null) {
              layers[Map.SHADOW_OFFSET] = Zone.obtainTileArray(layerSize);
            }
            if (tileIndex >= layers[Map.SHADOW_OFFSET].length) continue;
            layers[Map.SHADOW_OFFSET][tileIndex] = tile;
            counts.shadows++;
          } else {
            counts.failedResolve++;
            counts.failedShadows++;
            counts.failedShadowIds.add(shadowId);
          }
        }
      }
    }
    return counts;
  }

  private static String formatTileIds(IntSet ids) {
    if (ids == null || ids.size == 0) return "[]";
    StringBuilder out = new StringBuilder("[");
    int count = 0;
    for (IntSet.IntSetIterator it = ids.iterator(); it.hasNext && count < 8;) {
      int id = it.next();
      if (count++ > 0) out.append(',');
      out.append(String.format("0x%08X(o=%d,m=%d,s=%d)", id,
          DT1.Tile.Index.orientation(id), DT1.Tile.Index.mainIndex(id),
          DT1.Tile.Index.subIndex(id)));
    }
    if (ids.size > count) out.append(",...");
    return out.append(']').toString();
  }

  static CollisionApplyCounts rebuildTileCollisionFlags(DT1.Tile[][] layers, DT1s dt1s,
      byte[] flags, int zoneTilesX, int zoneTilesY, int width, int height) {
    return rebuildTileCollisionFlags(
        null, layers, dt1s, flags, zoneTilesX, zoneTilesY, width, height);
  }

  static CollisionApplyCounts rebuildTileCollisionFlags(TileGrid exportedFootprint,
      DT1.Tile[][] layers, DT1s dt1s, byte[] flags,
      int zoneTilesX, int zoneTilesY, int width, int height) {
    CollisionApplyCounts counts = new CollisionApplyCounts();
    if (layers == null || layers.length < Map.MAX_LAYERS || flags == null
        || zoneTilesX <= 0 || zoneTilesY <= 0) return counts;

    int subtileSize = DT1.Tile.SUBTILE_SIZE;
    int subtileWidth = zoneTilesX * subtileSize;
    int availableSubtileRows = flags.length / subtileWidth;
    width = Math.max(0, Math.min(width, zoneTilesX));
    height = Math.max(0, Math.min(height,
        Math.min(zoneTilesY, availableSubtileRows / subtileSize)));
    if (width == 0 || height == 0) return counts;

    int clearWidth = width * subtileSize;
    int clearHeight = height * subtileSize;
    for (int sy = 0; sy < clearHeight; sy++) {
      int rowStart = sy * subtileWidth;
      Arrays.fill(flags, rowStart, rowStart + clearWidth, (byte) 0);
    }

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        if (exportedFootprint != null
            && (!exportedFootprint.inBounds(x, y)
                || !exportedFootprint.exportedFloorCells[y][x])) {
          markVoidTileBlocked(flags, subtileWidth, x, y);
          counts.voidTiles++;
          continue;
        }
        int tileIndex = Zone.index(zoneTilesX, x, y);
        for (int layer = Map.FLOOR_OFFSET;
             layer < Map.FLOOR_OFFSET + Map.MAX_FLOORS; layer++) {
          DT1.Tile[] tiles = layers[layer];
          if (tiles == null || tileIndex >= tiles.length || tiles[tileIndex] == null) continue;
          orTileCollisionFlags(flags, subtileWidth, x, y, tiles[tileIndex]);
          counts.tiles++;
        }
        for (int layer = Map.WALL_OFFSET;
             layer < Map.WALL_OFFSET + Map.MAX_WALLS; layer++) {
          DT1.Tile[] tiles = layers[layer];
          if (tiles == null || tileIndex >= tiles.length) continue;
          DT1.Tile tile = tiles[tileIndex];
          if (tile == null) continue;
          orTileCollisionFlags(flags, subtileWidth, x, y, tile);
          counts.tiles++;

          // Match Preset.copyWalls: the left half carries the missing collision
          // flags for right north-corner wall graphics in the original DT1 data.
          if (dt1s != null && tile.orientation == Orientation.RIGHT_NORTH_CORNER_WALL) {
            DT1.Tile sibling = dt1s.get(
                Orientation.LEFT_NORTH_CORNER_WALL, tile.mainIndex, tile.subIndex);
            if (sibling != null) {
              orTileCollisionFlags(flags, subtileWidth, x, y, sibling);
              counts.siblingTiles++;
            }
          }
        }
      }
    }

    for (int sy = 0; sy < clearHeight; sy++) {
      int rowStart = sy * subtileWidth;
      for (int sx = 0; sx < clearWidth; sx++) {
        if (flags[rowStart + sx] != 0) counts.blockedSubtiles++;
      }
    }
    return counts;
  }

  private static void markVoidTileBlocked(byte[] flags, int subtileWidth, int tileX, int tileY) {
    int startX = tileX * DT1.Tile.SUBTILE_SIZE;
    int startY = tileY * DT1.Tile.SUBTILE_SIZE;
    byte blocked = (byte) DT1.Tile.FLAG_BLOCK_WALK;
    for (int y = 0; y < DT1.Tile.SUBTILE_SIZE; y++) {
      int rowStart = (startY + y) * subtileWidth + startX;
      Arrays.fill(flags, rowStart, rowStart + DT1.Tile.SUBTILE_SIZE, blocked);
    }
  }

  private static void orTileCollisionFlags(byte[] flags, int subtileWidth,
      int tileX, int tileY, DT1.Tile tile) {
    int startX = tileX * DT1.Tile.SUBTILE_SIZE;
    int sy = tileY * DT1.Tile.SUBTILE_SIZE + DT1.Tile.SUBTILE_SIZE - 1;
    for (int y = 0, t = 0; y < DT1.Tile.SUBTILE_SIZE; y++, sy--) {
      int rowStart = sy * subtileWidth;
      for (int x = 0; x < DT1.Tile.SUBTILE_SIZE; x++, t++) {
        flags[rowStart + startX + x] |= tile.flags[t];
      }
    }
  }

  static final class LayerApplyCounts {
    int floors;
    int walls;
    int shadows;
    int failedResolve;
    int failedFloors;
    int failedWalls;
    int failedShadows;
    final IntSet failedFloorIds = new IntSet();
    final IntSet failedWallIds = new IntSet();
    final IntSet failedShadowIds = new IntSet();
  }

  static final class CollisionApplyCounts {
    int tiles;
    int siblingTiles;
    int blockedSubtiles;
    int voidTiles;
  }

  /**
   * 在所有 zone 生成完成后，在 TileGrid 上生成路径系统。
   * 
   * 参考 D2MOO: DRLGOUTDOORS_SpawnAct1DirtPaths
   * 
   * 实现步骤：
   * 1. 创建路径顶点系统：从城镇和特殊关卡创建路径起点
   * 2. 计算路径连接：连接路径顶点，计算路径路径
   * 3. 在 TileGrid 上绘制路径：使用路径方向和连接信息
   * 
   * @param map 地图对象
   * @param seed 随机种子
   */
  public void generatePathsOnTileGrid(Map map, int seed) {
    if (map == null || map.zones == null) {
      return;
    }

    // 调试：打印 BM 区域信息
    for (Zone z : map.zones) {
      if (z != null && z.level != null && z.level.Id == LEVEL_BLOODMOOR) {
        DrlgLevel bmDrlg = drlgLevels.get(LEVEL_BLOODMOOR);
        int gridW = bmDrlg != null && bmDrlg.grid != null ? bmDrlg.grid.width : -1;
        int gridH = bmDrlg != null && bmDrlg.grid != null ? bmDrlg.grid.height : -1;
        Gdx.app.log(TAG, String.format("[PathDebug] BM:[x=%d,y=%d,w=%d,h=%d] zone.tiles=%dx%d TileGrid=%dx%d",
            z.x, z.y, z.width, z.height, z.tilesX, z.tilesY, gridW, gridH));
        break;
      }
    }

    if (DEBUG_BUILD) {
      Gdx.app.debug(TAG, "=== Starting Path Generation on TileGrid ===");
    }

    // 使用区域特定的种子，确保相同地图总是生成相同路径
    MathUtils.random.setSeed(seed);

    // D2MOD 风格：根据 zone 布局推断出口方向，在全局 subtile 坐标下 Bresenham 画线
    java.util.ArrayList<PathSegment> segments = createPathSegmentsD2MOD(map);
    boolean usedFallback = segments.isEmpty();
    if (usedFallback) {
      Gdx.app.log(TAG, "[PathDebug] generatePathsOnTileGrid: no path segments calculated, applying fallback dirt path");
    }

    if (DEBUG_BUILD) {
      if (!usedFallback) {
        Gdx.app.debug(TAG, String.format("generatePathsOnTileGrid: calculated %d path segments", segments.size()));
      }
    }

    // 3. 在 TileGrid 上绘制路径：使用路径方向和连接信息
    pathGenMap = map;
    map.clearPathDebugPoints();
    try {
      if (usedFallback) {
        applyFallbackDirtPathOnTileGrid(map);
      }
      drawPathsOnTileGrid(segments);
      Gdx.app.log(TAG, "[PathDebug] pathDebugPoints added: " + map.pathDebugPoints.size);
    } finally {
      pathGenMap = null;
    }

    if (DEBUG_BUILD) {
      Gdx.app.debug(TAG, "=== Path Generation on TileGrid Completed ===");
    }
  }

  /**
   * 用于调试：当正常路径段计算失败时，仍在 Blood Moor 的 TileGrid 上强制标记一条横向 dirt path，
   * 让后续 generateDirtPathFromGrid + applyTileGridToZone 能产出可见差异。
   */
  private void applyFallbackDirtPathOnTileGrid(Map map) {
    if (map == null || map.zones == null) return;

    DrlgLevel bmDrlg = drlgLevels.get(LEVEL_BLOODMOOR);
    if (bmDrlg == null || bmDrlg.grid == null) return;

    TileGrid grid = bmDrlg.grid;
    int width = grid.width;
    int height = grid.height;
    if (width <= 0 || height <= 0) return;

    // center band (3 tiles wide)
    int centerY = height / 2;
    int pathHalfWidth = 1;
    for (int x = 0; x < width; x++) {
      for (int dy = -pathHalfWidth; dy <= pathHalfWidth; dy++) {
        int y = centerY + dy;
        if (!grid.inBounds(x, y)) continue;
        grid.dirtPathFlags[y][x] = true;
      }
    }

    // add one debug point so F10 overlay has something even with fallback
    final int sub = DT1.Tile.SUBTILE_SIZE;
    for (Zone z : map.zones) {
      if (z == null || z.level == null || z.level.Id != LEVEL_BLOODMOOR) continue;
      float wx = z.x + (grid.width / 2f) * sub + sub / 2f;
      float wy = z.y + centerY * sub + sub / 2f;
      map.addPathDebugPoint(wx, wy);
      break;
    }
  }

  /**
   * 路径段：D2MOD 风格，存储全局 subtile 坐标的路径点列表。
   */
  private static class PathSegment {
    /** 路径点：每个 int[] 为 {globalSubX, globalSubY} 全局 subtile 坐标 */
    final java.util.ArrayList<int[]> points = new java.util.ArrayList<>();
    PathSegment() {}
    @SuppressWarnings("unused")
    PathSegment(PathVertex start, PathVertex end) { /* for deprecated createPathSegment */ }
  }

  /** 仅用于已废弃的旧路径方法，可删除 */
  private static class PathVertex {
    final Zone zone;
    final int tileX, tileY;
    PathVertex(Zone z, int x, int y) { zone = z; tileX = x; tileY = y; }
  }

  // ========== D2MOD 完全一致实现：DrlgOutdoors.cpp + DrlgOutPlace.cpp ==========

  /** D2MOD ALTDIR */
  private static final int ALTDIR_WEST = 0, ALTDIR_NORTH = 1, ALTDIR_EAST = 2, ALTDIR_SOUTH = 3, ALTDIR_CENTER = 4;

  /** D2MOD: 城镇顶点固定偏移 (tile)，SpawnAct1DirtPaths switch */
  private static final int[][] TOWN_OFFSETS = {
    {59, 19},  // WEST
    {29, 35},  // NORTH
    {4, 22},   // EAST
    {29, 3},   // SOUTH
  };

  /** D2MOD: DRLGOUTDOORS_CalculatePathCoordinates - 按方向对齐到 8-tile 网格 */
  private void calculatePathCoordinates(int levelPosX, int levelPosY, int[] v1, int dir, int[] v2Out) {
    int relX = v1[0] - levelPosX;
    int relY = v1[1] - levelPosY;
    switch (dir) {
      case ALTDIR_WEST:  relX = 8 * (relX / 8) + 11; break;
      case ALTDIR_NORTH: relY = 8 * (relY / 8) + 11; break;
      case ALTDIR_EAST:  relX = 8 * (relX / 8) - 5; break;
      case ALTDIR_SOUTH: relY = 8 * (relY / 8) - 5; break;
      default: break;
    }
    v2Out[0] = relX + levelPosX;
    v2Out[1] = relY + levelPosY;
  }

  /** D2MOD sub_6FD75F60: Bresenham 画线，输出点转为全局 subtile 坐标 */
  private void bresenhamToSubtiles(int tx0, int ty0, int tx1, int ty1, java.util.ArrayList<int[]> out) {
    final int sub = DT1.Tile.SUBTILE_SIZE;
    int x0 = tx0 * sub, y0 = ty0 * sub;
    int x1 = tx1 * sub, y1 = ty1 * sub;
    int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
    int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
    int err = dx - dy;
    int x = x0, y = y0;
    for (;;) {
      out.add(new int[]{x, y});
      if (x == x1 && y == y1) break;
      int e2 = 2 * err;
      if (e2 > -dy) { err -= dy; x += sx; }
      if (e2 < dx)  { err += dx; y += sy; }
    }
  }

  /** D2MOD: 判断 grid 格 (gi,gj) 是否为 preset（bHasPickedFile） */
  private boolean isGridCellPreset(boolean[][] presetGrid, int gi, int gj, int gridW, int gridH) {
    if (gi < 0 || gi >= gridW || gj < 0 || gj >= gridH) return true;
    return presetGrid[gj][gi];
  }

  /** D2MOD sub_6FD80750: A* 寻路，4 邻接，避开 preset。返回路径 grid 格序列，无路径返回 null */
  private java.util.ArrayList<int[]> pathfindGrid(int gx1, int gy1, int gx2, int gy2,
      boolean[][] presetGrid, int gridW, int gridH, int coordMinGx, int coordMinGy, int coordMaxGx, int coordMaxGy) {
    if (gx1 == gx2 && gy1 == gy2) {
      java.util.ArrayList<int[]> r = new java.util.ArrayList<>();
      r.add(new int[]{gx1, gy1});
      return r;
    }
    final int[] DX = {1, 0, -1, 0};
    final int[] DY = {0, 1, 0, -1};
    java.util.PriorityQueue<int[]> open = new java.util.PriorityQueue<>((a, b) -> {
      int fa = a[2] + Math.abs(a[0] - gx2) + Math.abs(a[1] - gy2);
      int fb = b[2] + Math.abs(b[0] - gx2) + Math.abs(b[1] - gy2);
      return Integer.compare(fa, fb);
    });
    java.util.Map<Long, int[]> cameFrom = new java.util.HashMap<>();
    java.util.Set<Long> closed = new java.util.HashSet<>();
    long key1 = (long) gx1 << 16 | (gy1 & 0xFFFF);
    open.add(new int[]{gx1, gy1, 0});
    while (!open.isEmpty()) {
      int[] cur = open.poll();
      int cx = cur[0], cy = cur[1], cost = cur[2];
      long ck = (long) cx << 16 | (cy & 0xFFFF);
      if (closed.contains(ck)) continue;
      closed.add(ck);
      if (cx == gx2 && cy == gy2) {
        java.util.ArrayList<int[]> path = new java.util.ArrayList<>();
        int[] p = new int[]{cx, cy};
        while (p != null) {
          path.add(0, new int[]{p[0], p[1]});
          Long pk = (long) p[0] << 16 | (p[1] & 0xFFFF);
          p = cameFrom.get(pk);
        }
        return path;
      }
      for (int d = 0; d < 4; d++) {
        int nx = cx + DX[d], ny = cy + DY[d];
        if (nx < coordMinGx || nx > coordMaxGx || ny < coordMinGy || ny > coordMaxGy) continue;
        if ((nx != gx2 || ny != gy2) && isGridCellPreset(presetGrid, nx, ny, gridW, gridH)) continue;
        long nk = (long) nx << 16 | (ny & 0xFFFF);
        if (closed.contains(nk)) continue;
        int[] next = new int[]{nx, ny, cost + 1};
        Long pk = (long) cx << 16 | (cy & 0xFFFF);
        if (!cameFrom.containsKey(nk)) cameFrom.put(nk, new int[]{cx, cy});
        open.add(next);
      }
    }
    return null;
  }

  /** D2MOD: 创建路径段（完全按 DRLGOUTDOORS_SpawnAct1DirtPaths 流程） */
  private java.util.ArrayList<PathSegment> createPathSegmentsD2MOD(Map map) {
    java.util.ArrayList<PathSegment> segments = new java.util.ArrayList<>();
    Zone town = null, bm = null;
    for (Zone z : map.zones) {
      if (z == null || z.level == null) continue;
      if (z.level.Id == LEVEL_ROGUEENCAMPMENT) town = z;
      else if (z.level.Id == LEVEL_BLOODMOOR) bm = z;
    }
    if (town == null || bm == null) return segments;

    final int sub = DT1.Tile.SUBTILE_SIZE;
    final int levelPosX = 0, levelPosY = 0; // act 以 town 为原点
    int townDir = town.townExitDirection >= 0 ? town.townExitDirection : 0;

    // 1. 城镇顶点 pVertices[0]: 固定偏移 (tile)
    int townOriginTx = town.x / sub;
    int townOriginTy = town.y / sub;
    int[] townOff = TOWN_OFFSETS[townDir % 4];
    int v0Tx = townOriginTx + townOff[0];
    int v0Ty = townOriginTy + townOff[1];

    // 2. BM 顶点：取 BM 与 town 相邻边的 grid 格中心。D2MOD 从 pGrid 取 nGrid0Entry，此处用几何等价
    int bmTx = bm.x / sub, bmTy = bm.y / sub;
    int bmW = bm.width / sub, bmH = bm.height / sub;
    int bmCenterGx = (bmTx + bmW / 2) / 8;
    int bmCenterGy = (bmTy + bmH / 2) / 8;
    int bmEdgeGx = bmCenterGx, bmEdgeGy = bmCenterGy;
    if (townDir == ALTDIR_SOUTH) bmEdgeGy = bmTy / 8;
    else if (townDir == ALTDIR_NORTH) bmEdgeGy = (bmTy + bmH - 1) / 8;
    else if (townDir == ALTDIR_EAST) bmEdgeGx = bmTx / 8;
    else bmEdgeGx = (bmTx + bmW - 1) / 8;
    int bmVTx = levelPosX + 8 * bmEdgeGx + 3;
    int bmVTy = levelPosY + 8 * bmEdgeGy + 3;

    // 3. CalculatePathCoordinates -> pVertices[6]
    int[] v0 = {v0Tx, v0Ty};
    int[] v6 = new int[2];
    calculatePathCoordinates(levelPosX, levelPosY, v0, townDir, v6);

    // 4. sub_6FD7F5B0: hub = 中心或有效格。简化为 BM 入口格
    int hubGx = bmEdgeGx, hubGy = bmEdgeGy;
    int hubTx = levelPosX + 8 * hubGx + 3;
    int hubTy = levelPosY + 8 * hubGy + 3;

    // 5. 构建 act grid：level 格 (gi,gj) 对应 tile (8*gi, 8*gj)
    int minTx = Math.min(townOriginTx, bmTx);
    int minTy = Math.min(townOriginTy, bmTy);
    int maxTx = Math.max(townOriginTx + town.width / sub, bmTx + bmW);
    int maxTy = Math.max(townOriginTy + town.height / sub, bmTy + bmH);
    int baseGx = minTx / 8;
    int baseGy = minTy / 8;
    int gridW = (maxTx - minTx) / 8 + 4;
    int gridH = (maxTy - minTy) / 8 + 4;
    boolean[][] presetGrid = new boolean[gridH][gridW];
    int townW = town.width / sub, townH = town.height / sub;
    for (int gj = 0; gj < gridH; gj++) {
      for (int gi = 0; gi < gridW; gi++) {
        int cellTx = (baseGx + gi) * 8;
        int cellTy = (baseGy + gj) * 8;
        presetGrid[gj][gi] = (cellTx + 8 > townOriginTx && cellTx < townOriginTx + townW
            && cellTy + 8 > townOriginTy && cellTy < townOriginTy + townH);
      }
    }

    // 6. sub_6FD80750: 从 v6 到 hub 的寻路（grid 坐标）
    int gx1 = v6[0] / 8 - baseGx;
    int gy1 = v6[1] / 8 - baseGy;
    int gx2 = hubGx - baseGx;
    int gy2 = hubGy - baseGy;
    gx1 = Math.max(0, Math.min(gridW - 1, gx1));
    gy1 = Math.max(0, Math.min(gridH - 1, gy1));
    gx2 = Math.max(0, Math.min(gridW - 1, gx2));
    gy2 = Math.max(0, Math.min(gridH - 1, gy2));

    java.util.ArrayList<int[]> gridPath = pathfindGrid(gx1, gy1, gx2, gy2, presetGrid, gridW, gridH, 0, 0, gridW - 1, gridH - 1);

    if (gridPath == null) {
      gridPath = new java.util.ArrayList<>();
      gridPath.add(new int[]{gx1, gy1});
      gridPath.add(new int[]{gx2, gy2});
    }

    // 7. sub_6FD7F810: D2MOD 在 path 前插 hub；此处 path 已是 vertex->hub，无需重复

    // 8. grid 路径 -> tile -> Bresenham subtile
    PathSegment seg = new PathSegment();
    for (int i = 0; i < gridPath.size() - 1; i++) {
      int[] a = gridPath.get(i);
      int[] b = gridPath.get(i + 1);
      int ta = (baseGx + a[0]) * 8 + 3;
      int tb = (baseGy + a[1]) * 8 + 3;
      int tc = (baseGx + b[0]) * 8 + 3;
      int td = (baseGy + b[1]) * 8 + 3;
      bresenhamToSubtiles(ta, tb, tc, td, seg.points);
      if (i < gridPath.size() - 2 && !seg.points.isEmpty()) seg.points.remove(seg.points.size() - 1);
    }
    if (gridPath.size() == 1) {
      int[] a = gridPath.get(0);
      int ta = (baseGx + a[0]) * 8 + 3;
      int tb = (baseGy + a[1]) * 8 + 3;
      seg.points.add(new int[]{ta * sub, tb * sub});
    }
    if (!seg.points.isEmpty()) segments.add(seg);

    // BM -> Cold Plains, BM -> Den of Evil: 直线
    Zone coldPlains = null, denOfEvil = null;
    for (Zone z : map.zones) {
      if (z == null || z.level == null) continue;
      if (z.level.Id == LEVEL_COLDPLAINS) coldPlains = z;
      else if (z.level.Id == LEVEL_DENOFEVIL) denOfEvil = z;
    }
    if (bm != null && coldPlains != null) {
      PathSegment s = new PathSegment();
      bresenhamToSubtiles(bmTx + bmW / 2, bmTy + bmH / 2,
          coldPlains.x / sub + coldPlains.width / sub / 2, coldPlains.y / sub + coldPlains.height / sub / 2, s.points);
      if (!s.points.isEmpty()) segments.add(s);
    }
    if (bm != null && denOfEvil != null) {
      PathSegment s = new PathSegment();
      bresenhamToSubtiles(bmTx + bmW / 2, bmTy + bmH / 2,
          denOfEvil.x / sub + denOfEvil.width / sub / 2, denOfEvil.y / sub + denOfEvil.height / sub / 2, s.points);
      if (!s.points.isEmpty()) segments.add(s);
    }
    return segments;
  }

  /** @deprecated 已由 createPathSegmentsD2MOD 替代 */
  @SuppressWarnings("unused")
  private java.util.ArrayList<Object> createPathVertices_DEPRECATED(Map map) {
    return new java.util.ArrayList<>();
  }

  /** @deprecated replaced by createPathSegmentsD2MOD */
  @SuppressWarnings("unused")
  private java.util.ArrayList<PathSegment> calculatePathSegments(
      Map map, java.util.ArrayList<PathVertex> vertices, int seed) {
    java.util.ArrayList<PathSegment> segments = new java.util.ArrayList<>();

    if (vertices.size() < 2) {
      return segments;
    }

    // 使用区域特定的种子
    MathUtils.random.setSeed(seed);

    // 简化实现：连接相邻的关卡
    // 1. 城镇 -> Blood Moor
    PathVertex townVertex = findVertexByLevelId(vertices, LEVEL_ROGUEENCAMPMENT);
    PathVertex bloodMoorVertex = findVertexByLevelId(vertices, LEVEL_BLOODMOOR);
    if (townVertex != null && bloodMoorVertex != null) {
      PathSegment segment = createPathSegment(townVertex, bloodMoorVertex, map);
      if (segment != null) {
        segments.add(segment);
      }
    }

    // 2. Blood Moor -> Cold Plains
    PathVertex coldPlainsVertex = findVertexByLevelId(vertices, LEVEL_COLDPLAINS);
    if (bloodMoorVertex != null && coldPlainsVertex != null) {
      PathSegment segment = createPathSegment(bloodMoorVertex, coldPlainsVertex, map);
      if (segment != null) {
        segments.add(segment);
      }
    }

    // 3. Blood Moor -> Den of Evil
    PathVertex denOfEvilVertex = findVertexByLevelId(vertices, LEVEL_DENOFEVIL);
    if (bloodMoorVertex != null && denOfEvilVertex != null) {
      PathSegment segment = createPathSegment(bloodMoorVertex, denOfEvilVertex, map);
      if (segment != null) {
        segments.add(segment);
      }
    }

    return segments;
  }

  /**
   * 查找指定关卡 ID 的顶点
   */
  private PathVertex findVertexByLevelId(java.util.ArrayList<PathVertex> vertices, int levelId) {
    for (PathVertex vertex : vertices) {
      if (vertex.zone.level.Id == levelId) {
        return vertex;
      }
    }
    return null;
  }

  /**
   * 创建路径段：使用简单的直线或 A* 算法连接两个顶点
   */
  private PathSegment createPathSegment(PathVertex start, PathVertex end, Map map) {
    PathSegment segment = new PathSegment(start, end);

    // 如果两个顶点在同一个 zone 中，使用直线连接
    if (start.zone == end.zone) {
      createStraightPath(segment, start, end);
    } else {
      // 跨 zone 连接：需要找到两个 zone 之间的连接点
      createCrossZonePath(segment, start, end, map);
    }

    return segment.points.isEmpty() ? null : segment;
  }

  /**
   * 创建直线路径（同一 zone 内）
   */
  private void createStraightPath(PathSegment segment, PathVertex start, PathVertex end) {
    int dx = end.tileX - start.tileX;
    int dy = end.tileY - start.tileY;
    int steps = Math.max(Math.abs(dx), Math.abs(dy));

    if (steps == 0) {
      segment.points.add(new int[]{start.tileX, start.tileY});
      Gdx.app.log(TAG, String.format("[PathDebug] createStraightPath add (%d,%d) [same zone]",
          start.tileX, start.tileY));
      return;
    }

    for (int i = 0; i <= steps; i++) {
      int x = start.tileX + (dx * i) / steps;
      int y = start.tileY + (dy * i) / steps;
      segment.points.add(new int[]{x, y});
    }
    Gdx.app.log(TAG, String.format("[PathDebug] createStraightPath %s: %d points from (%d,%d) to (%d,%d)",
        start.zone.level.LevelName, segment.points.size(), start.tileX, start.tileY, end.tileX, end.tileY));
  }

  /**
   * 创建跨 zone 路径
   */
  private void createCrossZonePath(PathSegment segment, PathVertex start, PathVertex end, Map map) {
    // 简化实现：找到两个 zone 的边界连接点，然后分别连接
    // 这里使用简单的启发式：假设相邻 zone 在某个方向上连接

    // 1. 在起点 zone 中，找到朝向终点的边界点
    int startBoundaryX = start.tileX;
    int startBoundaryY = start.tileY;
    
    DrlgLevel startDrlgLevel = drlgLevels.get(start.zone.level.Id);
    if (startDrlgLevel == null || startDrlgLevel.grid == null) return;
    
    TileGrid startGrid = startDrlgLevel.grid;
    
    // 确定方向（简化：假设终点在右侧或下方）
    int dirX = 0, dirY = 0;
    if (end.zone.level.Id > start.zone.level.Id) {
      // 终点在右侧或下方
      dirX = 1;
      dirY = 1;
    } else {
      dirX = -1;
      dirY = -1;
    }

    // 从起点到边界（当前简化实现中，startBoundaryX/Y 与 start.tileX/Y 相同，
    // 会导致 steps1 == 0，从而 /0。这里做一个安全保护：
    // 如果无法计算出有意义的“边界点”，则退化为简单的直线路径。）
    int steps1 = Math.max(Math.abs(startBoundaryX - start.tileX),
                          Math.abs(startBoundaryY - start.tileY));
    if (steps1 <= 0) {
      // 退化为从起点到终点的直线路径（跨 zone 版本）
      int dx = end.tileX - start.tileX;
      int dy = end.tileY - start.tileY;
      int steps = Math.max(Math.abs(dx), Math.abs(dy));
      Gdx.app.log(TAG, String.format("[PathDebug] createCrossZonePath fallback %s->%s: steps=%d startGrid=%dx%d",
          start.zone.level.LevelName, end.zone.level.LevelName, steps, startGrid.width, startGrid.height));
      if (steps == 0) {
        segment.points.add(new int[]{start.tileX, start.tileY});
        Gdx.app.log(TAG, String.format("[PathDebug] createCrossZonePath add (%d,%d) [steps=0]", start.tileX, start.tileY));
      } else {
        for (int i = 0; i <= steps; i++) {
          int x = start.tileX + (dx * i) / steps;
          int y = start.tileY + (dy * i) / steps;
          if (startGrid.inBounds(x, y)) {
            segment.points.add(new int[]{x, y});
          }
        }
        Gdx.app.log(TAG, String.format("[PathDebug] createCrossZonePath startZone points: %d (end (%d,%d) in end's coords, not in startGrid)",
            segment.points.size(), end.tileX, end.tileY));
      }
      return;
    }

    for (int i = 0; i <= steps1; i++) {
      int x = start.tileX + (startBoundaryX - start.tileX) * i / steps1;
      int y = start.tileY + (startBoundaryY - start.tileY) * i / steps1;
      if (startGrid.inBounds(x, y)) {
        segment.points.add(new int[]{x, y});
      }
    }

    // 从边界到终点（简化：直接连接一个终点）
    segment.points.add(new int[]{end.tileX, end.tileY});
  }

  /**
   * 在 TileGrid 上绘制路径（D2MOD 风格：先标记 DirtPathGrid，再按连通性生成土路瓦片）
   */
  private void drawPathsOnTileGrid(java.util.ArrayList<PathSegment> segments) {
    // 1. 将所有路径点标记到 dirtPathFlags（等价 D2MOD sub_6FD75F60 画线）
    for (PathSegment segment : segments) {
      drawPathSegment(segment);
    }
    // 2. 对每个有路径的 zone 执行 DRLG_OUTDOORS_GenerateDirtPath 等价逻辑
    if (pathGenMap != null && pathGenMap.zones != null) {
      for (Zone zone : pathGenMap.zones) {
        if (zone == null || zone.level == null) continue;
        DrlgLevel drlg = drlgLevels.get(zone.level.Id);
        if (drlg == null || drlg.grid == null) continue;
        generateDirtPathFromGrid(zone, drlg.grid);
      }
    }
  }

  /**
   * 绘制路径段：将全局 subtile 坐标的点分配到各 zone，在对应 zone 的 grid 上标记。
   */
  private void drawPathSegment(PathSegment segment) {
    if (segment.points.isEmpty() || pathGenMap == null || pathGenMap.zones == null) return;
    final int sub = DT1.Tile.SUBTILE_SIZE;
    for (Zone zone : pathGenMap.zones) {
      if (zone == null || zone.level == null) continue;
      DrlgLevel drlg = drlgLevels.get(zone.level.Id);
      if (drlg == null || drlg.grid == null) continue;
      TileGrid grid = drlg.grid;
      for (int[] p : segment.points) {
        int gx = p[0], gy = p[1];
        if (gx >= zone.x && gx < zone.x + zone.width && gy >= zone.y && gy < zone.y + zone.height) {
          int lx = (gx - zone.x) / sub;
          int ly = (gy - zone.y) / sub;
          if (grid.inBounds(lx, ly)) {
            markPathPointOnGrid(grid, lx, ly, zone);
          }
        }
      }
    }
  }

  /** 在 grid 上标记路径点（含加粗）并加入 pathDebugPoints */
  private void markPathPointOnGrid(TileGrid grid, int x, int y, Zone zone) {
    int pathWidth = 3;
    int halfWidth = pathWidth / 2;
    final int sub = DT1.Tile.SUBTILE_SIZE;
    if (grid.inBounds(x, y)) {
      boolean preserveD2MooFootprint = zone != null && zone.level != null
          && levelsFilledByExport.contains(zone.level.Id);
      if (preserveD2MooFootprint && !grid.exportedFloorCells[y][x]) return;
      grid.dirtPathFlags[y][x] = true;
      if (pathGenMap != null) {
        float wx = zone.x + x * sub + sub / 2f;
        float wy = zone.y + y * sub + sub / 2f;
        pathGenMap.addPathDebugPoint(wx, wy);
      }
      for (int dy = -halfWidth; dy <= halfWidth; dy++) {
        for (int dx = -halfWidth; dx <= halfWidth; dx++) {
          int nx = x + dx, ny = y + dy;
          if (grid.inBounds(nx, ny) && dx * dx + dy * dy <= halfWidth * halfWidth
              && (!preserveD2MooFootprint || grid.exportedFloorCells[ny][nx])) {
            grid.dirtPathFlags[ny][nx] = true;
          }
        }
      }
    }
  }

  /**
   * D2MOD: DRLG_OUTDOORS_GenerateDirtPath 等价实现。
   * 根据 dirtPathFlags 和 3x3 邻域连通性，用 byte_6FDCF958 查表得瓦片类型，写入 floorIds。
   */
  private void generateDirtPathFromGrid(Zone zone, TileGrid grid) {
    if (zone.dt1s == null) return;
    int w = grid.width;
    int h = grid.height;
    boolean[][] dp = grid.dirtPathFlags;

    // D2MOD MapOffsetToBoxIndex 邻域顺序：nBoxIndex 8,7,6,5,3,2,1,0 对应
    // (1,-1),(1,0),(1,1),(0,-1),(0,1),(-1,-1),(-1,0),(-1,1)
    final int[] DX = { 1, 1, 1, 0, 0, -1, -1, -1 };
    final int[] DY = { -1, 0, 1, -1, 1, -1, 0, 1 };

    int pathCells = 0;        // dirtPathFlags 为 true 的格子数
    int pathCellsResolved = 0; // 有解析出 tileId 的格子数
    int pathCellsChanged = 0; // 覆盖前 tileId != 新 tileId 的格子数
    int pathCellsUnresolved = 0; // 解析出 tileId < 0 的格子数

    boolean logStats = zone.level != null && zone.level.Id == LEVEL_BLOODMOOR;

    for (int nY = 0; nY < h; nY++) {
      for (int nX = 0; nX < w; nX++) {
        if (!dp[nY][nX]) continue;
        if (levelsFilledByExport.contains(zone.level.Id)
            && !grid.exportedFloorCells[nY][nX]) continue;
        pathCells++;
        int nDirectionsWithPathFlags = 0;
        for (int i = 0; i < 8; i++) {
          int nx = nX + DX[i];
          int ny = nY + DY[i];
          nDirectionsWithPathFlags = (nDirectionsWithPathFlags << 1) | (grid.inBounds(nx, ny) && dp[ny][nx] ? 1 : 0);
        }
        if (nDirectionsWithPathFlags == 0) continue;
        if (nDirectionsWithPathFlags >= D2MOD_PATH_TILE_TABLE.length) continue;
        int v19 = D2MOD_PATH_TILE_TABLE[nDirectionsWithPathFlags] & 0xFF;
        if (v19 == 0) continue;
        int packedFlags = (v19 << 8) | 0x82;
        int tileId = resolvePackedPathToTileId(zone, packedFlags);
        if (tileId < 0) {
          tileId = findPathFloorId(zone, grid);
        }
        if (tileId >= 0) {
          pathCellsResolved++;
          int oldId = grid.floorIds[nY][nX];
          grid.floorIds[nY][nX] = tileId;
          if (oldId != tileId) pathCellsChanged++;
        } else {
          pathCellsUnresolved++;
        }
      }
    }

    if (logStats) {
      Gdx.app.log(TAG, String.format(
          "[PathDebug] BloodMoor dirtPathStats: pathCells=%d resolved=%d changed=%d unresolved=%d",
          pathCells, pathCellsResolved, pathCellsChanged, pathCellsUnresolved));
    }
  }

  /**
   * D2MOD: DRLGROOMTILE_GetTileCache 等价——将 packed path flags 解析为 tile id。
   * packed = (nStyle<<8)|0x82，nStyle=v19 对应 DT1 mainIndex，0x82 低字节含 sequence。
   */
  private int resolvePackedPathToTileId(Zone zone, int packedFlags) {
    if (zone == null || zone.dt1s == null) return -1;
    int nStyle = (packedFlags >> 8) & 0xFF;
    // 优先用 nStyle 作为 mainIndex 查 DT1（D2MOD LvlTypes 的土路瓦片）
    for (int sub = 0; sub <= 7; sub++) {
      DT1.Tile t = zone.dt1s.get(Orientation.FLOOR, nStyle, sub);
      if (t != null && t.id > 0) return t.id;
    }
    // fallback: 任意 Act1 户外地板（泥土/石头）
    for (int main = 1; main <= 30; main++) {
      DT1.Tile t = zone.dt1s.get(Orientation.FLOOR, main, 0);
      if (t != null && t.id > 0) return t.id;
    }
    return -1;
  }

  /**
   * 查找路径地板 ID，必须能在 targetZone.dt1s 中解析出有效瓦片。
   * applyTileGridToZone 会用 zone.dt1s.get(id) 取瓦片，id 必须对 targetZone 有效。
   * 优先使用 path preset 的专用瓦片，确保路径与草地区分明显。
   */
  private int findPathFloorId(Zone targetZone, TileGrid grid) {
    // 0. 优先：从 path preset 获取路径专用瓦片（土路/石子路，与草地明显区分）
    int pathTileId = OutdoorFeatures.getPathTileIdForZone(targetZone);
    if (pathTileId > 0) return pathTileId;
    // 若当前 zone 无 path preset，从兄弟 zone 获取（共享 dt1 的 Act1 户外）
    if (pathGenMap != null && pathGenMap.zones != null && targetZone.dt1s != null) {
      for (Zone z : pathGenMap.zones) {
        if (z == null || z == targetZone) continue;
        pathTileId = OutdoorFeatures.getPathTileIdForZone(z);
        if (pathTileId > 0 && resolveTile(targetZone, pathTileId) != null) return pathTileId;
      }
    }

    // 1. 从 grid 中找一个已有的地板 ID（排除 0），且 targetZone.dt1s.get(id) 非空
    for (int y = 0; y < grid.height; y++) {
      for (int x = 0; x < grid.width; x++) {
        int id = grid.floorIds[y][x];
        if (id > 0 && resolveTile(targetZone, id) != null) {
          return id;
        }
      }
    }

    // 2. 从兄弟 outdoor zone（Cold Plains / Stony Field）的 grid 获取有效 id，共享 dt1
    if (pathGenMap != null && pathGenMap.zones != null && targetZone.dt1s != null) {
      int[] siblingLevels = { LEVEL_COLDPLAINS, LEVEL_STONYFIELD };
      for (int levelId : siblingLevels) {
        DrlgLevel sibling = drlgLevels.get(levelId);
        if (sibling == null || sibling.grid == null) continue;
        TileGrid sg = sibling.grid;
        for (int y = 0; y < sg.height; y++) {
          for (int x = 0; x < sg.width; x++) {
            int id = sg.floorIds[y][x];
            if (id > 0 && resolveTile(targetZone, id) != null) {
              return id;
            }
          }
        }
      }
    }

    // 3. 从 targetZone 地板层找一个与主导地板不同的瓦片（提高路径可见性）
    //    优先选择非最常见的瓦片，避免路径与草地在视觉上融合
    DT1.Tile[] floorLayer = targetZone.getLayer(Map.FLOOR_OFFSET);
    if (floorLayer != null && targetZone.dt1s != null) {
      IntMap<Integer> idCount = new IntMap<>();
      for (DT1.Tile tile : floorLayer) {
        if (tile != null && tile.id > 0 && resolveTile(targetZone, tile.id) != null) {
          idCount.put(tile.id, idCount.get(tile.id, 0) + 1);
        }
      }
      int maxCount = 0;
      int dominantId = -1;
      for (IntMap.Entry<Integer> e : idCount.entries()) {
        if (e.value > maxCount) {
          maxCount = e.value;
          dominantId = e.key;
        }
      }
      // 返回一个非主导的瓦片（石头/泥土等比草地更易区分）
      for (IntMap.Entry<Integer> e : idCount.entries()) {
        if (e.key != dominantId && e.value > 0) {
          return e.key;
        }
      }
      if (dominantId > 0) return dominantId;
    }

    // 4. 最终 fallback：尝试 Act1 户外地板瓦片，优先较高 mainIndex（Stones 等）
    if (targetZone.dt1s != null) {
      for (int mainIndex = 25; mainIndex >= 2; mainIndex--) {
        for (int subIndex = 0; subIndex <= 3; subIndex++) {
          DT1.Tile t = targetZone.dt1s.get(Orientation.FLOOR, mainIndex, subIndex);
          if (t != null && t.id > 0) return t.id;
        }
      }
    }

    return -1;
  }

  /** 在 zone.dt1s 中解析 tile id，不存在则返回 null */
  private static DT1.Tile resolveTile(Zone zone, int id) {
    return zone != null && zone.dt1s != null ? zone.dt1s.get(id) : null;
  }
}

