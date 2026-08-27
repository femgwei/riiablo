package com.riiablo.engine.server.skill;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;

import com.riiablo.engine.server.state.StateId;
import com.riiablo.attributes.Stat;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 光环管理器 - 基于 D2MOD SkillPal.cpp 移植
 * 
 * <p>管理圣骑士光环及其他光环效果：
 * <ul>
 *   <li>光环激活和切换</li>
 *   <li>光环范围内目标应用</li>
 *   <li>光环叠加规则（同类不叠加，取最高值）</li>
 *   <li>光环属性计算</li>
 *   <li>光环持续消耗</li>
 * </ul>
 * 
 * <p>参考：D2MOD/source/D2Game/src/SKILLS/SkillPal.cpp
 * 
 * @author riiablo team
 */
public class AuraManager {
  private static final Logger log = LogManager.getLogger(AuraManager.class);

  //==========================================================================
  // 常量 - 光环类型
  //==========================================================================

  /** 增益光环（对友方有效） */
  public static final int AURA_TYPE_BUFF = 0;

  /** 减益光环（对敌方有效） */
  public static final int AURA_TYPE_DEBUFF = 1;

  /** 伤害光环（对敌方造成伤害） */
  public static final int AURA_TYPE_DAMAGE = 2;

  //==========================================================================
  // 常量 - 光环互斥组
  //==========================================================================

  /** 无互斥组 */
  public static final int EXCLUSIVE_NONE = 0;

  /** 防御类光环（反抗、抵抗、净化等） */
  public static final int EXCLUSIVE_DEFENSE = 1;

  /** 攻击类光环（力量、狂热等） */
  public static final int EXCLUSIVE_OFFENSE = 2;

  /** 减益类光环（定罪等） */
  public static final int EXCLUSIVE_CURSE = 3;

  //==========================================================================
  // 常量 - 最大光环属性数
  //==========================================================================

  /** 每个光环最多 6 个属性加成 */
  public static final int MAX_AURA_STATS = 6;

  //==========================================================================
  // 内部类 - 光环数据
  //==========================================================================

  /**
   * 光环定义
   */
  public static class AuraDefinition {
    /** 光环技能 ID */
    public int skillId;

    /** 光环名称 */
    public String name;

    /** 光环类型 */
    public int auraType;

    /** 关联的状态 ID */
    public int stateId;

    /** 互斥组 */
    public int exclusiveGroup;

    /** 基础范围（像素） */
    public float baseRange;

    /** 每级范围增加 */
    public float rangePerLevel;

    /** 法力消耗（每秒） */
    public float manaCostPerSecond;

    /** 属性 ID 数组（最多 6 个） */
    public int[] statIds = new int[MAX_AURA_STATS];

    /** 基础属性值 */
    public int[] baseStatValues = new int[MAX_AURA_STATS];

    /** 每级属性增加值 */
    public int[] statPerLevel = new int[MAX_AURA_STATS];

    /** 是否影响自身 */
    public boolean affectsSelf;

    /** 是否影响队友 */
    public boolean affectsParty;

    /** 是否影响雇佣兵 */
    public boolean affectsMercenary;

    /** 是否影响敌人 */
    public boolean affectsEnemy;
  }

  /**
   * 活跃的光环实例
   */
  public static class ActiveAura {
    /** 光环定义 */
    public AuraDefinition definition;

    /** 光环施放者实体 ID */
    public int casterId;

    /** 当前技能等级 */
    public int skillLevel;

    /** 当前范围 */
    public float range;

    /** 当前属性值 */
    public int[] statValues = new int[MAX_AURA_STATS];

    /** 受影响的实体列表 */
    public Array<Integer> affectedEntities = new Array<>();

    /** 上次更新时间 */
    public float lastUpdateTime;

    /** Fractional per-second mana cost carried between simulation ticks. */
    public float manaCostAccumulator;

    /** 是否激活 */
    public boolean active;
  }

  /**
   * 实体受到的光环效果
   */
  public static class AuraEffect {
    /** 光环技能 ID */
    public int skillId;

    /** 施放者实体 ID */
    public int casterId;

    /** 效果值 */
    public int[] statValues = new int[MAX_AURA_STATS];

    /** 剩余持续时间 */
    public float duration;
  }

  //==========================================================================
  // 回调接口
  //==========================================================================

  /**
   * 光环事件回调
   */
  public interface AuraCallback {
    /**
     * 光环激活
     */
    void onAuraActivated(int casterId, int skillId, int skillLevel);

