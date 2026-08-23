package com.riiablo.engine.server.combat;

import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.engine.server.state.StateId;

/**
 * 状态效果应用器 - 基于 D2MOD 移植
 * 
 * <p>处理伤害造成的状态效果：
 * <ul>
 *   <li>中毒（持续伤害）</li>
 *   <li>燃烧（持续火焰伤害）</li>
 *   <li>冰冷减速</li>
 *   <li>冰冻（完全停止）</li>
 *   <li>眩晕</li>
 *   <li>击退</li>
 * </ul>
 * 
 * <p>参考：D2MOD/source/D2Game/src/UNIT/SUnitDmg.cpp
 * 
 * @author riiablo team
 */
public class StatusEffectApplier {
  private static final Logger log = LogManager.getLogger(StatusEffectApplier.class);

  /** 单例实例 */
  public static final StatusEffectApplier INSTANCE = new StatusEffectApplier();

  private StatusEffectApplier() {}

  /** Runtime sink supplied by the ECS StateUpdater. */
  public interface StateSink {
    void applyState(int targetEntityId, int stateId, int duration, int level,
        int sourceEntityId, int damagePerFrame, int damageType);
  }

  private StateSink stateSink;

  public void setStateSink(StateSink stateSink) {
    this.stateSink = stateSink;
  }

  //==========================================================================
  // 状态效果常量
  //==========================================================================

  /** 冰冷效果：减速系数（256 = 100%速度，0 = 完全停止） */
  public static final int COLD_SLOW_FACTOR = 128; // 50% 减速
  
  /** 冰冻效果：完全停止 */
  public static final int FREEZE_FACTOR = 0;

  /** 眩晕最短持续时间（帧） */
  public static final int MIN_STUN_DURATION = 5;
  
  /** 眩晕最长持续时间（帧） */
  public static final int MAX_STUN_DURATION = 50;

  /** 击退距离（子格） */
  public static final int KNOCKBACK_DISTANCE = 4;

  //==========================================================================
  // 状态效果应用
  //==========================================================================

  /**
   * 应用中毒效果
   * 
   * <p>参考 D2MOD SUNITDMG_ApplyPoisonDamage
   * 
   * @param targetEntityId 目标实体ID
   * @param poisonDamage 每帧毒素伤害
   * @param poisonDuration 持续时间（帧）
   * @param attackerEntityId 攻击者实体ID
   */
  public void applyPoison(int targetEntityId, int poisonDamage, int poisonDuration, 
      int attackerEntityId) {
    
    if (poisonDamage <= 0 || poisonDuration <= 0) {
      return;
    }

    if (stateSink != null) {
      stateSink.applyState(targetEntityId, StateId.POISON, poisonDuration, 1,
          attackerEntityId, poisonDamage, 4);
    }
    
    log.debug("Applied poison: target={}, damage={}/frame, duration={} frames",
        targetEntityId, poisonDamage, poisonDuration);
  }

  /**
   * 应用燃烧效果
   * 
   * <p>参考 D2MOD SUNITDMG_ApplyBurnDamage
   * 
   * @param targetEntityId 目标实体ID
   * @param burnDamage 每帧燃烧伤害
   * @param burnDuration 持续时间（帧）
   * @param attackerEntityId 攻击者实体ID
   */
  public void applyBurn(int targetEntityId, int burnDamage, int burnDuration,
      int attackerEntityId) {
    
    if (burnDamage <= 0 || burnDuration <= 0) {
      return;
    }

    if (stateSink != null) {
      stateSink.applyState(targetEntityId, StateId.BURNING, burnDuration, 1,
          attackerEntityId, burnDamage, 1);
    }
    
    log.debug("Applied burn: target={}, damage={}/frame, duration={} frames",
        targetEntityId, burnDamage, burnDuration);
  }

  /**
   * 应用冰冷减速效果
   * 
   * <p>参考 D2MOD SUNITDMG_ApplyColdState
   * 
   * @param targetEntityId 目标实体ID
   * @param coldDuration 持续时间（帧）
   * @param attackerEntityId 攻击者实体ID
   */
  public void applyCold(int targetEntityId, int coldDuration, int attackerEntityId) {
    if (coldDuration <= 0) {
      return;
    }

    if (stateSink != null) {
      stateSink.applyState(targetEntityId, StateId.COLD, coldDuration, 1,
          attackerEntityId, 0, 3);
    }
    
    log.debug("Applied cold slow: target={}, duration={} frames", targetEntityId, coldDuration);
  }

