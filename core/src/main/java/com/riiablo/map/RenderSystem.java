package com.riiablo.map;

import java.util.Arrays;
import java.util.Comparator;
import org.apache.commons.lang3.StringUtils;

import com.artemis.Aspect;
import com.artemis.BaseEntitySystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.annotations.All;
import com.artemis.utils.IntBag;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Bits;
import com.badlogic.gdx.utils.Pools;
import com.badlogic.gdx.utils.ScreenUtils;

import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.camera.IsometricCamera;
import com.riiablo.codec.Animation;
import com.riiablo.codec.util.BBox;
import com.riiablo.engine.Direction;
import com.riiablo.engine.Engine;
import com.riiablo.engine.client.component.AnimationWrapper;
import com.riiablo.engine.client.component.BBoxWrapper;
import com.riiablo.engine.client.component.Hovered;
import com.riiablo.engine.client.component.Overlay;
import com.riiablo.engine.client.component.Selectable;
import com.riiablo.engine.server.component.AIWrapper;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Classname;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Networked;
import com.riiablo.engine.server.component.Object;
import com.riiablo.engine.server.component.Position;
import com.riiablo.graphics.BlendMode;
import com.riiablo.graphics.PaletteIndexedBatch;
import com.riiablo.map.DT1.Tile;
import com.riiablo.profiler.GpuSystem;
import com.riiablo.util.DebugUtils;

@GpuSystem
@All(AnimationWrapper.class)
public class RenderSystem extends BaseEntitySystem {
  private static final String TAG = "RenderSystem";
  private static final boolean DEBUG          = true;
  private static final boolean DEBUG_MATH     = DEBUG && !true;
  private static final boolean DEBUG_BUFFER   = DEBUG && true;
  private static final boolean DEBUG_SUBTILE  = DEBUG && !true;
  private static final boolean DEBUG_TILE     = DEBUG && !true;
  private static final boolean DEBUG_CAMERA   = DEBUG && true;
  private static final boolean DEBUG_OVERSCAN = DEBUG && true;
  private static final boolean DEBUG_GRID     = DEBUG && true;
  private static final boolean DEBUG_WALKABLE = DEBUG && !true;
  private static final boolean DEBUG_MATERIAL = DEBUG && !true;
  private static final boolean DEBUG_SPECIAL  = DEBUG && true;
  private static final boolean DEBUG_MOUSE    = DEBUG && true;
  private static final boolean DEBUG_POPPADS  = DEBUG && !true;
  private static final boolean DEBUG_ENTITIES = DEBUG && true;
  private static final boolean DEBUG_SELECT   = DEBUG && true;
  private static final boolean DEBUG_CELLS    = DEBUG && !true;

  public static boolean RENDER_DEBUG_SUBTILE  = DEBUG_SUBTILE;
  public static boolean RENDER_DEBUG_TILE     = DEBUG_TILE;
  public static boolean RENDER_DEBUG_CAMERA   = DEBUG_CAMERA;
  public static int     RENDER_DEBUG_OVERSCAN = DEBUG_OVERSCAN ? 0b010 : 0;
  public static int     RENDER_DEBUG_GRID     = DEBUG_GRID ? 3 : 0;
  public static int     RENDER_DEBUG_WALKABLE = DEBUG_WALKABLE ? 1 : 0;
  public static int     RENDER_DEBUG_MATERIAL = DEBUG_MATERIAL ? 1 : 0;
  public static boolean RENDER_DEBUG_SPECIAL  = DEBUG_SPECIAL;
  public static boolean RENDER_DEBUG_SELECT   = DEBUG_SELECT;
  public static int     RENDER_DEBUG_CELLS    = DEBUG_CELLS ? 1 : 0;
  public static boolean RENDER_DEBUG_ENTITIES = DEBUG_ENTITIES;

  // Automap display modes: 0=off, 1=top-left, 2=top-right, 3=center
  public static int AUTOMAP_MODE = 0;
  public static final int AUTOMAP_MODE_OFF = 0;
  public static final int AUTOMAP_MODE_TOP_LEFT = 1;
  public static final int AUTOMAP_MODE_TOP_RIGHT = 2;
  public static final int AUTOMAP_MODE_CENTER = 3;
  public static final int AUTOMAP_MODES = 3;
  
  // automap窗口大小：屏幕的1/2
  private static final float AUTOMAP_SIZE_RATIO = 0.5f;
  // automap 等距投影基础缩放系数（标准 45° 等距投影）
  // 等价于主渲染中的坐标：isoX = (worldX - worldY) * 0.5, isoY = (worldX + worldY) * 0.25
  private static final float AUTOMAP_ISO_X_SCALE = 0.5f;
  private static final float AUTOMAP_ISO_Y_SCALE = 0.25f;
  
  // 首次打开automap的标志（用于调试日志）
  private static boolean automapFirstRender = false;

  // Automap color definitions - based on original Diablo/D2 style
  // Player arrow color - light blue
  private static final Color AUTOMAP_PLAYER_COLOR = new Color(0x80c0ffff);
  // Wall color - dark yellow (COLOR_DIM from D1/D2)
  private static final Color AUTOMAP_WALL_COLOR = new Color(0xa0a060ff);
  // Bright elements (doors, stairs) - bright yellow (COLOR_BRIGHT from D1/D2)
  private static final Color AUTOMAP_BRIGHT_COLOR = new Color(0xffff00ff);
  // Monster color - red (for "+" marker)
  private static final Color AUTOMAP_MONSTER_COLOR = new Color(0xff0000ff);
  // Dead monster color - gray (for dead monsters)
  private static final Color AUTOMAP_DEAD_MONSTER_COLOR = new Color(0x808080ff);
  // NPC color - gold (for "<N>" marker with name)
  private static final Color AUTOMAP_NPC_COLOR = new Color(0xffd700ff);
  // Warp/Portal color - light blue (for "<P>" marker)
  private static final Color AUTOMAP_WARP_COLOR = new Color(0x80c0ffff);
  // Mercenary color - light blue (for "+" marker)
  private static final Color AUTOMAP_MERC_COLOR = new Color(0x80c0ffff);
  // Player missile color - green
  private static final Color AUTOMAP_PLAYER_MISSILE_COLOR = new Color(0x00ff00ff);
  // Mercenary missile color - light blue
  private static final Color AUTOMAP_MERC_MISSILE_COLOR = new Color(0x80c0ffff);
  // Monster missile color - red
  private static final Color AUTOMAP_MONSTER_MISSILE_COLOR = new Color(0xff0000ff);
  
  // automap 显示范围（世界坐标）
  // 默认：屏幕左上角对应的世界坐标和屏幕范围对应的世界坐标大小
  // x, y 为显示范围的左上角世界坐标
  // width, height 为显示范围的世界坐标大小
  private static float automapViewX = 0;      // 显示范围左上角X（世界坐标）
  private static float automapViewY = 0;      // 显示范围左上角Y（世界坐标）
  private static float automapViewWidth = 0;  // 显示范围宽度（世界坐标）
  private static float automapViewHeight = 0; // 显示范围高度（世界坐标）
  private static float automapDefaultViewWidth = 0;  // 默认显示范围宽度（用于计算缩放限制）
  private static float automapDefaultViewHeight = 0; // 默认显示范围高度（用于计算缩放限制）
  
  // 小地图线长度 - 根据缩放动态计算 (参考 Devilution 的 AmLine 系统)
  private static int amLine64 = 32;  // 64单位线长 (缩放后)
  private static int amLine32 = 16;  // 32单位线长
  private static int amLine16 = 8;   // 16单位线长
  private static int amLine8 = 4;    // 8单位线长
  private static int amLine4 = 2;    // 4单位线长
  
  // 遗留字段 (保持兼容性)
  private static final float AUTOMAP_SCALE = 0.25f;
  
  // 小地图精灵资源路径
  private static final String AUTOMAP_SPRITE_PATH = "data\\global\\ui\\AUTOMAP\\MaxiMap.dc6";
  
  // 小地图精灵帧索引（基于 MaxiMap.dc6 布局）
  // 这些值对应原版 D2 的小地图精灵帧
  private static final int AUTOMAP_FRAME_WALL_LEFT = 1;           // 左墙
  private static final int AUTOMAP_FRAME_WALL_RIGHT = 2;          // 右墙
  private static final int AUTOMAP_FRAME_WALL_CORNER_NW = 3;      // 西北角墙
  private static final int AUTOMAP_FRAME_WALL_CORNER_NE = 4;      // 东北角墙
  private static final int AUTOMAP_FRAME_WALL_CORNER_SW = 5;      // 西南角墙
  private static final int AUTOMAP_FRAME_WALL_CORNER_SE = 6;      // 东南角墙
  private static final int AUTOMAP_FRAME_DOOR_LEFT = 7;           // 左门
  private static final int AUTOMAP_FRAME_DOOR_RIGHT = 8;          // 右门
  private static final int AUTOMAP_FRAME_WAYPOINT = 69;           // 传送点
  private static final int AUTOMAP_FRAME_PLAYER = 81;             // 玩家标记

  private static final Color RENDER_DEBUG_GRID_COLOR_1 = new Color(0x3f3f3f3f);
  private static final Color RENDER_DEBUG_GRID_COLOR_2 = new Color(0x7f7f7f3f);
  private static final Color RENDER_DEBUG_GRID_COLOR_3 = new Color(0x0000ff3f);
  public static int DEBUG_GRID_MODES = 3;

  // Extra padding to ensure proper overscan, should be odd value
  private static final int TILES_PADDING_X = 3;
  private static final int TILES_PADDING_Y = 7;

  private final Comparator<Integer> SUBTILE_ORDER = new Comparator<Integer>() {
    @Override
    public int compare(Integer e1, Integer e2) {
      Vector2 pos1 = mPosition.get(e1).position;
      Vector2 pos2 = mPosition.get(e2).position;
      int i = Float.compare(pos1.y, pos2.y);
      return i == 0 ? Float.compare(pos1.x, pos2.x): i;
    }
  };

  protected ComponentMapper<AnimationWrapper> mAnimationWrapper;
  protected ComponentMapper<Overlay> mOverlay;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Object> mObject;
//  private final Family family = Family.all(AnimationComponent.class, PositionComponent.class).get();
//  private ImmutableArray<Entity> entities;
//
//  // DEBUG
  protected ComponentMapper<Classname> mClassname;
  protected ComponentMapper<Class> mClass;
  protected ComponentMapper<Networked> mNetworked;
  protected ComponentMapper<BBoxWrapper> mBBoxWrapper;
  protected ComponentMapper<Angle> mAngle;
  protected ComponentMapper<Selectable> mSelectable;
  protected ComponentMapper<Hovered> mHovered;
  protected ComponentMapper<AIWrapper> mAIWrapper;
  protected ComponentMapper<AnimData> mAnimData;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<com.riiablo.engine.server.component.Warp> mWarp;
  protected ComponentMapper<com.riiablo.engine.server.component.Missile> mMissile;
  protected ComponentMapper<com.riiablo.engine.server.component.Interactable> mInteractable;
  protected ComponentMapper<com.riiablo.engine.server.component.Monster> mMonster;
  protected ComponentMapper<com.riiablo.engine.server.component.Corpse> mCorpse;
  protected EntitySubscription debugEntitites;

  private final Vector2 tmpVec2 = new Vector2();

  PaletteIndexedBatch batch;
  IsometricCamera     iso;
  Map                 map;
  int                 viewBuffer[];
  Array<Integer>      cache[][][];
  int                 src = -1;
  boolean             dirty;
  final Vector2       currentPos = new Vector2();
  
  // 小地图精灵资源
  com.riiablo.codec.DC6 automapSprite;
  boolean automapSpriteLoaded = false;
  boolean automapLoggedOnce = false;

  // sub-tile index in world-space
  int x, y;

  // sub-tile index in tile-space 2-D
  int stx, sty;

  // sub-tile index in tile-space 1-D
  int t;

  // pixel offset of sub-tile in world-space
  float spx, spy;

  // tile index in world-space
  int tx, ty;

  // pixel offset of tile in world-space
  float tpx, tpy;

  int width, height;
  int tilesX, tilesY;
  int renderWidth, renderHeight;

  // tile index of top right tile in render area
  int startX, startY;

  // tpx and tpy of startX, startY tile in world-space
  float startPx, startPy;

  // camera bounds
  int renderMinX, renderMinY;
  int renderMaxX, renderMaxY;

  float radius;

  // DT1 mainIndexes to not draw
  final Bits popped = new Bits();

  public RenderSystem(PaletteIndexedBatch batch, Map map) {
    this.batch = batch;
    this.map = map;
    this.iso = new IsometricCamera();
    iso.setToOrtho(false);
    iso.offset(0, -Tile.SUBTILE_HEIGHT50);
    iso.set(0, 0);
    iso.update();
    setClipPlane(-1000, 1000);
  }

  // This adjusts clip plane for debugging purposes (some elements rotated to map grid)
  private void setClipPlane(float near, float far) {
    iso.near = near;
    iso.far  = far;
    iso.update();
  }

  public IsometricCamera iso() {
    return iso;
  }

  public int getMinX() {
    return renderMinX;
  }

  public int getMinY() {
    return renderMinY;
  }

  public int getMaxX() {
    return renderMaxX;
  }

  public int getMaxY() {
    return renderMaxY;
  }

  @Override
  protected void initialize() {
    debugEntitites = world.getAspectSubscriptionManager().get(Aspect.all());
  }

  @Override
  protected void dispose() {
    debugEntitites = null;
  }

  public Map getMap() {
    return map;
  }

  public int getSrc() {
    return src;
  }

  public void setSrc(int src) {
    if (this.src != src) {
      assert mPosition.has(src) : "src entity must have a position component";
      this.src = src;
    }
  }

  public float radius() {
    return radius;
  }

  public boolean withinRadius(Vector2 vec) {
    return iso.position.dst(vec) <= radius;
  }

  public float zoom() {
    return iso.zoom;
  }

  public void zoom(float amt) {
    zoom(amt, false);
  }

  public void zoom(float amt, boolean resize) {
    if (iso.zoom != amt) {
      iso.zoom = amt;
      updatePosition(true);
      if (resize) resize();
    }
  }

  /**
   * resizes fov -- called rarely (typically only creation or screen resize)
   */
  public void resize() {
    updateBounds();
    final int viewBufferLen = tilesX + tilesY - 1;
    final int viewBufferMax = tilesX * 2 - 1; // FIXME: double check when adding support for other aspect ratios, need a ternary operation
    viewBuffer = new int[viewBufferLen];
    int x, y;
    for (x = 0, y = 1; y < viewBufferMax; x++, y += 2)
      viewBuffer[x] = viewBuffer[viewBufferLen - 1 - x] = y;
    while (viewBuffer[x] == 0)
      viewBuffer[x++] = viewBufferMax;
    if (DEBUG_BUFFER) {
      int len = 0;
      for (int i : viewBuffer) len += i;
      Gdx.app.debug(TAG, "viewBuffer[" + len + "]=" + Arrays.toString(viewBuffer));
    }
    dirty = true;

    cache = new Array[viewBufferLen][][];
    for (int i = 0; i < viewBufferLen; i++) {
      int viewBufferRun = viewBuffer[i];
      cache[i] = new Array[viewBufferRun][];
      for (int j = 0; j < viewBufferRun; j++) {
        cache[i][j] = new Array[] {
            new Array<Integer>(Tile.NUM_SUBTILES), // TODO: Really {@code (Tile.SUBTILE_SIZE - 1) * (Tile.SUBTILE_SIZE - 1)}
            new Array<Integer>(1), // better size TBD
            new Array<Integer>(Tile.SUBTILE_SIZE + Tile.SUBTILE_SIZE - 1), // only upper walls
        };
      }
    }
  }

  private void updateBounds() {
    width  = (int) (iso.viewportWidth  * iso.zoom);
    height = (int) (iso.viewportHeight * iso.zoom);

    updateCameraBounds();

    int minTilesX = ((Map.round(width)  + Tile.WIDTH  - 1) / Tile.WIDTH);
    int minTilesY = ((Map.round(height) + Tile.HEIGHT - 1) / Tile.HEIGHT);
    if ((minTilesX & 1) == 1) minTilesX++;
    if ((minTilesY & 1) == 1) minTilesY++;
    tilesX = minTilesX + TILES_PADDING_X;
    tilesY = minTilesY + TILES_PADDING_Y;
    renderWidth  = tilesX * Tile.WIDTH;
    renderHeight = tilesY * Tile.HEIGHT;
    assert (tilesX & 1) == 1;
    assert (tilesY & 1) == 1;

    float yardsX = (renderWidth  / 2) / Tile.SUBTILE_WIDTH;
    float yardsY = (renderHeight / 2) / Tile.SUBTILE_HEIGHT;
    radius = Vector2.len(yardsX, yardsY);
  }

  private void updateCameraBounds() {
    iso.toScreen(tmpVec2.set(iso.position));
    renderMinX = (int) tmpVec2.x - (width  >>> 1);
    renderMinY = (int) tmpVec2.y - (height >>> 1);
    renderMaxX = renderMinX + width;
    renderMaxY = renderMinY + height;
  }

  public void updatePosition() {
    updatePosition(false);
  }

  /**
   * updates position of camera -- once per frame
   */
  public void updatePosition(boolean force) {
    if (src == -1) return;
    Vector2 pos = mPosition.get(src).position;
    iso.set(pos);
    iso.update();
    if (pos.epsilonEquals(currentPos) && !force && !dirty) return;
    dirty = false;
    currentPos.set(pos);
    iso.toTile(tmpVec2.set(pos));
    this.x = (int) tmpVec2.x;
    this.y = (int) tmpVec2.y;

    // subtile index in tile-space
    stx = x < 0
        ? (x + 1) % Tile.SUBTILE_SIZE + (Tile.SUBTILE_SIZE - 1)
        : x % Tile.SUBTILE_SIZE;
    sty = y < 0
        ? (y + 1) % Tile.SUBTILE_SIZE + (Tile.SUBTILE_SIZE - 1)
        : y % Tile.SUBTILE_SIZE;
    t   = Tile.SUBTILE_INDEX[stx][sty];

    // pixel offset of subtile in world-space
    iso.toScreen(x, y, tmpVec2)
        .add(-Tile.SUBTILE_WIDTH50, -Tile.SUBTILE_HEIGHT50);
    spx = tmpVec2.x;
    spy = tmpVec2.y;

    // tile index in world-space
    tx = x < 0
        ? ((x + 1) / Tile.SUBTILE_SIZE) - 1
        : (x / Tile.SUBTILE_SIZE);
    ty = y < 0
        ? ((y + 1) / Tile.SUBTILE_SIZE) - 1
        : (y / Tile.SUBTILE_SIZE);

    // offset
    tpx = spx - Tile.SUBTILE_OFFSET[t][0];
    tpy = spy - Tile.SUBTILE_OFFSET[t][1];

    updateCameraBounds();

    final int offX = tilesX >>> 1;
    final int offY = tilesY >>> 1;
    startX = tx + offX - offY;
    startY = ty - offX - offY;
    startPx = tpx + renderWidth  / 2 - Tile.WIDTH50;
    startPy = tpy + renderHeight / 2 - Tile.HEIGHT50;

    if (DEBUG_MATH) {
      Gdx.app.debug(TAG,
          String.format("(%2d,%2d){%d,%d}[%2d,%2d](%dx%d)[%dx%d] %.0f,%.0f {%d,%d}->{%d,%d}",
              x, y, stx, sty, tx, ty, width, height, tilesX, tilesY, spx, spy, renderMinX, renderMinY, renderMaxX, renderMaxY));
    }

    updatePopPads();
  }

  private void updatePopPads() {
    map.updatePopPads(popped, x, y, tx, ty, stx, sty);
    if (DEBUG_POPPADS) {
      String popPads = getPopPads();
      if (!popPads.isEmpty()) Gdx.app.debug(TAG, "PopPad IDs: " + popPads);
    }
  }

