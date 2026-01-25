package com.riiablo.engine.server.monster;

import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.engine.server.ai.AiContext;

/**
 * 怪物运行时数据 - 基于 D2MOO D2MonsterDataStrc 移植
 * 
 * <p>存储怪物的运行时状态和数据，包括：
 * <ul>
 *   <li>怪物配置表引用</li>
 *   <li>AI 状态和控制</li>
 *   <li>召唤者标志</li>
 *   <li>组件索引</li>
 *   <li>所属关卡ID</li>
 * </ul>
 * 
 * <p>参考：D2MOO/source/D2Common/include/D2Structs.h D2MonsterDataStrc
 * 
 * @author riiablo team
 */
public class MonsterData {

  //==========================================================================
  // 配置表引用
  //==========================================================================

  /** MonStats.txt 记录 */
  public MonStats.Entry monStats;
  
  /** MonStats2.txt 记录 */
  public MonStats2.Entry monStats2;

  //==========================================================================
  // AI 数据
  //==========================================================================

  /** AI 状态 */
  public int aiState;
  
  /** AI 上下文 */
  public AiContext aiContext;
  
  /** AI 目标实体ID */
  public int aiTargetId = -1;
  
  /** AI 目标类型 */
  public int aiTargetType;
  
  /** AI 延迟计时器（帧数） */
  public int aiDelay;

  //==========================================================================
  // 召唤者数据
  //==========================================================================

  /** 召唤者标志（组合 MonsterFlags.SUMMONER_* 常量） */
  public int summonerFlags;
  
  /** 主人实体ID（如果是召唤物或雇佣兵） */
  public int ownerId = -1;
  
  /** 链接的怪物ID（如堕落者和萨满的关联） */
  public int linkedMonsterId = -1;

  //==========================================================================
  // 位置和关卡
  //==========================================================================

  /** 所属关卡ID */
  public int levelId;
  
  /** 出生位置 X */
  public float spawnX;
  
  /** 出生位置 Y */
  public float spawnY;

  //==========================================================================
  // 组件数据
  //==========================================================================

  /** 怪物组件索引数组（对应 monstats2 中的组件槽位） */
  public byte[] components = new byte[16];

  //==========================================================================
  // 战斗状态
  //==========================================================================

  /** 最后一次攻击者ID */
  public int lastAttackerId = -1;
  
  /** 最后受到攻击的时间（帧数） */
  public int lastHitTime;
  
  /** 杀死此怪物的实体ID */
  public int killerId = -1;

  //==========================================================================
  // 难度和加成
  //==========================================================================

  /** 当前难度（0=普通, 1=噩梦, 2=地狱） */
  public int difficulty;
  
  /** 玩家数量（用于计算加成） */
  public int playerCount = 1;
  
  /** 是否已应用玩家数量加成 */
  public boolean playerBonusApplied;

  //==========================================================================
  // 唯一怪物数据
  //==========================================================================

  /** 是否是冠军（Champion）怪物 */
  public boolean isChampion;
  
  /** 是否是唯一（Unique）怪物 */
  public boolean isUnique;
  
  /** 是否是小Boss（Minion） */
  public boolean isMinion;
  
  /** 唯一怪物修正列表 */
  public int[] uniqueMods = new int[3];
  
  /** 唯一怪物名称种子（用于生成随机名字） */
  public int nameSeed;

  //==========================================================================
  // 构造函数
  //==========================================================================

  /**
   * 创建怪物数据
   */
  public MonsterData() {
    reset();
  }

  /**
   * 重置数据到初始状态
   */
  public void reset() {
    monStats = null;
    monStats2 = null;
    
    aiState = 0;
    aiContext = null;
    aiTargetId = -1;
    aiTargetType = 0;
    aiDelay = 0;
    
    summonerFlags = 0;
    ownerId = -1;
    linkedMonsterId = -1;
    
    levelId = 0;
    spawnX = 0;
    spawnY = 0;
    
    for (int i = 0; i < components.length; i++) {
      components[i] = 0;
    }
    
    lastAttackerId = -1;
    lastHitTime = 0;
    killerId = -1;
    
    difficulty = 0;
    playerCount = 1;
    playerBonusApplied = false;
    
    isChampion = false;
    isUnique = false;
    isMinion = false;
    for (int i = 0; i < uniqueMods.length; i++) {
      uniqueMods[i] = 0;
    }
    nameSeed = 0;
  }

  //==========================================================================
  // 召唤者标志操作
  //==========================================================================

  /**
   * 检查召唤者标志
   * 
   * @param flag 要检查的标志
   * @return true 如果有标志
   */
  public boolean checkSummonerFlag(int flag) {
    return (summonerFlags & flag) != 0;
  }

  /**
   * 设置或清除召唤者标志
   * 
   * @param flag 标志
   * @param set true 设置，false 清除
   */
  public void toggleSummonerFlag(int flag, boolean set) {
    if (set) {
      summonerFlags |= flag;
    } else {
      summonerFlags &= ~flag;
    }
  }

  /**
   * 检查是否已被复活
   * 
   * @return true 如果已被复活
   */
  public boolean isRaised() {
    return checkSummonerFlag(MonsterFlags.SUMMONER_RAISED);
  }

  /**
   * 检查是否被皈依
   * 
   * @return true 如果被皈依
   */
  public boolean isConverted() {
    return checkSummonerFlag(MonsterFlags.SUMMONER_CONVERTED);
  }

  /**
   * 检查是否正在逃跑
   * 
   * @return true 如果正在逃跑
   */
  public boolean isFleeing() {
    return checkSummonerFlag(MonsterFlags.SUMMONER_FLEEING);
  }

  //==========================================================================
  // 所有者检查
  //==========================================================================

  /**
   * 检查是否有主人
   * 
   * @return true 如果有主人
   */
  public boolean hasOwner() {
    return ownerId >= 0;
  }

  /**
   * 检查是否是指定实体的宠物/召唤物
   * 
   * @param entityId 实体ID
   * @return true 如果是其宠物
   */
  public boolean isOwnedBy(int entityId) {
    return ownerId == entityId;
  }

  //==========================================================================
  // 类型检查
  //==========================================================================

  /**
   * 获取怪物标志
   * 
   * @return 怪物标志，如果没有配置返回 0
   */
  public int getFlags() {
    // TODO: 从 monStats 读取标志
    return 0;
  }

  /**
   * 检查是否是 Boss
   * 
   * @return true 如果是 Boss
   */
  public boolean isBoss() {
    return monStats != null && MonsterType.isBoss(monStats.hcIdx);
  }

  /**
   * 检查是否是雇佣兵
   * 
   * @return true 如果是雇佣兵
   */
  public boolean isHireling() {
    return monStats != null && MonsterType.isHireling(monStats.hcIdx);
  }

  /**
   * 检查是否是玩家召唤物
   * 
   * @return true 如果是召唤物
   */
  public boolean isSummon() {
    return monStats != null && MonsterType.isSummon(monStats.hcIdx);
  }

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 初始化 AI 上下文
   * 
   * @param entityId 实体ID
   */
  public void initAiContext(int entityId) {
    if (aiContext == null) {
      aiContext = new AiContext();
    }
    aiContext.reset();
    aiContext.entityId = entityId;
  }

  @Override
  public String toString() {
    String name = monStats != null ? monStats.NameStr : "unknown";
    return "MonsterData{" +
        "name=" + name +
        ", level=" + levelId +
        ", aiState=" + aiState +
        ", owner=" + ownerId +
        '}';
  }
}
