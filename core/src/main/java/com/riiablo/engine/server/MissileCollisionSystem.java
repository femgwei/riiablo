package com.riiablo.engine.server;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.ai.utils.Collision;
import com.badlogic.gdx.ai.utils.Ray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.NativeTargeting;
import com.riiablo.engine.server.component.NativeAiTargetOverride;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.combat.StatusEffectApplier;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.engine.server.party.PvpCombatRules;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.skill.BarbarianSkills;
import com.riiablo.engine.server.skill.DruidSkills;
import com.riiablo.engine.server.skill.PaladinSkills;
import com.riiablo.engine.server.monster.MonsterRank;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import net.mostlyoriginal.api.event.common.EventSystem;
import com.riiablo.map.Map;
import com.riiablo.map.DT1;

/**
 * 导弹碰撞和伤害系统
 * 
 * <p>处理所有导弹的：
 * <ul>
 *   <li>范围检查（使用 distanceTraveled，与 d2mod 一致）</li>
 *   <li>碰撞检测（与玩家和怪物）</li>
 *   <li>伤害应用（使用怪物的 A1MinD/A1MaxD，与 d2mod 一致）</li>
 * </ul>
 */
@All({Missile.class, Position.class, Velocity.class})
@com.artemis.annotations.Wire(failOnNull = false)
public class MissileCollisionSystem extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(MissileCollisionSystem.class);
  /** D2DynamicPathStrc::MAXPATHLEN - 1, as used by PATHTYPE_BLESSEDHAMMER. */
  public static final int BLESSED_HAMMER_PATH_POINTS = 77;
  private static final float BLESSED_HAMMER_ANGLE_STEP = MathUtils.PI2 / 32f;
  private static final float BLESSED_HAMMER_RADIUS_STEP = 9600f / 65536f;
  private static final int[] FROZEN_ORB_X = {
      30, 29, 29, 28, 27, 26, 24, 23, 21, 19, 16, 14, 11, 8, 5, 2,
      0, -2, -5, -8, -11, -14, -16, -19, -21, -23, -24, -26, -27, -28, -29, -29,
      -30, -29, -29, -28, -27, -26, -24, -23, -21, -19, -16, -14, -11, -8, -5, -2,
      0, 2, 5, 8, 11, 14, 16, 19, 21, 23, 24, 26, 27, 28, 29, 29};
  private static final int[] FROZEN_ORB_Y = {
      0, 2, 5, 8, 11, 14, 16, 19, 21, 23, 24, 26, 27, 28, 29, 29,
      30, 29, 29, 28, 27, 26, 24, 23, 21, 19, 16, 14, 11, 8, 5, 2,
      0, -2, -5, -8, -11, -14, -16, -19, -21, -23, -24, -26, -27, -28, -29, -29,
      -30, -29, -29, -28, -27, -26, -24, -23, -21, -19, -16, -14, -11, -8, -5, -2};
  
  protected ComponentMapper<Missile> mMissile;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<Angle> mAngle;
  protected ComponentMapper<Class> mClass;
  protected ComponentMapper<com.riiablo.engine.server.component.CofReference> mCofReference;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Mercenary> mMercenary;
  protected ComponentMapper<SummonedPet> mSummonedPet;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<NativeUnitFlags> mNativeUnitFlags;
  protected ComponentMapper<NativeAiTargetOverride> mNativeAiTargetOverride;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<AnimData> mAnimData;
  protected ComponentMapper<Sequence> mSequence;

  @com.artemis.annotations.Wire(name = "partyManager", failOnNull = false)
  protected PartyManager partyManager;
  @com.artemis.annotations.Wire(name = "factory", failOnNull = false)
  protected EntityFactory factory;
  
  protected EventSystem events;
  
  private final Vector2 tmpVec = new Vector2();
  private final Vector2 lastPos = new Vector2();
  private final Vector2 blessedHammerPoint = new Vector2();
  private final Vector2 blessedHammerStep = new Vector2();
  private final Vector2 blessedHammerNext = new Vector2();
  private final Ray<Vector2> wallRay = new Ray<>(new Vector2(), new Vector2());
  private final Collision<Vector2> wallCollision =
      new Collision<>(new Vector2(), new Vector2());
  private volatile int mercenaryCollisionCount;
  private volatile int mercenaryDamageCount;
  private volatile int mercenaryLastDamageTarget = Engine.INVALID_ENTITY;
  private volatile float mercenaryLastDamageBefore;
  private volatile float mercenaryLastDamageAfter;
  
  @Override
  protected void process(int entityId) {
    Missile missile = mMissile.get(entityId);
    if (!missile.authoritative) return;
    Position position = mPosition.get(entityId);
    Velocity velocity = mVelocity.get(entityId);
    int elapsedFrames = Math.max(1, Math.round(Math.max(0f, world.delta) * 25f));
    missile.nativeFrame += elapsedFrames;

    if (missile.rabiesController) {
      processRabiesController(entityId, missile, position, elapsedFrames);
      return;
    }

    if (missile.fistOfHeavensDelay) {
      processFistOfHeavensDelay(entityId, missile, position);
      return;
    }

    if (missile.blizzardCenter) {
      processBlizzardCenter(entityId, missile, position, elapsedFrames);
      return;
    }

    if (missile.frozenOrbController) {
      processFrozenOrbController(entityId, missile, position, elapsedFrames);
      if (!world.getEntityManager().isActive(entityId)) return;
    }
    if (missile.frozenOrbNova) {
      processFrozenOrbNova(missile, position, velocity);
    }
    if (missile.meteorCenter) {
      processMeteorCenter(entityId, missile, position);
      return;
    }

    // D2MOO SrvDo20 retargets Blade Creeper's missile path to its controller
    // every frame. The stored damage owner remains the casting player.
    lastPos.set(position.position);
    if (missile.attached) {
      if (missile.attachedEntityId < 0
          || !world.getEntityManager().isActive(missile.attachedEntityId)
          || !mPosition.has(missile.attachedEntityId)) {
        log.info("[BLADE_SENTINEL] phase=missile_remove missileId={} owner={} controller={} "
                + "reason=controller_missing",
            entityId, missile.ownerId, missile.attachedEntityId);
        world.delete(entityId);
        return;
      }
      AttributesWrapper controllerAttrs = mAttributesWrapper.has(missile.attachedEntityId)
          ? mAttributesWrapper.get(missile.attachedEntityId) : null;
      StatRef controllerHp = controllerAttrs != null && controllerAttrs.attrs != null
          ? controllerAttrs.attrs.get(Stat.hitpoints) : null;
      if (controllerHp != null && controllerHp.asFixed() <= 0f) {
        world.delete(entityId);
        return;
      }
      position.position.set(mPosition.get(missile.attachedEntityId).position);
      velocity.velocity.setZero();
    }

    // Guided Arrow/Bone Spirit tracks its native target every server tick.
    // Keep the current speed while steering; when the target disappears the
    // missile continues along its last heading, matching D2MOO's fallback.
    if (missile.homing && missile.targetId >= 0 && mPosition.has(missile.targetId)) {
      Attributes targetAttrs = mAttributesWrapper.has(missile.targetId)
          ? mAttributesWrapper.get(missile.targetId).attrs : null;
      boolean alive = targetAttrs == null || targetAttrs.get(Stat.hitpoints) == null
          || targetAttrs.get(Stat.hitpoints).asFixed() > 0f;
      if (alive) {
        float speed = velocity.velocity.len();
        tmpVec.set(mPosition.get(missile.targetId).position).sub(position.position);
        if (!tmpVec.isZero(0.0001f) && speed > 0f) velocity.velocity.set(tmpVec).setLength(speed);
      }
    }

    if (missile.chargedBoltPath && !missile.attached
        && missile.distanceTraveled >= missile.chargedBoltNextTurnDistance) {
      while (missile.distanceTraveled >= missile.chargedBoltNextTurnDistance) {
        long rolled = AssassinTrapSystem.chargedBoltRoll(
            missile.chargedBoltSeedLow, missile.chargedBoltSeedHigh);
        missile.chargedBoltSeedLow = (int) rolled;
        missile.chargedBoltSeedHigh = (int) (rolled >>> 32);
        missile.chargedBoltNextTurnDistance += 2f;
      }
      int direction = AssassinTrapSystem.chargedBoltDirection(
          missile.chargedBoltMainDirection, missile.chargedBoltSeedLow);
      float speed = velocity.velocity.len();
      AssassinTrapSystem.chargedBoltVector(direction, tmpVec);
      velocity.velocity.set(tmpVec).setLength(speed);
      if (mAngle.has(entityId)) mAngle.get(entityId).target.set(tmpVec);
    }

    if (missile.chaosIcePath && !missile.attached
        && missile.nativeFrame >= missile.chaosIceNextTurnFrame) {
      int interval = Math.max(1, missile.missile != null
          && missile.missile.Param != null && missile.missile.Param.length > 0
          ? missile.missile.Param[0] : 1);
      while (missile.nativeFrame >= missile.chaosIceNextTurnFrame) {
        NativeRng rng = new NativeRng(missile.chaosIceSeed);
        int xParam = missile.chaosIceX;
        int yParam = missile.chaosIceY;
        if ((rng.nextInt() & 1) != 0) xParam = -xParam;
        else yParam = -yParam;
        int nextX = (yParam + 4 * missile.chaosIceX) / 4;
        int nextY = (xParam + 4 * missile.chaosIceY) / 4;
        if (nextX == 0) nextX = 1;
        if (nextY == 0) nextY = 1;
        missile.chaosIceSeed = rng.state();
        missile.chaosIceX = nextX;
        missile.chaosIceY = nextY;
        missile.chaosIceNextTurnFrame += interval;
      }
      float speed = velocity.velocity.len();
      velocity.velocity.set(missile.chaosIceX, missile.chaosIceY).nor().setLength(speed);
      if (mAngle.has(entityId)) mAngle.get(entityId).target.set(velocity.velocity);
    }

    if (missile.blessedHammerPath && !missile.attached) {
      processBlessedHammerPath(entityId, missile, position, velocity);
      return;
    }
    
    // 更新导弹位置（VelocityAdder 系统被注释掉了，所以在这里更新）
    float moveDistance;
    if (missile.attached) {
      moveDistance = lastPos.dst(position.position);
    } else {
      moveDistance = velocity.velocity.len() * world.delta;
      Vector2 next = tmpVec.set(position.position).add(
          velocity.velocity.x * world.delta, velocity.velocity.y * world.delta);
      if (checkNativeMapCollision(entityId, missile, position.position, next)) return;
      position.position.set(next);
    }

    if (!updateNativeRoom(entityId, missile, position)) return;

    if (!missile.persistent && isStationaryPoisonCloud(missile, velocity)) {
      configurePersistentPoisonCloud(missile, null);
    }

    if (missile.missile != null && missile.missile.pSrvDoFunc == 2) {
      // Native SrvDo02 emits a cloud child at every missile update, producing
      // the Poison Javelin trail rather than a single impact puff.
      spawnPoisonCloud(missile, position.position);
    }
    
    // 更新已移动距离（与 d2mod 一致，使用 distanceTraveled）
    missile.distanceTraveled += moveDistance;

    // D2MOO SrvDo06: each moving maker lays one Fire Wall child. The maker
    // itself is only a control path and never enters unit collision.
    if (missile.fireWallMaker) {
      processFireWallMaker(entityId, missile, position, velocity);
      if (!world.getEntityManager().isActive(entityId)) return;
    }

    // Wake maker movement is ordinary path travel; SrvDo31 is evaluated
    // after the step so it can detect arrival at the configured endpoint.
    if (missile.wakeMaker) {
      processWakeMaker(entityId, missile, position, velocity);
      return;
    }

    if (missile.persistent) {
      missile.remainingFrames -= Math.max(1, Math.round(world.delta * 25f));
      missile.tickFrames++;
      if (missile.remainingFrames <= 0) {
        world.delete(entityId);
        return;
      }
      if (missile.tickFrames < Math.max(1, missile.tickInterval)) return;
      missile.tickFrames = 0;
      // Native area missiles may damage a target once per damage-rate window.
      missile.hitTargets.clear();
    }
    
    // 检查范围限制
    if (missile.range > 0 && missile.distanceTraveled >= missile.range) {
    // Native LastCollide performs one final unit lookup at the range edge
    // before the missile is removed.  This matters for fast arrows whose
    // final segment ends inside a target's hitbox.
      if (shouldResolveLastCollide(missile, true)) {
        if (!missile.attached && moveDistance > 0f && missile.distanceTraveled > missile.range) {
          // A native range check resolves at the exact endpoint, not at the
          // overshot position produced by a large single-frame velocity.
          clampToRangeEndpoint(lastPos, position.position,
              missile.distanceTraveled - moveDistance, moveDistance, missile.range, tmpVec);
          position.position.set(tmpVec);
          missile.distanceTraveled = missile.range;
        }
        missile.lastCollideResolved = true;
        checkCollisions(entityId, missile, position, lastPos);
        if (!world.getEntityManager().isActive(entityId)) return;
      }
      log.debug("Missile {} reached max range ({}), disposing. ownerId={}, pos=({}, {})", 
          entityId, missile.range, missile.ownerId, position.position.x, position.position.y);
      world.delete(entityId);
      return;
    }
    
    // Missiles.txt.Collision is authoritative. Non-colliding visual/control
    // missiles still advance lifetime and room, but never enter combat. Some
    // 1.10f rows leave Collision=0 while providing a server hit function; in
    // that case the hit function is the native indication that collision is
    // required.
    if (!hasNativeCollision(missile)) {
      if (missile.nativeLifetimeFrames > 0
          && missile.nativeFrame >= missile.nativeLifetimeFrames) world.delete(entityId);
      return;
    }

    // 碰撞检测：检查是否与玩家或怪物碰撞
    checkCollisions(entityId, missile, position, lastPos);
    // D2MOO still advances Range for stationary basic missiles. Riiablo's
    // ordinary range is distance-based, so these explicit one-shot visuals
    // need a frame clock or they remain in the world forever when they miss.
    if (missile.nativeLifetimeFrames > 0
        && missile.nativeFrame >= missile.nativeLifetimeFrames) {
      world.delete(entityId);
    }
  }

  /**
   * Advances D2Common's {@code PATHTYPE_BLESSEDHAMMER} along its expanding
   * spiral. Every crossed path edge gets its own map ray and swept-unit pass;
   * treating a whole tick as one chord lets the hammer cut corners through
   * walls and skip targets on the arc.
   */
  private void processBlessedHammerPath(
      int entityId, Missile missile, Position position, Velocity velocity) {
    float remaining = Math.max(0f, velocity.velocity.len() * world.delta);
    while (remaining > 0.0001f
        && missile.blessedHammerPointIndex <= BLESSED_HAMMER_PATH_POINTS) {
      blessedHammerPathPoint(missile.blessedHammerOrigin,
          missile.blessedHammerPointIndex, blessedHammerPoint);
      float distance = position.position.dst(blessedHammerPoint);
      if (distance <= 0.0001f) {
        missile.blessedHammerPointIndex++;
        continue;
      }

      float stepLength = Math.min(remaining, distance);
      blessedHammerStep.set(blessedHammerPoint).sub(position.position).setLength(stepLength);
      blessedHammerNext.set(position.position).add(blessedHammerStep);
      lastPos.set(position.position);
      if (checkNativeMapCollision(entityId, missile, lastPos, blessedHammerNext)) return;

      position.position.set(blessedHammerNext);
      missile.distanceTraveled += stepLength;
      remaining -= stepLength;
      if (mAngle.has(entityId)) mAngle.get(entityId).target.set(blessedHammerStep).nor();
      if (!updateNativeRoom(entityId, missile, position)) return;
      if (hasNativeCollision(missile)) {
        checkCollisions(entityId, missile, position, lastPos);
        if (!world.getEntityManager().isActive(entityId)) return;
      }
      if (stepLength + 0.0001f >= distance) missile.blessedHammerPointIndex++;
    }

    boolean pathComplete = missile.blessedHammerPointIndex > BLESSED_HAMMER_PATH_POINTS;
    boolean lifetimeComplete = missile.nativeLifetimeFrames > 0
        && missile.nativeFrame >= missile.nativeLifetimeFrames;
    if (pathComplete || lifetimeComplete) {
      log.debug("[BLESSED_HAMMER] phase=remove missileId={} owner={} reason={} "
              + "frame={} point={} traveled={}",
          entityId, missile.ownerId, pathComplete ? "path_complete" : "lifetime",
          missile.nativeFrame, missile.blessedHammerPointIndex,
          missile.distanceTraveled);
      world.delete(entityId);
    }
  }

  /** Returns native spiral point {@code 1..77}; point zero is the origin. */
  static Vector2 blessedHammerPathPoint(Vector2 origin, int pointIndex, Vector2 out) {
    if (out == null) out = new Vector2();
    if (origin == null || pointIndex <= 0) {
      return out.set(origin == null ? Vector2.Zero : origin);
    }
    float angle = pointIndex * BLESSED_HAMMER_ANGLE_STEP;
    float radius = pointIndex * BLESSED_HAMMER_RADIUS_STEP;
    return out.set(origin.x + MathUtils.cos(angle) * radius,
        origin.y + MathUtils.sin(angle) * radius);
  }

  /** Native {@code MISSMODE_SrvHit22_FistOfTheHeavensDelay}. */
  private void processFistOfHeavensDelay(
      int entityId, Missile delay, Position delayPosition) {
    int lifetime = Math.max(1, delay.nativeLifetimeFrames);
    if (delay.nativeFrame < lifetime || delay.fistOfHeavensTriggered) return;
    delay.fistOfHeavensTriggered = true;

    int targetId = delay.targetId;
    boolean ownerValid = delay.ownerId >= 0
        && world.getEntityManager().isActive(delay.ownerId)
        && mAttributesWrapper.has(delay.ownerId);
    boolean targetValid = targetId >= 0
        && world.getEntityManager().isActive(targetId)
        && mPosition.has(targetId) && mAttributesWrapper.has(targetId)
        && isAlive(targetId);
    if (!ownerValid || !targetValid) {
      log.info("[FIST_OF_HEAVENS] phase=delay_expire missileId={} owner={} target={} "
              + "result=discard ownerValid={} targetValid={}",
          entityId, delay.ownerId, targetId, ownerValid, targetValid);
      world.delete(entityId);
      return;
    }

    // SrvHit22 damages the saved Unit directly even if it moved after cast;
    // pass its current point to the ordinary damage resolver while retaining
    // the delay's original position as the Holy Bolt split origin.
    Position targetPosition = mPosition.get(targetId);
    Vector2 forcedPoint = new Vector2(targetPosition.position);
    checkCollisionWithEntity(
        entityId, delay, forcedPoint, forcedPoint, targetId, targetPosition);
    spawnFistOfHeavensBolts(delay, delayPosition.position, targetId);
    log.info("[FIST_OF_HEAVENS] phase=delay_expire missileId={} owner={} target={} "
            + "frame={} origin=({}, {}) result=triggered",
        entityId, delay.ownerId, targetId, delay.nativeFrame,
        delayPosition.position.x, delayPosition.position.y);
    world.delete(entityId);
  }

  /** Native MISSMODE_SrvDo10_BlizzardCenter. */
  private void processBlizzardCenter(
      int entityId, Missile center, Position position, int elapsedFrames) {
    if (factory == null || center.missile == null) {
      world.delete(entityId);
      return;
    }
    Skills.Entry skill = center.skillId >= 0 ? Riiablo.files.skills.get(center.skillId) : null;
    String childName = center.missile.SubMissile != null
        && center.missile.SubMissile.length > 0 ? center.missile.SubMissile[0] : null;
    Missiles.Entry childRow = childName != null && !childName.isEmpty()
        ? Riiablo.files.Missiles.get(childName) : null;
    if (skill == null || childRow == null) {
      log.warn("[SORCERESS_BLIZZARD] phase=center_remove missileId={} owner={} reason=data",
          entityId, center.ownerId);
      world.delete(entityId);
      return;
    }

    int level = Math.max(1, center.damageLevel);
    int interval = Math.max(1, SkillFormula.evaluate(skill.calc2, skill, level));
    int radius = Math.max(1, SkillFormula.evaluate(skill.calc1, skill, level));
    // Native SrvDo10 tests RemainingFrames % nFrames before creating the
    // child. Preserve that phase instead of using a variable wall-clock timer.
    if (center.remainingFrames > 0 && center.remainingFrames % interval == 0) {
      spawnBlizzardChild(entityId, center, position, skill, childRow, radius);
    }
    center.remainingFrames -= Math.max(1, elapsedFrames);
    if (center.remainingFrames <= 0) {
      log.debug("[SORCERESS_BLIZZARD] phase=center_remove missileId={} owner={} reason=expired",
          entityId, center.ownerId);
      world.delete(entityId);
    }
  }

  /** Native MISSMODE_SrvDo15_FrozenOrb: emit one bolt on each table cadence. */
  private void processFrozenOrbController(
      int entityId, Missile orb, Position position, int elapsedFrames) {
    if (factory == null || orb.missile == null) {
      world.delete(entityId);
      return;
    }
    String shardName = orb.missile.SubMissile != null
        && orb.missile.SubMissile.length > 0 ? orb.missile.SubMissile[0] : null;
    Missiles.Entry shard = shardName != null && !shardName.isEmpty()
        ? Riiablo.files.Missiles.get(shardName) : null;
    Skills.Entry skill = orb.skillId >= 0 ? Riiablo.files.skills.get(orb.skillId) : null;
    if (shard == null || skill == null) {
      log.warn("[SORCERESS_FROZEN_ORB] phase=controller_remove missileId={} owner={} reason=data",
          entityId, orb.ownerId);
      world.delete(entityId);
      return;
    }
    int interval = Math.max(1, arrayValue(orb.missile.Param, 0));
    if (orb.nativeFrame % interval == 0) {
      int index = Math.floorMod(orb.frozenOrbTargetPhase, FROZEN_ORB_X.length);
      Vector2 direction = tmpVec.set(FROZEN_ORB_X[index], FROZEN_ORB_Y[index]);
      if (direction.isZero(0.0001f)) {
        direction.set(mVelocity.get(entityId).velocity);
        if (direction.isZero(0.0001f)) direction.set(Vector2.X);
      }
      direction.nor();
      int childId = factory.createMissile(shard, direction, position.position, orb.ownerId);
      if (childId >= 0 && mMissile.has(childId)) {
        Missile child = mMissile.get(childId);
        child.skillId = orb.skillId;
        child.damageLevel = Math.max(1, orb.damageLevel);
        Attributes ownerAttrs = mAttributesWrapper.has(orb.ownerId)
            ? mAttributesWrapper.get(orb.ownerId).attrs : null;
        MissileDamageResolver.initializeSkill(child, skill, ownerAttrs, child.damageLevel,
            name -> baseSkillLevel(orb.ownerId, name), stateList(orb.ownerId));
        log.debug("[SORCERESS_FROZEN_ORB] phase=bolt source={} root={} bolt={} index={} "
                + "offset=({}, {}) damageSnapshot={}",
            orb.ownerId, entityId, childId, index, FROZEN_ORB_X[index], FROZEN_ORB_Y[index],
            child.damageSnapshot);
      }
      int step = arrayValue(orb.missile.Param, 1);
      orb.frozenOrbTargetPhase = Math.floorMod(index + (step == 0 ? 1 : step),
          FROZEN_ORB_X.length);
    }
  }

  /** Native MISSMODE_SrvDo16_FrozenOrbNova late-path steering adjustment. */
  private void processFrozenOrbNova(Missile nova, Position position, Velocity velocity) {
    if (nova.missile == null || nova.nativeLifetimeFrames <= 0) return;
    int remaining = nova.nativeLifetimeFrames - nova.nativeFrame;
    int turnWindow = Math.max(0, arrayValue(nova.missile.Param, 0));
    int turnInterval = Math.max(1, arrayValue(nova.missile.Param, 1));
    if (remaining < turnWindow && remaining >= 0 && remaining % turnInterval == 0) {
      int dx = nova.frozenOrbTargetX - nova.frozenOrbTargetY;
      int dy = nova.frozenOrbTargetX + nova.frozenOrbTargetY;
      tmpVec.set(nova.frozenOrbOrigin.x + dx * 0.5f,
          nova.frozenOrbOrigin.y + dy * 0.5f).sub(position.position);
      if (!tmpVec.isZero(0.0001f)) velocity.velocity.set(tmpVec).setLength(nova.missile.Vel);
      log.debug("[SORCERESS_FROZEN_ORB] phase=nova_turn missile={} remaining={} target=({}, {})",
          nova.missile.Missile, remaining, nova.frozenOrbTargetX, nova.frozenOrbTargetY);
    }
  }

  /** Native MISSMODE_SrvHit29_FrozenOrb: fan out one bolt every HitPar[0] entries. */
  private void spawnFrozenOrbNova(Missile source, Vector2 origin) {
    if (factory == null || source == null || source.missile == null
        || source.missile.HitSubMissile == null || source.missile.HitSubMissile.length == 0) return;
    String name = source.missile.HitSubMissile[0];
    Missiles.Entry row = name != null ? Riiablo.files.Missiles.get(name) : null;
    Skills.Entry skill = source.skillId >= 0 ? Riiablo.files.skills.get(source.skillId) : null;
    if (row == null || skill == null) return;
    int step = Math.max(1, arrayValue(source.missile.sHitPar, 0));
    int level = Math.max(1, source.damageLevel);
    Attributes ownerAttrs = mAttributesWrapper.has(source.ownerId)
        ? mAttributesWrapper.get(source.ownerId).attrs : null;
    int created = 0;
    for (int i = 0; i < FROZEN_ORB_X.length; i += step) {
      Vector2 direction = tmpVec.set(FROZEN_ORB_X[i], FROZEN_ORB_Y[i]);
      if (direction.isZero(0.0001f)) direction.set(Vector2.X);
      int childId = factory.createMissile(row, direction.nor(), origin, source.ownerId);
      if (childId < 0 || !mMissile.has(childId)) continue;
      Missile nova = mMissile.get(childId);
      nova.skillId = source.skillId;
      nova.damageLevel = level;
      nova.frozenOrbNova = true;
      nova.frozenOrbOrigin.set(origin);
      nova.frozenOrbTargetX = FROZEN_ORB_X[i];
      nova.frozenOrbTargetY = FROZEN_ORB_Y[i];
      nova.range = 0f;
      nova.nativeLifetimeFrames = Math.max(1, row.Range);
      MissileDamageResolver.initializeSkill(nova, skill, ownerAttrs, level,
          key -> baseSkillLevel(source.ownerId, key), stateList(source.ownerId));
      created++;
    }
    log.info("[SORCERESS_FROZEN_ORB] phase=nova source={} origin=({}, {}) step={} created={} "
            + "level={} lifetime={}", source.ownerId, origin.x, origin.y, step, created,
        level, row.Range);
  }

  /** Native MISSMODE_SrvHit14: delayed impact plus meteorfire fields. */
  private void processMeteorCenter(int entityId, Missile center, Position position) {
    Skills.Entry skill = center.skillId >= 0 ? Riiablo.files.skills.get(center.skillId) : null;
    if (skill == null || center.missile == null
        || center.nativeFrame < Math.max(1, center.nativeLifetimeFrames)) return;
    int level = Math.max(1, center.damageLevel);
    int radius = Math.max(1, SkillFormula.evaluate(skill.aurarangecalc, skill, level));
    Array<Integer> candidates = getEntitiesInRange(position.position.x, position.position.y, radius);
    int impacted = 0;
    for (int i = 0; i < candidates.size; i++) {
      int targetId = candidates.get(i);
      if (!mPosition.has(targetId) || !mAttributesWrapper.has(targetId)
          || (!mPlayer.has(targetId) && !mMonster.has(targetId))
          || !isEnemy(center.ownerId, targetId) || !isAlive(targetId)) continue;
      if (position.position.dst2(mPosition.get(targetId).position) > radius * radius) continue;
      resolveFixedElementalRate(entityId, center, targetId,
          mAttributesWrapper.get(targetId).attrs);
      impacted++;
    }
    int fields = spawnMeteorFireFields(center, position.position, skill, level);
    log.info("[SORCERESS_METEOR] phase=impact source={} center={} radius={} impacted={} "
            + "fields={} level={}", center.ownerId, entityId, radius, impacted, fields, level);
    world.delete(entityId);
  }

  private int spawnMeteorFireFields(
      Missile center, Vector2 origin, Skills.Entry skill, int level) {
    if (factory == null || center.missile.HitSubMissile == null
        || center.missile.HitSubMissile.length == 0) return 0;
    String name = center.missile.HitSubMissile[0];
    Missiles.Entry row = name != null ? Riiablo.files.Missiles.get(name) : null;
    if (row == null) return 0;
    int step = Math.max(1, arrayValue(center.missile.sHitPar, 1));
    int lifetime = ServerSkillSystem.meteorFireLifetime(skill, level);
    Attributes ownerAttrs = mAttributesWrapper.has(center.ownerId)
        ? mAttributesWrapper.get(center.ownerId).attrs : null;
    int created = 0;
    for (int i = 0; i < METEOR_X.length; i += step) {
      int childId = factory.createMissile(row, Vector2.X,
          new Vector2(origin).add(METEOR_X[i], METEOR_Y[i]), center.ownerId);
      if (childId < 0 || !mMissile.has(childId)) continue;
      Missile fire = mMissile.get(childId);
      fire.skillId = center.skillId;
      fire.damageLevel = level;
      fire.persistent = true;
      fire.remainingFrames = lifetime;
      fire.tickInterval = 1;
      fire.range = 0f;
      if (mVelocity.has(childId)) mVelocity.get(childId).velocity.setZero();
      MissileDamageResolver.initializeSorceressFireArea(fire, skill, ownerAttrs,
          mPlayer.has(center.ownerId), level,
          key -> baseSkillLevel(center.ownerId, key), stateList(center.ownerId));
      created++;
    }
    return created;
  }

  private static final int[] METEOR_X = {
      2, -2, 0, 0, -3, 0, 3, -1, 1, -1, 2, -4, -3, -1, 0, 1, 3, 4};
  private static final int[] METEOR_Y = {
      -2, -2, 2, 5, 3, 3, 3, 2, 1, -1, -1, -2, -2, -3, -4, -3, -3, -2};

  private void spawnBlizzardChild(
      int centerId, Missile center, Position centerPosition,
      Skills.Entry skill, Missiles.Entry childRow, int radius) {
    int maxRange = Math.max(0, radius - 1);
    NativeRng rng = new NativeRng(center.blizzardSeed);
    int offsetX = maxRange == 0 ? 0 : rng.nextInt(2 * maxRange) - maxRange;
    int offsetY = maxRange == 0 ? 0 : rng.nextInt(2 * maxRange) - maxRange;
    center.blizzardSeed = rng.state();
    // The center's position is authoritative; avoid using a shared temporary
    // vector because child creation may synchronously publish the entity.
    Vector2 origin = new Vector2(centerPosition.position);
    origin.add(offsetX, offsetY);
    MapWrapper wrapper = centerId >= 0 && mMapWrapper.has(centerId)
        ? mMapWrapper.get(centerId) : null;
    if (wrapper != null && wrapper.map != null
        && (wrapper.map.getZone(origin) == null
            || (wrapper.map.flags(origin) & nativeMapCollisionMask(5)) != 0)) {
      log.debug("[SORCERESS_BLIZZARD] phase=strike_skip owner={} reason=collision point=({}, {})",
          center.ownerId, origin.x, origin.y);
      return;
    }
    int childId = factory.createMissile(childRow, Vector2.X, origin, center.ownerId);
    if (childId < 0 || !mMissile.has(childId)) return;
    Missile child = mMissile.get(childId);
    child.skillId = center.skillId;
    child.damageLevel = Math.max(1, center.damageLevel);
    child.range = 0f;
    child.nativeLifetimeFrames = Math.max(1, childRow.Range);
    child.damageMultiplier = center.damageMultiplier;
    Attributes ownerAttrs = mAttributesWrapper.has(center.ownerId)
        ? mAttributesWrapper.get(center.ownerId).attrs : null;
    MissileDamageResolver.initializeSkillArea(
        child, skill, ownerAttrs, child.damageLevel,
        name -> baseSkillLevel(center.ownerId, name), stateList(center.ownerId));
    log.info("[SORCERESS_BLIZZARD] phase=strike owner={} centerSkill={} child={} "
            + "missile={} point=({}, {}) radius={} interval={} lifetime={}",
        center.ownerId, center.skillId, childId, childRow.Missile, origin.x, origin.y,
        radius, Math.max(1, SkillFormula.evaluate(skill.calc2, skill, child.damageLevel)),
        child.nativeLifetimeFrames);
  }

  private void spawnFistOfHeavensBolts(
      Missile delay, Vector2 origin, int primaryTargetId) {
    if (factory == null || delay == null || delay.missile == null
        || delay.missile.HitSubMissile == null
        || delay.missile.HitSubMissile.length == 0) return;
    String name = delay.missile.HitSubMissile[0];
    Missiles.Entry boltRow = name != null ? Riiablo.files.Missiles.get(name) : null;
    Skills.Entry skill = delay.skillId >= 0 ? Riiablo.files.skills.get(delay.skillId) : null;
    if (boltRow == null || !PaladinSkills.isFistOfTheHeavens(skill)) {
      log.warn("[FIST_OF_HEAVENS] phase=split_reject owner={} missile={} skill={} reason=data",
          delay.ownerId, name, delay.skillId);
      return;
    }
    int level = Math.max(1, delay.damageLevel);
    int range = PaladinSkills.getFistOfHeavensRange(delay.missile, skill, level);
    int maximum = PaladinSkills.getFistOfHeavensBoltCount(delay.missile, skill, level);
    int auraFilter = skill.aurafilter != 0 ? skill.aurafilter : 0xA587;
    Array<Integer> targets = getEntitiesInRange(origin.x, origin.y, range);
    targets.sort((left, right) -> {
      float leftDistance = mPosition.has(left)
          ? mPosition.get(left).position.dst2(origin) : Float.MAX_VALUE;
      float rightDistance = mPosition.has(right)
          ? mPosition.get(right).position.dst2(origin) : Float.MAX_VALUE;
      int distanceOrder = Float.compare(leftDistance, rightDistance);
      return distanceOrder != 0 ? distanceOrder : Integer.compare(left, right);
    });
    Attributes ownerAttrs = mAttributesWrapper.get(delay.ownerId).attrs;
    int created = 0;
    for (int i = 0; i < targets.size && created < maximum; i++) {
      int targetId = targets.get(i);
      if (!isFistOfHeavensAuraTarget(
          delay.ownerId, targetId, origin, auraFilter)) continue;
      Vector2 direction = new Vector2(mPosition.get(targetId).position).sub(origin);
      if (direction.isZero(0.0001f)) direction.set(1f, 1f);
      int boltId = factory.createMissile(
          boltRow, direction.nor(), origin, delay.ownerId);
      if (boltId < 0 || !mMissile.has(boltId)) continue;
      Missile bolt = mMissile.get(boltId);
      bolt.skillId = delay.skillId;
      bolt.damageLevel = level;
      bolt.targetId = targetId;
      MissileDamageResolver.initializePaladinFistOfHeavensBolt(
          bolt, skill, ownerAttrs, level,
          skillName -> baseSkillLevel(delay.ownerId, skillName));
      created++;
    }
    log.info("[FIST_OF_HEAVENS] phase=split owner={} primary={} skill={} level={} "
            + "range={} filter=0x{} maximum={} created={} missile={}",
        delay.ownerId, primaryTargetId, delay.skillId, level, range,
        Integer.toHexString(auraFilter), maximum, created, name);
  }

  private boolean isFistOfHeavensAuraTarget(
      int sourceId, int targetId, Vector2 origin, int filter) {
    if (targetId == sourceId || !mPosition.has(targetId) || !isAlive(targetId)) return false;
    boolean player = mPlayer.has(targetId);
    boolean monster = mMonster.has(targetId);
    if (!player && !monster) return false;
    if ((player && (filter & 0x0001) == 0)
        || (monster && (filter & 0x0002) == 0)) return false;
    if (monster) {
      Monster target = mMonster.get(targetId);
      if (target.monstats == null || target.monstats.npc) return false;
      if ((filter & 0x0004) != 0 && !isUndead(target)) return false;
      if ((filter & 0x4000) != 0 && target.monstats.boss) return false;
      if ((filter & 0x40000) != 0 && target.monstats.primeevil) return false;
    }
    if (mNativeUnitFlags.has(targetId)) {
      NativeUnitFlags flags = mNativeUnitFlags.get(targetId);
      if ((filter & 0x0080) != 0 && !NativeTargeting.canBeAttacked(flags)) return false;
      if ((filter & 0x0400) != 0 && !NativeTargeting.isValidCombatTarget(flags)) return false;
    }
    if ((filter & (0x0100 | 0x2000)) != 0 && isTownUnit(targetId)) return false;
    if ((filter & 0x10000) != 0 && !areAligned(sourceId, targetId)) return false;
    if ((filter & 0x8000) != 0 && !isEnemy(sourceId, targetId)) return false;
    if ((filter & 0x0200) != 0 && !hasAuraLineOfSight(sourceId, origin, targetId)) return false;
    return true;
  }

  private boolean hasAuraLineOfSight(int sourceId, Vector2 origin, int targetId) {
    Map map = null;
    if (mMapWrapper.has(sourceId)) map = mMapWrapper.get(sourceId).map;
    if (map == null && mMapWrapper.has(targetId)) map = mMapWrapper.get(targetId).map;
    if (map == null || map.getZone(origin) == null) return true;
    wallRay.set(origin, mPosition.get(targetId).position);
    return !map.castRay(wallRay, DT1.Tile.FLAG_BLOCK_JUMP, 0, wallCollision);
  }

  private boolean isTownUnit(int entityId) {
    if (!mMapWrapper.has(entityId)) return false;
    MapWrapper wrapper = mMapWrapper.get(entityId);
    return wrapper != null && wrapper.zone != null && wrapper.zone.isTown();
  }

  private static boolean isUndead(Monster monster) {
    return monster != null && monster.monstats != null
        && (monster.monstats.lUndead || monster.monstats.hUndead);
  }

  /** D2MOO SrvDo30/SrvHit53: infected units periodically pass remaining poison. */
  private void processRabiesController(
      int entityId, Missile controller, Position position, int elapsedFrames) {
    int infectedId = controller.attachedEntityId;
    if (infectedId < 0 || !world.getEntityManager().isActive(infectedId)
        || !mPosition.has(infectedId) || !mUnitStates.has(infectedId)
        || mUnitStates.get(infectedId).stateList == null
        || !mUnitStates.get(infectedId).stateList.hasState(StateId.RABIES)) {
      log.info("[DRUID_RABIES] phase=controller_remove missileId={} infected={} reason=state_missing",
          entityId, infectedId);
      world.delete(entityId);
      return;
    }

    position.position.set(mPosition.get(infectedId).position);
    controller.remainingFrames -= Math.max(1, elapsedFrames);
    if (controller.remainingFrames <= 0) {
      world.delete(entityId);
      return;
    }
    int interval = controller.missile != null && controller.missile.Param != null
        && controller.missile.Param.length > 0 && controller.missile.Param[0] > 0
        ? controller.missile.Param[0] : 5;
    if (controller.nativeFrame < controller.rabiesNextPulseFrame) return;
    controller.rabiesNextPulseFrame = controller.nativeFrame + interval;
    int radius = controller.missile != null && controller.missile.Param != null
        && controller.missile.Param.length > 1 && controller.missile.Param[1] > 0
        ? controller.missile.Param[1] : 3;
    Array<Integer> targets = getEntitiesInRange(
        position.position.x, position.position.y, radius);
    for (int i = 0; i < targets.size; i++) {
      int targetId = targets.get(i);
      if (targetId == infectedId || targetId == controller.rabiesSourceId
          || !mPosition.has(targetId) || !mUnitStates.has(targetId)
          || !mAttributesWrapper.has(targetId)
          || !isEnemy(controller.rabiesSourceId, targetId)) continue;
      UnitStates targetStates = mUnitStates.get(targetId);
      if (targetStates.stateList == null) targetStates.init(targetId);
      if (targetStates.stateList.hasState(StateId.RABIES)) continue;
      Attributes attrs = mAttributesWrapper.get(targetId).attrs;
      StatRef hp = attrs.get(Stat.hitpoints, StatRef.obtain());
      if (hp == null || hp.asFixed() <= 0f) continue;
      int duration = Math.max(10, controller.remainingFrames);
      int difficulty = combatDifficulty(controller.rabiesSourceId, targetId);
      CombatSystem.CombatResult poison =
          CombatSystem.INSTANCE.calculateFixedPoisonDamageSnapshot(
              attrs, mPlayer.has(targetId), controller.rabiesAttackerPlayer,
              controller.rabiesRawDamageFixed, controller.rabiesPoisonPierce,
              duration, targetStates.stateList, difficulty);
      UnitState infected = targetStates.stateList.addState(
          StateId.RABIES, duration, Math.max(1, controller.damageLevel),
          controller.rabiesSourceId);
      if (infected == null) continue;
      infected.skillId = controller.skillId;
      infected.needsSync = true;
      if (poison.poisonDamagePerFrame > 0f && poison.poisonDuration > 0) {
        StatusEffectApplier.INSTANCE.applyPoison(
            targetId, poison.poisonDamagePerFrame, poison.poisonDuration,
            controller.rabiesSourceId);
      }
      spawnRabiesContagionVisual(controller, position.position,
          targetId, mPosition.get(targetId).position);
      createRabiesControllerChild(controller, targetId, duration);
      log.info("[DRUID_RABIES] phase=spread source={} from={} target={} remaining={} "
              + "rawFixed={} damagePerFrame={} poisonDuration={} pierce={} "
              + "targetPlayer={} difficulty={} radius={}", controller.rabiesSourceId,
          infectedId, targetId, duration, controller.rabiesRawDamageFixed,
          poison.poisonDamagePerFrame, poison.poisonDuration,
          controller.rabiesPoisonPierce, mPlayer.has(targetId), difficulty, radius);
    }
  }

  private void spawnRabiesContagionVisual(
      Missile controller, Vector2 origin, int targetId, Vector2 target) {
    if (factory == null || controller == null || controller.missile == null
        || controller.missile.SubMissile == null
        || controller.missile.SubMissile.length == 0) return;
    String name = controller.missile.SubMissile[0];
    Missiles.Entry row = name != null ? Riiablo.files.Missiles.get(name) : null;
    if (row == null) return;
    Vector2 direction = new Vector2(target).sub(origin);
    if (direction.isZero(0.0001f)) direction.set(1f, 0f);
    int id = factory.createMissile(row, direction.nor(), origin, controller.rabiesSourceId);
    if (id < 0 || !mMissile.has(id)) return;
    Missile visual = mMissile.get(id);
    visual.skillId = controller.skillId;
    visual.damageLevel = controller.damageLevel;
    visual.rabiesContagionVisual = true;
    visual.attachedEntityId = controller.attachedEntityId;
    visual.targetId = targetId;
    visual.homing = true;
    log.info("[DRUID_RABIES] phase=contagion_create source={} missileId={} missile={} "
            + "origin=({}, {}) target=({}, {})", controller.rabiesSourceId, id,
        row.Missile, origin.x, origin.y, target.x, target.y);
  }

  private void createRabiesControllerChild(Missile parent, int infectedId, int duration) {
    if (factory == null || parent == null || parent.missile == null || !mPosition.has(infectedId)) {
      return;
    }
    int id = factory.createMissile(parent.missile, Vector2.X,
        mPosition.get(infectedId).position, parent.rabiesSourceId);
    if (id < 0 || !mMissile.has(id)) return;
    Missile child = mMissile.get(id);
    child.skillId = parent.skillId;
    child.damageLevel = parent.damageLevel;
    child.attached = true;
    child.attachedEntityId = infectedId;
    child.rabiesController = true;
    child.rabiesSourceId = parent.rabiesSourceId;
    child.remainingFrames = duration;
    child.rabiesNextPulseFrame = child.nativeFrame + 1;
    child.damageMultiplier = parent.damageMultiplier;
    copyRabiesCastSnapshot(parent, child);
  }

  static void copyRabiesCastSnapshot(Missile parent, Missile child) {
    if (parent == null || child == null) return;
    child.rabiesRawDamageFixed = parent.rabiesRawDamageFixed;
    child.rabiesPoisonPierce = parent.rabiesPoisonPierce;
    child.rabiesAttackerPlayer = parent.rabiesAttackerPlayer;
  }

  /** D2MOO MISSMODE_SrvDo31: a maker reaching its path end emits two waves. */
  private void processWakeMaker(int entityId, Missile maker, Position position,
      Velocity velocity) {
    if (maker.wakeSpawned) return;
    float dx = maker.wakeTargetX - position.position.x;
    float dy = maker.wakeTargetY - position.position.y;
    float distance = (float) Math.sqrt(dx * dx + dy * dy);
    float step = velocity.velocity.len() * Math.max(0f, world.delta);
    if (distance > Math.max(0.35f, step)) return;
    maker.wakeSpawned = true;
    if (factory == null || maker.missile == null
        || maker.missile.SubMissile == null || maker.missile.SubMissile.length == 0) {
      log.warn("[WAKE_OF_FIRE] phase=maker_stall missileId={} owner={} reason=missing_submissile",
          entityId, maker.ownerId);
      world.delete(entityId);
      return;
    }
    String waveName = maker.missile.SubMissile[0];
    Missiles.Entry wave = waveName != null && !waveName.isEmpty()
        ? Riiablo.files.Missiles.get(waveName) : null;
    if (wave == null) {
      log.warn("[WAKE_OF_FIRE] phase=maker_stall missileId={} owner={} reason=unknown_submissile name={}",
          entityId, maker.ownerId, waveName);
      world.delete(entityId);
      return;
    }
    Vector2 direction = new Vector2(maker.wakeDirectionX, maker.wakeDirectionY);
    if (direction.isZero(0.0001f)) direction.set(Vector2.Y);
    direction.nor();
    int spawned = 0;
    Skills.Entry skill = maker.skillId >= 0 ? Riiablo.files.skills.get(maker.skillId) : null;
    int damageOwnerId = maker.damageOwnerId >= 0 ? maker.damageOwnerId : maker.ownerId;
    Attributes ownerAttrs = damageOwnerId >= 0 && mAttributesWrapper.has(damageOwnerId)
        ? mAttributesWrapper.get(damageOwnerId).attrs : null;
    for (int sign : new int[] {1, -1}) {
      int childId = factory.createMissile(wave,
          new Vector2(direction).scl(sign), position.position, damageOwnerId);
      if (childId < 0 || !mMissile.has(childId)) continue;
      Missile child = mMissile.get(childId);
      child.skillId = maker.skillId;
      child.damageLevel = Math.max(1, maker.damageLevel);
      if (skill != null) {
        MissileDamageResolver.initializeSkill(child, skill, ownerAttrs, child.damageLevel);
      }
      spawned++;
    }
    log.info("[WAKE_OF_FIRE] phase=wave_spawn maker={} owner={} wave={} origin=({}, {}) "
            + "directions=2 spawned={} skill={}",
        entityId, damageOwnerId, wave.Missile, position.position.x, position.position.y,
        spawned, maker.skillId);
    world.delete(entityId);
  }

  /**
   * D2MOO keeps missile events authoritative even outside client sight, but
   * UNITS_GetRoom follows every room crossing. Town entry removes missiles
   * whose Missiles.txt Town flag is not set (MISSMODE_SrvDo02).
   */
  private boolean updateNativeRoom(int entityId, Missile missile, Position position) {
    if (!mMapWrapper.has(entityId)) return true;
    MapWrapper wrapper = mMapWrapper.get(entityId);
    Map map = wrapper.map;
    if (map == null) return true;

    Map.Zone zone = wrapper.zone;
    Map.RoomEx room = zone != null
        ? zone.findRoomEx(position.position.x, position.position.y) : null;
    if (room == null) {
      zone = map.getZone(position.position);
      room = zone != null ? zone.findRoomEx(position.position.x, position.position.y) : null;
      wrapper.zone = zone;
    }
    int nextRoomId = room != null ? room.id : -1;
    if (nextRoomId != missile.roomId) {
      log.debug("[MISSILE_ROOM] missileId={} missile={} level={} fromRoom={} toRoom={} pos=({}, {})",
          entityId, missile.missile != null ? missile.missile.Missile : "unknown",
          zone != null && zone.level != null ? zone.level.Id : -1,
          missile.roomId, nextRoomId, position.position.x, position.position.y);
      missile.roomId = nextRoomId;
    }
    if (zone != null && zone.isTown() && missile.missile != null && !missile.missile.Town) {
      log.debug("[MISSILE_ROOM] missileId={} missile={} level={} room={} action=remove_town",
          entityId, missile.missile.Missile,
          zone.level != null ? zone.level.Id : -1, missile.roomId);
      world.delete(entityId);
      return false;
    }
    return true;
  }

  /**
   * D2MOO's {@code MISSMODE_HandleMissileCollision} tests the movement path
   * against the collision mask selected by {@code CollideType}.  The old
   * implementation only checked units, allowing arrows and fireballs to pass
   * through missile barriers and walls.  Keep this check before unit broad
   * phase so a barrier hit cannot also damage a unit behind it.
   */
  private boolean checkNativeMapCollision(int entityId, Missile missile,
      Vector2 from, Vector2 to) {
    if (missile == null || missile.missile == null || from == null || to == null
        || from.epsilonEquals(to, 0.0001f)) return false;
    MapWrapper wrapper = mMapWrapper.has(entityId) ? mMapWrapper.get(entityId) : null;
    Map map = wrapper != null ? wrapper.map : null;
    if (map == null || map.getZone(from) == null) return false;

    int mask = nativeMapCollisionMask(missile.missile.CollideType);
    if (mask == 0) return false;
    wallRay.set(from, to);
    if (!map.castRay(wallRay, mask, 0, wallCollision)) return false;

    Vector2 impact = wallCollision.point;
    if (impact != null && !impact.isZero(0.0001f)) {
      // Raycast points are already in world subtiles.  Keep the missile on
      // the impact edge for the explosion callback and diagnostics.
      mPosition.get(entityId).position.set(impact);
    }
    log.debug("[MISSILE_MAP_HIT] missileId={} missile={} owner={} collideType={} "
            + "mask=0x{} from=({}, {}) to=({}, {}) impact=({}, {}) canDestroy={} "
            + "collideKill={}",
        entityId, missile.missile.Missile, missile.ownerId,
        missile.missile.CollideType, Integer.toHexString(mask), from.x, from.y,
        to.x, to.y, impact != null ? impact.x : to.x, impact != null ? impact.y : to.y,
        missile.missile.CanDestroy, missile.missile.CollideKill);
    spawnNativeMapExplosion(missile, impact != null && !impact.isZero(0.0001f) ? impact : to);
    // Native SrvDmgHitHandler is invoked with a null target for barrier/wall
    // collisions.  It consumes the travelling missile even when CollideKill
    // is clear; CollideKill controls unit-hit persistence, not map barriers.
    world.delete(entityId);
    return true;
  }

  /** Maps Missiles.txt CollideType to the low DT1 collision bits. */
  static int nativeMapCollisionMask(int collideType) {
    switch (collideType) {
      case 1: // player + missile barrier
      case 2: // monster + missile barrier
      case 3: // player/monster + missile barrier
      case 5: // monster + missile barrier
      case 6: // missile barrier only
        return DT1.Tile.FLAG_BLOCK_JUMP;
      case 8: // player/monster + missile barrier + wall
        return DT1.Tile.FLAG_BLOCK_JUMP | DT1.Tile.FLAG_BLOCK_WALK;
      default:
        // 0/4 are no-collision modes; 7 collides with other missiles only.
        return 0;
    }
  }
  
  /**
   * 检查碰撞
   */
  private void checkCollisions(int missileId, Missile missile, Position missilePos, Vector2 lastPos) {
    if (missile.ownerId < 0) {
      log.debug("Missile {} has no owner (ownerId={}), skipping collision check", missileId, missile.ownerId);
      return; // 无拥有者，跳过碰撞检测
    }
    
    // 获取导弹当前位置
    Vector2 currentPos = missilePos.position;
    boolean areaEffect = isNativeAreaEffect(missile) || missile.persistent;
    log.trace("Missile {} checking collisions at ({}, {}), ownerId={}", missileId, currentPos.x, currentPos.y, missile.ownerId);
    
    // 检查与玩家的碰撞
    // 使用 ECS 查询找到玩家实体
    com.artemis.AspectSubscriptionManager subscriptionManager = world.getAspectSubscriptionManager();
    com.artemis.EntitySubscription playerSubscription = subscriptionManager.get(Aspect.all(Player.class, Position.class));
    IntBag playerEntities = playerSubscription.getEntities();
    
    for (int i = 0; i < playerEntities.size(); i++) {
      int playerId = playerEntities.get(i);
      if (playerId == missile.ownerId) {
        log.trace("Missile {} skipping owner {}", missileId, playerId);
        continue; // 跳过拥有者
      }
      Position playerPos = mPosition.get(playerId);
      float distance = currentPos.dst(playerPos.position);
      log.trace("Missile {} checking player {} at distance {}", missileId, playerId, distance);
      if (checkCollisionWithEntity(missileId, missile, lastPos, currentPos, playerId, playerPos)) {
        if (!areaEffect) return; // 普通导弹命中后销毁；爆炸会遍历完整半径
      }
    }
    
    // 检查与所有怪物的碰撞
    // 注意：这里简化处理，实际应该使用空间分区或更高效的查询
    // 为了性能，可以限制检查范围（例如只检查附近的怪物）
    // Include the whole swept segment in the broad-phase query. Without this,
    // a fast missile can pass a target between ticks while the target is no
    // longer within the radius of the missile's end point.
    float checkRadius = (areaEffect ? nativeAreaRadius(missile) : 2.0f)
        + currentPos.dst(lastPos);
    Array<Integer> nearbyEntities = getEntitiesInRange(currentPos.x, currentPos.y, checkRadius);
    
    for (int i = 0; i < nearbyEntities.size; i++) {
      int targetId = nearbyEntities.get(i);
      if (targetId == missileId || targetId == missile.ownerId) {
        continue; // 跳过自己和自己
      }
      
      if (mMonster.has(targetId) && mPosition.has(targetId)) {
        if (mNativeUnitFlags.has(targetId)
            && !NativeTargeting.isValidCombatTarget(mNativeUnitFlags.get(targetId))) {
          continue;
        }
        Position targetPos = mPosition.get(targetId);
        if (checkCollisionWithEntity(missileId, missile, lastPos, currentPos, targetId, targetPos)) {
          if (!areaEffect) return; // 爆炸子导弹必须命中范围内的每个敌人
        }
      }
    }
    checkMissileDestruction(missileId, missile, lastPos, currentPos);
    if (areaEffect && !missile.persistent && collidesKill(missile)) world.delete(missileId);
  }

  /** D2MOO CollideType 7: missiles marked CanDestroy are valid targets. */
  private void checkMissileDestruction(int missileId, Missile source,
      Vector2 previousPos, Vector2 currentPos) {
    if (source == null || source.missile == null || source.missile.CollideType != 7) return;
    com.artemis.AspectSubscriptionManager subscriptions = world.getAspectSubscriptionManager();
    com.artemis.EntitySubscription missiles = subscriptions.get(
        Aspect.all(Missile.class, Position.class, Velocity.class));
    IntBag entities = missiles.getEntities();
    for (int i = 0; i < entities.size(); i++) {
      int targetId = entities.get(i);
      if (targetId == missileId || !mMissile.has(targetId) || !mPosition.has(targetId)) continue;
      Missile target = mMissile.get(targetId);
      if (!target.authoritative || target.missile == null || !target.missile.CanDestroy) continue;
      float distance = distanceToSegment(mPosition.get(targetId).position, previousPos, currentPos);
      if (distance > 2.0f) continue;
      log.info("[MISSILE_DESTROY] source={} target={} sourceMissile={} targetMissile={} distance={}",
          missileId, targetId, source.missile.Missile, target.missile.Missile, distance);
      world.delete(targetId);
      if (collidesKill(source)) {
        world.delete(missileId);
        return;
      }
    }
  }
  
  /**
   * 检查与特定实体的碰撞
   * @return true 如果发生碰撞并处理了伤害
   */
  private boolean checkCollisionWithEntity(int missileId, Missile missile, Vector2 previousPos, Vector2 missilePos,
      int targetId, Position targetPos) {
    // Use swept segment collision so fast missiles cannot jump over a target.
    float distance = distanceToSegment(targetPos.position, previousPos, missilePos);
    float collisionRadius = isNativeAreaEffect(missile)
        ? nativeAreaRadius(missile) : 2.0f;
    
    // Debug log disabled to reduce noise
    // log.debug("Missile {} checking collision with {}: distance={}, radius={}, missilePos=({}, {}), targetPos=({}, {})", 
    //     missileId, targetId, distance, collisionRadius, 
    //     missilePos.x, missilePos.y, targetPos.position.x, targetPos.position.y);
    
    if (distance <= collisionRadius) {
      int hitFunction = missile.missile != null ? missile.missile.pSrvHitFunc : 0;
      if (hitFunction == 17 || hitFunction == 18 || hitFunction == 21) {
        handleWarCryCollision(missileId, missile, targetId, hitFunction);
        // Howl and shout waves have CollideKill=0 and continue through the
        // whole ring. They are state carriers, never ordinary damage packets.
        return false;
      }
      if (hitFunction == 7) {
        if (canHolyBoltHeal(missile, targetId)) {
          if (!claimTargetHit(missile, targetId, targetHitStates(missile, targetId))) {
            return false;
          }
          healHolyBoltTarget(missileId, missile, targetId);
          if (!missile.persistent && collidesKill(missile)) world.delete(missileId);
          return true;
        }
        if (!holyBoltCanDamage(missile, targetId)) return false;
      }
      // 检查是否是敌人
      if (!isEnemy(missile.ownerId, targetId)) {
        return false;
      }
      if (mNativeUnitFlags.has(targetId)
          && !NativeTargeting.isValidCombatTarget(mNativeUnitFlags.get(targetId))) {
        log.debug("[MISSILE_HIT] phase=skip_native_target missileId={} owner={} target={} flags=0x{}",
            missileId, missile.ownerId, targetId,
            Integer.toHexString(mNativeUnitFlags.get(targetId).flags()));
        return false;
      }
      if (missile.rabiesContagionVisual && targetId != missile.targetId) {
        return false;
      }
      if (missile.rabiesContagionVisual) {
        log.info("[DRUID_RABIES] phase=contagion_arrive source={} missileId={} target={}",
            missile.ownerId, missileId, targetId);
        world.delete(missileId);
        return true;
      }
      if (!claimTargetHit(missile, targetId, targetHitStates(missile, targetId))) return false;

      // Native MISSMODE_SrvHit29 does not apply the root row as damage.  It
      // fans out the authoritative frozen-orb nova shards; each shard then
      // resolves the Frozen Orb cold packet independently.
      if (missile.missile != null && missile.missile.pSrvHitFunc == 29) {
        spawnFrozenOrbNova(missile, missilePos);
        log.info("[SORCERESS_FROZEN_ORB] phase=root_hit missileId={} owner={} target={} "
                + "position=({}, {})", missileId, missile.ownerId, targetId,
            missilePos.x, missilePos.y);
        if (!missile.attached) world.delete(missileId);
        return true;
      }
      if (mMercenary.has(missile.ownerId)) mercenaryCollisionCount++;

      log.info("[MISSILE_HIT] phase=collision missileId={} missile={} owner={} target={} "
              + "distance={} radius={} position=({}, {}) traveled={} range={}",
          missileId, missile.missile != null ? missile.missile.Missile : "unknown",
          missile.ownerId, targetId, distance, collisionRadius,
          missilePos.x, missilePos.y, missile.distanceTraveled, missile.range);

      if (missile.missile != null && missile.missile.pSrvHitFunc == 4) {
        spawnAmazonExplosion(missile, missilePos);
      }
      if (missile.missile != null && missile.missile.pSrvHitFunc == 9) {
        spawnImmolationFire(missile, missilePos);
      }
      if (missile.missile != null && missile.missile.pSrvHitFunc == 2) {
        spawnPoisonCloud(missile, missilePos);
      }

      if (missile.missile != null && missile.missile.pSrvHitFunc == 20) {
        spawnLightningFuryBolts(missile, missilePos, targetId);
      }

      if (missile.missile != null && missile.missile.pSrvHitFunc == 14
          && !missile.hitFunctionTriggered) {
        missile.hitFunctionTriggered = true;
        spawnRoyalStrikeMeteorFire(missile, missilePos);
      }

      if (!mAttributesWrapper.has(missile.ownerId) || !mAttributesWrapper.has(targetId)) {
        log.warn("Missile {} collided with entity {} without complete combat attributes", missileId, targetId);
        if (!missile.attached) world.delete(missileId);
        return true;
      }

      Attributes ownerAttrs = mAttributesWrapper.get(missile.ownerId).attrs;
      Attributes targetAttrs = mAttributesWrapper.get(targetId).attrs;
      Attributes attackAttrs = missile.damageSnapshot ? missile.damage : ownerAttrs;
      StatRef targetHitpoints = targetAttrs.get(Stat.hitpoints, StatRef.obtain());
      if (targetHitpoints == null || targetHitpoints.asFixed() <= 0f) {
        // A dead entity may remain in the ECS until its death animation and
        // reward processing finish.  Do not resolve additional missiles
        // against it, otherwise one cast can award experience repeatedly.
        log.info("[MISSILE_HIT] phase=skip_dead missileId={} owner={} target={} targetHp={}",
            missileId, missile.ownerId, targetId,
            targetHitpoints != null ? targetHitpoints.asFixed() : 0f);
        if (!missile.attached) world.delete(missileId);
        return true;
      }
      if (missile.fixedPoisonRate) {
        resolveFixedPoisonCloud(missileId, missile, targetId, targetAttrs);
        return true;
      }
      if (missile.fixedElementalRate) {
        resolveFixedElementalRate(missileId, missile, targetId, targetAttrs);
        return true;
      }
      log.info("[MISSILE_HIT] phase=stats missileId={} owner={} target={} "
              + "snapshot={} toHit={} throwMin={} throwMax={} weaponMin={} weaponMax={} "
              + "attackRating={} profileMin={} profileMax={} profileAr={} "
              + "fire={}..{} lightning={}..{} cold={}..{} poison={}..{} magic={}..{} "
              + "targetDefense={} targetHp={}",
          missileId, missile.ownerId, targetId,
          missile.damageSnapshot,
          missile.missile != null && missile.missile.ToHit,
          statInt(ownerAttrs, Stat.item_throw_mindamage),
          statInt(ownerAttrs, Stat.item_throw_maxdamage),
          statInt(ownerAttrs, Stat.mindamage), statInt(ownerAttrs, Stat.maxdamage),
          statInt(ownerAttrs, Stat.tohit),
          missile.attackMinDamage, missile.attackMaxDamage, missile.attackRating,
          statInt(attackAttrs, Stat.firemindam), statInt(attackAttrs, Stat.firemaxdam),
          statInt(attackAttrs, Stat.lightmindam), statInt(attackAttrs, Stat.lightmaxdam),
          statInt(attackAttrs, Stat.coldmindam), statInt(attackAttrs, Stat.coldmaxdam),
          statInt(attackAttrs, Stat.poisonmindam), statInt(attackAttrs, Stat.poisonmaxdam),
          statInt(attackAttrs, Stat.magicmindam), statInt(attackAttrs, Stat.magicmaxdam),
          statInt(targetAttrs, Stat.armorclass),
          targetHitpoints.asFixed());
      int minOverride = missile.damageSnapshot ? 0 : missile.attackMinDamage;
      int maxOverride = missile.damageSnapshot ? 0 : missile.attackMaxDamage;
      int arOverride = missile.damageSnapshot ? 0 : missile.attackRating;
      boolean alwaysHit = missile.damageSnapshot && missile.missile != null
          && !missile.missile.ToHit && !missile.usesAttackRating;
      CombatSystem.CombatResult combat = CombatSystem.INSTANCE.calculateAttackAtDifficulty(
          attackAttrs,
          targetAttrs,
          mPlayer.has(missile.ownerId),
          mPlayer.has(targetId),
          true,
          minOverride,
          maxOverride,
          arOverride,
          alwaysHit,
          null, null, 0, 0,
          stateList(missile.ownerId), stateList(targetId), isEntityMoving(targetId),
          missileMastery(missile), combatDifficulty(missile.ownerId, targetId),
          blessedHammerTargetBonusPercent(
              missile, mMonster.has(targetId) ? mMonster.get(targetId) : null),
          mMonster.has(targetId) && mMonster.get(targetId).monstats != null
              && mMonster.get(targetId).monstats.demon,
          mMonster.has(targetId) && isUndead(mMonster.get(targetId)));
      boolean damageHit = combat.hit && !combat.blocked;
      if (!combat.hit) {
        log.info("[MISSILE_HIT] phase=result missileId={} owner={} target={} result=miss chance={} damage=0",
            missileId, missile.ownerId, targetId, combat.hitChance);
        log.debug("Missile {} ranged miss on {} (owner={}, hitChance={}%)",
            missileId, targetId, missile.ownerId, combat.hitChance);
      } else if (combat.blocked) {
        log.info("[MISSILE_HIT] phase=result missileId={} owner={} target={} result=blocked chance={} damage=0",
            missileId, missile.ownerId, targetId, combat.hitChance);
        log.debug("Missile {} attack blocked by {} (owner={})", missileId, targetId, missile.ownerId);
        queueHitReaction(targetId, true);
      } else {
        float damage = combat.totalDamage * Math.max(0.01f, missile.damageMultiplier);
        log.info("[MISSILE_HIT] phase=result missileId={} owner={} target={} result=hit chance={} "
                + "physical={} total={} critical={} deadly={} crushing={}",
            missileId, missile.ownerId, targetId, combat.hitChance,
            combat.physicalDamage, damage, combat.critical, combat.deadlyStrike,
            combat.crushingBlow);
        if (damage > 0 && mAttributesWrapper.has(targetId)) {
          log.info("Missile {} hits {} for {} damage (ownerId={}, critical={}, deadly={})",
              missileId, targetId, damage, missile.ownerId, combat.critical, combat.deadlyStrike);
          // 使用 get(stat, dst) 接口避免重用问题
          StatRef hitpoints = targetAttrs.get(Stat.hitpoints, StatRef.obtain());
          if (hitpoints == null) {
            log.warn("{} has no hitpoints stat", targetId);
            return true; // 返回 true 表示已处理（虽然无法造成伤害）
          }
          String hitSound = missile.missile != null ? missile.missile.HitSound : null;
          log.info("[MISSILE_SOUND] phase=hit missileId={} missile={} target={} hitSound={} playedBy=DamageHandler",
              missileId,
              missile.missile != null ? missile.missile.Missile : "unknown",
              targetId,
              hitSound == null ? "" : hitSound);
          DamageEvent event = DamageEvent.obtainMissile(
              missile.ownerId, targetId, damage,
              combat.physicalDamage * Math.max(0.01f, missile.damageMultiplier), hitSound)
              .withReturnFire(missile.missile != null && missile.missile.ReturnFire);
          events.dispatch(event);
          float appliedDamage = Math.max(0f, event.damage);
          // Native elemental absorb restores the defender's life from the same
          // post-multiplier packet that produced this hit.  Apply it before
          // subtracting damage so a target at low life can survive an absorbed
          // elemental missile, and clamp to maxhp just like D2Game.
          if (combat.absorbedLife > 0) {
            StatRef maxHitpoints = targetAttrs.get(Stat.maxhp, StatRef.obtain());
            float maxLife = maxHitpoints != null ? Math.max(0f, maxHitpoints.asFixed()) : Float.MAX_VALUE;
            float requestedHeal = combat.absorbedLife * Math.max(0.01f, missile.damageMultiplier);
            float healed = Math.max(0f, Math.min(requestedHeal, maxLife - hitpoints.asFixed()));
            if (healed > 0f) {
              hitpoints.add(healed);
              log.info("[MISSILE_ABSORB] missileId={} target={} absorbed={} healed={} hp={}/{}",
                  missileId, targetId, combat.absorbedLife, healed,
                  hitpoints.asFixed(), maxLife);
            }
          }
          boolean mercenaryDamage = appliedDamage > 0f && mMercenary.has(missile.ownerId);
          if (mercenaryDamage) {
            mercenaryDamageCount++;
            mercenaryLastDamageTarget = targetId;
            mercenaryLastDamageBefore = hitpoints.asFixed();
          }
          hitpoints.sub(appliedDamage);
          float hpAfter = hitpoints.asFixed();
          if (hpAfter < 0) {
            hitpoints.set(0);
            hpAfter = 0;
          }
          if (hpAfter > 0f) queueHitReaction(targetId, false);
          if (mercenaryDamage) mercenaryLastDamageAfter = hpAfter;
          if (hpAfter <= 0) {
            log.debug("{} killed by missile from {}", targetId, missile.ownerId);
            events.dispatch(DeathEvent.obtain(missile.ownerId, targetId));
          }
        }
        // Poison is intentionally excluded from immediate totalDamage and is
        // resolved by StateUpdater over poisonDuration.  Apply hit states even
        // when this is a pure poison cloud whose immediate damage is zero.
        applyCombatStates(missile, targetId, combat);
      }

      if (damageHit) applyNativeMissileDamageState(missileId, missile, targetId);

      if (damageHit && isChainLightningMissile(missile)) {
        spawnChainLightningContinuation(missile, targetId, targetPos.position);
      }
      
      // Native Pierce keeps the missile alive after a successful collision.
      // A miss/block still consumes the projectile, while a dead target is
      // handled by the normal death path above.
      if (missile.attached) return true;
      if (damageHit && missile.pierceEnabled && missile.pierceChance > 0
          && rollPierce(missile, missile.pierceChance)) {
        log.info("[MISSILE_PIERCE] phase=continue missileId={} target={} chance={} hitCount={}",
            missileId, targetId, missile.pierceChance, missile.hitTargets.size);
        return true;
      }
      if (!missile.persistent && collidesKill(missile)) world.delete(missileId);
      return true;
    }
    
    return false;
  }

  /** Native SrvHit07 pet/ally branch. */
  private boolean canHolyBoltHeal(Missile missile, int targetId) {
    if (missile == null || missile.missile == null
        || arrayValue(missile.missile.sHitPar, 0) == 0
        || missile.ownerId < 0 || targetId == missile.ownerId
        || !mAttributesWrapper.has(targetId) || !isAlive(targetId)
        || !areAligned(missile.ownerId, targetId)) return false;
    Skills.Entry skill = missile.skillId >= 0 ? Riiablo.files.skills.get(missile.skillId) : null;
    if (skill == null || Riiablo.files.NativeSkills == null) return false;
    com.riiablo.codec.excel.NativeSkills.Entry nativeSkill =
        Riiablo.files.NativeSkills.get(skill.Id);
    if (nativeSkill == null) return false;
    boolean pet = mMercenary.has(targetId) || mSummonedPet.has(targetId);
    boolean ally = mPlayer.has(targetId) || pet;
    return (pet && nativeSkill.bool("TargetPet"))
        || (ally && nativeSkill.bool("TargetAlly"));
  }

  /** Native SrvHit07 target-type branch; false means the bolt keeps flying. */
  private boolean holyBoltCanDamage(Missile missile, int targetId) {
    int mode = arrayValue(missile != null && missile.missile != null
        ? missile.missile.sHitPar : null, 1);
    return holyBoltCanDamage(mode, mPlayer.has(targetId),
        mMonster.has(targetId) ? mMonster.get(targetId) : null);
  }

  static boolean holyBoltCanDamage(int mode, boolean player, Monster monster) {
    if (player) return mode == 0;
    if (monster == null) return false;
    if (mode == 0) return true;
    if (monster.monstats == null) return false;
    return mode == 2 ? monster.monstats.demon : isUndead(monster);
  }

  private void healHolyBoltTarget(int missileId, Missile missile, int targetId) {
    Skills.Entry skill = missile.skillId >= 0 ? Riiablo.files.skills.get(missile.skillId) : null;
    int level = Math.max(1, missile.damageLevel);
    int[] healing = PaladinSkills.getHolyBoltHealing(
        skill, level, name -> baseSkillLevel(missile.ownerId, name));
    int amount = healing[0];
    if (healing[1] > healing[0]) {
      NativeRng rng = new NativeRng(missile.rngState);
      amount += rng.nextInt(healing[1] - healing[0]);
      missile.rngState = rng.state();
    }
    Attributes target = mAttributesWrapper.get(targetId).attrs;
    StatRef life = target != null ? target.get(Stat.hitpoints, StatRef.obtain()) : null;
    StatRef maximum = target != null ? target.get(Stat.maxhp, StatRef.obtain()) : null;
    if (life == null || maximum == null) return;
    float before = life.asFixed();
    float after = Math.min(maximum.asFixed(), before + Math.max(0, amount));
    life.set(after);
    log.info("[HOLY_BOLT_HEAL] missileId={} missile={} owner={} target={} skill={} "
            + "level={} rollRange={}..{} requested={} applied={} hp={}/{} overlay={}",
        missileId, missile.missile.Missile, missile.ownerId, targetId,
        skill != null ? skill.skill : "", level, healing[0], healing[1], amount,
        after - before, after, maximum.asFixed(), missile.missile.ProgOverlay);
  }

  /** Resolves skill poison stored by D2 as an 8.8 per-frame rate. */
  private void resolveFixedPoisonCloud(
      int missileId, Missile missile, int targetId, Attributes targetAttrs) {
    int min = Math.max(0, missile.poisonMinRateFixed);
    int max = Math.max(min, missile.poisonMaxRateFixed);
    NativeRng rng = new NativeRng(missile.rngState);
    int raw = min;
    if (max > min) raw += rng.nextInt(max - min);
    missile.rngState = rng.state();
    if (!mUnitStates.has(targetId)) mUnitStates.create(targetId).init(targetId);
    StateList targetStates = stateList(targetId);
    CombatSystem.CombatResult poison =
        CombatSystem.INSTANCE.calculateFixedPoisonDamageSnapshot(
            targetAttrs, mPlayer.has(targetId), missile.poisonAttackerPlayer,
            raw, missile.poisonPiercePercent,
            Math.max(1, missile.poisonDurationFrames), targetStates,
            combatDifficulty(missile.ownerId, targetId));
    if (poison.poisonDamagePerFrame > 0f && poison.poisonDuration > 0) {
      StatusEffectApplier.INSTANCE.applyPoison(
          targetId, poison.poisonDamagePerFrame, poison.poisonDuration, missile.ownerId);
    }
    log.info("[POISON_CLOUD] phase=apply missileId={} owner={} target={} skill={} "
            + "rawFixed={} damagePerFrame={} duration={} pierce={}",
        missileId, missile.ownerId, targetId, missile.skillId, raw,
        poison.poisonDamagePerFrame, poison.poisonDuration,
        missile.poisonPiercePercent);
  }

  /** Resolves one 8.8 game-frame hit from Blaze or Fire Wall. */
  private void resolveFixedElementalRate(
      int missileId, Missile missile, int targetId, Attributes targetAttrs) {
    int min = Math.max(0, missile.elementalMinRateFixed);
    int max = Math.max(min, missile.elementalMaxRateFixed);
    NativeRng rng = new NativeRng(missile.rngState);
    int raw = min;
    if (max > min) raw += rng.nextInt(max - min);
    missile.rngState = rng.state();
    CombatSystem.FixedElementalDamageResult combat =
        CombatSystem.INSTANCE.calculateFixedElementalRateDamage(
            targetAttrs, mPlayer.has(targetId), missile.elementalAttackerPlayer,
            missile.fixedElementalType, raw, missile.elementalPiercePercent,
            missile.elementalDamageRate, stateList(targetId),
            combatDifficulty(missile.ownerId, targetId));
    StatRef life = targetAttrs.get(Stat.hitpoints, StatRef.obtain());
    if (life == null || life.asFixed() <= 0f) return;
    StatRef maximum = targetAttrs.get(Stat.maxhp, StatRef.obtain());
    float absorbed = combat.absorbedLifeFixed / 256f;
    if (absorbed > 0f && maximum != null) {
      life.add(Math.min(absorbed, Math.max(0f, maximum.asFixed() - life.asFixed())));
    }
    float damage = combat.damageFixed / 256f;
    if (damage <= 0f) return;
    DamageEvent event = DamageEvent.obtainMissile(
        missile.ownerId, targetId, damage, 0f,
        missile.missile != null ? missile.missile.HitSound : null)
        .withReturnFire(missile.missile != null && missile.missile.ReturnFire);
    events.dispatch(event);
    float applied = Math.max(0f, event.damage);
    float before = life.asFixed();
    life.sub(applied);
    if (life.asFixed() <= 0f) {
      life.set(0f);
      events.dispatch(DeathEvent.obtain(missile.ownerId, targetId));
    } else if (missile.missile != null && missile.missile.pSrvDmgFunc == 3) {
      // SrvDmg03 requests hit recovery with dParam1 / 128 probability.
      int chance = missile.missile.dParam != null && missile.missile.dParam.length > 0
          ? Math.max(0, missile.missile.dParam[0]) : 0;
      NativeRng recoveryRng = new NativeRng(missile.rngState);
      boolean recovery = (recoveryRng.nextInt() & 127) < chance;
      missile.rngState = recoveryRng.state();
      if (recovery) queueHitReaction(targetId, false);
    }
    log.info("[FIRE_AREA_HIT] missileId={} missile={} owner={} target={} skill={} "
            + "rawFixed={} damage={} absorbed={} hp={} -> {} damageRate={}",
        missileId, missile.missile != null ? missile.missile.Missile : "",
        missile.ownerId, targetId, missile.skillId, raw, applied, absorbed,
        before, life.asFixed(), missile.elementalDamageRate);
  }

  /** Native MISSMODE_SrvDo06 FireWallMaker child emission. */
  private void processFireWallMaker(int entityId, Missile maker, Position position,
      Velocity velocity) {
    if (factory == null || maker.missile == null || maker.missile.SubMissile == null
        || maker.missile.SubMissile.length == 0) {
      world.delete(entityId);
      return;
    }
    String childName = maker.missile.SubMissile[0];
    Missiles.Entry childRow = childName != null && !childName.isEmpty()
        ? Riiablo.files.Missiles.get(childName) : null;
    Skills.Entry skill = maker.skillId >= 0 ? Riiablo.files.skills.get(maker.skillId) : null;
    if (childRow == null || skill == null) {
      log.warn("[SORCERESS_FIRE_WALL] phase=maker_reject maker={} owner={} "
              + "child={} skill={}", entityId, maker.ownerId, childName, maker.skillId);
      world.delete(entityId);
      return;
    }

    Vector2 origin = new Vector2(position.position);
    if (maker.range > 0f && maker.distanceTraveled > maker.range
        && !velocity.velocity.isZero(0.0001f)) {
      origin.mulAdd(new Vector2(velocity.velocity).nor(),
          -(maker.distanceTraveled - maker.range));
    }
    int owner = maker.damageOwnerId >= 0 ? maker.damageOwnerId : maker.ownerId;
    int childId = factory.createMissile(childRow, Vector2.X, origin, owner);
    if (childId < 0 || !mMissile.has(childId)) return;
    Missile child = mMissile.get(childId);
    int level = Math.max(1, maker.damageLevel);
    child.persistent = true;
    child.remainingFrames = nativeMissileRange(childRow, level);
    child.tickInterval = 1;
    child.range = 0f;
    if (mVelocity.has(childId)) mVelocity.get(childId).velocity.setZero();
    Attributes ownerAttrs = mAttributesWrapper.has(owner)
        ? mAttributesWrapper.get(owner).attrs : null;
    MissileDamageResolver.initializeSorceressFireArea(
        child, skill, ownerAttrs, mPlayer.has(owner), level,
        name -> baseSkillLevel(owner, name), stateList(owner));
    maker.fireWallSegmentsCreated++;
    log.info("[SORCERESS_FIRE_WALL] phase=segment maker={} segment={} owner={} "
            + "skill={} level={} index={} position=({}, {}) lifetime={}",
        entityId, childId, owner, maker.skillId, level,
        maker.fireWallSegmentsCreated, origin.x, origin.y, child.remainingFrames);
  }

  private static int nativeMissileRange(Missiles.Entry row, int level) {
    if (row == null) return 0;
    long range = (long) row.Range + (long) Math.max(1, level) * row.LevRange;
    return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, range));
  }

  /**
   * Applies the native per-projectile/per-cast hit gate.
   *
   * <p>D2MOO stores {@code JUSTHIT} on the target Unit, not on one missile, so
   * every NextHit projectile observes the same target-wide delay. An attached
   * missile without NextHit may be evaluated again on the next game frame.
   * Non-attached missiles keep a lifetime hit set so a piercing path cannot
   * damage the same unit repeatedly. The shared cast set is checked last.</p>
   */
  static boolean claimTargetHit(Missile missile, int targetId, StateList targetStates) {
    if (missile == null || targetId < 0) return false;
    boolean nextHit = missile.missile != null && missile.missile.NextHit;
    if (nextHit && targetStates != null && targetStates.hasState(StateId.JUSTHIT)) return false;
    if (missile.attached) {
      if (!nextHit) {
        int nextFrame = missile.nextHitFrame.get(targetId, Integer.MIN_VALUE);
        if (missile.nativeFrame < nextFrame) return false;
        missile.nextHitFrame.put(targetId, missile.nativeFrame + 1);
      }
    } else if (!missile.hitTargets.add(targetId)) {
      // A piercing projectile must never repeatedly damage the same unit on
      // consecutive frames while its swept segment overlaps the target.
      return false;
    }
    if (missile.sharedHitTargets != null && !missile.sharedHitTargets.add(targetId)) return false;
    if (nextHit && targetStates != null) {
      targetStates.addState(StateId.JUSTHIT, Math.max(1, missile.missile.NextDelay),
          1, missile.ownerId);
    }
    return true;
  }

  private StateList targetHitStates(Missile missile, int targetId) {
    boolean nextHit = missile != null && missile.missile != null && missile.missile.NextHit;
    if (nextHit && !mUnitStates.has(targetId)) mUnitStates.create(targetId).init(targetId);
    return stateList(targetId);
  }

  /** Resolves the authoritative difficulty from the projectile's current map.
   *  Owner and target wrappers can be absent during cross-zone handoff, so use
   *  either side and fall back to Normal (the CombatSystem compatibility
   *  overload has the same default).
   */
  private int combatDifficulty(int ownerId, int targetId) {
    if (mMapWrapper.has(targetId)) {
      MapWrapper wrapper = mMapWrapper.get(targetId);
      if (wrapper != null && wrapper.map != null) return wrapper.map.getDifficulty();
    }
    if (mMapWrapper.has(ownerId)) {
      MapWrapper wrapper = mMapWrapper.get(ownerId);
      if (wrapper != null && wrapper.map != null) return wrapper.map.getDifficulty();
    }
    return 0;
  }

  static boolean hasNativeCollision(Missile missile) {
    // Synthetic/test missiles without a table row retain the legacy collision
    // behavior; real projectiles are strictly governed by Missiles.txt.
    if (missile == null) return false;
    if (missile.missile == null || missile.missile.Collision) return true;
    Missiles.Entry row = missile.missile;
    return row.pSrvHitFunc != 0 || row.pSrvDmgFunc != 0 || row.pSrvDoFunc != 0
        || row.Explosion != 0 || row.AlwaysExplode;
  }

  /** Native {@code MISSMODE_SrvDmg05_BlessedHammer}. */
  static int blessedHammerTargetBonusPercent(Missile missile, Monster target) {
    if (missile == null || missile.missile == null
        || missile.missile.pSrvDmgFunc != 5 || target == null
        || target.monstats == null || missile.missile.dParam == null) return 0;
    int bonus = 0;
    if ((target.monstats.lUndead || target.monstats.hUndead)
        && missile.missile.dParam.length > 0) {
      bonus += Math.max(0, missile.missile.dParam[0]);
    }
    if (target.monstats.demon && missile.missile.dParam.length > 1) {
      bonus += Math.max(0, missile.missile.dParam[1]);
    }
    return bonus;
  }

  static boolean collidesKill(Missile missile) {
    return missile == null || missile.missile == null || missile.missile.CollideKill;
  }

  static boolean hasLastCollide(Missile missile) {
    return missile != null && missile.missile != null && missile.missile.LastCollide;
  }

  /**
   * Native LastCollide is a single endpoint collision pass.  Keep the gate
   * explicit so range-boundary handling cannot fire for a non-colliding visual
   * missile or be evaluated twice when the final segment spans multiple
   * targets.
   */
  static boolean shouldResolveLastCollide(Missile missile, boolean atRange) {
    return atRange && missile != null && hasLastCollide(missile)
        && !missile.lastCollideResolved && hasNativeCollision(missile);
  }

  /** Computes the point at which a missile reaches its native range endpoint. */
  static Vector2 clampToRangeEndpoint(Vector2 start, Vector2 end,
      float distanceBefore, float moveDistance, float range, Vector2 out) {
    if (out == null) out = new Vector2();
    if (start == null || end == null || moveDistance <= 0f || range <= distanceBefore) {
      return out.set(end == null ? 0f : end.x, end == null ? 0f : end.y);
    }
    float fraction = Math.max(0f, Math.min(1f, (range - distanceBefore) / moveDistance));
    return out.set(start).lerp(end, fraction);
  }

  static boolean rollPierce(Missile missile, int chance) {
    if (chance >= 100) return true;
    NativeRng rng = new NativeRng(missile.rngState);
    boolean result = rng.roll(chance, 100);
    missile.rngState = rng.state();
    return result;
  }

  private static StateList.WeaponMasteryBonus missileMastery(Missile missile) {
    if (missile == null || missile.masteryAttackRatingPercent == 0
        && missile.masteryDamagePercent == 0 && missile.masteryCriticalChance == 0) return null;
    StateList.WeaponMasteryBonus mastery = new StateList.WeaponMasteryBonus();
    mastery.attackRatingPercent = missile.masteryAttackRatingPercent;
    mastery.damagePercent = missile.masteryDamagePercent;
    mastery.criticalChance = missile.masteryCriticalChance;
    return mastery;
  }

  /** Replicate native player/monster GH and BL reactions through CofReference. */
  private void queueHitReaction(int victimId, boolean blocked) {
    CofManager cofs = world.getSystem(CofManager.class);
    if (cofs == null || !mClass.has(victimId) || !mCofReference.has(victimId)
        || !mAnimData.has(victimId)) return;
    Class.Type type = mClass.get(victimId).type;
    if (type != Class.Type.PLR && type != Class.Type.MON) return;
    byte current = mCofReference.get(victimId).mode;
    if (type == Class.Type.PLR
        && (current == Engine.Player.MODE_DT || current == Engine.Player.MODE_DD)) return;
    if (type == Class.Type.MON
        && (current == Engine.Monster.MODE_DT || current == Engine.Monster.MODE_DD)) return;
    // Preserve an in-progress native skill sequence; Actioneer will restore
    // the reaction for idle victims only, matching D2MOO's soft-hit behavior.
    if (mSequence.has(victimId) && mSequence.get(victimId).started) return;
    byte reaction = type == Class.Type.PLR
        ? (blocked ? Engine.Player.MODE_BL : Engine.Player.MODE_GH)
        : (blocked ? Engine.Monster.MODE_BL : Engine.Monster.MODE_GH);
    byte neutral = type == Class.Type.PLR ? Engine.Player.MODE_NU : Engine.Monster.MODE_NU;
    mSequence.create(victimId).sequence(reaction, neutral);
    cofs.setMode(victimId, reaction, true);
    log.info("[HIT_REACTION] victim={} type={} mode={} blocked={} source=missile",
        victimId, type, blocked ? "BL" : "GH", blocked);
  }

  /** Applies post-damage state carried by native missile SrvDmg functions. */
  private void applyNativeMissileDamageState(int missileId, Missile missile, int targetId) {
    if (missile == null || missile.missile == null || missile.missile.pSrvDmgFunc != 7) return;
    Skills.Entry skill = missile.skillId >= 0 ? Riiablo.files.skills.get(missile.skillId) : null;
    boolean shockWave = DruidSkills.isShockWave(skill);
    int duration = shockWave
        ? DruidSkills.getShockWaveStunDuration(
            missile.missile, skill, Math.max(1, missile.damageLevel))
        : BarbarianSkills.getWarCryStunDuration(
            missile.missile, skill, Math.max(1, missile.damageLevel));
    String tag = shockWave ? "[DRUID_SHOCK_WAVE]" : "[BARBARIAN_WAR_CRY]";
    Monster monster = mMonster.has(targetId) ? mMonster.get(targetId) : null;
    int uniqueRoll = monster != null && MonsterRank.isUnique(monster.rank)
        ? com.badlogic.gdx.math.MathUtils.random(99) : 99;
    duration = BarbarianSkills.resolveWarCryStunDuration(
        monster, mPlayer.has(targetId), mMercenary.has(targetId), duration,
        uniqueRoll);
    if (duration <= 0) {
      log.info("{} phase=stun_reject missile={} source={} target={} skill={}", tag,
          missileId, missile.ownerId, targetId, missile.skillId);
      return;
    }
    if (!mUnitStates.has(targetId)) mUnitStates.create(targetId).init(targetId);
    StateList states = mUnitStates.get(targetId).stateList;
    UnitState stun = states.addState(
        StateId.STUNNED, duration, Math.max(1, missile.damageLevel), missile.ownerId);
    if (stun == null) return;
    // SUNITDMG replaces the expire frame even when a later stun is shorter.
    stun.duration = duration;
    stun.initialDuration = duration;
    stun.sourceEntityId = missile.ownerId;
    stun.skillId = missile.skillId;
    stun.needsSync = true;
    log.info("{} phase=stun_apply missile={} source={} target={} "
            + "skill={} level={} duration={}",
        tag, missileId, missile.ownerId, targetId, missile.skillId,
        Math.max(1, missile.damageLevel), duration);
  }

  /** D2MOO SrvHit17/18/21 state-only war-cry missile dispatch. */
  private void handleWarCryCollision(
      int missileId, Missile missile, int targetId, int hitFunction) {
    if (missile == null || missile.ownerId < 0 || targetId == missile.ownerId) return;
    Skills.Entry skill = missile.skillId >= 0 ? Riiablo.files.skills.get(missile.skillId) : null;
    if (skill == null) return;

    boolean accepted;
    if (hitFunction == 17) {
      accepted = mMonster.has(targetId) && !mMercenary.has(targetId)
          && !mSummonedPet.has(targetId) && isEnemy(missile.ownerId, targetId)
          && canSwitchWarCryAi(targetId);
    } else if (hitFunction == 18) {
      accepted = areAligned(missile.ownerId, targetId);
    } else {
      accepted = isEnemy(missile.ownerId, targetId)
          && (!mNativeUnitFlags.has(targetId)
              || NativeTargeting.isValidCombatTarget(mNativeUnitFlags.get(targetId)));
    }
    if (!accepted || !isAlive(targetId) || !missile.hitTargets.add(targetId)) return;
    if (missile.sharedHitTargets != null && !missile.sharedHitTargets.add(targetId)) return;

    if (!mUnitStates.has(targetId)) mUnitStates.create(targetId).init(targetId);
    UnitStates states = mUnitStates.get(targetId);
    if (states.stateList == null) states.init(targetId);
    int skillLevel = Math.max(1, missile.damageLevel);
    com.riiablo.engine.server.state.UnitState state;
    if (hitFunction == 17) {
      state = BarbarianSkills.applyHowlState(
          states.stateList, skill, skillLevel, entityLevel(missile.ownerId),
          entityLevel(targetId), missile.ownerId, true);
    } else {
      state = BarbarianSkills.applyWarCryState(
          states.stateList, skill, skillLevel, missile.ownerId, true,
          name -> baseSkillLevel(missile.ownerId, name));
    }
    if (state == null) return;
    log.info("[BARBARIAN_WAR_CRY] phase=missile_apply missile={} hitFunc={} source={} "
            + "target={} skill={} level={} state={} duration={} damage={} defense={} attack={}",
        missileId, hitFunction, missile.ownerId, targetId, skill.Id, skillLevel,
        com.riiablo.engine.server.state.StateId.getName(state.stateId), state.duration,
        state.damageModifier, state.defenseModifier, state.attackModifier);
  }

  private boolean canSwitchWarCryAi(int targetId) {
    if (!mMonster.has(targetId)) return false;
    Monster monster = mMonster.get(targetId);
    StateList stateList = mUnitStates.has(targetId) ? mUnitStates.get(targetId).stateList : null;
    return BarbarianSkills.canSwitchWarCryAi(monster, stateList);
  }

  private boolean isAlive(int entityId) {
    if (!mAttributesWrapper.has(entityId)) return true;
    Attributes attrs = mAttributesWrapper.get(entityId).attrs;
    StatRef hp = attrs != null ? attrs.get(Stat.hitpoints, StatRef.obtain()) : null;
    return hp == null || hp.asFixed() > 0f;
  }

  private int entityLevel(int entityId) {
    if (!mAttributesWrapper.has(entityId)) return 1;
    Attributes attrs = mAttributesWrapper.get(entityId).attrs;
    StatRef level = attrs != null ? attrs.get(Stat.level, StatRef.obtain()) : null;
    return level != null ? Math.max(1, level.asInt()) : 1;
  }

  /** Skills.txt .blvl reads hard points and deliberately excludes +skills. */
  private int baseSkillLevel(int entityId, String skillName) {
    Skills.Entry skill = skillName != null ? Riiablo.files.skills.get(skillName) : null;
    if (skill == null || !mPlayer.has(entityId) || mPlayer.get(entityId).data == null) return 0;
    return Math.max(0, mPlayer.get(entityId).data.getBaseSkillLevel(skill.Id));
  }

  boolean areAligned(int sourceId, int targetId) {
    boolean sourceConverted = mMonster.has(sourceId) && mMonster.get(sourceId).converted;
    boolean targetConverted = mMonster.has(targetId) && mMonster.get(targetId).converted;
    if (sourceConverted || targetConverted) {
      // Converted monsters are temporarily player-aligned: they can damage
      // ordinary evil monsters, but neither players nor other converted units.
      if (sourceConverted && targetConverted) return true;
      if (sourceConverted) {
        return mPlayer.has(targetId) || mMercenary.has(targetId) || mSummonedPet.has(targetId);
      }
      return mPlayer.has(sourceId) || mMercenary.has(sourceId) || mSummonedPet.has(sourceId);
    }
    if (mNativeAiTargetOverride.has(sourceId)) {
      NativeAiTargetOverride override = mNativeAiTargetOverride.get(sourceId);
      if (override.remainingFrames >= 0 && override.targetId == targetId) {
        // Confuse and Attract alter the native AI target node without changing
        // the persistent monster alignment. Its missile must therefore treat
        // the selected monster as hostile for this temporary command.
        return false;
      }
    }
    boolean sourceGood = mPlayer.has(sourceId) || mMercenary.has(sourceId)
        || mSummonedPet.has(sourceId);
    boolean targetGood = mPlayer.has(targetId) || mMercenary.has(targetId)
        || mSummonedPet.has(targetId);
    if (sourceGood != targetGood) return false;
    if (!sourceGood) return mMonster.has(sourceId) && mMonster.has(targetId);
    int sourceOwner = alignmentOwner(sourceId);
    int targetOwner = alignmentOwner(targetId);
    if (sourceOwner == targetOwner) return true;
    if (!mPlayer.has(sourceOwner) || !mPlayer.has(targetOwner) || partyManager == null) return false;
    short sourceParty = partyManager.getPartyId(sourceOwner);
    return sourceParty != com.riiablo.engine.server.party.Party.INVALID_ID
        && sourceParty == partyManager.getPartyId(targetOwner);
  }

  private int alignmentOwner(int entityId) {
    if (mMercenary.has(entityId)) return mMercenary.get(entityId).ownerId;
    if (mSummonedPet.has(entityId)) return mSummonedPet.get(entityId).ownerId;
    return entityId;
  }

  /** D2MOO SrvHit04 creates a zero-velocity SrvHit01 explosion sub-missile. */
  private void spawnAmazonExplosion(Missile source, Vector2 origin) {
    if (factory == null || source == null || source.missile == null
        || source.missile.HitSubMissile == null) return;
    Skills.Entry skill = source.skillId >= 0 ? Riiablo.files.skills.get(source.skillId) : null;
    for (String name : source.missile.HitSubMissile) {
      if (name == null || name.isEmpty()) continue;
      Missiles.Entry row = Riiablo.files.Missiles.get(name);
      if (row == null) continue;
      int childId = factory.createMissile(row, new Vector2(1f, 0f), origin, source.ownerId);
      if (childId < 0 || !mMissile.has(childId)) continue;
      Missile child = mMissile.get(childId);
      if (skill != null) {
        Attributes ownerAttrs = mAttributesWrapper.has(source.ownerId)
            ? mAttributesWrapper.get(source.ownerId).attrs : null;
        int level = Math.max(1, source.damageLevel);
        if (!MissileDamageResolver.initializeSkillArea(
            child, skill, ownerAttrs, level,
            synergyName -> baseSkillLevel(source.ownerId, synergyName),
            stateList(source.ownerId))) {
          MissileDamageResolver.initialize(child, ownerAttrs, null, -1, level, 0);
        }
        child.skillId = source.skillId;
        child.damageLevel = level;
      }
      log.info("[AMAZON_ARROW_EXPLOSION] phase=create owner={} skill={} source={} child={} "
              + "missile={} radius={} freeze={}",
          source.ownerId, source.skillId, source.missile.Missile, childId, name,
          nativeAreaRadius(child), child.freezesTarget);
    }
  }

  /**
   * Resolves a native map barrier hit through the row's explosion wiring.
   * D2MOO uses the same SrvDmgHitHandler path for a null target, which may
   * create ExplosionMissile or one of the HitSubMissile rows.  Keep this
   * generic so elemental projectiles do not depend on an Amazon-only handler.
   */
  private void spawnNativeMapExplosion(Missile source, Vector2 origin) {
    if (factory == null || source == null || source.missile == null || origin == null) return;
    String name = source.missile.ExplosionMissile;
    if ((name == null || name.isEmpty()) && source.missile.Explosion != 0
        && source.missile.HitSubMissile != null
        && source.missile.HitSubMissile.length > 0) {
      name = source.missile.HitSubMissile[0];
    }
    if (name == null || name.isEmpty()) return;
    Missiles.Entry row = Riiablo.files.Missiles.get(name);
    if (row == null) {
      log.debug("[MISSILE_MAP_HIT] explosion_missing missile={} explosion={}",
          source.missile.Missile, name);
      return;
    }
    int childId = factory.createMissile(row, Vector2.X, origin, source.ownerId);
    if (childId < 0 || !mMissile.has(childId)) return;
    Missile child = mMissile.get(childId);
    child.skillId = source.skillId;
    child.damageLevel = Math.max(1, source.damageLevel);
    child.damageMultiplier = source.damageMultiplier;
    if (source.damageSnapshot) {
      for (StatRef stat : source.damage.base()) {
        child.damage.base().putEncoded(stat.id(), stat.encodedParams(), stat.encodedValues());
      }
      child.damage.reset();
      child.damageSnapshot = true;
    }
    log.debug("[MISSILE_MAP_HIT] explosion_spawn source={} child={} missile={} pos=({}, {})",
        source.missile.Missile, childId, name, origin.x, origin.y);
  }

  private static boolean isNativeAreaEffect(Missile missile) {
    return missile != null && missile.missile != null
        && (missile.missile.pSrvHitFunc == 1 || missile.missile.pSrvHitFunc == 14)
        && nativeAreaRadius(missile) > 0;
  }

  /** D2MOO SrvHit14 creates the Royal Strike meteor's 18-position fire field. */
  private void spawnRoyalStrikeMeteorFire(Missile source, Vector2 origin) {
    if (factory == null || source == null || source.missile == null
        || source.missile.HitSubMissile == null
        || source.missile.HitSubMissile.length == 0) return;
    String name = source.missile.HitSubMissile[0];
    Missiles.Entry row = name != null ? Riiablo.files.Missiles.get(name) : null;
    if (row == null) return;
    Skills.Entry skill = source.skillId >= 0 ? Riiablo.files.skills.get(source.skillId) : null;
    int level = Math.max(1, source.damageLevel);
    int lifetime = row.Range;
    if (skill != null && skill.Param != null && skill.Param.length > 3) {
      lifetime = skill.Param[2] + (level - 1) * skill.Param[3];
    }
    int step = Math.max(1, arrayValue(source.missile.sHitPar, 1));
    int[] xs = {2, -2, 0, 0, -3, 0, 3, -1, 1, -1, 2, -4, -3, -1, 0, 1, 3, 4};
    int[] ys = {-2, -2, 2, 5, 3, 3, 3, 2, 1, -1, -1, -2, -2, -3, -4, -3, -3, -2};
    Attributes ownerAttrs = mAttributesWrapper.has(source.ownerId)
        ? mAttributesWrapper.get(source.ownerId).attrs : null;
    int created = 0;
    for (int i = 0; i < xs.length; i += step) {
      int id = factory.createMissile(row, Vector2.X,
          new Vector2(origin).add(xs[i], ys[i]), source.ownerId);
      if (id < 0 || !mMissile.has(id)) continue;
      Missile fire = mMissile.get(id);
      MissileDamageResolver.initialize(fire, ownerAttrs, null, -1, level, 0);
      fire.skillId = source.skillId;
      fire.damageLevel = level;
      fire.persistent = true;
      fire.remainingFrames = Math.max(1, lifetime);
      fire.tickInterval = Math.max(1, row.DamageRate > 0 ? row.DamageRate : 1);
      created++;
    }
    log.info("[ASSASSIN_PHOENIX] phase=meteor_fire owner={} skill={} level={} "
            + "missile={} step={} created={} lifetime={}",
        source.ownerId, source.skillId, level, name, step, created, lifetime);
  }

  private static boolean isChainLightningMissile(Missile missile) {
    if (missile == null || missile.missile == null || missile.chainHitsRemaining <= 1) {
      return false;
    }
    String name = missile.missile.Missile;
    return missile.missile.pSrvHitFunc == 12
        || "royalstrikechainlightning".equalsIgnoreCase(name);
  }

  /** D2MOO SrvHit12 selects a new nearby hostile and decrements TargetX. */
  private void spawnChainLightningContinuation(
      Missile source, int struckTarget, Vector2 origin) {
    Skills.Entry skill = source.skillId >= 0 ? Riiablo.files.skills.get(source.skillId) : null;
    int level = Math.max(1, source.damageLevel);
    int range = skill != null
        ? Math.max(1, SkillFormula.evaluate(skill.aurarangecalc, skill, level)) : 8;
    int nextTarget = Engine.INVALID_ENTITY;
    float best = Float.MAX_VALUE;
    Array<Integer> candidates = getEntitiesInRange(origin.x, origin.y, range);
    for (int i = 0; i < candidates.size; i++) {
      int candidate = candidates.get(i);
      if (candidate == struckTarget || candidate == source.ownerId
          || !mPosition.has(candidate) || !isEnemy(source.ownerId, candidate)
          || source.sharedHitTargets != null && source.sharedHitTargets.contains(candidate)) continue;
      Attributes attrs = mAttributesWrapper.has(candidate)
          ? mAttributesWrapper.get(candidate).attrs : null;
      StatRef hp = attrs != null ? attrs.get(Stat.hitpoints) : null;
      if (hp != null && hp.asFixed() <= 0f) continue;
      float distance = mPosition.get(candidate).position.dst2(origin);
      if (distance < best) {
        best = distance;
        nextTarget = candidate;
      }
    }
    if (nextTarget == Engine.INVALID_ENTITY) return;
    Vector2 direction = new Vector2(mPosition.get(nextTarget).position).sub(origin);
    if (direction.isZero(0.0001f)) return;
    int id = factory.createMissile(source.missile, direction.nor(), origin, source.ownerId);
    if (id < 0 || !mMissile.has(id)) return;
    Missile child = mMissile.get(id);
    Attributes ownerAttrs = mAttributesWrapper.has(source.ownerId)
        ? mAttributesWrapper.get(source.ownerId).attrs : null;
    MissileDamageResolver.initialize(child, ownerAttrs, null, -1, level, 0);
    child.skillId = source.skillId;
    child.damageLevel = level;
    child.chainHitsRemaining = source.chainHitsRemaining - 1;
    child.shareHitTargets(source.sharedHitTargets != null
        ? source.sharedHitTargets : new com.badlogic.gdx.utils.IntSet());
    log.info("[ASSASSIN_PHOENIX] phase=chain_continue owner={} fromTarget={} "
            + "toTarget={} remaining={} missile={}",
        source.ownerId, struckTarget, nextTarget, child.chainHitsRemaining, id);
  }

  /** D2MOO SrvHit09 creates a circular grid of stationary Immolation Fire missiles. */
  private void spawnImmolationFire(Missile source, Vector2 origin) {
    if (factory == null || source == null || source.missile == null) return;
    Skills.Entry skill = source.skillId >= 0 ? Riiablo.files.skills.get(source.skillId)
        : Riiablo.files.skills.get("Immolation Arrow");
    int level = Math.max(1, source.damageLevel);
    int radius = skill != null ? Math.max(1, SkillFormula.evaluate(skill.calc1, skill, level)) : 3;
    if (radius <= 0) radius = 3;
    String name = source.missile.HitSubMissile != null
        && source.missile.HitSubMissile.length > 0
        ? source.missile.HitSubMissile[0] : "immolationfire";
    Missiles.Entry row = Riiablo.files.Missiles.get(name);
    if (row == null) return;
    Attributes ownerAttrs = mAttributesWrapper.has(source.ownerId)
        ? mAttributesWrapper.get(source.ownerId).attrs : null;
    int spawned = 0;
    for (int x = -radius; x <= radius; x++) {
      for (int y = -radius; y <= radius; y++) {
        if (x * x + y * y > radius * radius) continue;
        Vector2 position = new Vector2(origin).add(x, y);
        int id = factory.createMissile(row, new Vector2(1f, 0f), position, source.ownerId);
        if (id < 0 || !mMissile.has(id)) continue;
        Missile fire = mMissile.get(id);
        MissileDamageResolver.initialize(fire, ownerAttrs, null, -1, level, 0);
        fire.skillId = source.skillId;
        fire.damageLevel = level;
        fire.persistent = true;
        fire.remainingFrames = Math.max(1, row.Range);
        fire.tickInterval = Math.max(1, row.DamageRate > 0 ? row.DamageRate : 1);
        fire.pierceEnabled = true;
        // HitShift=2 stores sub-1-point fixed damage in D2; retain at least
        // one integer point in this engine's integer combat representation.
        if (fire.damage.get(Stat.firemaxdam) == null
            || fire.damage.get(Stat.firemaxdam).asInt() <= 0) {
          fire.damage.base().put(Stat.firemindam, Math.max(1, row.EMin));
          fire.damage.base().put(Stat.firemaxdam, Math.max(row.EMin, row.Emax));
          fire.damage.reset();
          fire.damageSnapshot = true;
        }
        spawned++;
      }
    }
    log.info("[AMAZON_IMMOLATION_FIRE] phase=spawn owner={} skill={} level={} radius={} "
            + "missile={} count={} duration={} tick={}", source.ownerId, source.skillId,
        level, radius, name, spawned, row.Range, row.DamageRate);
  }

  /** D2MOO SrvDo02/SrvHit02 poison-javelin cloud creation. */
  private void spawnPoisonCloud(Missile source, Vector2 origin) {
    if (factory == null || source == null || source.missile == null) return;
    String name = source.missile.SubMissile != null
        && source.missile.SubMissile.length > 0 ? source.missile.SubMissile[0] : null;
    if ((name == null || name.isEmpty()) && source.missile.HitSubMissile != null
        && source.missile.HitSubMissile.length > 0) {
      name = source.missile.HitSubMissile[0];
    }
    if ((name == null || name.isEmpty()) && source.missile.CltSubMissile != null
        && source.missile.CltSubMissile.length > 0) {
      // Trap poison balls carry the same native cloud row in CltSubMissile;
      // the authoritative server still creates it so every client agrees.
      name = source.missile.CltSubMissile[0];
    }
    if (name == null || name.isEmpty()) return;
    Missiles.Entry row = Riiablo.files.Missiles.get(name);
    if (row == null) return;
    int id = factory.createMissile(row, new Vector2(1f, 0f), origin, source.ownerId);
    if (id < 0 || !mMissile.has(id)) return;
    Missile cloud = mMissile.get(id);
    cloud.skillId = source.skillId;
    cloud.damageLevel = Math.max(1, source.damageLevel);
    Skills.Entry skill = source.skillId >= 0 ? Riiablo.files.skills.get(source.skillId) : null;
    configurePersistentPoisonCloud(cloud, skill);
    log.info("[AMAZON_POISON_CLOUD] phase=create owner={} skill={} source={} child={} "
            + "missile={} duration={} tick={} poison={}..{} length={}",
        source.ownerId, source.skillId, source.missile.Missile, id, name,
        cloud.remainingFrames, cloud.tickInterval,
        statInt(cloud.damage, Stat.poisonmindam), statInt(cloud.damage, Stat.poisonmaxdam),
        statInt(cloud.damage, Stat.poisonlength));
  }

  private void configurePersistentPoisonCloud(Missile cloud, Skills.Entry sourceSkill) {
    if (cloud == null || cloud.missile == null) return;
    Attributes ownerAttrs = mAttributesWrapper.has(cloud.ownerId)
        ? mAttributesWrapper.get(cloud.ownerId).attrs : null;
    Skills.Entry damageSkill = sourceSkill;
    if ((damageSkill == null || !"pois".equalsIgnoreCase(damageSkill.EType))
        && cloud.missile.Skill != null && !cloud.missile.Skill.isEmpty()) {
      Skills.Entry missileSkill = Riiablo.files.skills.get(cloud.missile.Skill);
      if (missileSkill != null) damageSkill = missileSkill;
    }
    if (!cloud.damageSnapshot && damageSkill != null) {
      MissileDamageResolver.initializeSkillArea(
          cloud, damageSkill, ownerAttrs, Math.max(1, cloud.damageLevel));
    }
    if (!cloud.damageSnapshot) {
      int min = Math.max(1, cloud.missile.EMin);
      int max = Math.max(min, cloud.missile.Emax);
      int length = cloud.missile.ELen;
      if (damageSkill != null) {
        length = Math.max(length, damageSkill.ELen
            + skillDamageLengthBonus(damageSkill, cloud.damageLevel));
      }
      if (ownerAttrs != null) {
        cloud.damage.base().put(Stat.level, Math.max(1, statInt(ownerAttrs, Stat.level)));
        cloud.damage.base().put(Stat.tohit, Math.max(0, statInt(ownerAttrs, Stat.tohit)));
        cloud.damage.base().put(Stat.strength, Math.max(0, statInt(ownerAttrs, Stat.strength)));
        cloud.damage.base().put(Stat.dexterity, Math.max(0, statInt(ownerAttrs, Stat.dexterity)));
      }
      cloud.damage.base().put(Stat.poisonmindam, min);
      cloud.damage.base().put(Stat.poisonmaxdam, max);
      cloud.damage.base().put(Stat.poisonlength, Math.max(1, length));
      cloud.damage.reset();
      cloud.damageSnapshot = true;
    }
    cloud.persistent = true;
    cloud.remainingFrames = Math.max(1, cloud.missile.Range);
    cloud.tickInterval = Math.max(1,
        cloud.missile.DamageRate > 0 ? cloud.missile.DamageRate : 10);
    cloud.pierceEnabled = true;
  }

  private static boolean isStationaryPoisonCloud(Missile missile, Velocity velocity) {
    if (missile == null || missile.missile == null || velocity == null
        || !velocity.velocity.isZero(0.0001f) || missile.missile.pSrvDoFunc != 3) return false;
    String name = missile.missile.Missile;
    String type = missile.missile.EType;
    String skill = missile.missile.Skill;
    return "pois".equalsIgnoreCase(type)
        || name != null && name.toLowerCase(java.util.Locale.ROOT).contains("poisoncloud")
        || skill != null && skill.toLowerCase(java.util.Locale.ROOT).contains("poison");
  }

  private static int skillDamageLengthBonus(Skills.Entry skill, int level) {
    if (skill == null || skill.ELevLen == null || level <= 1) return 0;
    int first = arrayValue(skill.ELevLen, 0);
    int second = arrayValue(skill.ELevLen, 1);
    int third = arrayValue(skill.ELevLen, 2);
    if (level > 16) return 7 * first + 8 * second + (level - 16) * third;
    if (level > 8) return 7 * first + (level - 8) * second;
    return (level - 1) * first;
  }

  private static int nativeAreaRadius(Missile missile) {
    if (missile == null || missile.missile == null) return 0;
    if (missile.persistent) return 1;
    return Math.max(0, arrayValue(missile.missile.sHitPar, 0));
  }

  /** D2MOO MISSMODE_SrvHit20_LightningFury. */
  private void spawnLightningFuryBolts(Missile source, Vector2 origin, int struckTarget) {
    if (factory == null || source == null || source.missile == null
        || source.missile.HitSubMissile == null
        || source.missile.HitSubMissile.length == 0) return;
    String subMissileName = source.missile.HitSubMissile[0];
    if (subMissileName == null || subMissileName.isEmpty()) return;
    Missiles.Entry subMissile = Riiablo.files.Missiles.get(subMissileName);
    if (subMissile == null) {
      log.warn("[AMAZON_LIGHTNING_FURY] phase=reject owner={} reason=missing_submissile name={}",
          source.ownerId, subMissileName);
      return;
    }
    Skills.Entry skill = source.missile.Skill == null || source.missile.Skill.isEmpty()
        ? Riiablo.files.skills.get("Lightning Fury")
        : Riiablo.files.skills.get(source.missile.Skill);
    int level = Math.max(1, source.damageLevel);
    int range = lightningFuryRange(source.missile, skill, level);
    int maximum = lightningFuryBoltCount(source.missile, skill, level);

    Array<Integer> targets = getEntitiesInRange(origin.x, origin.y, range);
    targets.sort((left, right) -> Float.compare(
        mPosition.get(left).position.dst2(origin), mPosition.get(right).position.dst2(origin)));
    int created = 0;
    for (int i = 0; i < targets.size && created < maximum; i++) {
      int targetId = targets.get(i);
      if (targetId == struckTarget || targetId == source.ownerId
          || !mPosition.has(targetId) || !isEnemy(source.ownerId, targetId)) continue;
      if (mNativeUnitFlags.has(targetId)
          && !NativeTargeting.isValidCombatTarget(mNativeUnitFlags.get(targetId))) continue;
      Vector2 direction = new Vector2(mPosition.get(targetId).position).sub(origin);
      if (direction.isZero(0.0001f)) continue;
      int boltId = factory.createMissile(subMissile, direction.nor(), origin, source.ownerId);
      if (boltId < 0) continue;
      if (mMissile.has(boltId)) {
        Missile bolt = mMissile.get(boltId);
        Attributes ownerAttrs = mAttributesWrapper.has(source.ownerId)
            ? mAttributesWrapper.get(source.ownerId).attrs : null;
        if (skill != null) {
          MissileDamageResolver.initializeSkillArea(bolt, skill, ownerAttrs, level);
        } else {
          Monster ownerMonster = mMonster.has(source.ownerId) ? mMonster.get(source.ownerId) : null;
          MissileDamageResolver.initialize(bolt, ownerAttrs, ownerMonster, -1, level, 0);
        }
      }
      created++;
    }
    log.info("[AMAZON_LIGHTNING_FURY] phase=split owner={} struckTarget={} level={} "
            + "range={} maximum={} created={} missile={}",
        source.ownerId, struckTarget, level, range, maximum, created, subMissileName);
  }

  static int lightningFuryRange(Missiles.Entry missile, Skills.Entry skill, int level) {
    int configured = arrayValue(missile != null ? missile.sHitPar : null, 0);
    int calculated = configured > 0 ? configured
        : SkillFormula.evaluate(skill != null ? skill.aurarangecalc : null, skill, level);
    return Math.max(1, Math.min(64, calculated));
  }

  static int lightningFuryBoltCount(Missiles.Entry missile, Skills.Entry skill, int level) {
    int configured = arrayValue(missile != null ? missile.sHitPar : null, 1);
    int calculated = configured > 0 ? configured
        : SkillFormula.evaluate(skill != null ? skill.calc1 : null, skill, level);
    return Math.max(1, Math.min(64, calculated));
  }

  private static int arrayValue(int[] values, int index) {
    return values != null && index >= 0 && index < values.length ? values[index] : 0;
  }

  public int mercenaryCollisionCount() {
    return mercenaryCollisionCount;
  }

  public int mercenaryDamageCount() {
    return mercenaryDamageCount;
  }

  public int mercenaryLastDamageTarget() {
    return mercenaryLastDamageTarget;
  }

  public float mercenaryLastDamageBefore() {
    return mercenaryLastDamageBefore;
  }

  public float mercenaryLastDamageAfter() {
    return mercenaryLastDamageAfter;
  }

  /** Returns authoritative runtime modifiers for the owner or target. */
  private com.riiablo.engine.server.state.StateList stateList(int entityId) {
    if (!mUnitStates.has(entityId)) return null;
    UnitStates states = mUnitStates.get(entityId);
    return states != null ? states.stateList : null;
  }

  private boolean isEntityMoving(int entityId) {
    return mVelocity.has(entityId) && !mVelocity.get(entityId).velocity.isZero(0.0001f);
  }

  private static float distanceToSegment(Vector2 point, Vector2 start, Vector2 end) {
    float dx = end.x - start.x;
    float dy = end.y - start.y;
    float lengthSquared = dx * dx + dy * dy;
    if (lengthSquared == 0f) return point.dst(start);
    float t = ((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared;
    t = Math.max(0f, Math.min(1f, t));
    float closestX = start.x + t * dx;
    float closestY = start.y + t * dy;
    return point.dst(closestX, closestY);
  }

  private void applyCombatStates(Missile missile, int targetId,
      CombatSystem.CombatResult combat) {
    if (!mUnitStates.has(targetId)) return;
    int attackerId = missile != null ? missile.ownerId : Engine.INVALID_ENTITY;
    if (combat.poisonDuration > 0
        && combat.elementalDamage[CombatSystem.DAMAGE_POISON] > 0) {
      StatusEffectApplier.INSTANCE.applyPoison(targetId,
          combat.elementalDamage[CombatSystem.DAMAGE_POISON],
          combat.poisonDuration, attackerId);
    }
    if (combat.coldDuration > 0
        && combat.elementalDamage[CombatSystem.DAMAGE_COLD] > 0) {
      if (missile != null && missile.freezesTarget) {
        StatusEffectApplier.INSTANCE.applyFreeze(targetId, combat.coldDuration, attackerId);
      } else {
        StatusEffectApplier.INSTANCE.applyCold(targetId, combat.coldDuration, attackerId);
      }
    }
  }

  private static int statInt(Attributes attrs, short stat) {
    if (attrs == null) return 0;
    StatRef ref = attrs.get(stat);
    return ref != null ? ref.asInt() : 0;
  }

  private static float statFixed(Attributes attrs, short stat) {
    if (attrs == null) return 0;
    StatRef ref = attrs.get(stat);
    return ref != null ? ref.asFixed() : 0f;
  }
  
  /**
   * 判断两个实体是否是敌人
   */
  private boolean isEnemy(int entityId1, int entityId2) {
    // Hirelings retain Monster presentation/components, but D2MOO assigns
    // them their owner's good alignment. Treat them as player-aligned for
    // combat relations so their missiles can hit hostile monsters without
    // becoming hostile to players.
    boolean sourcePlayer = mPlayer.has(entityId1) || mMercenary.has(entityId1)
        || mSummonedPet.has(entityId1);
    boolean targetPlayer = mPlayer.has(entityId2) || mMercenary.has(entityId2)
        || mSummonedPet.has(entityId2);
    boolean sourceMonster = mMonster.has(entityId1);
    boolean targetMonster = mMonster.has(entityId2);
    if (sourceMonster && mMonster.get(entityId1).converted) {
      return targetMonster && !mMonster.get(entityId2).converted;
    }
    if (targetMonster && mMonster.get(entityId2).converted) {
      return !sourcePlayer && sourceMonster && !mMonster.get(entityId1).converted;
    }
    if (!sourcePlayer && !sourceMonster) return false;
    if (!targetPlayer && !targetMonster) return false;
    int relationSource = sourcePlayer ? alignmentOwner(entityId1) : entityId1;
    int relationTarget = targetPlayer ? alignmentOwner(entityId2) : entityId2;
    boolean enemy = PvpCombatRules.canDamage(partyManager, relationSource, relationTarget,
        sourcePlayer, targetPlayer);
    if (sourcePlayer && targetPlayer && !enemy) {
      log.info("[PVP] phase=missile_reject source={} target={} reason=not_hostile",
          entityId1, entityId2);
    }
    return enemy;
  }
  
  /**
   * 获取范围内的实体
   * 使用 ECS 的 AspectSubscriptionManager 查询所有有 Monster 和 Position 的实体
   */
  private Array<Integer> getEntitiesInRange(float x, float y, float radius) {
    Array<Integer> result = new Array<>();
    
    // 检查玩家
    com.artemis.AspectSubscriptionManager subscriptionManager = world.getAspectSubscriptionManager();
    com.artemis.EntitySubscription playerSubscription = subscriptionManager.get(Aspect.all(Player.class, Position.class));
    IntBag playerEntities = playerSubscription.getEntities();
    
    for (int i = 0; i < playerEntities.size(); i++) {
      int playerId = playerEntities.get(i);
      if (mPosition.has(playerId)) {
        Position playerPos = mPosition.get(playerId);
        if (playerPos.position.dst(x, y) <= radius) {
          result.add(playerId);
        }
      }
    }
    
    // 使用 AspectSubscriptionManager 查询所有有 Monster 和 Position 的实体
    com.artemis.EntitySubscription subscription = subscriptionManager.get(Aspect.all(Monster.class, Position.class));
    IntBag entities = subscription.getEntities();
    
    for (int i = 0; i < entities.size(); i++) {
      int entityId = entities.get(i);
      if (mPosition.has(entityId)) {
        Position pos = mPosition.get(entityId);
        if (pos.position.dst(x, y) <= radius) {
          result.add(entityId);
        }
      }
    }
    
    return result;
  }
}