    /**
     * 光环关闭
     */
    void onAuraDeactivated(int casterId, int skillId);

    /**
     * 实体进入光环范围
     */
    void onEntityEnterAura(int entityId, int casterId, int skillId, int[] statValues);

    /**
     * 实体离开光环范围
     */
    void onEntityLeaveAura(int entityId, int casterId, int skillId);

    /**
     * 获取实体位置
     */
    float[] getEntityPosition(int entityId);

    /**
     * 获取范围内的实体
     */
    Array<Integer> getEntitiesInRange(float x, float y, float range);

    /**
     * 检查实体关系（友方/敌方）
     */
    boolean isAlly(int entityId1, int entityId2);

    /**
     * 消耗法力
     */
    boolean consumeMana(int casterId, int amount);

    /**
     * 应用状态效果
     */
    void applyState(int targetId, int stateId, int duration,
        int[] statIds, int[] statValues);

    /**
     * 移除状态效果
     */
    void removeState(int targetId, int stateId);
  }

  //==========================================================================
  // 字段
  //==========================================================================

  /** 光环定义表 */
  private final IntMap<AuraDefinition> auraDefinitions = new IntMap<>();

  /** 活跃光环（施放者ID -> 光环实例） */
  private final IntMap<ActiveAura> activeAuras = new IntMap<>();

  /** 实体受到的光环效果（实体ID -> 光环效果列表） */
  private final IntMap<Array<AuraEffect>> entityAuraEffects = new IntMap<>();

  /** 回调 */
  private AuraCallback callback;

  /** 游戏时间 */
  private float gameTime;

  //==========================================================================
  // 构造函数
  //==========================================================================

  public AuraManager() {
    registerDefaultAuras();
  }

  //==========================================================================
  // 核心方法 - 光环激活/关闭
  //==========================================================================

  /**
   * 激活光环
   * 
   * @param casterId 施放者实体 ID
   * @param skillId 技能 ID
   * @param skillLevel 技能等级
   * @return true 如果成功激活
   */
  public boolean activateAura(int casterId, int skillId, int skillLevel) {
    AuraDefinition def = auraDefinitions.get(skillId);
    if (def == null) {
      log.debug("Aura {} not found", skillId);
      return false;
    }

    // 检查是否已有光环激活（同一实体只能激活一个主动光环）
    ActiveAura existing = activeAuras.get(casterId);
    if (existing != null) {
      // 检查是否是同一个光环
      if (existing.definition.skillId == skillId) {
        // Selecting the already-active aura is idempotent. This is important
        // for retransmitted network requests and matches the action-bar
        // semantics: choosing a skill does not toggle it off.
        if (existing.skillLevel == skillLevel) return true;
        // A learned-level change must replace already-applied values on every
        // target; rebuilding the aura provides that clean refresh.
        deactivateAura(casterId);
      }

      // 检查互斥组
      else if (existing.definition.exclusiveGroup != EXCLUSIVE_NONE &&
          existing.definition.exclusiveGroup == def.exclusiveGroup) {
        // 替换光环
        deactivateAura(casterId);
      } else {
        // 不同互斥组，关闭旧光环
        deactivateAura(casterId);
      }
    }

    // 创建新的活跃光环
    ActiveAura aura = new ActiveAura();
    aura.definition = def;
    aura.casterId = casterId;
    aura.skillLevel = skillLevel;
    aura.range = calculateAuraRange(def, skillLevel);
    aura.active = true;
    aura.lastUpdateTime = gameTime;

    // 计算属性值
    calculateAuraStats(aura);

    activeAuras.put(casterId, aura);

    log.debug("Activated aura {} (level {}) for entity {}", def.name, skillLevel, casterId);

    if (callback != null) {
      callback.onAuraActivated(casterId, skillId, skillLevel);
    }

    return true;
  }

  /**
   * 关闭光环
   * 
   * @param casterId 施放者实体 ID
   */
  public void deactivateAura(int casterId) {
    ActiveAura aura = activeAuras.remove(casterId);
    if (aura == null) {
      return;
    }

    // 移除所有受影响实体的效果
    for (int entityId : aura.affectedEntities) {
      removeAuraEffectFromEntity(entityId, aura.definition.skillId, casterId);

      if (callback != null) {
        callback.onEntityLeaveAura(entityId, casterId, aura.definition.skillId);
        callback.removeState(entityId, aura.definition.stateId);
      }
    }

    aura.active = false;
    aura.affectedEntities.clear();

    log.debug("Deactivated aura {} for entity {}", aura.definition.name, casterId);

    if (callback != null) {
      callback.onAuraDeactivated(casterId, aura.definition.skillId);
    }
  }

