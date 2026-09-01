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
  protected ComponentMapper<Pathfind> mPathfind;
  protected ComponentMapper<Target> mTarget;
  protected ComponentMapper<Casting> mCasting;
  protected ComponentMapper<Sequence> mSequence;
  protected ComponentMapper<Box2DBody> mBox2DBody;
  protected ComponentMapper<Size> mSize;
  protected ComponentMapper<UnitStates> mUnitStates;
  protected Actioneer actioneer;

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

    boolean sameZone = ownerWrapper.map == mercenaryWrapper.map
        && ownerZone == mercenaryZone;
    boolean dead = isDead(entityId);
    float distance = mercenaryPosition.dst(ownerPosition);
    int motion = motion(sameZone, distance, dead);
    if (motion == MOTION_TELEPORT) {
      int footprint = mSize.has(entityId) ? Math.max(1, mSize.get(entityId).size) : 1;
      if (!findLanding(map, ownerZone, ownerPosition, footprint, landing)) {
        log.warn("[MERC_FOLLOW] phase=teleport_reject merc={} owner={} level={} "
                + "ownerPos=({}, {}) reason=no_walkable_landing",
            entityId, ownerId, ownerZone.level != null ? ownerZone.level.Id : -1,
            ownerPosition.x, ownerPosition.y);
        return;
      }
      teleport(entityId, ownerId, map, ownerZone, landing, distance, dead);
      return;
    }
    if (dead || actioneer == null) return;

    if (motion == MOTION_FOLLOW) {
      float remaining = repathCooldown.get(entityId, 0f) - Math.max(0f, world.getDelta());
      if (remaining > 0f) {
        repathCooldown.put(entityId, remaining);
        return;
      }
      if (actioneer.canInterrupt(entityId)) {
        actioneer.moveTo(entityId, ownerId);
        repathCooldown.put(entityId, REPATH_SECONDS);
        followCount++;
        lastMercenary = entityId;
        lastOwner = ownerId;
        log.debug("[MERC_FOLLOW] phase=path merc={} owner={} distance={} level={}",
            entityId, ownerId, distance,
            ownerZone.level != null ? ownerZone.level.Id : -1);
      }
    } else if (motion == MOTION_SETTLE && mTarget.has(entityId)
        && mTarget.get(entityId).target == ownerId) {
      actioneer.moveTo(entityId, Engine.INVALID_ENTITY);
      repathCooldown.remove(entityId);
    }
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
    if (!sameZone || distance > TELEPORT_DISTANCE) return MOTION_TELEPORT;
    if (dead) return MOTION_NONE;
    if (distance > FOLLOW_DISTANCE) return MOTION_FOLLOW;
    if (distance <= SETTLE_DISTANCE) return MOTION_SETTLE;
    return MOTION_NONE;
  }

  static boolean findLanding(Map map, Map.Zone zone, Vector2 owner, int footprint, Vector2 out) {
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
    });
  }

  static boolean findLanding(Vector2 owner, Vector2 out, LandingValidator validator) {
    if (owner == null || out == null || validator == null) return false;
    int centerX = MathUtils.round(owner.x);
    int centerY = MathUtils.round(owner.y);
    // Native PetMove motion 3 selects an unoccupied point around the owner;
    // begin outside the owner's own collision footprint.
    for (int radius = 2; radius <= 8; radius++) {
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
