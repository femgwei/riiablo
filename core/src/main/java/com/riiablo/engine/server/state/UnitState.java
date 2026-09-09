package com.riiablo.engine.server.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.riiablo.attributes.NativeStatResolver;
import com.riiablo.attributes.Stat;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 单位状态实例 - 表示作用在单位上的一个状态效果
 * 
 * <p>该类存储状态的运行时数据，包括：
 * <ul>
 *   <li>状态ID和等级</li>
 *   <li>剩余持续时间</li>
 *   <li>状态修正值（如伤害、抗性变化）</li>
 *   <li>状态来源（施法者ID）</li>
 * </ul>
 * 
 * <p>参考：D2MOD 的状态链表结构
 * 
 * @author riiablo team
 */
public class UnitState {
  private static final Logger log = LogManager.getLogger(UnitState.class);

  //==========================================================================
  // 状态属性
  //==========================================================================

  /** 状态ID（对应 StateId 中的常量） */
  public int stateId;
  
  /** 状态等级（影响效果强度） */
  public int level;
  
  /** 剩余持续时间（帧数，0 表示永久） */
  public int duration;
  
  /** 初始持续时间（用于计算百分比） */
  public int initialDuration;
  
  /** 施法者实体ID（-1 表示无来源） */
  public int sourceEntityId = -1;
  
  /** 技能ID（如果状态由技能产生） */
  public int skillId = -1;

  /** Native curse metadata used for group arbitration and strength ordering. */
  public int curseGroup;
  public int curseStrength;

  /** D2StatList BASIC/PERMANENT category; not removed by death cleanup. */
  public boolean basicStatList;

  /**
   * One native stat-list entry owned by this state layer. D2MOO stores these
   * entries on the allocated {@code D2StatListStrc}; the state/source/skill
   * identity belongs to the list, not to the unit's already-folded totals.
   */
  public static final class StatContribution {
    public final int statId;
    public final int layer;
    public NativeStatResolver.Operation operation;
    public int encodedValue;

    private StatContribution(int statId, int layer,
        NativeStatResolver.Operation operation, int encodedValue) {
      this.statId = statId;
      this.layer = layer;
      this.operation = operation;
      this.encodedValue = encodedValue;
    }

    private StatContribution(StatContribution other) {
      this(other.statId, other.layer, other.operation, other.encodedValue);
    }
  }

  /** Exact stat deltas carried by this state; retained across refreshes and removable as one unit. */
  private final ArrayList<StatContribution> statContributions = new ArrayList<>(8);
  /* Last values emitted by the compatibility projection. Direct writes by
   * legacy skills are detected against these baselines on the next read. */
  private int projectedDamageModifier;
  private int projectedDefenseModifier;
  private int projectedAttackModifier;
  private int projectedVelocityModifier;
  private int projectedAnimationRateModifier;
  private int projectedFireResistModifier;
  private int projectedColdResistModifier;
  private int projectedLightResistModifier;
  private int projectedPoisonResistModifier;
  private int projectedMagicResistModifier;

  //==========================================================================
  // 状态效果修正值
  //==========================================================================

  /** 伤害修正（百分比，100 = 100%） */
  public int damageModifier;
  
  /** 防御修正（百分比） */
  public int defenseModifier;
  
  /** 攻击修正（百分比） */
  public int attackModifier;
  
  /** 移动速度修正（百分比） */
  public int velocityModifier;

  /** Generic state runtime scalar, e.g. native STAT_SKILL_FRENZY stacks. */
  public int runtimeValue;

  /** Native attackrate/other_animrate percentage used by animation stepping. */
  public int animationRateModifier;

  /** Native lifedrainmindam/lifedrainmaxdam supplied by a skill stat-list. */
  public int lifeLeechModifier;

  /** Native stunlength supplied by an attacker state, measured in frames. */
  public int stunLength;
  
  /** 火焰抗性修正 */
  public int fireResistModifier;
  
  /** 冰冷抗性修正 */
  public int coldResistModifier;
  
  /** 闪电抗性修正 */
  public int lightResistModifier;
  
  /** 毒素抗性修正 */
  public int poisonResistModifier;
  
  /** 魔法抗性修正 */
  public int magicResistModifier;

  /** 所有技能等级修正。 */
  public int skillModifier;

  /** 获得经验值修正（百分比）。 */
  public int experienceModifier;

  /** 法力恢复修正（百分比）。 */
  public int manaRecoveryModifier;

  /** 最大体力修正。 */
  public int maxStaminaModifier;