  private String getPopPads() {
    StringBuilder builder = new StringBuilder();
    for (int i = popped.nextSetBit(0); i >= 0; i = popped.nextSetBit(i + 1)) {
      builder.append(i).append(',');
    }

    if (builder.length() > 0) {
      builder.setLength(builder.length() - 1);
    }

    return builder.toString();
  }

  @Override
  protected void begin() {
    Riiablo.batch.begin();
  }

  @Override
  protected void end() {
    Riiablo.batch.end();
  }

  /**
   * renders map
   */
  @Override
  protected void processSystem() {
    updatePosition();
    draw(world.delta);
  }

  /**
   * renders map
   */
  public void draw(float delta) {
    prepareBatch();
    buildCaches();
    drawBackground();
    drawMiddleground();
    drawForeground();
  }

  private void prepareBatch() {
    batch.setPalette(getPalette());
    batch.setProjectionMatrix(iso.combined);
  }

  private Texture getPalette() {
    switch (map.getAct()) {
      case 0:  return Riiablo.palettes.act1;
      case 1:  return Riiablo.palettes.act2;
      case 2:  return Riiablo.palettes.act3;
      case 3:  return Riiablo.palettes.act4;
      case 4:  return Riiablo.palettes.act5;
      default: return Riiablo.palettes.act1;
    }
  }

  /**
   * TODO: This is still a fairly expensive calculation because it needs to read all entities
   *       viewbuffer size times -- this can be sped up using some kind of cache within entities
   *       themselves (so each time position changes, update viewbuffer cache position) or by
   *       storing the entity at its position in array within the zone (similar to how the collision
   *       map works -- this may very well be how the actual game works, but some spaces might allow
   *       more than 1 entity, e.g., player + item, or monsters that don't have collision -- I'll
   *       look into this more when I add entity collision detection.
   */
  private void buildCaches() {
    int x, y;
    int startX2 = startX;
    int startY2 = startY;
    for (y = 0; y < viewBuffer.length; y++) {
      int tx = startX2;
      int ty = startY2;
      int stx = tx * Tile.SUBTILE_SIZE;
      int sty = ty * Tile.SUBTILE_SIZE;
      int size = viewBuffer[y];
      for (x = 0; x < size; x++) {
        Map.Zone zone = map.getZone(stx, sty);
        if (zone != null) buildCache(cache[y][x], zone, stx, sty);
        tx++;
        stx += Tile.SUBTILE_SIZE;
      }

      startY2++;
      if (y >= tilesX - 1) {
        startX2++;
      } else {
        startX2--;
      }
    }
  }

  private void buildCache(Array<Integer>[] cache, Map.Zone zone, int stx, int sty) {
    cache[0].size = cache[1].size = cache[2].size = 0;
    int orderFlag;
    IntBag entitites = getEntityIds();
    for (int i = 0, size = entitites.size(); i < size; i++) {
      int id = entitites.get(i);
      Vector2 pos = mPosition.get(id).position;
      if ((stx <= pos.x && pos.x < stx + Tile.SUBTILE_SIZE)
       && (sty <= pos.y && pos.y < sty + Tile.SUBTILE_SIZE)) {
        Object objectComponent = mObject.get(id);
        if (objectComponent != null) {
          CofReference reference = mCofReference.get(id);
          orderFlag = objectComponent.base.OrderFlag[reference.mode];
        } else {
          orderFlag = stx == pos.x || sty == pos.y ? 2 : 0;
        }

        cache[orderFlag].add(id);
      }
    }
    cache[0].sort(SUBTILE_ORDER);
    cache[1].sort(SUBTILE_ORDER);
    cache[2].sort(SUBTILE_ORDER);
  }

  private void drawBackground() {
    int x, y;
    int startX2 = startX;
    int startY2 = startY;
    float startPx2 = startPx;
    float startPy2 = startPy;
    for (y = 0; y < viewBuffer.length; y++) {
      int tx = startX2;
      int ty = startY2;
      int stx = tx * Tile.SUBTILE_SIZE;
      int sty = ty * Tile.SUBTILE_SIZE;
      float px = startPx2;
      float py = startPy2;
      int size = viewBuffer[y];
      for (x = 0; x < size; x++) {
        Map.Zone zone = map.getZone(stx, sty);
        if (zone != null) {
          drawLowerWalls(batch, zone, tx, ty, px, py);
          drawFloors(batch, zone, tx, ty, px, py);
        }

        tx++;
        stx += Tile.SUBTILE_SIZE;
        px += Tile.WIDTH50;
        py -= Tile.HEIGHT50;
      }

      startY2++;
      if (y >= tilesX - 1) {
        startX2++;
        startPy2 -= Tile.HEIGHT;
      } else {
        startX2--;
        startPx2 -= Tile.WIDTH;
      }
    }

    startX2 = startX;
    startY2 = startY;
    startPx2 = startPx;
    startPy2 = startPy;
    for (y = 0; y < viewBuffer.length; y++) {
      int tx = startX2;
      int ty = startY2;
      int stx = tx * Tile.SUBTILE_SIZE;
      int sty = ty * Tile.SUBTILE_SIZE;
      float px = startPx2;
      float py = startPy2;
      int size = viewBuffer[y];
      for (x = 0; x < size; x++) {
        Map.Zone zone = map.getZone(stx, sty);
        if (zone != null) {
          //buildCaches(zone, stx, sty);
          drawShadows(batch, zone, tx, ty, px, py, cache[y][x]);
        }

        tx++;
        stx += Tile.SUBTILE_SIZE;
        px += Tile.WIDTH50;
        py -= Tile.HEIGHT50;
      }

      startY2++;
      if (y >= tilesX - 1) {
        startX2++;
        startPy2 -= Tile.HEIGHT;
      } else {
        startX2--;
        startPx2 -= Tile.WIDTH;
      }
    }
  }

  private void drawMiddleground() {
    int x, y;
    int startX2 = startX;
    int startY2 = startY;
    float startPx2 = startPx;
    float startPy2 = startPy;
    for (y = 0; y < viewBuffer.length; y++) {
      int tx = startX2;
      int ty = startY2;
      int stx = tx * Tile.SUBTILE_SIZE;
      int sty = ty * Tile.SUBTILE_SIZE;
      float px = startPx2;
      float py = startPy2;
      int size = viewBuffer[y];
      for (x = 0; x < size; x++) {
        Map.Zone zone = map.getZone(stx, sty);
        if (zone != null) {
          //buildCaches(zone, stx, sty);
          Array<Integer>[] cache = this.cache[y][x];
          drawEntities(cache, 1); // floors
          drawEntities(cache, 2); // walls/doors
          drawWalls(batch, zone, tx, ty, px, py);
          //drawWalls (trees and maybe columns?)
          drawEntities(cache, 0); // objects
        }

        tx++;
        stx += Tile.SUBTILE_SIZE;
        px += Tile.WIDTH50;
        py -= Tile.HEIGHT50;
      }

      startY2++;
      if (y >= tilesX - 1) {
        startX2++;
        startPy2 -= Tile.HEIGHT;
      } else {
        startX2--;
        startPx2 -= Tile.WIDTH;
      }
    }
  }

  private void drawForeground() {
    int x, y;
    int startX2 = startX;
    int startY2 = startY;
    float startPx2 = startPx;
    float startPy2 = startPy;
    for (y = 0; y < viewBuffer.length; y++) {
      int tx = startX2;
      int ty = startY2;
      int stx = tx * Tile.SUBTILE_SIZE;
      int sty = ty * Tile.SUBTILE_SIZE;
      float px = startPx2;
      float py = startPy2;
      int size = viewBuffer[y];
      for (x = 0; x < size; x++) {
        Map.Zone zone = map.getZone(stx, sty);
        if (zone != null) {
          drawRoofs(batch, zone, tx, ty, px, py);
        }

        tx++;
        stx += Tile.SUBTILE_SIZE;
        px += Tile.WIDTH50;
        py -= Tile.HEIGHT50;
      }

      startY2++;
      if (y >= tilesX - 1) {
        startX2++;
        startPy2 -= Tile.HEIGHT;
      } else {
        startX2--;
        startPx2 -= Tile.WIDTH;
      }
    }
  }

  void drawEntities(Array<Integer>[] cache, int i) {
    for (int entity : cache[i]) {
//      if (!entity.target().isZero() && !entity.position().epsilonEquals(entity.target())) {
//        entity.angle(angle(entity.position(), entity.target()));
//      }

//      CofComponent cofComponent = this.cofComponent.get(entity);
//      if (cofComponent != null && cofComponent.load != Dirty.NONE) return;

      // TODO: create EntityRenderer class to encapsulate the following code:
      Vector2 pos = mPosition.get(entity).position;
      Vector2 tmp = iso.toScreen(tmpVec2.set(pos));

      Overlay overlay = mOverlay.get(entity);
      if (overlay != null && overlay.entry.PreDraw) {
        overlay.animation.draw(batch, tmp.x, tmp.y);
      }

      Animation animation = mAnimationWrapper.get(entity).animation;
      animation.draw(batch, tmp.x, tmp.y);

      if (overlay != null && !overlay.entry.PreDraw) {
        overlay.animation.draw(batch, tmp.x, tmp.y);
      }
    }
  }

  void drawLowerWalls(PaletteIndexedBatch batch, Map.Zone zone, int tx, int ty, float px, float py) {
    if (px > renderMaxX || py > renderMaxY || px + Tile.WIDTH < renderMinX) return;
    for (int i = Map.WALL_OFFSET; i < Map.WALL_OFFSET + Map.MAX_WALLS; i++) {
      Tile tile = zone.get(i, tx, ty);
      if (tile == null) continue;
      switch (tile.orientation) {
        case Orientation.LOWER_LEFT_WALL:
        case Orientation.LOWER_RIGHT_WALL:
        case Orientation.LOWER_NORTH_CORNER_WALL:
        case Orientation.LOWER_SOUTH_CORNER_WALL:
          batch.draw(tile.texture, px, py + tile.height + Tile.WALL_HEIGHT);
          // fall-through to continue
        default:
      }
    }
  }

  void drawFloors(PaletteIndexedBatch batch, Map.Zone zone, int tx, int ty, float px, float py) {
    if (px > renderMaxX || py > renderMaxY) return;
    if (px + Tile.WIDTH < renderMinX || py + Tile.HEIGHT < renderMinY) return;
    for (int i = Map.FLOOR_OFFSET; i < Map.FLOOR_OFFSET + Map.MAX_FLOORS; i++) {
      Tile tile = zone.get(i, tx, ty);
      if (tile == null) continue;
      TextureRegion texture;
      int subst = map.warpSubsts.get(tile.id, -1);
      if (subst != -1) { // TODO: Performance can be improved if the reference is updated to below subst
        texture = map.dt1s.get(zone.level.LevelType).get(subst).texture;
      } else {
        texture = tile.texture;
      }
      //if (texture.getTexture().getTextureObjectHandle() == 0) return;
      batch.draw(texture, px, py);
    }
  }

  void drawShadows(PaletteIndexedBatch batch, Map.Zone zone, int tx, int ty, float px, float py, Array<Integer>[] cache) {
    batch.setBlendMode(BlendMode.SOLID, Riiablo.colors.modal75);
    for (int i = Map.SHADOW_OFFSET; i < Map.SHADOW_OFFSET + Map.MAX_SHADOWS; i++) {
      Tile tile = zone.get(i, tx, ty);
      if (tile == null) continue;
      if (px > renderMaxX || px + Tile.WIDTH  < renderMinX) continue;
      TextureRegion texture = tile.texture;
      if (py > renderMaxY || py + texture.getRegionHeight() < renderMinY) continue;
      batch.draw(texture, px, py);
    }
    /*
    for (int i = Map.WALL_OFFSET; i < Map.WALL_OFFSET + Map.MAX_WALLS; i++) {
      Map.Tile tile = zone.get(i, tx, ty);
      if (tile == null || tile.tile == null) continue;
      if (tile.tile.orientation != Orientation.SHADOW) continue;
      TextureRegion texture = tile.tile.texture;
      batch.draw(texture, px, py, texture.getRegionWidth(), texture.getRegionHeight());
    }
    */
    for (Array<Integer> c : cache) {
      for (int entity : c) {
//        CofComponent cofComponent = this.cofComponent.get(entity);
//        if (cofComponent != null && cofComponent.load != Dirty.NONE) continue;
        Animation animation = mAnimationWrapper.get(entity).animation;
        Vector2 pos = mPosition.get(entity).position;
        Vector2 tmp = iso.toScreen(tmpVec2.set(pos));
        animation.drawShadow(batch, tmp.x, tmp.y, false);
      }
    }
    batch.resetBlendMode();
  }

  void drawWalls(PaletteIndexedBatch batch, Map.Zone zone, int tx, int ty, float px, float py) {
    if (px > renderMaxX || py > renderMaxY || px + Tile.WIDTH < renderMinX) return;
    for (int i = Map.WALL_OFFSET; i < Map.WALL_OFFSET + Map.MAX_WALLS; i++) {
      Tile tile = zone.get(i, tx, ty);
      if (tile == null) continue;
      if (popped.get(tile.mainIndex)) continue;
      if (!isDrawableWallOrientation(tile.orientation)) continue;
      if (py + tile.texture.getRegionHeight() < renderMinY) continue;
      batch.draw(tile.texture, px, py);
      if (tile.orientation == Orientation.RIGHT_NORTH_CORNER_WALL) {
        Tile sibling = zone.dt1s.get(
            Orientation.LEFT_NORTH_CORNER_WALL, tile.mainIndex, tile.subIndex);
        if (sibling != null) batch.draw(sibling.texture, px, py);
      }
    }
  }

  static boolean isDrawableWallOrientation(int orientation) {
    switch (orientation) {
      case Orientation.LEFT_WALL:
      case Orientation.LEFT_NORTH_CORNER_WALL:
      case Orientation.LEFT_END_WALL:
      case Orientation.LEFT_WALL_DOOR:
      case Orientation.RIGHT_WALL:
      case Orientation.RIGHT_NORTH_CORNER_WALL:
      case Orientation.RIGHT_END_WALL:
      case Orientation.RIGHT_WALL_DOOR:
      case Orientation.SOUTH_CORNER_WALL:
      case Orientation.SPECIAL_10:
      case Orientation.SPECIAL_11:
      case Orientation.PILLAR:
      case Orientation.TREE:
        return true;
      default:
        return false;
    }
  }

  void drawRoofs(PaletteIndexedBatch batch, Map.Zone zone, int tx, int ty, float px, float py) {
    if (px > renderMaxX || px + Tile.WIDTH < renderMinX) return;
    for (int i = Map.WALL_OFFSET; i < Map.WALL_OFFSET + Map.MAX_WALLS; i++) {
      Tile tile = zone.get(i, tx, ty);
      if (tile == null || tile == null) continue;
      if (popped.get(tile.mainIndex)) continue;
      if (!Orientation.isRoof(tile.orientation)) continue;
      if (py + tile.roofHeight > renderMaxY) continue;
      if (py + tile.roofHeight + tile.texture.getRegionHeight() < renderMinY) continue;
      batch.draw(tile.texture, px, py + tile.roofHeight);
    }
  }

  public void drawDebug(ShapeRenderer shapes) {
    batch.setProjectionMatrix(iso.combined);
    shapes.setProjectionMatrix(iso.combined);
    if (RENDER_DEBUG_MATERIAL > 0)
      drawDebugMaterial(shapes);

    if (RENDER_DEBUG_GRID > 0)
      drawDebugGrid(shapes);

    if (RENDER_DEBUG_WALKABLE > 0)
      drawDebugWalkable(shapes);

    if (RENDER_DEBUG_CELLS > 0)
      drawDebugCells(shapes);

    if (RENDER_DEBUG_SPECIAL)
      drawDebugSpecial(shapes);

    if (RENDER_DEBUG_TILE) {
      shapes.setColor(Color.OLIVE);
      DebugUtils.drawDiamond2(shapes, tpx, tpy, Tile.WIDTH, Tile.HEIGHT);
    }

    if (RENDER_DEBUG_SUBTILE) {
      shapes.setColor(Color.WHITE);
      DebugUtils.drawDiamond2(shapes, spx, spy, Tile.SUBTILE_WIDTH, Tile.SUBTILE_HEIGHT);
    }

    if (RENDER_DEBUG_ENTITIES)
      drawDebugObjects(shapes);

    if (RENDER_DEBUG_CAMERA)
      drawDebugCamera(shapes);

    if (RENDER_DEBUG_OVERSCAN > 0)
      drawDebugOverscan(shapes);

    if (DEBUG_MOUSE)
      drawDebugMouse(shapes);

    // F10 调试：D2MOD 土路路径（红色线条与小方块），与 Pathfind 紫色路径使用相同坐标转换
    if (map != null && map.pathDebugPoints.size > 0)
      drawDebugD2MODPath(shapes);
  }

  private void drawDebugD2MODPath(ShapeRenderer shapes) {
    final float BOX_SIZE = 8;
    final float HALF_BOX = BOX_SIZE / 2;
    Vector2 tmpA = new Vector2();
    Vector2 tmpB = new Vector2();
    shapes.setColor(Color.RED);
    for (int i = 0; i < map.pathDebugPoints.size; i++) {
      float[] p = map.pathDebugPoints.get(i);
      iso.toScreen(p[0], p[1], tmpB);
      if (i > 0) {
        shapes.rectLine(tmpA, tmpB, 3);
      }
      tmpA.set(tmpB);
    }
    for (int i = 0; i < map.pathDebugPoints.size; i++) {
      float[] p = map.pathDebugPoints.get(i);
      iso.toScreen(p[0], p[1], tmpA).sub(HALF_BOX, HALF_BOX);
      shapes.setColor(Color.RED);
      shapes.rect(tmpA.x, tmpA.y, BOX_SIZE, BOX_SIZE);
    }
    // 白线：玩家位置到最近路径点（用平方距离比较，避免开方）
    if (src >= 0 && map.pathDebugPoints.size > 0) {
      float px = currentPos.x;
      float py = currentPos.y;
      int closest = 0;
      float[] first = map.pathDebugPoints.get(0);
      float minDistSq = (first[0] - px) * (first[0] - px) + (first[1] - py) * (first[1] - py);
      for (int i = 1; i < map.pathDebugPoints.size; i++) {
        float[] p = map.pathDebugPoints.get(i);
        float dx = p[0] - px;
        float dy = p[1] - py;
        float distSq = dx * dx + dy * dy;
        if (distSq < minDistSq) {
          minDistSq = distSq;
          closest = i;
        }
      }
      float[] nearest = map.pathDebugPoints.get(closest);
      iso.toScreen(px, py, tmpA);
      iso.toScreen(nearest[0], nearest[1], tmpB);
      shapes.setColor(Color.WHITE);
      shapes.rectLine(tmpA, tmpB, 2);
    }
  }

