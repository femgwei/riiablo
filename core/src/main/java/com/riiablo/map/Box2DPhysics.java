package com.riiablo.map;

import java.util.Arrays;
import org.apache.commons.lang3.Validate;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IntervalSystem;
import com.artemis.utils.IntBag;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Filter;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;

import com.riiablo.camera.IsometricCamera;
import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.server.component.Box2DBody;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.Object;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Size;

@All({Class.class, Position.class, Size.class, Box2DBody.class})
public class Box2DPhysics extends IntervalSystem {
  private static final String TAG = "Box2DPhysicsSystem";
  private static final boolean DEBUG = true;

  static final short CATEGORY_WORLD = 0x0001;
  static final short CATEGORY_MONSTER = 0x0002;
  static final short CATEGORY_PLAYER = 0x0004;

  protected ComponentMapper<Box2DBody> mBox2DBody;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Size> mSize;
  protected ComponentMapper<Class> mClass;
  protected ComponentMapper<Object> mObject;
  protected ComponentMapper<Interactable> mInteractable;

  @Wire(name = "map")
  protected Map map;

  @Wire(name = "iso")
  protected IsometricCamera iso;

  private World box2d;

  private final float interval;

  private final BodyDef wallBodyDef = new BodyDef() {{
    type = BodyDef.BodyType.StaticBody;
    awake = false;
    allowSleep = true;
    fixedRotation = true;
  }};
  private final BodyDef objectBodyDef = new BodyDef() {{
    type = BodyDef.BodyType.StaticBody;
    fixedRotation = true;
  }};
  private final BodyDef monsterBodyDef = new BodyDef() {{
    type = BodyDef.BodyType.DynamicBody;
    fixedRotation = true;
  }};
  private final BodyDef missileBodyDef = new BodyDef() {{
    type = BodyDef.BodyType.DynamicBody;
    fixedRotation = true;
    bullet = true;
  }};
  private final BodyDef playerBodyDef = monsterBodyDef;

  public Box2DPhysics(float interval) {
    super(null, interval);
    this.interval = interval;
  }

  public World getPhysics() {
    return box2d;
  }

  @Override
  protected void initialize() {
    Box2D.init();
    init();
  }

  public void init() {
    Validate.validState(box2d == null);
    box2d = new World(Vector2.Zero, true);
  }

  public void clear() {
    dispose();
    init();
  }

  @Override
  protected void dispose() {
    box2d.dispose();
    box2d = null;
  }

  @Override
  protected void processSystem() {
    box2d.step(interval, 6, 2);
  }

  @Override
  protected void inserted(int entityId) {
    Box2DBody wrapper = mBox2DBody.get(entityId);
    if (wrapper.body == null) wrapper.body = createBody(entityId);
    if (wrapper.body == null) mBox2DBody.remove(entityId);
  }

  public void createBodies() {
    Vector2 tileOffset = iso.getTileOffset(new Vector2()).scl(-1); // offset inverse of tile offset
    createBodies(tileOffset);
    if (DEBUG) Gdx.app.debug(TAG, "bodies=" + box2d.getBodyCount());
  }

  private Body createBody(int entityId) {
    int size = mSize.get(entityId).size;
    Class.Type type = mClass.get(entityId).type;
    if (size == Size.INSIGNIFICANT && type != Class.Type.OBJ) return null;

    Vector2 position = mPosition.get(entityId).position;

    Body body;
    switch (type) {
      case OBJ: {
          Object object = mObject.get(entityId);
          objectBodyDef.position.set(position);
          body = box2d.createBody(objectBodyDef);
          PolygonShape shape = new PolygonShape(); {
            shape.setAsBox(object.base.SizeX / 2f, object.base.SizeY / 2f);
            body.createFixture(shape, 1f);
          } shape.dispose();
          if (map != null && !mInteractable.has(entityId)) { // FIXME: need to tune this to allow pathing to entity that's solid
            map.or(position, object.base.SizeX, object.base.SizeY, DT1.Tile.FLAG_BLOCK_WALK);
          }
        }
        break;
      case MON: {
          monsterBodyDef.position.set(position);
          body = box2d.createBody(monsterBodyDef);
          CircleShape shape = new CircleShape(); {
            shape.setRadius(size / 2f);
            Fixture fixture = body.createFixture(shape, 1f);
            // Set collision filter: monsters don't collide with players or other monsters
            // This prevents players from pushing monsters away (like original D2)
            Filter filter = new Filter();
            filter.categoryBits = CATEGORY_MONSTER;
            filter.maskBits = CATEGORY_WORLD;
            filter.groupIndex = 0;
            fixture.setFilterData(filter);
          } shape.dispose();
        }
        break;
      case MIS: {
          missileBodyDef.position.set(position);
          body = box2d.createBody(missileBodyDef);
          CircleShape shape = new CircleShape(); {
            shape.setRadius(size / 2f);
            Fixture fixture = body.createFixture(shape, 1f);
            fixture.setSensor(true);
          } shape.dispose();
        }
        break;
      case PLR: {
          playerBodyDef.position.set(position);
          body = box2d.createBody(playerBodyDef);
          CircleShape shape = new CircleShape(); {
            shape.setRadius(size / 2f);
            Fixture fixture = body.createFixture(shape, 1f);
            // Set collision filter: players don't collide with monsters
            // This prevents players from pushing monsters away (like original D2)
            Filter filter = new Filter();
            filter.categoryBits = CATEGORY_PLAYER;
            filter.maskBits = CATEGORY_WORLD;
            filter.groupIndex = 0;
            fixture.setFilterData(filter);
          } shape.dispose();
        }
        break;
      default:
        body = null;
    }
    return body;
  }