  /** 最大生命百分比修正（Battle Orders 的 item_maxhp_percent）。 */
  public int maxLifeModifier;

  /** 最大法力百分比修正（Battle Orders 的 item_maxmana_percent）。 */
  public int maxManaModifier;

  /** 体力恢复修正（百分比）。 */
  public int staminaRecoveryModifier;

  /** ItemTypes.txt layer used by native passive weapon mastery stats. */
  public String masteryItemType;
  /** True for passive_mastery_throw_*; false for passive_mastery_melee_*. */
  public boolean throwingMastery;
  public int masteryAttackRatingModifier;
  public int masteryDamageModifier;
  public int masteryCriticalChance;

  //==========================================================================
  // 持续伤害数据
  //==========================================================================

  /** 每帧伤害（用于 DOT 效果） */
  public int damagePerFrame;
  /** Fractional authoritative DOT rate; native poison uses 8.8 fixed units. */
  public float exactDamagePerFrame;
  
  /** 伤害类型（0=物理, 1=火焰, 2=闪电, 3=冰冷, 4=毒素, 5=魔法） */
  public int damageType;

  /** Skill stat-list poison damage in native per-frame units. */
  public int poisonMinDamage;
  public int poisonMaxDamage;
  /** Positive values replace the combined item poison length (Venom = 10). */
  public int poisonLengthOverride;

  /** Native periodic-skill cadence; -1 means waiting for the cast keyframe. */
  public int periodicDelayFrames;
  public int periodicCountdownFrames;

  //==========================================================================
  // 状态标志
  //==========================================================================

  /** 是否是新应用的状态 */
  public boolean isNew = true;
  
  /** 是否需要发送更新 */
  public boolean needsSync;
  
  /** 是否已过期（待移除） */
  public boolean expired;

  //==========================================================================
  // 构造函数
  //==========================================================================

  /**
   * 创建一个新的状态实例
   */
  public UnitState() {
    reset();
  }

  /**
   * 创建一个指定ID的状态实例
   * 
   * @param stateId 状态ID
   */
  public UnitState(int stateId) {
    this();
    this.stateId = stateId;
  }

  /**
   * 创建一个指定ID和持续时间的状态实例
   * 
   * @param stateId 状态ID
   * @param duration 持续时间（帧数）
   */
  public UnitState(int stateId, int duration) {
    this(stateId);
    this.duration = duration;
    this.initialDuration = duration;
  }

  //==========================================================================
  // 状态操作
  //==========================================================================

  /**
   * 重置状态到初始值
   */
  public void reset() {
    stateId = StateId.NONE;
    level = 1;
    duration = 0;
    initialDuration = 0;
    sourceEntityId = -1;
    skillId = -1;
    curseGroup = 0;
    curseStrength = 0;
    basicStatList = false;

    clearModifiers();

    damagePerFrame = 0;
    exactDamagePerFrame = 0f;
    damageType = 0;
    poisonMinDamage = 0;
    poisonMaxDamage = 0;
    poisonLengthOverride = 0;
    periodicDelayFrames = 0;
    periodicCountdownFrames = -1;

    isNew = true;
    needsSync = false;
    expired = false;
  }

  /** Clears stat-list values while retaining state identity and lifetime. */
  public void clearModifiers() {
    statContributions.clear();
    damageModifier = 0;
    defenseModifier = 0;
    attackModifier = 0;
    velocityModifier = 0;
    runtimeValue = 0;
    animationRateModifier = 0;
    lifeLeechModifier = 0;
    stunLength = 0;
    fireResistModifier = 0;
    coldResistModifier = 0;
    lightResistModifier = 0;
    poisonResistModifier = 0;
    magicResistModifier = 0;
    skillModifier = 0;
    experienceModifier = 0;
    manaRecoveryModifier = 0;
    maxStaminaModifier = 0;
    maxLifeModifier = 0;
    maxManaModifier = 0;
    staminaRecoveryModifier = 0;
    masteryItemType = null;
    throwingMastery = false;
    masteryAttackRatingModifier = 0;
    masteryDamageModifier = 0;
    masteryCriticalChance = 0;
    projectedDamageModifier = 0;
    projectedDefenseModifier = 0;
    projectedAttackModifier = 0;
    projectedVelocityModifier = 0;
    projectedAnimationRateModifier = 0;
    projectedFireResistModifier = 0;
    projectedColdResistModifier = 0;
    projectedLightResistModifier = 0;
    projectedPoisonResistModifier = 0;
    projectedMagicResistModifier = 0;
  }

