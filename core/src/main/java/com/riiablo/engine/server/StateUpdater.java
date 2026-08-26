package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;

import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.combat.StatusEffectApplier;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import net.mostlyoriginal.api.event.common.EventSystem;

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
public class StateUpdater extends IteratingSystem implements StatusEffectApplier.StateSink {
  private static final Logger log = LogManager.getLogger(StateUpdater.class);

  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;

  protected EventSystem events;

  @Override
  protected void initialize() {
    super.initialize();
    StatusEffectApplier.INSTANCE.setStateSink(this);
  }

  //==========================================================================
  // 系统处理
  //==========================================================================

  @Override
  protected void process(int entityId) {
    UnitStates unitStates = mUnitStates.get(entityId);
    if (unitStates == null || unitStates.stateList == null) {
      return;
    }

    // Network clients render the server snapshot. They must not independently
    // tick DOT, expire states, or emit DeathEvent and rewards a second time.
    if (unitStates.snapshotOnly) {
      if (mVelocity.has(entityId)) applyVelocityModifiers(entityId, unitStates.stateList);
      return;
    }

    StateList stateList = unitStates.stateList;
    
    // Resolve this tick before decrementing duration. A one-frame state must
    // still deal its final DOT tick, then expire.
    processDamageOverTime(entityId, stateList);
    stateList.update();

    // Apply movement/control effects only while the state remains active.
    if (mVelocity.has(entityId)) {
      applyVelocityModifiers(entityId, stateList);
    }
  }

  //==========================================================================
  // 状态效果应用
  //==========================================================================

  /**
   * 应用移动速度修正
   * 
   * @param entityId 实体ID
   * @param stateList 状态列表
   */
  private void applyVelocityModifiers(int entityId, StateList stateList) {
    Velocity velocity = mVelocity.get(entityId);

    // Keep the desired velocity untouched. VelocityAdder applies this state
    // multiplier after Pathfinder has selected the direction for this tick.
    velocity.stateSpeedMultiplier = 1f;
    velocity.stateMovementLocked = false;
    
    // 检查冰冻状态 - 完全停止移动
    if (stateList.hasState(StateId.FREEZE)) {
      velocity.stateMovementLocked = true;
      return;
    }
    
    // 检查眩晕状态 - 完全停止移动
    if (stateList.hasState(StateId.STUNNED)) {
      velocity.stateMovementLocked = true;
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
      velocity.stateSpeedMultiplier = 1.0f - (slowPercent / 100.0f);
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
        applyDamageOverTime(entityId, poisonState.sourceEntityId,
            poisonState.damagePerFrame, stateList, StateId.POISON);
      }
    }
    
    // 处理燃烧
    if (stateList.hasState(StateId.BURNING)) {
      UnitState burningState = stateList.getState(StateId.BURNING);
      if (burningState.damagePerFrame > 0) {
        applyDamageOverTime(entityId, burningState.sourceEntityId,
            burningState.damagePerFrame, stateList, StateId.BURNING);
      }
    }
    
    // 处理撕开伤口
    if (stateList.hasState(StateId.OPENWOUNDS)) {
      UnitState woundsState = stateList.getState(StateId.OPENWOUNDS);
      // 撕开伤口伤害基于角色等级
      int damage = woundsState.level * 2;
      if (damage > 0) {
        applyDamageOverTime(entityId, woundsState.sourceEntityId,
            damage, stateList, StateId.OPENWOUNDS);
      }
    }
  }

  /** Applies one server tick of DOT and emits the normal damage/death events. */
  private void applyDamageOverTime(int entityId, int sourceEntityId, float damage,
      StateList stateList, int stateId) {
    if (damage <= 0 || !mAttributesWrapper.has(entityId)) return;
    Attributes attrs = mAttributesWrapper.get(entityId).attrs;
    if (attrs == null) return;
    StatRef hitpoints = attrs.get(Stat.hitpoints, StatRef.obtain());
    if (hitpoints == null || hitpoints.asFixed() <= 0f) return;

    DamageEvent event = DamageEvent.obtain(sourceEntityId, entityId, damage);
    if (events != null) events.dispatch(event);
    float appliedDamage = Math.max(0f, event.damage);
    hitpoints.sub(appliedDamage);
    float hpAfter = hitpoints.asFixed();
    if (hpAfter <= 0f) {
      hitpoints.set(0f);
      log.debug("Entity {} died from state {} (damage={})", entityId,
          StateId.getName(stateId), appliedDamage);
      if (events != null) events.dispatch(DeathEvent.obtain(sourceEntityId, entityId));
      // Prevent a dead entity from emitting the same death event every tick.
      stateList.clearAll();
    } else {
      log.trace("Entity {} takes {} damage from state {} (hp={})", entityId,
          appliedDamage, StateId.getName(stateId), hpAfter);
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

  @Override
  public void applyState(int entityId, int stateId, int duration, int level,
      int sourceId, int damagePerFrame, int damageType) {
    if (!mUnitStates.has(entityId)) {
      log.warn("Entity {} has no UnitStates component; state {} ignored", entityId, stateId);
      return;
    }
    UnitStates unitStates = mUnitStates.get(entityId);
    if (unitStates.stateList == null) unitStates.init(entityId);
    UnitState state = unitStates.stateList.addState(stateId, duration, level, sourceId);
    if (state == null) return;
    if (damagePerFrame > state.damagePerFrame) state.damagePerFrame = damagePerFrame;
    state.damageType = damageType;
    state.needsSync = true;
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
