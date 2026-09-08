package com.riiablo.engine.server.state;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;

import com.riiablo.codec.excel.States;
import com.riiablo.attributes.NativeStatResolver;
import com.riiablo.item.Item;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 状态列表 - 管理单位上的所有状态实例
 * 
 * <p>该类维护一个状态实例列表，提供添加、移除、查询和更新功能。
 * 使用对象池来减少 GC 压力。
 * 
 * <p>参考：D2MOD 的状态链表管理
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

  public enum DeathUnitType {
    PLAYER,
    MONSTER,
    BOSS
  }

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

    return createState(stateId, duration, level, sourceEntityId, -1);
  }

  /**
   * Adds or refreshes the stat-list layer identified by state, source and
   * skill. Unlike the legacy {@link #addState} bridge, this permits two
   * distinct owners to carry the same state without merging their deltas.
   */
  public UnitState addStateLayer(int stateId, int duration, int level,
      int sourceEntityId, int skillId) {
    if (!StateId.isValid(stateId)) {
      log.warn("尝试添加无效的状态ID: {}", stateId);
      return null;
    }
    UnitState existing = getStateLayer(stateId, sourceEntityId, skillId);
    if (existing != null) {
      existing.refresh(duration);
      existing.enhance(level);
      return existing;
    }
    return createState(stateId, duration, level, sourceEntityId, skillId);
  }

  /**
   * Applies a native D2MOO-style curse stat-list.  Curse layers are owned by
   * (source, skill, state), while States.txt group controls replacement of
   * different curses in the same group.  Layers of the same curse from
   * different sources remain independent so removing the stronger source can
   * reveal the weaker one again.
   *
   * @param table authoritative 1.10f States.txt projection (may be null)
   * @param stateId state to apply
   * @param duration duration in simulation frames
   * @param level skill level
   * @param sourceEntityId caster/owner
   * @param skillId originating skill
   * @param strength effective curse strength; zero derives from statValue/level
   * @param statId optional stat contribution, or {@code -1}
   * @param statValue encoded contribution value
   * @param operation stat operation (defaults to ADD)
   * @return the active layer, or {@code null} when rejected by native rules
   */
  public UnitState applyCurseState(States table, int stateId, int duration, int level,
      int sourceEntityId, int skillId, int strength, int statId, int statValue,
      NativeStatResolver.Operation operation) {
    States.Entry definition = table != null ? table.get(stateId) : null;
    if (!(definition != null && definition.curse) && !StateId.isCurse(stateId)) {
      log.warn("拒绝将非诅咒状态 {} 作为诅咒应用", StateId.getName(stateId));
      return null;
    }
    if (duration <= 0) return null;
    int resolvedStrength = strength != 0 ? Math.abs(strength)
        : (statValue != 0 ? Math.abs(statValue) : Math.max(1, level));
    int group = definition != null ? definition.group : 0;

    UnitState exact = getStateLayer(stateId, sourceEntityId, skillId);
    if (exact != null) {
      // A weaker re-application does not disturb the stronger stat-list.
      if (exact.curseStrength > resolvedStrength) return exact;
      exact.curseStrength = resolvedStrength;
      exact.curseGroup = group;
      exact.level = Math.max(exact.level, level);
      exact.sourceEntityId = sourceEntityId;
      exact.skillId = skillId;
      exact.duration = Math.max(exact.duration, duration);
      exact.initialDuration = exact.duration;
      if (statId >= 0) exact.setStatContribution(statId, 0, operation, statValue);
      exact.expired = false;
      exact.needsSync = true;
      return exact;
    }

    // Different curse IDs in one native group are mutually exclusive.  Keep
    // the strongest active layer; an incoming stronger layer replaces weaker
    // layers and their stat contributions atomically.
    if (group > 0) {
      for (int i = states.size - 1; i >= 0; i--) {
        UnitState current = states.get(i);
        if (current.stateId == stateId || current.curseGroup != group) continue;
        if (current.curseStrength >= resolvedStrength) return null;
      }
      for (int i = states.size - 1; i >= 0; i--) {
        UnitState current = states.get(i);
        if (current.stateId != stateId && current.curseGroup == group) {
          int removedId = current.stateId;
          states.removeIndex(i);
          statePool.free(current);
          refreshFlag(removedId);
        }
      }
    }

    UnitState applied = createState(stateId, duration, level, sourceEntityId, skillId);
    applied.curseGroup = group;
    applied.curseStrength = resolvedStrength;
    if (statId >= 0 && statValue != 0) {
      applied.setStatContribution(statId, 0, operation, statValue);
    }
    applied.needsSync = true;
    log.debug("应用诅咒状态 {} entity={} source={} skill={} group={} strength={} duration={}",
        StateId.getName(stateId), entityId, sourceEntityId, skillId, group,
        resolvedStrength, duration);
    return applied;
  }

  /** Convenience overload for curses without a stat contribution. */
  public UnitState applyCurseState(States table, int stateId, int duration, int level,
      int sourceEntityId, int skillId, int strength) {
    return applyCurseState(table, stateId, duration, level, sourceEntityId, skillId,
        strength, -1, 0, NativeStatResolver.Operation.ADD);
  }

  /** Removes only curable curses according to the native States.txt mask. */
  public int removeCurableCurses(States table) {
    int count = 0;
    for (int i = states.size - 1; i >= 0; i--) {
      UnitState state = states.get(i);
      States.Entry definition = table != null ? table.get(state.stateId) : null;
      boolean curse = definition != null ? definition.curse : state.isCurse();
      if (!curse || (definition != null && !definition.curable)) continue;
      int stateId = state.stateId;
      states.removeIndex(i);
      statePool.free(state);
      refreshFlag(stateId);
      count++;
    }
    return count;
  }

  private UnitState createState(int stateId, int duration, int level,
      int sourceEntityId, int skillId) {
    UnitState state = statePool.obtain();
    state.stateId = stateId;
    state.duration = duration;
    state.initialDuration = duration;
    state.level = level;
    state.sourceEntityId = sourceEntityId;
    state.skillId = skillId;
    if (StateId.isCurse(stateId)) state.curseStrength = Math.max(1, level);
    
    states.add(state);
    flags.set(stateId);
    
    log.debug("添加状态 {} 到实体 {}, 持续时间={}, 等级={}", 
        StateId.getName(stateId), entityId, duration, level);
    
    return state;
  }

  /** Extends an existing state expiry without changing its owner or payload. */
  public UnitState extendState(int stateId, int duration, int level, int sourceEntityId) {
    if (!StateId.isValid(stateId) || duration <= 0) return null;
    UnitState existing = getState(stateId);
    if (existing == null) return createState(stateId, duration, level, sourceEntityId, -1);
    if (duration > existing.duration) {
      existing.duration = duration;
      existing.initialDuration = duration;
      existing.needsSync = true;
    }
    return existing;
  }

  /**
   * Native poison/burning replacement: an equal-or-stronger per-frame rate
   * replaces owner, payload and expiry exactly; a weaker hit changes nothing.
   */
  public UnitState applyDamageOverTimeState(int stateId, int duration, int level,
      int sourceEntityId, float damagePerFrame, int damageType) {
    if ((stateId != StateId.POISON && stateId != StateId.BURNING)
        || duration <= 0 || damagePerFrame <= 0f) return null;
    UnitState state = getState(stateId);
    if (state != null) {
      float existingRate = state.exactDamagePerFrame > 0f
          ? state.exactDamagePerFrame : state.damagePerFrame;
      if (existingRate > damagePerFrame) return state;
    } else {
      state = createState(stateId, duration, level, sourceEntityId, -1);
    }
    state.duration = duration;
    state.initialDuration = duration;
    state.level = Math.max(1, level);
    state.sourceEntityId = sourceEntityId;
    state.damagePerFrame = (int) damagePerFrame;
    state.exactDamagePerFrame = damagePerFrame;
    state.damageType = damageType;
    state.expired = false;
    state.needsSync = true;
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
        refreshFlag(stateId);
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
        int stateId = state.stateId;
        states.removeIndex(i);
        statePool.free(state);
        refreshFlag(stateId);
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
   * D2Common {@code D2Common_10469}: on death, free every runtime stat-list
   * whose state is not marked to stay on this unit category. {@code noclear}
   * is intentionally not consulted here; D2MOO death cleanup uses only the
   * three stay-death masks.
   *
   * @return number of removed state-owned stat lists
   */
  public int retainForDeath(States table, DeathUnitType unitType) {
    int removed = 0;
    for (int i = states.size - 1; i >= 0; i--) {
      UnitState state = states.get(i);
      States.Entry entry = table != null ? table.get(state.stateId) : null;
      if (state.basicStatList || (entry != null && staysOnDeath(entry, unitType))) continue;
      int stateId = state.stateId;
      states.removeIndex(i);
      statePool.free(state);
      refreshFlag(stateId);
      removed++;
    }
    return removed;
  }

  /** Clears ordinary effects while preserving States.txt {@code noclear} entries. */
  public int clearRemovable(States table) {
    int removed = 0;
    for (int i = states.size - 1; i >= 0; i--) {
      UnitState state = states.get(i);
      States.Entry entry = table != null ? table.get(state.stateId) : null;
      if (state.basicStatList || (entry != null && entry.noClear)) continue;
      int stateId = state.stateId;
      states.removeIndex(i);
      statePool.free(state);
      refreshFlag(stateId);
      removed++;
    }
    return removed;
  }

  private static boolean staysOnDeath(States.Entry entry, DeathUnitType unitType) {
    if (entry == null || unitType == null) return false;
    switch (unitType) {
      case PLAYER: return entry.playerStayDeath;
      case BOSS: return entry.bossStayDeath;
      case MONSTER: return entry.monsterStayDeath;
      default: return false;
    }
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
        int stateId = state.stateId;
        states.removeIndex(i);
        statePool.free(state);
        refreshFlag(stateId);
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

  /** Returns one exact native stat-list layer rather than the first matching state. */
  public UnitState getStateLayer(int stateId, int sourceEntityId, int skillId) {
    if (!flags.check(stateId)) return null;
    for (UnitState state : states) {
      if (state.stateId == stateId && state.sourceEntityId == sourceEntityId
          && state.skillId == skillId) return state;
    }
    return null;
  }

  /** Removes only one source/skill-owned layer and its exact contributions. */
  public boolean removeStateLayer(int stateId, int sourceEntityId, int skillId) {
    for (int i = states.size - 1; i >= 0; i--) {
      UnitState state = states.get(i);
      if (state.stateId != stateId || state.sourceEntityId != sourceEntityId
          || state.skillId != skillId) continue;
      states.removeIndex(i);
      statePool.free(state);
      refreshFlag(stateId);
      return true;
    }
    return false;
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
        int stateId = state.stateId;
        states.removeIndex(i);
        statePool.free(state);
        refreshFlag(stateId);
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
      total += state.resolvedDamageModifier();
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
      total += state.resolvedDefenseModifier();
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
      // Frenzy is applied explicitly by StateUpdater so it can be composed
      // with slows before ordinary aura velocity modifiers.
      if (state.stateId == StateId.FRENZY || state.stateId == StateId.MONFRENZY
          || (state.stateId >= StateId.PROGRESSIVE_DAMAGE
              && state.stateId <= StateId.PROGRESSIVE_LIGHTNING)) {
        continue;
      }
      total += state.resolvedVelocityModifier();
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
      total += state.resolvedResistModifier(resistType);
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
   * Calculates the aggregate attack-rating modifier supplied by active
   * states (percentage points).
   */
  public int getTotalAttackModifier() {
    int total = 0;
    for (UnitState state : states) {
      total += state.resolvedAttackModifier();
    }
    return total;
  }

  public int getTotalAnimationRateModifier() {
    int total = 0;
    for (UnitState state : states) total += state.resolvedAnimationRateModifier();
    return Math.max(-90, Math.min(200, total));
  }

  public int getTotalLifeLeechModifier() {
    int total = 0;
    for (UnitState state : states) total += state.lifeLeechModifier;
    return Math.max(0, total);
  }

  public int getTotalStunLength() {
    int maximum = 0;
    for (UnitState state : states) maximum = Math.max(maximum, state.stunLength);
    return maximum;
  }

  public int getTotalSkillModifier() {
    int total = 0;
    for (UnitState state : states) total += state.skillModifier;
    return total;
  }

  public int getTotalExperienceModifier() {
    int total = 0;
    for (UnitState state : states) total += state.experienceModifier;
    return total;
  }

  public int getTotalMaxLifeModifier() {
    int total = 0;
    for (UnitState state : states) total += state.maxLifeModifier;
    return total;
  }

  public int getTotalMaxManaModifier() {
    int total = 0;
    for (UnitState state : states) total += state.maxManaModifier;
    return total;
  }

  public int getTotalMaxStaminaModifier() {
    int total = 0;
    for (UnitState state : states) total += state.maxStaminaModifier;
    return total;
  }

  /** Result of D2Common SKILLS_GetWeaponMasteryBonus for one attack weapon. */
  public static final class WeaponMasteryBonus {
    public int attackRatingPercent;
    public int damagePercent;
    public int criticalChance;

    public WeaponMasteryBonus clear() {
      attackRatingPercent = 0;
      damagePercent = 0;
      criticalChance = 0;
      return this;
    }

    public boolean isEmpty() {
      return attackRatingPercent == 0 && damagePercent == 0 && criticalChance == 0;
    }
  }

  /**
   * Selects the highest layered mastery value matching the actual attack
   * weapon. Native stat layers do not stack Sword/Axe/etc. mastery values.
   */
  public WeaponMasteryBonus getWeaponMastery(
      Item weapon, boolean throwingAttack, WeaponMasteryBonus out) {
    if (out == null) out = new WeaponMasteryBonus();
    out.clear();
    if (weapon == null || weapon.typeEntry == null) return out;
    for (UnitState state : states) {
      if (state.masteryItemType == null || state.masteryItemType.isEmpty()
          || state.throwingMastery != throwingAttack
          || !weapon.typeEntry.is(state.masteryItemType)) continue;
      out.attackRatingPercent = Math.max(
          out.attackRatingPercent, state.masteryAttackRatingModifier);
      out.damagePercent = Math.max(out.damagePercent, state.masteryDamageModifier);
      out.criticalChance = Math.max(out.criticalChance, state.masteryCriticalChance);
    }
    return out;
  }

  /**
   * Replaces the runtime state list with an authoritative network snapshot.
   * This is intentionally a small, allocation-free-at-steady-state bridge
   * for the client receiver; server-side callers should continue to use
   * {@link #addState(int, int, int, int)} so source and DOT metadata are kept.
   */
  public void replaceFromSnapshot(int[] stateIds, int[] durations, int[] levels) {
    clearAll();
    if (stateIds == null) return;
    int count = stateIds.length;
    for (int i = 0; i < count; i++) {
      int duration = durations != null && i < durations.length ? durations[i] : 0;
      int level = levels != null && i < levels.length ? levels[i] : 1;
      addState(stateIds[i], duration, level, -1);
    }
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

  private void refreshFlag(int stateId) {
    for (UnitState state : states) {
      if (state.stateId == stateId) return;
    }
    flags.clear(stateId);
  }

  @Override
  public String toString() {
    return "StateList{entityId=" + entityId + ", states=" + states.size + ", " + flags + "}";
  }
}
