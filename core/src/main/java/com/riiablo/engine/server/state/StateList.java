package com.riiablo.engine.server.state;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;

import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 状态列表 - 管理单位上的所有状态实例
 * 
 * <p>该类维护一个状态实例列表，提供添加、移除、查询和更新功能。
 * 使用对象池来减少 GC 压力。
 * 
 * <p>参考：D2MOO 的状态链表管理
 * 
 * @author riiablo team
 */
public class StateList {
  private static final Logger log = LogManager.getLogger(StateList.class);

  /** 状态实例对象池 */
  private static final Pool<UnitState> statePool = new Pool<UnitState>() {
    @Override
    protected UnitState newObject() {
      return new UnitState();
    }
    
    @Override
    protected void reset(UnitState state) {
      state.reset();
    }
  };

  /** 状态实例列表 */
  private final Array<UnitState> states;

  /** 状态标志位（用于快速查询） */
  private final StateFlags flags;

  /** 所属实体ID */
  private int entityId = -1;

  //==========================================================================
  // 构造函数
  //==========================================================================

  /**
   * 创建状态列表
   */
  public StateList() {
    this.states = new Array<>(8);
    this.flags = new StateFlags();
  }

  /**
   * 创建状态列表并关联实体
   * 
   * @param entityId 实体ID
   */
  public StateList(int entityId) {
    this();
    this.entityId = entityId;
  }

  //==========================================================================
  // 状态添加
  //==========================================================================

  /**
   * 添加或刷新状态
   * 
   * @param stateId 状态ID
   * @param duration 持续时间（帧数，0 表示永久）
   * @param level 状态等级
   * @param sourceEntityId 来源实体ID
   * @return 添加或更新的状态实例
   */
  public UnitState addState(int stateId, int duration, int level, int sourceEntityId) {
    if (!StateId.isValid(stateId)) {
      log.warn("尝试添加无效的状态ID: {}", stateId);
      return null;
    }

    // 检查是否已存在相同状态
    UnitState existing = getState(stateId);
    
    if (existing != null) {
      // 刷新持续时间（取较大值）
      existing.refresh(duration);
      // 增强等级（取较大值）
      existing.enhance(level);
      log.debug("刷新状态 {} 持续时间={}, 等级={}", StateId.getName(stateId), duration, level);
      return existing;
    }

    // 创建新状态
    UnitState state = statePool.obtain();
    state.stateId = stateId;
    state.duration = duration;
    state.initialDuration = duration;
    state.level = level;
    state.sourceEntityId = sourceEntityId;
    
    states.add(state);
    flags.set(stateId);
    
    log.debug("添加状态 {} 到实体 {}, 持续时间={}, 等级={}", 
        StateId.getName(stateId), entityId, duration, level);
    
    return state;
  }

  /**
   * 添加状态（简化版本）
   * 
   * @param stateId 状态ID
   * @param duration 持续时间
   * @return 状态实例
   */
  public UnitState addState(int stateId, int duration) {
    return addState(stateId, duration, 1, -1);
  }

  /**
   * 添加永久状态
   * 
   * @param stateId 状态ID
   * @return 状态实例
   */
  public UnitState addPermanentState(int stateId) {
    return addState(stateId, 0, 1, -1);
  }

  //==========================================================================
  // 状态移除
  //==========================================================================

  /**
   * 移除指定状态
   * 
   * @param stateId 状态ID
   * @return true 如果成功移除
   */
  public boolean removeState(int stateId) {
    for (int i = states.size - 1; i >= 0; i--) {
      UnitState state = states.get(i);
      if (state.stateId == stateId) {
        states.removeIndex(i);
        flags.clear(stateId);
        statePool.free(state);
        log.debug("移除状态 {} 从实体 {}", StateId.getName(stateId), entityId);
        return true;
      }
    }
    return false;
  }

  /**
   * 移除所有诅咒状态
   * 
   * @return 移除的状态数量
   */
  public int removeCurses() {
    int count = 0;
    for (int i = states.size - 1; i >= 0; i--) {
      UnitState state = states.get(i);
      if (state.isCurse()) {
        flags.clear(state.stateId);
        states.removeIndex(i);
        statePool.free(state);
        count++;
      }
    }
    if (count > 0) {
      log.debug("移除 {} 个诅咒状态从实体 {}", count, entityId);
    }
    return count;
  }

  /**
   * 移除所有状态
   */
  public void clearAll() {
    for (UnitState state : states) {
      statePool.free(state);
    }
    states.clear();
    flags.clearAll();
    log.debug("清除实体 {} 的所有状态", entityId);
  }

  /**
   * 移除来自指定实体的所有状态
   * 
   * @param sourceEntityId 来源实体ID
   * @return 移除的状态数量
   */
  public int removeStatesFromSource(int sourceEntityId) {
    int count = 0;
    for (int i = states.size - 1; i >= 0; i--) {
      UnitState state = states.get(i);
      if (state.sourceEntityId == sourceEntityId) {
        flags.clear(state.stateId);
        states.removeIndex(i);
        statePool.free(state);
        count++;
      }
    }
    return count;
  }

