package com.riiablo.engine.server.state;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;

import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 状态效果管理器 - 基于 D2MOO D2States.cpp 移植
 * 
 * <p>管理所有 Buff/Debuff 状态效果：
 * <ul>
 *   <li>状态应用和移除</li>
 *   <li>状态持续时间管理</li>
 *   <li>状态叠加和刷新规则</li>
 *   <li>状态效果计算</li>
 *   <li>状态免疫检查</li>
 * </ul>
 * 
 * <p>参考：D2MOO/source/D2Common/src/D2States.cpp
 * 
 * @author riiablo team
 */
public class StateEffectManager {
  private static final Logger log = LogManager.getLogger(StateEffectManager.class);

  //==========================================================================
  // 常量 - 状态类型
  //==========================================================================

  /** 增益状态（Buff） */
  public static final int STATE_TYPE_BUFF = 0;

  /** 减益状态（Debuff） */
  public static final int STATE_TYPE_DEBUFF = 1;

  /** 光环状态 */
  public static final int STATE_TYPE_AURA = 2;

  /** 变身状态 */
  public static final int STATE_TYPE_TRANSFORM = 3;

  /** 控制状态（冰冻、眩晕等） */
  public static final int STATE_TYPE_CONTROL = 4;

  //==========================================================================
  // 常量 - 状态标志
  //==========================================================================

  /** 可叠加 */
  public static final int FLAG_STACKABLE = 0x0001;

  /** 可刷新持续时间 */
  public static final int FLAG_REFRESHABLE = 0x0002;

  /** 死亡后保留 */
  public static final int FLAG_PERSIST_DEATH = 0x0004;

  /** 不可驱散 */
  public static final int FLAG_UNDISPELLABLE = 0x0008;

  /** 隐藏状态（不显示图标） */
  public static final int FLAG_HIDDEN = 0x0010;

  /** 禁用移动 */
  public static final int FLAG_DISABLE_MOVE = 0x0020;

  /** 禁用攻击 */
  public static final int FLAG_DISABLE_ATTACK = 0x0040;

  /** 禁用技能 */
  public static final int FLAG_DISABLE_SKILL = 0x0080;

  //==========================================================================
  // 内部类 - 状态效果
  //==========================================================================

  /**
   * 状态效果实例
   */
  public static class StateEffect {
    /** 状态 ID */
    public int stateId;

    /** 状态类型 */
    public int stateType;

    /** 标志位 */
    public int flags;

    /** 施放者实体 ID */
    public int casterId;

    /** 剩余持续时间（帧） */
    public int duration;

    /** 最大持续时间（帧） */
    public int maxDuration;

    /** 叠加层数 */
    public int stacks;

    /** 最大叠加层数 */
    public int maxStacks;

    /** 效果值1（如伤害/减速百分比等） */
    public int value1;

    /** 效果值2 */
    public int value2;

    /** 效果值3 */
    public int value3;

    /** 技能等级（用于计算效果） */
    public int skillLevel;

    /** 状态覆盖层 ID（视觉效果） */
    public int overlayId;

    public StateEffect(int stateId) {
      this.stateId = stateId;
      this.stacks = 1;
      this.maxStacks = 1;
    }

    public boolean isExpired() {
      return duration <= 0;
    }

    public boolean canStack() {
      return (flags & FLAG_STACKABLE) != 0;
    }

    public boolean canRefresh() {
      return (flags & FLAG_REFRESHABLE) != 0;
    }

    public boolean persistsOnDeath() {
      return (flags & FLAG_PERSIST_DEATH) != 0;
    }

    public boolean isUndispellable() {
      return (flags & FLAG_UNDISPELLABLE) != 0;
    }

    public boolean disablesMove() {
      return (flags & FLAG_DISABLE_MOVE) != 0;
    }

    public boolean disablesAttack() {
      return (flags & FLAG_DISABLE_ATTACK) != 0;
    }

    public boolean disablesSkill() {
      return (flags & FLAG_DISABLE_SKILL) != 0;
    }
  }

  /**
   * 状态效果定义（从数据表读取）
   */
  public static class StateDefinition {
    public int stateId;
    public String stateName;
    public int stateType;
    public int defaultFlags;
    public int maxStacks;
    public int overlayId;
    public boolean isTransform;
    public boolean isCurse;
    public boolean isAura;
  }

