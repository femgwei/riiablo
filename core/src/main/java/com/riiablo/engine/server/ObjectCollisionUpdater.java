package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.IntMap;
import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Box2DBody;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Object;
import com.riiablo.engine.server.component.Position;
import com.riiablo.map.Map;

/** Keeps native object collision aligned with Objects.txt HasCollision[mode]. */
@Wire(failOnNull = false)
@All({Object.class, Position.class})
public class ObjectCollisionUpdater extends IteratingSystem {
  protected ComponentMapper<Object> mObject;
  protected ComponentMapper<Box2DBody> mBox2DBody;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  @Wire(name = "map", failOnNull = false)
  protected Map map;

  private final IntMap<Footprint> footprints = new IntMap<>();

  private void setBox2DCollision(int entityId, boolean enabled) {
    Box2DBody bodyWrapper = mBox2DBody.get(entityId);
    // Object entities can enter the ECS one frame before Box2DPhysics creates
    // their body (especially after a room is reactivated).  Keep the desired
    // mode in CofReference and apply it on the next pass instead of throwing
    // during map loading.
    if (bodyWrapper == null || bodyWrapper.body == null) return;
    Body body = bodyWrapper.body;
    if (body.isActive() != enabled) body.setActive(enabled);
  }

  @Override
  protected void process(int entityId) {
    CofReference reference = mCofReference.get(entityId);
    Object object = mObject.get(entityId);
    Objects.Entry base = object == null ? null : object.base;
    // Headless/server objects without a rendered COF still obey their native
    // NU collision row. A missing CofReference must not make a solid door or
    // wall object silently non-blocking.
    int mode = reference == null ? Engine.Object.MODE_NU : reference.mode;
    boolean enabled = hasCollision(base, mode);
    setBox2DCollision(entityId, enabled);

    Position position = mPosition.get(entityId);
    MapWrapper wrapper = mMapWrapper.get(entityId);
    Map objectMap = wrapper != null && wrapper.map != null ? wrapper.map : map;
    Map.Zone zone = wrapper != null ? wrapper.zone : null;
    if (zone == null && objectMap != null && position != null) {
      zone = objectMap.getZone(position.position);
    }

    int width = base == null ? 0 : Math.max(0, base.SizeX);
    int height = base == null ? 0 : Math.max(0, base.SizeY);
    int x = position == null ? 0 : MathUtils.round(position.position.x - width / 2f);
    int y = position == null ? 0 : MathUtils.round(position.position.y - height / 2f);
    Footprint previous = footprints.get(entityId);
    if (previous != null && previous.matches(zone, x, y, width, height, enabled)) return;
    if (previous != null) previous.remove();

    if (enabled && zone != null && width > 0 && height > 0) {
      Footprint next = new Footprint(zone, x, y, width, height);
      next.add();
      footprints.put(entityId, next);
    } else {
      footprints.remove(entityId);
    }
    if (Gdx.app != null && (previous == null || previous.enabled != enabled || previous.zone != zone
        || previous.x != x || previous.y != y || previous.width != width
        || previous.height != height)) {
      Gdx.app.debug("ObjectCollisionUpdater", "entity=" + entityId
          + " mode=" + mode + " enabled=" + enabled
          + " zone=" + (zone == null ? "null" : zone.level == null ? "?" : zone.level.Id)
          + " footprint=" + x + "," + y + "," + width + "x" + height);
    }
  }

  @Override
  protected void removed(int entityId) {
    Footprint footprint = footprints.remove(entityId);
    if (footprint != null) footprint.remove();
  }

  static boolean hasCollision(Objects.Entry base, int mode) {
    return base != null && base.HasCollision != null
        && mode >= 0 && mode < base.HasCollision.length
        && base.HasCollision[mode];
  }

  private static final class Footprint {
    final Map.Zone zone;
    final int x;
    final int y;
    final int width;
    final int height;
    final boolean enabled = true;

    Footprint(Map.Zone zone, int x, int y, int width, int height) {
      this.zone = zone;
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
    }

    boolean matches(Map.Zone zone, int x, int y, int width, int height,
        boolean enabled) {
      return enabled && this.zone == zone && this.x == x && this.y == y
          && this.width == width && this.height == height;
    }

    void add() {
      zone.adjustObjectCollision(x, y, width, height, 1);
    }

    void remove() {
      zone.adjustObjectCollision(x, y, width, height, -1);
    }
  }
}
