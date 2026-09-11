package com.riiablo.engine.client.automap;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectMap;

import com.riiablo.Riiablo;
import com.riiablo.codec.DC6;
import com.riiablo.codec.excel.AutoMap;
import com.riiablo.graphics.PaletteIndexedBatch;

/**
 * 小地图瓷砖渲染器
 * 使用 MaxiMap.dc6 等资源渲染小地图的墙壁、边界和图标
 * 
 * 资源说明:
 * - MaxiMap.dc6 / MaxiMapS.dc6 - 主要小地图瓷砖精灵（包含所有瓷砖类型）
 * - Act2Map.dc6 / Act4Map.dc6 - 特定章节的地图资源
 * - ExTnMap.dc6 - 扩展城镇地图
 * 
 * AutoMap.txt 定义了瓷砖类型到精灵帧的映射关系:
 * - LevelName: 关卡名称（如 "1 Barracks"）
 * - TileName: 瓷砖类型（如 "Floor", "Wall"）
 * - Style/StartSequence/EndSequence: 瓷砖样式范围
 * - Cel1-Cel4: 对应的 MaxiMap.dc6 帧索引
 */
public class AutomapTileRenderer implements Disposable {
  private static final String TAG = "AutomapTileRenderer";
  
  // ==================== 资源路径 ====================
  
  /** 主要小地图瓷砖精灵 */
  public static final String PATH_MAXIMAP = "data\\global\\ui\\AUTOMAP\\MaxiMap.dc6";
  
  /** 小尺寸版本 */
  public static final String PATH_MAXIMAP_S = "data\\global\\ui\\Automap\\MaxiMapS.dc6";
  
  /** 第二章特殊地图 */
  public static final String PATH_ACT2_MAP = "data\\global\\ui\\AUTOMAP\\Act2Map.dc6";
  
  /** 第四章特殊地图 */
  public static final String PATH_ACT4_MAP = "data\\global\\ui\\AUTOMAP\\Act4Map.dc6";
  
  /** 扩展城镇地图 */
  public static final String PATH_EXTN_MAP = "data\\global\\ui\\Automap\\ExTnMap.dc6";
  
  // ==================== 瓷砖类型常量 ====================
  
  /** 地板瓷砖 */
  public static final int TILE_FLOOR = 0;
  
  /** 左墙 */
  public static final int TILE_WALL_LEFT = 1;
  
  /** 右墙 */
  public static final int TILE_WALL_RIGHT = 2;
  
  /** 左上角墙 */
  public static final int TILE_WALL_TOP_LEFT = 3;
  
  /** 右上角墙 */
  public static final int TILE_WALL_TOP_RIGHT = 4;
  
  /** 左下角墙 */
  public static final int TILE_WALL_BOTTOM_LEFT = 5;
  
  /** 右下角墙 */
  public static final int TILE_WALL_BOTTOM_RIGHT = 6;
  
  /** 柱子 */
  public static final int TILE_PILLAR = 7;
  
  /** 门 */
  public static final int TILE_DOOR = 8;
  
  // ==================== 渲染设置 ====================
  
  /** 瓷砖绘制宽度（等距坐标） */
  public static final int TILE_WIDTH = 8;
  
  /** 瓷砖绘制高度（等距坐标） */
  public static final int TILE_HEIGHT = 4;
  
  /** 子瓷砖绘制宽度 */
  public static final int SUBTILE_WIDTH = 16;
  
  /** 子瓷砖绘制高度 */
  public static final int SUBTILE_HEIGHT = 8;
  
  // ==================== 内部状态 ====================
  
  /** 主小地图精灵 */
  private DC6 maxiMap;
  
  /** 小尺寸小地图精灵 */
  private DC6 maxiMapSmall;
  
  /** 当前使用的精灵（根据章节切换） */
  private DC6 currentSprite;
  
  /** 当前章节 */
  private int currentAct = 1;
  
  /** AutoMap.txt 数据 */
  private AutoMap automapData;
  
  /** D2MOO AutoMap 查询结果缓存。键必须包含完整的 level/tile/style/sequence。 */
  private final ObjectMap<String, int[]> frameCaches = new ObjectMap<>();

  /** 可选的旧 levelId 到 D2MOO LevelName 映射。 */
  private final IntMap<String> levelNames = new IntMap<>();
  
  /** 是否已初始化 */
  private boolean initialized = false;
  
