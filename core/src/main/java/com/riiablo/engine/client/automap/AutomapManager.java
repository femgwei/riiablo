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
 * 参考: D2MOO D2AutomapCellStrc/D2AutomapLayerStrc, OpenDiablo2 AutoMapRecord
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
  
  /** 玩家颜色 - 绿色 */
  public static final Color COLOR_PLAYER = new Color(0.0f, 1.0f, 0.0f, 1.0f);
  
  /** 队友颜色 - 浅绿色 */
  public static final Color COLOR_PARTY = new Color(0.5f, 1.0f, 0.5f, 1.0f);
  
  /** 怪物颜色 - 红色 */
  public static final Color COLOR_MONSTER = new Color(1.0f, 0.0f, 0.0f, 1.0f);
  
  /** NPC颜色 - 黄色 */
  public static final Color COLOR_NPC = new Color(1.0f, 1.0f, 0.0f, 1.0f);
  
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
  
  /** 当前激活的图层ID */
  private int activeLayerId = -1;
  
  /** AutoMap.txt 数据（可选，用于高级渲染） */
  private AutoMap automapData;
  
  /** 小地图图标精灵（MaxiMap(s).dc6） */
  private DC6 iconSprite;
  
  /** 小地图瓷砖渲染器 */
  private AutomapTileRenderer tileRenderer;
  
  /** 小地图偏移 */
  private float offsetX = 0;
  private float offsetY = 0;
  
  /** 临时向量 */
  private final Vector2 tmpVec = new Vector2();
  
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
  
  // ==================== 实体标记 ====================
  
  /**
   * 清除所有实体标记
   */
  public void clearEntityMarkers() {
    entityMarkers.clear();
  }
  
  /**
   * 添加实体标记
   */
  public void addEntityMarker(int entityId, int type, float worldX, float worldY,
                              String name, Color color, float size) {
    EntityMarker marker = new EntityMarker();
    marker.set(entityId, type, worldX, worldY, name, color, size);
    entityMarkers.add(marker);
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
    for (int i = 0, size = entityMarkers.size; i < size; i++) {
      EntityMarker marker = entityMarkers.get(i);
      
      Color color = marker.color;
      shapes.setColor(color.r, color.g, color.b, alpha);
      
      // 根据类型绘制不同形状
      switch (marker.type) {
        case AutomapIconType.PLAYER:
          // 玩家用较大的圆点
          shapes.circle(marker.worldX, marker.worldY, marker.size);
          // 绘制方向指示器（箭头）
          shapes.triangle(
            marker.worldX, marker.worldY + marker.size + 4,
            marker.worldX - 4, marker.worldY + marker.size,
            marker.worldX + 4, marker.worldY + marker.size
          );
          break;
          
        case AutomapIconType.PARTY_MEMBER:
          // 队友用较小的圆点
          shapes.circle(marker.worldX, marker.worldY, marker.size);
          break;
          
        case AutomapIconType.MONSTER:
          // 怪物用小方块
          float halfSize = marker.size / 2;
          shapes.rect(marker.worldX - halfSize, marker.worldY - halfSize, 
                     marker.size, marker.size);
          break;
          
        case AutomapIconType.NPC:
          // NPC用圆点
          shapes.circle(marker.worldX, marker.worldY, marker.size);
          break;
          
        case AutomapIconType.WAYPOINT:
          // 传送点用菱形
          DebugUtils.drawDiamond(shapes, marker.worldX, marker.worldY, 
                                (int)(marker.size * 2), (int)marker.size);
          break;
          
        case AutomapIconType.SHRINE:
          // 神殿用三角形
          shapes.triangle(
            marker.worldX, marker.worldY + marker.size,
            marker.worldX - marker.size, marker.worldY - marker.size,
            marker.worldX + marker.size, marker.worldY - marker.size
          );
          break;
          
        case AutomapIconType.PORTAL:
          // 传送门用圆环
          shapes.circle(marker.worldX, marker.worldY, marker.size);
          shapes.setColor(0, 0, 0, alpha * 0.5f);
          shapes.circle(marker.worldX, marker.worldY, marker.size * 0.5f);
          break;
          
        default:
          // 默认用圆点
          shapes.circle(marker.worldX, marker.worldY, marker.size);
          break;
      }
    }
  }
  
  /**
   * 渲染实体名称（需要 SpriteBatch）
   */
  public void renderNames(SpriteBatch batch, BitmapFont font) {
    if (!isVisible() || !showNames) return;
    
    for (int i = 0, size = entityMarkers.size; i < size; i++) {
      EntityMarker marker = entityMarkers.get(i);
      if (marker.name != null && !marker.name.isEmpty()) {
        font.setColor(marker.color);
        font.draw(batch, marker.name, marker.worldX, marker.worldY + marker.size + 12);
      }
    }
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
  public void renderWithSprites(PaletteIndexedBatch batch, Map map,
                                int viewStartX, int viewStartY, 
                                int viewWidth, int viewHeight,
                                float screenCenterX, float screenCenterY) {
    if (!isVisible() || !tileRenderer.hasSprite()) return;
    
    AutomapLayer layer = getActiveLayer();
    if (layer == null) return;
    
    float alpha = opacity;
    if (currentMode == MODE_OVERLAY) {
      alpha *= 0.6f;
    }
    
    // 设置透明度
    batch.setColor(1f, 1f, 1f, alpha);
    
    // 渲染物体图标
    for (int i = 0, size = layer.objects.size; i < size; i++) {
      AutomapCell cell = layer.objects.get(i);
      if (cell.cellNo >= 0) {
        tileRenderer.renderTile(batch, cell.cellNo, cell.xPixel, cell.yPixel);
      }
    }
    
    // 渲染额外图标（传送点、神殿等）
    for (int i = 0, size = layer.extras.size; i < size; i++) {
      AutomapCell cell = layer.extras.get(i);
      if (cell.cellNo >= 0) {
        tileRenderer.renderTile(batch, cell.cellNo, cell.xPixel, cell.yPixel);
      }
    }
    
    // 恢复颜色
    batch.setColor(1f, 1f, 1f, 1f);
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
    entityMarkers.clear();
    automapData = null;
    iconSprite = null;
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
  }
  
  /**
   * 清除所有图层数据
   */
  public void clearAllLayers() {
    for (AutomapLayer layer : layers.values()) {
      layer.clear();
    }
    layers.clear();
    activeLayerId = -1;
  }
}