  /**
   * 实体的状态列表
   */
  public static class EntityStateList {
    public int entityId;
    public Array<StateEffect> states = new Array<>();
    public IntMap<StateEffect> stateByIdCache = new IntMap<>();

    public EntityStateList(int entityId) {
      this.entityId = entityId;
    }
  }

  //==========================================================================
  // 回调接口
  //==========================================================================

  /**
   * 状态效果回调
   */
  public interface StateCallback {
    /**
     * 状态应用时调用
     */
    void onStateApplied(int entityId, StateEffect state);

    /**
     * 状态移除时调用
     */
    void onStateRemoved(int entityId, StateEffect state);

    /**
     * 状态刷新时调用
     */
    void onStateRefreshed(int entityId, StateEffect state);

    /**
     * 状态叠加时调用
     */
    void onStateStacked(int entityId, StateEffect state, int newStacks);

    /**
     * 显示覆盖层效果
     */
    void onShowOverlay(int entityId, int overlayId);

    /**
     * 隐藏覆盖层效果
     */
    void onHideOverlay(int entityId, int overlayId);
  }

  //==========================================================================
  // 字段
  //==========================================================================

  /** 所有实体的状态列表 */
  private final IntMap<EntityStateList> entityStates = new IntMap<>();

  /** 状态定义表 */
  private final IntMap<StateDefinition> stateDefinitions = new IntMap<>();

  /** 回调 */
  private StateCallback callback;

  //==========================================================================
  // 构造函数
  //==========================================================================

  public StateEffectManager() {
    registerDefaultStates();
  }

  //==========================================================================
  // 核心方法 - 状态管理
  //==========================================================================

  /**
   * 应用状态效果
   * 
   * @param entityId 目标实体 ID
   * @param stateId 状态 ID
   * @param duration 持续时间（帧）
   * @param casterId 施放者实体 ID
   * @param skillLevel 技能等级
   * @param value1 效果值1
   * @return true 如果成功应用
   */
  public boolean applyState(int entityId, int stateId, int duration, int casterId,
      int skillLevel, int value1) {
    return applyState(entityId, stateId, duration, casterId, skillLevel, value1, 0, 0);
  }

  /**
   * 应用状态效果（完整参数）
   */
  public boolean applyState(int entityId, int stateId, int duration, int casterId,
      int skillLevel, int value1, int value2, int value3) {

    // 获取状态定义
    StateDefinition def = stateDefinitions.get(stateId);

    // 获取或创建实体状态列表
    EntityStateList stateList = getOrCreateStateList(entityId);

    // 检查是否已有相同状态
    StateEffect existing = stateList.stateByIdCache.get(stateId);

    if (existing != null) {
      // 处理已存在的状态
      return handleExistingState(entityId, existing, duration, skillLevel, value1, value2, value3);
    }

    // 创建新状态
    StateEffect state = new StateEffect(stateId);
    state.casterId = casterId;
    state.duration = duration;
    state.maxDuration = duration;
    state.skillLevel = skillLevel;
    state.value1 = value1;
    state.value2 = value2;
    state.value3 = value3;

    // 应用定义的默认值
    if (def != null) {
      state.stateType = def.stateType;
      state.flags = def.defaultFlags;
      state.maxStacks = def.maxStacks;
      state.overlayId = def.overlayId;
    } else {
      state.stateType = STATE_TYPE_BUFF;
      state.flags = FLAG_REFRESHABLE;
      state.maxStacks = 1;
    }

    // 添加到列表
    stateList.states.add(state);
    stateList.stateByIdCache.put(stateId, state);

    log.debug("Applied state {} to entity {} for {} frames (value={})",
        stateId, entityId, duration, value1);

    // 触发回调
    if (callback != null) {
      callback.onStateApplied(entityId, state);
      if (state.overlayId > 0) {
        callback.onShowOverlay(entityId, state.overlayId);
      }
    }

    return true;
  }

