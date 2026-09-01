package com.riiablo.engine.server.party;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;

import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 队伍管理器 - 基于 D2MOD Party.cpp 移植
 * 
 * <p>管理所有队伍的创建、销毁、成员加入/离开等操作。
 * 
 * <p>参考：D2MOD/source/D2Game/src/UNIT/Party.cpp
 * 
 * @author riiablo team
 */
public class PartyManager {
  private static final Logger log = LogManager.getLogger(PartyManager.class);

  //==========================================================================
  // 常量
  //==========================================================================

  /** 最大队伍数量 */
  public static final int MAX_PARTIES = 64;

  //==========================================================================
  // 数据
  //==========================================================================

  /** 所有队伍（按队伍 ID 索引） */
  private final IntMap<Party> parties;
  
  /** 玩家所属队伍映射（玩家实体 ID -> 队伍 ID） */
  private final IntMap<Short> playerPartyMap;
  
  /** 玩家之间的关系映射（玩家A的ID * MAX + 玩家B的ID -> 关系） */
  private final IntMap<Integer> relationMap;
  
  /** 下一个可用的队伍 ID */
  private short nextPartyId;
  
  /** 用于关系映射的最大玩家 ID */
  private static final int MAX_PLAYER_ID = 10000;

  //==========================================================================
  // 邀请系统
  //==========================================================================

  /** 邀请数据：被邀请者 ID -> 邀请者 ID */
  private final IntMap<Integer> invitations;

  //==========================================================================
  // 构造函数
  //==========================================================================

  /**
   * 创建队伍管理器
   */
  public PartyManager() {
    this.parties = new IntMap<>(MAX_PARTIES);
    this.playerPartyMap = new IntMap<>(64);
    this.relationMap = new IntMap<>(256);
    this.invitations = new IntMap<>(32);
    this.nextPartyId = Party.MIN_PARTY_ID;
  }

  //==========================================================================
  // 队伍创建/销毁
  //==========================================================================

  /**
   * 创建新队伍
   * 
   * <p>对应 D2MOD sub_6FCB9C40
   * 
   * @param leaderId 队长实体 ID
   * @return 队伍 ID，失败返回 -1
   */
  public short createParty(int leaderId) {
    if (parties.size >= MAX_PARTIES) {
      log.warn("无法创建队伍：队伍数量已达上限");
      return Party.INVALID_ID;
    }
    
    // 检查玩家是否已在队伍中
    if (playerPartyMap.containsKey(leaderId)) {
      log.warn("无法创建队伍：玩家已在队伍中, entityId={}", leaderId);
      return Party.INVALID_ID;
    }
    
    // 分配新的队伍 ID
    short partyId = allocatePartyId();
    
    // 创建队伍
    Party party = new Party(partyId, leaderId);
    parties.put(partyId, party);
    
    // 创建队长的成员数据
    PartyMember leader = new PartyMember();
    leader.entityId = leaderId;
    leader.alive = true;
    leader.online = true;
    
    party.addMember(leader);
    playerPartyMap.put(leaderId, partyId);
    
    log.info("创建队伍: partyId={}, leaderId={}", partyId, leaderId);
    return partyId;
  }

  /**
   * 分配队伍 ID
   * 
   * @return 新的队伍 ID
   */
  private short allocatePartyId() {
    short id = nextPartyId;
    
    // 确保 ID 不冲突
    while (parties.containsKey(id)) {
      id++;
      if (id < Party.MIN_PARTY_ID) {
        id = Party.MIN_PARTY_ID;
      }
    }
    
    nextPartyId = (short)(id + 1);
    if (nextPartyId < Party.MIN_PARTY_ID) {
      nextPartyId = Party.MIN_PARTY_ID;
    }
    
    return id;
  }

  /**
   * 销毁队伍
   * 
   * @param partyId 队伍 ID
   */
  public void destroyParty(short partyId) {
    Party party = parties.remove(partyId);
    if (party == null) {
      return;
    }
    
    // 移除所有成员的队伍映射
    for (PartyMember member : party.getMembers()) {
      playerPartyMap.remove(member.entityId);
    }
    
    party.reset();
    log.info("销毁队伍: partyId={}", partyId);
  }

