package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.ai.utils.Collision;
import com.badlogic.gdx.ai.utils.Ray;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.Size;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.WhirlwindRuntime;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;

/** Advances native Whirlwind's straight skill path and weapon-speed attack timer. */
@All({WhirlwindRuntime.class, Position.class, Velocity.class, AttributesWrapper.class})
public class WhirlwindSystem extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(WhirlwindSystem.class);
  static final float GAME_FRAMES_PER_SECOND = 25f;
  static final float ARRIVAL_EPSILON = 0.1f;
  static final int MAX_STALLED_FRAMES = 2;

  protected ComponentMapper<WhirlwindRuntime> mWhirlwindRuntime;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<Pathfind> mPathfind;
  protected ComponentMapper<Casting> mCasting;
  protected ComponentMapper<Sequence> mSequence;
  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<Size> mSize;

  protected Actioneer actioneer;

  @Wire(name = "map", failOnNull = false)
  protected Map map;

  private final Vector2 stepEnd = new Vector2();
  private final Ray<Vector2> ray = new Ray<>(new Vector2(), new Vector2());
  private final Collision<Vector2> collision = new Collision<>(new Vector2(), new Vector2());

  /** Resolves D2Common's straight Whirlwind path to its last clear point. */
  static boolean resolveDestination(
      Map map, Vector2 start, Vector2 requested, int unitSize, Vector2 result) {
    if (start == null || requested == null || result == null
        || start.epsilonEquals(requested, ARRIVAL_EPSILON)) return false;
    result.set(requested);
    if (map == null || map.getZone(start) == null) return true;
    Ray<Vector2> path = new Ray<>(new Vector2(start), new Vector2(requested));
    Collision<Vector2> hit = new Collision<>(new Vector2(), new Vector2());
    if (map.castRay(path, DT1.Tile.FLAG_BLOCK_WALK,
        Math.max(Size.INSIGNIFICANT, unitSize), hit)) {
      Vector2 direction = new Vector2(requested).sub(start).nor();
      result.set(hit.point).mulAdd(direction, -0.2f);
    }
    return !start.epsilonEquals(result, ARRIVAL_EPSILON);
  }

  @Override
  protected void process(int entityId) {
    WhirlwindRuntime runtime = mWhirlwindRuntime.get(entityId);
    Position position = mPosition.get(entityId);
    Velocity velocity = mVelocity.get(entityId);
    if (!isAlive(entityId)) {
      finish(entityId, "dead");
      return;
    }
    Casting casting = mCasting.get(entityId);
    if (casting == null || casting.skillId != runtime.skillId) {
      finish(entityId, "casting_removed");
      return;
    }
    if (!hasWhirlwindState(entityId)) {
      finish(entityId, "state_removed");
      return;
    }

    float distance = position.position.dst(runtime.destination);
    if (distance <= ARRIVAL_EPSILON) {
      position.position.set(runtime.destination);
      finish(entityId, "destination_reached");
      return;
    }

    if (runtime.positionInitialized) {
      if (position.position.epsilonEquals(runtime.lastPosition, 0.001f)) {
        runtime.stalledFrames++;
      } else {
        runtime.stalledFrames = 0;
      }
    }
    runtime.lastPosition.set(position.position);
    runtime.positionInitialized = true;
    if (runtime.elapsedFrames > 0f && runtime.stalledFrames >= MAX_STALLED_FRAMES) {
      finish(entityId, "movement_blocked");
      return;
    }

    float baseSpeed = velocity.speed(false);
    if (baseSpeed <= 0f) {
      finish(entityId, "zero_velocity");
      return;
    }
    velocity.velocity.set(runtime.destination).sub(position.position).nor().setLength(baseSpeed);
    float delta = Math.max(0f, world.getDelta());
    float stepDistance = baseSpeed * Math.max(0.0001f, velocity.stateSpeedMultiplier) * delta;
    if (stepDistance >= distance) {
      velocity.velocity.setLength(distance / Math.max(0.0001f,
          delta * Math.max(0.0001f, velocity.stateSpeedMultiplier)));
    }
    if (blocked(entityId, position.position, velocity.velocity, delta,
        velocity.stateSpeedMultiplier)) {
      finish(entityId, "collision");
      return;
    }

    runtime.elapsedFrames += delta * GAME_FRAMES_PER_SECOND;
    int safety = 0;
    while (runtime.elapsedFrames + 0.0001f >= runtime.nextAttackFrame && safety++ < 8) {
      actioneer.resolveWhirlwindPulse(entityId, runtime);
      runtime.nextAttackFrame += actioneer.whirlwindAttackInterval(entityId);
    }
  }

  private boolean blocked(
      int entityId, Vector2 start, Vector2 velocity, float delta, float stateMultiplier) {
    if (map == null || map.getZone(start) == null || velocity.isZero(0.0001f)) return false;
    stepEnd.set(start).mulAdd(velocity, delta * Math.max(0f, stateMultiplier));
    ray.set(start, stepEnd);
    int size = mSize.has(entityId) ? mSize.get(entityId).size : Size.INSIGNIFICANT;
    return map.castRay(ray, DT1.Tile.FLAG_BLOCK_WALK, size, collision);
  }

  private boolean isAlive(int entityId) {
    Attributes attrs = mAttributesWrapper.get(entityId).attrs;
    StatRef hp = attrs != null ? attrs.get(Stat.hitpoints, StatRef.obtain()) : null;
    return hp != null && hp.asFixed() > 0f;
  }

  private boolean hasWhirlwindState(int entityId) {
    if (!mUnitStates.has(entityId)) return false;
    UnitStates states = mUnitStates.get(entityId);
    return states != null && states.stateList != null
        && states.stateList.hasState(StateId.WHIRLWIND);
  }

  void finish(int entityId, String reason) {
    WhirlwindRuntime runtime = mWhirlwindRuntime.get(entityId);
    float destinationX = runtime != null ? runtime.destination.x : 0f;
    float destinationY = runtime != null ? runtime.destination.y : 0f;
    int strikes = runtime != null ? runtime.strikeIndex : 0;
    if (mVelocity.has(entityId)) mVelocity.get(entityId).velocity.setZero();
    if (mPathfind.has(entityId)) mPathfind.remove(entityId);
    if (mUnitStates.has(entityId)) {
      UnitStates states = mUnitStates.get(entityId);
      if (states != null && states.stateList != null) {
        states.stateList.removeState(StateId.WHIRLWIND);
      }
    }
    if (mCasting.has(entityId)) mCasting.remove(entityId);
    if (mSequence.has(entityId)) mSequence.remove(entityId);
    if (mWhirlwindRuntime.has(entityId)) mWhirlwindRuntime.remove(entityId);
    log.info("[WHIRLWIND] phase=finish entity={} reason={} destination=({}, {}) strikes={}",
        entityId, reason, destinationX, destinationY, strikes);
  }
}
