package com.riiablo.engine.server.party;

/**
 * 队伍工具类 - 基于 D2MOO Party.cpp 移植
 * 
 * <p>提供队伍相关的辅助计算方法。
 * 
 * <p>参考：D2MOO/source/D2Game/src/UNIT/Party.cpp
 * 
 * @author riiablo team
 */
public final class PartyUtil {
  private PartyUtil() {} // 不可实例化

  //==========================================================================
  // 经验共享
  //==========================================================================

  /** 经验共享范围（子格） - 约 2 屏 */
  public static final int EXP_SHARE_RANGE = 640;
  
  /** 经验惩罚：等级差阈值 */
  public static final int EXP_LEVEL_DIFF_THRESHOLD = 10;
  
  /** 经验惩罚：每级差值的惩罚百分比 */
  public static final int EXP_PENALTY_PER_LEVEL = 5;

  /**
   * 计算队伍经验加成
   * 
   * <p>队伍成员越多，总经验越多
   * 
   * @param memberCount 队伍成员数量
   * @return 经验加成百分比（100 = 无加成）
   */
  public static int calculateExpBonus(int memberCount) {
    if (memberCount <= 1) {
      return 100; // 无加成
    }
    
    // 每增加一个成员 +35% 经验（D2 原版规则）
    return 100 + (memberCount - 1) * 35;
  }

  /**
   * 计算经验共享后单人获得的经验
   * 
   * @param baseExp 击杀获得的基础经验
   * @param memberCount 同场景队伍成员数量
   * @param playerLevel 该玩家等级
   * @param totalLevel 所有成员等级之和
   * @return 该玩家获得的经验
   */
  public static int calculateSharedExp(int baseExp, int memberCount, 
                                        int playerLevel, int totalLevel) {
    if (memberCount <= 1 || totalLevel <= 0) {
      return baseExp;
    }
    
    // 先应用队伍加成
    int bonusedExp = baseExp * calculateExpBonus(memberCount) / 100;
    
    // 然后按等级比例分配
    return bonusedExp * playerLevel / totalLevel;
  }

  /**
   * 计算等级差惩罚
   * 
   * <p>等级差距过大时，经验会被惩罚
   * 
   * @param killerLevel 击杀者等级
   * @param monsterLevel 怪物等级
   * @return 经验保留百分比（0-100）
   */
  public static int calculateLevelPenalty(int killerLevel, int monsterLevel) {
    int diff = Math.abs(killerLevel - monsterLevel);
    
    if (diff <= EXP_LEVEL_DIFF_THRESHOLD) {
      return 100; // 无惩罚
    }
    
    int penalty = (diff - EXP_LEVEL_DIFF_THRESHOLD) * EXP_PENALTY_PER_LEVEL;
    return Math.max(0, 100 - penalty);
  }

  //==========================================================================
  // 金币共享
  //==========================================================================

  /**
   * 计算金币共享分配
   * 
   * <p>对应 D2MOO PARTY_CalculatePickAndDrop
   * 
   * @param goldValue 金币总量
   * @param currentGold 玩家当前金币
   * @param goldLimit 玩家金币上限
   * @return int[2]: [0]=可拾取量, [1]=掉落量
   */
  public static int[] calculateGoldPickAndDrop(int goldValue, int currentGold, int goldLimit) {
    int[] result = new int[2];
    int available = goldLimit - currentGold;
    
    if (goldValue > available) {
      result[0] = available; // 可拾取量
      result[1] = goldValue - available; // 掉落量
    } else {
      result[0] = goldValue;
      result[1] = 0;
    }
    
    return result;
  }

  /**
   * 计算玩家金币上限
   * 
   * @param level 玩家等级
   * @return 金币上限
   */
  public static int calculateGoldLimit(int level) {
    // D2 原版公式：10000 * level
    return 10000 * level;
  }

  //==========================================================================
  // 距离检查
  //==========================================================================

  /**
   * 检查两个位置是否在经验共享范围内
   * 
   * @param x1 位置 1 X
   * @param y1 位置 1 Y
   * @param x2 位置 2 X
   * @param y2 位置 2 Y
   * @return true 如果在范围内
   */
  public static boolean isInExpShareRange(int x1, int y1, int x2, int y2) {
    int dx = x2 - x1;
    int dy = y2 - y1;
    int distSq = dx * dx + dy * dy;
    return distSq <= EXP_SHARE_RANGE * EXP_SHARE_RANGE;
  }

  //==========================================================================
  // 队伍 UI 辅助
  //==========================================================================

  /**
   * 获取关系对应的颜色索引
   * 
   * @param relation 关系类型
   * @return 颜色索引（用于 UI 显示）
   */
  public static int getRelationColor(int relation) {
    switch (relation) {
      case PartyRelation.PARTY_MEMBER:
        return 0x00FF00; // 绿色 - 队友
      case PartyRelation.HOSTILE:
        return 0xFF0000; // 红色 - 敌对
      case PartyRelation.INVITED:
      case PartyRelation.INVITER:
        return 0xFFFF00; // 黄色 - 邀请中
      case PartyRelation.IGNORED:
        return 0x808080; // 灰色 - 忽略
      default:
        return 0xFFFFFF; // 白色 - 普通
    }
  }

  /**
   * 获取职业名称
   * 
   * @param classId 职业 ID
   * @return 职业名称
   */
  public static String getClassName(int classId) {
    switch (classId) {
      case 0: return "Amazon";
      case 1: return "Sorceress";
      case 2: return "Necromancer";
      case 3: return "Paladin";
      case 4: return "Barbarian";
      case 5: return "Druid";
      case 6: return "Assassin";
      default: return "Unknown";
    }
  }

  /**
   * 获取职业简称
   * 
   * @param classId 职业 ID
   * @return 职业简称（3 字符）
   */
  public static String getClassAbbr(int classId) {
    switch (classId) {
      case 0: return "AMA";
      case 1: return "SOR";
      case 2: return "NEC";
      case 3: return "PAL";
      case 4: return "BAR";
      case 5: return "DRU";
      case 6: return "ASN";
      default: return "???";
    }
  }
}
