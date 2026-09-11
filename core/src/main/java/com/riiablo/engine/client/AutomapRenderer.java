package com.riiablo.engine.client;

import com.artemis.BaseSystem;
import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import com.riiablo.Riiablo;
import com.riiablo.camera.IsometricCamera;
import com.riiablo.codec.DC6;
import com.riiablo.engine.client.automap.AutomapCamera;
import com.riiablo.engine.client.automap.AutomapEntityCells;
import com.riiablo.engine.client.automap.AutomapIconType;
import com.riiablo.engine.client.automap.AutomapManager;
import com.riiablo.engine.client.automap.AutomapRenderState;
import com.riiablo.engine.client.automap.AutomapTileRenderer;
import com.riiablo.engine.client.automap.AutomapVisibility;
import com.riiablo.map.Map;
import com.riiablo.map.RenderSystem;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Object;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.profiler.GpuSystem;
import com.riiablo.engine.Engine;

/**
 * 小地图渲染系统
 * 负责渲染游戏的小地图/自动地图覆盖层
 * 
 * 功能包括:
 * - 地形渲染（墙壁、地板、障碍物）
 * - 实体标记（玩家、怪物、NPC、传送点、神殿等）
 * - 迷雾/探索区域显示
 * - 支持多种显示模式（关闭、叠加、全屏、小地图）
 * - 独立摄像头控制（缩放、平移）
 * 
 * 使用 Tab 键切换显示模式
 * 使用 +/- 键缩放
 * 使用方向键平移
 * 使用 Home 键重置
 */
@GpuSystem
public class AutomapRenderer extends BaseSystem {
  private static final String TAG = "AutomapRenderer";
  
  /** automap 窗口大小比例（相对于屏幕） */
  private static final float SIZE_RATIO = 0.5f;
  
  protected RenderSystem renderer;

  @Wire(name = "iso")
  protected IsometricCamera iso;

  @Wire(name = "shapes")
  protected ShapeRenderer shapes;
  
  @Wire(name = "map")
  protected Map map;

  @Wire(failOnNull = false) protected ComponentMapper<Position> mPosition;
  @Wire(failOnNull = false) protected ComponentMapper<Class> mClass;
  @Wire(failOnNull = false) protected ComponentMapper<Monster> mMonster;
  @Wire(failOnNull = false) protected ComponentMapper<Object> mObject;
  @Wire(failOnNull = false) protected ComponentMapper<Interactable> mInteractable;
  private EntitySubscription automapEntities;
  
  /** 小地图管理器 */
  private AutomapManager automapManager;
  
  /** 小地图专用摄像头 */
  private AutomapCamera automapCamera;

  @Override
  protected void initialize() {
    // 初始化小地图管理器
    automapManager = new AutomapManager();
    automapManager.init();
    
    // 创建 automap 专用摄像头
    automapCamera = new AutomapCamera();
    automapEntities = world.getAspectSubscriptionManager()
        .get(Aspect.all(Position.class, Class.class));

    // 尝试加载 AutoMap.txt 数据和小地图图标精灵
    // 说明: 这里直接使用 Riiablo.files / Riiablo.assets，避免在系统间重复传递依赖
    if (Riiablo.files != null && Riiablo.files.AutoMap != null) {
      automapManager.loadAutomapData(Riiablo.files.AutoMap);
    } else {
      // Gdx.app.debug(TAG, "AutoMap.txt not loaded (Riiablo.files is null or AutoMap is null)");
    }

    if (Riiablo.assets != null && Riiablo.assets.isLoaded(AutomapTileRenderer.PATH_MAXIMAP, DC6.class)) {
      DC6 icon = Riiablo.assets.get(AutomapTileRenderer.PATH_MAXIMAP, DC6.class);
      automapManager.loadIconSprite(icon);
    } else {
      // Gdx.app.debug(TAG, "Automap icon sprite not loaded from assets");
    }
    
    // 同步初始模式
    syncModeFromRenderSystem();
    
    // Gdx.app.log(TAG, "AutomapRenderer initialized with independent camera");
  }
  
  /**
   * 获取小地图管理器
   */
  public AutomapManager getAutomapManager() {
    return automapManager;
  }
  