  /** 调色板纹理 */
  private Texture paletteTexture;
  
  public AutomapTileRenderer() {
  }
  
  /**
   * 初始化渲染器，加载必要资源
   */
  public void init() {
    if (initialized) return;
    
    try {
      // 加载主小地图精灵
      if (Riiablo.assets != null && Riiablo.assets.isLoaded(PATH_MAXIMAP)) {
        maxiMap = Riiablo.assets.get(PATH_MAXIMAP);
      }
      
      // 加载调色板纹理（使用 Act1 调色板）
      if (Riiablo.palettes != null) {
        paletteTexture = Riiablo.palettes.act1;
      }
      
      // 设置当前精灵
      currentSprite = maxiMap;
      
      initialized = true;
      Gdx.app.log(TAG, "AutomapTileRenderer initialized");
    } catch (Exception e) {
      Gdx.app.error(TAG, "Failed to initialize AutomapTileRenderer", e);
    }
  }
  
  /**
   * 加载 AutoMap.txt 数据
   */
  public void loadAutomapData(AutoMap data) {
    this.automapData = data;
    frameCaches.clear(); // 清除缓存以重新构建
  }

  /** 注册旧 API 使用的 levelId 到 AutoMap.txt LevelName 映射。 */
  public void setLevelName(int levelId, String levelName) {
    if (levelName == null || levelName.trim().isEmpty()) levelNames.remove(levelId);
    else levelNames.put(levelId, levelName);
    frameCaches.clear();
  }
  
  /**
   * 设置当前章节（用于切换地图资源）
   */
  public void setCurrentAct(int act) {
    if (this.currentAct == act) return;
    this.currentAct = act;
    
    // 根据章节选择合适的精灵
    // 暂时都使用 MaxiMap
    currentSprite = maxiMap;
  }
  
  /**
   * 获取指定瓷砖类型的精灵帧索引
   * 
   * @param levelId 关卡ID
   * @param tileOrientation 瓷砖方向
   * @param tileStyle 瓷砖样式
   * @param tileSequence 瓷砖序列
   * @return 帧索引数组（可能有多个变体），null 表示无对应图标
   */
  public int[] getFrameIndices(int levelId, int tileOrientation, int tileStyle, int tileSequence) {
    String levelName = levelNames.get(levelId);
    if (levelName == null) return null;
    return getFrameIndices(levelName, tileOrientationName(tileOrientation), tileStyle, tileSequence);
  }

  /**
   * D2MOO DATATBLS_GetAutomapCellId 使用的字符串查询规则。
   * LevelName、TileName 必须匹配；Style=-1 或 StartSequence=-1 为通配。
   */
  public int[] getFrameIndices(String levelName, String tileName, int tileStyle, int tileSequence) {
    if (automapData == null || levelName == null || tileName == null) return null;
    String key = cacheKey(levelName, tileName, tileStyle, tileSequence);
    int[] cached = frameCaches.get(key);
    if (cached != null) return cached;

    for (AutoMap.Entry entry : automapData) {
      if (!matches(entry, levelName, tileName, tileStyle, tileSequence)) continue;
      int[] frames = validCels(entry.Cel);
      if (frames != null) {
        frameCaches.put(key, frames);
        return frames;
      }
    }
    return null;
  }

  /** 使用方向常量的字符串 LevelName 便捷重载。 */
  public int[] getFrameIndices(String levelName, int tileOrientation, int tileStyle, int tileSequence) {
    return getFrameIndices(levelName, tileOrientationName(tileOrientation), tileStyle, tileSequence);
  }

  /** 返回 D2MOO 风格的单一 Automap cell（同一 seed 结果稳定）。 */
  public int getAutomapCellId(String levelName, String tileName, int tileStyle, int tileSequence,
      long automapSeed) {
    int[] frames = getFrameIndices(levelName, tileName, tileStyle, tileSequence);
    if (frames == null) {
      // Some 1.10f Jungle/Kurast terrain rows are generated with the base
      // style (0) while AutoMap.txt only lists the visual style variants.
      // Keep level/tile semantics, but tolerate that data-table gap by using
      // the first native cell for the same tile family.
      frames = getFrameIndicesAnyStyle(levelName, tileName);
    }
    return selectCellId(frames, automapSeed);
  }