  /**
   * Sets one native stat/layer entry. Re-setting the same key replaces the old
   * value, matching {@code STATLIST_SetStat}; use {@link #addStatContribution}
   * for {@code STATLIST_AddStat} behavior.
   */
  public void setStatContribution(int statId, int layer,
      NativeStatResolver.Operation operation, int encodedValue) {
    NativeStatResolver.Operation resolvedOperation = operation == null
        ? NativeStatResolver.Operation.ADD : operation;
    StatContribution contribution = findStatContribution(statId, layer);
    if (encodedValue == 0) {
      if (contribution != null) statContributions.remove(contribution);
      projectLegacyModifier(statId);
      return;
    }
    if (contribution == null) {
      contribution = new StatContribution(statId, layer, resolvedOperation, encodedValue);
      int index = 0;
      while (index < statContributions.size()) {
        StatContribution current = statContributions.get(index);
        if (current.statId > statId || (current.statId == statId && current.layer > layer)) break;
        index++;
      }
      statContributions.add(index, contribution);
    } else {
      contribution.operation = resolvedOperation;
      contribution.encodedValue = encodedValue;
    }
    projectLegacyModifier(statId);
  }

  /** Adds to one native stat/layer entry and removes the zero entry, like D2MOO. */
  public void addStatContribution(int statId, int layer,
      NativeStatResolver.Operation operation, int encodedDelta) {
    StatContribution contribution = findStatContribution(statId, layer);
    if (contribution == null) {
      if (encodedDelta == 0) return;
      setStatContribution(statId, layer, operation, encodedDelta);
      return;
    }
    contribution.encodedValue += encodedDelta;
    if (contribution.encodedValue == 0) statContributions.remove(contribution);
    projectLegacyModifier(statId);
  }

  /** Writes a compatibility scalar and its native source-owned stat layer together. */
  public void setNativeModifier(int statId, int value) {
    setStatContribution(statId, 0, NativeStatResolver.Operation.ADD, value);
    switch (statId) {
      case Stat.damagepercent: damageModifier = value; break;
      case Stat.skill_armor_percent:
      case Stat.item_armor_percent:
      case Stat.armorclass: defenseModifier = value; break;
      case Stat.tohit:
      case Stat.item_tohit_percent: attackModifier = value; break;
      case Stat.velocitypercent: velocityModifier = value; break;
      case Stat.attackrate:
      case Stat.other_animrate: animationRateModifier = value; break;
      case Stat.fireresist: fireResistModifier = value; break;
      case Stat.coldresist: coldResistModifier = value; break;
      case Stat.lightresist: lightResistModifier = value; break;
      case Stat.poisonresist: poisonResistModifier = value; break;
      case Stat.magicresist: magicResistModifier = value; break;
      case Stat.item_allskills: skillModifier = value; break;
      case Stat.item_addexperience: experienceModifier = value; break;
      case Stat.item_maxhp_percent: maxLifeModifier = value; break;
      case Stat.item_maxmana_percent: maxManaModifier = value; break;
      case Stat.skill_staminapercent:
      case Stat.skill_passive_staminapercent: maxStaminaModifier = value; break;
      case Stat.lifedrainmindam:
      case Stat.lifedrainmaxdam: lifeLeechModifier = value; break;
      case Stat.stunlength: stunLength = value; break;
      case Stat.manarecoverybonus: manaRecoveryModifier = value; break;
      case Stat.staminarecoverybonus: staminaRecoveryModifier = value; break;
      default: break;
    }
  }

  public int getStatContributionValue(int statId) {
    int value = 0;
    for (StatContribution contribution : statContributions) {
      if (contribution.statId == statId) value += contribution.encodedValue;
    }
    return value;
  }

  public boolean hasStatContribution(int statId) {
    for (StatContribution contribution : statContributions) {
      if (contribution.statId == statId) return true;
    }
    return false;
  }

  /** Read-only diagnostic projection in stable stat/layer insertion order. */
  public List<StatContribution> getStatContributions() {
    return Collections.unmodifiableList(statContributions);
  }

  public int resolvedDamageModifier() {
    syncLegacyModifiers();
    return hasStatContribution(Stat.damagepercent)
        ? getStatContributionValue(Stat.damagepercent) : damageModifier;
  }

  public int resolvedDefenseModifier() {
    syncLegacyModifiers();
    return hasAnyStatContribution(Stat.item_armor_percent, Stat.skill_armor_percent, Stat.armorclass)
        ? sumStatContributions(Stat.item_armor_percent, Stat.skill_armor_percent, Stat.armorclass)
        : defenseModifier;
  }

