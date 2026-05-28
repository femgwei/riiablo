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
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.combat.DamageCalculator;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import net.mostlyoriginal.api.event.common.EventSystem;

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
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  
  protected EventSystem events;
  
  private final Vector2 tmpVec = new Vector2();
  private final Vector2 lastPos = new Vector2();
  
  @Override
  protected void process(int entityId) {
    Missile missile = mMissile.get(entityId);
    Position position = mPosition.get(entityId);
    Velocity velocity = mVelocity.get(entityId);
    
    // 保存上一帧位置
    lastPos.set(position.position);
    
    // 更新导弹位置（VelocityAdder 系统被注释掉了，所以在这里更新）
    float moveDistance = velocity.velocity.len() * world.delta;
    position.position.add(velocity.velocity.x * world.delta, velocity.velocity.y * world.delta);
    
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
      if (checkCollisionWithEntity(missileId, missile, currentPos, playerId, playerPos)) {
        return; // 已命中，导弹将被销毁
      }
    }
    
    // 检查与所有怪物的碰撞
    // 注意：这里简化处理，实际应该使用空间分区或更高效的查询
    // 为了性能，可以限制检查范围（例如只检查附近的怪物）
    float checkRadius = 2.0f; // 碰撞检测半径
    Array<Integer> nearbyEntities = getEntitiesInRange(currentPos.x, currentPos.y, checkRadius);
    
    for (int i = 0; i < nearbyEntities.size; i++) {
      int targetId = nearbyEntities.get(i);
      if (targetId == missileId || targetId == missile.ownerId) {
        continue; // 跳过自己和自己
      }
      
      if (mMonster.has(targetId) && mPosition.has(targetId)) {
        Position targetPos = mPosition.get(targetId);
        if (checkCollisionWithEntity(missileId, missile, currentPos, targetId, targetPos)) {
          return; // 已命中，导弹将被销毁
        }
      }
    }
  }
  
  /**
   * 检查与特定实体的碰撞
   * @return true 如果发生碰撞并处理了伤害
   */
  private boolean checkCollisionWithEntity(int missileId, Missile missile, Vector2 missilePos, 
      int targetId, Position targetPos) {
    // 简单的距离碰撞检测
    float distance = missilePos.dst(targetPos.position);
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

      // D2MOD: 远程命中判定（level、AR、armorclass_vs_missile），未命中则不造成伤害
      boolean hit = true;
      if (mAttributesWrapper.has(missile.ownerId) && mAttributesWrapper.has(targetId)) {
        Attributes ownerAttrs = mAttributesWrapper.get(missile.ownerId).attrs;
        Attributes targetAttrs = mAttributesWrapper.get(targetId).attrs;
        int attLvl = statInt(ownerAttrs, Stat.level, 1);
        int defLvl = statInt(targetAttrs, Stat.level, 1);
        
        // 计算攻击等级：玩家使用 STAT_TOHIT + 5 * (DEX - 7)，怪物直接使用 STAT_TOHIT
        // 参考 Actioneer.java 中的逻辑，投掷武器也应该使用相同的 AR 计算公式
        int baseToHit = statInt(ownerAttrs, Stat.tohit, 0);
        int ar;
        if (mMonster.has(missile.ownerId)) {
          // 怪物：直接使用 baseToHit（DEX 通常为 0）
          ar = baseToHit;
        } else {
          // 玩家：STAT_TOHIT + 5 * (DEX - 7)
          int dexterity = statInt(ownerAttrs, Stat.dexterity, 0);
          ar = baseToHit + 5 * Math.max(0, dexterity - 7);
        }
        
        int def = statInt(targetAttrs, Stat.armorclass_vs_missile, 0);
        if (def == 0) def = statInt(targetAttrs, Stat.armorclass, 0);
        
        // 检查是否是投掷武器，记录 AR 值
        boolean isThrowableWeapon = false;
        if (mPlayer.has(missile.ownerId)) {
          try {
            if (com.riiablo.Riiablo.charData != null && com.riiablo.Riiablo.charData.getItems() != null) {
              com.riiablo.item.Item weapon = com.riiablo.Riiablo.charData.getItems().getEquipped(com.riiablo.item.BodyLoc.RARM);
              if (weapon == null) {
                weapon = com.riiablo.Riiablo.charData.getItems().getEquipped(com.riiablo.item.BodyLoc.LARM);
              }
              if (weapon != null && weapon.base != null) {
                isThrowableWeapon = weapon.type.is(com.riiablo.item.Type.JAVE) || 
                                   weapon.type.is(com.riiablo.item.Type.TKNI) || 
                                   weapon.type.is(com.riiablo.item.Type.TAXE);
              }
            }
          } catch (Exception e) {
            // ignore
          }
        }
        
        hit = DamageCalculator.INSTANCE.isHitSuccessful(ar, def, attLvl, defLvl);
        if (isThrowableWeapon) {
          int dexterity = mPlayer.has(missile.ownerId) ? statInt(ownerAttrs, Stat.dexterity, 0) : 0;
          log.info("[THROW_HIT_CHECK] Missile {} hit check: AR={} (baseToHit={}, dex={}), Def={}, attLvl={}, defLvl={}, hit={}, ownerId={}, targetId={}", 
              missileId, ar, baseToHit, dexterity, def, attLvl, defLvl, hit, missile.ownerId, targetId);
        }
        if (!hit) {
          log.debug("Missile {} ranged miss on {} (owner={})", missileId, targetId, missile.ownerId);
        }
      }

      if (hit) {
        float damage = calculateMissileDamage(missile.ownerId, missile);
        if (damage > 0 && mAttributesWrapper.has(targetId)) {
          log.info("[THROW_HIT] Missile {} hits {} for {} damage (ownerId={})", missileId, targetId, damage, missile.ownerId);
          Attributes targetAttrs = mAttributesWrapper.get(targetId).attrs;
          // 使用 get(stat, dst) 接口避免重用问题
          StatRef hitpoints = targetAttrs.get(Stat.hitpoints, StatRef.obtain());
          if (hitpoints == null) {
            log.warn("{} has no hitpoints stat", targetId);
            return true; // 返回 true 表示已处理（虽然无法造成伤害）
          }
          DamageEvent event = DamageEvent.obtain(missile.ownerId, targetId, damage);
          events.dispatch(event);
          hitpoints.sub(event.damage);
          float hpAfter = hitpoints.asFixed();
          if (hpAfter < 0) {
            hitpoints.set(0);
            hpAfter = 0;
          }
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
  
  /**
   * 判断两个实体是否是敌人
   */
  private boolean isEnemy(int entityId1, int entityId2) {
    // 玩家 vs 怪物 = 敌人
    if (mPlayer.has(entityId1) && mMonster.has(entityId2)) {
      return true;
    }
    if (mMonster.has(entityId1) && mPlayer.has(entityId2)) {
      return true;
    }
    
    // 怪物 vs 怪物 = 不是敌人（同一阵营）
    if (mMonster.has(entityId1) && mMonster.has(entityId2)) {
      return false;
    }
    
    // 玩家 vs 玩家 = 不是敌人（同一阵营，除非是 PvP）
    if (mPlayer.has(entityId1) && mPlayer.has(entityId2)) {
      return false; // TODO: 支持 PvP
    }
    
    return false;
  }
  
  private static int statInt(Attributes attrs, short stat, int defaultValue) {
    if (attrs == null) return defaultValue;
    StatRef r = attrs.get(stat);
    return r != null ? r.asInt() : defaultValue;
  }

  /**
   * 计算远程/导弹伤害（参考 D2MOD）
   * 怪物：A1MinD/A1MaxD + damagepercent；玩家投掷：item_throw_mindamage/maxdamage；否则 mindamage/maxdamage
   * 使用 DamageCalculator.calculateSimpleDamage 统一计算
   */
  private float calculateMissileDamage(int ownerId, Missile missile) {
    if (ownerId < 0 || !mAttributesWrapper.has(ownerId)) {
      return 10f;
    }
    
    Attributes ownerAttrs = mAttributesWrapper.get(ownerId).attrs;
    
    boolean isThrowableWeapon = false;
    if (mPlayer.has(ownerId)) {
      try {
        if (com.riiablo.Riiablo.charData != null && com.riiablo.Riiablo.charData.getItems() != null) {
          com.riiablo.item.Item weapon = com.riiablo.Riiablo.charData.getItems().getEquipped(com.riiablo.item.BodyLoc.RARM);
          if (weapon == null) {
            weapon = com.riiablo.Riiablo.charData.getItems().getEquipped(com.riiablo.item.BodyLoc.LARM);
          }
          if (weapon != null && weapon.base != null) {
            isThrowableWeapon = weapon.type.is(com.riiablo.item.Type.JAVE) || 
                               weapon.type.is(com.riiablo.item.Type.TKNI) || 
                               weapon.type.is(com.riiablo.item.Type.TAXE);
          }
        }
      } catch (Exception e) {
        // ignore
      }
    }
    
    int minDamage;
    int maxDamage;
    if (isThrowableWeapon) {
      minDamage = statInt(ownerAttrs, com.riiablo.attributes.Stat.item_throw_mindamage, 0);
      maxDamage = statInt(ownerAttrs, com.riiablo.attributes.Stat.item_throw_maxdamage, 0);
      log.info("[THROW_DAMAGE] Reading throw damage from player stats: ownerId={}, minDamage={}, maxDamage={}, isThrowable={}", 
          ownerId, minDamage, maxDamage, isThrowableWeapon);
      
      // Debug: check if stats exist in different stat lists
      if (minDamage <= 0 || maxDamage <= 0) {
        com.riiablo.attributes.StatRef minRef = ownerAttrs.get(com.riiablo.attributes.Stat.item_throw_mindamage);
        com.riiablo.attributes.StatRef maxRef = ownerAttrs.get(com.riiablo.attributes.Stat.item_throw_maxdamage);
        log.warn("[THROW_DAMAGE] Throw damage is 0 or missing: ownerId={}, minDamage={}, maxDamage={}, minRef={}, maxRef={}, " +
            "minRefValue={}, maxRefValue={}", 
            ownerId, minDamage, maxDamage, minRef, maxRef,
            minRef != null ? minRef.asInt() : "null", maxRef != null ? maxRef.asInt() : "null");
      }
    } else {
      minDamage = statInt(ownerAttrs, Stat.mindamage, 0);
      maxDamage = statInt(ownerAttrs, Stat.maxdamage, 0);
    }
    
    if (minDamage <= 0 || maxDamage <= 0) {
      log.warn("[THROW_DAMAGE] Invalid damage values: ownerId={}, isThrowable={}, minDamage={}, maxDamage={}, returning default 10f", 
          ownerId, isThrowableWeapon, minDamage, maxDamage);
      return 10f;
    }
    
    int damageBonus = statInt(ownerAttrs, Stat.damagepercent, 0);
    int dmg = DamageCalculator.INSTANCE.calculateSimpleDamage(minDamage, maxDamage, damageBonus);
    return (float) Math.max(1, dmg);
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
