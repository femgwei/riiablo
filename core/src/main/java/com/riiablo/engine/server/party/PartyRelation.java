package com.riiablo.engine.server.party;

/**
 * 队伍关系类型 - 基于 D2MOO 移植
 * 
 * <p>定义了玩家之间的各种关系状态。
 * 
 * <p>参考：D2MOO/source/D2Game/src/UNIT/Friendly.cpp
 * 
 * @author riiablo team
 */
public final class PartyRelation {
  private PartyRelation() {} // 不可实例化

  //==========================================================================
  // 关系类型常量
  //==========================================================================

  /** 无关系（陌生人） */
  public static final int NONE = 0;
  
  /** 同一队伍成员 */
  public static final int PARTY_MEMBER = 1;
  
  /** 敌对状态 */
  public static final int HOSTILE = 2;
  
  /** 邀请待确认 */
  public static final int INVITED = 3;
  
  /** 等待对方接受邀请 */
  public static final int INVITER = 4;
  
  /** 忽略玩家 */
  public static final int IGNORED = 5;
  
  /** 可以抢夺尸体（PK 死亡后） */
  public static final int LOOT_CORPSE = 6;

  //==========================================================================
  // 敌对标志
  //==========================================================================

  /** 敌对标志：发起敌对 */
  public static final int HOSTILITY_DECLARED = 0x01;
  
  /** 敌对标志：接受敌对 */
  public static final int HOSTILITY_ACCEPTED = 0x02;
  
  /** 敌对标志：可以 PK */
  public static final int HOSTILITY_PVP = 0x04;

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 检查关系是否有效
   * 
   * @param relation 关系类型
   * @return true 如果有效
   */
  public static boolean isValid(int relation) {
    return relation >= NONE && relation <= LOOT_CORPSE;
  }

  /**
   * 获取关系名称
   * 
   * @param relation 关系类型
   * @return 关系名称
   */
  public static String getName(int relation) {
    switch (relation) {
      case NONE: return "None";
      case PARTY_MEMBER: return "Party Member";
      case HOSTILE: return "Hostile";
      case INVITED: return "Invited";
      case INVITER: return "Inviter";
      case IGNORED: return "Ignored";
      case LOOT_CORPSE: return "Loot Corpse";
      default: return "Unknown";
    }
  }

  /**
   * 检查是否为友好关系
   * 
   * @param relation 关系类型
   * @return true 如果友好
   */
  public static boolean isFriendly(int relation) {
    return relation == PARTY_MEMBER;
  }

  /**
   * 检查是否为敌对关系
   * 
   * @param relation 关系类型
   * @return true 如果敌对
   */
  public static boolean isHostile(int relation) {
    return relation == HOSTILE;
  }

  /**
   * 检查是否可以攻击
   * 
   * @param relation 关系类型
   * @return true 如果可以攻击
   */
  public static boolean canAttack(int relation) {
    return relation == HOSTILE || relation == NONE;
  }

  /**
   * 检查是否可以拾取尸体
   * 
   * @param relation 关系类型
   * @return true 如果可以拾取
   */
  public static boolean canLootCorpse(int relation) {
    return relation == LOOT_CORPSE;
  }
}
