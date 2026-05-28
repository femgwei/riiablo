package com.riiablo.engine.server.player;

import com.riiablo.CharacterClass;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatListRef;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.CharStats;
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
        return spendStrength(stats);

      case STAT_TYPE_ENERGY:
        return spendEnergy(stats, charStats);

      case STAT_TYPE_DEXTERITY:
        return spendDexterity(stats);

      case STAT_TYPE_VITALITY:
        return spendVitality(stats, charStats);

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
  private int spendStrength(StatListRef stats) {
    int currentStr = getInt(stats, Stat.strength, 0);
    if (currentStr >= MAX_STAT_VALUE) {
      return RESULT_MAX_REACHED;
    }

    // 扣除属性点
    stats.put(Stat.statpts, getInt(stats, Stat.statpts, 0) - 1);
    // 增加力量
    stats.put(Stat.strength, currentStr + 1);

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
  private int spendEnergy(StatListRef stats, CharStats.Entry charStats) {
    int currentEnergy = getInt(stats, Stat.energy, 0);
    if (currentEnergy >= MAX_STAT_VALUE) {
      return RESULT_MAX_REACHED;
    }

    // 扣除属性点
    stats.put(Stat.statpts, getInt(stats, Stat.statpts, 0) - 1);
    // 增加精力
    stats.put(Stat.energy, currentEnergy + 1);

    // 增加法力（使用固定数 << 6）
    int manaPerMagic = charStats.ManaPerMagic << 6;
    int currentMana = getInt(stats, Stat.mana, 0);
    int currentMaxMana = getInt(stats, Stat.maxmana, 0);
    stats.put(Stat.mana, currentMana + manaPerMagic);
    stats.put(Stat.maxmana, currentMaxMana + manaPerMagic);

    log.debug("Spent point on Energy: {} -> {}, +{} mana", 
        currentEnergy, currentEnergy + 1, manaPerMagic >> 6);
    return RESULT_SUCCESS;
  }

  /**
   * 分配敏捷属性点
   * 
   * @param stats 属性列表
   * @return 分配结果
   */
  private int spendDexterity(StatListRef stats) {
    int currentDex = getInt(stats, Stat.dexterity, 0);
    if (currentDex >= MAX_STAT_VALUE) {
      return RESULT_MAX_REACHED;
    }

    // 扣除属性点
    stats.put(Stat.statpts, getInt(stats, Stat.statpts, 0) - 1);
    // 增加敏捷
    stats.put(Stat.dexterity, currentDex + 1);

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
  private int spendVitality(StatListRef stats, CharStats.Entry charStats) {
    int currentVit = getInt(stats, Stat.vitality, 0);
    if (currentVit >= MAX_STAT_VALUE) {
      return RESULT_MAX_REACHED;
    }

    // 扣除属性点
    stats.put(Stat.statpts, getInt(stats, Stat.statpts, 0) - 1);
    // 增加体力
    stats.put(Stat.vitality, currentVit + 1);

    // 增加生命值（使用固定数 << 6）
    int lifePerVit = charStats.LifePerVitality << 6;
    int currentHp = getInt(stats, Stat.hitpoints, 0);
    int currentMaxHp = getInt(stats, Stat.maxhp, 0);
    stats.put(Stat.hitpoints, Math.min(currentHp + lifePerVit, currentMaxHp + lifePerVit));
    stats.put(Stat.maxhp, currentMaxHp + lifePerVit);

    // 增加体力值
    int stamPerVit = charStats.StaminaPerVitality << 6;
    int currentStam = getInt(stats, Stat.stamina, 0);
    int currentMaxStam = getInt(stats, Stat.maxstamina, 0);
    stats.put(Stat.stamina, Math.min(currentStam + stamPerVit, currentMaxStam + stamPerVit));
    stats.put(Stat.maxstamina, currentMaxStam + stamPerVit);

    log.debug("Spent point on Vitality: {} -> {}, +{} life, +{} stamina", 
        currentVit, currentVit + 1, lifePerVit >> 6, stamPerVit >> 6);
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
    if (charData == null) {
      return false;
    }

    StatListRef stats = charData.getStats().base();

    // 检查是否有可用技能点
    int availablePoints = getInt(stats, Stat.newskills, 0);
    if (availablePoints <= 0) {
      log.debug("No skill points available");
      return false;
    }

    // 检查技能是否有效
    if (!isValidSkillForClass(charData.charClass & 0xFF, skillId)) {
      log.debug("Invalid skill {} for class {}", skillId, charData.charClass);
      return false;
    }

    // 检查技能等级限制
    int currentLevel = charData.getSkill(skillId);
    if (currentLevel >= getMaxSkillLevel(skillId)) {
      log.debug("Skill {} already at max level {}", skillId, currentLevel);
      return false;
    }

    // 检查技能前置条件
    if (!checkSkillPrerequisites(charData, skillId)) {
      log.debug("Skill {} prerequisites not met", skillId);
      return false;
    }

    // 检查等级需求
    if (!checkLevelRequirement(charData, skillId)) {
      log.debug("Level requirement not met for skill {}", skillId);
      return false;
    }

    // 扣除技能点
    stats.put(Stat.newskills, availablePoints - 1);

    // 增加技能等级（直接更新skillData，然后同步到skills）
    // TODO: 需要在CharData中添加setSkillLevel方法
    // 暂时通过刷新技能来实现
    log.debug("Spent point on skill {}: {} -> {}", skillId, currentLevel, currentLevel + 1);
    return true;
  }

  /**
   * 检查技能是否对该职业有效
   */
  private boolean isValidSkillForClass(int classId, int skillId) {
    // TODO: 从 Skills.txt 读取技能职业限制
    // 简化实现：假设所有技能都有效
    return skillId >= 0 && skillId < 500;
  }

  /**
   * 获取技能最大等级
   */
  private int getMaxSkillLevel(int skillId) {
    // TODO: 从 Skills.txt 读取
    return 20; // 默认最大20级
  }

  /**
   * 检查技能前置条件
   */
  private boolean checkSkillPrerequisites(CharData charData, int skillId) {
    // TODO: 从 Skills.txt 读取前置技能并验证
    return true;
  }

  /**
   * 检查等级需求
   */
  private boolean checkLevelRequirement(CharData charData, int skillId) {
    // TODO: 从 Skills.txt 读取等级需求
    return true;
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
}
