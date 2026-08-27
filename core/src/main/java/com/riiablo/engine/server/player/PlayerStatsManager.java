package com.riiablo.engine.server.player;

import com.riiablo.CharacterClass;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatListRef;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.CharStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.save.CharData;

/**
 * 玩家属性管理器 - 基于 D2MOD PLAYERSTATS 移植
 * 
 * <p>处理玩家属性点分配和技能点分配：
 * <ul>
 *   <li>力量分配：增加物理伤害和装备需求</li>
 *   <li>敏捷分配：增加命中率、格挡率</li>
 *   <li>体力分配：增加生命值和体力值</li>
 *   <li>精力分配：增加法力值</li>
 *   <li>技能点分配</li>
 * </ul>
 * 
 * <p>参考：D2MOD/source/D2Game/src/PLAYER/PlayerStats.cpp
 * 
 * @author riiablo team
 */
public class PlayerStatsManager {
  private static final Logger log = LogManager.getLogger(PlayerStatsManager.class);

  /** 单例实例 */
  public static final PlayerStatsManager INSTANCE = new PlayerStatsManager();

  private PlayerStatsManager() {}

  //==========================================================================
  // 属性ID常量
  //==========================================================================

  /** 属性类型：力量 */
  public static final int STAT_TYPE_STRENGTH = 0;
  /** 属性类型：精力 */
  public static final int STAT_TYPE_ENERGY = 1;
  /** 属性类型：敏捷 */
  public static final int STAT_TYPE_DEXTERITY = 2;
  /** 属性类型：体力 */
  public static final int STAT_TYPE_VITALITY = 3;

  //==========================================================================
  // 分配结果
  //==========================================================================

  /** 分配成功 */
  public static final int RESULT_SUCCESS = 0;
  /** 没有可用属性点 */
  public static final int RESULT_NO_POINTS = 1;
  /** 无效的属性类型 */
  public static final int RESULT_INVALID_STAT = 2;
  /** 属性已达上限 */
  public static final int RESULT_MAX_REACHED = 3;

  public static final String SKILL_OK = "OK";
  public static final String SKILL_NO_POINTS = "NO_SKILL_POINTS";
  public static final String SKILL_INVALID = "INVALID_SKILL";
  public static final String SKILL_WRONG_CLASS = "WRONG_CLASS";
  public static final String SKILL_MAX_LEVEL = "MAX_SKILL_LEVEL";
  public static final String SKILL_PREREQUISITE = "PREREQUISITE_NOT_MET";
  public static final String SKILL_LEVEL_REQUIRED = "LEVEL_REQUIREMENT_NOT_MET";

  /** 每个属性的最大值 */
  public static final int MAX_STAT_VALUE = 1023;

  //==========================================================================
  // 核心方法
  //==========================================================================

  /**
   * 分配属性点
   * 
   * <p>参考 D2MOD PLAYERSTATS_SpendStatPoint
   * 
   * @param charData 角色数据
   * @param statType 属性类型（0=力量, 1=精力, 2=敏捷, 3=体力）
   * @return 分配结果
   */
  public int spendStatPoint(CharData charData, int statType) {
    if (charData == null) {
      return RESULT_INVALID_STAT;
    }

    StatListRef stats = charData.getStats().base();
    StatListRef aggregate = charData.getStats().aggregate();

    // 检查是否有可用属性点
    int availablePoints = getInt(stats, Stat.statpts, 0);
    if (availablePoints <= 0) {
      log.debug("No stat points available");
      return RESULT_NO_POINTS;
    }

    // 获取角色职业配置
    CharacterClass classId = CharacterClass.get(charData.charClass & 0xFF);
    if (classId == null) {
      log.warn("Unknown character class: {}", charData.charClass);
      return RESULT_INVALID_STAT;
    }

    CharStats.Entry charStats = classId.entry();
    if (charStats == null) {
      log.warn("CharStats entry not found for class: {}", classId);
      return RESULT_INVALID_STAT;
    }

    // 根据属性类型进行分配
    switch (statType) {
      case STAT_TYPE_STRENGTH:
        return spendStrength(stats, aggregate);

      case STAT_TYPE_ENERGY:
        return spendEnergy(stats, aggregate, charStats);

      case STAT_TYPE_DEXTERITY:
        return spendDexterity(stats, aggregate);

      case STAT_TYPE_VITALITY:
        return spendVitality(stats, aggregate, charStats);

      default:
        log.warn("Invalid stat type: {}", statType);
        return RESULT_INVALID_STAT;
    }
  }

