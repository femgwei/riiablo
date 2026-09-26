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
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.component.TemporaryRunning;
import com.riiablo.engine.server.component.Size;
import com.riiablo.engine.server.component.Target;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.Engine;
import com.riiablo.engine.Direction;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;
import com.riiablo.map.pfa.GraphPath;

import java.util.Iterator;

@Wire(failOnNull = false)
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
  protected ComponentMapper<AttributesWrapper> mAttributes;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<Running> mRunning;
  protected ComponentMapper<TemporaryRunning> mTemporaryRunning;
  protected ComponentMapper<Target> mTarget;
  protected ComponentMapper<Interactable> mInteractable;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Mercenary> mMercenary;
  protected ComponentMapper<SummonedPet> mSummonedPet;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  /**
   * Optional for focused headless tests and legacy worlds.  The movement
   * queries below already treat a missing dynamic-collision service as an
   * empty dynamic layer; gameplay worlds register the full service.
   */
  @Wire(failOnNull = false)
  protected DynamicUnitCollisionSystem dynamicCollision;

  @Wire(name = "map")
  protected Map map;

  protected Actioneer actioneer;

  private final Vector2 tmpVec2 = new Vector2();
  private final Ray<Vector2> ray = new Ray<>(new Vector2(), new Vector2());
  private final Collision<Vector2> collision = new Collision<>(new Vector2(), new Vector2());
  private final Ray<Vector2> smoothRay = new Ray<>(new Vector2(), new Vector2());
  private final Vector2 smoothStart = new Vector2();
  private final Vector2 smoothEnd = new Vector2();

  @Override
  protected void process(int entityId) {
    Vector2 position0 = mPosition.get(entityId).position;
    tmpVec2.set(position0);
    Pathfind pathfind = mPathfind.get(entityId);
    Vector2 target = pathfind.target;
    Iterator<Vector2> targets = pathfind.targets;
    
    // Entity-target commands must follow every moving unit, not only players.
    // Networked players commonly chase monsters; treating a monster as a
    // one-time location makes the server stop at its stale click position.
    boolean shouldRepath = false;
    int targetId = pathfind.targetEntityId;
    if (targetId != Engine.INVALID_ENTITY && mPosition.has(targetId)) {
      Vector2 targetPos = mPosition.get(targetId).position;
      boolean dynamicTarget = mVelocity.has(targetId);
      if (dynamicTarget) {
        float moveDistance = pathfind.lastTargetPosition.dst(targetPos);
        if (moveDistance > 0.5f) {
          shouldRepath = true;
          pathfind.lastTargetPosition.set(targetPos);
        }

        pathfind.repathTimer -= world.delta;
        if (pathfind.repathTimer <= 0f) {
          shouldRepath = true;
          pathfind.repathTimer = Pathfind.REPATH_INTERVAL;
        }
      } else {
        // Static objects still remain entity targets for range/interaction,
        // but do not need periodic path recomputation.
        pathfind.lastTargetPosition.set(targetPos);
        pathfind.repathTimer = 0f;
      }
      
      // Check if in attack range (melee or ranged)
      float distance = position0.dst(targetPos);
      
      // Chase until the same footprint-aware range used to start the attack.
      // The player's +3 server hit allowance belongs to keyframe validation;
      // using it here stops the path before the client may begin attacking.
      boolean inMeleeApproachRange = isInMeleeApproachRange(
          actioneer, entityId, targetId);
      float meleeRangeThreshold = actioneer.getMeleeRange(entityId) + 1f;
      
      // Check ranged attack range (if monster has ranged attack capability)
      float rangedRangeThreshold = 0f;
      // A hireling follows its owner through the same target-entity path used
      // for hostile pursuit.  Do not apply the hireling's MissA1/MissA2 range
      // to that friendly target: doing so made an A1 rogue stop roughly one
      // missile range away from the player instead of regrouping at the
      // native owner-follow distance.  The ranged stop remains valid for
      // actual hostile targets.
      boolean ownerFollowTarget = mMercenary.has(entityId)
          && isMercenaryOwnerTarget(mMercenary.get(entityId), targetId);
      if (!ownerFollowTarget && mSummonedPet.has(entityId)) {
        ownerFollowTarget = isValkyrieOwnerTarget(mSummonedPet.get(entityId), targetId);
      }
      if (!ownerFollowTarget && mMonster.has(entityId)) {
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
      
      // Interaction movement and activation share one footprint-aware range.
      // Applying the melee stop threshold here can leave an NPC or object
      // target pending forever just outside its activation boundary.
      boolean interactionTarget = targetId != Engine.INVALID_ENTITY
          && mInteractable.has(targetId);
      Interactable interactable = interactionTarget ? mInteractable.get(targetId) : null;
      if ((interactionTarget
              && InteractionRange.contains(distance, interactable, mSize.get(entityId)))
          || (!interactionTarget && (inMeleeApproachRange
              || (rangedRangeThreshold > 0f && distance <= rangedRangeThreshold)))) {
        if (interactionTarget && mAngle.has(entityId)) {
          Vector2 facing = new Vector2(targetPos).sub(position0);
          if (!facing.isZero(0.0001f)) mAngle.get(entityId).target.set(facing).nor();
        }
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
    boolean running = VelocityModeChanger.isRunRequested(
            mRunning.has(entityId), mTemporaryRunning.has(entityId))
        && (!mAttributes.has(entityId)
            || StaminaSystem.hasRunStamina(mAttributes.get(entityId)));
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

  static boolean isInMeleeApproachRange(
      Actioneer actioneer, int attackerId, int targetId) {
    return actioneer.isInMeleeRange(attackerId, targetId, 0);
  }

  static boolean isMercenaryOwnerTarget(Mercenary mercenary, int targetId) {
    return mercenary != null && mercenary.ownerId != Engine.INVALID_ENTITY
        && mercenary.ownerId == targetId;
  }

  static boolean isValkyrieOwnerTarget(SummonedPet pet, int targetId) {
    return pet != null && !pet.passive && !pet.boneWall
        && pet.petType != null && pet.petType.equalsIgnoreCase("valkyrie")
        && pet.ownerId != Engine.INVALID_ENTITY && pet.ownerId == targetId;
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
    boolean success = findPath(src, position, target, flags, size, path, targetEntityId);
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
        success = findPath(src, position, collision.point, flags, size, path, targetEntityId);
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
    return findPath(src, srcPos, targetPos, flags, size, path, Engine.INVALID_ENTITY);
  }

  protected boolean findPath(int src, Vector2 srcPos, Vector2 targetPos,
      int flags, int size, GraphPath path, int targetEntityId) {
    if (!isNativeMonsterRoomPathAllowed(src, srcPos, targetPos)) {
      if (mMonster.has(src)) {
        Monster monster = mMonster.get(src);
        log.debug("[MONSTER_ROOM_PATH] entity={} monster={} source=({}, {}) target=({}, {}) action=reject",
            src, monster.monstats != null ? monster.monstats.Id : "unknown",
            srcPos.x, srcPos.y, targetPos.x, targetPos.y);
      }
      return false;
    }
    com.riiablo.map.MapGraph.Obstacle obstacle = dynamicCollision == null
        ? null
        : (moverId, ignoredTargetId, x, y, footprint) ->
            dynamicCollision.isFreeForPath(src, targetEntityId, x, y, footprint);
    boolean success = map.findPath(srcPos, targetPos, flags, size, path, obstacle,
        src, targetEntityId);
    if (success) {
      if (!pathWithinNativeMonsterRooms(src, path)) {
        path.clear();
        log.debug("[MONSTER_ROOM_PATH] entity={} source=({}, {}) target=({}, {}) action=reject_path",
            src, srcPos.x, srcPos.y, targetPos.x, targetPos.y);
        return false;
      }
      // Keep dynamic-unit avoidance while still removing unnecessary grid
      // corners. A raw A* path makes units alternate between neighboring
      // directions, which is especially visible in the walk/run animation.
      smoothPathWithDynamicCollision(src, targetEntityId, flags, size, path);
      mPathfind.create(src).set(path);
    }

    return success;
  }

  /**
   * Smooths an A* path without allowing the shortcut to cross a current
   * dynamic unit footprint. Static map collision is checked by the existing
   * map raycaster; dynamic collision is sampled at half-cell intervals.
   */
  private void smoothPathWithDynamicCollision(
      int moverId, int targetId, int flags, int size, GraphPath path) {
    int length = path.getCount();
    if (length <= 2) return;

    int outId = 1;
    int inId = 2;
    while (inId < length) {
      smoothStart.set(path.getNodePosition(outId - 1));
      smoothEnd.set(path.getNodePosition(inId));
      smoothRay.start.set(smoothStart);
      smoothRay.end.set(smoothEnd);

      boolean blocked = map.castRay(smoothRay, flags, size, collision);
      if (!blocked && isDynamicSegmentFree(moverId, targetId, size,
          smoothStart, smoothEnd)) {
        inId++;
        continue;
      }

      path.swapNodes(outId, inId - 1);
      outId++;
      inId++;
    }

    path.swapNodes(outId, inId - 1);
    path.truncatePath(outId + 1);
  }

  private boolean isDynamicSegmentFree(
      int moverId, int targetId, int size, Vector2 start, Vector2 end) {
    if (dynamicCollision == null) return true;

    float dx = end.x - start.x;
    float dy = end.y - start.y;
    int samples = Math.max(1,
        (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)) * 2f));
    for (int i = 1; i <= samples; i++) {
      float fraction = i / (float) samples;
      int x = com.riiablo.map.Map.round(start.x + dx * fraction);
      int y = com.riiablo.map.Map.round(start.y + dy * fraction);
      if (!dynamicCollision.isFreeForPath(moverId, targetId, x, y, size)) {
        return false;
      }
    }
    return true;
  }

  static boolean isRoomPathAllowed(Map.Zone zone, Vector2 source, Vector2 target) {
    return zone == null || !zone.hasNativeRoomTopology()
        || zone.areRoomsAdjacent(source.x, source.y, target.x, target.y);
  }

  private boolean isNativeMonsterRoomPathAllowed(int src, Vector2 source, Vector2 target) {
    if (!mMonster.has(src) || !mMapWrapper.has(src)) return true;
    MapWrapper wrapper = mMapWrapper.get(src);
    if (wrapper == null || wrapper.zone == null) return true;
    return isRoomPathAllowed(wrapper.zone, source, target);
  }

  private boolean pathWithinNativeMonsterRooms(int src, GraphPath path) {
    if (!mMonster.has(src) || !mMapWrapper.has(src)) return true;
    MapWrapper wrapper = mMapWrapper.get(src);
    Map.Zone zone = wrapper != null ? wrapper.zone : null;
    if (zone == null || !zone.hasNativeRoomTopology()) return true;
    Map.RoomEx sourceRoom = zone.findRoomEx(mPosition.get(src).position.x,
        mPosition.get(src).position.y);
    if (sourceRoom == null) return false;
    for (int i = 0; i < path.getCount(); i++) {
      Vector2 point = path.getNodePosition(i);
      Map.RoomEx room = zone.findRoomEx(point.x, point.y);
      if (room == null || (room != sourceRoom && !sourceRoom.isAdjacentTo(room.id))) return false;
    }
    return true;
  }
}
