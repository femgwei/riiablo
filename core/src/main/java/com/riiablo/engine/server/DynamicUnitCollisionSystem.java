package com.riiablo.engine.server;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.BaseSystem;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Size;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.Box2DBody;
import com.riiablo.map.Map;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/** Maintains D2-style dynamic unit footprints without physics-engine pushing. */
public class DynamicUnitCollisionSystem extends BaseSystem {
  private static final Logger log = LogManager.getLogger(DynamicUnitCollisionSystem.class);
  protected ComponentMapper<Class> mClass;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Size> mSize;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Box2DBody> mBox2DBody;
  protected ComponentMapper<Mercenary> mMercenary;

  private final UnitCollisionGrid grid = new UnitCollisionGrid();
  private EntitySubscription units;
  private final boolean enabled;
  private final Vector2 candidate = new Vector2();
  private final Vector2 resolved = new Vector2();

  public DynamicUnitCollisionSystem() {
    this(true);
  }

  public DynamicUnitCollisionSystem(boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  protected void initialize() {
    units = world.getAspectSubscriptionManager().get(
        Aspect.all(Class.class, Position.class, Size.class, Velocity.class));
  }

  @Override
  protected void processSystem() {
    rebuildNow();
  }

  /** Rebuilds the dynamic footprint index after a warp preloads a room. */
  public void rebuildNow() {
    if (!enabled || units == null) return;
    grid.clear();
    IntBag entities = units.getEntities();
    // Preserve player positions when a deferred room population appears on
    // top of an arrival point.  Insert players first, then relocate monsters
    // which would otherwise make the player's source cell dynamically
    // occupied and leave pathfinding with no valid first step.
    insertUnits(entities, true);
    insertUnits(entities, false);
  }

  private void insertUnits(IntBag entities, boolean players) {
    for (int i = 0; i < entities.size(); i++) {
      int entityId = entities.get(i);
      if (!hasPresence(entityId) || isPlayer(entityId) != players) continue;
      Position position = mPosition.get(entityId);
      int x = Map.round(position.position.x);
      int y = Map.round(position.position.y);
      int size = footprint(entityId);
      if (grid.isFree(entityId, -1, x, y, size)) {
        grid.put(entityId, x, y, size);
      } else if (!relocateOverlappingUnit(entityId, x, y, size)) {
        // Keep a deterministic footprint even when a malformed/reduced map
        // has no walkable relocation candidate; later ticks can retry after
        // the room topology finishes loading.
        grid.put(entityId, x, y, size);
      }
    }
  }

  private boolean isPlayer(int entityId) {
    return mClass.has(entityId) && mClass.get(entityId).type == Class.Type.PLR;
  }

  private boolean relocateOverlappingUnit(int entityId, int originX, int originY,
      int size) {
    Position position = mPosition.get(entityId);
    Map.Zone zone = null;
    if (mMapWrapper != null && mMapWrapper.has(entityId)) {
      MapWrapper wrapper = mMapWrapper.get(entityId);
      zone = wrapper == null ? null : wrapper.zone;
    }
    for (int radius = 1; radius <= 16; radius++) {
      for (int dy = -radius; dy <= radius; dy++) {
        for (int dx = -radius; dx <= radius; dx++) {
          if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) continue;
          int x = originX + dx;
          int y = originY + dy;
          if (!staticWalkable(zone, x, y, size)) continue;
          if (!grid.isFree(entityId, -1, x, y, size)) continue;
          position.position.set(x, y);
          if (mBox2DBody != null && mBox2DBody.has(entityId)
              && mBox2DBody.get(entityId).body != null) {
            mBox2DBody.get(entityId).body.setTransform(position.position, 0f);
            mBox2DBody.get(entityId).body.setLinearVelocity(0f, 0f);
          }
          grid.put(entityId, x, y, size);
          log.warn("[DYNAMIC_SPAWN_RELOCATE] entity={} from=({}, {}) to=({}, {}) size={} level={}",
              entityId, originX, originY, x, y, size,
              zone != null && zone.level != null ? zone.level.Id : -1);
          return true;
        }
      }
    }
    return false;
  }

  private boolean staticWalkable(Map.Zone zone, int x, int y, int size) {
    if (zone == null) return true;
    if (!zone.contains(x, y)) return false;
    return zone.findFreeCoordinates(candidate.set(x, y), size, 0, true, resolved);
  }

  /** Used by the pathfinder; targetId is ignored for approach paths. */
  public boolean isFreeForPath(int moverId, int targetId,
      int x, int y, int size) {
    return !enabled || grid.isFree(moverId, targetId, x, y, size,
        blockerFor(moverId));
  }

  /** Transfers a unit to a destination without pushing another unit. */
  public boolean tryMove(int entityId, Vector2 destination) {
    if (!enabled || destination == null || !hasPresence(entityId)) return true;
    return grid.move(entityId, -1, Map.round(destination.x),
        Map.round(destination.y), footprint(entityId), blockerFor(entityId));
  }

  public boolean tryMove(int entityId, float x, float y) {
    return tryMove(entityId, new Vector2(x, y));
  }

  /** Same as {@link #tryMove(int, Vector2)}, but useful for tests and probes. */
  public boolean tryMove(int entityId, int x, int y, int size) {
    if (!enabled) return true;
    return grid.move(entityId, -1, x, y, size);
  }

  boolean hasPresence(int entityId) {
    if (!mClass.has(entityId) || !mVelocity.has(entityId)) return false;
    Class.Type type = mClass.get(entityId).type;
    if (type != Class.Type.MON && type != Class.Type.PLR) return false;
    if (type == Class.Type.MON && mMonster.has(entityId)) {
      Monster monster = mMonster.get(entityId);
      if (!isDynamicObstacle(monster)) return false;
      MonStats.Entry stats = monster.monstats;
      if (stats != null) {
        String base = stats.BaseId == null ? "" : stats.BaseId.toLowerCase(java.util.Locale.ROOT);
        String id = stats.Id == null ? "" : stats.Id.toLowerCase(java.util.Locale.ROOT);
        // D2 gives flying/wraith-like units the no-presence pattern.
        if (base.startsWith("wraith") || base.startsWith("bird")
            || base.startsWith("parrot") || id.startsWith("wraith")
            || id.startsWith("bird") || id.startsWith("parrot")) return false;
      }
    }
    return true;
  }

  /**
   * Ambient critters are not dynamic path obstacles in native D2.  They can
   * still be targeted/rendered by their own systems; this only keeps them out
   * of the authoritative unit-footprint grid so a player can pass through a
   * chicken, rat, or similar small animal.
   */
  static boolean isDynamicObstacle(Monster monster) {
    return monster == null || monster.monstats2 == null || !monster.monstats2.critter;
  }

  private int footprint(int entityId) {
    return mSize.has(entityId) ? Math.max(1, mSize.get(entityId).size) : 1;
  }

  /**
   * D2's player path mask contains no monster/pet bit.  Keep ordinary unit
   * collision intact for the rest of this legacy grid, but do not let the
   * owner's own hireling block player path planning or authoritative steps.
   * Monster movers still see the hireling, matching COLLIDE_PET behavior.
   */
  private UnitCollisionGrid.UnitBlocker blockerFor(int moverId) {
    if (!isPlayer(moverId)) return null;
    return entityId -> mMercenary == null || !mMercenary.has(entityId)
        || mMercenary.get(entityId).ownerId != moverId;
  }
}