  private int[] getFrameIndicesAnyStyle(String levelName, String tileName) {
    if (automapData == null || levelName == null || tileName == null) return null;
    for (AutoMap.Entry entry : automapData) {
      if (!same(entry.LevelName, levelName) || !same(entry.TileName, tileName)) continue;
      int[] frames = validCels(entry.Cel);
      if (frames != null) return frames;
    }
    return null;
  }

  /** Compact diagnostics for headless map validation when a native lookup misses. */
  public String diagnostic(String levelName, String tileName, int tileStyle, int tileSequence) {
    int levelMatches = 0;
    int tileMatches = 0;
    int styleMatches = 0;
    String example = null;
    StringBuilder styles = new StringBuilder();
    if (automapData != null) {
      for (AutoMap.Entry entry : automapData) {
        if (!same(entry.LevelName, levelName)) continue;
        levelMatches++;
        if (!same(entry.TileName, tileName)) continue;
        tileMatches++;
        if (styles.length() < 160) {
          if (styles.length() > 0) styles.append(',');
          styles.append(entry.Style).append(':').append(entry.StartSequence)
              .append("..").append(entry.EndSequence);
        }
        if (entry.Style != -1 && entry.Style != tileStyle) continue;
        if (entry.StartSequence != -1
            && (tileSequence < entry.StartSequence
                || (entry.EndSequence != -1 && tileSequence > entry.EndSequence))) continue;
        styleMatches++;
        if (example == null) {
          example = entry.LevelName + "/" + entry.TileName + "/"
              + entry.Style + "/" + entry.StartSequence + ".." + entry.EndSequence;
        }
      }
    }
    return "levelMatches=" + levelMatches + " tileMatches=" + tileMatches
        + " styleMatches=" + styleMatches + " example="
        + (example == null ? "none" : example) + " entries=" + styles;
  }

  /** 使用方向常量的单 Cel 查询重载。 */
  public int getAutomapCellId(String levelName, int tileOrientation, int tileStyle, int tileSequence,
      long automapSeed) {
    return getAutomapCellId(levelName, tileOrientationName(tileOrientation), tileStyle,
        tileSequence, automapSeed);
  }

  /** 纯查询匹配函数，供无资源单元测试复用。 */
  public static boolean matches(AutoMap.Entry entry, String levelName, String tileName,
      int tileStyle, int tileSequence) {
    if (entry == null || !same(entry.LevelName, levelName) || !same(entry.TileName, tileName)) {
      return false;
    }
    if (entry.Style != -1 && entry.Style != tileStyle) return false;
    // 原版以 StartSequence=-1 表示忽略序列；EndSequence=-1 则表示无上界。
    if (entry.StartSequence != -1 && tileSequence < entry.StartSequence) return false;
    if (entry.EndSequence != -1 && tileSequence > entry.EndSequence) return false;
    return true;
  }

  private static boolean same(String a, String b) {
    return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
  }

  private static int[] validCels(int[] cels) {
    if (cels == null) return null;
    int count = 0;
    for (int cel : cels) if (cel >= 0) count++;
    if (count == 0) return null;
    int[] result = new int[count];
    int i = 0;
    for (int cel : cels) if (cel >= 0) result[i++] = cel;
    return result;
  }

  /** 从有效 Cel 列表按 seed 稳定选择一个帧；无有效帧时返回 -1。 */
  public static int selectCellId(int[] cels, long automapSeed) {
    int[] frames = validCels(cels);
    if (frames == null) return -1;
    long mixed = automapSeed ^ (automapSeed >>> 33);
    mixed *= 0xff51afd7ed558ccdL;
    mixed ^= (mixed >>> 33);
    return frames[(int) ((mixed & Long.MAX_VALUE) % frames.length)];
  }

  private static String cacheKey(String levelName, String tileName, int style, int sequence) {
    return levelName.trim().toLowerCase() + '|' + tileName.trim().toLowerCase()
        + '|' + style + '|' + sequence;
  }

  private static String tileOrientationName(int orientation) {
    String name = tileNameForOrientation(orientation);
    return name != null ? name : Integer.toString(orientation);
  }