  /**
   * 分配力量属性点
   * 
   * @param stats 属性列表
   * @return 分配结果
   */
  private int spendStrength(StatListRef stats, StatListRef aggregate) {
    int currentStr = getInt(stats, Stat.strength, 0);
    if (currentStr >= MAX_STAT_VALUE) {
      return RESULT_MAX_REACHED;
    }

    // 扣除属性点
    spendPoint(stats, aggregate);
    // 增加力量
    stats.put(Stat.strength, currentStr + 1);
    addInt(aggregate, Stat.strength, 1);

    log.debug("Spent point on Strength: {} -> {}", currentStr, currentStr + 1);
    return RESULT_SUCCESS;
  }

  /**
   * 分配精力属性点
   * 
   * <p>精力增加法力值和最大法力值
   * 
   * @param stats 属性列表
   * @param charStats 职业配置
   * @return 分配结果
   */
  private int spendEnergy(StatListRef stats, StatListRef aggregate, CharStats.Entry charStats) {
    int currentEnergy = getInt(stats, Stat.energy, 0);
    if (currentEnergy >= MAX_STAT_VALUE) {
      return RESULT_MAX_REACHED;
    }

    // 扣除属性点
    spendPoint(stats, aggregate);
    // 增加精力
    stats.put(Stat.energy, currentEnergy + 1);
    addInt(aggregate, Stat.energy, 1);

    // 增加法力（使用固定数 << 6）
    float manaPerMagic = charStats.ManaPerMagic / 4f;
    float currentMana = getFixed(stats, Stat.mana, 0);
    float currentMaxMana = getFixed(stats, Stat.maxmana, 0);
    stats.put(Stat.mana, currentMana + manaPerMagic);
    stats.put(Stat.maxmana, currentMaxMana + manaPerMagic);
    addFixed(aggregate, Stat.mana, manaPerMagic);
    addFixed(aggregate, Stat.maxmana, manaPerMagic);

    log.debug("Spent point on Energy: {} -> {}, +{} mana", 
        currentEnergy, currentEnergy + 1, manaPerMagic);
    return RESULT_SUCCESS;
  }

  /**
   * 分配敏捷属性点
   * 
   * @param stats 属性列表
   * @return 分配结果
   */
  private int spendDexterity(StatListRef stats, StatListRef aggregate) {
    int currentDex = getInt(stats, Stat.dexterity, 0);
    if (currentDex >= MAX_STAT_VALUE) {
      return RESULT_MAX_REACHED;
    }

    // 扣除属性点
    spendPoint(stats, aggregate);
    // 增加敏捷
    stats.put(Stat.dexterity, currentDex + 1);
    addInt(aggregate, Stat.dexterity, 1);

    log.debug("Spent point on Dexterity: {} -> {}", currentDex, currentDex + 1);
    return RESULT_SUCCESS;
  }

