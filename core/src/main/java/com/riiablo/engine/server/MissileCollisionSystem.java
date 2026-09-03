package com.riiablo.engine.server;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.artemis.utils.IntBag;
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
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.combat.StatusEffectApplier;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.engine.server.party.PvpCombatRules;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import net.mostlyoriginal.api.event.common.EventSystem;
import com.riiablo.map.Map;

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
  
  protected ComponentMapper<Missile> mMissile;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<Class> mClass;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Mercenary> mMercenary;
  protected ComponentMapper<SummonedPet> mSummonedPet;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<NativeUnitFlags> mNativeUnitFlags;
  protected ComponentMapper<MapWrapper> mMapWrapper;

  @com.artemis.annotations.Wire(name = "partyManager", failOnNull = false)
  protected PartyManager partyManager;
  @com.artemis.annotations.Wire(name = "factory", failOnNull = false)
  protected EntityFactory factory;
  
  protected EventSystem events;
  
  private final Vector2 tmpVec = new Vector2();
  private final Vector2 lastPos = new Vector2();
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
    
    // 更新导弹位置（VelocityAdder 系统被注释掉了，所以在这里更新）
    float moveDistance;
    if (missile.attached) {
      moveDistance = lastPos.dst(position.position);
    } else {
      moveDistance = velocity.velocity.len() * world.delta;
      position.position.add(velocity.velocity.x * world.delta, velocity.velocity.y * world.delta);
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
      log.debug("Missile {} reached max range ({}), disposing. ownerId={}, pos=({}, {})", 
          entityId, missile.range, missile.ownerId, position.position.x, position.position.y);
      world.delete(entityId);
      return;
    }
    
    // 碰撞检测：检查是否与玩家或怪物碰撞
    checkCollisions(entityId, missile, position, lastPos);
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
    if (areaEffect && !missile.persistent) world.delete(missileId);
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
      if (missile.attached) {
        int nextFrame = missile.nextHitFrame.get(targetId, Integer.MIN_VALUE);
        if (missile.nativeFrame < nextFrame) return false;
        // With NextHit set, D2MOO installs JUSTHIT for NextDelay frames. If
        // it is clear, the collision may be evaluated again on the next game
        // frame (the blade still needs to be able to hit on its return pass).
        int delay = missile.missile != null && missile.missile.NextHit
            ? Math.max(1, missile.missile.NextDelay) : 1;
        missile.nextHitFrame.put(targetId, missile.nativeFrame + delay);
      } else if (!missile.hitTargets.add(targetId)) {
        // A piercing projectile must never repeatedly damage the same unit on
        // consecutive frames while its swept segment overlaps the target.
        return false;
      }
      if (mMercenary.has(missile.ownerId)) mercenaryCollisionCount++;

      log.info("[MISSILE_HIT] phase=collision missileId={} missile={} owner={} target={} "
              + "distance={} radius={} position=({}, {}) traveled={} range={}",
          missileId, missile.missile != null ? missile.missile.Missile : "unknown",
          missile.ownerId, targetId, distance, collisionRadius,
          missilePos.x, missilePos.y, missile.distanceTraveled, missile.range);

      // D2 radial/fan skills create many missiles for one activation. Resolve
      // a target only once for that cast so overlapping launch paths cannot
      // multiply the same hit dozens of times.
      if (missile.sharedHitTargets != null && !missile.sharedHitTargets.add(targetId)) {
        return false;
      }

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
      CombatSystem.CombatResult combat = CombatSystem.INSTANCE.calculateAttack(
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
          stateList(missile.ownerId), stateList(targetId), isEntityMoving(targetId));
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
          DamageEvent event = DamageEvent.obtain(missile.ownerId, targetId, damage, hitSound);
          events.dispatch(event);
          float appliedDamage = Math.max(0f, event.damage);
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
      
      // Native Pierce keeps the missile alive after a successful collision.
      // A miss/block still consumes the projectile, while a dead target is
      // handled by the normal death path above.
      if (missile.attached) return true;
      if (damageHit && missile.pierceEnabled && missile.pierceChance > 0
          && com.badlogic.gdx.math.MathUtils.random(99) < missile.pierceChance) {
        log.info("[MISSILE_PIERCE] phase=continue missileId={} target={} chance={} hitCount={}",
            missileId, targetId, missile.pierceChance, missile.hitTargets.size);
        return true;
      }
      if (!missile.persistent) world.delete(missileId);
      return true;
    }
    
    return false;
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
        MissileDamageResolver.initializeSkillArea(
            child, skill, ownerAttrs, Math.max(1, source.damageLevel));
      }
      log.info("[AMAZON_ARROW_EXPLOSION] phase=create owner={} skill={} source={} child={} "
              + "missile={} radius={} freeze={}",
          source.ownerId, source.skillId, source.missile.Missile, childId, name,
          nativeAreaRadius(child), child.freezesTarget);
    }
  }

  private static boolean isNativeAreaEffect(Missile missile) {
    return missile != null && missile.missile != null
        && missile.missile.pSrvHitFunc == 1 && nativeAreaRadius(missile) > 0;
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
    if (!sourcePlayer && !sourceMonster) return false;
    if (!targetPlayer && !targetMonster) return false;
    boolean enemy = PvpCombatRules.canDamage(partyManager, entityId1, entityId2,
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