  /**
   * 处理已存在的状态
   */
  private boolean handleExistingState(int entityId, StateEffect existing, int duration,
      int skillLevel, int value1, int value2, int value3) {

    // 检查是否可叠加
    if (existing.canStack() && existing.stacks < existing.maxStacks) {
      existing.stacks++;
      existing.value1 += value1;
      existing.value2 += value2;
      existing.value3 += value3;

      log.debug("Stacked state {} on entity {}, stacks={}", 
          existing.stateId, entityId, existing.stacks);

      if (callback != null) {
        callback.onStateStacked(entityId, existing, existing.stacks);
      }
      return true;
    }

    // 检查是否可刷新
    if (existing.canRefresh()) {
      // 刷新持续时间（取较大值）
      if (duration > existing.duration) {
        existing.duration = duration;
        existing.maxDuration = duration;
      }

      // 使用较高的技能等级
      if (skillLevel > existing.skillLevel) {
        existing.skillLevel = skillLevel;
        existing.value1 = value1;
        existing.value2 = value2;
        existing.value3 = value3;
      }

      log.debug("Refreshed state {} on entity {}", existing.stateId, entityId);

      if (callback != null) {
        callback.onStateRefreshed(entityId, existing);
      }
      return true;
    }

    // 无法叠加或刷新
    return false;
  }

  /**
   * 移除状态效果
   * 
   * @param entityId 目标实体 ID
   * @param stateId 状态 ID
   * @return true 如果成功移除
   */
  public boolean removeState(int entityId, int stateId) {
    EntityStateList stateList = entityStates.get(entityId);
    if (stateList == null) {
      return false;
    }

    StateEffect state = stateList.stateByIdCache.remove(stateId);
    if (state == null) {
      return false;
    }

    stateList.states.removeValue(state, true);

    log.debug("Removed state {} from entity {}", stateId, entityId);

    // 触发回调
    if (callback != null) {
      callback.onStateRemoved(entityId, state);
      if (state.overlayId > 0) {
        callback.onHideOverlay(entityId, state.overlayId);
      }
    }

    return true;
  }

  /**
   * 驱散状态（只能驱散可驱散的状态）
   * 
   * @param entityId 目标实体 ID
   * @param stateType 要驱散的状态类型（-1 表示所有）
   * @return 驱散的状态数量
   */
  public int dispelStates(int entityId, int stateType) {
    EntityStateList stateList = entityStates.get(entityId);
    if (stateList == null) {
      return 0;
    }

    int dispelled = 0;
    Array<StateEffect> toRemove = new Array<>();

    for (StateEffect state : stateList.states) {
      if (state.isUndispellable()) {
        continue;
      }

      if (stateType < 0 || state.stateType == stateType) {
        toRemove.add(state);
        dispelled++;
      }
    }

    for (StateEffect state : toRemove) {
      removeState(entityId, state.stateId);
    }

    return dispelled;
  }

  /**
   * 检查是否有指定状态
   * 
   * @param entityId 目标实体 ID
   * @param stateId 状态 ID
   * @return true 如果有该状态
   */
  public boolean hasState(int entityId, int stateId) {
    EntityStateList stateList = entityStates.get(entityId);
    return stateList != null && stateList.stateByIdCache.containsKey(stateId);
  }

  /**
   * 获取状态效果
   * 
   * @param entityId 目标实体 ID
   * @param stateId 状态 ID
   * @return 状态效果，null 如果不存在
   */
  public StateEffect getState(int entityId, int stateId) {
    EntityStateList stateList = entityStates.get(entityId);
    return stateList != null ? stateList.stateByIdCache.get(stateId) : null;
  }

  /**
   * 获取实体所有状态
   */
  public Array<StateEffect> getAllStates(int entityId) {
    EntityStateList stateList = entityStates.get(entityId);
    return stateList != null ? stateList.states : null;
  }

  //==========================================================================
  // 更新方法
  //==========================================================================

  /**
   * 更新所有状态效果（每帧调用）
   */
  public void update() {
    for (IntMap.Entry<EntityStateList> entry : entityStates) {
      updateEntityStates(entry.value);
    }
  }

