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
import com.badlogic.gdx.utils.IntMap;
import com.d2moo.common.collision.D2Collision;

/** Maintains D2-style dynamic unit footprints without physics-engine pushing. */
public class DynamicUnitCollisionSystem extends BaseSystem {
  private static final Logger log = LogManager.getLogger(DynamicUnitCollisionSystem.class);
  protected ComponentMapper<Class> mClass;
  protected ComponentMapper<com.riiablo.engine.server.component.Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<com.riiablo.engine.server.component.Corpse> mCorpse;
  protected ComponentMapper<com.riiablo.engine.server.component.PlayerCorpse> mPlayerCorpse;
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
  protected ComponentMapper<com.riiablo.engine.server.component.Item> mItem;
  protected ComponentMapper<com.riiablo.engine.server.component.Leap> mLeap;

  private final UnitCollisionGrid grid = new UnitCollisionGrid();
  private final SharedUnitCollisionIndex shared = new SharedUnitCollisionIndex();
  private final IntMap<LegacyFootprint> legacy = new IntMap<>();
  private final com.badlogic.gdx.utils.IntSet registered = new com.badlogic.gdx.utils.IntSet();
  private EntitySubscription positioned;
  private int pathQueryDepth;

  /** A synchronous search reads one collision snapshot, including its smoothing queries. */
  public void beginPathQuery() {
    if (pathQueryDepth == 0) synchronizeNativeAll();
    pathQueryDepth++;
  }

