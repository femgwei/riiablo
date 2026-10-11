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
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.Box2DBody;
import com.riiablo.engine.Engine;
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
  protected ComponentMapper<SummonedPet> mSummonedPet;

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
    // A normal frame only refreshes the index.  Spawn/warp relocation is a
    // one-shot repair and must not run here, otherwise an overlapping Fallen
    // is teleported back to the same nearby cell every frame.
    rebuildNow(false);
  }

  /** Rebuilds the dynamic footprint index after a warp preloads a room. */
  public void rebuildNow() {
    rebuildNow(true);
  }

  private void rebuildNow(boolean relocateOverlaps) {
    if (!enabled || units == null) return;
    grid.clear();
    IntBag entities = units.getEntities();
    // Preserve player positions when a deferred room population appears on
    // top of an arrival point.  Insert players first, then relocate monsters
    // which would otherwise make the player's source cell dynamically
    // occupied and leave pathfinding with no valid first step.
    insertUnits(entities, true, relocateOverlaps);
    insertUnits(entities, false, relocateOverlaps);
  }

  private void insertUnits(IntBag entities, boolean players, boolean relocateOverlaps) {
    for (int i = 0; i < entities.size(); i++) {
      int entityId = entities.get(i);
      if (!hasPresence(entityId) || isPlayer(entityId) != players) continue;
      Position position = mPosition.get(entityId);
      int x = Map.round(position.position.x);
      int y = Map.round(position.position.y);
      int size = footprint(entityId);
      if (grid.isFree(entityId, -1, x, y, size)) {
        grid.put(entityId, x, y, size);
      } else if (isOwnedCompanionOverlappingOwner(entityId)) {
        // Keep the real position in the collision index. MercenaryFollowSystem
        // will install a native Escape path; changing the coordinate here
        // produces a visible one-cell teleport when the player approaches.
        grid.put(entityId, x, y, size);
      } else if (!relocateOverlaps || !relocateOverlappingUnit(entityId, x, y, size)) {
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
   * Applies the unit-type portion of D2's path masks to the authoritative
   * footprint grid. Player paths contain neither MONSTER nor PET; monster
   * paths contain PET but not ordinary MONSTER footprints.
   */
  private UnitCollisionGrid.UnitBlocker blockerFor(int moverId) {
    // D2Common's COLLIDE_MASK_MONSTER_PATH contains COLLIDE_PET, but not
    // COLLIDE_MONSTER or COLLIDE_PLAYER.  Normal presence patterns still put
    // COLLIDE_NO_PATH at their center, so they remain blockers; pet-presence
    // patterns replace that center marker with COLLIDE_PET.
    NativePathProfile profile = nativePathProfile(moverId);
    int ownerId = ownedCompanionOwner(moverId);
    return entityId -> entityId != ownerId
        && blocksNativePath(profile, isPetPresence(entityId));
  }

  /** The dynamic part of D2Common's nMoveTestCollisionMask. */
  enum NativePathProfile {
    PLAYER,
    MONSTER,
    MONSTER_CAN_OPEN_DOORS,
    FLYING
  }

  private NativePathProfile nativePathProfile(int moverId) {
    if (!mMonster.has(moverId)) return NativePathProfile.PLAYER;
    Monster monster = mMonster.get(moverId);
    MonStats.Entry stats = monster == null ? null : monster.monstats;
    if (stats != null && stats.flying) return NativePathProfile.FLYING;
    if (stats != null && stats.opendoors) {
      return NativePathProfile.MONSTER_CAN_OPEN_DOORS;
    }
    return NativePathProfile.MONSTER;
  }

  static boolean blocksNativePath(NativePathProfile profile, boolean petPresence) {
    // COLLIDE_MASK_FLYING_UNIT contains NO_PATH but deliberately omits PET.
    // Ordinary and door-opening monster masks contain both NO_PATH and PET;
    // PLAYER_PATH contains NO_PATH and also omits PET.
    if (profile == NativePathProfile.MONSTER
        || profile == NativePathProfile.MONSTER_CAN_OPEN_DOORS) return true;
    return !petPresence;
  }

  static boolean blocksNativePath(boolean monsterMover, boolean petPresence) {
    // MONSTER_PATH tests both NO_PATH and PET. PLAYER_PATH tests NO_PATH but
    // deliberately omits PET, allowing player-owned/town-compatible pets.
    return blocksNativePath(monsterMover
        ? NativePathProfile.MONSTER : NativePathProfile.PLAYER, petPresence);
  }

  /** Mirrors D2Common_11281_CollisionPatternFromSize for monster units. */
  static boolean isPetPresence(boolean monster, boolean revive,
      boolean small, boolean isAtt) {
    return monster && (revive || small) && !isAtt;
  }

  private boolean isPetPresence(int entityId) {
    if (!mMonster.has(entityId)) return false;
    Monster monster = mMonster.get(entityId);
    if (monster == null || monster.monstats2 == null) return false;
    return isPetPresence(true, monster.monstats2.revive,
        monster.monstats2.small, monster.monstats2.isAtt);
  }

  private int ownedCompanionOwner(int entityId) {
    if (mMercenary != null && mMercenary.has(entityId)) {
      return mMercenary.get(entityId).ownerId;
    }
    if (mSummonedPet != null && mSummonedPet.has(entityId)) {
      SummonedPet pet = mSummonedPet.get(entityId);
      if (pet != null && !pet.passive && !pet.boneWall
          && pet.petType != null && pet.petType.equalsIgnoreCase("valkyrie")) {
        return pet.ownerId;
      }
    }
    return Engine.INVALID_ENTITY;
  }

  private boolean isOwnedCompanionOverlappingOwner(int entityId) {
    int ownerId = ownedCompanionOwner(entityId);
    return ownerId >= 0 && isPlayer(ownerId) && mPosition.has(ownerId);
  }
}