  /**
   * 从 RenderSystem 同步显示模式
   * 注意：RenderSystem使用新的模式系统（TOP_LEFT, TOP_RIGHT, CENTER）
   * AutomapManager仍使用旧模式（OFF, OVERLAY, FULL）
   * 这里将新模式映射到旧模式，或者直接不映射，让RenderSystem直接处理
   */
  private void syncModeFromRenderSystem() {
    // 新的automap系统由RenderSystem直接处理，这里暂时不映射
    // 如果需要AutomapManager的功能，可以在这里添加映射逻辑
    int prevMode = automapManager.getMode();
    if (RenderSystem.AUTOMAP_MODE == RenderSystem.AUTOMAP_MODE_OFF) {
      automapManager.setMode(AutomapManager.MODE_OFF);
    } else {
      // 非关闭模式，设置为OVERLAY模式（半透明叠加）
      automapManager.setMode(AutomapManager.MODE_OVERLAY);
    }
    // 如果模式变化，记录日志
    if (prevMode != automapManager.getMode()) {
      // Gdx.app.log(TAG, "Mode synced: RenderSystem.AUTOMAP_MODE=" + RenderSystem.AUTOMAP_MODE 
      //     + " -> AutomapManager.mode=" + automapManager.getMode()
      //     + " (visible=" + automapManager.isVisible() + ")");
    }
  }
  
  /**
   * 切换小地图显示模式
   */
  public void toggleMode() {
    // 使用 RenderSystem 的模式切换
    RenderSystem.AUTOMAP_MODE = (RenderSystem.AUTOMAP_MODE + 1) % (RenderSystem.AUTOMAP_MODES + 1);
    syncModeFromRenderSystem();
  }

  @Override
  protected void begin() {
    // 同步模式
    syncModeFromRenderSystem();
    
    if (!automapManager.isVisible()) {
      // 小地图不可见时不渲染
      return;
    }
    
    // Gdx.app.log(TAG, "begin(): automap visible, setting up rendering...");
    
    // 初始化摄像头（延迟初始化，确保 iso 已设置）
    if (!automapCamera.isInitialized() && iso != null) {
      automapCamera.initialize(iso);
    }
    
    // 同步摄像头位置
    if (automapCamera.isInitialized()) {
      // 在 CENTER 模式下，将 zoom 设置为 1.0（全屏显示）
      if (RenderSystem.AUTOMAP_MODE == RenderSystem.AUTOMAP_MODE_CENTER) {
        automapCamera.setAutomapZoom(1.0f);
      }
      automapCamera.syncWithMainCamera();
    }
    
    // 清除之前的实体标记
    automapManager.clearEntityMarkers();
    
    // 设置视口裁剪（仅渲染到 automap 窗口区域）
    float screenWidth = Gdx.graphics.getWidth();
    float screenHeight = Gdx.graphics.getHeight();
    float windowWidth, windowHeight;
    float windowX, windowY;
    
    // 根据模式计算窗口大小
    if (RenderSystem.AUTOMAP_MODE == RenderSystem.AUTOMAP_MODE_CENTER) {
      // CENTER 模式：全屏
      windowWidth = screenWidth;
      windowHeight = screenHeight;
    } else {
      // 其他模式：使用 SIZE_RATIO
      windowWidth = screenWidth * SIZE_RATIO;
      windowHeight = screenHeight * SIZE_RATIO;
    }
    
    switch (RenderSystem.AUTOMAP_MODE) {
      case RenderSystem.AUTOMAP_MODE_TOP_LEFT:
        windowX = 0;
        windowY = screenHeight - windowHeight;
        break;
      case RenderSystem.AUTOMAP_MODE_TOP_RIGHT:
        windowX = screenWidth - windowWidth;
        windowY = screenHeight - windowHeight;
        break;
      case RenderSystem.AUTOMAP_MODE_CENTER:
      default:
        windowX = (screenWidth - windowWidth) / 2f;
        windowY = (screenHeight - windowHeight) / 2f;
        break;
    }
    
    // 启用裁剪
    Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
    Gdx.gl.glScissor((int)windowX, (int)windowY, (int)windowWidth, (int)windowHeight);
    
    shapes.identity();
    // 使用 automap 专用摄像头
    if (automapCamera.isInitialized()) {
      shapes.setProjectionMatrix(automapCamera.combined);
    } else {
      shapes.setProjectionMatrix(iso.combined);
    }
    shapes.setAutoShapeType(true);
    shapes.begin(ShapeRenderer.ShapeType.Filled);
  }

  @Override
  protected void end() {
    if (!automapManager.isVisible()) return;
    
    shapes.end();
    
    // 禁用裁剪
    Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
  }

