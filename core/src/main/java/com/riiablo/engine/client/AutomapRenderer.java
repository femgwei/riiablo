package com.riiablo.engine.client;

import com.artemis.BaseSystem;
import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.files.FileHandle;
import java.io.IOException;

import com.riiablo.Riiablo;
import com.riiablo.Cvars;
import com.riiablo.camera.IsometricCamera;
import com.riiablo.codec.DC6;
import com.riiablo.engine.client.automap.AutomapCamera;
import com.riiablo.engine.client.automap.AutomapExplorationStore;
import com.riiablo.engine.client.automap.AutomapEntityCells;
import com.riiablo.engine.client.automap.AutomapIconType;
import com.riiablo.engine.client.automap.AutomapManager;
import com.riiablo.engine.client.automap.AutomapMarkerPolicy;
import com.riiablo.engine.client.automap.AutomapOptions;
import com.riiablo.engine.client.automap.AutomapRenderState;
import com.riiablo.engine.client.automap.AutomapTileRenderer;
import com.riiablo.engine.client.automap.AutomapVisibility;
import com.riiablo.engine.client.automap.AutomapViewport;
import com.riiablo.map.Map;
import com.riiablo.map.RenderSystem;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Item;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Object;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Networked;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.Warp;
import com.riiablo.engine.server.party.PartyRelation;
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
  @Wire(failOnNull = false) protected ComponentMapper<Corpse> mCorpse;
  @Wire(failOnNull = false) protected ComponentMapper<Missile> mMissile;
  @Wire(failOnNull = false) protected ComponentMapper<Item> mItem;
  @Wire(failOnNull = false) protected ComponentMapper<Object> mObject;
  @Wire(failOnNull = false) protected ComponentMapper<Interactable> mInteractable;
  @Wire(failOnNull = false) protected ComponentMapper<Warp> mWarp;
  @Wire(failOnNull = false) protected ComponentMapper<Networked> mNetworked;
  @com.artemis.annotations.SkipWire
  protected ClientNetworkReceiver clientNetworkReceiver;
  private EntitySubscription automapEntities;
  
  /** 小地图管理器 */
  private AutomapManager automapManager;
  
  /** 小地图专用摄像头 */
  private AutomapCamera automapCamera;
  private final Rectangle viewport = new Rectangle();
  private final Matrix4 renderProjection = new Matrix4();
  private boolean wasVisible;
  private boolean nativeAutomapLoaded;
  private AutomapExplorationStore.MaFile loadedNativeAutomap;
  private String nativeAutomapCharacter;
  private int nativeAutomapDifficulty = -1;
  private int nativeAutomapSeed;
  private String lastOptionsDiagnostic;
  private int lastAutomapLevelId = Integer.MIN_VALUE;
  private boolean lastAutomapTown;

  @Override
  protected void initialize() {
    // 初始化小地图管理器
    automapManager = new AutomapManager();
    automapManager.init();
    
    // 创建 automap 专用摄像头
    automapCamera = new AutomapCamera();
    clientNetworkReceiver = world.getSystem(ClientNetworkReceiver.class);
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

  private void loadNativeAutomapSave() {
    if (nativeAutomapLoaded) return;
    if (Riiablo.saves == null || Riiablo.charData == null) return;
    String name = Riiablo.charData.name;
    int difficulty = Riiablo.charData.diff;
    if (name == null || name.isEmpty() || difficulty < 0 || difficulty > 3) return;
    // Mark the load complete only after all prerequisites and character
    // identity are available.  GameScreen/world construction can call this
    // before the global save handle has been installed; keeping the flag
    // false allows the next render/save transition to retry.
    nativeAutomapLoaded = true;
    nativeAutomapCharacter = name;
    nativeAutomapDifficulty = difficulty;
    nativeAutomapSeed = map != null ? map.seed() : Riiablo.charData.mapSeed;
    try {
      FileHandle mapFile = Riiablo.saves.child(name + ".map");
      if (mapFile.exists()) {
        AutomapExplorationStore.MapSeeds seeds = AutomapExplorationStore.readMap(mapFile);
        if (seeds.seed(difficulty) != 0 && seeds.seed(difficulty) != nativeAutomapSeed) {
          Gdx.app.log(TAG, "Ignoring native Automap save with mismatched map seed");
          return;
        }
      }
      FileHandle maFile = Riiablo.saves.child(name + ".ma" + difficulty);
      if (maFile.exists()) {
        loadedNativeAutomap = AutomapExplorationStore.readMa(maFile);
        automapManager.loadNativeAutomap(loadedNativeAutomap);
        Gdx.app.log(TAG, String.format(
            "Loaded native Automap: path=%s difficulty=%d seed=%d bytes=%d",
            maFile.file().getAbsolutePath(), difficulty, nativeAutomapSeed, maFile.length()));
      }
    } catch (IOException | RuntimeException e) {
      Gdx.app.error(TAG, "Failed to load native Automap save", e);
    }
  }

  /** Saves the current difficulty's native Automap sidecars. */
  public void saveNativeAutomap() {
    if (Riiablo.saves == null || automapManager == null) return;
    loadNativeAutomapSave();
    // A quit/menu transition can happen between fixed simulation ticks. Make
    // the current room reveal authoritative before converting runtime cells
    // back into the native .ma representation.
    updateExplorationFromPlayer();
    // Shutdown/menu transitions can clear or reuse the shared CharData before
    // this system is disposed. Persist against the identity captured at load.
    String name = nativeAutomapCharacter;
    int difficulty = nativeAutomapDifficulty;
    int mapSeed = nativeAutomapSeed;
    if (name == null || name.isEmpty() || difficulty < 0 || difficulty > 3) return;
    try {
      FileHandle mapFile = Riiablo.saves.child(name + ".map");
      AutomapExplorationStore.MapSeeds seeds = mapFile.exists()
          ? AutomapExplorationStore.readMap(mapFile) : new AutomapExplorationStore.MapSeeds();
      seeds.seeds[difficulty] = mapSeed;
      AutomapExplorationStore.writeMap(mapFile, seeds);
      loadedNativeAutomap = automapManager.createNativeAutomap(loadedNativeAutomap);
      AutomapExplorationStore.writeMa(Riiablo.saves.child(name + ".ma" + difficulty),
          loadedNativeAutomap);
      if (Gdx.app != null) {
        FileHandle maFile = Riiablo.saves.child(name + ".ma" + difficulty);
        Gdx.app.log(TAG, String.format(
            "Saved native Automap: path=%s difficulty=%d seed=%d bytes=%d",
            maFile.file().getAbsolutePath(), difficulty, mapSeed,
            maFile.length()));
      }
    } catch (IOException | RuntimeException e) {
      Gdx.app.error(TAG, "Failed to save native Automap save", e);
    }
  }
  
  /**
   * 从 RenderSystem 同步显示模式
   * 注意：RenderSystem使用新的模式系统（TOP_LEFT, TOP_RIGHT, CENTER）
   * AutomapManager仍使用旧模式（OFF, OVERLAY, FULL）
   * 这里将新模式映射到旧模式，或者直接不映射，让RenderSystem直接处理
   */
  private void syncModeFromRenderSystem() {
    int prevMode = automapManager.getMode();
    boolean miniMap = false;
    switch (RenderSystem.AUTOMAP_MODE) {
      case RenderSystem.AUTOMAP_MODE_TOP_LEFT:
      case RenderSystem.AUTOMAP_MODE_TOP_RIGHT:
        automapManager.setMode(AutomapManager.MODE_MINIMAP);
        miniMap = true;
        break;
      case RenderSystem.AUTOMAP_MODE_CENTER:
        automapManager.setMode(AutomapManager.MODE_FULL);
        break;
      case RenderSystem.AUTOMAP_MODE_OFF:
      default:
        automapManager.setMode(AutomapManager.MODE_OFF);
        break;
    }

    loadNativeAutomapSave();
    if (automapCamera != null) automapCamera.setMiniMapMode(miniMap);
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
    applyOptions();
    // 同步模式
    syncModeFromRenderSystem();
    
    if (!automapManager.isVisible()) {
      if (wasVisible && automapManager.centered && automapCamera != null) {
        automapCamera.reset();
      }
      wasVisible = false;
      // 小地图不可见时不渲染
      return;
    }
    wasVisible = true;
    
    // Gdx.app.log(TAG, "begin(): automap visible, setting up rendering...");
    
    AutomapViewport.calculate(RenderSystem.AUTOMAP_MODE,
        Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), viewport);

    // 初始化摄像头（延迟初始化，确保 iso 已设置）
    if (!automapCamera.isInitialized() && iso != null) {
      automapCamera.initialize(iso);
    }
    
    // 同步摄像头位置
    if (automapCamera.isInitialized()) {
      automapCamera.syncWithMainCamera();
      automapManager.setPointerRadius(Math.min(viewport.width, viewport.height)
          * automapCamera.zoom * 0.4f);
    }
    
    // 清除之前的实体标记
    automapManager.clearEntityMarkers();
    
    // 设置视口裁剪（仅渲染到 automap 窗口区域）
    // 启用裁剪
    Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
    Gdx.gl.glScissor((int) viewport.x, (int) viewport.y,
        Math.max(1, (int) viewport.width), Math.max(1, (int) viewport.height));
    
    shapes.identity();
    updateRenderProjection();
    shapes.setProjectionMatrix(renderProjection);
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
    // The descriptor is queued by GameScreen and may use a normalized path;
    // AutomapTileRenderer performs a lazy lookup again on the first frame.
    automapManager.getTileRenderer().ensureSpriteLoaded();

    // Gdx.app.log(TAG, "processSystem: automap is visible, calling drawAutomap()...");
    
    // 收集实体标记
    collectEntityMarkers();

    // Native AutoMap.txt/MaxiMap.dc6 is the primary terrain path.  The old
    // RenderSystem line/icon renderer is only retained when no native cells
    // can be drawn (e.g. reduced resources), so production clients do not
    // paint the custom geometric map on top of DC6 art.
    int nativeTerrain = renderNativeTerrainSprites();
    if (nativeTerrain == 0) renderer.drawAutomap(shapes);

    renderNativeEntitySprites();
    renderNames();
    
    // 渲染增强的实体标记
    renderEnhancedMarkers();
  }

  private void updateRenderProjection() {
    Matrix4 base = automapCamera.isInitialized() ? automapCamera.combined : iso.combined;
    float screenWidth = Math.max(1f, Gdx.graphics.getWidth());
    float screenHeight = Math.max(1f, Gdx.graphics.getHeight());
    float centerX = viewport.x + viewport.width * 0.5f;
    float centerY = viewport.y + viewport.height * 0.5f;
    float ndcX = centerX * 2f / screenWidth - 1f;
    float ndcY = centerY * 2f / screenHeight - 1f;
    renderProjection.setToTranslation(ndcX, ndcY, 0f).mul(base);
  }

  private void updateExplorationFromPlayer() {
    if (mPosition == null || map == null || Riiablo.game == null) return;
    int playerId = Riiablo.game.player;
    if (playerId == Engine.INVALID_ENTITY || !mPosition.has(playerId)) return;
    Position position = mPosition.get(playerId);
    if (position == null || position.position == null) return;
    Map.Zone zone = map.getZone(position.position.x, position.position.y);
    if (zone == null) return;
    int levelId = zone.levelId();
    boolean town = zone.isTown();
    if ((levelId != lastAutomapLevelId || town != lastAutomapTown) && Gdx.app != null) {
      Gdx.app.log(TAG, "[AUTOMAP_LEVEL] levelId=" + levelId
          + " town=" + town
          + " x=" + Math.round(position.position.x)
          + " y=" + Math.round(position.position.y));
      lastAutomapLevelId = levelId;
      lastAutomapTown = town;
    }
    automapManager.updatePlayerPosition(levelId,
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
      if (show(Cvars.Client.Automap.ShowCorpses) && mCorpse != null && mCorpse.has(id)) {
        Monster monster = mMonster != null && mMonster.has(id) ? mMonster.get(id) : null;
        name = monster == null || monster.monstats == null ? null : monster.monstats.NameStr;
        automapManager.addEntityMarker(id, AutomapIconType.CORPSE,
            position.position.x, position.position.y, name,
            AutomapManager.COLOR_CORPSE, 4);
      } else if (show(Cvars.Client.Automap.ShowMissiles)
          && mMissile != null && mMissile.has(id)) {
        Missile missile = mMissile.get(id);
        name = missile == null || missile.missile == null ? null : missile.missile.Missile;
        automapManager.addEntityMarker(id, AutomapIconType.MISSILE,
            position.position.x, position.position.y, name,
            AutomapManager.COLOR_MISSILE, 3);
      } else if (show(Cvars.Client.Automap.ShowItems) && mItem != null && mItem.has(id)) {
        Item item = mItem.get(id);
        if (item == null || !shouldDisplayItemMarker(item.item)) continue;
        name = item == null || item.item == null ? null : item.item.getNameString();
        automapManager.addEntityMarker(id, AutomapIconType.ITEM,
            position.position.x, position.position.y, name,
            AutomapManager.COLOR_ITEM, 4);
      } else if (mWarp != null && mWarp.has(id)
          && mWarp.get(id).townPortalOwner >= 0) {
        // Dynamic Town Portals have a logical Warp entity and a separate
        // object entity for the animated visual.  The logical endpoint is the
        // authoritative automap marker; add it before the visual object and
        // let AutomapManager coalesce the two at the same location.
        automapManager.addPortalMarker(id, position.position.x, position.position.y, null);
      } else if (mMonster != null && mMonster.has(id) && mMonster.get(id).monstats != null) {
        Monster monster = mMonster.get(id);
        name = monster.monstats.NameStr;
        if (monster.monstats.npc && Riiablo.string != null) {
          name = Riiablo.string.lookup(name);
        }
        boolean interactable = mInteractable != null && mInteractable.has(id);
        boolean npc = monster.monstats.npc;
        if (npc && (!AutomapMarkerPolicy.shouldDisplayNpc(monster.monstats, interactable)
            || !isNpcWithinRange(position.position.x, position.position.y))) continue;
        if (!AutomapMarkerPolicy.shouldDisplayMonster(
            monster.monstats, monster.monstats2, npc)) continue;
        int cell = monster.monstats2 == null ? -1 : AutomapEntityCells.monsterCell(monster.monstats2);
        int type = npc ? AutomapIconType.NPC : show(Cvars.Client.Automap.ShowMonsterRanks)
            ? AutomapMarkerPolicy.monsterType(monster.rank) : AutomapIconType.MONSTER;
        automapManager.addNativeEntityMarker(id, type, position.position.x, position.position.y,
            name, npc ? AutomapManager.COLOR_NPC : monsterColor(type),
            npc ? 5 : 4, cell);
      } else if (mObject != null && mObject.has(id) && mObject.get(id).base != null) {
        Object object = mObject.get(id);
        boolean portal = AutomapMarkerPolicy.isTownPortalObject(object.base);
        // Portal visuals are animated object sprites, not MaxiMap terrain
        // cells.  Never interpret their table value as a native cell: that
        // was the source of the yellow NPC-like fallback marker.
        int cell = portal ? -1 : AutomapEntityCells.objectCell(object.base);
        int type = portal ? AutomapIconType.PORTAL
            : show(Cvars.Client.Automap.ShowQuestIndicators)
                ? AutomapMarkerPolicy.objectType(object.base) : AutomapIconType.OBJECT;
        if (portal) {
          automapManager.addPortalMarker(id, position.position.x, position.position.y, null);
          continue;
        }
        // AutoMap=0 means this object has no Automap representation. Ordinary
        // scenery such as camp torches must stay invisible instead of turning
        // into a generic geometric marker. Enhanced entrances/quest targets
        // remain eligible for their explicit overlay markers.
        if (!AutomapMarkerPolicy.shouldDisplayObject(cell, type, object.base,
            object.mode, automapManager.isHackMapEnabled())) continue;
        // Town waypoints and the stash already exist in the generated native
        // terrain cell lists. The runtime object is interactive state, not a
        // second Automap picture; suppress its duplicate native marker.
        if (cell >= 0 && entityZone != null
            && !AutomapMarkerPolicy.requiresColorOverlay(type)
            && automapManager.hasNearbyTerrainCell(entityZone.levelId(), cell,
                position.position.x, position.position.y, 16f)) {
          continue;
        }
        automapManager.addNativeEntityMarker(id, type,
            position.position.x, position.position.y, object.base.Name,
            objectColor(type), type == AutomapIconType.OBJECT ? 4 : 6, cell);
      } else if (clazz.type == Class.Type.PLR) {
        if (id == Riiablo.game.player) {
          automapManager.addPlayerMarker(id, position.position.x, position.position.y,
              Riiablo.charData == null ? null : Riiablo.charData.name);
        } else if (isPartyMember(id)) {
          automapManager.addPartyMarker(id, position.position.x, position.position.y,
              partyMemberName(id));
        }
      }
    }
  }

  /** Ground gold is rendered as a quantity label, not as an automap item icon. */
  static boolean shouldDisplayItemMarker(com.riiablo.item.Item item) {
    return item != null && item.code != null && !"gld".equalsIgnoreCase(item.code);
  }

  private boolean isNpcWithinRange(float npcX, float npcY) {
    if (Riiablo.game == null || mPosition == null) return false;
    int playerId = Riiablo.game.player;
    if (playerId == Engine.INVALID_ENTITY || !mPosition.has(playerId)) return false;
    Position player = mPosition.get(playerId);
    return player != null && player.position != null
        && AutomapMarkerPolicy.isNpcWithinScreenRange(
            player.position.x, player.position.y, npcX, npcY,
            Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
  }

  private static boolean show(com.riiablo.cvar.Cvar<Boolean> option) {
    return Boolean.TRUE.equals(option.get());
  }

  private static com.badlogic.gdx.graphics.Color monsterColor(int type) {
    switch (type) {
      case AutomapIconType.CHAMPION: return AutomapManager.COLOR_CHAMPION;
      case AutomapIconType.UNIQUE: return AutomapManager.COLOR_UNIQUE;
      case AutomapIconType.MINION: return AutomapManager.COLOR_MINION;
      case AutomapIconType.BOSS: return AutomapManager.COLOR_BOSS;
      default: return AutomapManager.COLOR_MONSTER;
    }
  }

  private static com.badlogic.gdx.graphics.Color objectColor(int type) {
    if (type == AutomapIconType.QUEST) return AutomapManager.COLOR_QUEST;
    if (type == AutomapIconType.ENTRANCE || type == AutomapIconType.EXIT) {
      return AutomapManager.COLOR_ENTRANCE;
    }
    return AutomapManager.COLOR_DOOR;
  }

  private boolean isPartyMember(int localEntityId) {
    ClientPartyState.Member member = partyMember(localEntityId);
    return member != null && member.relation == PartyRelation.PARTY_MEMBER;
  }

  private String partyMemberName(int localEntityId) {
    ClientPartyState.Member member = partyMember(localEntityId);
    return member == null ? null : member.name;
  }

  private ClientPartyState.Member partyMember(int localEntityId) {
    if (clientNetworkReceiver == null || mNetworked == null || !mNetworked.has(localEntityId)) {
      return null;
    }
    Networked networked = mNetworked.get(localEntityId);
    return networked == null ? null
        : clientNetworkReceiver.partyState().get(networked.serverId);
  }

  private void applyOptions() {
    int mode = Cvars.Client.Automap.Mode.get() == null
        ? RenderSystem.AUTOMAP_MODE_CENTER : Cvars.Client.Automap.Mode.get();
    RenderSystem.setAutomapPreferredMode(mode);
    boolean fade = Boolean.TRUE.equals(Cvars.Client.Automap.Fade.get());
    float opacity = AutomapOptions.opacity(fade);
    RenderSystem.setAutomapOpacity(opacity);
    automapManager.opacity = opacity;
    automapManager.centered = Boolean.TRUE.equals(
        Cvars.Client.Automap.CenterWhenCleared.get());
    automapManager.showPartyMembers = Boolean.TRUE.equals(
        Cvars.Client.Automap.ShowParty.get());
    automapManager.showNames = Boolean.TRUE.equals(
        Cvars.Client.Automap.ShowNames.get());
    automapManager.showMinimapPointers = Boolean.TRUE.equals(
        Cvars.Client.Automap.ShowMinimapPointers.get());
    automapManager.setHackMapEnabled(
        Boolean.TRUE.equals(Cvars.Client.Automap.HackMap.get()), Riiablo.home);
    String diagnostic = "mode=" + mode + " fade=" + fade + " opacity=" + opacity
        + " hackMap=" + automapManager.isHackMapEnabled()
        + " hackMapIcons=" + automapManager.getHackMapIconCount();
    if (!diagnostic.equals(lastOptionsDiagnostic) && Gdx.app != null) {
      Gdx.app.debug(TAG, "[AUTOMAP_OPTIONS] " + diagnostic);
      lastOptionsDiagnostic = diagnostic;
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
      Riiablo.batch.setProjectionMatrix(renderProjection);
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

  private void renderNames() {
    if (!automapManager.showNames || Riiablo.batch == null || shapes == null
        || Riiablo.fonts == null || Riiablo.batch.isDrawing()) return;
    Matrix4 previousProjection = new Matrix4(shapes.getProjectionMatrix());
    boolean batchBegun = false;
    shapes.end();
    try {
      Riiablo.batch.setProjectionMatrix(renderProjection);
      Riiablo.batch.begin();
      batchBegun = true;
      automapManager.renderNames(Riiablo.batch, Riiablo.fonts.fontformal10);
    } finally {
      if (batchBegun && Riiablo.batch.isDrawing()) Riiablo.batch.end();
      shapes.setProjectionMatrix(previousProjection);
      shapes.begin(ShapeRenderer.ShapeType.Filled);
    }
  }

  /** Draws native terrain/object cells in the same camera space as the map. */
  private int renderNativeTerrainSprites() {
    if (Riiablo.batch == null || shapes == null || automapManager == null) return 0;
    if (Riiablo.batch.isDrawing()) return 0;
    Matrix4 previousProjection = new Matrix4(shapes.getProjectionMatrix());
    boolean batchBegun = false;
    shapes.end();
    try {
      Riiablo.batch.setProjectionMatrix(renderProjection);
      Riiablo.batch.begin();
      batchBegun = true;
      return automapManager.renderWithSprites(Riiablo.batch, map, 0, 0, 0, 0, 0, 0);
    } finally {
      if (batchBegun && Riiablo.batch.isDrawing()) Riiablo.batch.end();
      shapes.setProjectionMatrix(previousProjection);
      shapes.begin(ShapeRenderer.ShapeType.Filled);
    }
  }
  
  /**
   * 渲染增强的实体标记（使用 AutomapManager 的标记系统）
   */
  private void renderEnhancedMarkers() {
    if (automapManager == null || shapes == null) return;
    // Native D2 draws player/NPC markers as vectors after the MaxiMap terrain
    // pass. Enhanced HackMap-only categories use their configured geometric
    // marker when no native terrain cell exists.
    automapManager.render(shapes, map, 0, 0, 0, 0, 0, 0);
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
    if (automapCamera != null) automapCamera.resetOffset();
    RenderSystem.automapReset();
  }
  
  @Override
  protected void dispose() {
    super.dispose();
    saveNativeAutomap();
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
    if (automapCamera != null) automapCamera.zoomIn();
    RenderSystem.automapZoomIn();
  }
  
  /**
   * 缩小 (显示更大范围)
   */
  public void zoomOut() {
    if (automapCamera != null) automapCamera.zoomOut();
    RenderSystem.automapZoomOut();
  }
  
  /**
   * 向上平移
   */
  public void panUp() {
    if (automapCamera != null) automapCamera.panUp();
    RenderSystem.automapUp();
  }
  
  /**
   * 向下平移
   */
  public void panDown() {
    if (automapCamera != null) automapCamera.panDown();
    RenderSystem.automapDown();
  }
  
  /**
   * 向左平移
   */
  public void panLeft() {
    if (automapCamera != null) automapCamera.panLeft();
    RenderSystem.automapLeft();
  }
  
  /**
   * 向右平移
   */
  public void panRight() {
    if (automapCamera != null) automapCamera.panRight();
    RenderSystem.automapRight();
  }
  
  /**
   * 重置 (缩放和偏移)
   */
  public void reset() {
    if (automapCamera != null) automapCamera.reset();
    RenderSystem.automapReset();
  }
  
  /**
   * 获取 automap 摄像头
   */
  public AutomapCamera getAutomapCamera() {
    return automapCamera;
  }
}