  //==========================================================================
  // 成员加入/离开
  //==========================================================================

  /**
   * 加入队伍
   * 
   * <p>对应 D2MOD sub_6FCB9D10
   * 
   * @param partyId 队伍 ID
   * @param entityId 玩家实体 ID
   * @return true 如果成功
   */
  public boolean joinParty(short partyId, int entityId) {
    Party party = parties.get(partyId);
    if (party == null || !party.isValid()) {
      log.warn("无法加入队伍：队伍不存在, partyId={}", partyId);
      return false;
    }
    
    // 检查是否已在其他队伍
    Short currentPartyId = playerPartyMap.get(entityId);
    if (currentPartyId != null) {
      if (currentPartyId == partyId) {
        return true; // 已在该队伍中
      }
      // 先离开当前队伍
      leaveParty(entityId);
    }
    
    // 创建成员数据
    PartyMember member = new PartyMember();
    member.entityId = entityId;
    member.alive = true;
    member.online = true;
    
    if (!party.addMember(member)) {
      log.warn("无法加入队伍：队伍已满, partyId={}", partyId);
      return false;
    }
    
    playerPartyMap.put(entityId, partyId);
    
    // 清除与队伍成员的敌对状态
    for (PartyMember other : party.getMembers()) {
      if (other.entityId != entityId) {
        // getRelation() intentionally reports PARTY_MEMBER once joined, so
        // remove the bilateral stale player-list flags directly.
        setRelation(entityId, other.entityId, PartyRelation.NONE);
        setRelation(other.entityId, entityId, PartyRelation.NONE);
      }
    }
    
    log.info("玩家加入队伍: entityId={}, partyId={}", entityId, partyId);
    return true;
  }

  /**
   * 离开队伍
   * 
   * <p>对应 D2MOD PARTY_LeaveParty
   * 
   * @param entityId 玩家实体 ID
   */
  public void leaveParty(int entityId) {
    Short partyId = playerPartyMap.remove(entityId);
    if (partyId == null) {
      return;
    }
    
    Party party = parties.get(partyId);
    if (party == null) {
      return;
    }
    
    for (PartyMember other : party.getMembers()) {
      if (other != null && other.entityId != entityId) {
        setRelation(entityId, other.entityId, PartyRelation.NONE);
        setRelation(other.entityId, entityId, PartyRelation.NONE);
      }
    }
    party.removeMember(entityId);
    
    log.info("玩家离开队伍: entityId={}, partyId={}", entityId, partyId);
    
    // 如果队伍只剩 1 人或更少，解散队伍
    if (party.getMemberCount() <= 1) {
      destroyParty(partyId);
    }
  }

  /** Updates the authoritative runtime snapshot used by party XP and quests. */
  public void updateMember(int entityId, int level, int hp, int maxHp,
      int mana, int maxMana, int levelId, int x, int y, boolean alive) {
    Party party = getPartyForPlayer(entityId);
    if (party == null) return;
    PartyMember member = party.getMember(entityId);
    if (member == null) return;
    member.level = Math.max(1, level);
    member.online = true;
    member.update(hp, maxHp, mana, maxMana, levelId, x, y, alive);
  }

  /** Marks a member online/offline without changing party membership. */
  public void setOnline(int entityId, boolean online) {
    Party party = getPartyForPlayer(entityId);
    if (party == null) return;
    PartyMember member = party.getMember(entityId);
    if (member != null) member.online = online;
  }

  /**
   * Removes all runtime state owned by a player connection.
   *
   * <p>D2GS entity ids are connection-scoped, so invitations, asymmetric
   * hostility and party membership must not survive deletion and attach to a
   * later entity that reuses the same id.</p>
   */
  public void removePlayer(int entityId) {
    declineInvitation(entityId);

    IntArray invitedPlayers = new IntArray();
    for (IntMap.Entry<Integer> invitation : invitations.entries()) {
      if (invitation.value != null && invitation.value == entityId) {
        invitedPlayers.add(invitation.key);
      }
    }
    for (int i = 0; i < invitedPlayers.size; i++) {
      cancelInvitation(entityId, invitedPlayers.get(i));
    }

    leaveParty(entityId);

    IntArray relationKeys = new IntArray();
    for (IntMap.Entry<Integer> relation : relationMap.entries()) {
      int source = relation.key / MAX_PLAYER_ID;
      int target = relation.key % MAX_PLAYER_ID;
      if (source == entityId || target == entityId) relationKeys.add(relation.key);
    }
    for (int i = 0; i < relationKeys.size; i++) relationMap.remove(relationKeys.get(i));
  }