  private void drawDebugGrid(ShapeRenderer shapes) {
    int x, y;
    switch (RENDER_DEBUG_GRID) {
      case 1:
        shapes.setColor(RENDER_DEBUG_GRID_COLOR_1);
        float startPx2 = startPx;
        float startPy2 = startPy;
        for (y = 0; y < viewBuffer.length; y++) {
          float px = startPx2;
          float py = startPy2;
          int size = viewBuffer[y];
          for (x = 0; x < size; x++) {
            for (int t = 0; t < Tile.NUM_SUBTILES; t++) {
              DebugUtils.drawDiamond2(shapes,
                  px + Tile.SUBTILE_OFFSET[t][0], py + Tile.SUBTILE_OFFSET[t][1],
                  Tile.SUBTILE_WIDTH, Tile.SUBTILE_HEIGHT);
            }

            px += Tile.WIDTH50;
            py -= Tile.HEIGHT50;
          }

          if (y >= tilesX - 1) {
            startPy2 -= Tile.HEIGHT;
          } else {
            startPx2 -= Tile.WIDTH;
          }
        }

      case 2:
        shapes.setColor(RENDER_DEBUG_GRID_COLOR_2);
        startPx2 = startPx;
        startPy2 = startPy;
        for (y = 0; y < viewBuffer.length; y++) {
          float px = startPx2;
          float py = startPy2;
          int size = viewBuffer[y];
          for (x = 0; x < size; x++) {
            DebugUtils.drawDiamond2(shapes, px, py, Tile.WIDTH, Tile.HEIGHT);
            px += Tile.WIDTH50;
            py -= Tile.HEIGHT50;
          }

          if (y >= tilesX - 1) {
            startPy2 -= Tile.HEIGHT;
          } else {
            startPx2 -= Tile.WIDTH;
          }
        }

      case 3:
        shapes.setColor(RENDER_DEBUG_GRID_COLOR_3);
        ShapeRenderer.ShapeType shapeType = shapes.getCurrentType();
        shapes.set(ShapeRenderer.ShapeType.Filled);
        final int LINE_WIDTH = 2;
        int startX2 = startX;
        int startY2 = startY;
        startPx2 = startPx;
        startPy2 = startPy;
        for (y = 0; y < viewBuffer.length; y++) {
          int tx = startX2;
          int ty = startY2;
          float px = startPx2;
          float py = startPy2;
          int size = viewBuffer[y];
          for (x = 0; x < size; x++) {
            Map.Zone zone = map.getZone(tx * Tile.SUBTILE_SIZE, ty * Tile.SUBTILE_SIZE);
            if (zone != null) {
              int localTX = zone.getLocalTX(tx);
              int modX = localTX < 0
                  ? (localTX + 1) % zone.gridSizeX + (zone.gridSizeX - 1)
                  : localTX % zone.gridSizeX;
              if (modX == 0)
                shapes.rectLine(px, py + Tile.HEIGHT50, px + Tile.WIDTH50, py + Tile.HEIGHT, LINE_WIDTH);
              else if (modX == zone.gridSizeX - 1)
                shapes.rectLine(px + Tile.WIDTH, py + Tile.HEIGHT50, px + Tile.WIDTH50, py, LINE_WIDTH);

              int localTY = zone.getLocalTY(ty);
              int modY = localTY < 0
                  ? (localTY + 1) % zone.gridSizeY + (zone.gridSizeY - 1)
                  : localTY % zone.gridSizeY;
              if (modY == 0)
                shapes.rectLine(px + Tile.WIDTH50, py + Tile.HEIGHT, px + Tile.WIDTH, py + Tile.HEIGHT50, LINE_WIDTH);
              else if (modY == zone.gridSizeY - 1)
                shapes.rectLine(px + Tile.WIDTH50, py, px, py + Tile.HEIGHT50, LINE_WIDTH);

              if (modX == 0 && modY == 0) {
                Map.Preset preset = zone.getGrid(tx, ty);
                StringBuilder sb = new StringBuilder(tx + "," + ty);
                if (preset != null)
                  sb.append('\n').append(preset.ds1Path);
                String desc = sb.toString();

                shapes.end();
                batch.getProjectionMatrix()
                    .translate(px + Tile.WIDTH50, py + Tile.HEIGHT - Tile.SUBTILE_HEIGHT, 0)
                    .rotate(Vector3.X,  60)
                    .rotate(Vector3.Z, -45);
                //batch.getProjectionMatrix()
                //    .translate(px + Tile.WIDTH50, py + Tile.HEIGHT - Tile.SUBTILE_HEIGHT, 0)
                //    .rotateRad(Vector3.Z, -0.463647609f)
                //    .shear;
                batch.begin();
                batch.setShader(null);
                BitmapFont font = Riiablo.fonts.consolas16;
                GlyphLayout layout = new GlyphLayout(font, desc);
                font.draw(batch, layout, 0, 0);
                /*GlyphLayout layout = new GlyphLayout(font, desc, 0, desc.length(), font.getColor(), 0, Align.center, false, null);
                font.draw(batch, layout,
                    px + Tile.WIDTH50,
                    py + Tile.HEIGHT - font.getLineHeight());
                */
                batch.end();
                batch.setProjectionMatrix(iso.combined);
                shapes.begin(ShapeRenderer.ShapeType.Filled);
              }
            }

            tx++;
            px += Tile.WIDTH50;
            py -= Tile.HEIGHT50;
          }

          startY2++;
          if (y >= tilesX - 1) {
            startX2++;
            startPy2 -= Tile.HEIGHT;
          } else {
            startX2--;
            startPx2 -= Tile.WIDTH;
          }
        }
        shapes.set(shapeType);

      default:
    }
  }

  private void drawDebugWalkable(ShapeRenderer shapes) {
    final int[] WALKABLE_ID = {
        20, 21, 22, 23, 24,
        15, 16, 17, 18, 19,
        10, 11, 12, 13, 14,
         5,  6,  7,  8,  9,
         0,  1,  2,  3,  4
    };

    ShapeRenderer.ShapeType shapeType = shapes.getCurrentType();
    shapes.set(ShapeRenderer.ShapeType.Filled);

    int startX2 = startX;
    int startY2 = startY;
    float startPx2 = startPx;
    float startPy2 = startPy;
    int x, y;
    for (y = 0; y < viewBuffer.length; y++) {
      int tx = startX2;
      int ty = startY2;
      float px = startPx2;
      float py = startPy2;
      int size = viewBuffer[y];
      for (x = 0; x < size; x++) {
        Map.Zone zone = map.getZone(tx * Tile.SUBTILE_SIZE, ty * Tile.SUBTILE_SIZE);
        if (zone != null) {
          if (RENDER_DEBUG_WALKABLE == 1) {
            for (int sty = 0, t = 0; sty < Tile.SUBTILE_SIZE; sty++) {
              for (int stx = 0; stx < Tile.SUBTILE_SIZE; stx++, t++) {
                int flags = zone.flags(zone.getLocalTX(tx) * Tile.SUBTILE_SIZE + stx, zone.getLocalTY(ty) * Tile.SUBTILE_SIZE + sty);
                if (flags == 0) continue;
                drawDebugWalkableTiles(shapes, px, py, t, flags);
              }
            }
          } else {
            //Map.Tile[][] tiles = zone.tiles[RENDER_DEBUG_WALKABLE - 1];
            //if (tiles != null) {
              Tile tile = zone.get(RENDER_DEBUG_WALKABLE - 2, tx, ty);
              for (int t = 0; tile != null && tile != null && t < Tile.NUM_SUBTILES; t++) {
                int flags = tile.flags[WALKABLE_ID[t]] & 0xFF;
                if (flags == 0) continue;
                drawDebugWalkableTiles(shapes, px, py, t, flags);
              }
            //}
          }
        }

        tx++;
        px += Tile.WIDTH50;
        py -= Tile.HEIGHT50;
      }

      startY2++;
      if (y >= tilesX - 1) {
        startX2++;
        startPy2 -= Tile.HEIGHT;
      } else {
        startX2--;
        startPx2 -= Tile.WIDTH;
      }
    }

    shapes.set(shapeType);
  }

  private static void drawDebugWalkableTiles(ShapeRenderer shapes, float px, float py, int t, int flags) {
    float offX = px + Tile.SUBTILE_OFFSET[t][0];
    float offY = py + Tile.SUBTILE_OFFSET[t][1];

    shapes.setColor(Color.CORAL);
    shapes.set(ShapeRenderer.ShapeType.Line);
    DebugUtils.drawDiamond2(shapes, offX, offY, Tile.SUBTILE_WIDTH, Tile.SUBTILE_HEIGHT);
    shapes.set(ShapeRenderer.ShapeType.Filled);

    offY += Tile.SUBTILE_HEIGHT50;

    if ((flags & Tile.FLAG_BLOCK_WALK) != 0) {
      shapes.setColor(Color.FIREBRICK);
      shapes.triangle(
          offX + 16, offY,
          offX + 16, offY + 8,
          offX + 24, offY + 4);
    }
    if ((flags & Tile.FLAG_BLOCK_LIGHT_LOS) != 0) {
      shapes.setColor(Color.FOREST);
      shapes.triangle(
          offX + 16, offY,
          offX + 32, offY,
          offX + 24, offY + 4);
    }
    if ((flags & Tile.FLAG_BLOCK_JUMP) != 0) {
      shapes.setColor(Color.ROYAL);
      shapes.triangle(
          offX + 16, offY,
          offX + 32, offY,
          offX + 24, offY - 4);
    }
    if ((flags & Tile.FLAG_BLOCK_PLAYER_WALK) != 0) {
      shapes.setColor(Color.VIOLET);
      shapes.triangle(
          offX + 16, offY,
          offX + 16, offY - 8,
          offX + 24, offY - 4);
    }
    if ((flags & Tile.FLAG_BLOCK_UNKNOWN1) != 0) {
      shapes.setColor(Color.GOLD);
      shapes.triangle(
          offX + 16, offY,
          offX + 16, offY - 8,
          offX + 8, offY - 4);
    }
    if ((flags & Tile.FLAG_BLOCK_LIGHT) != 0) {
      shapes.setColor(Color.SKY);
      shapes.triangle(
          offX, offY,
          offX + 16, offY,
          offX + 8, offY - 4);
    }
    if ((flags & Tile.FLAG_BLOCK_UNKNOWN2) != 0) {
      shapes.setColor(Color.WHITE);
      shapes.triangle(
          offX, offY,
          offX + 16, offY,
          offX + 8, offY + 4);
    }
    if ((flags & Tile.FLAG_BLOCK_UNKNOWN3) != 0) {
      shapes.setColor(Color.SLATE);
      shapes.triangle(
          offX + 16, offY,
          offX + 16, offY + 8,
          offX + 8, offY + 4);
    }
  }

  private void drawDebugMaterial(ShapeRenderer shapes) {
    final int[] WALKABLE_ID = {
        20, 21, 22, 23, 24,
        15, 16, 17, 18, 19,
        10, 11, 12, 13, 14,
        5, 6, 7, 8, 9,
        0, 1, 2, 3, 4
    };

    ShapeRenderer.ShapeType shapeType = shapes.getCurrentType();
    shapes.set(ShapeRenderer.ShapeType.Filled);

    int startX2 = startX;
    int startY2 = startY;
    float startPx2 = startPx;
    float startPy2 = startPy;
    int x, y;
    for (y = 0; y < viewBuffer.length; y++) {
      int tx = startX2;
      int ty = startY2;
      float px = startPx2;
      float py = startPy2;
      int size = viewBuffer[y];
      for (x = 0; x < size; x++) {
        Map.Zone zone = map.getZone(tx * Tile.SUBTILE_SIZE, ty * Tile.SUBTILE_SIZE);
        if (zone != null) {
          if (RENDER_DEBUG_MATERIAL == 1) {
            Material type = zone.material(tx, ty);
            for (int sty = 0, t = 0; sty < Tile.SUBTILE_SIZE; sty++) {
              for (int stx = 0; stx < Tile.SUBTILE_SIZE; stx++, t++) {
                int flags = zone.flags(zone.getLocalTX(tx) * Tile.SUBTILE_SIZE + stx, zone.getLocalTY(ty) * Tile.SUBTILE_SIZE + sty);
                if (flags != 0) continue;
                drawDebugMaterialTiles(shapes, px, py, t, type);
              }
            }
          } else {
            Tile tile = zone.get(RENDER_DEBUG_MATERIAL - 2, tx, ty);
            for (int t = 0; tile != null && tile != null && t < Tile.NUM_SUBTILES; t++) {
              int flags = tile.flags[WALKABLE_ID[t]] & 0xFF;
              if (flags != 0) continue;
              Material type = Material.getMaterial(zone.level, tile);
              drawDebugMaterialTiles(shapes, px, py, t, type);
            }
          }
        }

        tx++;
        px += Tile.WIDTH50;
        py -= Tile.HEIGHT50;
      }

      startY2++;
      if (y >= tilesX - 1) {
        startX2++;
        startPy2 -= Tile.HEIGHT;
      } else {
        startX2--;
        startPx2 -= Tile.WIDTH;
      }
    }

    shapes.set(shapeType);
  }

  private static void drawDebugMaterialTiles(ShapeRenderer shapes, float px, float py, int t, Material material) {
    float offX = px + Tile.SUBTILE_OFFSET[t][0];
    float offY = py + Tile.SUBTILE_OFFSET[t][1];

    shapes.setColor(material.color);
    DebugUtils.drawDiamond2(shapes, offX, offY, Tile.SUBTILE_WIDTH, Tile.SUBTILE_HEIGHT);
  }

  private void drawDebugCells(ShapeRenderer shapes) {
    int type;
    int l = RENDER_DEBUG_CELLS - 1;
    if (Map.FLOOR_OFFSET <= l && l < Map.FLOOR_OFFSET + Map.MAX_FLOORS) {
      l -= Map.FLOOR_OFFSET;
      type = 0;
    } else if (Map.SHADOW_OFFSET <= l && l < Map.SHADOW_OFFSET + Map.MAX_SHADOWS) {
      l -= Map.SHADOW_OFFSET;
      type = 1;
    } else if (Map.WALL_OFFSET <= l && l < Map.WALL_OFFSET + Map.MAX_WALLS) {
      l -= Map.WALL_OFFSET;
      type = 2;
    } else if (Map.TAG_OFFSET <= l && l < Map.TAG_OFFSET + Map.MAX_TAGS) {
      l -= Map.TAG_OFFSET;
      type = 3;
    } else {
      return;
    }

    shapes.end();
    batch.begin();
    batch.setShader(null);
    int startX2 = startX;
    int startY2 = startY;
    float startPx2 = startPx;
    float startPy2 = startPy;
    int x, y;
    for (y = 0; y < viewBuffer.length; y++) {
      int tx = startX2;
      int ty = startY2;
      int stx = tx * Tile.SUBTILE_SIZE;
      int sty = ty * Tile.SUBTILE_SIZE;
      float px = startPx2;
      float py = startPy2;
      int size = viewBuffer[y];
      for (x = 0; x < size; x++) {
        Map.Zone zone = map.getZone(stx, sty);
        if (zone != null) {
          Map.Preset preset = zone.getGrid(tx, ty);
          if (preset != null) {
            DS1 ds1 = preset.ds1;
            int value = 0;
            int gx = (tx - zone.tx) % zone.gridSizeX;
            int gy = (ty - zone.ty) % zone.gridSizeY;
            switch (type) {
              case 0: {
                if (l >= ds1.numFloors) break;
                int ptr = l + (gy * ds1.floorLine) + (gx * ds1.numFloors);
                value = ds1.floors[ptr].value;
              }
                break;
              case 1: {
                if (l >= ds1.numShadows) break;
                int ptr = l + (gy * ds1.shadowLine) + (gx * ds1.numShadows);
                value = ds1.shadows[ptr].value;
              }
                break;
              case 2: {
                if (l >= ds1.numWalls) break;
                int ptr = l + (gy * ds1.wallLine) + (gx * ds1.numWalls);
                value = ds1.walls[ptr].value;
              }
                break;
              case 3: {
                if (l >= ds1.numTags) break;
                int ptr = l + (gy * ds1.tagLine) + (gx * ds1.numTags);
                value = ds1.tags[ptr];
              }
                break;
              default:
                value = 0;
            }

            if (value != 0) {
              value &= ~(DS1.Cell.MAIN_INDEX_MASK | DS1.Cell.SUB_INDEX_MASK);
              BitmapFont font = Riiablo.fonts.consolas12;
              String str = String.format("%08x", value);
              GlyphLayout layout = new GlyphLayout(font, str, 0, str.length(), font.getColor(), 0, Align.center, false, null);
              font.draw(batch, layout,
                  px + Tile.WIDTH50,
                  py + Tile.HEIGHT50 + font.getLineHeight() / 4);
            }
          }
        }

        tx++;
        stx += Tile.SUBTILE_SIZE;
        px += Tile.WIDTH50;
        py -= Tile.HEIGHT50;
      }

      startY2++;
      if (y >= tilesX - 1) {
        startX2++;
        startPy2 -= Tile.HEIGHT;
      } else {
        startX2--;
        startPx2 -= Tile.WIDTH;
      }
    }

    batch.end();
    batch.setShader(Riiablo.shader);
    shapes.begin(ShapeRenderer.ShapeType.Line);
  }

  private void drawDebugSpecial(ShapeRenderer shapes) {
    for (int i = Map.WALL_OFFSET, x, y; i < Map.WALL_OFFSET + Map.MAX_WALLS; i++) {
      int startX2 = startX;
      int startY2 = startY;
      float startPx2 = startPx;
      float startPy2 = startPy;
      for (y = 0; y < viewBuffer.length; y++) {
        int tx = startX2;
        int ty = startY2;
        int stx = tx * Tile.SUBTILE_SIZE;
        int sty = ty * Tile.SUBTILE_SIZE;
        float px = startPx2;
        float py = startPy2;
        int size = viewBuffer[y];
        for (x = 0; x < size; x++) {
          Map.Zone zone = map.getZone(stx, sty);
          if (zone != null) {
            DS1.Cell cell = zone.getCell(i, tx, ty);
            if (cell != null) {
              if (Map.ID.POPPADS.contains(cell.id)) {
                shapes.setColor(Map.ID.getColor(cell));
                Map.Preset preset = zone.getGrid(tx, ty);
                Map.Preset.PopPad popPad = preset.popPads.get(cell.id);
                if (popPad.startX == zone.getGridX(tx) && popPad.startY == zone.getGridY(ty)) {
                  int width  = popPad.endX - popPad.startX;
                  int height = popPad.endY - popPad.startY;
                  iso.getPixOffset(tmpVec2);
                  float offsetX = tmpVec2.x;
                  float offsetY = tmpVec2.y;
                  iso.toScreen(tmpVec2.set(stx, sty));
                  float topLeftX = tmpVec2.x - offsetX;
                  float topLeftY = tmpVec2.y - offsetY;
                  iso.toScreen(tmpVec2.set(stx, sty).add(width, 0));
                  float topRightX = tmpVec2.x - offsetX;
                  float topRightY = tmpVec2.y - offsetY;
                  iso.toScreen(tmpVec2.set(stx, sty).add(0, height));
                  float bottomLeftX = tmpVec2.x - offsetX;
                  float bottomLeftY = tmpVec2.y - offsetY;
                  iso.toScreen(tmpVec2.set(stx, sty).add(width, height));
                  float bottomRightX = tmpVec2.x - offsetX;
                  float bottomRightY = tmpVec2.y - offsetY;
                  shapes.line(topLeftX, topLeftY, topRightX, topRightY);
                  shapes.line(topRightX, topRightY, bottomRightX, bottomRightY);
                  shapes.line(bottomRightX, bottomRightY, bottomLeftX, bottomLeftY);
                  shapes.line(bottomLeftX, bottomLeftY, topLeftX, topLeftY);
                }
              } else {
                shapes.setColor(Color.WHITE);
                DebugUtils.drawDiamond2(shapes, px, py, Tile.WIDTH, Tile.HEIGHT);
              }
              shapes.end();

              batch.begin();
              batch.setShader(null);
              BitmapFont font = Riiablo.fonts.consolas12;
              String str = String.format("%s%n%08x", Map.ID.getName(cell.id), cell.value);
              GlyphLayout layout = new GlyphLayout(font, str, 0, str.length(), font.getColor(), 0, Align.center, false, null);
              font.draw(batch, layout,
                  px + Tile.WIDTH50,
                  py + Tile.HEIGHT50 + font.getLineHeight() / 4);
              batch.end();
              batch.setShader(Riiablo.shader);

              shapes.begin(ShapeRenderer.ShapeType.Line);
            }
          }

          tx++;
          stx += Tile.SUBTILE_SIZE;
          px += Tile.WIDTH50;
          py -= Tile.HEIGHT50;
        }

        startY2++;
        if (y >= tilesX - 1) {
          startX2++;
          startPy2 -= Tile.HEIGHT;
        } else {
          startX2--;
          startPx2 -= Tile.WIDTH;
        }
      }
    }
  }

