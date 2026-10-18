package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.ai.utils.Collision;
import com.badlogic.gdx.ai.utils.Ray;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.Size;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;
import com.d2moo.common.path.D2PathStep;

@All({Position.class, Velocity.class})
public class VelocityAdder extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(VelocityAdder.class);

  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<Missile> mMissile;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Size> mSize;
  protected ComponentMapper<Pathfind> mPathfind;
  protected ComponentMapper<com.riiablo.engine.server.component.WhirlwindRuntime> mWhirlwindRuntime;
  protected ComponentMapper<com.riiablo.engine.server.component.Leap> mLeap;
  protected ComponentMapper<com.riiablo.engine.server.component.Casting> mCasting;
  protected ComponentMapper<com.riiablo.engine.server.component.Angle> mAngle;
  private final boolean nativeOnly;

  public VelocityAdder() { this(false); }

  /** Local physics worlds install only the native route; replicas install neither. */
  public VelocityAdder(boolean nativeOnly) { this.nativeOnly = nativeOnly; }

  @Wire(failOnNull = false)
  protected DynamicUnitCollisionSystem dynamicCollision;

  @Wire(name = "map", failOnNull = false)
  protected Map map;

  private final Vector2 desired = new Vector2();
  private final Vector2 candidate = new Vector2();
  private final Vector2 start = new Vector2();
  private final Ray<Vector2> ray = new Ray<>(new Vector2(), new Vector2());
  private final Collision<Vector2> collision = new Collision<>(new Vector2(), new Vector2());
  private String blockReason;

  @Override
  protected void process(int entityId) {
    // MissileCollisionSystem performs swept movement and collision resolution
    // in one place. Moving missiles here as well would advance them twice.
    if (mMissile.has(entityId)) return;
    Velocity velocity = mVelocity.get(entityId);
    velocity.nativeMovementOwned = false;
    if (moveNativeMonster(entityId, velocity) || nativeOnly) return;
    if (velocity.stateMovementLocked) return;

    Position position = mPosition.get(entityId);
    start.set(position.position);
    boolean requested = !velocity.velocity.isZero(0.001f);
    blockReason = "none";
    float scale = world.delta * velocity.movementMultiplier();
    desired.set(position.position).mulAdd(velocity.velocity, scale);
    if (canMove(entityId, position.position, desired)) {
      position.position.set(desired);
      recordMovement(entityId, requested);
      return;
    }

    // D2Common's unit collision resolver attempts axis-preserving movement
    // after a diagonal move is blocked. This lets units slide along walls.
    boolean moved = false;
    float dx = desired.x - position.position.x;
    float dy = desired.y - position.position.y;
    if (Math.abs(dx) >= Math.abs(dy)) {
      candidate.set(position.position.x + dx, position.position.y);
      if (canMove(entityId, position.position, candidate)) {
        position.position.set(candidate);
        moved = true;
      }
      candidate.set(position.position.x, position.position.y + dy);
      if (canMove(entityId, position.position, candidate)) {
        position.position.set(candidate);
        moved = true;
      }
    } else {
      candidate.set(position.position.x, position.position.y + dy);
      if (canMove(entityId, position.position, candidate)) {
        position.position.set(candidate);
        moved = true;
      }
      candidate.set(position.position.x + dx, position.position.y);
      if (canMove(entityId, position.position, candidate)) {
        position.position.set(candidate);
        moved = true;
      }
    }
    if (!moved) velocity.velocity.setZero();
    recordMovement(entityId, requested);
    log.debug("[MOVEMENT_COLLISION] entity={} from=({}, {}) desired=({}, {}) result=({}, {}) slid={} blocked={}",
        entityId, start.x, start.y, desired.x, desired.y,
        position.position.x, position.position.y, moved, !moved);
  }

  private boolean moveNativeMonster(int id, Velocity velocity) {
    Position position = mPosition.get(id);
    if (!mMonster.has(id) || velocity.nativeMonsterBaseVelocity < 0 || !position.hasNativePrecision()
        || Math.abs(world.delta - com.riiablo.engine.SimulationClock.STEP_SECONDS) > 0.000001f
        || velocity.stateSpeedMultiplier != 1f || mLeap.has(id) || mWhirlwindRuntime.has(id)) return false;
    com.riiablo.engine.server.component.Casting cast = mCasting.get(id);
    if (cast != null && cast.diabRun != null) return false; // skill-owned speed, not MonStats
    boolean requested = !velocity.velocity.isZero(0.001f);
    Pathfind path = mPathfind.get(id);
    if (requested && (path == null || !path.hasNativeWaypoint())) return false;
    int nativeVelocity;
    try { nativeVelocity = velocity.nativeMonsterVelocity(); }
    catch (IllegalArgumentException unsupported) { return false; }
    velocity.nativeMovementOwned = true;
    if (!requested || velocity.stateMovementLocked) return true;
    start.set(position.position);
    blockReason = "none";
    int targetX = D2PathStep.toFp16Center(path.nativeWaypointX());
    int targetY = D2PathStep.toFp16Center(path.nativeWaypointY());
    D2PathStep.DirectionVector direction = D2PathStep.directionVector(
        position.nativePrecisionX(), position.nativePrecisionY(), targetX, targetY);
    D2PathStep.StepResult result = position.stepNativeUnit((direction.x * nativeVelocity) >> 8,
        (direction.y * nativeVelocity) >> 8, targetX, targetY, (x, y, nx, ny) ->
            canMove(id, candidate.set(x, y), desired.set(nx, ny)));
    recordMovement(id, true);
    // Requested velocity is rebuilt by Pathfinder next tick. Expose actual
    // displacement to mode/facing consumers and never feed it through physics.
    velocity.velocity.set(position.position).sub(start).scl(1f / world.delta);
    if (result.blocked && result.crossedCells == 0) velocity.velocity.setZero();
    if (mAngle.has(id) && !velocity.velocity.isZero())
      mAngle.get(id).target.set(velocity.velocity).nor();
    MapWrapper wrapper = mMapWrapper.get(id);
    if (wrapper != null && wrapper.zone != null && !wrapper.roomIdPinned) {
      Map.RoomEx room = wrapper.zone.findRoomEx(position.position.x, position.position.y);
      wrapper.roomId = room == null ? -1 : room.id;
    }
    return true;
  }

  private void recordMovement(int entityId, boolean requested) {
    Position position = mPosition.get(entityId);
    // A legacy floating-point displacement is not a native fp16 step. Latch
    // invalidation now, even if a later tick returns to the old projection.
    if (!mVelocity.get(entityId).nativeMovementOwned && !start.equals(position.position))
      position.invalidateNativePrecision();
    if (!mPathfind.has(entityId)) return;
    Pathfind path = mPathfind.get(entityId);
    path.recordMovement(requested, start.dst2(mPosition.get(entityId).position),
        world.delta, "dynamic".equals(blockReason));
    path.movementBlockReason = path.stalledTime > 0f ? blockReason : "none";
  }

  private boolean dynamicMove(int entityId, Vector2 to) {
    if (dynamicCollision == null || dynamicCollision.tryMove(entityId, to)) return true;
    blockReason = "dynamic";
    return false;
  }

  private boolean canMove(int entityId, Vector2 from, Vector2 to) {
    if (from.epsilonEquals(to, 0.0001f)) return true;
    MapWrapper wrapper = mMapWrapper.has(entityId) ? mMapWrapper.get(entityId) : null;
    if (wrapper != null && wrapper.zone != null && wrapper.zone.hasNativeRoomTopology()) {
      Map.RoomEx source = wrapper.zone.findRoomEx(from.x, from.y);
      Map.RoomEx target = wrapper.zone.findRoomEx(to.x, to.y);
      // RoomEx rectangles may leave a one-subtile seam. Let the collision
      // grid decide seam traversal, but never permit a direct jump between
      // two non-adjacent exported rooms.
      if (source != null && target != null
          && !wrapper.zone.areRoomsAdjacent(from.x, from.y, to.x, to.y)) {
        log.debug("[ROOM_MOVEMENT_BOUNDARY] entity={} fromRoom={} toRoom={} action=reject",
            entityId, source.id, target.id);
        blockReason = "room:" + source.id + "->" + target.id;
        return false;
      }
    }
    // Some headless/unit worlds intentionally omit generated zones. Preserve
    // the legacy velocity behaviour there; generated maps use the authoritative
    // collision grid below.
    if (map == null || map.getZone(from) == null) {
      return dynamicMove(entityId, to);
    }
    // Synthetic skill-viewer arenas intentionally have no DT1 collision map;
    // their zeroed walk grid is fully traversable. Do not let the empty
    // raycaster reject every movement step.
    if (wrapper != null && wrapper.zone != null && wrapper.zone.isSyntheticArena()) {
      return dynamicMove(entityId, to);
    }
    ray.set(from, to);
    int size = mSize.has(entityId) ? mSize.get(entityId).size : Size.INSIGNIFICANT;
    if (mWhirlwindRuntime.has(entityId)) {
      if (WhirlwindCollision.cast(map, from, to, size, false, collision.point)) {
        blockReason = "terrain";
        return false;
      }
      return dynamicMove(entityId, to);
    }
    Monster monster = mMonster.has(entityId) ? mMonster.get(entityId) : null;
    if (monster != null && monster.vultureAirborne) return dynamicMove(entityId, to);
    int flags = NativeGroundPathProfile.forUnit(mPlayer.has(entityId),
        monster == null ? null : monster.monstats).terrainFlags;
    // Keep the authoritative movement ray consistent with Pathfinder: native
    // door-opening monsters may traverse a door even though the world fixture
    // is present. Flying units use the missile-barrier profile and never open
    // doors.
    boolean canOpenDoors = monster != null && monster.monstats != null
        && monster.monstats.opendoors
        && NativeGroundPathProfile.forUnit(false, monster.monstats)
            != NativeGroundPathProfile.FLYING;
    if (map.castRay(wrapper == null ? null : wrapper.zone,
        ray, flags, size, collision, canOpenDoors)) {
      blockReason = "terrain";
      return false;
    }
    return dynamicMove(entityId, to);
  }
}