  private void createBodies(Vector2 offset) {
    IntMap<Filter> filters = new IntMap<>();

    for (Map.Zone zone : new Array.ArrayIterator<>(map.zones)) {
      boolean[][] handled = new boolean[zone.height][zone.width];
      int zoneEndX = zone.x + zone.width;
      for (int y = 0, ty = zone.y, height = zone.height; y < height; y++, ty++) {
        for (int x = 0, tx = zone.x, width = zone.width; x < width; x++, tx++) {
          if (handled[y][x]) continue;
          int flags = map.flags(tx, ty);
          short unitMask = unitMaskForTileFlags(flags);
          if (unitMask != 0) {
            int endX = tx + 1;
            while (endX < zoneEndX
                && unitMaskForTileFlags(map.flags(endX, ty)) == unitMask) {
              endX++;
            }

            int lenX = endX - tx;
            int endY = ty + 1;

            int dy = y + 1;
            while (dy < height && allEqualMovementMask(map, tx, endY, lenX, unitMask)) {
              Arrays.fill(handled[dy], x, x + lenX, true);
              dy++;
              endY++;
            }

            int lenY = endY - ty;
            wallBodyDef.position.set((endX + tx) / 2f, (endY + ty) / 2f).add(offset);

            PolygonShape shape = new PolygonShape();
            shape.setAsBox(lenX / 2f, lenY / 2f);

            Filter filter = filters.get(unitMask);
            if (filter == null) {
              filters.put(unitMask, filter = new Filter());
              filter.categoryBits = CATEGORY_WORLD;
              filter.maskBits = unitMask;
              filter.groupIndex   = 0;
            }

            Body body = box2d.createBody(wallBodyDef);
            Fixture f = body.createFixture(shape, 0);
            f.setFilterData(filter);

            shape.dispose();

            x += lenX - 1;
            tx = endX - 1;
          }
        }
      }
    }

    IntBag objectEntities = world.getAspectSubscriptionManager()
        .get(Aspect.all(Object.class, Position.class))
        .getEntities();
    for (int i = 0, size = objectEntities.size(); i < size; i++) {
      int id = objectEntities.get(i);
      if (mInteractable.has(id)) continue; // FIXME: need to tune this to allow pathing to entity that's solid
      Vector2 position = mPosition.get(id).position;
      Objects.Entry base = mObject.get(id).base;
      map.or(position, base.SizeX, base.SizeY, DT1.Tile.FLAG_BLOCK_WALK);
    }
  }

  static short unitMaskForTileFlags(int flags) {
    short mask = 0;
    if ((flags & DT1.Tile.FLAG_BLOCK_WALK) != 0) {
      mask |= CATEGORY_MONSTER | CATEGORY_PLAYER;
    }
    if ((flags & DT1.Tile.FLAG_BLOCK_PLAYER_WALK) != 0) {
      mask |= CATEGORY_PLAYER;
    }
    return mask;
  }

  private static boolean allEqualMovementMask(Map map, int x, int y, int len, short mask) {
    len += x;
    while (x < len && unitMaskForTileFlags(map.flags(x, y)) == mask) x++;
    return x == len;
  }
}