  /**
   * 检查施放者是否有活跃光环
   */
  public boolean hasActiveAura(int casterId) {
    return activeAuras.containsKey(casterId);
  }

  /**
   * 获取施放者的活跃光环
   */
  public ActiveAura getActiveAura(int casterId) {
    return activeAuras.get(casterId);
  }

  //==========================================================================
  // 核心方法 - 光环更新
  //==========================================================================

  /**
   * 更新所有光环（每帧调用）
   * 
   * @param deltaTime 帧间隔时间
   */
  public void update(float deltaTime) {
    gameTime += deltaTime;

    Array<Integer> invalidCasters = new Array<>();
    for (IntMap.Entry<ActiveAura> entry : activeAuras) {
      if (callback == null || callback.getEntityPosition(entry.key) == null) {
        invalidCasters.add(entry.key);
      } else {
        updateAura(entry.value, deltaTime);
        if (!entry.value.active) invalidCasters.add(entry.key);
      }
    }
    // Never mutate IntMap while its iterator is active. Removing after the
    // pass also guarantees effects are cleaned up when a caster dies/leaves.
    for (int casterId : invalidCasters) {
      deactivateAura(casterId);
    }
  }

  /**
   * 更新单个光环
   */
  private void updateAura(ActiveAura aura, float deltaTime) {
    if (!aura.active || callback == null) {
      return;
    }

    // 检查法力消耗
    if (aura.definition.manaCostPerSecond > 0) {
      aura.manaCostAccumulator += aura.definition.manaCostPerSecond * deltaTime;
      int manaCost = MathUtils.floor(aura.manaCostAccumulator);
      if (manaCost > 0 && !callback.consumeMana(aura.casterId, manaCost)) {
        // Mark now and remove after the IntMap update pass completes.
        aura.active = false;
        return;
      }
      aura.manaCostAccumulator -= manaCost;
    }

    // 获取施放者位置
    float[] casterPos = callback.getEntityPosition(aura.casterId);
    if (casterPos == null) {
      return;
    }

    // 获取范围内的实体
    Array<Integer> entitiesInRange = callback.getEntitiesInRange(
        casterPos[0], casterPos[1], aura.range);

    // 更新受影响实体
    Array<Integer> newAffected = new Array<>();

    for (int entityId : entitiesInRange) {
      // 跳过自身（除非光环影响自身）
      if (entityId == aura.casterId && !aura.definition.affectsSelf) {
        continue;
      }

      // 检查关系
      boolean isAlly = callback.isAlly(aura.casterId, entityId);

      // 判断是否应该应用
      boolean shouldApply = false;
      if (aura.definition.auraType == AURA_TYPE_BUFF) {
        shouldApply = isAlly && (aura.definition.affectsParty || entityId == aura.casterId);
      } else if (aura.definition.auraType == AURA_TYPE_DEBUFF ||
                 aura.definition.auraType == AURA_TYPE_DAMAGE) {
        shouldApply = !isAlly && aura.definition.affectsEnemy;
      }

      if (shouldApply) {
        newAffected.add(entityId);

        // 检查是否是新进入的实体
        if (!aura.affectedEntities.contains(entityId, false)) {
          applyAuraToEntity(entityId, aura);
        }
      }
    }

    // 检查离开范围的实体
    for (int entityId : aura.affectedEntities) {
      if (!newAffected.contains(entityId, false)) {
        removeAuraEffectFromEntity(entityId, aura.definition.skillId, aura.casterId);

        if (callback != null) {
          callback.onEntityLeaveAura(entityId, aura.casterId, aura.definition.skillId);
          callback.removeState(entityId, aura.definition.stateId);
        }
      }
    }

    aura.affectedEntities = newAffected;
    aura.lastUpdateTime = gameTime;
  }