  //==========================================================================
  // 邀请系统
  //==========================================================================

  /**
   * 发送队伍邀请
   * 
   * @param inviterId 邀请者实体 ID
   * @param inviteeId 被邀请者实体 ID
   * @return true 如果成功
   */
  public boolean sendInvitation(int inviterId, int inviteeId) {
    // 检查被邀请者是否已在队伍中
    if (playerPartyMap.containsKey(inviteeId)) {
      log.warn("无法邀请：目标已在队伍中, inviteeId={}", inviteeId);
      return false;
    }
    
    // 检查是否已有邀请
    if (invitations.containsKey(inviteeId)) {
      log.warn("无法邀请：目标已有待处理的邀请, inviteeId={}", inviteeId);
      return false;
    }
    
    // 确保邀请者有队伍
    Short partyId = playerPartyMap.get(inviterId);
    if (partyId == null) {
      // 创建新队伍
      partyId = createParty(inviterId);
      if (partyId == Party.INVALID_ID) {
        return false;
      }
    }
    
    // 检查队伍是否已满
    Party party = parties.get(partyId);
    if (party != null && party.isFull()) {
      log.warn("无法邀请：队伍已满, partyId={}", partyId);
      return false;
    }
    
    // 记录邀请
    invitations.put(inviteeId, inviterId);
    
    // 设置关系
    setRelation(inviterId, inviteeId, PartyRelation.INVITER);
    setRelation(inviteeId, inviterId, PartyRelation.INVITED);
    
    log.info("发送队伍邀请: inviterId={}, inviteeId={}", inviterId, inviteeId);
    return true;
  }

  /**
   * 接受队伍邀请
   * 
   * @param inviteeId 被邀请者实体 ID
   * @return true 如果成功
   */
  public boolean acceptInvitation(int inviteeId) {
    Integer inviterId = invitations.remove(inviteeId);
    if (inviterId == null) {
      log.warn("无法接受邀请：没有待处理的邀请, inviteeId={}", inviteeId);
      return false;
    }
    
    // 清除关系
    setRelation(inviterId, inviteeId, PartyRelation.NONE);
    setRelation(inviteeId, inviterId, PartyRelation.NONE);
    
    // 获取邀请者的队伍
    Short partyId = playerPartyMap.get(inviterId);
    if (partyId == null) {
      log.warn("无法接受邀请：邀请者不在队伍中, inviterId={}", inviterId);
      return false;
    }
    
    // 加入队伍
    return joinParty(partyId, inviteeId);
  }

  /**
   * 拒绝队伍邀请
   * 
   * @param inviteeId 被邀请者实体 ID
   */
  public void declineInvitation(int inviteeId) {
    Integer inviterId = invitations.remove(inviteeId);
    if (inviterId != null) {
      // 清除关系
      setRelation(inviterId, inviteeId, PartyRelation.NONE);
      setRelation(inviteeId, inviterId, PartyRelation.NONE);
      
      log.info("拒绝队伍邀请: inviterId={}, inviteeId={}", inviterId, inviteeId);
    }
  }

  /**
   * 取消队伍邀请
   * 
   * @param inviterId 邀请者实体 ID
   * @param inviteeId 被邀请者实体 ID
   */
  public void cancelInvitation(int inviterId, int inviteeId) {
    Integer storedInviterId = invitations.get(inviteeId);
    if (storedInviterId != null && storedInviterId == inviterId) {
      invitations.remove(inviteeId);
      
      // 清除关系
      setRelation(inviterId, inviteeId, PartyRelation.NONE);
      setRelation(inviteeId, inviterId, PartyRelation.NONE);
      
      log.info("取消队伍邀请: inviterId={}, inviteeId={}", inviterId, inviteeId);
    }
  }

  /** Returns the authoritative inviter for an invitee, or {@code -1}. */
  public int getInviter(int inviteeId) {
    Integer inviterId = invitations.get(inviteeId);
    return inviterId == null ? -1 : inviterId;
  }

