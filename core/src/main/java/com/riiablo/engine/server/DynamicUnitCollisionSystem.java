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
import com.riiablo.engine.server.collision.CollisionSize;
import com.riiablo.map.Map;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/** Maintains D2-style dynamic unit footprints without physics-engine pushing. */
public class DynamicUnitCollisionSystem extends BaseSystem {
  private static final Logger log = LogManager.getLogger(DynamicUnitCollisionSystem.class);
  protected ComponentMapper<Class> mClass;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<com.riiablo.engine.server.component.Corpse> mCorpse;
  protected ComponentMapper<com.riiablo.engine.server.component.UnitLifecycle> mLifecycle;
  protected ComponentMapper<com.riiablo.engine.server.component.Missile> mMissile;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Size> mSize;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<com.riiablo.engine.server.component.Casting> mCasting;
  protected ComponentMapper<com.riiablo.engine.server.component.WhirlwindRuntime> mWhirlwindRuntime;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Box2DBody> mBox2DBody;
  protected ComponentMapper<Mercenary> mMercenary;
  protected ComponentMapper<SummonedPet> mSummonedPet;
  protected ComponentMapper<com.riiablo.engine.server.component.NativeUnitFlags> mNativeUnitFlags;

  private final UnitCollisionGrid grid = new UnitCollisionGrid();
  private final com.badlogic.gdx.utils.IntSet newUnits = new com.badlogic.gdx.utils.IntSet();
  private EntitySubscription units;
  private final com.badlogic.gdx.utils.IntSet spawnMissiles = new com.badlogic.gdx.utils.IntSet();
  private final com.badlogic.gdx.utils.IntSet spawnUnits = new com.badlogic.gdx.utils.IntSet();
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
    world.getAspectSubscriptionManager().get(Aspect.all(
        com.riiablo.engine.server.component.Missile.class, Position.class))
        .addSubscriptionListener(new EntitySubscription.SubscriptionListener() {
          @Override public void inserted(IntBag ids) {
            for (int i = 0; i < ids.size(); i++) spawnMissiles.add(ids.get(i));
          }
          @Override public void removed(IntBag ids) {
            for (int i = 0; i < ids.size(); i++) spawnMissiles.remove(ids.get(i));
          }
        });
    units = world.getAspectSubscriptionManager().get(
        Aspect.all(Class.class, Position.class, Size.class, Velocity.class));
    units.addSubscriptionListener(new EntitySubscription.SubscriptionListener() {
      @Override public void inserted(IntBag entities) {
        for (int i = 0; i < entities.size(); i++) {
          newUnits.add(entities.get(i));
          spawnUnits.add(entities.get(i));
        }
      }
      @Override public void removed(IntBag entities) {
        for (int i = 0; i < entities.size(); i++) {
          newUnits.remove(entities.get(i));
          spawnUnits.remove(entities.get(i));
        }
      }
    });
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

  /** Refreshes current positions without relocating overlapping units. */
  public void refreshNow() {
    rebuildNow(false);
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
    newUnits.clear();
  }