  /**
   * 将光环效果应用到实体
   */
  private void applyAuraToEntity(int entityId, ActiveAura aura) {
    // 检查叠加规则：同类光环不叠加，取最高值
    Array<AuraEffect> effects = entityAuraEffects.get(entityId);
    if (effects == null) {
      effects = new Array<>();
      entityAuraEffects.put(entityId, effects);
    }

    // 查找同类光环效果
    AuraEffect existingEffect = null;
    for (AuraEffect effect : effects) {
      if (effect.skillId == aura.definition.skillId) {
        existingEffect = effect;
        break;
      }
    }

    if (existingEffect != null) {
      // 比较效果值，取最高
      boolean shouldReplace = false;
      for (int i = 0; i < MAX_AURA_STATS; i++) {
        if (aura.statValues[i] > existingEffect.statValues[i]) {
          shouldReplace = true;
          break;
        }
      }

      if (!shouldReplace) {
        return; // 当前效果更低，不替换
      }

      // 更新效果值
      System.arraycopy(aura.statValues, 0, existingEffect.statValues, 0, MAX_AURA_STATS);
      existingEffect.casterId = aura.casterId;
    } else {
      // 添加新效果
      AuraEffect newEffect = new AuraEffect();
      newEffect.skillId = aura.definition.skillId;
      newEffect.casterId = aura.casterId;
      System.arraycopy(aura.statValues, 0, newEffect.statValues, 0, MAX_AURA_STATS);
      effects.add(newEffect);
    }

    log.debug("Applied aura {} to entity {}", aura.definition.name, entityId);

    if (callback != null) {
      callback.onEntityEnterAura(entityId, aura.casterId, aura.definition.skillId, aura.statValues);
      callback.applyState(entityId, aura.definition.stateId, -1,
          aura.definition.statIds, aura.statValues);
    }
  }

  /**
   * 从实体移除光环效果
   */
  private void removeAuraEffectFromEntity(int entityId, int skillId, int casterId) {
    Array<AuraEffect> effects = entityAuraEffects.get(entityId);
    if (effects == null) {
      return;
    }

    for (int i = effects.size - 1; i >= 0; i--) {
      AuraEffect effect = effects.get(i);
      if (effect.skillId == skillId && effect.casterId == casterId) {
        effects.removeIndex(i);
        log.debug("Removed aura {} effect from entity {}", skillId, entityId);
        break;
      }
    }
  }

  //==========================================================================
  // 属性计算
  //==========================================================================

  /**
   * 计算光环范围
   */
  private float calculateAuraRange(AuraDefinition def, int skillLevel) {
    return def.baseRange + (skillLevel - 1) * def.rangePerLevel;
  }

  /**
   * 计算光环属性值
   */
  private void calculateAuraStats(ActiveAura aura) {
    AuraDefinition def = aura.definition;
    for (int i = 0; i < MAX_AURA_STATS; i++) {
      if (def.statIds[i] <= 0) {
        aura.statValues[i] = 0;
        continue;
      }
      aura.statValues[i] = def.baseStatValues[i] + (aura.skillLevel - 1) * def.statPerLevel[i];
    }
  }

  //==========================================================================
  // 光环注册
  //==========================================================================