  /**
   * 分配体力属性点
   * 
   * <p>体力增加生命值、最大生命值、体力值和最大体力值
   * 
   * @param stats 属性列表
   * @param charStats 职业配置
   * @return 分配结果
   */
  private int spendVitality(StatListRef stats, StatListRef aggregate, CharStats.Entry charStats) {
    int currentVit = getInt(stats, Stat.vitality, 0);
    if (currentVit >= MAX_STAT_VALUE) {
      return RESULT_MAX_REACHED;
    }

    // 扣除属性点
    spendPoint(stats, aggregate);
    // 增加体力
    stats.put(Stat.vitality, currentVit + 1);
    addInt(aggregate, Stat.vitality, 1);

    // 增加生命值（使用固定数 << 6）
    float lifePerVit = charStats.LifePerVitality / 4f;
    float currentHp = getFixed(stats, Stat.hitpoints, 0);
    float currentMaxHp = getFixed(stats, Stat.maxhp, 0);
    stats.put(Stat.hitpoints, Math.min(currentHp + lifePerVit, currentMaxHp + lifePerVit));
    stats.put(Stat.maxhp, currentMaxHp + lifePerVit);
    addFixed(aggregate, Stat.hitpoints, lifePerVit);
    addFixed(aggregate, Stat.maxhp, lifePerVit);

    // 增加体力值
    float stamPerVit = charStats.StaminaPerVitality / 4f;
    float currentStam = getFixed(stats, Stat.stamina, 0);
    float currentMaxStam = getFixed(stats, Stat.maxstamina, 0);
    stats.put(Stat.stamina, Math.min(currentStam + stamPerVit, currentMaxStam + stamPerVit));
    stats.put(Stat.maxstamina, currentMaxStam + stamPerVit);
    addFixed(aggregate, Stat.stamina, stamPerVit);
    addFixed(aggregate, Stat.maxstamina, stamPerVit);

    log.debug("Spent point on Vitality: {} -> {}, +{} life, +{} stamina", 
        currentVit, currentVit + 1, lifePerVit, stamPerVit);
    return RESULT_SUCCESS;
  }

  //==========================================================================
  // 技能点分配
  //==========================================================================

  /**
   * 分配技能点
   * 
   * @param charData 角色数据
   * @param skillId 技能ID
   * @return 是否成功
   */
  public boolean spendSkillPoint(CharData charData, int skillId) {
    String validation = validateSkillPoint(charData, skillId);
    if (!SKILL_OK.equals(validation)) return false;

    StatListRef stats = charData.getStats().base();
    int availablePoints = getInt(stats, Stat.newskills, 0);
    int currentLevel = charData.getBaseSkillLevel(skillId);

    // 扣除技能点
    stats.put(Stat.newskills, availablePoints - 1);
    charData.getStats().aggregate().put(Stat.newskills, availablePoints - 1);

    // Increase the saved/base skill level and notify the client UI.
    if (!charData.setSkillLevel(skillId, currentLevel + 1)) {
      // Keep the point available if the character data rejected the update.
      stats.put(Stat.newskills, availablePoints);
      charData.getStats().aggregate().put(Stat.newskills, availablePoints);
      return false;
    }
    log.info("[SKILL_POINT_SPEND] character={} skill={} level={}->{} points={}->{}",
        charData.name, skillId, currentLevel, currentLevel + 1,
        availablePoints, availablePoints - 1);
    return true;
  }

  /** Returns a stable network-safe rejection reason without mutating character data. */
  public String validateSkillPoint(CharData charData, int skillId) {
    if (charData == null || charData.getStats() == null) return SKILL_INVALID;
    Skills.Entry skill = Riiablo.files.skills.get(skillId);
    if (skill == null) return SKILL_INVALID;
    int classId = charData.charClass & 0xFF;
    if (!isValidSkillForClass(classId, skillId)) return SKILL_WRONG_CLASS;
    if (getInt(charData.getStats().base(), Stat.newskills, 0) <= 0) return SKILL_NO_POINTS;
    if (charData.getBaseSkillLevel(skillId) >= getMaxSkillLevel(skillId)) {
      return SKILL_MAX_LEVEL;
    }
    if (!checkSkillPrerequisites(charData, skillId)) return SKILL_PREREQUISITE;
    if (!checkLevelRequirement(charData, skillId)) return SKILL_LEVEL_REQUIRED;
    return SKILL_OK;
  }

  /**
   * 检查技能是否对该职业有效
   */
  private boolean isValidSkillForClass(int classId, int skillId) {
    Skills.Entry skill = Riiablo.files.skills.get(skillId);
    if (skill == null || skill.charclass == null || skill.charclass.isEmpty()) return false;
    CharacterClass characterClass;
    try {
      characterClass = CharacterClass.get(classId);
    } catch (RuntimeException ignored) {
      return false;
    }
    return skillId >= characterClass.firstSpell && skillId < characterClass.lastSpell
        && Skills.getClassId(skill.charclass) == classId;
  }

