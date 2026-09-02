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
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.NativeTargeting;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.combat.StatusEffectApplier;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.engine.server.party.PvpCombatRules;
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
public class MissileCollisionSystem extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(MissileCollisionSystem.class);
  
  protected ComponentMapper<Missile> mMissile;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<Class> mClass;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Mercenary> mMercenary;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<NativeUnitFlags> mNativeUnitFlags;
  protected ComponentMapper<MapWrapper> mMapWrapper;

  @com.artemis.annotations.Wire(name = "partyManager", failOnNull = false)
  protected PartyManager partyManager;
  
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
    
    // 保存上一帧位置
    lastPos.set(position.position);
    
    // 更新导弹位置（VelocityAdder 系统被注释掉了，所以在这里更新）
    float moveDistance = velocity.velocity.len() * world.delta;
    position.position.add(velocity.velocity.x * world.delta, velocity.velocity.y * world.delta);

    if (!updateNativeRoom(entityId, missile, position)) return;
    
    // 更新已移动距离（与 d2mod 一致，使用 distanceTraveled）
    missile.distanceTraveled += moveDistance;
    
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
        return; // 已命中，导弹将被销毁
      }
    }
    
    // 检查与所有怪物的碰撞
    // 注意：这里简化处理，实际应该使用空间分区或更高效的查询
    // 为了性能，可以限制检查范围（例如只检查附近的怪物）
    // Include the whole swept segment in the broad-phase query. Without this,
    // a fast missile can pass a target between ticks while the target is no
    // longer within the radius of the missile's end point.
    float checkRadius = 2.0f + currentPos.dst(lastPos);
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
          return; // 已命中，导弹将被销毁
        }
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
    float collisionRadius = 2.0f; // 碰撞半径（增大以更容易命中）
    
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

      if (!mAttributesWrapper.has(missile.ownerId) || !mAttributesWrapper.has(targetId)) {
        log.warn("Missile {} collided with entity {} without complete combat attributes", missileId, targetId);
        world.delete(missileId);
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
        world.delete(missileId);
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
          && !missile.missile.ToHit;
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
          stateList(missile.ownerId), stateList(targetId));
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
        float damage = combat.totalDamage;
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
          applyCombatStates(missile.ownerId, targetId, combat);
          if (hpAfter <= 0) {
            log.debug("{} killed by missile from {}", targetId, missile.ownerId);
            events.dispatch(DeathEvent.obtain(missile.ownerId, targetId));
          }
        }
      }
      
      world.delete(missileId);
      return true;
    }
    
    return false;
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

  private void applyCombatStates(int attackerId, int targetId,
      CombatSystem.CombatResult combat) {
    if (!mUnitStates.has(targetId)) return;
    if (combat.poisonDuration > 0
        && combat.elementalDamage[CombatSystem.DAMAGE_POISON] > 0) {
      StatusEffectApplier.INSTANCE.applyPoison(targetId,
          combat.elementalDamage[CombatSystem.DAMAGE_POISON],
          combat.poisonDuration, attackerId);
    }
    if (combat.coldDuration > 0
        && combat.elementalDamage[CombatSystem.DAMAGE_COLD] > 0) {
      StatusEffectApplier.INSTANCE.applyCold(targetId, combat.coldDuration, attackerId);
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
    boolean sourcePlayer = mPlayer.has(entityId1) || mMercenary.has(entityId1);
    boolean targetPlayer = mPlayer.has(entityId2) || mMercenary.has(entityId2);
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