  /**
   * 应用冰冻效果（完全停止）
   * 
   * <p>参考 D2MOD SUNITDMG_ApplyFreezeState
   * 
   * @param targetEntityId 目标实体ID
   * @param freezeDuration 持续时间（帧）
   * @param attackerEntityId 攻击者实体ID
   */
  public void applyFreeze(int targetEntityId, int freezeDuration, int attackerEntityId) {
    if (freezeDuration <= 0) {
      return;
    }

    if (stateSink != null) {
      stateSink.applyState(targetEntityId, StateId.FREEZE, freezeDuration, 1,
          attackerEntityId, 0, 3);
    }
    
    log.debug("Applied freeze: target={}, duration={} frames", targetEntityId, freezeDuration);
  }

  /**
   * 应用眩晕效果
   * 
   * @param targetEntityId 目标实体ID
   * @param stunDuration 持续时间（帧）
   */
  public void applyStun(int targetEntityId, int stunDuration) {
    if (stunDuration <= 0) {
      return;
    }

    // 限制眩晕持续时间
    stunDuration = Math.max(MIN_STUN_DURATION, Math.min(MAX_STUN_DURATION, stunDuration));

    if (stateSink != null) {
      stateSink.applyState(targetEntityId, StateId.STUNNED, stunDuration, 1,
          -1, 0, 0);
    }
    
    log.debug("Applied stun: target={}, duration={} frames", targetEntityId, stunDuration);
  }

  /**
   * 应用击退效果
   * 
   * @param targetEntityId 目标实体ID
   * @param attackerX 攻击者X坐标
   * @param attackerY 攻击者Y坐标
   * @param targetX 目标X坐标
   * @param targetY 目标Y坐标
   */
  public void applyKnockback(int targetEntityId, float attackerX, float attackerY,
      float targetX, float targetY) {
    
    // 计算击退方向
    float dx = targetX - attackerX;
    float dy = targetY - attackerY;
    float dist = (float) Math.sqrt(dx * dx + dy * dy);
    
    if (dist < 0.01f) {
      return; // 距离太近，无法计算方向
    }

    // 归一化方向
    dx /= dist;
    dy /= dist;

    // 计算击退目标位置
    float knockbackX = targetX + dx * KNOCKBACK_DISTANCE;
    float knockbackY = targetY + dy * KNOCKBACK_DISTANCE;

    // TODO: 通过移动系统应用击退
    // 需要检查碰撞和边界
    
    log.debug("Applied knockback: target={}, to=({}, {})", 
        targetEntityId, knockbackX, knockbackY);
  }

  //==========================================================================
  // 从 DamageResult 应用所有状态效果
  //==========================================================================

  /**
   * 根据伤害结果应用所有状态效果
   * 
   * @param result 伤害结果
   * @param targetEntityId 目标实体ID
   * @param attackerEntityId 攻击者实体ID
   * @param attackerX 攻击者X
   * @param attackerY 攻击者Y
   * @param targetX 目标X
   * @param targetY 目标Y
   */
  public void applyFromDamageResult(DamageResult result, int targetEntityId, int attackerEntityId,
      float attackerX, float attackerY, float targetX, float targetY) {
    
    // 毒素效果
    if (result.poisonDamage > 0 && result.poisonDuration > 0) {
      applyPoison(targetEntityId, result.poisonDamage, result.poisonDuration, attackerEntityId);
    }

    // 燃烧效果
    if (result.burnDamage > 0 && result.burnDuration > 0) {
      applyBurn(targetEntityId, result.burnDamage, result.burnDuration, attackerEntityId);
    }

    // 冰冻效果优先于冰冷减速
    if (result.freezeDuration > 0) {
      applyFreeze(targetEntityId, result.freezeDuration, attackerEntityId);
    } else if (result.coldDuration > 0) {
      applyCold(targetEntityId, result.coldDuration, attackerEntityId);
    }

    // 眩晕效果
    if (result.stunDuration > 0) {
      applyStun(targetEntityId, result.stunDuration);
    }

    // 击退效果
    if (result.hasKnockback()) {
      applyKnockback(targetEntityId, attackerX, attackerY, targetX, targetY);
    }
  }

  //==========================================================================
  // 持续伤害计算
  //==========================================================================

  /**
   * 计算持续伤害的每帧伤害
   * 
   * <p>用于中毒、燃烧等持续伤害效果
   * 
   * @param totalDamage 总伤害
   * @param duration 持续时间（帧）
   * @return 每帧伤害
   */
  public int calculateDamagePerFrame(int totalDamage, int duration) {
    if (duration <= 0) return 0;
    return totalDamage / duration;
  }

  /**
   * 计算冰冷减速百分比
   * 
   * @param coldLevel 冰冷等级/强度
   * @return 减速百分比（0-100）
   */
  public int calculateColdSlowPercent(int coldLevel) {
    // 基础减速50%，高等级冰冷可增加
    int slowPercent = 50 + coldLevel / 2;
    return Math.min(slowPercent, 75); // 最大减速75%
  }
}