  /**
   * 获取技能最大等级
   */
  private int getMaxSkillLevel(int skillId) {
    Skills.Entry skill = Riiablo.files.skills.get(skillId);
    return skill != null && skill.maxlvl > 0 ? skill.maxlvl : 20;
  }

  /**
   * 检查技能前置条件
   */
  private boolean checkSkillPrerequisites(CharData charData, int skillId) {
    Skills.Entry skill = Riiablo.files.skills.get(skillId);
    if (skill == null) return false;
    return hasPrerequisite(charData, skill.reqskill1)
        && hasPrerequisite(charData, skill.reqskill2)
        && hasPrerequisite(charData, skill.reqskill3);
  }

  /**
   * 检查等级需求
   */
  private boolean checkLevelRequirement(CharData charData, int skillId) {
    Skills.Entry skill = Riiablo.files.skills.get(skillId);
    if (skill == null) return false;
    int level = getInt(charData.getStats().aggregate(), Stat.level, charData.level & 0xFF);
    return level >= Math.max(1, skill.reqlevel);
  }

  private boolean hasPrerequisite(CharData charData, String prerequisite) {
    if (prerequisite == null || prerequisite.isEmpty()) return true;
    Skills.Entry required = Riiablo.files.skills.get(prerequisite);
    return required != null && charData.getBaseSkillLevel(required.Id) > 0;
  }

  //==========================================================================
  // 批量分配
  //==========================================================================

  /**
   * 批量分配属性点
   * 
   * @param charData 角色数据
   * @param statType 属性类型
   * @param count 分配数量
   * @return 实际分配的数量
   */
  public int spendStatPoints(CharData charData, int statType, int count) {
    int spent = 0;
    for (int i = 0; i < count; i++) {
      if (spendStatPoint(charData, statType) == RESULT_SUCCESS) {
        spent++;
      } else {
        break;
      }
    }
    return spent;
  }

  //==========================================================================
  // 查询方法
  //==========================================================================

  /**
   * 获取可用属性点数
   */
  public int getAvailableStatPoints(CharData charData) {
    if (charData == null) return 0;
    return getInt(charData.getStats().base(), Stat.statpts, 0);
  }

  /**
   * 获取可用技能点数
   */
  public int getAvailableSkillPoints(CharData charData) {
    if (charData == null) return 0;
    return getInt(charData.getStats().base(), Stat.newskills, 0);
  }

  /**
   * 获取属性值
   */
  public int getStatValue(CharData charData, int statType) {
    if (charData == null) return 0;
    StatListRef stats = charData.getStats().base();
    
    switch (statType) {
      case STAT_TYPE_STRENGTH:
        return getInt(stats, Stat.strength, 0);
      case STAT_TYPE_ENERGY:
        return getInt(stats, Stat.energy, 0);
      case STAT_TYPE_DEXTERITY:
        return getInt(stats, Stat.dexterity, 0);
      case STAT_TYPE_VITALITY:
        return getInt(stats, Stat.vitality, 0);
      default:
        return 0;
    }
  }

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 安全获取整数属性值
   */
  private int getInt(StatListRef stats, short stat, int defaultValue) {
    StatRef ref = stats.get(stat);
    return ref != null ? ref.asInt() : defaultValue;
  }

  private float getFixed(StatListRef stats, short stat, float defaultValue) {
    StatRef ref = stats.get(stat);
    return ref != null ? ref.asFixed() : defaultValue;
  }

  private void spendPoint(StatListRef base, StatListRef aggregate) {
    int remaining = getInt(base, Stat.statpts, 0) - 1;
    base.put(Stat.statpts, remaining);
    if (aggregate != null) aggregate.put(Stat.statpts, remaining);
  }

  private void addInt(StatListRef stats, short stat, int delta) {
    if (stats != null) stats.put(stat, getInt(stats, stat, 0) + delta);
  }

  private void addFixed(StatListRef stats, short stat, float delta) {
    if (stats != null) stats.put(stat, getFixed(stats, stat, 0) + delta);
  }
}