  @Override
  protected void processSystem() {
    // Exploration is simulation state, not a draw-only effect. Continue
    // updating it while the overlay is hidden so opening Automap later shows
    // the rooms the player already traversed.
    updateExplorationFromPlayer();
    if (!automapManager.isVisible()) {
      return;
    }

    // Gdx.app.log(TAG, "processSystem: automap is visible, calling drawAutomap()...");
    
    // 收集实体标记
    collectEntityMarkers();
    
    // 使用原有的地形渲染
    renderer.drawAutomap(shapes);

    renderNativeEntitySprites();
    
    // 渲染增强的实体标记
    renderEnhancedMarkers();
  }

  private void updateExplorationFromPlayer() {
    if (mPosition == null || map == null || Riiablo.game == null) return;
    int playerId = Riiablo.game.player;
    if (playerId == Engine.INVALID_ENTITY || !mPosition.has(playerId)) return;
    Position position = mPosition.get(playerId);
    if (position == null || position.position == null) return;
    Map.Zone zone = map.getZone(position.position.x, position.position.y);
    if (zone == null) return;
    automapManager.updatePlayerPosition(zone.levelId(),
        Math.round(position.position.x), Math.round(position.position.y), map);
  }
  
  /**
   * 收集所有需要在小地图上显示的实体
   * 注意: 当前实现依赖 RenderSystem 的实体渲染
   * 将来可以扩展为独立的实体收集逻辑
   */
  private void collectEntityMarkers() {
    if (automapEntities == null || mPosition == null || mClass == null) return;
    IntBag entities = automapEntities.getEntities();
    int[] ids = entities.getData();
    for (int i = 0, n = entities.size(); i < n; i++) {
      int id = ids[i];
      Position position = mPosition.get(id);
      Class clazz = mClass.get(id);
      if (position == null || clazz == null) continue;
      Map.Zone entityZone = map == null ? null : map.getZone(position.position.x, position.position.y);
      if (!AutomapVisibility.isEntityVisible(entityZone, position.position.x, position.position.y)) continue;
      String name = null;
      if (mMonster != null && mMonster.has(id) && mMonster.get(id).monstats != null) {
        Monster monster = mMonster.get(id);
        name = monster.monstats.NameStr;
        boolean npc = monster.monstats.npc || (mInteractable != null && mInteractable.has(id));
        int cell = monster.monstats2 == null ? -1 : AutomapEntityCells.monsterCell(monster.monstats2);
        int type = npc ? AutomapIconType.NPC : AutomapIconType.MONSTER;
        automapManager.addNativeEntityMarker(id, type, position.position.x, position.position.y,
            name, npc ? AutomapManager.COLOR_NPC : AutomapManager.COLOR_MONSTER,
            npc ? 5 : 4, cell);
      } else if (mObject != null && mObject.has(id) && mObject.get(id).base != null) {
        Object object = mObject.get(id);
        int cell = AutomapEntityCells.objectCell(object.base);
        automapManager.addNativeEntityMarker(id, AutomapIconType.OBJECT,
            position.position.x, position.position.y, object.base.Name,
            AutomapManager.COLOR_DOOR, 4, cell);
      } else if (clazz.type == Class.Type.PLR) {
        automapManager.addPlayerMarker(id, position.position.x, position.position.y, null);
      }
    }
  }

  private void renderNativeEntitySprites() {
    if (Riiablo.batch == null || shapes == null) return;
    // Do not nest a SpriteBatch pass.  Another system owning the batch must
    // finish its pass before Automap is rendered.
    if (Riiablo.batch.isDrawing()) {
      if (Gdx.app != null) {
        Gdx.app.debug(TAG, "Skipping native entity sprites: SpriteBatch is already drawing");
      }
      return;
    }

    // Keep the exact ShapeRenderer projection/type state across the sprite
    // pass.  This method is called from processSystem while shapes is active;
    // any failure in the native DC6 renderer must still restore that state so
    // subsequent map/UI systems can render normally.
    Matrix4 previousProjection = new Matrix4(shapes.getProjectionMatrix());
    AutomapRenderState.Phase phase = AutomapRenderState.Phase.SHAPES;
    boolean batchBegun = false;
    shapes.end();
    try {
      phase = AutomapRenderState.enterSprites(phase);
      Riiablo.batch.setProjectionMatrix(automapCamera != null && automapCamera.isInitialized()
          ? automapCamera.combined : iso.combined);
      Riiablo.batch.begin();
      batchBegun = true;
      automapManager.renderNativeEntitySprites(Riiablo.batch, automapManager.opacity);
    } finally {
      if (batchBegun && Riiablo.batch.isDrawing()) {
        Riiablo.batch.end();
      }
      if (phase == AutomapRenderState.Phase.SPRITES) {
        AutomapRenderState.leaveSprites(phase);
      }
      shapes.setProjectionMatrix(previousProjection);
      shapes.begin(ShapeRenderer.ShapeType.Filled);
    }
  }
  