  private void drawDebugObjects(ShapeRenderer shapes) {
    shapes.set(ShapeRenderer.ShapeType.Line);
    int startX2 = startX;
    int startY2 = startY;
    int x, y;
    for (y = 0; y < viewBuffer.length; y++) {
      int tx = startX2;
      int ty = startY2;
      int stx = tx * Tile.SUBTILE_SIZE;
      int sty = ty * Tile.SUBTILE_SIZE;
      int size = viewBuffer[y];
      for (x = 0; x < size; x++) {
        IntBag entities = debugEntitites.getEntities();
        for (int i = 0, numEntities = entities.size(); i < numEntities; i++) {
          int id = entities.get(i);
          Vector2 position = mPosition.get(id).position;
          if ((stx <= position.x && position.x < stx + Tile.SUBTILE_SIZE)
           && (sty <= position.y && position.y < sty + Tile.SUBTILE_SIZE)) {
            Vector2 tmp = iso.agg(tmpVec2.set(position)).toScreen().ret();
            shapes.setColor(Color.WHITE);
            DebugUtils.drawDiamond(shapes, tmp.x, tmp.y, Tile.SUBTILE_WIDTH, Tile.SUBTILE_HEIGHT);
            if (RENDER_DEBUG_SELECT && mSelectable.has(id)) {
              BBoxWrapper boxWrapper = mBBoxWrapper.get(id);
              if (boxWrapper != null) {
                BBox box = boxWrapper.box;
                if (box != null) {
                  shapes.setColor(mHovered.has(id) ? Color.GREEN : Color.GRAY);
                  shapes.rect(tmpVec2.x + box.xMin, tmpVec2.y - box.yMax, box.width, box.height);
                }
              }
            }
          }
        }

        tx++;
        stx += Tile.SUBTILE_SIZE;
      }

      startY2++;
      if (y >= tilesX - 1) {
        startX2++;
      } else {
        startX2--;
      }
    }

    shapes.set(ShapeRenderer.ShapeType.Line);

    shapes.end();
    batch.begin();
    batch.setShader(null);
    startX2 = startX;
    startY2 = startY;
    StringBuilder builder = new StringBuilder(64);
    for (y = 0; y < viewBuffer.length; y++) {
      int tx = startX2;
      int ty = startY2;
      int stx = tx * Tile.SUBTILE_SIZE;
      int sty = ty * Tile.SUBTILE_SIZE;
      int size = viewBuffer[y];
      for (x = 0; x < size; x++) {
        IntBag entities = debugEntitites.getEntities();
        for (int i = 0, numEntities = entities.size(); i < numEntities; i++) {
          int id = entities.get(i);
          Vector2 position = mPosition.get(id).position;
          if ((stx <= position.x && position.x < stx + Tile.SUBTILE_SIZE)
           && (sty <= position.y && position.y < sty + Tile.SUBTILE_SIZE)) {
            builder.setLength(0);

            builder.append(id);
            Networked networked = mNetworked.get(id);
            if (networked != null) {
              builder
                  .append(' ')
                  .append('(')
                  .append(networked.serverId)
                  .append(')');
            }
            builder.append('\n');

            builder.append(mClassname.get(id).classname).append('\n');

            Class.Type logicalType = mClass.get(id).type;
            builder.append(logicalType).append('\n');

            CofReference reference = mCofReference.get(id);
            if (reference != null) {
              Class.Type type = reference.effectiveType(logicalType);
              builder
                  .append(reference.effectiveToken().toUpperCase())
                  .append(' ')
                  .append(type.getMode(reference.effectiveMode(logicalType)))
                  .append(' ')
                  .append(Engine.getWClass(reference.effectiveWClass()))
                  .append('\n');
            }
            Angle angle = mAngle.get(id);
            if (angle != null) {
              builder
                  .append(String.format("%.02f", angle.angle.angleRad()))
                  .append('\n');
            }
            AnimData animData = mAnimData.get(id);
            if (animData != null) {
              builder
                  .append(StringUtils.leftPad(String.format("%d.%02X", animData.frame >>> 8, animData.frame & 0xFF), animData.numFrames >>> 8 > 10 ? 5 : 4))
                  .append('/')
                  .append(Integer.toString(animData.numFrames >>> 8))
                  .append(' ')
                  .append(String.format("%02X", animData.override >= 0 ? animData.override : animData.speed))
                  .append('\n');
            }
            AnimationWrapper animationWrapper = mAnimationWrapper.get(id);
            if (animationWrapper != null) {
              Animation animation = animationWrapper.animation;
              if (animation.getNumFramesPerDir() > 1) {
                builder
                    .append(StringUtils.leftPad(Integer.toString(animation.getFrame()), 2))
                    .append('/')
                    .append(Integer.toString(animation.getNumFramesPerDir() - 1))
                    .append(' ')
                    .append(animation.getFrameDelta())
                    .append('\n');
              }
            }
            AIWrapper aiComponent = mAIWrapper.get(id);
            if (aiComponent != null) {
              builder.append(aiComponent.ai.getState()).append('\n');
            }
            Vector2 tmp = iso.agg(tmpVec2.set(position)).toScreen().ret();

            AttributesWrapper attributesWrapper = mAttributesWrapper.get(id);
            if (attributesWrapper != null) {
              Attributes attrs = attributesWrapper.attrs;
              if (attrs != null && attrs.contains(Stat.hitpoints)) {
                float hitpoints = attrs.get(Stat.hitpoints).asFixed();
                float maxhp = attrs.get(Stat.maxhp).asFixed();
                if (maxhp > 0f && hitpoints > 0f) {
//                  batch.setColor(Riiablo.colors.black);
//                  batch.draw(Riiablo.textures.white, tmp.x - 50, tmp.y - Tile.SUBTILE_HEIGHT50, 100, 5);
                  batch.setColor(Riiablo.colors.darkRed);
                  batch.draw(Riiablo.textures.white, tmp.x - 50, tmp.y - Tile.SUBTILE_HEIGHT50, hitpoints / maxhp * 100, 5);
                  batch.resetColor();
                  builder.append("hitpoints: ").append((int) hitpoints).append('/').append((int) maxhp).append('\n');
                }
              }
            }

            GlyphLayout layout = Riiablo.fonts.consolas12.draw(batch, builder.toString(), tmp.x, tmp.y - Tile.SUBTILE_HEIGHT50 - 4, 0, Align.center, false);
            Pools.free(layout);
          }
        }

        tx++;
        stx += Tile.SUBTILE_SIZE;
      }

      startY2++;
      if (y >= tilesX - 1) {
        startX2++;
      } else {
        startX2--;
      }
    }
    batch.end();
    batch.setShader(Riiablo.shader);
    shapes.begin();
  }

  private void drawDebugCamera(ShapeRenderer shapes) {
    Vector2 tmp = tmpVec2.set(this.currentPos);
    iso.toScreen(tmp);
    float viewportWidth  = width;
    float viewportHeight = height;
    shapes.setColor(Color.GREEN);
    shapes.rect(
        tmp.x - MathUtils.ceil(viewportWidth  / 2) - 1,
        tmp.y - MathUtils.ceil(viewportHeight / 2) - 1,
        viewportWidth + 2, viewportHeight + 2);
  }

  private void drawDebugOverscan(ShapeRenderer shapes) {
    if ((RENDER_DEBUG_OVERSCAN & 0b001) == 0b001) {
      shapes.setColor(Color.LIGHT_GRAY);
      shapes.rect(
          tpx - renderWidth  / 2 + Tile.WIDTH50,
          tpy - renderHeight / 2 + Tile.HEIGHT50 + renderHeight,
          renderWidth, 96);
    }
    if ((RENDER_DEBUG_OVERSCAN & 0b010) == 0b010) {
      shapes.setColor(Color.GRAY);
      shapes.rect(
          tpx - renderWidth / 2 + Tile.WIDTH50,
          tpy - renderHeight / 2 + Tile.HEIGHT50,
          renderWidth, renderHeight);
    }
    if ((RENDER_DEBUG_OVERSCAN & 0b100) == 0b100) {
      shapes.setColor(Color.DARK_GRAY);
      shapes.rect(
          tpx - renderWidth / 2 + Tile.WIDTH50,
          tpy - renderHeight / 2 + Tile.HEIGHT50 - 96,
          renderWidth, 96);
    }
  }

  private void drawDebugMouse(ShapeRenderer shapes) {
    Vector2 tmp = iso.agg(tmpVec2.set(Gdx.input.getX(), Gdx.input.getY())).unproject().toWorld().toTile().toScreen().ret();
    shapes.set(ShapeRenderer.ShapeType.Filled);
    shapes.setColor(Color.SALMON);
    DebugUtils.drawDiamond(shapes, tmp.x, tmp.y, Tile.SUBTILE_WIDTH, Tile.SUBTILE_HEIGHT);
  }

  // 标记是否已发起加载请求
  private boolean automapLoadRequested = false;
  
  /**
   * 尝试获取小地图精灵资源（非阻塞）
   * 使用 Riiablo.mpqs 直接加载，绕过 AssetManager
   */
  private void tryLoadAutomapSprite() {
    // 如果已成功加载，直接返回
    if (automapSprite != null) return;
    
    // 只尝试一次
    if (automapSpriteLoaded) return;
    automapSpriteLoaded = true;
    
    try {
      // 直接从 MPQ 加载，不经过 AssetManager
      // Gdx.app.log(TAG, "Loading automap sprite from MPQ...");
      automapSprite = com.riiablo.codec.DC6.loadFromFile(Riiablo.mpqs.resolve(AUTOMAP_SPRITE_PATH));
      
      if (automapSprite != null) {
        // 必须先加载方向 0 的纹理，才能调用 getTexture()
        automapSprite.loadDirection(0);
        
        int frameCount = automapSprite.getNumFramesPerDir();
        int directions = automapSprite.getNumDirections();
        // Gdx.app.log(TAG, "Automap sprite loaded: " + frameCount + " frames, " + directions + " dirs");
      } else {
        // Gdx.app.error(TAG, "Automap sprite is null after loading");
      }
    } catch (Exception e) {
      // Gdx.app.error(TAG, "Failed to load automap sprite: " + e.getMessage());
    }
  }
  
  // 用于调试：计数绘制的精灵数量
  private int automapSpriteDrawCount = 0;
  private boolean automapSpriteDebugLogged = false;
  
  // 调试：记录跳过的原因
  private int automapSkippedNoFrame = 0;
  private int automapSkippedNoRegion = 0;
  private int automapSkippedException = 0;
  
  /**
   * 使用精灵渲染小地图墙壁
   */
  private void drawAutomapWallSprite(int orientation, float px, float py) {
    if (automapSprite == null) return;
    
    int frameIndex = getAutomapFrameForOrientation(orientation);
    if (frameIndex < 0 || frameIndex >= automapSprite.getNumFramesPerDir()) {
      automapSkippedNoFrame++;
      return;
    }
    
    try {
      com.badlogic.gdx.graphics.g2d.TextureRegion region = automapSprite.getTexture(0, frameIndex);
      if (region != null) {
        // 居中绘制精灵
        float cx = px + Tile.WIDTH50 - region.getRegionWidth() / 2f;
        float cy = py + Tile.HEIGHT50 - region.getRegionHeight() / 2f;
        batch.draw(region, cx, cy);
        automapSpriteDrawCount++;
        
        // 首次绘制时记录调试信息
        if (!automapSpriteDebugLogged && automapSpriteDrawCount == 1) {
          Gdx.app.log(TAG, "First sprite drawn: frame=" + frameIndex 
            + ", size=" + region.getRegionWidth() + "x" + region.getRegionHeight()
            + ", pos=" + cx + "," + cy);
        }
      } else {
        automapSkippedNoRegion++;
      }
    } catch (Exception e) {
      automapSkippedException++;
      // 只记录第一个异常的详细信息
      if (automapSkippedException == 1) {
        // Gdx.app.error(TAG, "Automap sprite draw exception: " + e.getClass().getSimpleName() 
        //     + " - " + e.getMessage() + ", frame=" + frameIndex);
      }
    }
  }
  
  /**
   * 根据瓷砖方向获取对应的小地图精灵帧索引
   */
  private int getAutomapFrameForOrientation(int orientation) {
    switch (orientation) {
      case Orientation.LEFT_WALL:
      case Orientation.LOWER_LEFT_WALL:
      case Orientation.LEFT_END_WALL:
        return AUTOMAP_FRAME_WALL_LEFT;
      case Orientation.RIGHT_WALL:
      case Orientation.LOWER_RIGHT_WALL:
      case Orientation.RIGHT_END_WALL:
        return AUTOMAP_FRAME_WALL_RIGHT;
      case Orientation.RIGHT_NORTH_CORNER_WALL:
      case Orientation.LOWER_NORTH_CORNER_WALL:
        return AUTOMAP_FRAME_WALL_CORNER_NE;
      case Orientation.LEFT_NORTH_CORNER_WALL:
        return AUTOMAP_FRAME_WALL_CORNER_NW;
      case Orientation.SOUTH_CORNER_WALL:
      case Orientation.LOWER_SOUTH_CORNER_WALL:
        return AUTOMAP_FRAME_WALL_CORNER_SE;
      case Orientation.LEFT_WALL_DOOR:
        return AUTOMAP_FRAME_DOOR_LEFT;
      case Orientation.RIGHT_WALL_DOOR:
        return AUTOMAP_FRAME_DOOR_RIGHT;
      case Orientation.PILLAR:
        return AUTOMAP_FRAME_WALL_CORNER_SE;
      default:
        return -1;
    }
  }

  // 用于离屏渲染小地图的标志
  private boolean automapSaveRequested = false;
  private boolean automapSaved = false;
  
  /**
   * 请求保存小地图到文件（下次渲染时执行）
   */
  public void requestSaveAutomap() {
    automapSaveRequested = true;
    automapSaved = false;
    // Gdx.app.log(TAG, "Automap save requested");
  }
  