  private void insertUnits(IntBag entities, boolean players, boolean relocateOverlaps) {
    for (int i = 0; i < entities.size(); i++) {
      int entityId = entities.get(i);
      if (!hasPresence(entityId) || isPlayer(entityId) != players) continue;
      Position position = mPosition.get(entityId);
      int x = Map.round(position.position.x);
      int y = Map.round(position.position.y);
      int size = footprint(entityId);
      if (grid.isFree(entityId, -1, x, y, size, sameSpaceBlocker(entityId))) {
        grid.put(entityId, x, y, size);
      } else if (isOwnedCompanionOverlappingOwner(entityId)) {
        // Keep the real position in the collision index. MercenaryFollowSystem
        // will install a native Escape path; changing the coordinate here
        // produces a visible one-cell teleport when the player approaches.
        grid.put(entityId, x, y, size);
      } else if ((!relocateOverlaps && !newUnits.contains(entityId))
          || !relocateOverlappingUnit(entityId, x, y, size)) {
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
          if (!grid.isFree(entityId, -1, x, y, size, sameSpaceBlocker(entityId))) continue;
          position.invalidateNativePrecision();
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

  public com.badlogic.gdx.utils.IntArray pathBlockers(int moverId, int x, int y, int size) {
    return grid.blockers(moverId, Engine.INVALID_ENTITY, x, y, size, blockerFor(moverId));
  }

  /** Warp/staging queries must test the destination, before changing the wrapper. */
  public boolean isFreeForPath(Map.Zone destination, int moverId, int targetId,
      int x, int y, int size) {
    NativePathProfile profile = nativePathProfile(moverId);
    int ownerId = ownedCompanionOwner(moverId);
    return !enabled || grid.isFree(moverId, targetId, x, y, size, entityId ->
        entityId != ownerId && sharesCollisionSpace(destination, entityId)
            && blocksNativePath(profile, isPetPresence(entityId)));
  }

  /** MonsterSpawn uses CheckMaskWithSize, not the larger walking pattern. */
  public boolean isFreeForMonsterSpawn(Map.Zone zone, int x, int y, int size) {
    return !enabled || grid.isFreeWithSize(Engine.INVALID_ENTITY, x, y, size,
        other -> sharesCollisionSpace(zone, other));
  }

  public boolean isFreeForMonsterSpawnReplacing(int sourceId, Map.Zone zone, int x, int y, int size) {
    return !enabled || grid.isFreeWithSize(sourceId, x, y, size,
        other -> sharesCollisionSpace(zone, other));
  }

  /** SpawnCol uses native masks, not the unit's walking/flight profile. */
  public boolean isFreeForMonsterSpawnMask(int sourceId, Map.Zone zone, int x, int y, int size, int mask) {
    if (size < 0 || size > 3) return false;
    int radius = size >= 2 ? 1 : 0;
    for (int dy = -radius; dy <= radius; dy++) for (int dx = -radius; dx <= radius; dx++) {
      if (size == 2 && dx != 0 && dy != 0) continue;
      final int px = x + dx, py = y + dy;
      if (!zone.contains(px, py)) return false;
      int flags = zone.staticFlagsWorld(px, py);
      if (zone.objectCollisionReferences(px, py) > 0) flags |= 0x400;
      if (zone.doorCollisionReferences(px, py) > 0) flags |= 0x800;
      if ((flags & mask) != 0) return false;
      if (enabled && (mask & 0x100) != 0 && hasSpawnWraith(sourceId, zone, px, py)) return false;
      if (enabled && !grid.isFreeWithSize(sourceId, px, py, 1, other -> {
        if (!sharesCollisionSpace(zone, other)) return false;
        int bits = isPlayer(other) ? 0x80 : 0x100;
        Position position = mPosition.get(other);
        if (position != null) {
          int distance = Math.abs(Map.round(position.position.x) - px)
              + Math.abs(Map.round(position.position.y) - py);
          if (distance <= (footprint(other) >= 3 ? 1 : 0))
            bits |= isPetPresence(other) ? 0x2000 : 0x1000;
        }
        return (bits & mask) != 0;
      })) return false;
      if ((mask & 0x200) != 0 && hasSpawnItem(zone, px, py)) return false;
      if (enabled && (mask & 0x40) != 0 && hasSpawnMissile(sourceId, zone, px, py)) return false;
    }
    return true;
  }

  /** Factory registration makes newborn missiles visible before subscription insertion. */
  public void reserveMissileSpawn(int id) {
    spawnMissiles.add(id);
  }

  /** Marker-only registration does not add walking presence. */
  public void registerMonsterSpawnMarkers(int id) {
    spawnUnits.add(id);
  }

  private boolean hasSpawnWraith(int sourceId, Map.Zone zone, int x, int y) {
    for (com.badlogic.gdx.utils.IntSet.IntSetIterator it = spawnUnits.iterator(); it.hasNext;) {
      int id = it.next();
      if (id == sourceId || !mClass.has(id) || mClass.get(id).type != Class.Type.MON
          || !mMonster.has(id) || !mPosition.has(id) || !mVelocity.has(id)
          || !sharesCollisionSpace(zone, id)) continue;
      Monster monster = mMonster.get(id);
      com.riiablo.engine.server.component.UnitLifecycle lifecycle = mLifecycle.get(id);
      if (mCorpse.has(id) || lifecycle != null && lifecycle.phase.ordinal()
          >= com.riiablo.engine.server.component.UnitLifecycle.Phase.DEATH.ordinal()) continue;
      if (!NativeGroundPathProfile.isWraith(monster.monstats)
          || (mCasting.has(id) && mCasting.get(id).monsterHatchStarted)) continue;
      Position position = mPosition.get(id);
      // PATH_AllocDynamicPath: SMALL_NO_PRESENCE retains only a MONSTER cross.
      if (Math.abs(Map.round(position.position.x) - x)
          + Math.abs(Map.round(position.position.y) - y) <= 1) return true;
    }
    return false;
  }

  private boolean hasSpawnMissile(int sourceId, Map.Zone zone, int x, int y) {
    for (com.badlogic.gdx.utils.IntSet.IntSetIterator it = spawnMissiles.iterator(); it.hasNext;) {
      int id = it.next();
      if (id == sourceId || !mMissile.has(id) || !mPosition.has(id)
          || !sharesCollisionSpace(zone, id)) continue;
      com.riiablo.engine.server.component.Missile missile = mMissile.get(id);
      // Missiles.cpp: Collision controls the footprint, NOT CollideType.
      if (!missile.authoritative || missile.presentationOnly || missile.missile == null
          || !missile.missile.Collision) continue;
      int size = missile.missile.Size;
      if (size < 1 || size > 3) continue;
      Position position = mPosition.get(id);
      int dx = Math.abs(Map.round(position.position.x) - x);
      int dy = Math.abs(Map.round(position.position.y) - y);
      // PATH_AddCollisionFootprintForUnit uses WithSize for missiles, not unit patterns.
      if (size == 1 ? dx == 0 && dy == 0
          : size == 2 ? dx + dy <= 1 : dx <= 1 && dy <= 1) return true;
    }
    return false;
  }

  private boolean hasSpawnItem(Map.Zone zone, int x, int y) {
    IntBag ids = world.getAspectSubscriptionManager().get(Aspect.all(
        com.riiablo.engine.server.component.Item.class, Position.class)).getEntities();
    for (int i = 0; i < ids.size(); i++) {
      int id = ids.get(i);
      Position position = mPosition.get(id);
      if (sharesCollisionSpace(zone, id) && Map.round(position.position.x) == x
          && Map.round(position.position.y) == y) return true;
    }
    return false;
  }

  /** Reserve a successfully created exact-position monster within this tick. */
  public void reserveMonsterSpawn(int entityId, int x, int y, int size) {
    registerMonsterSpawnMarkers(entityId);
    if (enabled && hasPresence(entityId)) grid.put(entityId, x, y, size);
  }

  public void removeMonsterSpawnPresence(int entityId) { grid.remove(entityId); }

  /** Landing/placement query: every indexed unit blocks, including the owner. */
  public boolean isFreeForLanding(int moverId, int x, int y, int size) {
    return !enabled || grid.isFree(moverId, Engine.INVALID_ENTITY, x, y, size,
        sameSpaceBlocker(moverId));
  }

  /** Dynamic NO_PATH portion of the native relocation mask 0x1C09 (no PET bit). */
  public boolean isFreeForFrogEmergence(int moverId, int x, int y, int size) {
    return !enabled || grid.isFreeWithSize(moverId, x, y, size,
        other -> sharesCollisionSpace(moverId, other) && !isPetPresence(other));
  }

  /** Reserves a landing immediately so later pets in this tick see it. */
  public boolean tryLand(int moverId, int x, int y, int size) {
    if (!enabled || !hasPresence(moverId)) return true;
    return grid.move(moverId, Engine.INVALID_ENTITY, x, y, size, sameSpaceBlocker(moverId));
  }

  /** Transfers a unit to a destination without pushing another unit. */
  public boolean tryMove(int entityId, Vector2 destination) {
    if (!enabled || destination == null || !hasPresence(entityId)) return true;
    return grid.move(entityId, -1, Map.round(destination.x),
        Map.round(destination.y), footprint(entityId),
        mWhirlwindRuntime.has(entityId) ? other -> false : blockerFor(entityId));
  }

  public boolean tryMove(int entityId, float x, float y) {
    return tryMove(entityId, new Vector2(x, y));
  }

  /** Same as {@link #tryMove(int, Vector2)}, but useful for tests and probes. */
  public boolean tryMove(int entityId, int x, int y, int size) {
    if (!enabled) return true;
    return grid.move(entityId, -1, x, y, size);
  }

  public boolean hasPresence(int entityId) {
    if (mCasting.has(entityId) && mCasting.get(entityId).monsterHatchStarted) return false;
    if (!mClass.has(entityId) || !mVelocity.has(entityId)) return false;
    Class.Type type = mClass.get(entityId).type;
    if (type != Class.Type.MON && type != Class.Type.PLR) return false;
    if (type == Class.Type.MON && mMonster.has(entityId)) {
      Monster monster = mMonster.get(entityId);
      if (monster.frogNoPresence) return false;
      if (monster.nativeOwnerId >= 0 && monster.monstats != null
          && "imp1".equals(monster.monstats.BaseId)) return false;
      if (!isDynamicObstacle(monster)) return false;
      MonStats.Entry stats = monster.monstats;
      if (stats != null) {
        String base = stats.BaseId == null ? "" : stats.BaseId.toLowerCase(java.util.Locale.ROOT);
        String id = stats.Id == null ? "" : stats.Id.toLowerCase(java.util.Locale.ROOT);
        // D2 gives flying/wraith-like units the no-presence pattern.
        if (NativeGroundPathProfile.hasNoPresence(stats)) return false;
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
    if (mMonster.has(entityId) && mMonster.get(entityId).frogSmallPresence) return 1;
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
        && sharesCollisionSpace(moverId, entityId)
        && blocksNativePath(profile, isPetPresence(entityId));
  }

  private UnitCollisionGrid.UnitBlocker sameSpaceBlocker(int moverId) {
    return entityId -> sharesCollisionSpace(moverId, entityId);
  }

  private boolean sharesCollisionSpace(int firstId, int secondId) {
    // Landing probes may use INVALID_ENTITY (-1) as a temporary mover id.
    // ComponentMapper/Bags cannot be indexed with that sentinel.
    if (firstId < 0 || secondId < 0) return true;
    MapWrapper first = mMapWrapper.get(firstId), second = mMapWrapper.get(secondId);
    if (first == null || first.zone == null || second == null || second.zone == null) return true;
    if (first.map != second.map) return false;
    return first.zone == second.zone || first.map != null
        && first.map.isNativeSeamConnection(first.zone, second.zone);
  }

  private boolean sharesCollisionSpace(Map.Zone destination, int entityId) {
    MapWrapper other = mMapWrapper.get(entityId);
    if (destination == null || other == null || other.zone == null) return true;
    if (destination == other.zone) return true;
    return other.map != null && other.map.getZones().contains(destination, true)
        && other.map.isNativeSeamConnection(destination, other.zone);
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

  /**
   * Mirrors the live-unit branch of D2Common_11281_CollisionPatternFromSize.
   *
   * <p>The native routine does not use MonStats2.revive as a blanket pet
   * marker.  It first asks MONSTERS_CanBeInTown, which is true for NPC rows,
   * rows marked inTown, or a unit carrying the runtime ISREVIVE flag.  The
   * MonStats2 revive/small bits belong to the unused table-only helper
   * (11282), and treating them as runtime state makes ordinary revive-capable
   * monsters disappear from monster path collision.</p>
   */
  static boolean isPetPresence(boolean monster, boolean npc, boolean inTown,
      boolean resurrected, boolean interact) {
    return monster && !interact && (npc || inTown || resurrected);
  }

  /** Compatibility probe for the unused table-only native helper (11282). */
  static boolean isTablePetPresence(boolean monster, boolean revive,
      boolean small, boolean isAtt) {
    return monster && (revive || small) && !isAtt;
  }

  private boolean isPetPresence(int entityId) {
    if (!mMonster.has(entityId)) return false;
    Monster monster = mMonster.get(entityId);
    if (monster == null || monster.monstats2 == null) return false;
    // #11281 returns ordinary SMALL_UNIT_PRESENCE for an invalid size
    // BEFORE CanBeInTown/interact can convert it to a PET marker.
    if (mSize.has(entityId) && !CollisionSize.isValid(mSize.get(entityId).size)) return false;
    MonStats.Entry stats = monster.monstats;
    return isPetPresence(true,
        stats != null && stats.npc,
        stats != null && stats.inTown,
        mNativeUnitFlags != null && mNativeUnitFlags.has(entityId)
            && mNativeUnitFlags.get(entityId).has(
                com.riiablo.engine.server.component.NativeUnitFlags.IS_REVIVE),
        stats != null && stats.interact);
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
