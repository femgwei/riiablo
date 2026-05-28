package com.riiablo.engine.server.skill;

import com.badlogic.gdx.utils.IntIntMap;

import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.save.CharData;

/**
 * 技能协同管理器 - 基于 D2MOD 移植
 * 
 * <p>管理技能之间的协同加成：
 * <ul>
 *   <li>技能协同加成计算</li>
 *   <li>协同技能查询</li>
 *   <li>技能伤害/效果增强</li>
 * </ul>
 * 
 * <p>协同机制说明：
 * 某些技能可以获得其他技能的加成，例如：
 * - 火球可以从火焰箭、火墙等技能获得伤害加成
 * - 每点协同技能通常提供一定百分比的加成
 * 
 * <p>参考：D2MOD/source/D2Game/src/SKILLS/Skills.cpp
 * 
 * @author riiablo team
 */
public class SkillSynergyManager {
  private static final Logger log = LogManager.getLogger(SkillSynergyManager.class);

  /** 单例实例 */
  public static final SkillSynergyManager INSTANCE = new SkillSynergyManager();

  private SkillSynergyManager() {}

  //==========================================================================
  // 协同类型
  //==========================================================================

  /** 协同类型：伤害加成 */
  public static final int SYNERGY_DAMAGE = 0;
  /** 协同类型：持续时间加成 */
  public static final int SYNERGY_DURATION = 1;
  /** 协同类型：范围加成 */
  public static final int SYNERGY_RANGE = 2;
  /** 协同类型：治疗加成 */
  public static final int SYNERGY_HEALING = 3;
  /** 协同类型：攻击准确率加成 */
  public static final int SYNERGY_ATTACK_RATING = 4;

  //==========================================================================
  // 核心方法
  //==========================================================================

  /**
   * 计算技能协同加成百分比
   * 
   * <p>参考 D2MOD SKILLS_GetSynergyBonus
   * 
   * @param charData 角色数据
   * @param skillId 技能ID
   * @param synergyType 协同类型
   * @return 协同加成百分比
   */
  public int calculateSynergyBonus(CharData charData, int skillId, int synergyType) {
    if (charData == null) {
      return 0;
    }

    // 获取协同技能列表
    int[] synergySkills = getSynergySkills(skillId, synergyType);
    if (synergySkills == null || synergySkills.length == 0) {
      return 0;
    }

    // 计算协同加成
    int totalBonus = 0;
    int bonusPerLevel = getSynergyBonusPerLevel(skillId, synergyType);

    for (int synergySkillId : synergySkills) {
      int skillLevel = charData.getSkill(synergySkillId);
      if (skillLevel > 0) {
        totalBonus += skillLevel * bonusPerLevel;
      }
    }

    log.debug("Skill {} synergy bonus: {}% (type={})", skillId, totalBonus, synergyType);
    return totalBonus;
  }

  /**
   * 获取技能的协同技能列表
   * 
   * @param skillId 技能ID
   * @param synergyType 协同类型
   * @return 协同技能ID数组
   */
  private int[] getSynergySkills(int skillId, int synergyType) {
    // TODO: 从 Skills.txt 读取协同技能
    // 这里提供一些示例协同关系
    
    switch (skillId) {
      // 法师火焰系
      case SkillId.FIRE_BOLT:
        return new int[] { SkillId.FIRE_BALL, SkillId.METEOR };
      case SkillId.FIRE_BALL:
        return new int[] { SkillId.FIRE_BOLT, SkillId.METEOR };
      case SkillId.METEOR:
        return new int[] { SkillId.FIRE_BOLT, SkillId.FIRE_BALL, SkillId.FIRE_MASTERY };
      
      // 法师冰冷系
      case SkillId.ICE_BOLT:
        return new int[] { SkillId.ICE_BLAST, SkillId.GLACIAL_SPIKE };
      case SkillId.ICE_BLAST:
        return new int[] { SkillId.ICE_BOLT, SkillId.GLACIAL_SPIKE };
      case SkillId.BLIZZARD:
        return new int[] { SkillId.ICE_BOLT, SkillId.ICE_BLAST, SkillId.GLACIAL_SPIKE };
      
      // 法师闪电系
      case SkillId.CHARGED_BOLT:
        return new int[] { SkillId.LIGHTNING, SkillId.CHAIN_LIGHTNING };
      case SkillId.LIGHTNING:
        return new int[] { SkillId.CHARGED_BOLT, SkillId.CHAIN_LIGHTNING };
      
      // 死灵法师骨系
      case SkillId.TEETH:
        return new int[] { SkillId.BONE_SPEAR, SkillId.BONE_SPIRIT };
      case SkillId.BONE_SPEAR:
        return new int[] { SkillId.TEETH, SkillId.BONE_SPIRIT };
      case SkillId.BONE_SPIRIT:
        return new int[] { SkillId.TEETH, SkillId.BONE_SPEAR };
      
      // 圣骑士圣光系
      case SkillId.HOLY_BOLT:
        return new int[] { SkillId.BLESSED_HAMMER, SkillId.FIST_OF_THE_HEAVENS };
      case SkillId.BLESSED_HAMMER:
        return new int[] { SkillId.HOLY_BOLT, SkillId.BLESSED_AIM, SkillId.VIGOR };
      
      default:
        return null;
    }
  }