  /**
   * 更新单个实体的状态
   */
  private void updateEntityStates(EntityStateList stateList) {
    Array<StateEffect> expired = null;

    for (StateEffect state : stateList.states) {
      // 减少持续时间
      if (state.duration > 0) {
        state.duration--;
      }

      // 检查是否过期
      if (state.isExpired()) {
        if (expired == null) {
          expired = new Array<>();
        }
        expired.add(state);
      }
    }

    // 移除过期状态
    if (expired != null) {
      for (StateEffect state : expired) {
        removeState(stateList.entityId, state.stateId);
      }
    }
  }

  /**
   * 处理实体死亡
   */
  public void onEntityDeath(int entityId) {
    EntityStateList stateList = entityStates.get(entityId);
    if (stateList == null) {
      return;
    }

    Array<StateEffect> toRemove = new Array<>();

    for (StateEffect state : stateList.states) {
      if (!state.persistsOnDeath()) {
        toRemove.add(state);
      }
    }

    for (StateEffect state : toRemove) {
      removeState(entityId, state.stateId);
    }
  }

  //==========================================================================
  // 状态查询方法
  //==========================================================================

  /**
   * 检查实体是否被控制（无法行动）
   */
  public boolean isControlled(int entityId) {
    EntityStateList stateList = entityStates.get(entityId);
    if (stateList == null) {
      return false;
    }

    for (StateEffect state : stateList.states) {
      if (state.stateType == STATE_TYPE_CONTROL) {
        return true;
      }
    }
    return false;
  }

  /**
   * 检查实体是否可以移动
   */
  public boolean canMove(int entityId) {
    EntityStateList stateList = entityStates.get(entityId);
    if (stateList == null) {
      return true;
    }

    for (StateEffect state : stateList.states) {
      if (state.disablesMove()) {
        return false;
      }
    }
    return true;
  }

  /**
   * 检查实体是否可以攻击
   */
  public boolean canAttack(int entityId) {
    EntityStateList stateList = entityStates.get(entityId);
    if (stateList == null) {
      return true;
    }

    for (StateEffect state : stateList.states) {
      if (state.disablesAttack()) {
        return false;
      }
    }
    return true;
  }

  /**
   * 检查实体是否可以使用技能
   */
  public boolean canUseSkill(int entityId) {
    EntityStateList stateList = entityStates.get(entityId);
    if (stateList == null) {
      return true;
    }

    for (StateEffect state : stateList.states) {
      if (state.disablesSkill()) {
        return false;
      }
    }
    return true;
  }

  /**
   * 获取移动速度修正
   */
  public int getMoveSpeedModifier(int entityId) {
    EntityStateList stateList = entityStates.get(entityId);
    if (stateList == null) {
      return 0;
    }

    int modifier = 0;
    for (StateEffect state : stateList.states) {
      // 假设 value1 用于存储速度修正
      if (state.stateId == StateId.FREEZE || state.stateId == StateId.COLD) {
        modifier -= state.value1;
      } else if (state.stateId == StateId.STAMINA || state.stateId == StateId.INCREASEDSPEED) {
        modifier += state.value1;
      }
    }
    return modifier;
  }

  //==========================================================================
  // 状态注册
  //==========================================================================