  /**
   * 注册默认光环
   */
  private void registerDefaultAuras() {
    // 力量光环
    registerAura(SkillId.MIGHT, "Might", AURA_TYPE_BUFF, StateId.MIGHT,
        EXCLUSIVE_OFFENSE, 320, 20, 0,
        new int[]{Stat.damagepercent}, new int[]{40}, new int[]{10},
        true, true, true, false);

    // 祈祷光环
    registerAura(SkillId.PRAYER, "Prayer", AURA_TYPE_BUFF, StateId.PRAYER,
        EXCLUSIVE_NONE, 320, 20, 0.5f,
        new int[]{Stat.hpregen}, new int[]{2}, new int[]{1},
        true, true, true, false);

    // 抵抗闪电
    registerAura(SkillId.RESIST_LIGHTNING, "Resist Lightning", AURA_TYPE_BUFF, StateId.RESISTLIGHT,
        EXCLUSIVE_DEFENSE, 320, 20, 0,
        new int[]{Stat.lightresist}, new int[]{30}, new int[]{5},
        true, true, true, false);

    // 抵抗火焰
    registerAura(SkillId.RESIST_FIRE, "Resist Fire", AURA_TYPE_BUFF, StateId.RESISTFIRE,
        EXCLUSIVE_DEFENSE, 320, 20, 0,
        new int[]{Stat.fireresist}, new int[]{30}, new int[]{5},
        true, true, true, false);

    // 抵抗冰冷
    registerAura(SkillId.RESIST_COLD, "Resist Cold", AURA_TYPE_BUFF, StateId.RESISTCOLD,
        EXCLUSIVE_DEFENSE, 320, 20, 0,
        new int[]{Stat.coldresist}, new int[]{30}, new int[]{5},
        true, true, true, false);

    // 反抗光环
    registerAura(SkillId.DEFIANCE, "Defiance", AURA_TYPE_BUFF, StateId.DEFIANCE,
        EXCLUSIVE_DEFENSE, 320, 20, 0,
        new int[]{Stat.item_armor_percent}, new int[]{70}, new int[]{15},
        true, true, true, false);

    // 专注光环
    registerAura(SkillId.CONCENTRATION, "Concentration", AURA_TYPE_BUFF, StateId.CONCENTRATION,
        EXCLUSIVE_OFFENSE, 320, 20, 0,
        new int[]{Stat.damagepercent, Stat.item_tohit_percent}, new int[]{60, 20}, new int[]{10, 5},
        true, true, true, false);

    // 狂热光环
    registerAura(SkillId.FANATICISM, "Fanaticism", AURA_TYPE_BUFF, StateId.FANATICISM,
        EXCLUSIVE_OFFENSE, 200, 10, 0,
        new int[]{Stat.damagepercent, Stat.item_tohit_percent, Stat.velocitypercent}, new int[]{180, 56, 15}, new int[]{20, 8, 1},
        true, true, true, false);

    // 定罪光环
    registerAura(SkillId.CONVICTION, "Conviction", AURA_TYPE_DEBUFF, StateId.CONVICTION,
        EXCLUSIVE_CURSE, 400, 20, 0,
        new int[]{Stat.fireresist, Stat.coldresist, Stat.lightresist, Stat.armorclass},
        new int[]{-30, -30, -30, -50}, new int[]{-5, -5, -5, -10},
        false, false, false, true);

    // 救赎光环
    registerAura(SkillId.REDEMPTION, "Redemption", AURA_TYPE_BUFF, StateId.REDEMPTION,
        EXCLUSIVE_NONE, 320, 10, 0,
        new int[]{Stat.hpregen}, new int[]{25}, new int[]{5},
        true, false, false, false);

    // 冥想光环
    registerAura(SkillId.MEDITATION, "Meditation", AURA_TYPE_BUFF, StateId.MEDITATION,
        EXCLUSIVE_NONE, 320, 20, 0,
        new int[]{Stat.manarecoverybonus}, new int[]{60}, new int[]{15},
        true, true, true, false);

    log.debug("Registered {} default auras", auraDefinitions.size);
  }

  private void registerAura(int skillId, String name, int auraType, int stateId,
      int exclusiveGroup, float baseRange, float rangePerLevel, float manaCost,
      int[] statIds, int[] baseStats, int[] perLevel,
      boolean self, boolean party, boolean merc, boolean enemy) {

    AuraDefinition def = new AuraDefinition();
    def.skillId = skillId;
    def.name = name;
    def.auraType = auraType;
    def.stateId = stateId;
    def.exclusiveGroup = exclusiveGroup;
    def.baseRange = baseRange;
    def.rangePerLevel = rangePerLevel;
    def.manaCostPerSecond = manaCost;
    def.affectsSelf = self;
    def.affectsParty = party;
    def.affectsMercenary = merc;
    def.affectsEnemy = enemy;

    for (int i = 0; i < Math.min(statIds.length, MAX_AURA_STATS); i++) {
      def.statIds[i] = statIds.length > i ? statIds[i] : 0;
      def.baseStatValues[i] = baseStats.length > i ? baseStats[i] : 0;
      def.statPerLevel[i] = perLevel.length > i ? perLevel[i] : 0;
    }

    auraDefinitions.put(skillId, def);
  }

  //==========================================================================
  // 配置方法
  //==========================================================================

  public void setCallback(AuraCallback callback) {
    this.callback = callback;
  }

  /**
   * 注册光环定义
   */
  public void registerAuraDefinition(AuraDefinition def) {
    auraDefinitions.put(def.skillId, def);
  }

  /**
   * 获取实体受到的所有光环效果
   */
  public Array<AuraEffect> getEntityAuraEffects(int entityId) {
    return entityAuraEffects.get(entityId);
  }

  /**
   * 清理实体数据
   */
  public void removeEntity(int entityId) {
    // 关闭该实体的光环
    deactivateAura(entityId);

    // 清理效果列表
    entityAuraEffects.remove(entityId);
  }
}
