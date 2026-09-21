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
import com.riiablo.map.Map;

/** Maintains D2-style dynamic unit footprints without physics-engine pushing. */
public class DynamicUnitCollisionSystem extends BaseSystem {
  protected ComponentMapper<Class> mClass;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Size> mSize;
  protected ComponentMapper<Velocity> mVelocity;

  private final UnitCollisionGrid grid = new UnitCollisionGrid();
  private EntitySubscription units;
  private final boolean enabled;

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
    if (!enabled) return;
    grid.clear();
    IntBag entities = units.getEntities();
    for (int i = 0; i < entities.size(); i++) {
      int entityId = entities.get(i);
      if (hasPresence(entityId)) {
        Position position = mPosition.get(entityId);
        grid.put(entityId, Map.round(position.position.x),
            Map.round(position.position.y), footprint(entityId));
      }
    }
  }

  /** Used by the pathfinder; targetId is ignored for approach paths. */
  public boolean isFreeForPath(int moverId, int targetId,
      int x, int y, int size) {
    return !enabled || grid.isFree(moverId, targetId, x, y, size);
  }

  /** Transfers a unit to a destination without pushing another unit. */
  public boolean tryMove(int entityId, Vector2 destination) {
    if (!enabled || destination == null || !hasPresence(entityId)) return true;
    return grid.move(entityId, -1, Map.round(destination.x),
        Map.round(destination.y), footprint(entityId));
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
      MonStats.Entry stats = mMonster.get(entityId).monstats;
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

  private int footprint(int entityId) {
    return mSize.has(entityId) ? Math.max(1, mSize.get(entityId).size) : 1;
  }
}
