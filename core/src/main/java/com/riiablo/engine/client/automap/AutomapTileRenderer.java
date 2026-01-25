package com.riiablo.engine.client.automap;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntMap;

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
  
  /** 瓷砖类型到帧索引的缓存 (key = levelId << 16 | tileType) */
  private final IntMap<int[]> frameCaches = new IntMap<>();
  
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
    if (automapData == null) return null;
    
    // 构建缓存键
    int cacheKey = (levelId << 16) | (tileOrientation << 8) | tileStyle;
    
    int[] cached = frameCaches.get(cacheKey);
    if (cached != null) return cached;
    
    // 查找匹配的 AutoMap 记录
    for (AutoMap.Entry entry : automapData) {
      if (entry.Style == tileStyle) {
        // 检查序列范围
        if (entry.StartSequence >= 0 && entry.EndSequence >= 0) {
          if (tileSequence < entry.StartSequence || tileSequence > entry.EndSequence) {
            continue;
          }
        }
        
        // 收集有效的帧索引（排除 -1）
        int[] frames = entry.Cel;
        if (frames != null) {
          int count = 0;
          for (int frame : frames) {
            if (frame >= 0) count++;
          }
          
          if (count > 0) {
            int[] result = new int[count];
            int idx = 0;
            for (int frame : frames) {
              if (frame >= 0) result[idx++] = frame;
            }
            
            frameCaches.put(cacheKey, result);
            return result;
          }
        }
      }
    }
    
    return null;
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
    initialized = false;
  }
}