  //==========================================================================
  // 敌对系统
  //==========================================================================

  /**
   * 声明敌对
   * 
   * @param attackerId 发起者实体 ID
   * @param targetId 目标实体 ID
   * @return true 如果成功
   */
  public boolean declareHostility(int attackerId, int targetId) {
    // 不能对队友敌对
    if (areInSameParty(attackerId, targetId)) {
      log.warn("无法敌对队友: attackerId={}, targetId={}", attackerId, targetId);
      return false;
    }
    
    // D2MOO FRIENDLY_OpenHostility toggles the player-list flag in both
    // directions so the challenged player may retaliate immediately and both
    // clients render the same hostile relation.
    setRelation(attackerId, targetId, PartyRelation.HOSTILE);
    setRelation(targetId, attackerId, PartyRelation.HOSTILE);
    log.info("声明敌对: attackerId={}, targetId={}", attackerId, targetId);
    return true;
  }

  /**
   * 移除敌对
   * 
   * @param entityId1 玩家 1 实体 ID
   * @param entityId2 玩家 2 实体 ID
   */
  public void removeHostility(int entityId1, int entityId2) {
    if (getRelation(entityId1, entityId2) == PartyRelation.HOSTILE
        || getRelation(entityId2, entityId1) == PartyRelation.HOSTILE) {
      setRelation(entityId1, entityId2, PartyRelation.NONE);
      setRelation(entityId2, entityId1, PartyRelation.NONE);
    }
  }

  /** Returns the bilateral D2MOO hostility flag, excluding party members. */
  public boolean areHostile(int entityId1, int entityId2) {
    if (entityId1 == entityId2 || areInSameParty(entityId1, entityId2)) return false;
    return getRelation(entityId1, entityId2) == PartyRelation.HOSTILE
        || getRelation(entityId2, entityId1) == PartyRelation.HOSTILE;
  }

  //==========================================================================
  // 关系管理
  //==========================================================================

  /**
   * 设置玩家关系
   * 
   * @param entityId1 玩家 1 实体 ID
   * @param entityId2 玩家 2 实体 ID
   * @param relation 关系类型
   */
  public void setRelation(int entityId1, int entityId2, int relation) {
    int key = entityId1 * MAX_PLAYER_ID + entityId2;
    if (relation == PartyRelation.NONE) {
      relationMap.remove(key);
    } else {
      relationMap.put(key, relation);
    }
  }

  /**
   * 获取玩家关系
   * 
   * @param entityId1 玩家 1 实体 ID
   * @param entityId2 玩家 2 实体 ID
   * @return 关系类型
   */
  public int getRelation(int entityId1, int entityId2) {
    // 检查是否在同一队伍
    if (areInSameParty(entityId1, entityId2)) {
      return PartyRelation.PARTY_MEMBER;
    }
    
    int key = entityId1 * MAX_PLAYER_ID + entityId2;
    return relationMap.get(key, PartyRelation.NONE);
  }

  /**
   * 检查两个玩家是否在同一队伍
   * 
   * @param entityId1 玩家 1 实体 ID
   * @param entityId2 玩家 2 实体 ID
   * @return true 如果在同一队伍
   */
  public boolean areInSameParty(int entityId1, int entityId2) {
    Short party1 = playerPartyMap.get(entityId1);
    Short party2 = playerPartyMap.get(entityId2);
    return party1 != null && party1.equals(party2);
  }

  //==========================================================================
  // 金币/经验共享
  //==========================================================================

  /**
   * 计算金币共享分配
   * 
   * <p>对应 D2MOD PARTY_ShareGoldDrop
   * 
   * @param entityId 拾取者实体 ID
   * @param levelId 当前场景 ID
   * @param goldValue 金币总量
   * @return 每人应得金币（数组：[0]=拾取者，[1...n]=其他成员）
   */
  public int[] calculateGoldShare(int entityId, int levelId, int goldValue) {
    Short partyId = playerPartyMap.get(entityId);
    if (partyId == null) {
      return new int[] { goldValue }; // 不在队伍中，全部归自己
    }
    
    Party party = parties.get(partyId);
    if (party == null) {
      return new int[] { goldValue };
    }
    
    // 获取同场景的存活成员数量
    int livingMembers = party.getLivingMembersInLevel(levelId);
    if (livingMembers <= 1) {
      return new int[] { goldValue };
    }
    
    // 平均分配
    int sharePerMember = goldValue / livingMembers;
    int remainder = goldValue % livingMembers;
    
    int[] shares = new int[livingMembers];
    for (int i = 0; i < livingMembers; i++) {
      shares[i] = sharePerMember;
    }
    shares[0] += remainder; // 余数归拾取者
    
    return shares;
  }

