package com.riiablo.engine.server.monster;

import com.badlogic.gdx.math.MathUtils;

import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 怪物工具类 - 基于 D2MOD Monster.cpp 移植
 * 
 * <p>提供怪物相关的计算和工具方法：
 * <ul>
 *   <li>玩家数量加成计算</li>
 *   <li>怪物等级计算</li>
 *   <li>难度调整</li>
 *   <li>经验值计算</li>
 * </ul>
 * 
 * <p>参考：D2MOD/source/D2Game/src/MONSTER/Monster.cpp
 * 
 * @author riiablo team
 */
public final class MonsterUtil {
  private static final Logger log = LogManager.getLogger(MonsterUtil.class);

  private MonsterUtil() {} // 不可实例化

  //==========================================================================
  // 难度常量
  //==========================================================================

  /** 普通难度 */
  public static final int DIFFICULTY_NORMAL = 0;
  /** 噩梦难度 */
  public static final int DIFFICULTY_NIGHTMARE = 1;
  /** 地狱难度 */
  public static final int DIFFICULTY_HELL = 2;

  //==========================================================================
  // 玩家数量加成表（基于 D2MOD）
  //==========================================================================

  /**
   * HP 加成百分比表（索引 = 玩家数量 - 1）
   * 
   * <p>玩家数量 1-8 对应的生命值加成：
   * 100%, 150%, 200%, 250%, 300%, 350%, 400%, 450%
   */
  private static final int[] HP_BONUS_TABLE = {
      100,  // 1 玩家
      150,  // 2 玩家
      200,  // 3 玩家
      250,  // 4 玩家
      300,  // 5 玩家
      350,  // 6 玩家
      400,  // 7 玩家
      450   // 8 玩家
  };

  /**
   * 经验值加成百分比表（索引 = 玩家数量 - 1）
   * 
   * <p>玩家数量 1-8 对应的经验值加成：
   * 100%, 165%, 230%, 295%, 360%, 425%, 490%, 555%
   */
  private static final int[] EXP_BONUS_TABLE = {
      100,  // 1 玩家
      165,  // 2 玩家
      230,  // 3 玩家
      295,  // 4 玩家
      360,  // 5 玩家
      425,  // 6 玩家
      490,  // 7 玩家
      555   // 8 玩家
  };

  //==========================================================================
  // 玩家数量加成计算
  //==========================================================================

  /**
   * 获取生命值加成百分比
   * 
   * <p>基于 D2MOD MONSTER_GetHpBonus 函数
   * 
   * @param playerCount 玩家数量（1-8）
   * @return 生命值加成百分比（100 = 100%）
   */
  public static int getHpBonus(int playerCount) {
    int index = MathUtils.clamp(playerCount - 1, 0, HP_BONUS_TABLE.length - 1);
    return HP_BONUS_TABLE[index];
  }

  /**
   * 获取经验值加成百分比
   * 
   * <p>基于 D2MOD MONSTER_GetExperienceBonus 函数
   * 
   * @param playerCount 玩家数量（1-8）
   * @return 经验值加成百分比（100 = 100%）
   */
  public static int getExperienceBonus(int playerCount) {
    int index = MathUtils.clamp(playerCount - 1, 0, EXP_BONUS_TABLE.length - 1);
    return EXP_BONUS_TABLE[index];
  }

  /**
   * 计算调整后的生命值
   * 
   * @param baseHp 基础生命值
   * @param playerCount 玩家数量
   * @return 调整后的生命值
   */
  public static int calculateAdjustedHp(int baseHp, int playerCount) {
    int bonus = getHpBonus(playerCount);
    return baseHp * bonus / 100;
  }

  /**
   * 计算调整后的经验值
   * 
   * @param baseExp 基础经验值
   * @param playerCount 玩家数量
   * @return 调整后的经验值
   */
  public static int calculateAdjustedExp(int baseExp, int playerCount) {
    int bonus = getExperienceBonus(playerCount);
    return baseExp * bonus / 100;
  }

  //==========================================================================
  // 怪物等级计算
  //==========================================================================

  /**
   * 计算怪物在指定难度和区域的等级
   * 
   * <p>基于 D2MOD DATATBLS_GetMonsterLevelInArea 逻辑
   * 
   * @param baseLevel 基础等级（来自 monstats.txt）
   * @param areaLevel 区域等级（来自 levels.txt）
   * @param difficulty 难度（0=普通, 1=噩梦, 2=地狱）
   * @param isExpansion 是否资料片
   * @return 调整后的怪物等级
   */
  public static int calculateMonsterLevel(int baseLevel, int areaLevel, int difficulty, boolean isExpansion) {
    // 普通难度使用怪物基础等级
    if (difficulty == DIFFICULTY_NORMAL) {
      return baseLevel;
    }
    
    // 资料片在高难度使用区域等级
    if (isExpansion) {
      return areaLevel;
    }
    
    // 非资料片使用基础等级 + 难度偏移
    int levelOffset = 0;
    switch (difficulty) {
      case DIFFICULTY_NIGHTMARE:
        levelOffset = 40; // 噩梦难度增加 40 级
        break;
      case DIFFICULTY_HELL:
        levelOffset = 80; // 地狱难度增加 80 级
        break;
    }
    
    return baseLevel + levelOffset;
  }

