package com.riiablo.engine.server.monster;

/**
 * 怪物等级/品质 - 基于 D2MOO MonsterSpawn.h 移植
 * 
 * <p>定义了怪物的等级品质：普通、冠军、暗金、BOSS等。
 * 
 * <p>参考：D2MOO/source/D2Game/src/MONSTER/MonsterSpawn.h
 * 
 * @author riiablo team
 */
public final class MonsterRank {
  private MonsterRank() {} // 不可实例化

  //==========================================================================
  // 怪物等级
  //==========================================================================

  /** 普通怪物 */
  public static final int NORMAL = 0;

  /** 冠军怪物（蓝色名字） */
  public static final int CHAMPION = 1;

  /** 暗金怪物（金色名字，有随机词缀） */
  public static final int UNIQUE = 2;

  /** 超级暗金（精英怪头目） */
  public static final int SUPER_UNIQUE = 3;

  /** BOSS（游戏剧情 BOSS） */
  public static final int BOSS = 4;

  /** 小怪（暗金怪的随从） */
  public static final int MINION = 5;

  //==========================================================================
  // 冠军类型
  //==========================================================================

  /** 冠军：狂暴（伤害增加） */
  public static final int CHAMP_BERSERKER = 0;

  /** 冠军：冠军（平衡型） */
  public static final int CHAMP_CHAMPION = 1;

  /** 冠军：残废（攻速减慢，但更强） */
  public static final int CHAMP_POSSESSED = 2;

  /** 冠军：狂热（攻速增加） */
  public static final int CHAMP_FANATIC = 3;

  /** 冠军：幽灵（半透明，物理伤害减少） */
  public static final int CHAMP_GHOSTLY = 4;

  //==========================================================================
  // 属性修正
  //==========================================================================

  /** 冠军生命倍率 */
  public static final float CHAMPION_HP_MULTIPLIER = 3.0f;

  /** 暗金生命倍率 */
  public static final float UNIQUE_HP_MULTIPLIER = 4.0f;

  /** 超级暗金生命倍率 */
  public static final float SUPER_UNIQUE_HP_MULTIPLIER = 5.0f;

  /** 冠军经验倍率 */
  public static final float CHAMPION_EXP_MULTIPLIER = 3.0f;

  /** 暗金经验倍率 */
  public static final float UNIQUE_EXP_MULTIPLIER = 5.0f;

  /** 随从生命倍率 */
  public static final float MINION_HP_MULTIPLIER = 1.5f;

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 是否是精英怪
   */
  public static boolean isElite(int rank) {
    return rank == CHAMPION || rank == UNIQUE || rank == SUPER_UNIQUE;
  }

  /**
   * 是否是暗金怪
   */
  public static boolean isUnique(int rank) {
    return rank == UNIQUE || rank == SUPER_UNIQUE;
  }

  /**
   * 获取生命倍率
   */
  public static float getHpMultiplier(int rank) {
    switch (rank) {
      case CHAMPION:
        return CHAMPION_HP_MULTIPLIER;
      case UNIQUE:
        return UNIQUE_HP_MULTIPLIER;
      case SUPER_UNIQUE:
      case BOSS:
        return SUPER_UNIQUE_HP_MULTIPLIER;
      case MINION:
        return MINION_HP_MULTIPLIER;
      default:
        return 1.0f;
    }
  }

  /**
   * 获取经验倍率
   */
  public static float getExpMultiplier(int rank) {
    switch (rank) {
      case CHAMPION:
        return CHAMPION_EXP_MULTIPLIER;
      case UNIQUE:
      case SUPER_UNIQUE:
      case BOSS:
        return UNIQUE_EXP_MULTIPLIER;
      default:
        return 1.0f;
    }
  }

  /**
   * 获取等级名称
   */
  public static String getName(int rank) {
    switch (rank) {
      case NORMAL: return "Normal";
      case CHAMPION: return "Champion";
      case UNIQUE: return "Unique";
      case SUPER_UNIQUE: return "Super Unique";
      case BOSS: return "Boss";
      case MINION: return "Minion";
      default: return "Unknown";
    }
  }

  /**
   * 获取冠军类型名称
   */
  public static String getChampionTypeName(int champType) {
    switch (champType) {
      case CHAMP_BERSERKER: return "Berserker";
      case CHAMP_CHAMPION: return "Champion";
      case CHAMP_POSSESSED: return "Possessed";
      case CHAMP_FANATIC: return "Fanatic";
      case CHAMP_GHOSTLY: return "Ghostly";
      default: return "Unknown";
    }
  }
}
