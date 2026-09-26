package com.riiablo.engine.server;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntMap;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Box2DBody;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.Size;
import com.riiablo.engine.server.component.Target;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.combat.CombatPositionHistory;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;

/** Native hireling owner-follow and cross-zone relocation lifecycle. */
@Wire(failOnNull = false)
public final class MercenaryFollowSystem extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(MercenaryFollowSystem.class);

  static final float FOLLOW_DISTANCE = 24f;
  static final float SETTLE_DISTANCE = 16f;
  static final float TELEPORT_DISTANCE = 100f;
  private static final float REPATH_SECONDS = 0.5f;

  static final int MOTION_NONE = 0;
  static final int MOTION_FOLLOW = 1;
  static final int MOTION_SETTLE = 2;
  static final int MOTION_TELEPORT = 3;

  protected ComponentMapper<Mercenary> mMercenary;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<AttributesWrapper> mAttributes;
  protected ComponentMapper<Corpse> mCorpse;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<Running> mRunning;
  protected ComponentMapper<Pathfind> mPathfind;
  protected ComponentMapper<Target> mTarget;
  protected ComponentMapper<Casting> mCasting;
  protected ComponentMapper<Sequence> mSequence;
  protected ComponentMapper<Box2DBody> mBox2DBody;
  protected ComponentMapper<Size> mSize;
  protected ComponentMapper<UnitStates> mUnitStates;
  protected Actioneer actioneer;
  @Wire(name = "combatPositionHistory", failOnNull = false)
  protected CombatPositionHistory positionHistory;

  private final IntMap<Float> repathCooldown = new IntMap<>();
  private final Vector2 landing = new Vector2();
  private volatile int teleportCount;
  private volatile int followCount;
  private volatile int lastMercenary = Engine.INVALID_ENTITY;
  private volatile int lastOwner = Engine.INVALID_ENTITY;

  public MercenaryFollowSystem() {
    super(Aspect.all(Mercenary.class, Position.class, MapWrapper.class));
  }

  @Override
  protected void process(int entityId) {
    Mercenary mercenary = mMercenary.get(entityId);
    int ownerId = mercenary.ownerId;
    if (ownerId == Engine.INVALID_ENTITY || !mPosition.has(ownerId)
        || !mMapWrapper.has(ownerId)) return;

    MapWrapper ownerWrapper = mMapWrapper.get(ownerId);
    MapWrapper mercenaryWrapper = mMapWrapper.get(entityId);
    Map map = ownerWrapper.map != null ? ownerWrapper.map : mercenaryWrapper.map;
    if (map == null) return;
    Vector2 ownerPosition = mPosition.get(ownerId).position;
    Vector2 mercenaryPosition = mPosition.get(entityId).position;
    Map.Zone ownerZone = map.getZone(ownerPosition);
    Map.Zone mercenaryZone = map.getZone(mercenaryPosition);
    if (ownerZone == null) return;

    // UnitCollisionGrid stores a square footprint for each unit (size 2 has
    // cells at center +/- 1).  A hireling spawned only two cells from a
    // medium player therefore still overlaps the player's starting cell even
    // though their center coordinates differ.  Once that happens the
    // pathfinder treats the player's source cell as dynamically occupied and
    // every ground path can fail.  Repair old saves as well as newly restored
    // hirelings before normal follow logic runs.
    int ownerFootprint = footprint(ownerId);
    int mercenaryFootprint = footprint(entityId);
    float distance = nativeFollowDistance(
        mercenaryPosition, ownerPosition, mercenaryFootprint);
    if (!isDead(entityId) && footprintsOverlap(ownerPosition,
        mercenaryPosition, ownerFootprint, mercenaryFootprint)) {
      if (findLanding(map, ownerZone, ownerPosition, mercenaryFootprint,
          ownerFootprint, landing)) {
        if (actioneer != null && actioneer.canInterrupt(entityId)
            && actioneer.tryRunTo(entityId, landing, 60)) {
          repathCooldown.put(entityId, REPATH_SECONDS);
          log.debug("[MERC_FOLLOW] phase=avoid_owner merc={} owner={} from=({}, {}) to=({}, {})",
              entityId, ownerId, mercenaryPosition.x, mercenaryPosition.y,
              landing.x, landing.y);
        }
      } else {
        log.warn("[MERC_FOLLOW] phase=separation_reject merc={} owner={} "
                + "ownerPos=({}, {}) mercPos=({}, {}) ownerSize={} mercSize={} "
                + "reason=no_walkable_landing",
            entityId, ownerId, ownerPosition.x, ownerPosition.y,
            mercenaryPosition.x, mercenaryPosition.y, ownerFootprint,
            mercenaryFootprint);
      }
      return;
    }

    boolean sameMap = ownerWrapper.map == mercenaryWrapper.map;
    boolean sameZone = sameMap && ownerZone == mercenaryZone;
    boolean dead = isDead(entityId);
    // A zone/room transition inside one map is still pathable in native D2;
    // do not turn it into an unconditional warp.  The old sameZone overload
    // remains for focused compatibility tests, while the live path uses the
    // map-aware rule.
    // A dead hireling is a corpse, not a travelling pet.  It must remain at
    // its death coordinates until the owner leaves the level; the zone-change
    // lifecycle then unloads the corpse instead of carrying it through a warp.
    if (dead) return;
    int motion = motion(sameMap, sameZone, distance, false, true);
    // Native hirelings also follow inside the 16..24 band while the owner is
    // actively walking/running; otherwise they wait until the outer leash.
    if (motion == MOTION_NONE && !dead && distance > SETTLE_DISTANCE
        && mVelocity.has(ownerId) && !mVelocity.get(ownerId).velocity.isZero(0.001f)) {
      motion = MOTION_FOLLOW;
    }
    if (motion == MOTION_TELEPORT) {
      int footprint = footprint(entityId);
      if (!findLanding(map, ownerZone, ownerPosition, footprint,
          footprint(ownerId), landing)) {
        log.warn("[MERC_FOLLOW] phase=teleport_reject merc={} owner={} level={} "
                + "ownerPos=({}, {}) reason=no_walkable_landing",
            entityId, ownerId, ownerZone.level != null ? ownerZone.level.Id : -1,
            ownerPosition.x, ownerPosition.y);
        return;
      }
      teleport(entityId, ownerId, map, ownerZone, landing, distance, dead);
      return;
    }
    if (actioneer == null) return;

    // A combat chase installs the hostile entity as Target while the
    // hireling is outside its skill range.  Do not replace that target with
    // the owner on the next follow tick; doing so made a hireling oscillate
    // between the player and the monster and, in practice, never attack.
    if (isCombatTarget(entityId, ownerId)) return;

    if (motion == MOTION_FOLLOW) {
      // Native Hireable AI does not rethink while its walk/run path is still
      // active. Replacing the route every 0.5 seconds made the authoritative
      // velocity and animation direction visibly stutter.
      if (mPathfind.has(entityId)) return;
      float remaining = repathCooldown.get(entityId, 0f) - Math.max(0f, world.getDelta());
      if (remaining > 0f) {
        repathCooldown.put(entityId, remaining);
        return;
      }
      if (actioneer.canInterrupt(entityId)) {
        boolean run = distance > FOLLOW_DISTANCE || mRunning.has(ownerId);
        // D2GAME_PETAI_PetMove motion 1 follows one of the owner's recent 20
        // path coordinates instead of pathing into the owner's occupied
        // centre. This lets a hireling finish the selected route and regroup
        // close to the player; 16/24 remain AI trigger bands, not stop ranges.
        boolean pathStarted = tryOwnerTrail(entityId, ownerId, ownerZone,
            mercenaryPosition, mercenaryFootprint, run);
        if (!pathStarted) {
          int mercFootprint = footprint(entityId);
          if (findLanding(map, ownerZone, ownerPosition, mercFootprint,
              footprint(ownerId), landing)) {
            pathStarted = run
                ? actioneer.tryRunTo(entityId, landing, 60)
                : actioneer.tryMoveTo(entityId, landing);
          }
          if (!pathStarted) {
            if (findLanding(map, ownerZone, ownerPosition, mercFootprint,
                footprint(ownerId), landing)) {
              teleport(entityId, ownerId, map, ownerZone, landing, distance, false);
            } else {
              log.debug("[MERC_FOLLOW] phase=path_failed_no_landing merc={} owner={} distance={}",
                  entityId, ownerId, distance);
            }
          }
        }
        if (pathStarted) {
          repathCooldown.put(entityId, REPATH_SECONDS);
          followCount++;
          lastMercenary = entityId;
          lastOwner = ownerId;
          log.debug("[MERC_FOLLOW] phase=path merc={} owner={} distance={} level={}",
              entityId, ownerId, distance,
              ownerZone.level != null ? ownerZone.level.Id : -1);
        }
      }
    }
  }

  private boolean tryOwnerTrail(int entityId, int ownerId, Map.Zone ownerZone,
      Vector2 mercenaryPosition, int mercenaryFootprint, boolean run) {
    if (positionHistory == null) return false;
    int frames = Math.min(20, positionHistory.capacity());
    for (int age = 0; age < frames; age++) {
      CombatPositionHistory.Snapshot snapshot =
          positionHistory.recentSnapshot(ownerId, age);
      if (snapshot == null || snapshot.zone != null && snapshot.zone != ownerZone) continue;
      landing.set(snapshot.x, snapshot.y);
      // PetMove ignores trail coordinates at most five native cells from the
      // hireling and keeps searching backward through the ring buffer.
      if (nativeFollowDistance(mercenaryPosition, landing, mercenaryFootprint) <= 5f) {
        continue;
      }
      boolean started = run
          ? actioneer.tryRunTo(entityId, landing, 60)
          : actioneer.tryMoveTo(entityId, landing);
      if (started) return true;
    }
    return false;
  }

  @Override
  protected void removed(int entityId) {
    repathCooldown.remove(entityId);
  }

  private boolean isDead(int entityId) {
    if (mCorpse.has(entityId)) return true;
    if (!mAttributes.has(entityId) || mAttributes.get(entityId).attrs == null) return false;
    StatRef life = mAttributes.get(entityId).attrs.get(Stat.hitpoints, StatRef.obtain());
    return life != null && life.asFixed() <= 0f;
  }

  private boolean isCombatTarget(int entityId, int ownerId) {
    if (!mTarget.has(entityId)) return false;
    int targetId = mTarget.get(entityId).target;
    if (targetId == Engine.INVALID_ENTITY || targetId == ownerId
        || !mPosition.has(targetId)) return false;
    // A Target component can survive a completed interaction on legacy saves;
    // only treat a living, non-owner target as a combat chase.  The target's
    // actual hostility is validated by MercenarySkillSystem.
    return !isDead(targetId);
  }

  private int footprint(int entityId) {
    return mSize.has(entityId) ? Math.max(1, mSize.get(entityId).size) : 1;
  }

  public static boolean footprintsOverlap(Vector2 first, Vector2 second,
      int firstSize, int secondSize) {
    if (first == null || second == null) return false;
    int firstRadius = Math.max(0, Math.max(1, firstSize) - 1);
    int secondRadius = Math.max(0, Math.max(1, secondSize) - 1);
    // DynamicUnitCollisionSystem indexes positions with Map.round(...), so
    // use the same cells here.  Comparing raw floats would miss an overlap
    // such as owner=10.6 (cell 11), merc=13.0 (cell 13).
    int firstX = Map.round(first.x);
    int firstY = Map.round(first.y);
    int secondX = Map.round(second.x);
    int secondY = Map.round(second.y);
    return Math.abs(firstX - secondX) <= firstRadius + secondRadius
        && Math.abs(firstY - secondY) <= firstRadius + secondRadius;
  }

  /** Exact projection of D2Game AIUTIL_GetDistanceToCoordinates_FullUnitSize. */
  static float nativeFollowDistance(Vector2 unit, Vector2 target, int unitSize) {
    if (unit == null || target == null) return Float.MAX_VALUE;
    int dx = Math.abs(Math.abs(Map.round(unit.x) - Map.round(target.x))
        - Math.max(0, unitSize));
    int dy = Math.abs(Math.abs(Map.round(unit.y) - Map.round(target.y))
        - Math.max(0, unitSize));
    int major = Math.max(dx, dy);
    int minor = Math.min(dx, dy);
    return (minor + 2 * major) / 2;
  }

  private void teleport(int entityId, int ownerId, Map map, Map.Zone ownerZone,
      Vector2 destination, float oldDistance, boolean dead) {
    Vector2 position = mPosition.get(entityId).position;
    float fromX = position.x;
    float fromY = position.y;
    if (mPathfind.has(entityId)) mPathfind.remove(entityId);
    if (mTarget.has(entityId)) mTarget.remove(entityId);
    if (mCasting.has(entityId)) mCasting.remove(entityId);
    if (mSequence.has(entityId)) mSequence.remove(entityId);
    if (mVelocity.has(entityId)) mVelocity.get(entityId).velocity.setZero();
    position.set(destination);

    MapWrapper wrapper = mMapWrapper.get(entityId);
    wrapper.set(map, ownerZone);
    Map.RoomEx room = ownerZone.findRoomEx(destination.x, destination.y);
    wrapper.roomId = room != null ? room.id : -1;
    if (mBox2DBody.has(entityId) && mBox2DBody.get(entityId).body != null) {
      mBox2DBody.get(entityId).body.setTransform(destination, 0f);
      mBox2DBody.get(entityId).body.setLinearVelocity(0f, 0f);
    }
    if (mUnitStates.has(entityId)) {
      UnitStates states = mUnitStates.get(entityId);
      if (states.stateList == null) states.init(entityId);
      states.stateList.addState(StateId.SYNC_WARPED, 2, 1, entityId);
    }
    repathCooldown.remove(entityId);
    teleportCount++;
    lastMercenary = entityId;
    lastOwner = ownerId;
    log.info("[MERC_FOLLOW] phase=teleport merc={} owner={} from=({}, {}) to=({}, {}) "
            + "distance={} level={} room={} dead={}",
        entityId, ownerId, fromX, fromY, destination.x, destination.y, oldDistance,
        ownerZone.level != null ? ownerZone.level.Id : -1, wrapper.roomId, dead);
  }

  static int motion(boolean sameZone, float distance, boolean dead) {
    if (dead) return MOTION_NONE;
    if (!sameZone || distance > TELEPORT_DISTANCE) return MOTION_TELEPORT;
    if (distance > FOLLOW_DISTANCE) return MOTION_FOLLOW;
    if (distance <= SETTLE_DISTANCE) return MOTION_SETTLE;
    return MOTION_NONE;
  }

  /**
   * Native pet movement does not warp merely because two active rooms differ.
   * It attempts WalkToOwner first; a teleport is reserved for a different
   * level/map or a genuinely unreachable/very distant hireling.
   */
  static int motion(boolean sameMap, boolean sameZone, float distance, boolean dead,
      boolean pathAvailable) {
    if (dead) return MOTION_NONE;
    if (!sameMap || distance > TELEPORT_DISTANCE) return MOTION_TELEPORT;
    if (!pathAvailable && distance > FOLLOW_DISTANCE) return MOTION_TELEPORT;
    if (distance > FOLLOW_DISTANCE) return MOTION_FOLLOW;
    if (distance <= SETTLE_DISTANCE) return MOTION_SETTLE;
    return MOTION_NONE;
  }

  public static boolean findLanding(Map map, Map.Zone zone, Vector2 owner, int footprint, Vector2 out) {
    return findLanding(map, zone, owner, footprint, 1, out);
  }

  /** Finds a static-walkable hireling landing point outside both unit footprints. */
  public static boolean findLanding(Map map, Map.Zone zone, Vector2 owner,
      int footprint, int ownerFootprint, Vector2 out) {
    if (map == null || zone == null || owner == null || out == null) return false;
    return findLanding(owner, out, (x, y) -> {
      if (map.getZone(x, y) != zone) return false;
      if (zone.hasNativeRoomTopology() && zone.findRoomEx(x, y) == null) return false;
      int radius = Math.max(0, footprint - 1);
      for (int dy = -radius; dy <= radius; dy++) {
        for (int dx = -radius; dx <= radius; dx++) {
          if ((map.flags(x + dx, y + dy) & DT1.Tile.FLAG_BLOCK_WALK) != 0) return false;
        }
      }
      return true;
    }, Math.max(2, Math.max(1, footprint) + Math.max(1, ownerFootprint) - 1));
  }

  static boolean findLanding(Vector2 owner, Vector2 out, LandingValidator validator) {
    return findLanding(owner, out, validator, 2);
  }

  private static boolean findLanding(Vector2 owner, Vector2 out,
      LandingValidator validator, int minimumRadius) {
    if (owner == null || out == null || validator == null) return false;
    int centerX = MathUtils.round(owner.x);
    int centerY = MathUtils.round(owner.y);
    // Native PetMove motion 3 selects an unoccupied point around the owner;
    // begin outside the owner's own collision footprint.
    for (int radius = Math.max(2, minimumRadius); radius <= 8; radius++) {
      for (int dy = -radius; dy <= radius; dy++) {
        for (int dx = -radius; dx <= radius; dx++) {
          if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) continue;
          int x = centerX + dx;
          int y = centerY + dy;
          if (validator.isValid(x, y)) {
            out.set(x, y);
            return true;
          }
        }
      }
    }
    return false;
  }

  interface LandingValidator {
    boolean isValid(int x, int y);
  }

  public int teleportCount() { return teleportCount; }
  public int followCount() { return followCount; }
  public int lastMercenary() { return lastMercenary; }
  public int lastOwner() { return lastOwner; }
}