  /**
   * 渲染增强的实体标记（使用 AutomapManager 的标记系统）
   */
  private void renderEnhancedMarkers() {
    // AutomapManager 会处理实体的增强渲染
    // 目前使用 RenderSystem 的基础渲染，将来可以替换为更复杂的图标渲染
  }
  
  /**
   * 更新玩家位置，用于探索区域追踪
   * 
   * @param levelId 当前关卡ID
   * @param playerX 玩家X坐标（子瓷砖）
   * @param playerY 玩家Y坐标（子瓷砖）
   */
  public void updatePlayerPosition(int levelId, int playerX, int playerY) {
    // Prefer native RoomEx activation visibility; manager falls back to the
    // legacy radius when a map or exported topology is unavailable.
    automapManager.updatePlayerPosition(levelId, playerX, playerY, map);
  }
  
  /**
   * 添加传送点标记
   */
  public void addWaypointMarker(float worldX, float worldY) {
    automapManager.addWaypointMarker(worldX, worldY);
  }
  
  /**
   * 添加神殿标记
   */
  public void addShrineMarker(float worldX, float worldY) {
    automapManager.addShrineMarker(worldX, worldY);
  }
  
  /**
   * 添加传送门标记
   */
  public void addPortalMarker(int entityId, float worldX, float worldY, String owner) {
    automapManager.addPortalMarker(entityId, worldX, worldY, owner);
  }
  
  /**
   * 设置是否显示名称
   */
  public void setShowNames(boolean show) {
    automapManager.showNames = show;
  }
  
  /**
   * 切换名称显示
   */
  public void toggleShowNames() {
    automapManager.showNames = !automapManager.showNames;
  }
  
  /**
   * 设置是否显示队友
   */
  public void setShowPartyMembers(boolean show) {
    automapManager.showPartyMembers = show;
  }
  
  /**
   * 切换队友显示
   */
  public void toggleShowPartyMembers() {
    automapManager.showPartyMembers = !automapManager.showPartyMembers;
  }
  
  /**
   * 居中小地图到玩家位置
   */
  public void centerOnPlayer() {
    automapManager.centerOnPlayer();
  }
  
  @Override
  protected void dispose() {
    super.dispose();
    if (automapManager != null) {
      automapManager.dispose();
      automapManager = null;
    }
  }
  
  //==========================================================================
  // 摄像头控制方法（供按键绑定使用）
  //==========================================================================
  
  /**
   * 放大 (显示更详细)
   */
  public void zoomIn() {
    // 使用 RenderSystem 的缩放系统
    com.riiablo.map.RenderSystem.automapZoomIn();
  }
  
  /**
   * 缩小 (显示更大范围)
   */
  public void zoomOut() {
    // 使用 RenderSystem 的缩放系统
    com.riiablo.map.RenderSystem.automapZoomOut();
  }
  
  /**
   * 向上平移
   */
  public void panUp() {
    // 使用 RenderSystem 的偏移系统
    com.riiablo.map.RenderSystem.automapUp();
  }
  
  /**
   * 向下平移
   */
  public void panDown() {
    // 使用 RenderSystem 的偏移系统
    com.riiablo.map.RenderSystem.automapDown();
  }
  
  /**
   * 向左平移
   */
  public void panLeft() {
    // 使用 RenderSystem 的偏移系统
    com.riiablo.map.RenderSystem.automapLeft();
  }
  
  /**
   * 向右平移
   */
  public void panRight() {
    // 使用 RenderSystem 的偏移系统
    com.riiablo.map.RenderSystem.automapRight();
  }
  
  /**
   * 重置 (缩放和偏移)
   */
  public void reset() {
    // 使用 RenderSystem 的重置系统
    com.riiablo.map.RenderSystem.automapReset();
  }
  
  /**
   * 获取 automap 摄像头
   */
  public AutomapCamera getAutomapCamera() {
    return automapCamera;
  }
}