  /**
   * 注册默认状态定义
   */
  private void registerDefaultStates() {
    // 控制状态
    registerState(StateId.FREEZE, "Frozen", STATE_TYPE_CONTROL, 
        FLAG_DISABLE_MOVE | FLAG_DISABLE_ATTACK, 1, 0);
    registerState(StateId.COLD, "Chill", STATE_TYPE_DEBUFF, FLAG_REFRESHABLE, 1, 0);
    registerState(StateId.STUNNED, "Stun", STATE_TYPE_CONTROL, 
        FLAG_DISABLE_MOVE | FLAG_DISABLE_ATTACK | FLAG_DISABLE_SKILL, 1, 0);

    // 减益状态
    registerState(StateId.POISON, "Poison", STATE_TYPE_DEBUFF, FLAG_REFRESHABLE, 1, 0);
    registerState(StateId.WEAKEN, "Weaken", STATE_TYPE_DEBUFF, FLAG_REFRESHABLE, 1, 0);
    registerState(StateId.AMPLIFYDAMAGE, "Amplify Damage", STATE_TYPE_DEBUFF, FLAG_REFRESHABLE, 1, 0);
    registerState(StateId.LOWERRESIST, "Lower Resist", STATE_TYPE_DEBUFF, FLAG_REFRESHABLE, 1, 0);
    registerState(StateId.DECREPIFY, "Decrepify", STATE_TYPE_DEBUFF, 
        FLAG_REFRESHABLE | FLAG_DISABLE_MOVE, 1, 0);
    registerState(StateId.TERROR, "Terror", STATE_TYPE_CONTROL, FLAG_DISABLE_ATTACK, 1, 0);
    registerState(StateId.CONFUSE, "Confuse", STATE_TYPE_CONTROL, 0, 1, 0);

    // 增益状态
    registerState(StateId.MIGHT, "Might", STATE_TYPE_AURA, FLAG_HIDDEN, 1, 0);
    registerState(StateId.HOLYFIRE, "Holy Fire", STATE_TYPE_AURA, FLAG_HIDDEN, 1, 0);
    registerState(StateId.CONCENTRATION, "Concentration", STATE_TYPE_AURA, FLAG_HIDDEN, 1, 0);
    registerState(StateId.FANATICISM, "Fanaticism", STATE_TYPE_AURA, FLAG_HIDDEN, 1, 0);
    registerState(StateId.CONVICTION, "Conviction", STATE_TYPE_AURA, FLAG_HIDDEN, 1, 0);
    registerState(StateId.STAMINA, "Stamina", STATE_TYPE_AURA, FLAG_HIDDEN, 1, 0);
    registerState(StateId.MEDITATION, "Meditation", STATE_TYPE_AURA, FLAG_HIDDEN, 1, 0);
    registerState(StateId.REDEMPTION, "Redemption", STATE_TYPE_AURA, FLAG_HIDDEN, 1, 0);
    registerState(StateId.BATTLEORDERS, "Battle Orders", STATE_TYPE_BUFF, FLAG_REFRESHABLE, 1, 0);
    registerState(StateId.SHOUT, "Shout", STATE_TYPE_BUFF, FLAG_REFRESHABLE, 1, 0);
    registerState(StateId.OAKSAGE, "Oak Sage", STATE_TYPE_BUFF, FLAG_REFRESHABLE, 1, 0);
    registerState(StateId.CYCLONEARMOR, "Cyclone Armor", STATE_TYPE_BUFF, FLAG_REFRESHABLE, 1, 0);
    registerState(StateId.BONEARMOR, "Bone Armor", STATE_TYPE_BUFF, FLAG_REFRESHABLE, 1, 0);

    // 变身状态
    registerState(StateId.WOLF, "Werewolf", STATE_TYPE_TRANSFORM, FLAG_UNDISPELLABLE, 1, 0);
    registerState(StateId.BEAR, "Werebear", STATE_TYPE_TRANSFORM, FLAG_UNDISPELLABLE, 1, 0);

    log.debug("Registered {} default states", stateDefinitions.size);
  }

  private void registerState(int stateId, String name, int type, int flags, int maxStacks, int overlayId) {
    StateDefinition def = new StateDefinition();
    def.stateId = stateId;
    def.stateName = name;
    def.stateType = type;
    def.defaultFlags = flags;
    def.maxStacks = maxStacks;
    def.overlayId = overlayId;
    def.isTransform = (type == STATE_TYPE_TRANSFORM);
    def.isCurse = (type == STATE_TYPE_DEBUFF);
    def.isAura = (type == STATE_TYPE_AURA);
    stateDefinitions.put(stateId, def);
  }

  //==========================================================================
  // 辅助方法
  //==========================================================================

  private EntityStateList getOrCreateStateList(int entityId) {
    EntityStateList stateList = entityStates.get(entityId);
    if (stateList == null) {
      stateList = new EntityStateList(entityId);
      entityStates.put(entityId, stateList);
    }
    return stateList;
  }

  /**
   * 清除实体的所有状态
   */
  public void clearAllStates(int entityId) {
    EntityStateList stateList = entityStates.remove(entityId);
    if (stateList != null && callback != null) {
      for (StateEffect state : stateList.states) {
        callback.onStateRemoved(entityId, state);
        if (state.overlayId > 0) {
          callback.onHideOverlay(entityId, state.overlayId);
        }
      }
    }
  }

  public void setCallback(StateCallback callback) {
    this.callback = callback;
  }
}