  public int resolvedAttackModifier() {
    syncLegacyModifiers();
    return hasAnyStatContribution(Stat.item_tohit_percent, Stat.tohit)
        ? sumStatContributions(Stat.item_tohit_percent, Stat.tohit)
        : attackModifier;
  }

  public int resolvedVelocityModifier() {
    syncLegacyModifiers();
    return hasStatContribution(Stat.velocitypercent)
        ? getStatContributionValue(Stat.velocitypercent) : velocityModifier;
  }

  public int resolvedAnimationRateModifier() {
    syncLegacyModifiers();
    if (hasStatContribution(Stat.attackrate)) {
      return getStatContributionValue(Stat.attackrate);
    }
    if (hasStatContribution(Stat.other_animrate)) {
      return getStatContributionValue(Stat.other_animrate);
    }
    return animationRateModifier;
  }

  public int resolvedResistModifier(int resistType) {
    syncLegacyModifiers();
    final int statId;
    final int legacyValue;
    switch (resistType) {
      case 0: statId = Stat.fireresist; legacyValue = fireResistModifier; break;
      case 1: statId = Stat.coldresist; legacyValue = coldResistModifier; break;
      case 2: statId = Stat.lightresist; legacyValue = lightResistModifier; break;
      case 3: statId = Stat.poisonresist; legacyValue = poisonResistModifier; break;
      case 4: statId = Stat.magicresist; legacyValue = magicResistModifier; break;
      default: return 0;
    }
    return hasStatContribution(statId) ? getStatContributionValue(statId) : legacyValue;
  }

  private StatContribution findStatContribution(int statId, int layer) {
    for (StatContribution contribution : statContributions) {
      if (contribution.statId == statId && contribution.layer == layer) return contribution;
    }
    return null;
  }

  private boolean hasAnyStatContribution(int... statIds) {
    for (int statId : statIds) if (hasStatContribution(statId)) return true;
    return false;
  }

  private int sumStatContributions(int... statIds) {
    int value = 0;
    for (int statId : statIds) value += getStatContributionValue(statId);
    return value;
  }

  /** Keeps old serializers and un-migrated consumers working during the staged conversion. */
  private void projectLegacyModifier(int statId) {
    switch (statId) {
      case Stat.damagepercent:
        damageModifier = getStatContributionValue(Stat.damagepercent);
        projectedDamageModifier = damageModifier;
        break;
      case Stat.item_armor_percent:
      case Stat.skill_armor_percent:
      case Stat.armorclass:
        defenseModifier = sumStatContributions(
            Stat.item_armor_percent, Stat.skill_armor_percent, Stat.armorclass);
        projectedDefenseModifier = defenseModifier;
        break;
      case Stat.item_tohit_percent:
      case Stat.tohit:
        attackModifier = sumStatContributions(
            Stat.item_tohit_percent, Stat.tohit);
        projectedAttackModifier = attackModifier;
        break;
      case Stat.attackrate:
      case Stat.other_animrate:
        animationRateModifier = hasStatContribution(Stat.attackrate)
            ? getStatContributionValue(Stat.attackrate)
            : getStatContributionValue(Stat.other_animrate);
        projectedAnimationRateModifier = animationRateModifier;
        break;
      case Stat.velocitypercent:
        velocityModifier = getStatContributionValue(Stat.velocitypercent);
        projectedVelocityModifier = velocityModifier;
        break;
      case Stat.fireresist:
        fireResistModifier = getStatContributionValue(Stat.fireresist);
        projectedFireResistModifier = fireResistModifier;
        break;
      case Stat.coldresist:
        coldResistModifier = getStatContributionValue(Stat.coldresist);
        projectedColdResistModifier = coldResistModifier;
        break;
      case Stat.lightresist:
        lightResistModifier = getStatContributionValue(Stat.lightresist);
        projectedLightResistModifier = lightResistModifier;
        break;
      case Stat.poisonresist:
        poisonResistModifier = getStatContributionValue(Stat.poisonresist);
        projectedPoisonResistModifier = poisonResistModifier;
        break;
      case Stat.magicresist:
        magicResistModifier = getStatContributionValue(Stat.magicresist);
        projectedMagicResistModifier = magicResistModifier;
        break;
      default:
        break;
    }
  }

