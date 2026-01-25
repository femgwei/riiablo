package com.riiablo.engine.server.object;

/**
 * 神殿类型枚举 - 基于 D2MOO Shrines.h 移植
 * 
 * <p>定义了游戏中所有神殿的效果类型。
 * 
 * <p>参考：D2MOO/source/D2Game/src/OBJECTS/Shrines.h
 * 
 * @author riiablo team
 */
public final class ShrineType {
  private ShrineType() {} // 不可实例化

  //==========================================================================
  // 神殿类型
  //==========================================================================

  /** 无效 */
  public static final int NONE = -1;

  /** 恢复神殿 - 恢复生命和法力 */
  public static final int REFILLING = 0;

  /** 生命神殿 - 提升生命恢复 */
  public static final int HEALTH = 1;

  /** 法力神殿 - 提升法力恢复 */
  public static final int MANA = 2;

  /** 恢复生命神殿 - 缓慢恢复生命 */
  public static final int HEALTH_EXCHANGE = 3;

  /** 恢复法力神殿 - 缓慢恢复法力 */
  public static final int MANA_EXCHANGE = 4;

  /** 护甲神殿 - 提升防御 */
  public static final int ARMOR = 5;

  /** 战斗神殿 - 提升攻击 */
  public static final int COMBAT = 6;

  /** 抗性神殿 - 提升全抗 */
  public static final int RESIST_FIRE = 7;

  /** 抗冰神殿 */
  public static final int RESIST_COLD = 8;

  /** 抗电神殿 */
  public static final int RESIST_LIGHTNING = 9;

  /** 抗毒神殿 */
  public static final int RESIST_POISON = 10;

  /** 技能神殿 - 提升所有技能 */
  public static final int SKILL = 11;

  /** 法力恢复神殿 */
  public static final int MANA_RECHARGE = 12;

  /** 体力神殿 - 提升体力恢复 */
  public static final int STAMINA = 13;

  /** 经验神殿 - 提升经验获取 */
  public static final int EXPERIENCE = 14;

  /** 宝石神殿 - 升级宝石 */
  public static final int GEM = 15;

  /** 妖术神殿 - 随机效果 */
  public static final int PORTAL = 16;

  /** 怪物神殿 - 刷新怪物 */
  public static final int MONSTER = 17;

  /** 火焰神殿 - 火焰伤害增加 */
  public static final int FIRE = 18;

  /** 爆炸神殿 - 附近爆炸 */
  public static final int EXPLODING = 19;

  /** 毒神殿 - 毒素伤害增加 */
  public static final int POISON = 20;

  /** 井 */
  public static final int WELL = 21;

  //==========================================================================
  // 神殿效果持续时间（秒）
  //==========================================================================

  /** 默认持续时间 */
  public static final int DEFAULT_DURATION = 120;

  /** 经验神殿持续时间 */
  public static final int EXPERIENCE_DURATION = 300;

  /** 技能神殿持续时间 */
  public static final int SKILL_DURATION = 96;

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 是否是增益神殿
   */
  public static boolean isBuff(int type) {
    switch (type) {
      case HEALTH:
      case MANA:
      case ARMOR:
      case COMBAT:
      case RESIST_FIRE:
      case RESIST_COLD:
      case RESIST_LIGHTNING:
      case RESIST_POISON:
      case SKILL:
      case STAMINA:
      case EXPERIENCE:
        return true;
      default:
        return false;
    }
  }

  /**
   * 是否是负面神殿
   */
  public static boolean isNegative(int type) {
    return type == MONSTER || type == EXPLODING;
  }

  /**
   * 是否是恢复类神殿
   */
  public static boolean isRestoring(int type) {
    return type == REFILLING || type == WELL || 
           type == HEALTH_EXCHANGE || type == MANA_EXCHANGE;
  }

  /**
   * 获取神殿效果持续时间（帧）
   */
  public static int getDurationFrames(int type) {
    int seconds;
    switch (type) {
      case EXPERIENCE:
        seconds = EXPERIENCE_DURATION;
        break;
      case SKILL:
        seconds = SKILL_DURATION;
        break;
      default:
        seconds = DEFAULT_DURATION;
        break;
    }
    return seconds * 25; // 每秒25帧
  }

  /**
   * 获取神殿名称
   */
  public static String getName(int type) {
    switch (type) {
      case REFILLING: return "Refilling Shrine";
      case HEALTH: return "Health Shrine";
      case MANA: return "Mana Shrine";
      case HEALTH_EXCHANGE: return "Health Exchange Shrine";
      case MANA_EXCHANGE: return "Mana Exchange Shrine";
      case ARMOR: return "Armor Shrine";
      case COMBAT: return "Combat Shrine";
      case RESIST_FIRE: return "Resist Fire Shrine";
      case RESIST_COLD: return "Resist Cold Shrine";
      case RESIST_LIGHTNING: return "Resist Lightning Shrine";
      case RESIST_POISON: return "Resist Poison Shrine";
      case SKILL: return "Skill Shrine";
      case MANA_RECHARGE: return "Mana Recharge Shrine";
      case STAMINA: return "Stamina Shrine";
      case EXPERIENCE: return "Experience Shrine";
      case GEM: return "Gem Shrine";
      case PORTAL: return "Portal Shrine";
      case MONSTER: return "Monster Shrine";
      case FIRE: return "Fire Shrine";
      case EXPLODING: return "Exploding Shrine";
      case POISON: return "Poison Shrine";
      case WELL: return "Well";
      default: return "Unknown Shrine";
    }
  }
}