  public void endPathQuery() {
    if (pathQueryDepth <= 0) throw new IllegalStateException("Unbalanced path query");
    pathQueryDepth--;
  }
  private static final class LegacyFootprint {
    int x, y, size;
    Map.Zone zone;
  }
  private final com.badlogic.gdx.utils.IntSet newUnits = new com.badlogic.gdx.utils.IntSet();
  private EntitySubscription units;
  private final com.badlogic.gdx.utils.IntSet spawnMissiles = new com.badlogic.gdx.utils.IntSet();
  private final com.badlogic.gdx.utils.IntSet spawnUnits = new com.badlogic.gdx.utils.IntSet();
  private final com.badlogic.gdx.utils.IntSet corpseUnits = new com.badlogic.gdx.utils.IntSet();
  private final com.badlogic.gdx.utils.IntSet playerCorpses = new com.badlogic.gdx.utils.IntSet();
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
    positioned = world.getAspectSubscriptionManager().get(Aspect.all(Position.class));
    positioned.addSubscriptionListener(new EntitySubscription.SubscriptionListener() {
      @Override public void inserted(IntBag ids) {
        for (int i = 0; i < ids.size(); i++) synchronizeNative(ids.get(i));
      }
      @Override public void removed(IntBag ids) {
        for (int i = 0; i < ids.size(); i++) {
          int id = ids.get(i);
          shared.remove(id);
          grid.remove(id);
          legacy.remove(id);
          registered.remove(id);
        }
      }
    });
    world.getAspectSubscriptionManager().get(Aspect.all(
        com.riiablo.engine.server.component.PlayerCorpse.class,
        com.riiablo.engine.server.component.Corpse.class, Position.class))
        .addSubscriptionListener(new EntitySubscription.SubscriptionListener() {
          @Override public void inserted(IntBag ids) {
            for (int i = 0; i < ids.size(); i++) playerCorpses.add(ids.get(i));
          }
          @Override public void removed(IntBag ids) {
            for (int i = 0; i < ids.size(); i++) playerCorpses.remove(ids.get(i));
          }
        });
    world.getAspectSubscriptionManager().get(Aspect.all(Monster.class, Position.class))
        .addSubscriptionListener(new EntitySubscription.SubscriptionListener() {
          @Override public void inserted(IntBag ids) {
            for (int i = 0; i < ids.size(); i++) corpseUnits.add(ids.get(i));
          }
          @Override public void removed(IntBag ids) {
            for (int i = 0; i < ids.size(); i++) corpseUnits.remove(ids.get(i));
          }
        });
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
          grid.remove(entities.get(i));
          legacy.remove(entities.get(i));
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
    synchronizeNativeAll();
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
      if (isPlayer(entityId) != players) continue;
      if (!hasPresence(entityId)) {
        grid.remove(entityId);
        legacy.remove(entityId);
        continue;
      }
      Position position = mPosition.get(entityId);
      int x = Map.round(position.position.x);
      int y = Map.round(position.position.y);
      int size = footprint(entityId);
      Map.Zone zone = zoneOf(entityId);
      LegacyFootprint previous = legacy.get(entityId);
      if (previous != null && previous.x == x && previous.y == y && previous.size == size
          && previous.zone == zone) continue;
      // Generated maps use native allocation and bit state. The owner index
      // remains diagnostic there; spawn relocation is only a legacy fallback.
      if (nativeZone(zone)) {
        grid.put(entityId, x, y, size);
      } else
      if (grid.isFree(entityId, -1, x, y, size, sameSpaceBlocker(entityId))) {
        grid.put(entityId, x, y, size);
      } else if (isOwnedCompanionOverlappingOwner(entityId) || isMountedPair(entityId)) {
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
      LegacyFootprint next = new LegacyFootprint();
      next.x = Map.round(position.position.x); next.y = Map.round(position.position.y);
      next.size = size; next.zone = zone;
      legacy.put(entityId, next);
    }
  }

  private Map.Zone zoneOf(int id) {
    if (id < 0 || !mMapWrapper.has(id)) return null;
    MapWrapper wrapper = mMapWrapper.get(id);
    return wrapper == null ? null : wrapper.zone;
  }

  private void synchronizeNativeAll() {
    if (!enabled || positioned == null) return;
    ObjectCollisionUpdater objects = world.getSystem(ObjectCollisionUpdater.class);
    if (objects != null) objects.synchronizeCollision();
    IntBag ids = positioned.getEntities();
    for (int i = 0; i < ids.size(); i++) synchronizeNative(ids.get(i));
    for (com.badlogic.gdx.utils.IntSet.IntSetIterator it = registered.iterator(); it.hasNext;) {
      synchronizeNative(it.next());
    }
  }

  private SharedUnitCollisionIndex.Footprint nativeFootprint(int id) {
    if (com.artemis.PendingEntityDeletion.isDeleted(world, id)) return null;
    if (mLeap.has(id) && mLeap.get(id).nativeTick != null) return null;
    com.riiablo.engine.server.component.UnitLifecycle lifecycle = mLifecycle.get(id);
    if (lifecycle != null && lifecycle.phase.ordinal()
        >= com.riiablo.engine.server.component.UnitLifecycle.Phase.REMOVED.ordinal()) return null;
    Map.Zone zone = zoneOf(id);
    if (!nativeZone(zone) || !mPosition.has(id)) return null;
    Position p = mPosition.get(id);
    int x = Map.round(p.position.x), y = Map.round(p.position.y);
    int pattern = SharedUnitCollisionIndex.WITH_SIZE, size = 1, mask;
    boolean hidden = false;
    if (mPlayerCorpse.has(id) && mCorpse.has(id) && !mPlayer.has(id)) {
      mask = D2Collision.COLLIDE_CORPSE; size = 3;
    } else if (mMonster.has(id) && (mMonster.get(id).nativeCorpseCollision
        || lifecycle != null && lifecycle.isDead() && (mMonster.get(id).monstats2 == null
            || !mMonster.get(id).monstats2.deadCol))) {
      mask = D2Collision.COLLIDE_CORPSE; size = 3;
    } else if (mMissile.has(id)) {
      com.riiablo.engine.server.component.Missile missile = mMissile.get(id);
      if (!missile.authoritative || missile.presentationOnly || missile.missile == null
          || !missile.missile.Collision) return null;
      size = missile.missile.Size;
      if (size < 1 || size > 3) return null;
      mask = D2Collision.COLLIDE_MISSILE;
    } else if (mItem.has(id)) {
      mask = D2Collision.COLLIDE_ITEM;
    } else if (mClass.has(id) && mVelocity.has(id)
        && (isPlayer(id) || mClass.get(id).type == Class.Type.MON)) {
      if (isPlayer(id) && mPlayerCorpse.has(id)) return null;
      mask = isPlayer(id) ? D2Collision.COLLIDE_PLAYER : D2Collision.COLLIDE_MONSTER;
      size = footprint(id);
      boolean allocationWraith = mMonster.has(id) && !mMonster.get(id).nativeRestoredCollisionPattern
          && NativeGroundPathProfile.isWraith(mMonster.get(id).monstats);
      if (size == 0 && !allocationWraith) return null;
      pattern = size >= 3 ? D2Collision.PATTERN_BIG_UNIT_PRESENCE : D2Collision.PATTERN_SMALL_UNIT_PRESENCE;
      if (isPetPresence(id)) pattern += 2;
      if (mMonster.has(id)) {
        Monster monster = mMonster.get(id);
        if (mCasting.has(id) && mCasting.get(id).monsterHatchStarted) return null;
        hidden = monster.frogNoPresence;
        if (hidden || monster.vultureAirborne
            || !monster.nativeRestoredCollisionPattern && NativeGroundPathProfile.isWraith(monster.monstats)) {
          pattern = D2Collision.PATTERN_SMALL_NO_PRESENCE;
        } else if (!monster.nativeRestoredCollisionPattern && !isDynamicObstacle(monster)) return null;
      }
    } else return null;
    Map.Zone center = zone.nativeCollisionZone(x, y);
    return new SharedUnitCollisionIndex.Footprint(center == null ? zone : center,
        x, y, pattern, size, mask, hidden);
  }

  /** Apply only observed transitions. Identical state never rewrites the grid. */
  private void synchronizeNative(int id) {
    if (!enabled) return;
    SharedUnitCollisionIndex.Footprint next = nativeFootprint(id);
    if (next != null && next.hidden && shared.get(id) == null) {
      int size = mSize.has(id) ? mSize.get(id).size : 1;
      int initialPattern = size >= 3 ? D2Collision.PATTERN_BIG_UNIT_PRESENCE
          : size == 0 ? D2Collision.PATTERN_NONE : D2Collision.PATTERN_SMALL_UNIT_PRESENCE;
      shared.update(id, new SharedUnitCollisionIndex.Footprint(next.zone, next.x, next.y,
          initialPattern, size, next.mask, false));
    }
    shared.update(id, next);
  }

  /** Same-tick allocation boundary for ground items and other positioned units. */
  public void reserveNativeCollision(int id) {
    synchronizeNativeAll();
    registered.add(id);
    synchronizeNative(id);
  }

  /** Per-unit movement boundary; allocation already registered this id. */
  public void synchronizeNativeCollision(int id) {
    synchronizeNative(id);
  }

  /** #10142 owns a real remove/search/restore transaction, with no query-time repaint. */
  int computeNativePath(com.d2moo.common.path.D2PathRequest.State request, int ownerId, int targetId) {
    synchronizeNativeAll();
    final SharedUnitCollisionIndex.Footprint owner = shared.get(ownerId);
    final SharedUnitCollisionIndex.Footprint target = shared.get(targetId);
    final Map.Zone zone = zoneOf(ownerId);
    return com.d2moo.common.path.D2PathRequest.compute(request, false,
        new com.d2moo.common.path.D2PathRequest.Effects() {
          public boolean removeOwner(com.d2moo.common.path.D2PathRequest.State s) {
            shared.remove(ownerId); return owner != null;
          }
          public boolean removeTarget(com.d2moo.common.path.D2PathRequest.State s) {
            if (target == null) return false;
            shared.remove(targetId); return true;
          }
          public void addTarget(com.d2moo.common.path.D2PathRequest.State s) { shared.update(targetId, target); }
          public void addOwner(com.d2moo.common.path.D2PathRequest.State s) { shared.update(ownerId, owner); }
          public boolean blocked(com.d2moo.common.path.D2PathRequest.State s, int x, int y) {
            return shared.check(zone, x, y, s.collisionPattern, s.unitSize, s.moveMask, -1, -1) != 0;
          }
          public int search(com.d2moo.common.path.D2PathRequest.Search search) {
            return com.d2moo.common.path.D2PathDispatch.compute(search, (x,y) -> blocked(search.state,x,y));
          }
        });
  }

  int nativePathPattern(int id) {
    synchronizeNative(id);
    SharedUnitCollisionIndex.Footprint footprint = shared.get(id);
    return footprint == null ? D2Collision.PATTERN_NONE : footprint.pattern;
  }

  boolean nativePathBlocked(int id, int x, int y, int size, int mask) {
    Map.Zone zone = zoneOf(id);
    if (!nativeZone(zone)) return false;
    SharedUnitCollisionIndex.Footprint footprint = shared.get(id);
    int pattern = footprint == null ? testPattern(size) : footprint.pattern;
    return shared.check(zone,x,y,pattern,size,mask,id,-1) != 0;
  }

  boolean hasNativePathCollision(int id) { return nativeZone(zoneOf(id)); }

  /** Caller synchronizes once before the tick, then commits precision before publishing again. */
  boolean moveNativePath(int id, int x, int y, int mask, boolean unchecked) {
    SharedUnitCollisionIndex.Footprint footprint = shared.get(id);
    if (footprint == null) return unchecked || shared.check(zoneOf(id),x,y,
        D2Collision.PATTERN_NONE,0,mask,-1,-1) == 0;
    if (unchecked) { shared.update(id,footprint.at(x,y)); return true; }
    return shared.move(id, footprint.at(x,y), mask);
  }

  /** Explicit deletion boundary for consumers which perform more work in the same tick. */
  public void releaseNativeCollision(int id) {
    shared.remove(id);
    grid.remove(id);
    legacy.remove(id);
    registered.remove(id);
  }

  private boolean isPlayer(int entityId) {
    return entityId >= 0 && mClass.has(entityId) && mClass.get(entityId).type == Class.Type.PLR;
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
    Map.Zone zone = zoneOf(moverId);
    if (enabled && nativeZone(zone)) {
      if (pathQueryDepth == 0) synchronizeNativeAll();
      return shared.check(zone, x, y, testPattern(size), size, dynamicMoveMask(moverId),
          moverId, pathIgnored(moverId, targetId)) == 0;
    }
    return !enabled || grid.isFree(moverId, targetId, x, y, size,
        blockerFor(moverId));
  }

  public com.badlogic.gdx.utils.IntArray pathBlockers(int moverId, int x, int y, int size) {
    if (nativeZone(zoneOf(moverId)) && isFreeForPath(moverId, -1, x, y, size)) {
      return new com.badlogic.gdx.utils.IntArray();
    }
    return grid.blockers(moverId, Engine.INVALID_ENTITY, x, y, size, blockerFor(moverId));
  }

  /** Warp/staging queries must test the destination, before changing the wrapper. */
  public boolean isFreeForPath(Map.Zone destination, int moverId, int targetId,
      int x, int y, int size) {
    if (enabled && nativeZone(destination)) {
      if (pathQueryDepth == 0) synchronizeNativeAll();
      return shared.check(destination, x, y, testPattern(size), size, dynamicMoveMask(moverId),
          moverId, pathIgnored(moverId, targetId)) == 0;
    }
    NativePathProfile profile = nativePathProfile(moverId);
    int ownerId = ownedCompanionOwner(moverId);
    return !enabled || grid.isFree(moverId, targetId, x, y, size, entityId ->
        entityId != ownerId && sharesCollisionSpace(destination, entityId)
            && blocksNativePath(profile, isPetPresence(entityId)));
  }

  /** MonsterSpawn uses CheckMaskWithSize, not the larger walking pattern. */
  public boolean isFreeForMonsterSpawn(Map.Zone zone, int x, int y, int size) {
    if (enabled && nativeZone(zone)) {
      synchronizeNativeAll();
      return shared.check(zone, x, y, SharedUnitCollisionIndex.WITH_SIZE, size, 0x3000, -1, -1) == 0;
    }
    return !enabled || grid.isFreeWithSize(Engine.INVALID_ENTITY, x, y, size,
        other -> sharesCollisionSpace(zone, other));
  }

  public boolean isFreeForMonsterSpawnReplacing(int sourceId, Map.Zone zone, int x, int y, int size) {
    if (enabled && nativeZone(zone)) {
      synchronizeNativeAll();
      return shared.check(zone, x, y, SharedUnitCollisionIndex.WITH_SIZE, size, 0x3000, sourceId, -1) == 0;
    }
    return !enabled || grid.isFreeWithSize(sourceId, x, y, size,
        other -> sharesCollisionSpace(zone, other));
  }

  /** SpawnCol uses native masks, not the unit's walking/flight profile. */
  public boolean isFreeForMonsterSpawnMask(int sourceId, Map.Zone zone, int x, int y, int size, int mask) {
    if (size < 0 || size > 3) return false;
    if (nativeZone(zone)) {
      synchronizeNativeAll();
      return shared.check(zone, x, y, SharedUnitCollisionIndex.WITH_SIZE, size, mask, sourceId, -1) == 0;
    }
    int radius = size >= 2 ? 1 : 0;
    for (int dy = -radius; dy <= radius; dy++) for (int dx = -radius; dx <= radius; dx++) {
      if (size == 2 && dx != 0 && dy != 0) continue;
      final int px = x + dx, py = y + dy;
      if (!zone.contains(px, py)) return false;
      int flags = zone.map == null ? zone.nativeCollisionFlagsWorld(px, py)
          : zone.map.pathCollisionFlags(zone, px, py);
      if ((flags & mask) != 0) return false;
      if (enabled && (mask & 0x8000) != 0 && hasSpawnCorpse(sourceId, zone, px, py)) return false;
      if (enabled && (mask & 0x100) != 0 && hasSpawnMonsterMarker(sourceId, zone, px, py)) return false;
      if (enabled && (mask & 0x80) != 0 && hasSpawnPlayerMarker(sourceId, zone, px, py)) return false;
      if (enabled && !grid.isFreeWithSize(sourceId, px, py, 1, other -> {
        if (!hasPresence(other) || !sharesCollisionSpace(zone, other)) return false;
        int bits = 0; // PLAYER/MONSTER footprints are queried separately from walking presence.
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
    reserveNativeCollision(id);
  }

  /** Marker-only registration does not add walking presence. */
  public void registerMonsterSpawnMarkers(int id) {
    spawnUnits.add(id);
    reserveNativeCollision(id);
  }

  /** Player allocation writes collision before ECS subscription insertion. */
  public void reservePlayerSpawn(int id) {
    spawnUnits.add(id);
    reserveNativeCollision(id);
    if (enabled && hasPresence(id) && mPosition.has(id)) {
      Position position = mPosition.get(id);
      grid.put(id, Map.round(position.position.x), Map.round(position.position.y), footprint(id));
    }
  }

  private boolean hasSpawnPlayerMarker(int sourceId, Map.Zone zone, int x, int y) {
    for (com.badlogic.gdx.utils.IntSet.IntSetIterator it = spawnUnits.iterator(); it.hasNext;) {
      int id = it.next();
      if (id == sourceId || !isPlayer(id) || !hasPresence(id) || !mPosition.has(id)
          || mCorpse.has(id) || !sharesCollisionSpace(zone, id)) continue;
      com.riiablo.engine.server.component.UnitLifecycle lifecycle = mLifecycle.get(id);
      if (lifecycle != null && lifecycle.isDead()) continue;
      Position position = mPosition.get(id);
      int dx = Math.abs(Map.round(position.position.x) - x);
      int dy = Math.abs(Map.round(position.position.y) - y);
      if (footprint(id) >= 3 ? dx <= 1 && dy <= 1 : dx + dy <= 1) return true;
    }
    return false;
  }

  /** Native server dead collision is a 3x3 CORPSE marker, not live presence. */
  public void markMonsterDeadCollision(int id) {
    if (!mMonster.has(id)) return;
    mMonster.get(id).nativeCorpseCollision = true;
    corpseUnits.add(id);
    grid.remove(id);
    legacy.remove(id);
    reserveNativeCollision(id);
  }

  /** The retained inventory corpse, not the respawnable player, owns this box. */
  public void reservePlayerCorpse(int playerId, int corpseId) {
    grid.remove(playerId);
    legacy.remove(playerId);
    shared.remove(playerId);
    playerCorpses.add(corpseId);
    reserveNativeCollision(corpseId);
  }

  private boolean hasSpawnCorpse(int sourceId, Map.Zone zone, int x, int y) {
    for (com.badlogic.gdx.utils.IntSet.IntSetIterator it = playerCorpses.iterator(); it.hasNext;) {
      int id = it.next();
      if (id == sourceId || mPlayer.has(id) || !mPlayerCorpse.has(id) || !mCorpse.has(id)
          || !mPosition.has(id) || !sharesCollisionSpace(zone, id)) continue;
      Position position = mPosition.get(id);
      if (Math.abs(Map.round(position.position.x) - x) <= 1
          && Math.abs(Map.round(position.position.y) - y) <= 1) return true;
    }
    for (com.badlogic.gdx.utils.IntSet.IntSetIterator it = corpseUnits.iterator(); it.hasNext;) {
      int id = it.next();
      if (id == sourceId || !mMonster.has(id) || !mPosition.has(id)
          || !mMonster.get(id).nativeCorpseCollision || !sharesCollisionSpace(zone, id)) continue;
      Position position = mPosition.get(id);
      if (Math.abs(Map.round(position.position.x) - x) <= 1
          && Math.abs(Map.round(position.position.y) - y) <= 1) return true;
    }
    return false;
  }

  private boolean hasSpawnMonsterMarker(int sourceId, Map.Zone zone, int x, int y) {
    for (com.badlogic.gdx.utils.IntSet.IntSetIterator it = spawnUnits.iterator(); it.hasNext;) {
      int id = it.next();
      if (id == sourceId || !mClass.has(id) || mClass.get(id).type != Class.Type.MON
          || !mMonster.has(id) || !mPosition.has(id) || !mVelocity.has(id)
          || !sharesCollisionSpace(zone, id)) continue;
      Monster monster = mMonster.get(id);
      if (monster.nativeCorpseCollision) continue;
      boolean present = hasPresence(id);
      com.riiablo.engine.server.component.UnitLifecycle lifecycle = mLifecycle.get(id);
      if (!present && (mCorpse.has(id) || lifecycle != null && lifecycle.phase.ordinal()
          >= com.riiablo.engine.server.component.UnitLifecycle.Phase.DEATH.ordinal())) continue;
      if (mCasting.has(id) && mCasting.get(id).monsterHatchStarted) continue;
      Position position = mPosition.get(id);
      int dx = Math.abs(Map.round(position.position.x) - x);
      int dy = Math.abs(Map.round(position.position.y) - y);
      // The walking index stores NO_PATH/PET only, not the full MONSTER mask.
      if (present) {
        if (footprint(id) >= 3 ? dx <= 1 && dy <= 1 : dx + dy <= 1) return true;
        continue;
      }
      boolean hiddenFrog = monster.frogNoPresence && monster.frogSpawnMarkerInitialized;
      if ((!NativeGroundPathProfile.isWraith(monster.monstats)
          || monster.nativeRestoredCollisionPattern) && !hiddenFrog) continue;
      if (hiddenFrog) observeHiddenFrogMove(id, position.position);
      int distance = dx + dy;
      if (hiddenFrog && !monster.frogSpawnMarkerMoved && distance == 0) continue;
      // PATH_AllocDynamicPath: SMALL_NO_PRESENCE retains only a MONSTER cross.
      if (distance <= 1) return true;
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

  public void removeMonsterSpawnPresence(int entityId) {
    grid.remove(entityId);
    legacy.remove(entityId);
    synchronizeNative(entityId);
  }

  /** Landing/placement query: every indexed unit blocks, including the owner. */
  public boolean isFreeForLanding(int moverId, int x, int y, int size) {
    if (enabled && nativeZone(zoneOf(moverId))) {
      synchronizeNativeAll();
      return shared.check(zoneOf(moverId), x, y, testPattern(size), size, 0x3000, moverId, -1) == 0;
    }
    return !enabled || grid.isFree(moverId, Engine.INVALID_ENTITY, x, y, size,
        sameSpaceBlocker(moverId));
  }

  /** Dynamic NO_PATH portion of the native relocation mask 0x1C09 (no PET bit). */
  public boolean isFreeForFrogEmergence(int moverId, int x, int y, int size) {
    if (enabled && nativeZone(zoneOf(moverId))) {
      synchronizeNativeAll();
      return shared.check(zoneOf(moverId), x, y, SharedUnitCollisionIndex.WITH_SIZE,
          size, 0x1C09, moverId, -1) == 0;
    }
    return !enabled || grid.isFreeWithSize(moverId, x, y, size,
        other -> sharesCollisionSpace(moverId, other) && !isPetPresence(other));
  }

  /** Reserves a landing immediately so later pets in this tick see it. */
  public boolean tryLand(int moverId, int x, int y, int size) {
    if (!enabled || !hasPresence(moverId)) return true;
    if (nativeZone(zoneOf(moverId))) return moveNative(moverId, x, y, 0x3000, false);
    return grid.move(moverId, Engine.INVALID_ENTITY, x, y, size, sameSpaceBlocker(moverId));
  }

  /** Legacy map compatibility: a cross-zone pet arrives in the owner's space,
   * not its old zone. Native RoomEx/shared-bit relocation keeps its existing path.
   * The caller commits MapWrapper/Position immediately after a successful reservation.
   */
  public boolean tryLand(int moverId, Map.Zone destination, int x, int y, int size) {
    Map.Zone source = zoneOf(moverId);
    if (destination != null && destination != source && !nativeZone(source) && !nativeZone(destination)) {
      return !enabled || !hasPresence(moverId)
          || grid.move(moverId, Engine.INVALID_ENTITY, x, y, size,
              other -> sharesCollisionSpace(destination, other));
    }
    return tryLand(moverId, x, y, size);
  }

  /** Transfers a unit to a destination without pushing another unit. */
  public boolean tryMove(int entityId, Vector2 destination) {
    if (!enabled || destination == null) return true;
    if (nativeZone(zoneOf(entityId))) {
      return moveNative(entityId, Map.round(destination.x), Map.round(destination.y),
          mWhirlwindRuntime.has(entityId) ? WhirlwindCollision.MOVE_MASK : dynamicMoveMask(entityId));
    }
    if (!hasPresence(entityId)) {
      observeHiddenFrogMove(entityId, destination);
      return true;
    }
    return grid.move(entityId, -1, Map.round(destination.x),
        Map.round(destination.y), footprint(entityId),
        mWhirlwindRuntime.has(entityId) ? other -> false : blockerFor(entityId));
  }

  private void observeHiddenFrogMove(int id, Vector2 destination) {
    if (!mMonster.has(id)) return;
    Monster monster = mMonster.get(id);
    if (monster.frogNoPresence && monster.frogSpawnMarkerInitialized
        && (Map.round(destination.x) != monster.frogSpawnMarkerX
            || Map.round(destination.y) != monster.frogSpawnMarkerY)) {
      monster.frogSpawnMarkerMoved = true;
      monster.frogSpawnMarkerX = Map.round(destination.x);
      monster.frogSpawnMarkerY = Map.round(destination.y);
    }
  }

  public boolean tryMove(int entityId, float x, float y) {
    return tryMove(entityId, new Vector2(x, y));
  }

  /** Same as {@link #tryMove(int, Vector2)}, but useful for tests and probes. */
  public boolean tryMove(int entityId, int x, int y, int size) {
    if (!enabled) return true;
    if (nativeZone(zoneOf(entityId))) return moveNative(entityId, x, y,
        mWhirlwindRuntime.has(entityId) ? WhirlwindCollision.MOVE_MASK : dynamicMoveMask(entityId));
    return grid.move(entityId, -1, x, y, size);
  }

  private static boolean nativeZone(Map.Zone zone) {
    return zone != null && zone.map != null && zone.map.usesNativeCollision(zone);
  }

  private static int testPattern(int size) {
    return size == 0 ? D2Collision.PATTERN_NONE : size == 3
        ? D2Collision.PATTERN_BIG_UNIT_PRESENCE : D2Collision.PATTERN_SMALL_UNIT_PRESENCE;
  }

  private int pathIgnored(int moverId, int targetId) {
    int owner = ownedCompanionOwner(moverId);
    return owner >= 0 ? owner : targetId;
  }

  private int dynamicMoveMask(int id) {
    if (id >= 0 && mMonster.has(id)) {
      Monster monster = mMonster.get(id);
      if (monster.frogNoPresence || monster.vultureAirborne
          || NativeGroundPathProfile.isWraith(monster.monstats)) return 0;
    }
    NativePathProfile profile = nativePathProfile(id);
    return profile == NativePathProfile.MONSTER || profile == NativePathProfile.MONSTER_CAN_OPEN_DOORS
        ? 0x3000 : 0x1000;
  }

  private boolean moveNative(int id, int x, int y, int mask) {
    return moveNative(id, x, y, mask, true);
  }

  private boolean moveNative(int id, int x, int y, int mask, boolean ignoreOwner) {
    synchronizeNativeAll();
    SharedUnitCollisionIndex.Footprint current = nativeFootprint(id);
    if (current == null) return true;
    SharedUnitCollisionIndex.Footprint next = current.at(x, y);
    // Retain native pet-owner escape compatibility without rebuilding owner bits.
    int owner = ownedCompanionOwner(id);
    if (ignoreOwner && owner >= 0
        && shared.check(next.zone, x, y, next.pattern, next.size, mask, id, owner) == 0) mask = 0;
    if (!shared.move(id, next, mask)) return false;
    if (hasPresence(id)) grid.put(id, x, y, footprint(id));
    observeHiddenFrogMove(id, new Vector2(x, y));
    return true;
  }

  public boolean hasPresence(int entityId) {
    if (isPlayer(entityId) && mPlayerCorpse.has(entityId)) return false;
    if (mMonster.has(entityId) && mMonster.get(entityId).nativeCorpseCollision) return false;
    if (mCasting.has(entityId) && mCasting.get(entityId).monsterHatchStarted) return false;
    if (!mClass.has(entityId) || !mVelocity.has(entityId)) return false;
    Class.Type type = mClass.get(entityId).type;
    if (type != Class.Type.MON && type != Class.Type.PLR) return false;
    if (type == Class.Type.MON && mMonster.has(entityId)) {
      Monster monster = mMonster.get(entityId);
      if (monster.frogNoPresence || monster.vultureAirborne) return false;
      if (!monster.nativeRestoredCollisionPattern && !isDynamicObstacle(monster)) return false;
      MonStats.Entry stats = monster.monstats;
      if (stats != null) {
        // Resurrection rebuilds from size; it does not repeat allocation's wraith override.
        if (!monster.nativeRestoredCollisionPattern && NativeGroundPathProfile.hasNoPresence(stats)) return false;
      }
    }
    return footprint(entityId) != 0;
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
    int size = mSize.has(entityId) ? mSize.get(entityId).size : 1;
    // #11281: NONE for zero, SMALL_UNIT_PRESENCE for invalid sizes.
    return size >= 0 && size <= 3 ? size : 1;
  }

  /**
   * Applies the unit-type portion of D2's path masks to the authoritative
   * footprint grid. Player paths contain neither MONSTER nor PET; monster
   * paths contain PET but not ordinary MONSTER footprints.
   */
  private UnitCollisionGrid.UnitBlocker blockerFor(int moverId) {
    if (mMonster.has(moverId) && mMonster.get(moverId).vultureAirborne) return other -> false;
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
    if (moverId < 0 || !mMonster.has(moverId)) return NativePathProfile.PLAYER;
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

  private boolean isMountedPair(int id) {
    if (!mMonster.has(id)) return false;
    Monster monster = mMonster.get(id);
    int ownerId = monster.nativeOwnerId;
    if (ownerId < 0 || !mMonster.has(ownerId)
        || monster.nativeOwnerIdentity != null && !monster.nativeOwnerIdentity.isCurrent(world, ownerId)) return false;
    Monster owner = mMonster.get(ownerId);
    if (owner.nativeOwnerId != id || owner.nativeOwnerIdentity != null
        && !owner.nativeOwnerIdentity.isCurrent(world, id)) return false;
    String base = monster.monstats == null ? null : monster.monstats.BaseId;
    String ownerBase = owner.monstats == null ? null : owner.monstats.BaseId;
    return "imp1".equals(base) && isImpMount(ownerBase)
        || "imp1".equals(ownerBase) && isImpMount(base);
  }

  private static boolean isImpMount(String base) {
    return "barricadetower".equals(base) || "siegebeast1".equals(base);
  }
}