  //==========================================================================
  // 难度调整
  //==========================================================================

  /**
   * 获取难度名称
   * 
   * @param difficulty 难度ID
   * @return 难度名称
   */
  public static String getDifficultyName(int difficulty) {
    switch (difficulty) {
      case DIFFICULTY_NORMAL: return "Normal";
      case DIFFICULTY_NIGHTMARE: return "Nightmare";
      case DIFFICULTY_HELL: return "Hell";
      default: return "Unknown";
    }
  }

  /**
   * 获取难度的抗性减少惩罚
   * 
   * <p>噩梦和地狱难度会降低玩家抗性
   * 
   * @param difficulty 难度
   * @return 抗性减少值
   */
  public static int getResistancePenalty(int difficulty) {
    switch (difficulty) {
      case DIFFICULTY_NORMAL:
        return 0;
      case DIFFICULTY_NIGHTMARE:
        return -40; // 噩梦 -40%
      case DIFFICULTY_HELL:
        return -100; // 地狱 -100%
      default:
        return 0;
    }
  }

  /**
   * 获取难度的经验值惩罚
   * 
   * <p>高等级角色获得的经验值会降低
   * 
   * @param difficulty 难度
   * @param monsterLevel 怪物等级
   * @param playerLevel 玩家等级
   * @return 经验值倍率（0.0-1.0）
   */
  public static float getExperiencePenalty(int difficulty, int monsterLevel, int playerLevel) {
    int levelDiff = playerLevel - monsterLevel;
    
    // 如果玩家等级比怪物高太多，经验值降低
    if (levelDiff <= 5) {
      return 1.0f;
    }
    
    // 每超过5级，经验值减少5%
    float penalty = 1.0f - ((levelDiff - 5) * 0.05f);
    return Math.max(penalty, 0.05f); // 最低 5%
  }

  //==========================================================================
  // 伤害计算辅助
  //==========================================================================

  /**
   * 计算怪物伤害范围
   * 
   * @param minDamage 最小伤害
   * @param maxDamage 最大伤害
   * @param difficulty 难度
   * @return 随机伤害值
   */
  public static int calculateDamage(int minDamage, int maxDamage, int difficulty) {
    // 难度调整
    float diffMultiplier = 1.0f;
    switch (difficulty) {
      case DIFFICULTY_NIGHTMARE:
        diffMultiplier = 2.0f;
        break;
      case DIFFICULTY_HELL:
        diffMultiplier = 3.0f;
        break;
    }
    
    int adjustedMin = (int)(minDamage * diffMultiplier);
    int adjustedMax = (int)(maxDamage * diffMultiplier);
    
    return MathUtils.random(adjustedMin, adjustedMax);
  }

  //==========================================================================
  // 死亡检测
  //==========================================================================

  /**
   * 计算怪物死亡后的尸体保留时间
   * 
   * @param isBoss 是否是 Boss
   * @param isUndead 是否是亡灵
   * @return 尸体保留时间（秒）
   */
  public static float getCorpseDuration(boolean isBoss, boolean isUndead) {
    if (isBoss) {
      return 30.0f; // Boss 尸体保留更久
    }
    if (isUndead) {
      return 15.0f; // 亡灵尸体
    }
    return 10.0f; // 普通怪物
  }

  /**
   * 检查是否可以复活该怪物尸体
   * 
   * @param monsterFlags 怪物标志
   * @param summonerFlags 召唤者标志
   * @return true 如果可以复活
   */
  public static boolean canRaise(int monsterFlags, int summonerFlags) {
    // Boss 和已被复活的尸体不能再次复活
    if (MonsterFlags.hasFlag(monsterFlags, MonsterFlags.BOSS)) {
      return false;
    }
    if (MonsterFlags.hasFlag(summonerFlags, MonsterFlags.SUMMONER_RAISED)) {
      return false;
    }
    // 检查怪物是否允许被复活
    return MonsterFlags.hasFlag(monsterFlags, MonsterFlags.CANRAISE);
  }

  //==========================================================================
  // 调试信息
  //==========================================================================

  /**
   * 生成怪物信息字符串（调试用）
   * 
   * @param monsterId 怪物ID
   * @param level 怪物等级
   * @param hp 当前生命值
   * @param maxHp 最大生命值
   * @return 信息字符串
   */
  public static String formatMonsterInfo(int monsterId, int level, int hp, int maxHp) {
    return String.format("Monster[id=%d, lv=%d, hp=%d/%d]", monsterId, level, hp, maxHp);
  }
}
