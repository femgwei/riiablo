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

  /** Preset prewarming runs inside a Warp transaction, before the next ECS
   * subscription pass. Its collision must exist before selecting arrival. */
  public void synchronizeCreatedObject(int entityId) {
    if (mObject.has(entityId) && mPosition.has(entityId)) process(entityId);
  }

  /** Publish mode/deletion transitions before a same-tick collision query. */
  public void synchronizeCollision() {
    com.artemis.utils.IntBag ids = world.getAspectSubscriptionManager()
        .get(com.artemis.Aspect.all(Object.class, Position.class)).getEntities();
    for (int i = 0; i < ids.size(); i++) process(ids.get(i));
  }

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
    if (com.artemis.PendingEntityDeletion.isDeleted(world, entityId)) {
      removed(entityId);
      return;
    }
    CofReference reference = mCofReference.get(entityId);
    Object object = mObject.get(entityId);
    Objects.Entry base = object == null ? null : object.base;
    // Headless/server objects without a rendered COF still obey their native
    // NU collision row. A missing CofReference must not make a solid door or
    // wall object silently non-blocking.
    int mode = reference == null ? Engine.Object.MODE_NU : reference.mode;
    // Object.mode is the persistent state serialized to clients. CofReference
    // may still be OP or carry a stale idle mode for one update while a native
    // room snapshot is applied; reconcile idle collision with the authoritative
    // object state without interrupting an active operation animation.
    if (reference != null && mode != Engine.Object.MODE_OP
        && object != null && object.mode >= Engine.Object.MODE_NU
        && object.mode <= Engine.Object.MODE_S5 && object.mode != mode) {
      mode = object.mode;
    }
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
    boolean door = base != null && base.IsDoor;
    int nativeMask = nativeCollisionMask(base);
    if (previous != null && previous.matches(zone, x, y, width, height, enabled, door, nativeMask)) return;
    if (previous != null) previous.remove();

    // Cache both colliding and non-colliding modes. Without the latter every
    // static decoration was treated as a state transition on every 25 Hz
    // tick, flooding long-running headless tests with millions of lines.
    Footprint next = new Footprint(zone, x, y, width, height, door, enabled, nativeMask);
    if (enabled && zone != null && width > 0 && height > 0) next.add();
    footprints.put(entityId, next);
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

  /** D2Common Units.cpp::UNITS_GetCollisionMask (object branch). */
  static int nativeCollisionMask(Objects.Entry base) {
    if (base == null) return 0;
    return com.d2moo.common.collision.D2Collision.objectMask(
        base.IsDoor, base.BlocksVis, base.BlockMissile, base.SubClass);
  }

  private static final class Footprint {
    final Map.Zone zone;
    final int x;
    final int y;
    final int width;
    final int height;
    final boolean door;
    final boolean enabled;
    final int nativeMask;
    final int generation;
    final Map.Zone[] cellZones;
    final int[] cellGenerations;

    Footprint(Map.Zone zone, int x, int y, int width, int height,
        boolean door, boolean enabled, int nativeMask) {
      this.zone = zone;
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
      this.door = door;
      this.enabled = enabled;
      this.nativeMask = nativeMask;
      this.generation = zone == null ? 0 : zone.nativeCollisionGeneration();
      int cells = enabled && zone != null && width > 0 && height > 0 ? width * height : 0;
      cellZones = new Map.Zone[cells];
      cellGenerations = new int[cells];
      for (int dy = 0; dy < height && cells > 0; dy++) for (int dx = 0; dx < width; dx++) {
        int index = dy * width + dx;
        Map.Zone owner = zone.nativeCollisionZone(x + dx, y + dy);
        cellZones[index] = owner;
        cellGenerations[index] = owner == null ? 0 : owner.nativeCollisionGeneration();
      }
    }

    boolean matches(Map.Zone zone, int x, int y, int width, int height,
        boolean enabled, boolean door, int nativeMask) {
      if (!(this.enabled == enabled && this.zone == zone && this.x == x && this.y == y
          && this.width == width && this.height == height && this.door == door
          && this.nativeMask == nativeMask
          && (zone == null || generation == zone.nativeCollisionGeneration()))) return false;
      for (int i = 0; i < cellZones.length; i++) {
        Map.Zone owner = zone.nativeCollisionZone(x + i % width, y + i / width);
        if (cellZones[i] != owner
            || owner != null && cellGenerations[i] != owner.nativeCollisionGeneration()) return false;
      }
      return true;
    }

    void add() {
      zone.adjustObjectCollision(x, y, width, height, 1, door, 0);
      alterNative(true);
    }

    void remove() {
      if (enabled && zone != null && width > 0 && height > 0
          && generation == zone.nativeCollisionGeneration()) {
        zone.adjustObjectCollision(x, y, width, height, -1, door, 0);
      }
      alterNative(false);
    }

    private void alterNative(boolean set) {
      for (int i = 0; i < cellZones.length; i++) {
        Map.Zone owner = cellZones[i];
        if (owner == null || owner.nativeCollisionGeneration() != cellGenerations[i]) continue;
        int cx = x + i % width, cy = y + i / width;
        if (set) owner.setNativeCollisionMask(cx, cy, nativeMask);
        else owner.resetNativeCollisionMask(cx, cy, nativeMask);
      }
    }
  }
}
