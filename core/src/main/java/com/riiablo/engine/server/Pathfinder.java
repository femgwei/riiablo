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
import com.riiablo.engine.server.component.Player;
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
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.NativeAiPathCache;
import com.riiablo.engine.server.component.NativePathIdentity;
import com.riiablo.engine.Engine;
import com.riiablo.engine.Direction;
import com.riiablo.engine.server.combat.NativeMeleeDistance;
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
  @Wire(name = "serverContext", failOnNull = false)
  protected ServerGameplayContext serverContext;

  private com.riiablo.Files files() { return ServerGameplayContext.files(serverContext); }
  static final int MOVEMENT_DIRECTIONS = 16;
  static final int ADJACENT_DIRECTION_STABLE_FRAMES = 3;
  static final float DYNAMIC_BLOCK_REPATH_TIME = 0.25f;
  static final int DYNAMIC_DETOUR_RADIUS = 4;
  static final float IMMEDIATE_TURN_COS = MathUtils.cosDeg(45f);
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Size> mSize;
  protected ComponentMapper<Pathfind> mPathfind;
  protected ComponentMapper<NativeAiPathCache> mNativeAiPathCache;
  protected ComponentMapper<NativePathIdentity> mNativePathIdentity;
  protected ComponentMapper<Angle> mAngle;
  protected ComponentMapper<AttributesWrapper> mAttributes;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<Running> mRunning;
  protected ComponentMapper<TemporaryRunning> mTemporaryRunning;
  protected ComponentMapper<Target> mTarget;
  protected ComponentMapper<Interactable> mInteractable;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Mercenary> mMercenary;
  protected ComponentMapper<SummonedPet> mSummonedPet;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<NativeUnitFlags> mNativeUnitFlags;
  protected ComponentMapper<com.riiablo.engine.server.component.Casting> mCasting;
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
    Pathfind nativePath = mPathfind.get(entityId);
    if (nativePath.nativeRuntime != null) {
      nativePath.nativeRuntime.prepare();
      return;
    }
    Velocity velocity = mVelocity.get(entityId);
    if (velocity.stateMovementLocked) {
      velocity.velocity.setZero();
      return;
    }
    Vector2 position0 = mPosition.get(entityId).position;
    tmpVec2.set(position0);
    Pathfind pathfind = mPathfind.get(entityId);
    if (pathfind.targetIdentity != null
        && !pathfind.targetIdentity.isCurrent(world, pathfind.targetEntityId)) {
      findPath(entityId, null);
      return;
    }

    // Players must recover on the tick after an actual dynamic rejection,
    // including single-click orders that have no held-input refresh loop.
    if (mPlayer.has(entityId) && shouldRepathPlayerAfterDynamicBlock(pathfind)) {
      int targetId = pathfind.targetEntityId;
      Vector2 goal = new Vector2(targetId != Engine.INVALID_ENTITY && mPosition.has(targetId)
          ? mPosition.get(targetId).position : pathfind.destination);
      if (!repathPreservingMovementIntent(entityId, goal, targetId)) findPath(entityId, null);
      return;
    }

    // A path is computed before other units move.  If a unit then occupies a
    // later waypoint (for example, the first Fallen reaches the player and
    // starts attacking in a narrow passage), the old path keeps requesting a
    // direction that the authoritative collision step rejects.  Rebuild from
    // the current position after a short, observable stall so a free side
    // route can be selected.  If no route exists, clear the stale path so the
    // monster stops instead of playing walk animation in place; its AI can
    // issue a fresh approach request on the next think tick.
    if (mMonster.has(entityId) && shouldRepathAfterDynamicBlock(pathfind)) {
      if (pathfind.nativeBounded) {
        findPath(entityId, null);
        return;
      }
      int targetEntityId = pathfind.targetEntityId;
      if (isOverlappingDynamicTarget(entityId, targetEntityId)) {
        float stalledTime = pathfind.stalledTime;
        findPath(entityId, null);
        Monster monster = mMonster.get(entityId);
        log.debug("[MONSTER_PATH_STALLED] entity={} monster={} stalledTime={} "
                + "blockedByDynamic=true action=stop_target_overlap",
            entityId,
            monster.monstats != null ? monster.monstats.Id : "unknown",
            stalledTime);
        return;
      }
      Vector2 repathTarget = targetEntityId != Engine.INVALID_ENTITY
          && mPosition.has(targetEntityId)
          ? mPosition.get(targetEntityId).position
          : pathfind.destination;
      float stalledTime = pathfind.stalledTime;
      boolean success = repathPreservingMovementIntent(
          entityId, repathTarget, targetEntityId);
      Monster monster = mMonster.get(entityId);
      log.debug("[MONSTER_PATH_STALLED] entity={} monster={} stalledTime={} "
              + "blockedByDynamic=true action={}",
          entityId,
          monster.monstats != null ? monster.monstats.Id : "unknown",
          stalledTime,
          success ? "repath" : "stop");
      if (!success) findPath(entityId, null);
      return;
    }

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
      if (dynamicTarget && isOverlappingDynamicTarget(entityId, targetId)) {
        // The authoritative collision grid has already allowed the bodies to
        // touch (or a legacy save spawned them in the same footprint).  Do
        // not keep feeding a walk velocity into a physically blocked target;
        // let the AI's next tick select its melee action.
        findPath(entityId, null);
        return;
      }
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
          actioneer, entityId, targetId)
          // D2MOO's AI evaluates the live unit coordinates.  The combat
          // history can lag behind a newly blocked monster path by one tick;
          // use the same footprint-aware live check so the unit stops walking
          // and AIStepper can select its melee mode.
          || isInCurrentMeleeApproachRange(entityId, targetId);
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
      // Charge owns a melee impact even when the monster also fires missiles.
      if (!ownerFollowTarget && !isMonsterCharge(entityId) && mMonster.has(entityId)) {
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
            com.riiablo.codec.excel.Missiles.Entry missile = files().Missiles.get(missileName);
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
      
      // D2Common's exhausted-point check distinguishes a reachable endpoint
      // from the final unit goal. A recovery detour can finish before the
      // periodic timer, while the range checks above still require pursuit.
      // Coordinate walks (including DiabRun) have no entity goal to resume.
      boolean exhaustedShortOfTarget = !targets.hasNext()
          && position0.epsilonEquals(target, 0.1f)
          && (Map.round(position0.x) != Map.round(targetPos.x)
              || Map.round(position0.y) != Map.round(targetPos.y));
      if (exhaustedShortOfTarget) {
        log.debug("[ENTITY_PATH_CONTINUE] entity={} target={} position=({}, {}) goal=({}, {})",
            entityId, targetId, position0.x, position0.y, targetPos.x, targetPos.y);
      }
      // Reuse the existing exact/dynamic recovery policy; do not promote a
      // failed query to partial or retry before the current endpoint is reached.
      if (shouldRepath || exhaustedShortOfTarget) {
        if (pathfind.nativePathType == 13) {
          int bonus = Math.round(mVelocity.get(entityId).modeSpeedBonusMultiplier * 100f);
          if (findNativeChase(entityId, targetId))
            mVelocity.get(entityId).setModeSpeedBonusPercent(bonus);
          return;
        }
        repathPreservingMovementIntent(entityId, targetPos, targetId);
        return;
      }
    }
    
    // D2MOD: Check if path is valid (has path points)
    // Similar to PATH_GetNumberOfPathPoints in D2MOD
    // If target is zero or no more path points, stop movement
    Position sourcePosition = mPosition.get(entityId);
    boolean nativeWaypoint = velocity.nativeMonsterBaseVelocity >= 0
        && sourcePosition.hasNativePrecision() && pathfind.hasNativeWaypoint();
    boolean reachedWaypoint = nativeWaypoint
        ? sourcePosition.nativePrecisionX() == com.d2moo.common.path.D2PathStep.toFp16Center(pathfind.nativeWaypointX())
            && sourcePosition.nativePrecisionY() == com.d2moo.common.path.D2PathStep.toFp16Center(pathfind.nativeWaypointY())
        : tmpVec2.epsilonEquals(target, 0.1f);
    if (target.isZero() && !nativeWaypoint || (!targets.hasNext() && reachedWaypoint)) {
      // Path is invalid or completed, stop movement
      findPath(entityId, null);
      return;
    }
    
    // Check if reached current target waypoint
    if (reachedWaypoint) {
      if (targets.hasNext()) {
        pathfind.advanceTarget();
      } else {
        // No more path points, stop movement
        findPath(entityId, null);
        return;
      }
    }

    boolean running = VelocityModeChanger.isRunRequested(
            mRunning.has(entityId), mTemporaryRunning.has(entityId))
        && (!mAttributes.has(entityId)
            || StaminaSystem.hasRunStamina(mAttributes.get(entityId)));
    velocity.movementRunning = running;
    float speed = velocity.speed(running);
    com.riiablo.engine.server.component.Casting cast = mCasting.get(entityId);
    if (isMonsterCharge(entityId)) {
      speed = NativeMonsterCharge.requestedSpeed(mMonster.get(entityId).monstats,
          files().skills.get(cast.skillId), velocity);
    }
    if (cast != null && cast.diabRun != null && cast.diabRun.moving) {
      speed = NativeDiabRun.baseSpeed(files().skills.get(cast.skillId), 1)
          * Math.max(Velocity.MIN_SPEED_MULTIPLIER, velocity.baseSpeedMultiplier + velocity.modeSpeedBonusMultiplier);
    }
    if (speed <= 0f) {
      log.warn("[MOVEMENT] invalid speed entity={} running={} walkSpeed={} runSpeed={}",
          entityId, running, velocity.walkSpeed, velocity.runSpeed);
      velocity.velocity.setZero();
      return;
    }
    // Consume waypoints only after authoritative position reaches them.
    // Advancing the iterator using a hypothetical multi-segment step cuts
    // corners, then discards the very waypoint needed to escape a wall.
    tmpVec2.set(target).sub(position0);
    speed = limitWaypointSpeed(speed, world.delta
        * velocity.movementMultiplier(), tmpVec2.len());

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

  static float limitWaypointSpeed(float speed, float delta, float distance) {
    return delta > 0f ? Math.min(speed, distance / delta) : 0f;
  }

  static boolean isInMeleeApproachRange(
      Actioneer actioneer, int attackerId, int targetId) {
    return actioneer.isInMeleeRange(attackerId, targetId, 0);
  }

  private boolean isMonsterCharge(int entityId) {
    if (!mMonster.has(entityId)) return false;
    com.riiablo.engine.server.component.Casting cast = mCasting.get(entityId);
    com.riiablo.codec.excel.Skills.Entry skill = cast == null ? null
        : files().skills.get(cast.skillId);
    return skill != null && skill.srvdofunc == 67;
  }

  private boolean isInCurrentMeleeApproachRange(int attackerId, int targetId) {
    if (!mPosition.has(attackerId) || !mPosition.has(targetId)) return false;
    int attackerSize = mSize.has(attackerId) ? mSize.get(attackerId).size : Size.INSIGNIFICANT;
    int targetSize = mSize.has(targetId) ? mSize.get(targetId).size : Size.INSIGNIFICANT;
    int meleeRange = actioneer.getMeleeRange(attackerId);
    Position attacker = mPosition.get(attackerId);
    Position target = mPosition.get(targetId);
    return NativeMeleeDistance.isInRange(
        Math.round(attacker.position.x), Math.round(attacker.position.y), attackerSize,
        Math.round(target.position.x), Math.round(target.position.y), targetSize,
        meleeRange, 0);
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

  /** Install native circle waypoints directly, without A* or path smoothing. */
  public boolean findNativeOrbit(int src, int targetId, int budget, boolean clockwise) {
    return findNativePath(src, targetId, budget, clockwise ? 2 : -2);
  }

  public boolean findNativePath(int src, int targetId, int budget, int directionOffset) {
    if (mPosition.has(targetId)) {
      Boolean nativeResult = NativePathRuntime.request(world,src,map,dynamicCollision,
          mPosition.get(targetId).position,targetId,directionOffset == 0 ? 2 : directionOffset > 0 ? 5 : 6,
          budget,directionOffset);
      if (nativeResult != null) return nativeResult;
    }
    // These projected waypoints do not establish complete native dynamic-path
    // state. Like ordinary path requests, they must not retain a trusted cache.
    if (mNativeAiPathCache.has(src)) mNativeAiPathCache.get(src).invalidate();
    if (!mVelocity.has(src) || !mPosition.has(src) || !mPosition.has(targetId)) return false;
    // Only accept a live allocation. Delayed command callers must additionally
    // validate their saved token; looking up today's token cannot identify an
    // old target after its ECS slot has been reused.
    com.riiablo.engine.server.component.NativePathIdentity targetIdentity =
        world.getMapper(com.riiablo.engine.server.component.NativePathIdentity.class).get(targetId);
    if (targetIdentity == null || targetIdentity.token() == null
        || !targetIdentity.token().isCurrent(world, targetId)) return false;
    Vector2 source = mPosition.get(src).position;
    Vector2 target = mPosition.get(targetId).position;
    // Projected native waypoints bypass the ordinary A* entry point, not its
    // monster destination policy. Reject before replacing a current path.
    if ((mMonster.has(src) && !isMonsterTargetDestinationAllowed(src, target))
        || !isNativeMonsterRoomPathAllowed(src, source, target)) {
      findPath(src, null);
      return false;
    }
    MapWrapper owner = mMapWrapper.get(src);
    int flags = groundPathFlags(src);
    int size = mSize.has(src) ? mSize.get(src).size : 1;
    com.riiablo.engine.server.ai.NativeTowardPathProbe.Blocked blocked =
        (x, y) -> map.isBlockedForPath(owner == null ? null : owner.zone,
            x, y, flags, size, canOpenDoors(src))
            || (dynamicCollision != null && !dynamicCollision.isFreeForPath(
                src, targetId, x, y, size));
    GraphPath path = directionOffset == 0
        ? com.riiablo.engine.server.ai.NativeTowardPathProbe.toward(
            (int) source.x, (int) source.y, (int) target.x, (int) target.y, budget, 1, blocked)
        : com.riiablo.engine.server.ai.NativeTowardPathProbe.orbit(
            (int) source.x, (int) source.y, (int) target.x, (int) target.y,
            budget, directionOffset, blocked);
    // Orbit waypoints can leave the permitted rooms even when the target is
    // legal. Apply the same exported-room postcheck as ordinary paths.
    if (path.getCount() < 2 || !pathWithinNativeMonsterRooms(src, path)) {
      findPath(src, null);
      return false;
    }
    mRunning.remove(src);
    mVelocity.get(src).clearModeSpeedBonus();
    // Native circle ends at its generated final point instead of chasing the unit.
    Pathfind nativePath = mPathfind.create(src).set(path);
    nativePath.nativeBounded = true;
    nativePath.targetEntityId = Engine.INVALID_ENTITY;
    nativePath.targetIdentity = null;
    return true;
  }

  /** Type 13 uses Toward with flag 0x100 and retains the live target. */
  public boolean findNativeChase(int src, int targetId) {
    if (mPosition.has(targetId)) {
      Boolean nativeResult = NativePathRuntime.request(world,src,map,dynamicCollision,
          mPosition.get(targetId).position,targetId,13,77,0);
      if (nativeResult != null) return nativeResult;
    }
    if (!findNativePath(src, targetId, 77, 0)) return false;
    Pathfind path = mPathfind.get(src);
    path.nativePathType = 13;
    path.targetEntityId = targetId;
    path.lastTargetPosition.set(mPosition.get(targetId).position);
    com.riiablo.engine.server.component.NativePathIdentity identity = world.getMapper(
        com.riiablo.engine.server.component.NativePathIdentity.class).get(targetId);
    path.targetIdentity = identity == null ? null : identity.token();
    return true;
  }

  public boolean findPath(int src, Vector2 target, boolean raycast) {
    return findPath(src, target, raycast, Engine.INVALID_ENTITY);
  }

  public boolean findPath(int src, Vector2 target, boolean raycast, int targetEntityId) {
    if (target != null && mNativeAiPathCache.has(src)) {
      // ECS A* endpoints are not native tTargetCoord / path-point state.
      mNativeAiPathCache.get(src).invalidate();
    }
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

    Boolean nativeResult = NativePathRuntime.request(world,src,map,dynamicCollision,
        target,targetEntityId,13,5,0);
    if (nativeResult != null) return nativeResult;

    Vector2 position = mPosition.get(src).position;
    // Fn023 changes the dynamic path to a straight coordinate flight with
    // collision mask NONE. Ground A* may reject these distant air waypoints.
    if (mMonster.has(src) && mMonster.get(src).vultureAirborne) {
      if (!isMonsterTargetDestinationAllowed(src, target)
          || !isNativeMonsterRoomPathAllowed(src, position, target)) return false;
      GraphPath flight = Pools.obtain(GraphPath.class);
      flight.add(new com.riiablo.map.pfa.Point2((int) position.x, (int) position.y));
      flight.add(new com.riiablo.map.pfa.Point2((int) target.x, (int) target.y));
      mPathfind.create(src).set(flight);
      return true;
    }
    int flags = groundPathFlags(src);
    int size = mSize.get(src).size;
    GraphPath path = Pools.obtain(GraphPath.class);
    // D2Common's dynamic path carries nMoveTestCollisionMask into A*.
    // Dynamic footprints are therefore part of the initial search as well as
    // any later recovery search; DynamicUnitCollisionSystem applies the
    // native MONSTER_PATH/PET filtering for the footprint layer.
    boolean success = findPath(src, position, target, flags, size, path,
        targetEntityId, true);
    if (success) {
      return true;
    }
    // A large mover cannot reach an occupied unit's centre even when that
    // exact goal node ignores the target: every neighboring footprint can
    // still collide. Use the existing collision-checked approach search for
    // ordinary monster pursuit, retaining the unit identity for continuation.
    // Charge also needs a reachable melee endpoint beside its occupied target;
    // coordinate orders and other skill movement keep their exact semantics.
    if (mMonster.has(src) && (!mCasting.has(src) || isMonsterCharge(src))
        && targetEntityId != Engine.INVALID_ENTITY && mPosition.has(targetEntityId)
        && target.epsilonEquals(mPosition.get(targetEntityId).position, 0.01f)
        && dynamicCollision != null
        && !dynamicCollision.isFreeForPath(src, Engine.INVALID_ENTITY,
            Map.round(target.x), Map.round(target.y), size)
        && isMonsterTargetDestinationAllowed(src, target)
        && isNativeMonsterRoomPathAllowed(src, position, target)
        && findDynamicApproachPath(src, target, targetEntityId, position)) {
      Pools.free(path);
      return true;
    }
    if (raycast) {
      ray.set(position, target);
      MapWrapper owner = mMapWrapper.has(src) ? mMapWrapper.get(src) : null;
      success = map.castRay(owner == null ? null : owner.zone,
          ray, flags, size, collision, canOpenDoors(src));
      if (success) {
        success = findPath(src, position, collision.point, flags, size, path,
            targetEntityId, true);
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

  static boolean shouldRepathAfterDynamicBlock(Pathfind pathfind) {
    return pathfind != null && pathfind.blockedByDynamic
        && pathfind.stalledTime >= DYNAMIC_BLOCK_REPATH_TIME;
  }

  static boolean shouldRepathPlayerAfterDynamicBlock(Pathfind pathfind) {
    return pathfind != null && pathfind.blockedByDynamic && pathfind.stalledTime > 0f;
  }

  private boolean repathPreservingMovementIntent(
      int entityId, Vector2 target, int targetEntityId) {
    Velocity velocity = mVelocity.get(entityId);
    float bonus = velocity.modeSpeedBonusMultiplier;
    boolean running = mRunning.has(entityId);
    Vector2 source = mPosition.get(entityId).position;
    boolean success = findPathWithDynamicObstacles(entityId, target, targetEntityId)
        && hasUsableMovementPath(entityId, source);
    if (!success) {
      // A* may legitimately reach the exact target while the first segment is
      // unusable: the target can be surrounded by monsters, or the smoothed
      // segment can cross a footprint between two free grid nodes.  Give the
      // mover a nearby, dynamically free approach point so it can make local
      // progress and let the next AI tick finish the attack positioning.
      findPath(entityId, null);
      success = findDynamicApproachPath(entityId, target, targetEntityId, source);
    }
    if (success) {
      velocity.modeSpeedBonusMultiplier = bonus;
      if (running) mRunning.create(entityId);
    }
    return success;
  }

  private boolean findDynamicApproachPath(
      int entityId, Vector2 target, int targetEntityId, Vector2 source) {
    if (target == null || targetEntityId == Engine.INVALID_ENTITY) {
      return false;
    }
    if (dynamicCollision != null) dynamicCollision.beginPathQuery();
    try {
      return findDynamicApproachInCollisionSnapshot(entityId, target, targetEntityId, source);
    } finally {
      if (dynamicCollision != null) dynamicCollision.endPathQuery();
    }
  }

  private boolean findDynamicApproachInCollisionSnapshot(
      int entityId, Vector2 target, int targetEntityId, Vector2 source) {
    int size = mSize.get(entityId).size;
    for (int radius = 1; radius <= DYNAMIC_DETOUR_RADIUS; radius++) {
      for (int dy = -radius; dy <= radius; dy++) {
        for (int dx = -radius; dx <= radius; dx++) {
          if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) continue;
          detourTarget.set(target.x + dx, target.y + dy);
          int x = Map.round(detourTarget.x);
          int y = Map.round(detourTarget.y);
          if (dynamicCollision != null
              && !dynamicCollision.isFreeForPath(
                  entityId, Engine.INVALID_ENTITY, x, y, size)) {
            continue;
          }
          if (!findPathWithDynamicObstacles(entityId, detourTarget, targetEntityId)) continue;
          if (hasUsableMovementPath(entityId, source)) return true;
          findPath(entityId, null);
        }
      }
    }
    return false;
  }

  private final Vector2 detourTarget = new Vector2();

  private boolean hasUsableMovementPath(int entityId, Vector2 source) {
    if (!mPathfind.has(entityId)) return false;
    Pathfind pathfind = mPathfind.get(entityId);
    if (pathfind.path == null || pathfind.path.getCount() < 2
        || pathfind.target.epsilonEquals(source, 0.1f)) {
      return false;
    }

    int size = mSize.get(entityId).size;
    int targetX = Map.round(pathfind.target.x);
    int targetY = Map.round(pathfind.target.y);
    if (dynamicCollision != null
        && !dynamicCollision.isFreeForPath(entityId, Engine.INVALID_ENTITY,
            targetX, targetY, size)) {
      return false;
    }

    // The A* graph validates node footprints.  Validate the actual first
    // movement segment as well; a diagonal/long smoothed segment can still
    // cross a dynamic footprint even when both endpoint nodes are free.
    if (dynamicCollision != null
        && !isDynamicSegmentFree(entityId, Engine.INVALID_ENTITY, size,
            source, pathfind.target)) {
      return false;
    }
    return true;
  }

  protected boolean findPath(int src, Vector2 srcPos, Vector2 targetPos, int flags, int size, GraphPath path) {
    return findPath(src, srcPos, targetPos, flags, size, path, Engine.INVALID_ENTITY);
  }

  protected boolean findPath(int src, Vector2 srcPos, Vector2 targetPos,
      int flags, int size, GraphPath path, int targetEntityId) {
    return findPath(src, srcPos, targetPos, flags, size, path, targetEntityId, false);
  }

  private boolean findPathWithDynamicObstacles(
      int src, Vector2 targetPos, int targetEntityId) {
    if (targetPos == null || !mPosition.has(src) || !mVelocity.has(src)) return false;
    int flags = groundPathFlags(src);
    int size = mSize.get(src).size;
    GraphPath path = Pools.obtain(GraphPath.class);
    boolean success = findPath(src, mPosition.get(src).position, targetPos,
        flags, size, path, targetEntityId, true);
    if (!success) Pools.free(path);
    return success;
  }

  private boolean findPath(int src, Vector2 srcPos, Vector2 targetPos,
      int flags, int size, GraphPath path, int targetEntityId,
      boolean includeDynamicObstacles) {
    if (dynamicCollision != null) dynamicCollision.beginPathQuery();
    try {
      return findPathInCollisionSnapshot(src, srcPos, targetPos, flags, size, path,
          targetEntityId, includeDynamicObstacles);
    } finally {
      if (dynamicCollision != null) dynamicCollision.endPathQuery();
    }
  }

  private boolean findPathInCollisionSnapshot(int src, Vector2 srcPos, Vector2 targetPos,
      int flags, int size, GraphPath path, int targetEntityId,
      boolean includeDynamicObstacles) {
    if (mMonster.has(src) && !isMonsterTargetDestinationAllowed(src, targetPos)) {
      path.clear();
      return false;
    }
    if (!isNativeMonsterRoomPathAllowed(src, srcPos, targetPos)) {
      if (mMonster.has(src)) {
        Monster monster = mMonster.get(src);
        log.debug("[MONSTER_ROOM_PATH] entity={} monster={} source=({}, {}) target=({}, {}) action=reject",
            src, monster.monstats != null ? monster.monstats.Id : "unknown",
            srcPos.x, srcPos.y, targetPos.x, targetPos.y);
      }
      return false;
    }
    com.riiablo.map.MapGraph.Obstacle obstacle = !includeDynamicObstacles
        || dynamicCollision == null
        ? null
        : (moverId, ignoredTargetId, x, y, footprint) ->
            dynamicCollision.isFreeForPath(src,
                ignoredDynamicTarget(targetEntityId, x, y),
                x, y, footprint);
    MapWrapper owner = mMapWrapper.has(src) ? mMapWrapper.get(src) : null;
    com.riiablo.engine.server.component.Casting cast = mCasting.get(src);
    boolean runPath = cast != null && cast.diabRun != null;
    // Ground clicks may land inside a prop's footprint. Search toward the
    // closest reachable node instead of clipping the command to the first
    // obstacle on a ray; that clipping discards valid routes around props.
    boolean playerGroundPath = mPlayer.has(src) && targetEntityId == Engine.INVALID_ENTITY;
    boolean success = (runPath || playerGroundPath)
        ? map.findPartialPath(owner == null ? null : owner.zone,
            srcPos, targetPos, flags, size, path, obstacle, canOpenDoors(src))
        : map.findPath(owner == null ? null : owner.zone,
            srcPos, targetPos, flags, size, path, obstacle, src, targetEntityId, canOpenDoors(src));
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
      smoothPathWithDynamicCollision(src, targetEntityId, flags, size, path,
          canOpenDoors(src));
      boolean startAtSource = false;
      if (path.getCount() >= 2 && !srcPos.epsilonEquals(path.getNodePosition(0), 0.1f)) {
        ray.set(srcPos, path.getNodePosition(1));
        if (map.castRay(owner == null ? null : owner.zone, ray, flags, size, collision, canOpenDoors(src))
            || !isDynamicSegmentFree(src, targetEntityId, size, srcPos, path.getNodePosition(1))) {
          ray.set(srcPos, path.getNodePosition(0));
          // Re-centre by ordinary movement within the already occupied cell,
          // never snap coordinates or relax the swept collision. An off-centre
          // start can otherwise enter a forbidden side cross before the legal
          // native diagonal node and repeat the same terrain stall forever.
          startAtSource = !map.castRay(owner == null ? null : owner.zone,
              ray, flags, size, collision, canOpenDoors(src))
              && isDynamicSegmentFree(src, targetEntityId, size, srcPos, path.getNodePosition(0));
        }
      }
      Pathfind pathfind = mPathfind.create(src).set(path, startAtSource);
      // D2Common keeps pTargetUnit separate from the generated path points.
      // Recovery can remove/recreate this component and ends at an approach
      // coordinate, but must still track the original unit on the next tick.
      // Install metadata here for every accepted path, not only public requests.
      pathfind.targetEntityId = targetEntityId;
      NativePathIdentity targetIdentity = targetEntityId == Engine.INVALID_ENTITY
          ? null : mNativePathIdentity.get(targetEntityId);
      pathfind.targetIdentity = targetIdentity == null ? null : targetIdentity.token();
      if (targetEntityId != Engine.INVALID_ENTITY && mPosition.has(targetEntityId)) {
        pathfind.lastTargetPosition.set(mPosition.get(targetEntityId).position);
        pathfind.repathTimer = Pathfind.REPATH_INTERVAL;
      } else {
        pathfind.lastTargetPosition.setZero();
        pathfind.repathTimer = 0f;
      }
    }

    return success;
  }

  /** Mirrors PATH_IsTargetDestinationAllowed for monster ground paths. */
  private boolean isMonsterTargetDestinationAllowed(int entityId, Vector2 target) {
    if (target == null || map == null) return true;
    Map.Zone targetZone = map.getZone(target);
    if (targetZone == null || !targetZone.isTown()) return true;
    Monster monster = mMonster.get(entityId);
    if (monster == null || monster.monstats == null) return true;
    boolean revived = mNativeUnitFlags != null && mNativeUnitFlags.has(entityId)
        && mNativeUnitFlags.get(entityId).has(NativeUnitFlags.IS_REVIVE);
    return isTownDestinationAllowed(monster.monstats.npc, monster.monstats.inTown, revived);
  }

  static boolean isTownDestinationAllowed(boolean npc, boolean inTown, boolean revived) {
    return npc || inTown || revived;
  }

  /**
   * The target unit is allowed only at its exact final node.  Passing the
   * target id through the whole A* search made the player disappear from every
   * candidate node; a later movement step then hit the player/dynamic grid
   * and looked like a permanently stalled path.
   */
  private int ignoredDynamicTarget(int targetEntityId, int x, int y) {
    if (targetEntityId == Engine.INVALID_ENTITY || !mPosition.has(targetEntityId)) {
      return Engine.INVALID_ENTITY;
    }
    Vector2 targetPosition = mPosition.get(targetEntityId).position;
    int targetX = Map.round(targetPosition.x);
    int targetY = Map.round(targetPosition.y);
    return x == targetX && y == targetY
        ? targetEntityId : Engine.INVALID_ENTITY;
  }

  static boolean footprintsOverlap(
      Vector2 first, int firstSize, Vector2 second, int secondSize) {
    if (first == null || second == null) return false;
    int firstRadius = Math.max(0, Math.max(1, firstSize) - 1);
    int secondRadius = Math.max(0, Math.max(1, secondSize) - 1);
    return Math.abs(Map.round(first.x) - Map.round(second.x))
        <= firstRadius + secondRadius
        && Math.abs(Map.round(first.y) - Map.round(second.y))
        <= firstRadius + secondRadius;
  }

  private boolean isOverlappingDynamicTarget(int entityId, int targetId) {
    return targetId != Engine.INVALID_ENTITY && mPosition.has(targetId)
        && mVelocity.has(targetId)
        && footprintsOverlap(
            mPosition.get(entityId).position, mSize.get(entityId).size,
            mPosition.get(targetId).position,
            mSize.has(targetId) ? mSize.get(targetId).size : Size.MEDIUM);
  }

  /**
   * Smooths an A* path without allowing the shortcut to cross a current
   * dynamic unit footprint. Static map collision is checked by the existing
   * map raycaster; dynamic collision is sampled at half-cell intervals.
   */
  private void smoothPathWithDynamicCollision(
      int moverId, int targetId, int flags, int size, GraphPath path,
      boolean canOpenDoors) {
    int length = path.getCount();
    if (length <= 2) return;

    int outId = 1;
    int inId = 2;
    while (inId < length) {
      smoothStart.set(path.getNodePosition(outId - 1));
      smoothEnd.set(path.getNodePosition(inId));
      smoothRay.start.set(smoothStart);
      smoothRay.end.set(smoothEnd);

      MapWrapper owner = mMapWrapper.has(moverId) ? mMapWrapper.get(moverId) : null;
      boolean blocked = map.castRay(owner == null ? null : owner.zone,
          smoothRay, flags, size, collision, canOpenDoors);
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
    int sourceX = Map.round(start.x);
    int sourceY = Map.round(start.y);
    int samples = Math.max(1,
        (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)) * 2f));
    for (int i = 1; i <= samples; i++) {
      float fraction = i / (float) samples;
      int x = com.riiablo.map.Map.round(start.x + dx * fraction);
      int y = com.riiablo.map.Map.round(start.y + dy * fraction);
      // Test newly entered cells, not the mover's already occupied cell.
      if (x == sourceX && y == sourceY) continue;
      if (!dynamicCollision.isFreeForPath(moverId, targetId, x, y, size)) {
        return false;
      }
    }
    return true;
  }

  /** Mirrors PATH_AllocDynamicPath's non-flying open-door branch. */
  private int groundPathFlags(int entityId) {
    Monster monster = mMonster.has(entityId) ? mMonster.get(entityId) : null;
    if (monster != null && monster.vultureAirborne) return 0;
    return NativeGroundPathProfile.forUnit(mPlayer.has(entityId),
        monster == null ? null : monster.monstats).terrainFlags;
  }

  private boolean canOpenDoors(int entityId) {
    if (!mMonster.has(entityId)) return false;
    Monster monster = mMonster.get(entityId);
    return monster != null && monster.monstats != null
        && monster.monstats.opendoors
        && NativeGroundPathProfile.forUnit(false, monster.monstats)
            != NativeGroundPathProfile.FLYING;
  }

  static boolean isRoomPathAllowed(Map.Zone zone, Vector2 source, Vector2 target) {
    return zone == null || !zone.hasNativeRoomTopology()
        || zone.areRoomsAdjacent(source.x, source.y, target.x, target.y);
  }

  private boolean isNativeMonsterRoomPathAllowed(int src, Vector2 source, Vector2 target) {
    if (!mMonster.has(src) || !mMapWrapper.has(src)) return true;
    MapWrapper wrapper = mMapWrapper.get(src);
    if (wrapper == null || wrapper.zone == null) return true;
    Map.Zone targetZone = map != null ? map.getZone(target) : null;
    if (targetZone != null && targetZone != wrapper.zone
        && map != null && map.areZonesAdjacent(wrapper.zone, targetZone)) return true;
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
      Map.Zone pointZone = map != null ? map.getZone(point) : zone;
      if (pointZone != null && pointZone != zone) {
        if (map.areZonesAdjacent(zone, pointZone)) continue;
        return false;
      }
      Map.RoomEx room = zone.findRoomEx(point.x, point.y);
      if (room == null || (room != sourceRoom && !sourceRoom.isAdjacentTo(room.id))) return false;
    }
    return true;
  }
}
