package com.riiablo.engine.server.quest;

/**
 * 任务运行时数据 - 基于 D2MOO D2QuestDataStrc 移植
 * 
 * <p>存储任务的运行时状态和数据。
 * 
 * <p>参考：D2MOO/source/D2Game/include/QUESTS/Quests.h
 * 
 * @author riiablo team
 */
public class QuestData {

  //==========================================================================
  // 任务基础信息
  //==========================================================================

  /** 任务 ID（对应 QuestId 常量） */
  public int questId;
  
  /** 任务所属幕数（1-5） */
  public int actNo;
  
  /** 是否是主线任务（非序章） */
  public boolean isMainQuest;

  //==========================================================================
  // 任务状态
  //==========================================================================

  /** 任务是否激活 */
  public boolean active;
  
  /** 当前任务状态 */
  public int state;
  
  /** 上一个任务状态 */
  public int lastState;
  
  /** 任务标志位（组合 QuestState.FLAG_* 常量） */
  public int flags;

  //==========================================================================
  // 任务进度
  //==========================================================================

  /** 任务序列 ID */
  public int seqId;
  
  /** 任务过滤器索引 */
  public int filterIndex;

  //==========================================================================
  // 玩家追踪
  //==========================================================================

  /** 参与此任务的玩家 GUID 列表 */
  public int[] playerGUIDs = new int[32];
  
  /** 参与玩家数量 */
  public int playerCount;

  //==========================================================================
  // 构造函数
  //==========================================================================

  /**
   * 创建任务数据
   */
  public QuestData() {
    reset();
  }

  /**
   * 创建任务数据并初始化
   * 
   * @param questId 任务 ID
   */
  public QuestData(int questId) {
    this();
    init(questId);
  }

  //==========================================================================
  // 初始化和重置
  //==========================================================================

  /**
   * 初始化任务数据
   * 
   * @param questId 任务 ID
   */
  public void init(int questId) {
    this.questId = questId;
    this.actNo = QuestId.getAct(questId);
    this.isMainQuest = true;
    this.active = false;
    this.state = QuestState.NOT_STARTED;
    this.lastState = QuestState.NOT_STARTED;
    this.flags = 0;
    this.seqId = questId;
    this.filterIndex = questId;
    this.playerCount = 0;
  }

  /**
   * 重置任务数据
   */
  public void reset() {
    questId = -1;
    actNo = 0;
    isMainQuest = true;
    active = false;
    state = QuestState.NOT_STARTED;
    lastState = QuestState.NOT_STARTED;
    flags = 0;
    seqId = 0;
    filterIndex = 0;
    for (int i = 0; i < playerGUIDs.length; i++) {
      playerGUIDs[i] = -1;
    }
    playerCount = 0;
  }

  //==========================================================================
  // 状态管理
  //==========================================================================

  /**
   * 设置任务状态
   * 
   * @param newState 新状态
   */
  public void setState(int newState) {
    if (newState != state) {
      lastState = state;
      state = newState;
    }
  }

  /**
   * 设置任务标志
   * 
   * @param flag 要设置的标志
   */
  public void setFlag(int flag) {
    flags = QuestState.setFlag(flags, flag);
  }

  /**
   * 清除任务标志
   * 
   * @param flag 要清除的标志
   */
  public void clearFlag(int flag) {
    flags = QuestState.clearFlag(flags, flag);
  }

  /**
   * 检查是否有指定标志
   * 
   * @param flag 要检查的标志
   * @return true 如果有标志
   */
  public boolean hasFlag(int flag) {
    return QuestState.hasFlag(flags, flag);
  }

  /**
   * 检查任务是否已完成
   * 
   * @return true 如果已完成
   */
  public boolean isCompleted() {
    return state == QuestState.COMPLETED || hasFlag(QuestState.FLAG_COMPLETED);
  }

  /**
   * 检查任务是否正在进行
   * 
   * @return true 如果正在进行
   */
  public boolean isInProgress() {
    return active && !isCompleted();
  }

  /**
   * 完成任务
   */
  public void complete() {
    setState(QuestState.COMPLETED);
    setFlag(QuestState.FLAG_COMPLETED);
    active = false;
  }

  //==========================================================================
  // 玩家管理
  //==========================================================================

  /**
   * 添加玩家 GUID
   * 
   * @param guid 玩家 GUID
   * @return true 如果成功添加
   */
  public boolean addPlayerGUID(int guid) {
    // 检查是否已存在
    for (int i = 0; i < playerCount; i++) {
      if (playerGUIDs[i] == guid) {
        return false;
      }
    }
    
    // 添加新玩家
    if (playerCount < playerGUIDs.length) {
      playerGUIDs[playerCount++] = guid;
      return true;
    }
    
    return false;
  }

  /**
   * 移除玩家 GUID
   * 
   * @param guid 玩家 GUID
   * @return true 如果成功移除
   */
  public boolean removePlayerGUID(int guid) {
    for (int i = 0; i < playerCount; i++) {
      if (playerGUIDs[i] == guid) {
        // 用最后一个元素填充空位
        playerGUIDs[i] = playerGUIDs[--playerCount];
        playerGUIDs[playerCount] = -1;
        return true;
      }
    }
    return false;
  }

  /**
   * 检查玩家是否参与此任务
   * 
   * @param guid 玩家 GUID
   * @return true 如果参与
   */
  public boolean hasPlayerGUID(int guid) {
    for (int i = 0; i < playerCount; i++) {
      if (playerGUIDs[i] == guid) {
        return true;
      }
    }
    return false;
  }

  /**
   * 清除所有玩家
   */
  public void clearPlayers() {
    for (int i = 0; i < playerCount; i++) {
      playerGUIDs[i] = -1;
    }
    playerCount = 0;
  }

  //==========================================================================
  // 调试信息
  //==========================================================================

  @Override
  public String toString() {
    return "QuestData{" +
        "id=" + questId +
        ", name=" + QuestId.getName(questId) +
        ", act=" + actNo +
        ", state=" + QuestState.getName(state) +
        ", flags=0x" + Integer.toHexString(flags) +
        ", active=" + active +
        ", players=" + playerCount +
        '}';
  }
}