  /**
   * Imports direct writes made by pre-stat-list skills. Once a caller is
   * migrated it writes the native entry and this method is a no-op. For a
   * legacy caller, a changed scalar replaces only that category's canonical
   * layer, preserving the D2MOO source-owned representation for all other
   * categories.
   */
  private void syncLegacyModifiers() {
    if (damageModifier != projectedDamageModifier) {
      replaceLegacyCategory(Stat.damagepercent, damageModifier);
      projectedDamageModifier = damageModifier;
    }
    if (defenseModifier != projectedDefenseModifier) {
      replaceLegacyCategory(Stat.item_armor_percent, defenseModifier,
          Stat.skill_armor_percent, Stat.armorclass);
      projectedDefenseModifier = defenseModifier;
    }
    if (attackModifier != projectedAttackModifier) {
      replaceLegacyCategory(Stat.item_tohit_percent, attackModifier,
          Stat.tohit);
      projectedAttackModifier = attackModifier;
    }
    if (velocityModifier != projectedVelocityModifier) {
      replaceLegacyCategory(Stat.velocitypercent, velocityModifier);
      projectedVelocityModifier = velocityModifier;
    }
    if (animationRateModifier != projectedAnimationRateModifier) {
      replaceLegacyCategory(Stat.attackrate, animationRateModifier, Stat.other_animrate);
      projectedAnimationRateModifier = animationRateModifier;
    }
    if (fireResistModifier != projectedFireResistModifier) {
      replaceLegacyCategory(Stat.fireresist, fireResistModifier);
      projectedFireResistModifier = fireResistModifier;
    }
    if (coldResistModifier != projectedColdResistModifier) {
      replaceLegacyCategory(Stat.coldresist, coldResistModifier);
      projectedColdResistModifier = coldResistModifier;
    }
    if (lightResistModifier != projectedLightResistModifier) {
      replaceLegacyCategory(Stat.lightresist, lightResistModifier);
      projectedLightResistModifier = lightResistModifier;
    }
    if (poisonResistModifier != projectedPoisonResistModifier) {
      replaceLegacyCategory(Stat.poisonresist, poisonResistModifier);
      projectedPoisonResistModifier = poisonResistModifier;
    }
    if (magicResistModifier != projectedMagicResistModifier) {
      replaceLegacyCategory(Stat.magicresist, magicResistModifier);
      projectedMagicResistModifier = magicResistModifier;
    }
  }

  private void replaceLegacyCategory(int canonicalStat, int value, int... aliases) {
    for (int statId : aliases) removeStatContributions(statId);
    setStatContribution(canonicalStat, 0, NativeStatResolver.Operation.ADD, value);
  }

  private void removeStatContributions(int statId) {
    for (int i = statContributions.size() - 1; i >= 0; i--) {
      if (statContributions.get(i).statId == statId) statContributions.remove(i);
    }
  }

  /**
   * 更新状态（每帧调用）
   * 
   * @return true 如果状态仍然有效，false 如果已过期
   */
  public boolean update() {
    if (expired) {
      return false;
    }
    
    // 永久状态（duration == 0）不会过期
    if (duration > 0) {
      duration--;
      if (duration <= 0) {
        expired = true;
        log.debug("状态 {} 已过期", StateId.getName(stateId));
        return false;
      }
    }
    
    isNew = false;
    return true;
  }

  /**
   * 刷新状态持续时间
   * 
   * @param newDuration 新的持续时间
   */
  public void refresh(int newDuration) {
    if (newDuration > duration) {
      duration = newDuration;
      initialDuration = newDuration;
      needsSync = true;
      log.debug("状态 {} 刷新持续时间为 {} 帧", StateId.getName(stateId), newDuration);
    }
  }

  /**
   * 增强状态等级
   * 
   * @param newLevel 新等级
   */
  public void enhance(int newLevel) {
    if (newLevel > level) {
      level = newLevel;
      needsSync = true;
      log.debug("状态 {} 增强到等级 {}", StateId.getName(stateId), newLevel);
    }
  }

  //==========================================================================
  // 状态查询
  //==========================================================================

  /**
   * 检查状态是否有效
   * 
   * @return true 如果状态有效
   */
  public boolean isValid() {
    return stateId != StateId.NONE && !expired;
  }

  /**
   * 检查是否是永久状态
   * 
   * @return true 如果是永久状态
   */
  public boolean isPermanent() {
    return duration == 0 && !expired;
  }

  /**
   * 获取剩余持续时间百分比
   * 
   * @return 0.0-1.0 之间的值
   */
  public float getDurationPercent() {
    if (initialDuration <= 0) {
      return 1.0f;
    }
    return (float) duration / initialDuration;
  }