  /** Maps the full DT1 orientation vocabulary to D2MOO AutoMap tile names. */
  public static String tileNameForOrientation(int orientation) {
    switch (orientation) {
      case com.riiablo.map.Orientation.FLOOR: return "fl";
      case com.riiablo.map.Orientation.LEFT_WALL: return "wl";
      case com.riiablo.map.Orientation.RIGHT_WALL: return "wr";
      case com.riiablo.map.Orientation.RIGHT_NORTH_CORNER_WALL: return "wtlr";
      case com.riiablo.map.Orientation.LEFT_NORTH_CORNER_WALL: return "wtll";
      case com.riiablo.map.Orientation.LEFT_END_WALL: return "wle";
      case com.riiablo.map.Orientation.RIGHT_END_WALL: return "wre";
      case com.riiablo.map.Orientation.SOUTH_CORNER_WALL: return "wbr";
      case com.riiablo.map.Orientation.LEFT_WALL_DOOR: return "wld";
      case com.riiablo.map.Orientation.RIGHT_WALL_DOOR: return "wrd";
      case com.riiablo.map.Orientation.PILLAR: return "co";
      case com.riiablo.map.Orientation.SHADOW: return "sh";
      case com.riiablo.map.Orientation.TREE: return "tr";
      case com.riiablo.map.Orientation.ROOF: return "rf";
      case com.riiablo.map.Orientation.LOWER_LEFT_WALL: return "ld";
      case com.riiablo.map.Orientation.LOWER_RIGHT_WALL: return "rd";
      case com.riiablo.map.Orientation.LOWER_NORTH_CORNER_WALL: return "fd";
      case com.riiablo.map.Orientation.LOWER_SOUTH_CORNER_WALL: return "fi";
      default: return null;
    }
  }
  
  /**
   * 获取特殊图标的精灵帧索引
   * 
   * @param iconType 图标类型（参考 AutomapIconType）
   * @return 帧索引，-1 表示无对应图标
   */
  public int getIconFrame(int iconType) {
    // 直接返回图标类型值，因为 AutomapIconType 中定义的值
    // 就是 MaxiMap.dc6 中的帧索引
    if (iconType >= 0) {
      return iconType;
    }
    return -1;
  }
  
  /**
   * 渲染小地图瓷砖
   * 
   * @param batch 渲染批处理器
   * @param frameIndex 帧索引
   * @param x 屏幕X坐标
   * @param y 屏幕Y坐标
   */
  public void renderTile(PaletteIndexedBatch batch, int frameIndex, float x, float y) {
    if (currentSprite == null || frameIndex < 0) return;
    
    try {
      // 获取纹理区域
      TextureRegion region = currentSprite.getTexture(0, frameIndex);
      if (region != null) {
        batch.draw(region, x, y);
      }
    } catch (Exception e) {
      // 帧索引越界，忽略
    }
  }
  
  /**
   * 渲染特殊图标
   * 
   * @param batch 渲染批处理器
   * @param iconType 图标类型
   * @param x 屏幕X坐标
   * @param y 屏幕Y坐标
   */
  public void renderIcon(PaletteIndexedBatch batch, int iconType, float x, float y) {
    int frameIndex = getIconFrame(iconType);
    if (frameIndex >= 0) {
      renderTile(batch, frameIndex, x, y);
    }
  }
  
  /**
   * 获取帧的宽度
   */
  public int getFrameWidth(int frameIndex) {
    if (currentSprite == null || frameIndex < 0) return TILE_WIDTH;
    
    try {
      TextureRegion region = currentSprite.getTexture(0, frameIndex);
      if (region != null) {
        return region.getRegionWidth();
      }
    } catch (Exception e) {
      // 忽略
    }
    return TILE_WIDTH;
  }
  
  /**
   * 获取帧的高度
   */
  public int getFrameHeight(int frameIndex) {
    if (currentSprite == null || frameIndex < 0) return TILE_HEIGHT;
    
    try {
      TextureRegion region = currentSprite.getTexture(0, frameIndex);
      if (region != null) {
        return region.getRegionHeight();
      }
    } catch (Exception e) {
      // 忽略
    }
    return TILE_HEIGHT;
  }
  
  /**
   * 检查是否已加载精灵资源
   */
  public boolean hasSprite() {
    return currentSprite != null;
  }
  
  /**
   * 获取精灵总帧数
   */
  public int getFrameCount() {
    if (currentSprite == null) return 0;
    return currentSprite.getNumFramesPerDir();
  }
  
  @Override
  public void dispose() {
    // DC6 资源由 AssetManager 管理，这里不需要手动释放
    maxiMap = null;
    maxiMapSmall = null;
    currentSprite = null;
    frameCaches.clear();
    levelNames.clear();
    initialized = false;
  }
}
