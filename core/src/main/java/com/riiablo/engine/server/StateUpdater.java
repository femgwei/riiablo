package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;

import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 状态更新系统 - 基于 D2MOD 状态处理逻辑移植
 * 
 * <p>该系统每帧更新所有单位的状态，处理：
 * <ul>
 *   <li>状态持续时间倒计时</li>
 *   <li>过期状态移除</li>
 *   <li>持续伤害（DOT）效果</li>
 *   <li>状态效果应用（减速、眩晕等）</li>
 * </ul>
 * 
 * <p>参考：D2MOD/source/D2Common/src/D2States.cpp
 * 
 * @author riiablo team
 */
@All(UnitStates.class)
public class StateUpdater extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(StateUpdater.class);

  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<Velocity> mVelocity;

  //==========================================================================
  // 系统处理
  //==========================================================================

  @Override
  protected void process(int entityId) {
    UnitStates unitStates = mUnitStates.get(entityId);
    if (unitStates == null || unitStates.stateList == null) {
      return;
    }

    StateList stateList = unitStates.stateList;
    
    // 更新所有状态（处理过期）
    stateList.update();
    
    // 应用状态效果
    applyStateEffects(entityId, stateList);
  }

  //==========================================================================
  // 状态效果应用
  //==========================================================================

  /**
   * 应用状态效果到实体
   * 
   * @param entityId 实体ID
   * @param stateList 状态列表
   */
  private void applyStateEffects(int entityId, StateList stateList) {
    // 处理移动速度修正
    if (mVelocity.has(entityId)) {
      applyVelocityModifiers(entityId, stateList);
    }
    
    // 处理持续伤害
    processDamageOverTime(entityId, stateList);
  }

  /**
   * 应用移动速度修正
   * 
   * @param entityId 实体ID
   * @param stateList 状态列表
   */
  private void applyVelocityModifiers(int entityId, StateList stateList) {
    Velocity velocity = mVelocity.get(entityId);
    
    // 检查冰冻状态 - 完全停止移动
    if (stateList.hasState(StateId.FREEZE)) {
      velocity.velocity.setZero();
      return;
    }
    
    // 检查眩晕状态 - 完全停止移动
    if (stateList.hasState(StateId.STUNNED)) {
      velocity.velocity.setZero();
      return;
    }
    
    // 计算减速效果
    int slowPercent = 0;
    
    // 寒冷减速
    if (stateList.hasState(StateId.COLD)) {
      UnitState coldState = stateList.getState(StateId.COLD);
      slowPercent += 30 + coldState.level * 2; // 基础30% + 等级加成
    }
    
    // 减速状态
    if (stateList.hasState(StateId.SLOWED)) {
      UnitState slowState = stateList.getState(StateId.SLOWED);
      slowPercent += 25 + slowState.level * 5;
    }
    
    // 衰老诅咒
    if (stateList.hasState(StateId.DECREPIFY)) {
      slowPercent += 50;
    }
    
    // 应用减速（限制最大减速为90%）
    if (slowPercent > 0) {
      slowPercent = Math.min(slowPercent, 90);
      float multiplier = 1.0f - (slowPercent / 100.0f);
      velocity.velocity.scl(multiplier);
    }
  }

  /**
   * 处理持续伤害效果
   * 
   * @param entityId 实体ID
   * @param stateList 状态列表
   */
  private void processDamageOverTime(int entityId, StateList stateList) {
    // 处理中毒
    if (stateList.hasState(StateId.POISON)) {
      UnitState poisonState = stateList.getState(StateId.POISON);
      if (poisonState.damagePerFrame > 0) {
        // TODO: 应用毒素伤害
        // 需要 AttributesWrapper 组件来修改生命值
        log.trace("实体 {} 受到 {} 点毒素伤害", entityId, poisonState.damagePerFrame);
      }
    }
    
    // 处理燃烧
    if (stateList.hasState(StateId.BURNING)) {
      UnitState burningState = stateList.getState(StateId.BURNING);
      if (burningState.damagePerFrame > 0) {
        // TODO: 应用燃烧伤害
        log.trace("实体 {} 受到 {} 点燃烧伤害", entityId, burningState.damagePerFrame);
      }
    }
    
    // 处理撕开伤口
    if (stateList.hasState(StateId.OPENWOUNDS)) {
      UnitState woundsState = stateList.getState(StateId.OPENWOUNDS);
      // 撕开伤口伤害基于角色等级
      int damage = woundsState.level * 2;
      // TODO: 应用伤害
      log.trace("实体 {} 受到 {} 点撕开伤口伤害", entityId, damage);
    }
  }

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 向实体添加状态（便捷方法）
   * 
   * @param entityId 目标实体ID
   * @param stateId 状态ID
   * @param duration 持续时间（帧数）
   * @param level 状态等级
   * @param sourceId 来源实体ID
   */
  public void addState(int entityId, int stateId, int duration, int level, int sourceId) {
    if (!mUnitStates.has(entityId)) {
      log.warn("实体 {} 没有 UnitStates 组件", entityId);
      return;
    }
    
    UnitStates unitStates = mUnitStates.get(entityId);
    if (unitStates.stateList == null) {
      unitStates.init(entityId);
    }
    
    unitStates.stateList.addState(stateId, duration, level, sourceId);
  }

  /**
   * 移除实体的状态
   * 
   * @param entityId 目标实体ID
   * @param stateId 状态ID
   */
  public void removeState(int entityId, int stateId) {
    if (!mUnitStates.has(entityId)) {
      return;
    }
    
    UnitStates unitStates = mUnitStates.get(entityId);
    if (unitStates.stateList != null) {
      unitStates.stateList.removeState(stateId);
    }
  }

  /**
   * 检查实体是否有指定状态
   * 
   * @param entityId 实体ID
   * @param stateId 状态ID
   * @return true 如果有状态
   */
  public boolean hasState(int entityId, int stateId) {
    if (!mUnitStates.has(entityId)) {
      return false;
    }
    
    UnitStates unitStates = mUnitStates.get(entityId);
    return unitStates.stateList != null && unitStates.stateList.hasState(stateId);
  }

  /**
   * 检查实体是否被冰冻
   * 
   * @param entityId 实体ID
   * @return true 如果被冰冻
   */
  public boolean isFrozen(int entityId) {
    return hasState(entityId, StateId.FREEZE);
  }

  /**
   * 检查实体是否被眩晕
   * 
   * @param entityId 实体ID
   * @return true 如果被眩晕
   */
  public boolean isStunned(int entityId) {
    return hasState(entityId, StateId.STUNNED);
  }

  /**
   * 检查实体是否能行动
   * 
   * @param entityId 实体ID
   * @return true 如果能行动
   */
  public boolean canAct(int entityId) {
    return !isFrozen(entityId) && !isStunned(entityId);
  }

  /**
   * 检查实体是否在变形状态
   * 
   * @param entityId 实体ID
   * @return true 如果在变形状态
   */
  public boolean isTransformed(int entityId) {
    if (!mUnitStates.has(entityId)) {
      return false;
    }
    
    UnitStates unitStates = mUnitStates.get(entityId);
    return unitStates.stateList != null && unitStates.stateList.isTransformed();
  }
}