  //==========================================================================
  // 状态查询
  //==========================================================================

  /**
   * 检查是否有指定状态
   * 
   * @param stateId 状态ID
   * @return true 如果状态存在
   */
  public boolean hasState(int stateId) {
    return flags.check(stateId);
  }

  /**
   * 获取指定状态实例
   * 
   * @param stateId 状态ID
   * @return 状态实例，如果不存在返回 null
   */
  public UnitState getState(int stateId) {
    if (!flags.check(stateId)) {
      return null;
    }
    
    for (UnitState state : states) {
      if (state.stateId == stateId) {
        return state;
      }
    }
    return null;
  }

  /**
   * 获取状态等级
   * 
   * @param stateId 状态ID
   * @return 状态等级，如果不存在返回 0
   */
  public int getStateLevel(int stateId) {
    UnitState state = getState(stateId);
    return state != null ? state.level : 0;
  }

  /**
   * 获取状态剩余时间
   * 
   * @param stateId 状态ID
   * @return 剩余帧数，如果不存在返回 0
   */
  public int getStateDuration(int stateId) {
    UnitState state = getState(stateId);
    return state != null ? state.duration : 0;
  }

  /**
   * 检查是否有任何诅咒状态
   * 
   * @return true 如果有诅咒
   */
  public boolean hasCurse() {
    return flags.checkMask(StateMask.CURSE);
  }

  /**
   * 检查是否有任何光环状态
   * 
   * @return true 如果有光环
   */
  public boolean hasAura() {
    return flags.checkMask(StateMask.AURA);
  }

  /**
   * 检查是否是变形状态
   * 
   * @return true 如果是变形
   */
  public boolean isTransformed() {
    return flags.checkMask(StateMask.TRANSFORM);
  }

  /**
   * 获取状态数量
   * 
   * @return 状态数量
   */
  public int size() {
    return states.size;
  }

  /**
   * 检查是否没有状态
   * 
   * @return true 如果没有状态
   */
  public boolean isEmpty() {
    return states.size == 0;
  }

  /**
   * 获取状态标志
   * 
   * @return 状态标志对象
   */
  public StateFlags getFlags() {
    return flags;
  }

  //==========================================================================
  // 状态更新
  //==========================================================================

  /**
   * 更新所有状态（每帧调用）
   * 
   * <p>移除过期状态，处理持续伤害等
   */
  public void update() {
    for (int i = states.size - 1; i >= 0; i--) {
      UnitState state = states.get(i);
      if (!state.update()) {
        // 状态已过期，移除
        flags.clear(state.stateId);
        states.removeIndex(i);
        statePool.free(state);
      }
    }
  }

  /**
   * 计算所有状态的伤害修正总和
   * 
   * @return 伤害修正百分比
   */
  public int getTotalDamageModifier() {
    int total = 0;
    for (UnitState state : states) {
      total += state.damageModifier;
    }
    return total;
  }

  /**
   * 计算所有状态的防御修正总和
   * 
   * @return 防御修正百分比
   */
  public int getTotalDefenseModifier() {
    int total = 0;
    for (UnitState state : states) {
      total += state.defenseModifier;
    }
    return total;
  }

  /**
   * 计算所有状态的移动速度修正总和
   * 
   * @return 移动速度修正百分比
   */
  public int getTotalVelocityModifier() {
    int total = 0;
    for (UnitState state : states) {
      total += state.velocityModifier;
    }
    return total;
  }

  /**
   * 计算所有状态的抗性修正总和
   * 
   * @param resistType 抗性类型（0=火, 1=冷, 2=闪电, 3=毒, 4=魔法）
   * @return 抗性修正值
   */
  public int getTotalResistModifier(int resistType) {
    int total = 0;
    for (UnitState state : states) {
      switch (resistType) {
        case 0: total += state.fireResistModifier; break;
        case 1: total += state.coldResistModifier; break;
        case 2: total += state.lightResistModifier; break;
        case 3: total += state.poisonResistModifier; break;
        case 4: total += state.magicResistModifier; break;
      }
    }
    return total;
  }

  //==========================================================================
  // 遍历支持
  //==========================================================================

  /**
   * 获取状态数组（只读访问）
   * 
   * @return 状态数组
   */
  public Array<UnitState> getStates() {
    return states;
  }

  /**
   * 设置所属实体ID
   * 
   * @param entityId 实体ID
   */
  public void setEntityId(int entityId) {
    this.entityId = entityId;
  }

  /**
   * 获取所属实体ID
   * 
   * @return 实体ID
   */
  public int getEntityId() {
    return entityId;
  }

  @Override
  public String toString() {
    return "StateList{entityId=" + entityId + ", states=" + states.size + ", " + flags + "}";
  }
}
