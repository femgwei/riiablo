package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.ai.utils.Collision;
import com.badlogic.gdx.ai.utils.Ray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Pools;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.component.Size;
import com.riiablo.engine.server.component.Target;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.Engine;
import com.riiablo.engine.Direction;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;
import com.riiablo.map.pfa.GraphPath;

import java.util.Iterator;

@All({Pathfind.class, Position.class, Velocity.class})
public class Pathfinder extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(Pathfinder.class);
  static final int MOVEMENT_DIRECTIONS = 16;
  static final int ADJACENT_DIRECTION_STABLE_FRAMES = 3;
  static final float IMMEDIATE_TURN_COS = MathUtils.cosDeg(45f);
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Size> mSize;
  protected ComponentMapper<Pathfind> mPathfind;
  protected ComponentMapper<Angle> mAngle;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<Running> mRunning;
  protected ComponentMapper<Target> mTarget;
  protected ComponentMapper<Class> mClass;
  protected ComponentMapper<Monster> mMonster;

  @Wire(name = "map")
  protected Map map;

  protected Actioneer actioneer;

  private final Vector2 tmpVec2 = new Vector2();
  private final Ray<Vector2> ray = new Ray<>(new Vector2(), new Vector2());
  private final Collision<Vector2> collision = new Collision<>(new Vector2(), new Vector2());

  @Override
  protected void process(int entityId) {
    Vector2 position0 = mPosition.get(entityId).position;
    tmpVec2.set(position0);
    Pathfind pathfind = mPathfind.get(entityId);
    Vector2 target = pathfind.target;
    Iterator<Vector2> targets = pathfind.targets;
    
    // Check if target is a player entity and handle dynamic repathing
    // Use targetEntityId stored in Pathfind component (set when findPath is called with target entity)
    boolean shouldRepath = false;
    int targetId = pathfind.targetEntityId;
    if (targetId != Engine.INVALID_ENTITY && mPosition.has(targetId)) {
      Vector2 targetPos = mPosition.get(targetId).position;
      
      // Check if target is a player
      boolean isPlayerTarget = false;
      if (mClass.has(targetId)) {
        Class.Type targetType = mClass.get(targetId).type;
        isPlayerTarget = (targetType == Class.Type.PLR);
      }
      
      // If target is a player, check if player has moved or repath timer expired
      if (isPlayerTarget) {
        // Check if player has moved significantly (more than 0.5 tiles)
        float moveDistance = pathfind.lastTargetPosition.dst(targetPos);
        if (moveDistance > 0.5f) {
          shouldRepath = true;
          pathfind.lastTargetPosition.set(targetPos);
        }
        
        // Check repath timer
        pathfind.repathTimer -= world.delta;
        if (pathfind.repathTimer <= 0f) {
          shouldRepath = true;
          pathfind.repathTimer = Pathfind.REPATH_INTERVAL;
        }
      } else {
        // Not a player target, reset tracking
        pathfind.targetEntityId = Engine.INVALID_ENTITY;
        pathfind.lastTargetPosition.setZero();
        pathfind.repathTimer = 0f;
      }
      
      // Check if in attack range (melee or ranged)
      float distance = position0.dst(targetPos);
      
      // Check melee range
      int meleeRange = actioneer.getMeleeRange(entityId);
      int rangeBonus = 0; // Default for monsters
      if (mClass.has(entityId)) {
        Class.Type type = mClass.get(entityId).type;
        if (type == Class.Type.PLR) {
          rangeBonus = 3; // D2MOD: 2 * (pAttacker->dwUnitType == UNIT_PLAYER) + 1 = 2 * 1 + 1 = 3
        }
      }
      float meleeRangeThreshold = meleeRange + rangeBonus + 1f;
      
      // Check ranged attack range (if monster has ranged attack capability)
      float rangedRangeThreshold = 0f;
      if (mMonster.has(entityId)) {
        com.riiablo.engine.server.component.Monster monster = mMonster.get(entityId);
        if ((monster.monstats.MissA1 != null && !monster.monstats.MissA1.isEmpty()) ||
            (monster.monstats.MissA2 != null && !monster.monstats.MissA2.isEmpty())) {
          String missileName = null;
          if (monster.monstats.MissA1 != null && !monster.monstats.MissA1.isEmpty()) {
            missileName = monster.monstats.MissA1;
          } else if (monster.monstats.MissA2 != null && !monster.monstats.MissA2.isEmpty()) {
            missileName = monster.monstats.MissA2;
          }
          if (missileName != null) {
            com.riiablo.codec.excel.Missiles.Entry missile = com.riiablo.Riiablo.files.Missiles.get(missileName);
            if (missile != null) {
              rangedRangeThreshold = missile.Range - 2f;
              if (rangedRangeThreshold < meleeRangeThreshold) {
                rangedRangeThreshold = meleeRangeThreshold + 5f;
              }
            }
          }
        }
      }
      
      // If in melee or ranged attack range, stop movement to allow immediate attack
      if (distance <= meleeRangeThreshold || (rangedRangeThreshold > 0f && distance <= rangedRangeThreshold)) {
        findPath(entityId, null);
        return;
      }
      
      // If should repath (player moved or timer expired), recalculate path
      if (shouldRepath) {
        repathPreservingMovementIntent(entityId, targetPos, targetId);
        return;
      }
    }
    
    // D2MOD: Check if path is valid (has path points)
    // Similar to PATH_GetNumberOfPathPoints in D2MOD
    // If target is zero or no more path points, stop movement
    if (target.isZero() || (!targets.hasNext() && tmpVec2.epsilonEquals(target, 0.1f))) {
      // Path is invalid or completed, stop movement
      findPath(entityId, null);
      return;
    }
    
    // Check if reached current target waypoint
    if (tmpVec2.epsilonEquals(target, 0.1f)) {
      if (targets.hasNext()) {
        target.set(targets.next());
      } else {
        // No more path points, stop movement
        findPath(entityId, null);
        return;
      }
    }

    Velocity velocity = mVelocity.get(entityId);
    boolean running = mRunning.has(entityId);
    float speed = velocity.speed(running);
    if (speed <= 0f) {
      log.warn("[MOVEMENT] invalid speed entity={} running={} walkSpeed={} runSpeed={}",
          entityId, running, velocity.walkSpeed, velocity.runSpeed);
      velocity.velocity.setZero();
      return;
    }
    float distance = speed * world.delta;
    float traveled = 0;
    while (traveled < distance) {
      float targetLen = tmpVec2.dst(target);
      float part = Math.min(distance - traveled, targetLen);
      if (part == 0) break;
      tmpVec2.lerp(target, part / targetLen);
      traveled += part;
      if (MathUtils.isEqual(part, targetLen, 0.1f)) {
        if (targets.hasNext()) {
          target.set(targets.next());
        } else {
          // No more path points, stop movement
          findPath(entityId, null);
          return;
        }
      }
    }

    tmpVec2.sub(position0);

    Angle angle = mAngle.get(entityId);
    int oldDirection = Direction.radiansToDirection(
        angle.target.angleRad(), MOVEMENT_DIRECTIONS);
    if (updateMovementFacing(angle, pathfind, tmpVec2) && mMonster.has(entityId)) {
      int newDirection = Direction.radiansToDirection(
          angle.target.angleRad(), MOVEMENT_DIRECTIONS);
      Monster monster = mMonster.get(entityId);
      log.info(
          "[MONSTER_DIRECTION_SYNC] entity={} monster={} direction={}->{} speed={} target={}",
          entityId,
          monster.monstats != null ? monster.monstats.Id : "unknown",
          oldDirection,
          newDirection,
          speed,
          targetId);
    }

    velocity.velocity.set(tmpVec2).setLength(speed);
  }

  /**
   * Applies hysteresis only to neighboring movement facings. Large turns are
   * accepted immediately, while small boundary crossings must remain stable
   * for several ticks. The authoritative movement vector is never modified.
   */
  static boolean updateMovementFacing(Angle angle, Pathfind pathfind, Vector2 movement) {
    if (angle == null || pathfind == null || movement == null || movement.isZero(0.0001f)) {
      return false;
    }

    int proposedDirection = Direction.radiansToDirection(
        movement.angleRad(), MOVEMENT_DIRECTIONS);
    int currentDirection = Direction.radiansToDirection(
        angle.target.angleRad(), MOVEMENT_DIRECTIONS);
    if (proposedDirection == currentDirection) {
      pathfind.pendingDirection = -1;
      pathfind.pendingDirectionFrames = 0;
      return false;
    }

    float alignment = MathUtils.clamp(
        angle.target.dot(movement) / movement.len(), -1f, 1f);
    int requiredFrames = alignment < IMMEDIATE_TURN_COS
        ? 1 : ADJACENT_DIRECTION_STABLE_FRAMES;
    if (pathfind.pendingDirection == proposedDirection) {
      pathfind.pendingDirectionFrames++;
    } else {
      pathfind.pendingDirection = proposedDirection;
      pathfind.pendingDirectionFrames = 1;
    }
    if (pathfind.pendingDirectionFrames < requiredFrames) return false;

    // Keep the authoritative facing aligned with the actual movement vector.
    // Direction.radiansToDirection returns a D2 animation direction id, whose
    // numeric value is not the same as the index in Direction's radians table.
    // Converting that id back through directionToRadians therefore points at
    // a different angle (for example id 9 is not radians-table index 9), which
    // makes AngularVelocity turn the entity sideways while it is moving.
    angle.target.set(movement).nor();
    pathfind.pendingDirection = -1;
    pathfind.pendingDirectionFrames = 0;
    return true;
  }

  public boolean findPath(int src, Vector2 target) {
    return findPath(src, target, false, Engine.INVALID_ENTITY);
  }

  public boolean findPath(int src, Vector2 target, boolean raycast) {
    return findPath(src, target, raycast, Engine.INVALID_ENTITY);
  }

  public boolean findPath(int src, Vector2 target, boolean raycast, int targetEntityId) {
    // Don't allow pathfinding if entity doesn't have Velocity component (e.g., dead player)
    if (!mVelocity.has(src)) {
      return false;
    }

    // A native AITACTICS_SetVelocity bonus belongs to one mode-change
    // request. A new ordinary monster path starts from the native 75% base;
    // AI.moveTo installs any explicit bonus again after path creation.
    if (mMonster.has(src)) {
      mVelocity.get(src).clearModeSpeedBonus();
      mRunning.remove(src);
    }
    if (target == null) {
      mPathfind.remove(src);
      mVelocity.get(src).velocity.setZero();
      return false;
    }

    Vector2 position = mPosition.get(src).position;
    int flags = DT1.Tile.FLAG_BLOCK_WALK;
    int size = mSize.get(src).size;
    GraphPath path = Pools.obtain(GraphPath.class);
    boolean success = findPath(src, position, target, flags, size, path);
    if (success) {
      // Store target entity ID in Pathfind component for dynamic repathing
      if (mPathfind.has(src)) {
        Pathfind pathfind = mPathfind.get(src);
        pathfind.targetEntityId = targetEntityId;
        if (targetEntityId != Engine.INVALID_ENTITY && mPosition.has(targetEntityId)) {
          pathfind.lastTargetPosition.set(mPosition.get(targetEntityId).position);
          pathfind.repathTimer = Pathfind.REPATH_INTERVAL;
        } else {
          pathfind.lastTargetPosition.setZero();
          pathfind.repathTimer = 0f;
        }
      }
      return true;
    }
    if (raycast) {
      ray.set(position, target);
      success = map.castRay(ray, flags, size, collision);
      if (success) {
        success = findPath(src, position, collision.point, flags, size, path);
        if (!success || path.getCount() <= 1) {
          // The ray ended at the blocking boundary and there is no usable
          // path to its last clear point. Moving directly toward the original
          // target here bypasses the collision graph and lets units escape
          // irregular cave/outdoor footprints.
          stopBlockedMovement(mVelocity.get(src));
          mPathfind.remove(src);
          Pools.free(path);
          return false;
        }

        // Store target entity ID in Pathfind component for dynamic repathing
        if (mPathfind.has(src)) {
          Pathfind pathfind = mPathfind.get(src);
          pathfind.targetEntityId = targetEntityId;
          if (targetEntityId != Engine.INVALID_ENTITY && mPosition.has(targetEntityId)) {
            pathfind.lastTargetPosition.set(mPosition.get(targetEntityId).position);
            pathfind.repathTimer = Pathfind.REPATH_INTERVAL;
          } else {
            pathfind.lastTargetPosition.setZero();
            pathfind.repathTimer = 0f;
          }
        }

        return true;
      }

      Pools.free(path);
      return false;
    } else {
      Pools.free(path);
      return false;
    }
  }

  static void stopBlockedMovement(Velocity velocity) {
    if (velocity != null) velocity.velocity.setZero();
  }

  private boolean repathPreservingMovementIntent(
      int entityId, Vector2 target, int targetEntityId) {
    Velocity velocity = mVelocity.get(entityId);
    float bonus = velocity.modeSpeedBonusMultiplier;
    boolean running = mRunning.has(entityId);
    boolean success = findPath(entityId, target, false, targetEntityId);
    if (success) {
      velocity.modeSpeedBonusMultiplier = bonus;
      if (running) mRunning.create(entityId);
    }
    return success;
  }

  protected boolean findPath(int src, Vector2 srcPos, Vector2 targetPos, int flags, int size, GraphPath path) {
    boolean success = map.findPath(srcPos, targetPos, flags, size, path);
    if (success) {
      map.smoothPath(flags, size, path);
      mPathfind.create(src).set(path);
    }

    return success;
  }
}