  /**
   * 检查状态是否有来源
   * 
   * @return true 如果有来源
   */
  public boolean hasSource() {
    return sourceEntityId >= 0;
  }

  /**
   * 检查是否是诅咒状态
   * 
   * @return true 如果是诅咒
   */
  public boolean isCurse() {
    return StateId.isCurse(stateId);
  }

  /**
   * 检查是否是光环状态
   * 
   * @return true 如果是光环
   */
  public boolean isAura() {
    return StateId.isAura(stateId);
  }

  /**
   * 检查是否是变形状态
   * 
   * @return true 如果是变形
   */
  public boolean isTransform() {
    return StateId.isTransform(stateId);
  }

  //==========================================================================
  // 复制和克隆
  //==========================================================================

  /**
   * 从另一个状态复制数据
   * 
   * @param other 源状态
   */
  public void copyFrom(UnitState other) {
    this.stateId = other.stateId;
    this.level = other.level;
    this.duration = other.duration;
    this.initialDuration = other.initialDuration;
    this.sourceEntityId = other.sourceEntityId;
    this.skillId = other.skillId;
    this.curseGroup = other.curseGroup;
    this.curseStrength = other.curseStrength;
    this.basicStatList = other.basicStatList;
    
    this.damageModifier = other.damageModifier;
    this.defenseModifier = other.defenseModifier;
    this.attackModifier = other.attackModifier;
    this.velocityModifier = other.velocityModifier;
    this.runtimeValue = other.runtimeValue;
    this.animationRateModifier = other.animationRateModifier;
    this.lifeLeechModifier = other.lifeLeechModifier;
    this.stunLength = other.stunLength;
    this.fireResistModifier = other.fireResistModifier;
    this.coldResistModifier = other.coldResistModifier;
    this.lightResistModifier = other.lightResistModifier;
    this.poisonResistModifier = other.poisonResistModifier;
    this.magicResistModifier = other.magicResistModifier;
    this.skillModifier = other.skillModifier;
    this.experienceModifier = other.experienceModifier;
    this.manaRecoveryModifier = other.manaRecoveryModifier;
    this.maxStaminaModifier = other.maxStaminaModifier;
    this.maxLifeModifier = other.maxLifeModifier;
    this.maxManaModifier = other.maxManaModifier;
    this.staminaRecoveryModifier = other.staminaRecoveryModifier;
    this.masteryItemType = other.masteryItemType;
    this.throwingMastery = other.throwingMastery;
    this.masteryAttackRatingModifier = other.masteryAttackRatingModifier;
    this.masteryDamageModifier = other.masteryDamageModifier;
    this.masteryCriticalChance = other.masteryCriticalChance;
    this.statContributions.clear();
    for (StatContribution contribution : other.statContributions) {
      this.statContributions.add(new StatContribution(contribution));
    }
    this.projectedDamageModifier = other.projectedDamageModifier;
    this.projectedDefenseModifier = other.projectedDefenseModifier;
    this.projectedAttackModifier = other.projectedAttackModifier;
    this.projectedVelocityModifier = other.projectedVelocityModifier;
    this.projectedAnimationRateModifier = other.projectedAnimationRateModifier;
    this.projectedFireResistModifier = other.projectedFireResistModifier;
    this.projectedColdResistModifier = other.projectedColdResistModifier;
    this.projectedLightResistModifier = other.projectedLightResistModifier;
    this.projectedPoisonResistModifier = other.projectedPoisonResistModifier;
    this.projectedMagicResistModifier = other.projectedMagicResistModifier;
    
    this.damagePerFrame = other.damagePerFrame;
    this.exactDamagePerFrame = other.exactDamagePerFrame;
    this.damageType = other.damageType;
    this.poisonMinDamage = other.poisonMinDamage;
    this.poisonMaxDamage = other.poisonMaxDamage;
    this.poisonLengthOverride = other.poisonLengthOverride;
    this.periodicDelayFrames = other.periodicDelayFrames;
    this.periodicCountdownFrames = other.periodicCountdownFrames;
    
    this.isNew = other.isNew;
    this.needsSync = other.needsSync;
    this.expired = other.expired;
  }

  @Override
  public String toString() {
    return "UnitState{" +
        "stateId=" + StateId.getName(stateId) +
        ", level=" + level +
        ", duration=" + duration +
        ", expired=" + expired +
        '}';
  }
}
