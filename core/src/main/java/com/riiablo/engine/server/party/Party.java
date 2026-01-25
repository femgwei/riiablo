package com.riiablo.engine.server.party;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;

/**
 * 队伍实例 - 基于 D2MOO D2PartyStrc 移植
 * 
 * <p>代表一个游戏内的队伍，管理队伍成员。
 * 
 * <p>参考：D2MOO/source/D2Game/include/UNIT/Party.h
 * 
 * @author riiablo team
 */
public class Party {

  //==========================================================================
  // 常量
  //==========================================================================

  /** 最大队伍人数 */
  public static final int MAX_MEMBERS = 8;
  
  /** 无效队伍 ID */
  public static final short INVALID_ID = -1;
  
  /** 最小有效队伍 ID */
  public static final short MIN_PARTY_ID = 3;

  //==========================================================================
  // 队伍数据
  //==========================================================================

  /** 队伍 ID */
  private short partyId;
  
  /** 队长实体 ID */
  private int leaderId;
  
  /** 队伍成员列表 */
  private final Array<PartyMember> members;
  
  /** 成员 ID 快速查找 */
  private final IntMap<PartyMember> memberMap;
  
  /** 队伍是否有效 */
  private boolean valid;

  //==========================================================================
  // 构造函数
  //==========================================================================

  /**
   * 创建空队伍
   */
  public Party() {
    this.partyId = INVALID_ID;
    this.leaderId = -1;
    this.members = new Array<>(MAX_MEMBERS);
    this.memberMap = new IntMap<>(MAX_MEMBERS);
    this.valid = false;
  }

  /**
   * 创建队伍
   * 
   * @param partyId 队伍 ID
   * @param leaderId 队长实体 ID
   */
  public Party(short partyId, int leaderId) {
    this.partyId = partyId;
    this.leaderId = leaderId;
    this.members = new Array<>(MAX_MEMBERS);
    this.memberMap = new IntMap<>(MAX_MEMBERS);
    this.valid = true;
  }

  //==========================================================================
  // 队伍管理
  //==========================================================================

  /**
   * 初始化队伍
   * 
   * @param partyId 队伍 ID
   * @param leaderId 队长实体 ID
   */
  public void init(short partyId, int leaderId) {
    this.partyId = partyId;
    this.leaderId = leaderId;
    this.members.clear();
    this.memberMap.clear();
    this.valid = true;
  }

  /**
   * 重置队伍
   */
  public void reset() {
    this.partyId = INVALID_ID;
    this.leaderId = -1;
    this.members.clear();
    this.memberMap.clear();
    this.valid = false;
  }

  //==========================================================================
  // 成员管理
  //==========================================================================

  /**
   * 添加成员到队伍
   * 
   * @param member 成员数据
   * @return true 如果添加成功
   */
  public boolean addMember(PartyMember member) {
    if (!valid || member == null) {
      return false;
    }
    
    // 检查是否已满
    if (members.size >= MAX_MEMBERS) {
      return false;
    }
    
    // 检查是否已存在
    if (memberMap.containsKey(member.entityId)) {
      return false;
    }
    
    members.add(member);
    memberMap.put(member.entityId, member);
    
    // 第一个成员成为队长
    if (members.size == 1) {
      leaderId = member.entityId;
    }
    
    return true;
  }

  /**
   * 从队伍移除成员
   * 
   * @param entityId 成员实体 ID
   * @return true 如果移除成功
   */
  public boolean removeMember(int entityId) {
    if (!valid) {
      return false;
    }
    
    PartyMember member = memberMap.remove(entityId);
    if (member == null) {
      return false;
    }
    
    members.removeValue(member, true);
    
    // 如果移除的是队长，转让队长
    if (leaderId == entityId && members.size > 0) {
      leaderId = members.first().entityId;
    }
    
    return true;
  }

  /**
   * 检查是否包含成员
   * 
   * @param entityId 实体 ID
   * @return true 如果包含
   */
  public boolean hasMember(int entityId) {
    return memberMap.containsKey(entityId);
  }

  /**
   * 获取成员
   * 
   * @param entityId 实体 ID
   * @return 成员数据，不存在返回 null
   */
  public PartyMember getMember(int entityId) {
    return memberMap.get(entityId);
  }

  /**
   * 获取成员数量
   * 
   * @return 成员数量
   */
  public int getMemberCount() {
    return members.size;
  }

  /**
   * 获取所有成员
   * 
   * @return 成员列表
   */
  public Array<PartyMember> getMembers() {
    return members;
  }

  /**
   * 检查队伍是否已满
   * 
   * @return true 如果已满
   */
  public boolean isFull() {
    return members.size >= MAX_MEMBERS;
  }

  /**
   * 检查队伍是否为空
   * 
   * @return true 如果为空
   */
  public boolean isEmpty() {
    return members.size == 0;
  }

  //==========================================================================
  // 同场景成员查询
  //==========================================================================

  /**
   * 获取同一场景的存活成员数量
   * 
   * @param levelId 场景 ID
   * @return 存活成员数量
   */
  public int getLivingMembersInLevel(int levelId) {
    int count = 0;
    for (PartyMember member : members) {
      if (member.isValid() && member.alive && member.levelId == levelId) {
        count++;
      }
    }
    return count;
  }

  /**
   * 获取同一场景的所有成员
   * 
   * @param levelId 场景 ID
   * @param result 输出数组
   * @return 成员数量
   */
  public int getMembersInLevel(int levelId, Array<PartyMember> result) {
    result.clear();
    for (PartyMember member : members) {
      if (member.isValid() && member.levelId == levelId) {
        result.add(member);
      }
    }
    return result.size;
  }

  //==========================================================================
  // Getter/Setter
  //==========================================================================

  public short getPartyId() {
    return partyId;
  }

  public int getLeaderId() {
    return leaderId;
  }

  public void setLeaderId(int leaderId) {
    this.leaderId = leaderId;
  }

  public boolean isValid() {
    return valid;
  }

  /**
   * 检查指定成员是否为队长
   * 
   * @param entityId 实体 ID
   * @return true 如果是队长
   */
  public boolean isLeader(int entityId) {
    return leaderId == entityId;
  }

  //==========================================================================
  // 调试信息
  //==========================================================================

  @Override
  public String toString() {
    return "Party{" +
        "partyId=" + partyId +
        ", leaderId=" + leaderId +
        ", memberCount=" + members.size +
        ", valid=" + valid +
        '}';
  }
}