  /**
   * 获取每级协同加成百分比
   * 
   * @param skillId 技能ID
   * @param synergyType 协同类型
   * @return 每级加成百分比
   */
  private int getSynergyBonusPerLevel(int skillId, int synergyType) {
    // TODO: 从 Skills.txt 读取
    // 大多数技能每级协同提供一定百分比加成
    
    switch (synergyType) {
      case SYNERGY_DAMAGE:
        // 大多数伤害技能每级+X%伤害
        return getSkillSynergyDamagePerLevel(skillId);
      
      case SYNERGY_DURATION:
        // 持续时间加成通常较低
        return 5;
      
      case SYNERGY_RANGE:
        // 范围加成
        return 3;
      
      case SYNERGY_HEALING:
        // 治疗加成
        return 10;
      
      case SYNERGY_ATTACK_RATING:
        // 攻击准确率加成
        return 10;
      
      default:
        return 0;
    }
  }

  /**
   * 获取技能伤害协同每级加成
   */
  private int getSkillSynergyDamagePerLevel(int skillId) {
    // 不同技能的协同系数不同
    switch (skillId) {
      // 火系技能通常+8%每级
      case SkillId.FIRE_BOLT:
      case SkillId.FIRE_BALL:
      case SkillId.METEOR:
        return 8;
      
      // 冰系技能通常+5%每级
      case SkillId.ICE_BOLT:
      case SkillId.ICE_BLAST:
      case SkillId.BLIZZARD:
        return 5;
      
      // 闪电系技能通常+8%每级
      case SkillId.CHARGED_BOLT:
      case SkillId.LIGHTNING:
      case SkillId.CHAIN_LIGHTNING:
        return 8;
      
      // 骨系技能通常+6%每级
      case SkillId.TEETH:
      case SkillId.BONE_SPEAR:
      case SkillId.BONE_SPIRIT:
        return 6;
      
      // 祝福之锤每级+14%
      case SkillId.BLESSED_HAMMER:
        return 14;
      
      default:
        return 5; // 默认5%每级
    }
  }

  /**
   * 应用协同加成到伤害
   * 
   * @param baseDamage 基础伤害
   * @param synergyBonus 协同加成百分比
   * @return 加成后的伤害
   */
  public int applyDamageSynergy(int baseDamage, int synergyBonus) {
    if (synergyBonus <= 0) {
      return baseDamage;
    }
    return baseDamage + baseDamage * synergyBonus / 100;
  }

  /**
   * 获取技能的所有协同信息（用于UI显示）
   * 
   * @param skillId 技能ID
   * @return 协同信息Map (技能ID -> 每级加成%)
   */
  public IntIntMap getSynergyInfo(int skillId) {
    IntIntMap info = new IntIntMap();
    
    int[] synergySkills = getSynergySkills(skillId, SYNERGY_DAMAGE);
    if (synergySkills != null) {
      int bonusPerLevel = getSynergyBonusPerLevel(skillId, SYNERGY_DAMAGE);
      for (int synergyId : synergySkills) {
        info.put(synergyId, bonusPerLevel);
      }
    }
    
    return info;
  }
}