  /**
   * 计算经验共享
   * 
   * <p>经验根据等级加权分配
   * 
   * @param entityId 击杀者实体 ID
   * @param levelId 当前场景 ID
   * @param experience 经验总量
   * @param memberLevels 各成员等级（输出参数）
   * @return 每人应得经验
   */
  public int[] calculateExpShare(int entityId, int levelId, int experience, 
                                  Array<Integer> memberLevels) {
    Short partyId = playerPartyMap.get(entityId);
    if (partyId == null) {
      return new int[] { experience };
    }
    
    Party party = parties.get(partyId);
    if (party == null) {
      return new int[] { experience };
    }
    
    // 获取同场景的存活成员
    Array<PartyMember> membersInLevel = new Array<>();
    party.getMembersInLevel(levelId, membersInLevel);
    
    int livingCount = 0;
    int totalLevel = 0;
    
    for (PartyMember member : membersInLevel) {
      if (member.alive) {
        livingCount++;
        totalLevel += member.level;
        if (memberLevels != null) {
          memberLevels.add(member.level);
        }
      }
    }
    
    if (livingCount <= 1 || totalLevel <= 0) {
      return new int[] { experience };
    }
    
    // 按等级加权分配
    int[] shares = new int[livingCount];
    int idx = 0;
    int distributed = 0;
    
    for (PartyMember member : membersInLevel) {
      if (member.alive) {
        int share = experience * member.level / totalLevel;
        shares[idx++] = share;
        distributed += share;
      }
    }
    
    // 余数归击杀者
    if (shares.length > 0) {
      shares[0] += experience - distributed;
    }
    
    return shares;
  }

  //==========================================================================
  // 查询方法
  //==========================================================================

  /**
   * 获取玩家所属队伍 ID
   * 
   * <p>对应 D2MOD PARTY_GetPartyIdForUnitOwner
   * 
   * @param entityId 玩家实体 ID
   * @return 队伍 ID，不在队伍中返回 -1
   */
  public short getPartyId(int entityId) {
    Short partyId = playerPartyMap.get(entityId);
    return partyId != null ? partyId : Party.INVALID_ID;
  }

  /**
   * 获取队伍
   * 
   * @param partyId 队伍 ID
   * @return 队伍实例，不存在返回 null
   */
  public Party getParty(short partyId) {
    return parties.get(partyId);
  }

  /**
   * 获取玩家所属队伍
   * 
   * @param entityId 玩家实体 ID
   * @return 队伍实例，不存在返回 null
   */
  public Party getPartyForPlayer(int entityId) {
    Short partyId = playerPartyMap.get(entityId);
    if (partyId == null) {
      return null;
    }
    return parties.get(partyId);
  }

  /**
   * 获取同场景的存活队伍成员数量
   * 
   * <p>对应 D2MOD PARTY_GetLivingPartyMemberCountInSameLevel
   * 
   * @param entityId 玩家实体 ID
   * @param levelId 场景 ID
   * @return 存活成员数量
   */
  public int getLivingPartyMembersInLevel(int entityId, int levelId) {
    Party party = getPartyForPlayer(entityId);
    if (party == null) {
      return 1; // 只有自己
    }
    return party.getLivingMembersInLevel(levelId);
  }

  /**
   * 获取所有队伍
   * 
   * @return 队伍映射
   */
  public IntMap<Party> getParties() {
    return parties;
  }

  /**
   * 清空所有数据
   */
  public void clear() {
    for (Party party : parties.values()) {
      party.reset();
    }
    parties.clear();
    playerPartyMap.clear();
    relationMap.clear();
    invitations.clear();
    nextPartyId = Party.MIN_PARTY_ID;
  }
}