  /**
   * 离屏渲染小地图到 PNG 文件
   * 使用标准 SpriteBatch 而不是 PaletteIndexedBatch
   */
  private void saveAutomapToFile() {
    if (automapSprite == null) {
      // Gdx.app.error(TAG, "Cannot save automap: sprite not loaded");
      return;
    }
    
    // Gdx.app.log(TAG, "Starting automap save to file...");
    
    // 计算地图边界 - 使用当前视口的瓦片坐标计算像素边界
    float minX = Float.MAX_VALUE, maxX = Float.MIN_VALUE;
    float minY = Float.MAX_VALUE, maxY = Float.MIN_VALUE;
    
    int wallCount = 0;
    
    // 遍历所有可见瓦片，计算边界
    int startX2 = startX;
    int startY2 = startY;
    float startPx2 = startPx;
    float startPy2 = startPy;
    
    for (int y = 0; y < viewBuffer.length; y++) {
      int tx = startX2;
      int ty = startY2;
      float px = startPx2;
      float py = startPy2;
      int size = viewBuffer[y];
      
      for (int x = 0; x < size; x++) {
        Map.Zone zone = map.getZone(tx * Tile.SUBTILE_SIZE, ty * Tile.SUBTILE_SIZE);
        if (zone != null) {
          // 遍历所有墙层
          for (int layer = Map.WALL_OFFSET; layer < Map.WALL_OFFSET + Map.MAX_WALLS; layer++) {
            Tile tile = zone.get(layer, tx, ty);
            if (tile != null && Orientation.isWall(tile.orientation)) {
              minX = Math.min(minX, px);
              maxX = Math.max(maxX, px + Tile.WIDTH);
              minY = Math.min(minY, py - Tile.HEIGHT);
              maxY = Math.max(maxY, py + Tile.HEIGHT);
              wallCount++;
            }
          }
        }
        tx++;
        px += Tile.WIDTH50;
        py -= Tile.HEIGHT50;
      }
      
      startY2++;
      if (y >= tilesX - 1) {
        startX2++;
        startPy2 -= Tile.HEIGHT;
      } else {
        startX2--;
        startPx2 -= Tile.WIDTH;
      }
    }
    
    // Gdx.app.log(TAG, "Automap bounds: wallCount=" + wallCount 
    //     + ", minX=" + minX + ", maxX=" + maxX 
    //     + ", minY=" + minY + ", maxY=" + maxY);
    
    if (wallCount == 0) {
      Gdx.app.error(TAG, "No walls found to render");
      return;
    }
    
    // 添加边距
    float padding = 100;
    minX -= padding;
    minY -= padding;
    maxX += padding;
    maxY += padding;
    
    // 计算图片尺寸
    int width = (int)(maxX - minX);
    int height = (int)(maxY - minY);
    
    // 限制最大尺寸
    int maxSize = 2048;
    if (width > maxSize || height > maxSize) {
      float scale = Math.min((float)maxSize / width, (float)maxSize / height);
      width = (int)(width * scale);
      height = (int)(height * scale);
    }
    
    Gdx.app.log(TAG, "Creating framebuffer: " + width + "x" + height);
    
    // 创建帧缓冲
    FrameBuffer fbo = null;
    SpriteBatch simpleBatch = null;
    ShapeRenderer simpleShapes = null;
    
    try {
      fbo = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
      simpleBatch = new SpriteBatch();
      simpleShapes = new ShapeRenderer();
      
      // 设置正交投影矩阵
      Matrix4 projection = new Matrix4();
      projection.setToOrtho2D(minX, minY, maxX - minX, maxY - minY);
      
      // 开始渲染到帧缓冲
      fbo.begin();
      Gdx.gl.glClearColor(0, 0, 0, 1);
      Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
      
      // 使用 ShapeRenderer 渲染墙壁（不使用精灵，避免调色板问题）
      simpleShapes.setProjectionMatrix(projection);
      simpleShapes.begin(ShapeRenderer.ShapeType.Line);
      
      int drawnCount = 0;
      
      // 重新遍历，渲染墙壁线条
      startX2 = startX;
      startY2 = startY;
      startPx2 = startPx;
      startPy2 = startPy;
      
      for (int y = 0; y < viewBuffer.length; y++) {
        int tx = startX2;
        int ty = startY2;
        float px = startPx2;
        float py = startPy2;
        int size = viewBuffer[y];
        
        for (int x = 0; x < size; x++) {
          Map.Zone zone = map.getZone(tx * Tile.SUBTILE_SIZE, ty * Tile.SUBTILE_SIZE);
          if (zone != null) {
            // 遍历所有墙层
            for (int layer = Map.WALL_OFFSET; layer < Map.WALL_OFFSET + Map.MAX_WALLS; layer++) {
              Tile tile = zone.get(layer, tx, ty);
              if (tile != null && Orientation.isWall(tile.orientation)) {
                int orientation = tile.orientation;
                
                // 根据方向选择颜色
                switch (orientation) {
                  case Orientation.LEFT_WALL:
                  case Orientation.LOWER_LEFT_WALL:
                    simpleShapes.setColor(Color.WHITE);
                    // 左墙：从中心向左下
                    simpleShapes.line(px + Tile.WIDTH50, py, px, py - Tile.HEIGHT50);
                    break;
                  case Orientation.RIGHT_WALL:
                  case Orientation.LOWER_RIGHT_WALL:
                    simpleShapes.setColor(Color.WHITE);
                    // 右墙：从中心向右下
                    simpleShapes.line(px + Tile.WIDTH50, py, px + Tile.WIDTH, py - Tile.HEIGHT50);
                    break;
                  case Orientation.SOUTH_CORNER_WALL:
                  case Orientation.LOWER_SOUTH_CORNER_WALL:
                    simpleShapes.setColor(Color.YELLOW);
                    // 南角：画一个点
                    simpleShapes.circle(px + Tile.WIDTH50, py - Tile.HEIGHT50, 5);
                    break;
                  case Orientation.LEFT_NORTH_CORNER_WALL:
                  case Orientation.RIGHT_NORTH_CORNER_WALL:
                  case Orientation.LOWER_NORTH_CORNER_WALL:
                    simpleShapes.setColor(Color.CYAN);
                    // 北角：画一个点
                    simpleShapes.circle(px + Tile.WIDTH50, py, 5);
                    break;
                  case Orientation.LEFT_WALL_DOOR:
                  case Orientation.RIGHT_WALL_DOOR:
                    simpleShapes.setColor(Color.ORANGE);
                    // 门：画一个小方块
                    simpleShapes.rect(px + Tile.WIDTH50 - 5, py - 5, 10, 10);
                    break;
                  default:
                    simpleShapes.setColor(Color.GRAY);
                    // 其他墙：画一个小圆
                    simpleShapes.circle(px + Tile.WIDTH50, py - Tile.HEIGHT50 / 2, 3);
                    break;
                }
                drawnCount++;
              }
            }
          }
          tx++;
          px += Tile.WIDTH50;
          py -= Tile.HEIGHT50;
        }
        
        startY2++;
        if (y >= tilesX - 1) {
          startX2++;
          startPy2 -= Tile.HEIGHT;
        } else {
          startX2--;
          startPx2 -= Tile.WIDTH;
        }
      }
      
      simpleShapes.end();
      
      // 额外渲染一些参考点（使用 ShapeRenderer）
      simpleShapes.begin(ShapeRenderer.ShapeType.Filled);
      
      // 在玩家位置画一个红点
      simpleShapes.setColor(Color.RED);
      simpleShapes.circle(iso.position.x, iso.position.y, 20);
      
      // 画视口边界
      simpleShapes.setColor(Color.GREEN);
      simpleShapes.rectLine(minX + padding, minY + padding, maxX - padding, minY + padding, 3);
      simpleShapes.rectLine(maxX - padding, minY + padding, maxX - padding, maxY - padding, 3);
      simpleShapes.rectLine(maxX - padding, maxY - padding, minX + padding, maxY - padding, 3);
      simpleShapes.rectLine(minX + padding, maxY - padding, minX + padding, minY + padding, 3);
      
      simpleShapes.end();
      
      Gdx.app.log(TAG, "Drew " + drawnCount + " sprites to framebuffer");
      
      // 在 FBO 仍然绑定时读取像素
      Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, width, height);
      
      // 结束 FBO
      fbo.end();
      
      // 翻转像素（FBO 是上下颠倒的）
      Pixmap flipped = new Pixmap(width, height, Pixmap.Format.RGBA8888);
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          flipped.drawPixel(x, height - 1 - y, pixmap.getPixel(x, y));
        }
      }
      pixmap.dispose();
      
      // 保存到文件
      FileHandle file = Gdx.files.absolute("automap_debug.png");
      PixmapIO.writePNG(file, flipped);
      flipped.dispose();
      
      // Gdx.app.log(TAG, "Automap saved to: " + file.path());
      
    } catch (Exception e) {
      // Gdx.app.error(TAG, "Failed to save automap: " + e.getMessage(), e);
    } finally {
      if (simpleBatch != null) simpleBatch.dispose();
      if (simpleShapes != null) simpleShapes.dispose();
      if (fbo != null) fbo.dispose();
    }
    
    automapSaveRequested = false;
    automapSaved = true;
  }
  
  /**
   * Draws the automap overlay showing the explored map areas.
   * 
   * automap 行为描述：
   * 1. 窗口大小：屏幕宽高的1/2
   * 2. 窗口位置：3种模式（左上、右上、中间，默认中间）
   * 3. 默认显示内容：当前屏幕范围对应的世界坐标内容，缩小到1/2显示
   * 4. 箭头移动：改变显示范围的x, y（世界坐标），范围大小不变
   * 5. +/-缩放：以当前范围中心为中心，缩放10%
   */
  public void drawAutomap(ShapeRenderer shapes) {
    if (AUTOMAP_MODE == AUTOMAP_MODE_OFF) {
      automapFirstRender = false;  // 重置标志
      return;
    }
    
    // 检测是否是首次打开（从OFF切换到ON）
    boolean isFirstRender = !automapFirstRender;
    if (isFirstRender) {
      automapFirstRender = true;
      // Gdx.app.log(TAG, "=== Automap first render - Debug info ===");
    }
    
    // 尝试加载小地图精灵（保留用于将来可能的精灵渲染）
    tryLoadAutomapSprite();
    
    // 仅在首次打开小地图时记录一次
    if (!automapLoggedOnce) {
      // Gdx.app.log(TAG, "Automap using line rendering (original D2 style)");
      automapLoggedOnce = true;
    }
    
    // 获取屏幕尺寸
    float screenWidth = iso.viewportWidth;
    float screenHeight = iso.viewportHeight;
    
    // automap窗口大小：
    // - CENTER 模式：占满全屏
    // - TOP_LEFT / TOP_RIGHT 模式：屏幕的 1/2
    float sizeRatio = AUTOMAP_SIZE_RATIO; // 默认 0.5
    if (AUTOMAP_MODE == AUTOMAP_MODE_CENTER) {
      sizeRatio = 1.0f;
    }
    float automapWidth = screenWidth * sizeRatio;
    float automapHeight = screenHeight * sizeRatio;
    
    // 调试日志：检查 automap 尺寸计算（已屏蔽）
    // if (isFirstRender) {
    //   Gdx.app.log(TAG, String.format(
    //       "[Automap Size Debug] mode=%d (CENTER=%d), screenSize=(%.1f, %.1f), sizeRatio=%.2f, automapSize=(%.1f, %.1f)",
    //       AUTOMAP_MODE, AUTOMAP_MODE_CENTER, screenWidth, screenHeight, sizeRatio, automapWidth, automapHeight));
    // }
    
    // 计算automap窗口位置
    float automapScreenX, automapScreenY;
    switch (AUTOMAP_MODE) {
      case AUTOMAP_MODE_TOP_LEFT:
        automapScreenX = 0;
        automapScreenY = screenHeight - automapHeight;
        break;
      case AUTOMAP_MODE_TOP_RIGHT:
        automapScreenX = screenWidth - automapWidth;
        automapScreenY = screenHeight - automapHeight;
        break;
      case AUTOMAP_MODE_CENTER:
      default:
        automapScreenX = (screenWidth - automapWidth) / 2f;
        automapScreenY = (screenHeight - automapHeight) / 2f;
        break;
    }
    
    // 调试日志：检查 automap 窗口位置和大小（已屏蔽）
    // if (isFirstRender) {
    //   Gdx.app.log(TAG, String.format(
    //       "[Automap Window Debug] position=(%.1f, %.1f), size=(%.1f, %.1f), screenSize=(%.1f, %.1f)",
    //       automapScreenX, automapScreenY, automapWidth, automapHeight, screenWidth, screenHeight));
    //   Gdx.app.log(TAG, String.format(
    //       "[Automap Summary] mode=%d, sizeRatio=%.2f, expectedSize=(%.1f, %.1f), actualSize=(%.1f, %.1f), match=%s",
    //       AUTOMAP_MODE, sizeRatio, screenWidth * sizeRatio, screenHeight * sizeRatio, 
    //       automapWidth, automapHeight, 
    //       (Math.abs(automapWidth - screenWidth * sizeRatio) < 0.1f && Math.abs(automapHeight - screenHeight * sizeRatio) < 0.1f) ? "YES" : "NO"));
    // }
    
    // 获取玩家位置
    Vector2 playerPos = iso.position;
    
    // 初始化显示范围（如果未初始化或需要重置）
    // 在 CENTER 模式下，应该根据 automap 窗口大小来调整显示范围
    // 在其他模式下，使用 iso.viewport 的大小
    if (automapViewWidth == 0 || automapViewHeight == 0) {
      float defaultViewWidth, defaultViewHeight;
      if (AUTOMAP_MODE == AUTOMAP_MODE_CENTER) {
        // CENTER 模式：使用 automap 窗口大小（全屏）
        defaultViewWidth = automapWidth * iso.zoom * 3 / Tile.SUBTILE_SIZE;
        defaultViewHeight = automapHeight * iso.zoom * 3 / Tile.SUBTILE_SIZE;
      } else {
        // 其他模式：使用 iso.viewport 大小
        defaultViewWidth = iso.viewportWidth / Tile.SUBTILE_SIZE; 
        defaultViewHeight = iso.viewportHeight / Tile.SUBTILE_SIZE;
      } 
      
      automapViewWidth = defaultViewWidth;
      automapViewHeight = defaultViewHeight;
      
      // 保存默认值用于计算缩放限制
      automapDefaultViewWidth = defaultViewWidth;
      automapDefaultViewHeight = defaultViewHeight;
      
      if (isFirstRender) {
         //Gdx.app.log(TAG, String.format(
         //     "Automap initialized: viewWidth=%.1f, viewHeight=%.1f (mode=%d, automapSize=(%.1f,%.1f), player=%.1f,%.1f)",
         //     automapViewWidth, automapViewHeight, AUTOMAP_MODE, automapWidth, automapHeight, playerPos.x, playerPos.y));
      }
    }
    
    // 每帧更新：以玩家为中心（加上用户偏移）
    // automapViewX/Y 作为用户偏移量使用（初始为0）
    float centerX = playerPos.x + automapViewX;
    float centerY = playerPos.y + automapViewY;
    
    // 计算显示范围的边界（以中心点为基准）
    float viewMinX = centerX - automapViewWidth / 2f;
    float viewMaxX = centerX + automapViewWidth / 2f;
    float viewMinY = centerY - automapViewHeight / 2f;
    float viewMaxY = centerY + automapViewHeight / 2f;
    
    // 计算世界坐标到屏幕坐标的缩放比例
    // 注意：对于等距投影，我们需要考虑等距坐标的范围
    // isoX 范围 = (viewWidth + viewHeight) * AUTOMAP_ISO_X_SCALE
    // isoY 范围 = (viewWidth + viewHeight) * AUTOMAP_ISO_Y_SCALE
    // 所以缩放比例应该基于等距坐标的范围，而不是世界坐标的范围
    float isoRangeX = (automapViewWidth + automapViewHeight) * AUTOMAP_ISO_X_SCALE;
    float isoRangeY = (automapViewWidth + automapViewHeight) * AUTOMAP_ISO_Y_SCALE;
    float worldToScreenScaleX = automapWidth / isoRangeX;
    float worldToScreenScaleY = automapHeight / isoRangeY;
    
    // 首次渲染时记录参数
    if (isFirstRender) {
      Gdx.app.log(TAG, String.format(
          "Automap window: screenX=%.1f, screenY=%.1f, width=%.1f, height=%.1f",
          automapScreenX, automapScreenY, automapWidth, automapHeight));
      Gdx.app.log(TAG, String.format(
          "Automap view: center=(%.1f, %.1f), width=%.1f, height=%.1f, bounds=[%.1f-%.1f, %.1f-%.1f]",
          centerX, centerY, automapViewWidth, automapViewHeight,
          viewMinX, viewMaxX, viewMinY, viewMaxY));
      Gdx.app.log(TAG, String.format(
          "Scale: scaleX=%.4f, scaleY=%.4f, player=(%.1f, %.1f)",
          worldToScreenScaleX, worldToScreenScaleY, playerPos.x, playerPos.y));
      // 调试日志：检查显示范围（已屏蔽）
      // Gdx.app.log(TAG, String.format(
      //     "[View Range Debug] viewWidth=%.1f (%.1f%% of screen), viewHeight=%.1f (%.1f%% of screen), SUBTILE_SIZE=%d",
      //     automapViewWidth, (automapViewWidth * Tile.SUBTILE_SIZE / screenWidth * 100f),
      //     automapViewHeight, (automapViewHeight * Tile.SUBTILE_SIZE / screenHeight * 100f),
      //     Tile.SUBTILE_SIZE));
    }
    
    // 使用相机的投影矩阵（用于绘制）
    shapes.setProjectionMatrix(iso.combined);
    
    // 保存之前的投影矩阵类型
    ShapeRenderer.ShapeType previousType = shapes.getCurrentType();
    
    // 使用 Line 模式绘制墙壁线条
    shapes.set(ShapeRenderer.ShapeType.Line);
    // 根据显示范围大小调整线宽
    float avgScale = (worldToScreenScaleX + worldToScreenScaleY) / 2f;
    float lineWidth = Math.min(5.0f, 2f * avgScale);
    Gdx.gl.glLineWidth(lineWidth);
    
    // Set alpha
    float alpha = 0.7f;
    
    // 等距坐标转换公式（Isometric Projection）：
    // 暗黑2使用等距视角，世界坐标需要转换为等距屏幕坐标
    // 这里为 automap 单独使用可调角度的等距变换：
    // isoX = (worldX - worldY) * AUTOMAP_ISO_X_SCALE
    // isoY = (worldX + worldY) * AUTOMAP_ISO_Y_SCALE
    // 其中 isoY / isoX = tan(角度)，当前系数约对应 30° 的夹角（比默认 45° 更“平”）
    
    // 计算玩家在等距坐标系中的位置（作为中心点）
    float playerIsoX = (centerX - centerY) * AUTOMAP_ISO_X_SCALE;
    float playerIsoY = (centerX + centerY) * AUTOMAP_ISO_Y_SCALE;
    
    // automap窗口中心
    float automapCenterX = automapScreenX + automapWidth / 2f;
    float automapCenterY = automapScreenY + automapHeight / 2f;
    
    // 调试日志：检查中心点计算和等距坐标范围（已屏蔽）
    // if (isFirstRender) {
    //   Gdx.app.log(TAG, String.format(
    //       "[Automap Center Debug] screenPos=(%.1f, %.1f), size=(%.1f, %.1f), center=(%.1f, %.1f)",
    //       automapScreenX, automapScreenY, automapWidth, automapHeight, automapCenterX, automapCenterY));
    //   // 计算等距坐标的范围（用于调试）
    //   // 对于等距投影：isoX = (worldX - worldY) * AUTOMAP_ISO_X_SCALE, isoY = (worldX + worldY) * AUTOMAP_ISO_Y_SCALE
    //   // 当世界坐标范围是 [centerX ± viewWidth/2, centerY ± viewHeight/2] 时：
    //   // isoX 范围 = (viewWidth + viewHeight) * AUTOMAP_ISO_X_SCALE
    //   // isoY 范围 = (viewWidth + viewHeight) * AUTOMAP_ISO_Y_SCALE
    //   float viewIsoWidth = (automapViewWidth + automapViewHeight) * AUTOMAP_ISO_X_SCALE;
    //   float viewIsoHeight = (automapViewWidth + automapViewHeight) * AUTOMAP_ISO_Y_SCALE;
    //   Gdx.app.log(TAG, String.format(
    //       "[Iso Range Debug] playerIso=(%.1f, %.1f), viewIsoRange=(%.1f, %.1f), expectedScreenRange=(%.1f, %.1f)",
    //       playerIsoX, playerIsoY, viewIsoWidth, viewIsoHeight,
    //       viewIsoWidth * worldToScreenScaleX, viewIsoHeight * worldToScreenScaleY));
    // }
    
    // 调试统计
    int wallsFound = 0;
    
    // 绘制显示范围内的墙壁线条
    // 使用遍历方式，只绘制在显示范围内的瓷砖
    // 注意：viewMinX/viewMaxX 等是 subtile 坐标（与玩家/实体相同的坐标系）
    // 需要将 subtile 坐标转换为 tile 坐标来遍历
    int tileViewMinX = (int) Math.floor(viewMinX / Tile.SUBTILE_SIZE);
    int tileViewMaxX = (int) Math.ceil(viewMaxX / Tile.SUBTILE_SIZE);
    int tileViewMinY = (int) Math.floor(viewMinY / Tile.SUBTILE_SIZE);
    int tileViewMaxY = (int) Math.ceil(viewMaxY / Tile.SUBTILE_SIZE);
    
    // 添加边界padding
    int padding = 2;
    
    // 遍历显示范围内的瓷砖
    for (int ty = tileViewMinY - padding; ty <= tileViewMaxY + padding; ty++) {
      for (int tx = tileViewMinX - padding; tx <= tileViewMaxX + padding; tx++) {
        // 瓦片的世界坐标（subtile 坐标，与实体坐标系一致）
        // tile 坐标 * SUBTILE_SIZE = subtile 坐标（tile 的左上角）
        float tileWorldX = tx * Tile.SUBTILE_SIZE;
        float tileWorldY = ty * Tile.SUBTILE_SIZE;
        
        // 检查瓷砖是否在显示范围内（使用 subtile 坐标）
        if (tileWorldX < viewMinX - Tile.SUBTILE_SIZE || tileWorldX > viewMaxX + Tile.SUBTILE_SIZE ||
            tileWorldY < viewMinY - Tile.SUBTILE_SIZE || tileWorldY > viewMaxY + Tile.SUBTILE_SIZE) {
          continue;
        }
        
        // 获取该瓷砖的zone
        Map.Zone zone = map.getZone(tx * Tile.SUBTILE_SIZE, ty * Tile.SUBTILE_SIZE);
        if (zone == null) continue;
        
        // 遍历所有墙层，绘制线条
        for (int layer = Map.WALL_OFFSET; layer < Map.WALL_OFFSET + Map.MAX_WALLS; layer++) {
          Tile tile = zone.get(layer, tx, ty);
          if (tile != null && Orientation.isWall(tile.orientation)) {
            wallsFound++;
            
            // 将世界坐标转换为等距坐标，再映射到automap窗口
            float tileIsoX = (tileWorldX - tileWorldY) * AUTOMAP_ISO_X_SCALE;
            float tileIsoY = (tileWorldX + tileWorldY) * AUTOMAP_ISO_Y_SCALE;
            
            // 相对于玩家的等距偏移
            float relIsoX = tileIsoX - playerIsoX;
            float relIsoY = tileIsoY - playerIsoY;
            
            // 映射到automap窗口（以窗口中心为基准，应用缩放）
            float screenX = automapCenterX + relIsoX * worldToScreenScaleX;
            float screenY = automapCenterY - relIsoY * worldToScreenScaleY; // Y轴翻转（标准45°等距）
            
            // 首次渲染时，记录前10个墙壁的坐标变换
            // if (isFirstRender && wallsFound <= 10) {
            //   Gdx.app.debug(TAG, String.format(
            //       "Wall #%d: tile(%d,%d) world(%.1f, %.1f) -> screen(%.1f, %.1f) [window range: X[%.1f-%.1f], Y[%.1f-%.1f]]",
            //       wallsFound, tx, ty, tileWorldX, tileWorldY, screenX, screenY,
            //       automapScreenX, automapScreenX + automapWidth,
            //       automapScreenY, automapScreenY + automapHeight));
            // }
            
            // 使用屏幕坐标绘制（需要临时切换到automap坐标系统）
            // 保存当前投影矩阵
            Matrix4 originalMatrix = shapes.getProjectionMatrix();
            // 设置automap的本地坐标系统（0,0到automapWidth,automapHeight）
            Matrix4 automapLocalMatrix = new Matrix4();
            automapLocalMatrix.setToOrtho2D(0, 0, automapWidth, automapHeight);
            shapes.setProjectionMatrix(automapLocalMatrix);
            
            // 转换为automap本地坐标（相对于窗口左上角）
            float localX = screenX - automapScreenX;
            float localY = screenY - automapScreenY;
            
            // 调试日志：检查坐标转换（已屏蔽）
            // if (isFirstRender && wallsFound <= 5) {
            //   Gdx.app.log(TAG, String.format(
            //       "[Wall Coord Debug] wall#%d: world=(%.1f,%.1f) iso=(%.1f,%.1f) relIso=(%.1f,%.1f) -> screen=(%.1f,%.1f) -> local=(%.1f,%.1f) [center=(%.1f,%.1f), scale=(%.2f,%.2f)]",
            //       wallsFound, tileWorldX, tileWorldY, tileIsoX, tileIsoY, relIsoX, relIsoY,
            //       screenX, screenY, localX, localY, automapCenterX, automapCenterY, worldToScreenScaleX, worldToScreenScaleY));
            // }
            
            // 使用本地坐标绘制墙壁线条（需要考虑缩放）
            drawAutomapWallLine(shapes, tile.orientation, localX, localY, alpha, worldToScreenScaleX, worldToScreenScaleY);
            
            // 恢复原始投影矩阵
            shapes.setProjectionMatrix(originalMatrix);
          }
        }
      }
    }
    
    // 调试日志：偶尔输出统计信息
    if (wallsFound == 0 && (System.currentTimeMillis() / 1000) % 2 == 0) {
      // Gdx.app.debug(TAG, "Automap: wallsFound=" + wallsFound 
      //     + ", viewX=" + automapViewX + ", viewY=" + automapViewY
      //     + ", viewWidth=" + automapViewWidth + ", viewHeight=" + automapViewHeight);
    }
    
    Gdx.gl.glLineWidth(1f); // 恢复默认线宽

    // 切换到 Filled 模式绘制实体
    shapes.set(ShapeRenderer.ShapeType.Filled);
    
    // Draw entities on automap (先绘制图标，不绘制文本)
    drawAutomapEntities(shapes, null, alpha, isFirstRender, worldToScreenScaleX, worldToScreenScaleY);
    
    shapes.set(previousType);
    
    // 在所有绘制完成后，单独绘制文本（确保文本在最上层）
    // 创建临时 SpriteBatch 用于绘制文本（NPC 名字等）
    SpriteBatch textBatch = new SpriteBatch();
    // 设置投影矩阵为 automap 本地坐标系统
    Matrix4 textBatchMatrix = new Matrix4();
    float textScreenWidth = iso.viewportWidth;
    float textScreenHeight = iso.viewportHeight;
    float textSizeRatio = AUTOMAP_SIZE_RATIO;
    if (AUTOMAP_MODE == AUTOMAP_MODE_CENTER) {
      textSizeRatio = 1.0f;
    }
    float textAutomapWidth = textScreenWidth * textSizeRatio;
    float textAutomapHeight = textScreenHeight * textSizeRatio;
    textBatchMatrix.setToOrtho2D(0, 0, textAutomapWidth, textAutomapHeight);
    textBatch.setProjectionMatrix(textBatchMatrix);
    textBatch.begin();
    
    // 绘制 NPC 名字（需要重新计算坐标）
    drawAutomapNpcNames(textBatch, alpha, isFirstRender, worldToScreenScaleX, worldToScreenScaleY);
    
    textBatch.end();
    textBatch.dispose();
  }
  
  /**
   * 绘制单个墙壁的线条
   * 原版暗黑2小地图风格
   * 使用automap窗口的本地坐标（相对于窗口左上角，单位：像素）
   * 
   * @param shapes ShapeRenderer 实例
   * @param orientation 墙壁方向
   * @param px 瓷砖左上角X坐标（automap窗口本地坐标，像素）
   * @param py 瓷砖左上角Y坐标（automap窗口本地坐标，像素）
   * @param alpha 透明度
   * @param scaleX X轴缩放比例（世界坐标到屏幕坐标）
   * @param scaleY Y轴缩放比例（世界坐标到屏幕坐标）
   */
  private void drawAutomapWallLine(ShapeRenderer shapes, int orientation, float px, float py, float alpha, float scaleX, float scaleY) {
    // px, py 是瓷砖左上角在automap窗口中的坐标（像素）
    // 现在坐标系是 subtile 坐标，一个 tile = SUBTILE_SIZE (5) subtiles
    // scaleX/scaleY 是 世界坐标(subtile) -> 屏幕像素 的缩放比例
    
    // 一个 tile 在 subtile 坐标系中的尺寸是 SUBTILE_SIZE x SUBTILE_SIZE
    // 转换到屏幕像素后的尺寸：
    float tileWidthPixels = Tile.SUBTILE_SIZE * scaleX;   // tile 宽度（像素）
    float tileHeightPixels = Tile.SUBTILE_SIZE * scaleY;  // tile 高度（像素）
    
    // 计算瓷砖中心点（在automap窗口中的像素坐标）
    float cx = px + tileWidthPixels / 2f;   // 中心X = 左上角X + 一半宽度
    float cy = py + tileHeightPixels / 2f;  // 中心Y = 左上角Y + 一半高度
    
    // 使用 tile 尺寸的一部分作为线条长度（像素单位）
    // 半个 tile 的 subtile 长度 = SUBTILE_SIZE / 2 = 2.5
    float halfTilePixels = tileWidthPixels / 2f;  // 半个 tile 宽度（像素）
    float quarterTilePixels = halfTilePixels / 2f; // 四分之一 tile（像素）
    
    // 用于绘制的线条长度
    float line32 = halfTilePixels;     // 半个 tile（用于主线条）
    float line16 = halfTilePixels;     // 半个 tile 高度
    float line8 = quarterTilePixels;   // 四分之一
    float line4 = line8 / 2f;          // 八分之一
    
    // 根据方向绘制不同的线条
    // 等距视角的automap中，墙壁应该绘制成45度斜线：
    // - 左墙（LEFT_WALL）朝向西南，在等距投影后显示为向左下延伸的斜线
    // - 右墙（RIGHT_WALL）朝向东南，在等距投影后显示为向右下延伸的斜线
    // 等距变换：isoX = (worldX - worldY) * AUTOMAP_ISO_X_SCALE, isoY = (worldX + worldY) * AUTOMAP_ISO_Y_SCALE
    // 左墙方向（worldY增加1 subtile）：等距偏移 = (-AUTOMAP_ISO_X_SCALE, AUTOMAP_ISO_Y_SCALE)
    // 右墙方向（worldX增加1 subtile）：等距偏移 = (AUTOMAP_ISO_X_SCALE, AUTOMAP_ISO_Y_SCALE)
    // 屏幕像素偏移 = 等距偏移 * scale
    
    // 计算斜线方向向量（基于等距投影比例和缩放）
    // 左墙：向左下（-ISO_X_SCALE * scaleX, +ISO_Y_SCALE * scaleY）
    float leftWallDx = -AUTOMAP_ISO_X_SCALE * scaleX;  // 1 subtile 在等距X方向的屏幕像素
    float leftWallDy = AUTOMAP_ISO_Y_SCALE * scaleY;  // 1 subtile 在等距Y方向的屏幕像素
    // 归一化方向向量，然后缩放到 line32 长度
    float leftWallLen = (float) Math.sqrt(leftWallDx * leftWallDx + leftWallDy * leftWallDy);
    leftWallDx = leftWallDx / leftWallLen * line32;
    leftWallDy = leftWallDy / leftWallLen * line32;
    
    // 右墙：向右下（+ISO_X_SCALE * scaleX, +ISO_Y_SCALE * scaleY）
    float rightWallDx = AUTOMAP_ISO_X_SCALE * scaleX;
    float rightWallDy = AUTOMAP_ISO_Y_SCALE * scaleY;
    float rightWallLen = (float) Math.sqrt(rightWallDx * rightWallDx + rightWallDy * rightWallDy);
    rightWallDx = rightWallDx / rightWallLen * line32;
    rightWallDy = rightWallDy / rightWallLen * line32;
    
    switch (orientation) {
      case Orientation.LEFT_WALL:
      case Orientation.LOWER_LEFT_WALL:
      case Orientation.LEFT_END_WALL:
        // 左墙（南北向）：应该绘制成东西向的斜线（使用右墙方向）
        shapes.setColor(AUTOMAP_WALL_COLOR.r, AUTOMAP_WALL_COLOR.g, AUTOMAP_WALL_COLOR.b, alpha);
        shapes.line(cx, cy, cx + rightWallDx, cy + rightWallDy);
        break;
        
      case Orientation.LEFT_WALL_DOOR:
        // 左门（南北向）：应该绘制成东西向的斜线，中间有缺口
        shapes.setColor(AUTOMAP_BRIGHT_COLOR.r, AUTOMAP_BRIGHT_COLOR.g, AUTOMAP_BRIGHT_COLOR.b, alpha);
        float leftDoorDx8 = rightWallDx / line32 * line8;  // 按比例缩放
        float leftDoorDy8 = rightWallDy / line32 * line8;
        shapes.line(cx, cy, cx + leftDoorDx8, cy + leftDoorDy8);
        shapes.line(cx + rightWallDx, cy + rightWallDy, cx + rightWallDx - leftDoorDx8, cy + rightWallDy - leftDoorDy8);
        break;
        
      case Orientation.RIGHT_WALL:
      case Orientation.LOWER_RIGHT_WALL:
      case Orientation.RIGHT_END_WALL:
        // 右墙（东西向）：应该绘制成南北向的斜线（使用左墙方向）
        shapes.setColor(AUTOMAP_WALL_COLOR.r, AUTOMAP_WALL_COLOR.g, AUTOMAP_WALL_COLOR.b, alpha);
        shapes.line(cx, cy, cx + leftWallDx, cy + leftWallDy);
        break;
        
      case Orientation.RIGHT_WALL_DOOR:
        // 右门（东西向）：应该绘制成南北向的斜线，中间有缺口
        shapes.setColor(AUTOMAP_BRIGHT_COLOR.r, AUTOMAP_BRIGHT_COLOR.g, AUTOMAP_BRIGHT_COLOR.b, alpha);
        float rightDoorDx8 = leftWallDx / line32 * line8;  // 按比例缩放
        float rightDoorDy8 = leftWallDy / line32 * line8;
        shapes.line(cx, cy, cx + rightDoorDx8, cy + rightDoorDy8);
        shapes.line(cx + leftWallDx, cy + leftWallDy, cx + leftWallDx - rightDoorDx8, cy + leftWallDy - rightDoorDy8);
        break;
        
      case Orientation.RIGHT_NORTH_CORNER_WALL:
      case Orientation.LEFT_NORTH_CORNER_WALL:
      case Orientation.LOWER_NORTH_CORNER_WALL:
        // 北角墙：画一个菱形标记（在瓷砖中心）
        shapes.setColor(AUTOMAP_WALL_COLOR.r, AUTOMAP_WALL_COLOR.g, AUTOMAP_WALL_COLOR.b, alpha);
        // 绘制小菱形
        float halfSize = line8;
        shapes.line(cx, cy + halfSize, cx - halfSize, cy);
        shapes.line(cx - halfSize, cy, cx, cy - halfSize);
        shapes.line(cx, cy - halfSize, cx + halfSize, cy);
        shapes.line(cx + halfSize, cy, cx, cy + halfSize);
        break;
        
      case Orientation.SOUTH_CORNER_WALL:
      case Orientation.LOWER_SOUTH_CORNER_WALL:
        // 南角墙：在下方画一个菱形
        shapes.setColor(AUTOMAP_WALL_COLOR.r, AUTOMAP_WALL_COLOR.g, AUTOMAP_WALL_COLOR.b, alpha);
        float scY = cy - line16;  // 向下偏移
        float scHalf = line8;
        shapes.line(cx, scY + scHalf, cx - scHalf, scY);
        shapes.line(cx - scHalf, scY, cx, scY - scHalf);
        shapes.line(cx, scY - scHalf, cx + scHalf, scY);
        shapes.line(cx + scHalf, scY, cx, scY + scHalf);
        break;
        
      case Orientation.PILLAR:
        // 柱子：画一个小方块（在中心）
        shapes.setColor(AUTOMAP_WALL_COLOR.r, AUTOMAP_WALL_COLOR.g, AUTOMAP_WALL_COLOR.b, alpha);
        shapes.rect(cx - line4, cy - line4, line8, line8);
        break;
        
      default:
        // 其他类型：画一个小点
        shapes.setColor(AUTOMAP_WALL_COLOR.r, AUTOMAP_WALL_COLOR.g, AUTOMAP_WALL_COLOR.b, alpha * 0.7f);
        shapes.circle(cx, cy, line4);
        break;
    }
  }
  
  /**
   * 使用精灵批处理渲染所有墙壁（备用方法，目前不使用）
   */
  private void drawAutomapWallsWithSprites() {
    int startX2 = startX;
    int startY2 = startY;
    float startPx2 = startPx;
    float startPy2 = startPy;

    for (int y = 0; y < viewBuffer.length; y++) {
      int tx = startX2;
      int ty = startY2;
      float px = startPx2;
      float py = startPy2;
      int size = viewBuffer[y];

      for (int x = 0; x < size; x++) {
        Map.Zone zone = map.getZone(tx * Tile.SUBTILE_SIZE, ty * Tile.SUBTILE_SIZE);
        if (zone != null) {
          // 渲染墙壁精灵
          drawAutomapWallsSpriteForZone(zone, tx, ty, px, py);
        }

        tx++;
        px += Tile.WIDTH50;
        py -= Tile.HEIGHT50;
      }

      startY2++;
      if (y >= tilesX - 1) {
        startX2++;
        startPy2 -= Tile.HEIGHT;
      } else {
        startX2--;
        startPx2 -= Tile.WIDTH;
      }
    }
  }
  
  // 调试：记录找到的墙壁数量和方向
  private int automapTilesScanned = 0;
  private int automapWallsFound = 0;
  private java.util.HashSet<Integer> automapOrientationsFound = new java.util.HashSet<>();
  
  /**
   * 为指定区域渲染墙壁精灵
   */
  private void drawAutomapWallsSpriteForZone(Map.Zone zone, int tx, int ty, float px, float py) {
    try {
      for (int i = Map.WALL_OFFSET; i < Map.WALL_OFFSET + Map.MAX_WALLS; i++) {
        Tile tile = zone.get(i, tx, ty);
        if (tile == null) continue;
        
        automapTilesScanned++;
        int orientation = tile.orientation;
        automapOrientationsFound.add(orientation);
        
        // 跳过地板、屋顶和阴影
        if (Orientation.isFloor(orientation) || Orientation.isRoof(orientation) || orientation == Orientation.SHADOW) {
          continue;
        }
        
        automapWallsFound++;
        
        // 使用精灵渲染
        drawAutomapWallSprite(orientation, px, py);
      }
    } catch (ArrayIndexOutOfBoundsException e) {
      // 忽略边界外的瓷砖
    }
  }
  
  /**
   * 绘制小地图墙壁
   * 遍历当前瓷砖的所有墙壁层，根据墙壁方向绘制对应的小地图图形
   */
  private void drawAutomapWalls(ShapeRenderer shapes, Map.Zone zone, int tx, int ty, float px, float py, float alpha) {
    // 使用 try-catch 防止数组越界异常
    try {
      // 遍历墙壁层 - zone.get() 期望全局瓷砖坐标，内部会转换为本地坐标
      for (int i = Map.WALL_OFFSET; i < Map.WALL_OFFSET + Map.MAX_WALLS; i++) {
        Tile tile = zone.get(i, tx, ty);
        if (tile == null) continue;
        
        int orientation = tile.orientation;
        
        // 跳过地板、屋顶和阴影
        if (Orientation.isFloor(orientation) || Orientation.isRoof(orientation) || orientation == Orientation.SHADOW) {
          continue;
        }
        
        // 根据墙壁方向绘制对应的小地图线条
        // 注意：这个方法已经废弃，使用新的手动坐标转换方法
        // drawAutomapWallLine(shapes, orientation, px, py, alpha);
      }
    } catch (ArrayIndexOutOfBoundsException e) {
      // 忽略边界外的瓷砖
    }
  }
  

  private void drawAutomapEntities(ShapeRenderer shapes, SpriteBatch textBatch, float alpha, boolean isFirstRender, 
                                   float worldToScreenScaleX, float worldToScreenScaleY) {
    IntBag entities = getSubscription().getEntities();
    int[] entityIds = entities.getData();

    // 获取窗口参数（用于坐标转换）
    float screenWidth = iso.viewportWidth;
    float screenHeight = iso.viewportHeight;
    // 与 drawAutomap 中保持一致：
    // - CENTER 模式：全屏
    // - TOP_LEFT / TOP_RIGHT：屏幕 1/2
    float sizeRatio = AUTOMAP_SIZE_RATIO; // 默认 0.5
    if (AUTOMAP_MODE == AUTOMAP_MODE_CENTER) {
      sizeRatio = 1.0f;
    }
    float automapWidth = screenWidth * sizeRatio;
    float automapHeight = screenHeight * sizeRatio;
    float automapScreenX, automapScreenY;
    switch (AUTOMAP_MODE) {
      case AUTOMAP_MODE_TOP_LEFT:
        automapScreenX = 0;
        automapScreenY = screenHeight - automapHeight;
        break;
      case AUTOMAP_MODE_TOP_RIGHT:
        automapScreenX = screenWidth - automapWidth;
        automapScreenY = screenHeight - automapHeight;
        break;
      case AUTOMAP_MODE_CENTER:
      default:
        automapScreenX = (screenWidth - automapWidth) / 2f;
        automapScreenY = (screenHeight - automapHeight) / 2f;
        break;
    }
    
    // 调试日志：检查 drawAutomapEntities 中的尺寸计算（已屏蔽）
    // if (isFirstRender) {
    //   Gdx.app.log(TAG, String.format(
    //       "[Automap Entities Debug] mode=%d, sizeRatio=%.2f, automapSize=(%.1f, %.1f), position=(%.1f, %.1f)",
    //       AUTOMAP_MODE, sizeRatio, automapWidth, automapHeight, automapScreenX, automapScreenY));
    // }

    // 计算显示范围边界（用于裁剪实体）
    // 以玩家为中心 + 用户偏移
    Vector2 playerPos = iso.position;
    float centerX = playerPos.x + automapViewX;
    float centerY = playerPos.y + automapViewY;
    float viewMinX = centerX - automapViewWidth / 2f;
    float viewMaxX = centerX + automapViewWidth / 2f;
    float viewMinY = centerY - automapViewHeight / 2f;
    float viewMaxY = centerY + automapViewHeight / 2f;
    
    // 等距坐标：计算玩家在等距坐标系中的位置（作为中心点）
    float playerIsoX = (centerX - centerY) * AUTOMAP_ISO_X_SCALE;
    float playerIsoY = (centerX + centerY) * AUTOMAP_ISO_Y_SCALE;
    
    // automap窗口中心
    float automapCenterX = automapScreenX + automapWidth / 2f;
    float automapCenterY = automapScreenY + automapHeight / 2f;
    
    // 注意：textBatch 现在在 drawAutomapNpcNames() 中单独处理，这里不再使用

    // 首次渲染时记录实体坐标
    int entityCount = 0;

    for (int i = 0, size = entities.size(); i < size; i++) {
      int entityId = entityIds[i];
      if (!mPosition.has(entityId)) continue;

      // 使用世界坐标
      Vector2 worldPos = mPosition.get(entityId).position;
      
      // 将世界坐标转换为等距坐标，再映射到automap窗口
      float entityIsoX = (worldPos.x - worldPos.y) * AUTOMAP_ISO_X_SCALE;
      float entityIsoY = (worldPos.x + worldPos.y) * AUTOMAP_ISO_Y_SCALE;
      
      // 相对于玩家的等距偏移
      float relIsoX = entityIsoX - playerIsoX;
      float relIsoY = entityIsoY - playerIsoY;
      
      // 映射到automap窗口（以窗口中心为基准，应用缩放）
      float screenX = automapCenterX + relIsoX * worldToScreenScaleX;
      float screenY = automapCenterY - relIsoY * worldToScreenScaleY; // Y轴翻转（标准45°等距）
      
      // 检查实体是否在显示范围内
      if (worldPos.x < viewMinX || worldPos.x > viewMaxX ||
          worldPos.y < viewMinY || worldPos.y > viewMaxY) {
        if (isFirstRender) {
          // 首次渲染时，也记录超出范围的实体（前5个）
          if (entityCount < 5) {
            // Gdx.app.debug(TAG, String.format(
            //     "Entity #%d (out of view): world(%.1f, %.1f) -> screen(%.1f, %.1f) [view range: X[%.1f-%.1f], Y[%.1f-%.1f]]",
            //     entityId, worldPos.x, worldPos.y, screenX, screenY,
            //     viewMinX, viewMaxX, viewMinY, viewMaxY));
            entityCount++;
          }
        }
        continue;
      }

      // 首次渲染时记录显示范围内的实体坐标
      if (isFirstRender && entityCount < 10) {
        String entityType = "Unknown";
        if (mClass.has(entityId)) {
          Class.Type type = mClass.get(entityId).type;
          entityType = type.toString();
        }
        // Gdx.app.debug(TAG, String.format(
        //     "Entity #%d (%s): world(%.1f, %.1f) -> screen(%.1f, %.1f) [window: X[%.1f-%.1f], Y[%.1f-%.1f]]",
        //     entityId, entityType, worldPos.x, worldPos.y, screenX, screenY,
        //     automapScreenX, automapScreenX + automapWidth,
        //     automapScreenY, automapScreenY + automapHeight));
        entityCount++;
      }

      // Determine entity type and draw (使用屏幕坐标)
      // 保存当前投影矩阵，切换到automap本地坐标系统
      Matrix4 originalMatrix = shapes.getProjectionMatrix();
      Matrix4 automapLocalMatrix = new Matrix4();
      automapLocalMatrix.setToOrtho2D(0, 0, automapWidth, automapHeight);
      shapes.setProjectionMatrix(automapLocalMatrix);
      // 注意：textBatch 现在在 drawAutomapNpcNames() 中单独处理
      
      // 转换为automap本地坐标（相对于窗口左上角）
      float localX = screenX - automapScreenX;
      float localY = screenY - automapScreenY;

      if (mClass.has(entityId)) {
        Class.Type type = mClass.get(entityId).type;
        // 根据 automap 显示范围动态缩放图标大小：
        // - 缩放放大（viewWidth 变小）时，图标变大
        // - 缩放缩小（viewWidth 变大）时，图标变小
        float referenceWidth = 2000f; // 参考世界宽度（可根据感觉微调）
        float zoomFactor = referenceWidth / Math.max(automapViewWidth, 1f);
        // 基础尺寸为 3 像素，根据 zoomFactor 缩放，并限制在 [2, 7] 像素范围内
        float markerSize = 3f * zoomFactor;
        markerSize = Math.max(markerSize, 2f);
        
        switch (type) {
          case PLR:
            // Player: draw directional arrow (orange)
            shapes.setColor(AUTOMAP_PLAYER_COLOR.r, AUTOMAP_PLAYER_COLOR.g, AUTOMAP_PLAYER_COLOR.b, alpha);
            drawAutomapPlayerArrow(shapes, localX, localY, entityId, worldToScreenScaleX, worldToScreenScaleY, 
                automapWidth, automapHeight, screenWidth, screenHeight);
            break;
            
          case MON:
            // Determine if monster is hostile using MonStats.Align field
            // Align: 0 = enemy (hostile), 1 = friendly/neutral, 2 = neutral
            boolean isHostile = true; // default to hostile
            boolean isNpc = false;
            int align = 0;
            String monClassName = mClassname.has(entityId) ? mClassname.get(entityId).classname : "";
            
            if (mMonster.has(entityId)) {
              com.riiablo.engine.server.component.Monster mon = mMonster.get(entityId);
              if (mon.monstats != null) {
                align = mon.monstats.Align;
                isNpc = mon.monstats.npc;
                // Align 0 = enemy, 1+ = friendly/neutral
                isHostile = (align == 0) && !isNpc;
              }
            }
            
            // Also check Interactable component as a fallback for NPCs
            if (mInteractable.has(entityId)) {
              isNpc = true;
              isHostile = false;
            }
            
            // Filter out decorative/critter animals by name (they have align=0 but are not threats)
            // These are passive creatures in town that don't attack
            if (isHostile && isDecorativeCreature(monClassName)) {
              isHostile = false;
            }
            
            // 只有可以交互的 NPC 才绘制图标和文字
            if (isNpc && mInteractable.has(entityId)) {
              // NPC: draw hollow box + cross marker, gold color
              shapes.setColor(AUTOMAP_NPC_COLOR.r, AUTOMAP_NPC_COLOR.g, AUTOMAP_NPC_COLOR.b, alpha);
              drawAutomapCrossMarker(shapes, localX, localY, markerSize * 0.7f, 
                  automapWidth, automapHeight, screenWidth, screenHeight,
                  worldToScreenScaleX, worldToScreenScaleY);
              
              // NPC 名字将在 drawAutomapNpcNames() 中单独绘制，确保文本在最上层
              
              if (isFirstRender) {
                String npcName = mClassname.has(entityId) ? mClassname.get(entityId).classname : "Unknown";
                // Gdx.app.debug(TAG, String.format(
                //     "[NPC] id=%d name='%s' align=%d pos=(%.1f,%.1f)",
                //     entityId, npcName, align, worldPos.x, worldPos.y));
              }
            } else if (isHostile) {
                // Check if monster is dead (has Corpse component or is in MODE_DD)
                boolean isDead = mCorpse.has(entityId);
                if (!isDead && mCofReference.has(entityId)) {
                  // Also check if mode is MODE_DD (corpse mode)
                  isDead = (mCofReference.get(entityId).mode == Engine.Monster.MODE_DD);
                }
                
                // Hostile monster: draw hollow box + cross marker
                // Use gray color if dead, red color if alive
                if (isDead) {
                  shapes.setColor(AUTOMAP_DEAD_MONSTER_COLOR.r, AUTOMAP_DEAD_MONSTER_COLOR.g, AUTOMAP_DEAD_MONSTER_COLOR.b, alpha);
                } else {
                  shapes.setColor(AUTOMAP_MONSTER_COLOR.r, AUTOMAP_MONSTER_COLOR.g, AUTOMAP_MONSTER_COLOR.b, alpha);
                }
                drawAutomapCrossMarker(shapes, localX, localY, markerSize * 0.7f,
                    automapWidth, automapHeight, screenWidth, screenHeight,
                    worldToScreenScaleX, worldToScreenScaleY);
              if (isFirstRender) {
                String monName = mClassname.has(entityId) ? mClassname.get(entityId).classname : "Unknown";
                // Gdx.app.debug(TAG, String.format(
                //     "[MON+] id=%d name='%s' align=%d pos=(%.1f,%.1f) HOSTILE",
                //     entityId, monName, align, worldPos.x, worldPos.y));
              }
            } else {
              // Friendly/neutral creature (cow, chicken, rogue, etc): skip drawing
              if (isFirstRender) {
                String monName = mClassname.has(entityId) ? mClassname.get(entityId).classname : "Unknown";
                // Gdx.app.debug(TAG, String.format(
                //     "[MON-] id=%d name='%s' align=%d pos=(%.1f,%.1f) FRIENDLY/NEUTRAL (skipped)",
                //     entityId, monName, align, worldPos.x, worldPos.y));
              }
            }
            break;
            
          case WRP:
            // Warp/Portal: draw hollow circle marker, light blue color
            shapes.setColor(AUTOMAP_WARP_COLOR.r, AUTOMAP_WARP_COLOR.g, AUTOMAP_WARP_COLOR.b, alpha);
            drawAutomapPortalMarker(shapes, localX, localY, markerSize * 0.7f);
            break;
            
          case MIS:
            // Missile: draw short directional line
            drawAutomapMissileMarker(shapes, localX, localY, markerSize, entityId, alpha);
            break;
            
          case OBJ:
            // Object: skip for now (reserved for sprite loading)
            break;
            
          case ITM:
            // Item: skip for now (reserved for sprite loading)
            break;
            
          default:
            // Other types: skip for now
            break;
        }
      }
      
      // 恢复原始投影矩阵
      shapes.setProjectionMatrix(originalMatrix);
    }
    
    if (isFirstRender) {
      // Gdx.app.debug(TAG, "=== Total entities checked: " + entities.size() + ", entities logged: " + entityCount + " ===");
    }
  }
  
  /**
   * 单独绘制 NPC 名字（在所有绘制完成后调用，确保文本在最上层）
   */
  private void drawAutomapNpcNames(SpriteBatch textBatch, float alpha, boolean isFirstRender,
                                   float worldToScreenScaleX, float worldToScreenScaleY) {
    IntBag entities = getSubscription().getEntities();
    int[] entityIds = entities.getData();
    
    // 获取窗口参数（用于坐标转换）
    float screenWidth = iso.viewportWidth;
    float screenHeight = iso.viewportHeight;
    float sizeRatio = AUTOMAP_SIZE_RATIO;
    if (AUTOMAP_MODE == AUTOMAP_MODE_CENTER) {
      sizeRatio = 1.0f;
    }
    float automapWidth = screenWidth * sizeRatio;
    float automapHeight = screenHeight * sizeRatio;
    float automapScreenX, automapScreenY;
    switch (AUTOMAP_MODE) {
      case AUTOMAP_MODE_TOP_LEFT:
        automapScreenX = 0;
        automapScreenY = screenHeight - automapHeight;
        break;
      case AUTOMAP_MODE_TOP_RIGHT:
        automapScreenX = screenWidth - automapWidth;
        automapScreenY = screenHeight - automapHeight;
        break;
      case AUTOMAP_MODE_CENTER:
      default:
        automapScreenX = (screenWidth - automapWidth) / 2f;
        automapScreenY = (screenHeight - automapHeight) / 2f;
        break;
    }
    
    // 计算显示范围边界
    Vector2 playerPos = iso.position;
    float centerX = playerPos.x + automapViewX;
    float centerY = playerPos.y + automapViewY;
    float viewMinX = centerX - automapViewWidth / 2f;
    float viewMaxX = centerX + automapViewWidth / 2f;
    float viewMinY = centerY - automapViewHeight / 2f;
    float viewMaxY = centerY + automapViewHeight / 2f;
    
    // 等距坐标：计算玩家在等距坐标系中的位置（作为中心点）
    float playerIsoX = (centerX - centerY) * AUTOMAP_ISO_X_SCALE;
    float playerIsoY = (centerX + centerY) * AUTOMAP_ISO_Y_SCALE;
    
    // automap窗口中心
    float automapCenterX = automapScreenX + automapWidth / 2f;
    float automapCenterY = automapScreenY + automapHeight / 2f;
    
    // 使用更大的字体，白色
    BitmapFont font = Riiablo.fonts.consolas16;
    // 放大字体（1.2倍）
    font.getData().setScale(1.2f);
    float textAlpha = Math.max(alpha, 0.8f);
    font.setColor(1.0f, 1.0f, 1.0f, textAlpha);  // 白色
    
    // 计算 markerSize（与 drawAutomapEntities 中的计算保持一致）
    float referenceWidth = 2000f;
    float zoomFactor = referenceWidth / Math.max(automapViewWidth, 1f);
    float markerSize = 3f * zoomFactor;
    markerSize = Math.max(markerSize, 2f);
    
    for (int i = 0, size = entities.size(); i < size; i++) {
      int entityId = entityIds[i];
      if (!mPosition.has(entityId)) continue;
      if (!mClass.has(entityId)) continue;
      
      Class.Type type = mClass.get(entityId).type;
      if (type != Class.Type.MON) continue;
      
      // 检查是否是 NPC，并且可以交互
      // 只有可以交互的 NPC 才绘制图标和文字
      if (!mInteractable.has(entityId)) continue;
      
      // 验证确实是 NPC（通过 Monster 组件或 Interactable 组件）
      boolean isNpc = false;
      if (mMonster.has(entityId)) {
        com.riiablo.engine.server.component.Monster mon = mMonster.get(entityId);
        if (mon.monstats != null) {
          isNpc = mon.monstats.npc;
        }
      }
      // 如果有 Interactable 组件，也认为是 NPC
      if (mInteractable.has(entityId)) {
        isNpc = true;
      }
      
      if (!isNpc) continue;
      
      // 使用世界坐标
      Vector2 worldPos = mPosition.get(entityId).position;
      
      // 检查实体是否在显示范围内
      if (worldPos.x < viewMinX || worldPos.x > viewMaxX ||
          worldPos.y < viewMinY || worldPos.y > viewMaxY) {
        continue;
      }
      
      // 将世界坐标转换为等距坐标，再映射到automap窗口
      float entityIsoX = (worldPos.x - worldPos.y) * AUTOMAP_ISO_X_SCALE;
      float entityIsoY = (worldPos.x + worldPos.y) * AUTOMAP_ISO_Y_SCALE;
      
      // 相对于玩家的等距偏移
      float relIsoX = entityIsoX - playerIsoX;
      float relIsoY = entityIsoY - playerIsoY;
      
      // 映射到automap窗口（以窗口中心为基准，应用缩放）
      float screenX = automapCenterX + relIsoX * worldToScreenScaleX;
      float screenY = automapCenterY - relIsoY * worldToScreenScaleY;
      
      // 转换为automap本地坐标（相对于窗口左上角）
      float localX = screenX - automapScreenX;
      float localY = screenY - automapScreenY;
      
      // 绘制 NPC 名字（在图标上方）
      String npcName = mClassname.has(entityId) ? mClassname.get(entityId).classname : "Unknown";
      if (npcName != null && !npcName.isEmpty() && !npcName.equals("Unknown")) {
        // 计算文本位置：图标上方，水平居中
        // 重要：图标绘制时使用的 localX, localY 是基于窗口左上角的坐标（Y向下为正）
        // 但是 shapes 的投影矩阵是 setToOrtho2D(0, 0, automapWidth, automapHeight)，原点在左下角（Y向上为正）
        // 如果图标之前的位置是对的，说明 shapes 内部可能做了坐标转换，或者 localY 已经是正确的 LibGDX 坐标
        // 为了确保文字和图标使用相同的坐标系统，我们需要使用与图标相同的 localY
        // 但是 textBatch 的投影矩阵也是 setToOrtho2D(0, 0, automapWidth, automapHeight)，原点在左下角
        // 所以我们需要将 localY（基于窗口左上角）转换为 LibGDX 坐标（基于窗口左下角）
        // 图标中心在窗口坐标中是 localY（从顶部算起），在 LibGDX 坐标中是 automapHeight - localY（从底部算起）
        // 图标大小是 markerSize * 0.7f（直径），所以半径是 markerSize * 0.35f
        // 图标顶部在 LibGDX 坐标中是：(automapHeight - localY) + markerSize * 0.35f
        // 文本基线应该在图标顶部上方，所以：textY = (automapHeight - localY) + markerSize * 0.35f + 8f
        
        // 但是，如果图标之前的位置是对的，可能 localY 已经是 LibGDX 坐标了
        // 让我尝试另一种方法：直接使用 localY，但向上偏移（在 LibGDX 坐标中，向上是增加）
        // 如果图标中心在 localY（LibGDX 坐标），图标顶部在 localY + markerSize * 0.35f
        // 文本基线在 localY + markerSize * 0.35f + 8f
        
        // 先尝试：假设 localY 已经是 LibGDX 坐标（从底部算起，Y向上为正）
        float iconTopY = localY + markerSize * 0.35f;  // 图标顶部
        float textY = iconTopY + 22f;  // 文本基线在图标顶部上方 22 像素（向上调整）
        
        // 使用 font.draw() 直接绘制，Align.center 自动处理水平居中
        GlyphLayout layout = font.draw(textBatch, npcName, localX, textY, 0, Align.center, false);
        Pools.free(layout);
      }
    }
  }
  
  /**
   * 绘制玩家方向箭头
   * 参考 Devilution 的 DrawAutomapPlr 函数
   * 根据玩家朝向绘制8个方向的箭头
   * 
   * @param shapes ShapeRenderer 实例
   * @param x 玩家X坐标（世界坐标）
   * @param y 玩家Y坐标（世界坐标）
   * @param entityId 实体ID
   */
  // Direction stabilization to prevent jittering
  private static int stableDirection = Direction.DOWN;      // Current stable direction for rendering
  private static int pendingDirection = Direction.DOWN;     // New direction waiting to be confirmed
  private static long directionChangeTime = 0;              // Time when pending direction was first detected
  private static final long DIRECTION_STABLE_THRESHOLD = 100; // Threshold in ms before direction change is applied
  
  private void drawAutomapPlayerArrow(ShapeRenderer shapes, float x, float y, int entityId, 
                                      float scaleX, float scaleY, float automapWidth, float automapHeight,
                                      float screenWidth, float screenHeight) {
    // 保存当前的ShapeType
    ShapeRenderer.ShapeType previousType = shapes.getCurrentType();
    
    // 切换到Line模式（箭头需要Line模式）
    shapes.set(ShapeRenderer.ShapeType.Line);
    
    // 使用与墙线宽相同的计算逻辑（与十字标记一致）
    float avgScale = (scaleX + scaleY) / 2f;
    float wallLineWidth = Math.min(5.0f, 2f * avgScale);
    float arrowLineWidth = wallLineWidth;
    
    // 设置箭头线宽
    Gdx.gl.glLineWidth(arrowLineWidth);
    
    // Get player facing direction
    int rawDirection = Direction.DOWN; // Default direction
    float radians = 0;
    if (mAngle.has(entityId)) {
      Angle angle = mAngle.get(entityId);
      radians = MathUtils.atan2(angle.angle.y, angle.angle.x);
      rawDirection = Direction.radiansToDirection(radians, 8);
    }
    
    // Direction stabilization: only change direction if it persists for DIRECTION_STABLE_THRESHOLD ms
    long currentTime = System.currentTimeMillis();
    if (rawDirection != stableDirection) {
      if (rawDirection != pendingDirection) {
        // New direction detected, start timing
        pendingDirection = rawDirection;
        directionChangeTime = currentTime;
      } else if (currentTime - directionChangeTime >= DIRECTION_STABLE_THRESHOLD) {
        // Pending direction has been stable long enough, apply it
        // Gdx.app.debug(TAG, String.format(
        //     "Player arrow direction changed: %s -> %s (angle=%.1f deg)",
        //     getDirectionName(stableDirection), getDirectionName(rawDirection), 
        //     radians * MathUtils.radiansToDegrees));
        stableDirection = rawDirection;
      }
    } else {
      // Direction matches stable, reset pending
      pendingDirection = stableDirection;
    }
    
    // Use the stable direction for rendering
    int direction = stableDirection;
    
    // 根据方向绘制箭头 (参考 Devilution)
    // 使用 amLine 系统，但需要根据缩放比例转换
    // 箭头大小应该受屏幕大小和显示模式影响：
    // - 在 CENTER 模式下，大小是其他模式的 2 倍
    // - 根据屏幕大小调整（全屏时大些，窗口时小些）
    float sizeMultiplier = 1.0f;
    if (AUTOMAP_MODE == AUTOMAP_MODE_CENTER) {
      sizeMultiplier = 2.0f; // CENTER 模式：2 倍大小
    }
    // 根据屏幕大小调整（相对于参考屏幕大小 854x480）
    float screenSizeFactor = Math.min(screenWidth / 854.0f, screenHeight / 480.0f);
    sizeMultiplier *= screenSizeFactor;
    // 整体缩小到 1/6
    sizeMultiplier *= 1.0f / 6.0f;
    
    float line16 = amLine16 * scaleY / 2 * sizeMultiplier;
    float line8 = amLine8 * scaleY / 2 * sizeMultiplier;
    float line4 = amLine4 * scaleY / 2 * sizeMultiplier;
    
    // D2等距视角方向映射（用户确认）：
    //   SOUTH (0) -> 屏幕 左下 (45°)
    //   WEST  (1) -> 屏幕 左上 (45°)
    //   NORTH (2) -> 屏幕 右上 (45°)
    //   EAST  (3) -> 屏幕 右下 (45°)
    //   DOWN  (4) -> 屏幕 下 (垂直，介于SOUTH和EAST之间)
    //   LEFT  (5) -> 屏幕 左 (水平，介于SOUTH和WEST之间)
    //   UP    (6) -> 屏幕 上 (垂直，介于WEST和NORTH之间)
    //   RIGHT (7) -> 屏幕 右 (水平，介于NORTH和EAST之间)
    
    // 使用45度角的等距线长度
    float diagLen = line16 * 0.707f;  // cos(45°) ≈ 0.707
    float diagHalf = line8 * 0.707f;
    
    switch (direction) {
      case Direction.SOUTH: // 屏幕 左下 -> automap左下（45度）
        shapes.line(x, y, x - diagLen, y - diagLen);
        shapes.line(x - diagLen, y - diagLen, x - diagHalf, y - diagLen + line4);
        shapes.line(x - diagLen, y - diagLen, x - diagLen + line4, y - diagHalf);
        break;
      case Direction.WEST: // 屏幕 左上 -> automap左上（45度）
        shapes.line(x, y, x - diagLen, y + diagLen);
        shapes.line(x - diagLen, y + diagLen, x - diagLen + line4, y + diagHalf);
        shapes.line(x - diagLen, y + diagLen, x - diagHalf, y + diagLen - line4);
        break;
      case Direction.NORTH: // 屏幕 右上 -> automap右上（45度）
        shapes.line(x, y, x + diagLen, y + diagLen);
        shapes.line(x + diagLen, y + diagLen, x + diagHalf, y + diagLen - line4);
        shapes.line(x + diagLen, y + diagLen, x + diagLen - line4, y + diagHalf);
        break;
      case Direction.EAST: // 屏幕 右下 -> automap右下（45度）
        shapes.line(x, y, x + diagLen, y - diagLen);
        shapes.line(x + diagLen, y - diagLen, x + diagLen - line4, y - diagHalf);
        shapes.line(x + diagLen, y - diagLen, x + diagHalf, y - diagLen + line4);
        break;
      case Direction.DOWN: // 屏幕 下 -> automap向下（垂直）
        shapes.line(x, y, x, y - line16);
        shapes.line(x, y - line16, x - line4, y - line8);
        shapes.line(x, y - line16, x + line4, y - line8);
        break;
      case Direction.LEFT: // 屏幕 左 -> automap向左（水平）
        shapes.line(x, y, x - line16, y);
        shapes.line(x - line16, y, x - line8, y + line4);
        shapes.line(x - line16, y, x - line8, y - line4);
        break;
      case Direction.UP: // 屏幕 上 -> automap向上（垂直）
        shapes.line(x, y, x, y + line16);
        shapes.line(x, y + line16, x - line4, y + line8);
        shapes.line(x, y + line16, x + line4, y + line8);
        break;
      case Direction.RIGHT: // 屏幕 右 -> automap向右（水平）
        shapes.line(x, y, x + line16, y);
        shapes.line(x + line16, y, x + line8, y + line4);
        shapes.line(x + line16, y, x + line8, y - line4);
        break;
      default:
        // 默认画一个圆点（也需要应用大小调整）
        float defaultSize = amLine8 * sizeMultiplier;
        shapes.circle(x, y, defaultSize);
        break;
    }
  }
  
  /**
   * Check if monster is a decorative/critter creature that should not be shown as hostile
   * These are passive creatures in town that don't attack players despite having align=0
   */
  private boolean isDecorativeCreature(String className) {
    if (className == null || className.isEmpty()) return false;
    String lowerName = className.toLowerCase();
    // List of decorative/critter animals that should not show as hostile
    return lowerName.contains("chicken") ||
           lowerName.contains("frog") ||
           lowerName.contains("rat") && !lowerName.contains("quill") || // rat but not quillrat
           lowerName.contains("bird") ||
           lowerName.contains("cat") ||
           lowerName.contains("dog") ||
           lowerName.contains("fish") ||
           lowerName.equals("maggot") || // not maggotlar etc.
           lowerName.contains("critter");
  }

  /**
   * 获取方向的名称（用于调试）
   */
  private String getDirectionName(int direction) {
    switch (direction) {
      case Direction.SOUTH: return "SOUTH";
      case Direction.WEST: return "WEST";
      case Direction.NORTH: return "NORTH";
      case Direction.EAST: return "EAST";
      case Direction.DOWN: return "DOWN(SW)";
      case Direction.LEFT: return "LEFT(NW)";
      case Direction.UP: return "UP(NE)";
      case Direction.RIGHT: return "RIGHT(SE)";
      default: return "UNKNOWN(" + direction + ")";
    }
  }
  
  /**
   * 绘制空心的“希腊十字”标记（等臂十字），仅描边，不填充
   * 颜色在外部通过 shapes.setColor 设定
   *
   * 形状示意（+ 号各臂宽度相同）：
   *
   *     ┌───┐
   *     │   │
   * ┌───┼───┼───┐
   * │   │   │   │
   * └───┼───┼───┘
   *     │   │
   *     └───┘
   */
  private void drawAutomapCrossMarker(ShapeRenderer shapes, float x, float y, float size,
                                      float automapWidth, float automapHeight, 
                                      float screenWidth, float screenHeight,
                                      float worldToScreenScaleX, float worldToScreenScaleY) {
    // r   : 十字每条臂从中心到外侧的长度（像素）
    // t   : 臂的“半厚度”，决定中间竖条 / 横条的宽度
    // 应用与玩家箭头相同的大小调整逻辑
    float sizeMultiplier = 1.0f;
    if (AUTOMAP_MODE == AUTOMAP_MODE_CENTER) {
      sizeMultiplier = 2.0f; // CENTER 模式：2 倍大小
    }
    // 根据屏幕大小调整（相对于参考屏幕大小 854x480）
    float screenSizeFactor = Math.min(screenWidth / 854.0f, screenHeight / 480.0f);
    sizeMultiplier *= screenSizeFactor;
    // 整体缩小到 1/6
    sizeMultiplier *= 1.0f / 6.0f;
    
    // 应用大小调整
    size = size * sizeMultiplier;
    
    // r: 十字每条臂从中心到外侧的长度（像素）
    float r = size;
    
    // 地图网格线方向：30°和150°
    // 30°方向：cos(30°) = √3/2 ≈ 0.866, sin(30°) = 1/2 = 0.5
    // 150°方向：cos(150°) = -√3/2 ≈ -0.866, sin(150°) = 1/2 = 0.5
    float cos30 = 0.8660254037844386f; // cos(30°)
    float sin30 = 0.5f;                // sin(30°)
    float cos150 = -0.8660254037844386f; // cos(150°)
    float sin150 = 0.5f;                 // sin(150°)
    
    // 保存当前的ShapeType
    ShapeRenderer.ShapeType previousType = shapes.getCurrentType();
    
    // 切换到Line模式（十字标记需要Line模式）
    shapes.set(ShapeRenderer.ShapeType.Line);
    
    // 使用与墙线宽相同的计算逻辑
    float avgScale = (worldToScreenScaleX + worldToScreenScaleY) / 2f;
    float wallLineWidth = Math.min(5.0f, 2f * avgScale);
    // 十字线宽等于墙线宽（用户要求调宽一倍）
    float crossLineWidth = wallLineWidth;
    
    // 设置十字线宽
    Gdx.gl.glLineWidth(crossLineWidth);
    
    // 绘制两条实线交叉组成十字
    // 第一条线：沿着30°方向，从西南到东北
    // 第二条线：沿着150°方向，从西北到东南
    
    // 第一条线（30°方向）：从西南到东北
    float line1StartX = x - r * cos30;
    float line1StartY = y - r * sin30;
    float line1EndX = x + r * cos30;
    float line1EndY = y + r * sin30;
    
    // 第二条线（150°方向）：从西北到东南
    float line2StartX = x - r * cos150;
    float line2StartY = y - r * sin150;
    float line2EndX = x + r * cos150;
    float line2EndY = y + r * sin150;
    
    // 绘制两条线
    shapes.line(line1StartX, line1StartY, line1EndX, line1EndY);
    shapes.line(line2StartX, line2StartY, line2EndX, line2EndY);
    
    // 恢复之前的ShapeType
    shapes.set(previousType);
  }

  /**
   * 绘制传送门 / 传送点 的空心圆标记
   * 颜色在外部通过 shapes.setColor 设定
   */
  private void drawAutomapPortalMarker(ShapeRenderer shapes, float x, float y, float size) {
    // 使用 ShapeRenderer 的 circle，在 Line 模式下只绘制轮廓
    float radius = size;
    // 细分段数可以稍微大一点让圆更平滑
    shapes.circle(x, y, radius, 24);
  }

  /**
   * Draw a "<N>" marker for NPCs
   */
  private void drawAutomapNpcMarker(ShapeRenderer shapes, float x, float y, float size, 
                                     int entityId, Matrix4 projMatrix) {
    float bracketWidth = size * 0.6f;
    float bracketHeight = size * 1.2f;
    
    // Left bracket "<"
    shapes.line(x - size - bracketWidth, y, x - size, y + bracketHeight / 2);
    shapes.line(x - size - bracketWidth, y, x - size, y - bracketHeight / 2);
    
    // Letter "N"
    float nWidth = size * 0.8f;
    float nHeight = bracketHeight;
    float nStartX = x - nWidth / 2;
    shapes.line(nStartX, y - nHeight / 2, nStartX, y + nHeight / 2);
    shapes.line(nStartX, y + nHeight / 2, nStartX + nWidth, y - nHeight / 2);
    shapes.line(nStartX + nWidth, y - nHeight / 2, nStartX + nWidth, y + nHeight / 2);
    
    // Right bracket ">"
    shapes.line(x + size + bracketWidth, y, x + size, y + bracketHeight / 2);
    shapes.line(x + size + bracketWidth, y, x + size, y - bracketHeight / 2);
  }

  /**
   * Draw a "<P>" marker for warps/portals
   */
  private void drawAutomapWarpMarker(ShapeRenderer shapes, float x, float y, float size) {
    float bracketWidth = size * 0.6f;
    float bracketHeight = size * 1.2f;
    
    // Left bracket "<"
    shapes.line(x - size - bracketWidth, y, x - size, y + bracketHeight / 2);
    shapes.line(x - size - bracketWidth, y, x - size, y - bracketHeight / 2);
    
    // Letter "P"
    float pWidth = size * 0.6f;
    float pHeight = bracketHeight;
    float pStartX = x - pWidth / 2;
    shapes.line(pStartX, y - pHeight / 2, pStartX, y + pHeight / 2);
    shapes.line(pStartX, y + pHeight / 2, pStartX + pWidth, y + pHeight / 2);
    shapes.line(pStartX + pWidth, y + pHeight / 2, pStartX + pWidth, y);
    shapes.line(pStartX + pWidth, y, pStartX, y);
    
    // Right bracket ">"
    shapes.line(x + size + bracketWidth, y, x + size, y + bracketHeight / 2);
    shapes.line(x + size + bracketWidth, y, x + size, y - bracketHeight / 2);
  }

  /**
   * Draw a directional line for missiles (default to red/monster color)
   */
  private void drawAutomapMissileMarker(ShapeRenderer shapes, float x, float y, float size,
                                         int entityId, float alpha) {
    Color missileColor = AUTOMAP_MONSTER_MISSILE_COLOR;
    
    int direction = Direction.SOUTH;
    if (mAngle.has(entityId)) {
      Angle angle = mAngle.get(entityId);
      float radians = MathUtils.atan2(angle.angle.y, angle.angle.x);
      direction = Direction.radiansToDirection(radians, 8);
    }
    
    shapes.setColor(missileColor.r, missileColor.g, missileColor.b, alpha);
    
    float lineLen = size * 1.5f;
    float diagLen = lineLen * 0.707f;
    
    switch (direction) {
      case Direction.SOUTH: shapes.line(x, y, x - diagLen, y - diagLen); break;
      case Direction.WEST: shapes.line(x, y, x - diagLen, y + diagLen); break;
      case Direction.NORTH: shapes.line(x, y, x + diagLen, y + diagLen); break;
      case Direction.EAST: shapes.line(x, y, x + diagLen, y - diagLen); break;
      case Direction.DOWN: shapes.line(x, y, x, y - lineLen); break;
      case Direction.LEFT: shapes.line(x, y, x - lineLen, y); break;
      case Direction.UP: shapes.line(x, y, x, y + lineLen); break;
      case Direction.RIGHT: shapes.line(x, y, x + lineLen, y); break;
      default: shapes.line(x - size, y, x + size, y); break;
    }
  }

  /**
   * 切换automap显示开关（Tab键）
   * 只在 OFF 和默认位置（CENTER）之间切换
   * 切换位置的开关会单独提供
   */
  public static void toggleAutomap() {
    if (AUTOMAP_MODE == AUTOMAP_MODE_OFF) {
      // 打开automap，使用默认位置（屏幕中间）
      AUTOMAP_MODE = AUTOMAP_MODE_CENTER;
      // 重置偏移量，让地图回到玩家当前位置对应的屏幕范围
      automapViewX = 0;
      automapViewY = 0;
      // 重置显示范围，会在下次渲染时重新初始化为默认值
      automapViewWidth = 0;
      automapViewHeight = 0;
      // 重置默认值，会在下次渲染时重新计算
      automapDefaultViewWidth = 0;
      automapDefaultViewHeight = 0;
      // Gdx.app.log(TAG, ">>> Automap ENABLED (mode=" + AUTOMAP_MODE + ", reset offsets)");
    } else {
      // 关闭automap，重置显示范围
      AUTOMAP_MODE = AUTOMAP_MODE_OFF;
      automapViewWidth = 0;
      automapViewHeight = 0;
      // Gdx.app.log(TAG, ">>> Automap DISABLED");
    }
  }
  
  /**
   * 切换automap显示位置
   * 循环: CENTER -> TOP_LEFT -> TOP_RIGHT -> CENTER
   * 注意：这个方法会在后面单独提供绑定到某个按键
   */
  public static void toggleAutomapPosition() {
    if (AUTOMAP_MODE == AUTOMAP_MODE_OFF) {
      // 如果automap关闭，先打开到默认位置
      AUTOMAP_MODE = AUTOMAP_MODE_CENTER;
      return;
    }
    
    // 切换前记录当前窗口比例
    float oldSizeRatio = (AUTOMAP_MODE == AUTOMAP_MODE_CENTER) ? 1.0f : AUTOMAP_SIZE_RATIO;
    
    // 循环切换位置模式
    switch (AUTOMAP_MODE) {
      case AUTOMAP_MODE_CENTER:
        AUTOMAP_MODE = AUTOMAP_MODE_TOP_LEFT;
        break;
      case AUTOMAP_MODE_TOP_LEFT:
        AUTOMAP_MODE = AUTOMAP_MODE_TOP_RIGHT;
        break;
      case AUTOMAP_MODE_TOP_RIGHT:
        AUTOMAP_MODE = AUTOMAP_MODE_CENTER;
        break;
      default:
        AUTOMAP_MODE = AUTOMAP_MODE_CENTER;
        break;
    }
    
    // 切换后新的窗口比例
    float newSizeRatio = (AUTOMAP_MODE == AUTOMAP_MODE_CENTER) ? 1.0f : AUTOMAP_SIZE_RATIO;
    
    // 为了避免在不同位置模式下元素看起来变大/变小，
    // 根据窗口比例调整显示范围宽高，保持 world->screen 缩放大致一致
    if (automapViewWidth > 0 && automapViewHeight > 0 && oldSizeRatio > 0 && newSizeRatio > 0) {
      float scale = oldSizeRatio / newSizeRatio;
      automapViewWidth *= scale;
      automapViewHeight *= scale;
    }
  }
  
  /**
   * 缩小显示范围 (放大地图，显示更详细)
   * 减少 automapViewWidth/Height
   */
  public static void automapZoomIn() {
    if (AUTOMAP_MODE == AUTOMAP_MODE_OFF) return;
    if (automapViewWidth == 0 || automapViewHeight == 0) return;
    if (automapDefaultViewWidth == 0 || automapDefaultViewHeight == 0) return;
    
    // 将显示范围缩小10%（显示更详细）
    float newWidth = automapViewWidth * 0.9f;
    float newHeight = automapViewHeight * 0.9f;
    
    // 限制最小显示范围：默认值的 1/2
    float minWidth = automapDefaultViewWidth * 0.5f;
    float minHeight = automapDefaultViewHeight * 0.5f;
    if (newWidth < minWidth) newWidth = minWidth;
    if (newHeight < minHeight) newHeight = minHeight;
    
    automapViewWidth = newWidth;
    automapViewHeight = newHeight;
    
    // Gdx.app.log(TAG, "Automap zoom in: width=" + automapViewWidth + ", height=" + automapViewHeight + 
    //     " (min=" + minWidth + "," + minHeight + ")");
  }
  
  /**
   * 扩大显示范围 (缩小地图，显示更多)
   * 增加 automapViewWidth/Height
   */
  public static void automapZoomOut() {
    if (AUTOMAP_MODE == AUTOMAP_MODE_OFF) return;
    if (automapViewWidth == 0 || automapViewHeight == 0) return;
    if (automapDefaultViewWidth == 0 || automapDefaultViewHeight == 0) return;
    
    // 将显示范围扩大10%（显示更多内容）
    float newWidth = automapViewWidth * 1.1f;
    float newHeight = automapViewHeight * 1.1f;
    
    // 限制最大显示范围：默认值的 2 倍
    float maxWidth = automapDefaultViewWidth * 2.0f;
    float maxHeight = automapDefaultViewHeight * 2.0f;
    if (newWidth > maxWidth) newWidth = maxWidth;
    if (newHeight > maxHeight) newHeight = maxHeight;
    
    automapViewWidth = newWidth;
    automapViewHeight = newHeight;
    
    // Gdx.app.log(TAG, "Automap zoom out: width=" + automapViewWidth + ", height=" + automapViewHeight + 
    //     " (max=" + maxWidth + "," + maxHeight + ")");
  }
  
  /**
   * 向上平移 (增加 Y 偏移)
   * automapViewY 作为相对于玩家的偏移
   */
  public static void automapUp() {
    if (AUTOMAP_MODE == AUTOMAP_MODE_OFF) return;
    
    float step = 3.0f;
    automapViewY += step;
    
    // Gdx.app.log(TAG, "Automap pan up: offsetY=" + automapViewY);
  }
  
  /**
   * 向下平移 (减少 Y 偏移)
   */
  public static void automapDown() {
    if (AUTOMAP_MODE == AUTOMAP_MODE_OFF) return;
    
    float step = 3.0f;
    automapViewY -= step;
    
    // Gdx.app.log(TAG, "Automap pan down: offsetY=" + automapViewY);
  }
  
  /**
   * 向左平移 (减少 X 偏移)
   */
  public static void automapLeft() {
    if (AUTOMAP_MODE == AUTOMAP_MODE_OFF) return;
    
    float step = 3.0f;
    automapViewX -= step;
    
    // Gdx.app.log(TAG, "Automap pan left: offsetX=" + automapViewX);
  }
  
  /**
   * 向右平移 (增加 X 偏移)
   */
  public static void automapRight() {
    if (AUTOMAP_MODE == AUTOMAP_MODE_OFF) return;
    
    float step = 3.0f;
    automapViewX += step;
    
    // Gdx.app.log(TAG, "Automap pan right: offsetX=" + automapViewX);
  }
  
  /**
   * 重置小地图到默认状态（跟随玩家，默认缩放）
   */
  public static void automapReset() {
    automapViewX = 0;
    automapViewY = 0;
    automapViewWidth = 0;  // 会在下次渲染时重新初始化为默认值
    automapViewHeight = 0;
    // 重置默认值，会在下次渲染时重新计算
    automapDefaultViewWidth = 0;
    automapDefaultViewHeight = 0;
    // Gdx.app.log(TAG, "Automap reset");
  }
  
  /**
   * 获取当前automap显示范围宽度
   */
  public static float getAutomapViewWidth() {
    return automapViewWidth;
  }
  
  /**
   * 获取当前automap显示范围高度
   */
  public static float getAutomapViewHeight() {
    return automapViewHeight;
  }
  
  /**
   * 检查小地图是否打开
   */
  public static boolean isAutomapVisible() {
    return AUTOMAP_MODE != AUTOMAP_MODE_OFF;
  }
  
  // 静态标志，用于在 toggleAutomap 和 drawAutomap 之间通信
  private static boolean automapSaveRequestedStatic = false;
}
