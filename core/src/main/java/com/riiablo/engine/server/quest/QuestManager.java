package com.riiablo.engine.server.quest;

import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;

import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 任务管理器 - 基于 D2MOO Quests.cpp 移植
 * 
 * <p>管理游戏任务的完整生命周期：
 * <ul>
 *   <li>任务初始化和激活</li>
 *   <li>任务触发器管理</li>
 *   <li>任务进度追踪</li>
 *   <li>任务奖励发放</li>
 *   <li>任务事件处理</li>
 * </ul>
 * 
 * <p>参考：D2MOO/source/D2Game/src/QUESTS/Quests.cpp
 * 
 * @author riiablo team
 */
public class QuestManager {
  private static final Logger log = LogManager.getLogger(QuestManager.class);

  //==========================================================================
  // 常量 - 任务触发器类型
  //==========================================================================

  /** 怪物击杀触发器 */
  public static final int TRIGGER_MONSTER_KILL = 1;

  /** 物品拾取触发器 */
  public static final int TRIGGER_ITEM_PICKUP = 2;

  /** 进入区域触发器 */
  public static final int TRIGGER_ENTER_AREA = 3;

  /** NPC 对话触发器 */
  public static final int TRIGGER_NPC_TALK = 4;

  /** 使用物品触发器 */
  public static final int TRIGGER_ITEM_USE = 5;

  /** 对象交互触发器 */
  public static final int TRIGGER_OBJECT_INTERACT = 6;

  /** 给予 NPC 物品触发器 */
  public static final int TRIGGER_ITEM_GIVE = 7;

  //==========================================================================
  // 内部类 - 任务触发器
  //==========================================================================

  /**
   * 任务触发器
   */
  public static class QuestTrigger {
    /** 触发器类型 */
    public int type;

    /** 任务 ID */
    public int questId;

    /** 触发器目标 ID（怪物ID/物品ID/区域ID/NPC ID/对象ID） */
    public int targetId;

    /** 触发需要的数量（默认 1） */
    public int requiredCount = 1;

    /** 触发后的任务状态 */
    public int resultState;

    /** 触发后设置的标志 */
    public int resultFlag;

    /** 是否只触发一次 */
    public boolean once = true;

    /** 是否已触发 */
    public boolean triggered;
  }

  /**
   * 任务奖励
   */
  public static class QuestReward {
    /** 经验值奖励 */
    public int experience;

    /** 金币奖励 */
    public int gold;

    /** 技能点奖励 */
    public int skillPoints;

    /** 属性点奖励 */
    public int statPoints;

    /** 生命上限增加 */
    public int lifeBonus;

    /** 法力上限增加 */
    public int manaBonus;

    /** 抗性奖励（各难度不同） */
    public int[] resistanceBonus = new int[3]; // 普通/噩梦/地狱

    /** 奖励物品代码（如 "amu" = 护身符） */
    public String[] itemCodes;
  }

  /**
   * 玩家任务数据
   */
  public static class PlayerQuestData {
    /** 玩家 ID */
    public int playerId;

    /** 当前幕数（1-5） */
    public int currentAct = 1;

    /** 当前难度（0=普通，1=噩梦，2=地狱） */
    public int difficulty;

    /** 各任务数据 */
    public IntMap<QuestData> quests = new IntMap<>();

    /** 已完成任务 ID 集合 */
    public IntArray completedQuests = new IntArray();
  }

  //==========================================================================
  // 回调接口
  //==========================================================================

  /**
   * 任务事件回调
   */
  public interface QuestCallback {
    /**
     * 任务状态变更
     */
    void onQuestStateChanged(int playerId, int questId, int oldState, int newState);

    /**
     * 任务完成
     */
    void onQuestCompleted(int playerId, int questId, QuestReward reward);

    /**
     * 发放经验值
     */
    void onGrantExperience(int playerId, int amount);

    /**
     * 发放金币
     */
    void onGrantGold(int playerId, int amount);

    /**
     * 发放物品
     */
    void onGrantItem(int playerId, String itemCode);

    /**
     * 显示任务消息
     */
    void onShowQuestMessage(int playerId, int messageId);

    /**
     * 解锁下一幕
     */
    void onUnlockAct(int playerId, int actNo);
  }

  //==========================================================================
  // 字段
  //==========================================================================

  /** 玩家任务数据 */
  private final IntMap<PlayerQuestData> playerData = new IntMap<>();

  /** 全局触发器列表 */
  private final IntMap<QuestTrigger[]> monsterKillTriggers = new IntMap<>();
  private final IntMap<QuestTrigger[]> areaEnterTriggers = new IntMap<>();
  private final IntMap<QuestTrigger[]> npcTalkTriggers = new IntMap<>();
  private final IntMap<QuestTrigger[]> objectInteractTriggers = new IntMap<>();

  /** 任务奖励表 */
  private final IntMap<QuestReward> questRewards = new IntMap<>();

  /** 回调 */
  private QuestCallback callback;

  //==========================================================================
  // 构造函数
  //==========================================================================

  public QuestManager() {
    // 注册默认任务触发器和奖励
    registerDefaultQuests();
  }

  //==========================================================================
  // 核心方法 - 玩家任务管理
  //==========================================================================

  /**
   * 初始化玩家任务数据
   * 
   * @param playerId 玩家 ID
   * @param difficulty 难度
   */
  public void initPlayerQuests(int playerId, int difficulty) {
    PlayerQuestData data = new PlayerQuestData();
    data.playerId = playerId;
    data.difficulty = difficulty;
    data.currentAct = 1;

    // 初始化所有任务
    for (int questId : QuestId.getAllQuestIds()) {
      QuestData quest = new QuestData(questId);
      data.quests.put(questId, quest);
    }

    // 激活第一幕的任务
    activateActQuests(data, 1);

    playerData.put(playerId, data);
    log.debug("Initialized quests for player {}", playerId);
  }

  /**
   * 激活指定幕数的任务
   */
  private void activateActQuests(PlayerQuestData data, int actNo) {
    for (IntMap.Entry<QuestData> entry : data.quests) {
      QuestData quest = entry.value;
      if (quest.actNo == actNo && !quest.isCompleted()) {
        quest.active = true;
        quest.setState(QuestState.ACTIVE);
      }
    }
    log.debug("Activated Act {} quests for player {}", actNo, data.playerId);
  }

  /**
   * 获取玩家任务数据
   */
  public PlayerQuestData getPlayerData(int playerId) {
    return playerData.get(playerId);
  }

  /**
   * 获取玩家指定任务
   */
  public QuestData getQuest(int playerId, int questId) {
    PlayerQuestData data = playerData.get(playerId);
    if (data == null) {
      return null;
    }
    return data.quests.get(questId);
  }

  //==========================================================================
  // 核心方法 - 触发器处理
  //==========================================================================

  /**
   * 怪物击杀事件
   * 
   * @param playerId 击杀玩家 ID
   * @param monsterId 怪物类型 ID
   * @param monsterEntityId 怪物实体 ID
   */
  public void onMonsterKill(int playerId, int monsterId, int monsterEntityId) {
    QuestTrigger[] triggers = monsterKillTriggers.get(monsterId);
    if (triggers == null) {
      return;
    }

    PlayerQuestData data = playerData.get(playerId);
    if (data == null) {
      return;
    }

    for (QuestTrigger trigger : triggers) {
      processTrigger(data, trigger);
    }
  }

  /**
   * 进入区域事件
   * 
   * @param playerId 玩家 ID
   * @param areaId 区域 ID
   */
  public void onEnterArea(int playerId, int areaId) {
    QuestTrigger[] triggers = areaEnterTriggers.get(areaId);
    if (triggers == null) {
      return;
    }

    PlayerQuestData data = playerData.get(playerId);
    if (data == null) {
      return;
    }

    for (QuestTrigger trigger : triggers) {
      processTrigger(data, trigger);
    }
  }

  /**
   * NPC 对话事件
   * 
   * @param playerId 玩家 ID
   * @param npcId NPC 类型 ID
   */
  public void onNpcTalk(int playerId, int npcId) {
    QuestTrigger[] triggers = npcTalkTriggers.get(npcId);
    if (triggers == null) {
      return;
    }

    PlayerQuestData data = playerData.get(playerId);
    if (data == null) {
      return;
    }

    for (QuestTrigger trigger : triggers) {
      processTrigger(data, trigger);
    }
  }

  /**
   * 对象交互事件
   * 
   * @param playerId 玩家 ID
   * @param objectId 对象类型 ID
   */
  public void onObjectInteract(int playerId, int objectId) {
    QuestTrigger[] triggers = objectInteractTriggers.get(objectId);
    if (triggers == null) {
      return;
    }

    PlayerQuestData data = playerData.get(playerId);
    if (data == null) {
      return;
    }

    for (QuestTrigger trigger : triggers) {
      processTrigger(data, trigger);
    }
  }

  /**
   * 处理触发器
   */
  private void processTrigger(PlayerQuestData data, QuestTrigger trigger) {
    // 检查是否已触发过
    if (trigger.once && trigger.triggered) {
      return;
    }

    // 获取任务
    QuestData quest = data.quests.get(trigger.questId);
    if (quest == null || !quest.active || quest.isCompleted()) {
      return;
    }

    // 标记触发
    trigger.triggered = true;

    // 更新任务状态
    int oldState = quest.state;
    if (trigger.resultState > 0) {
      quest.setState(trigger.resultState);
    }
    if (trigger.resultFlag > 0) {
      quest.setFlag(trigger.resultFlag);
    }

    log.debug("Quest {} triggered: {} -> {}", trigger.questId, 
        QuestState.getName(oldState), QuestState.getName(quest.state));

    // 通知回调
    if (callback != null && oldState != quest.state) {
      callback.onQuestStateChanged(data.playerId, trigger.questId, oldState, quest.state);
    }

    // 检查是否完成
    if (quest.state == QuestState.COMPLETED) {
      completeQuest(data, trigger.questId);
    }
  }

  //==========================================================================
  // 核心方法 - 任务完成和奖励
  //==========================================================================

  /**
   * 完成任务
   */
  public void completeQuest(int playerId, int questId) {
    PlayerQuestData data = playerData.get(playerId);
    if (data == null) {
      return;
    }
    completeQuest(data, questId);
  }

  private void completeQuest(PlayerQuestData data, int questId) {
    QuestData quest = data.quests.get(questId);
    if (quest == null) {
      return;
    }

    // 标记完成
    quest.complete();
    data.completedQuests.add(questId);

    log.debug("Quest {} completed for player {}", questId, data.playerId);

    // 发放奖励
    QuestReward reward = questRewards.get(questId);
    grantReward(data, reward);

    // 通知回调
    if (callback != null) {
      callback.onQuestCompleted(data.playerId, questId, reward);
    }

    // 检查是否解锁下一幕
    checkActUnlock(data);
  }

  /**
   * 发放任务奖励
   */
  private void grantReward(PlayerQuestData data, QuestReward reward) {
    if (reward == null || callback == null) {
      return;
    }

    // 经验值
    if (reward.experience > 0) {
      callback.onGrantExperience(data.playerId, reward.experience);
    }

    // 金币
    if (reward.gold > 0) {
      callback.onGrantGold(data.playerId, reward.gold);
    }

    // 物品
    if (reward.itemCodes != null) {
      for (String itemCode : reward.itemCodes) {
        if (itemCode != null && !itemCode.isEmpty()) {
          callback.onGrantItem(data.playerId, itemCode);
        }
      }
    }

    // 技能点和属性点需要通过其他方式处理
    // 这里仅记录日志
    if (reward.skillPoints > 0) {
      log.debug("Granting {} skill points to player {}", reward.skillPoints, data.playerId);
    }
    if (reward.statPoints > 0) {
      log.debug("Granting {} stat points to player {}", reward.statPoints, data.playerId);
    }
  }

  /**
   * 检查是否解锁下一幕
   */
  private void checkActUnlock(PlayerQuestData data) {
    // 检查当前幕的关键任务是否完成
    int keyQuestId = getActKeyQuest(data.currentAct);
    if (keyQuestId < 0) {
      return;
    }

    QuestData keyQuest = data.quests.get(keyQuestId);
    if (keyQuest != null && keyQuest.isCompleted()) {
      int nextAct = data.currentAct + 1;
      if (nextAct <= 5) {
        data.currentAct = nextAct;
        activateActQuests(data, nextAct);

        if (callback != null) {
          callback.onUnlockAct(data.playerId, nextAct);
        }

        log.debug("Player {} unlocked Act {}", data.playerId, nextAct);
      }
    }
  }

  /**
   * 获取幕数的关键任务（通关任务）
   */
  private int getActKeyQuest(int actNo) {
    switch (actNo) {
      case 1: return QuestId.A1Q6_ANDARIEL; // 击杀安达利尔
      case 2: return QuestId.A2Q6_DURIEL; // 击杀督瑞尔
      case 3: return QuestId.A3Q6_MEPHISTO; // 击杀墨菲斯托
      case 4: return QuestId.A4Q2_DIABLO; // 击杀暗黑破坏神
      case 5: return QuestId.A5Q6_BAAL; // 击杀巴尔
      default: return -1;
    }
  }

  //==========================================================================
  // 任务注册
  //==========================================================================

  /**
   * 注册默认任务
   */
  private void registerDefaultQuests() {
    // 第一幕任务
    registerAct1Quests();

    // 第二幕任务
    registerAct2Quests();

    // 第三幕任务
    registerAct3Quests();

    // 第四幕任务
    registerAct4Quests();

    // 第五幕任务
    registerAct5Quests();

    log.debug("Registered default quests");
  }

  private void registerAct1Quests() {
    // 邪恶巢穴
    registerQuestReward(QuestId.A1Q1_DEN_OF_EVIL, 0, 0, 1, 0, 0, 0);

    // 血鸟
    registerQuestReward(QuestId.A1Q2_BLOOD_RAVEN, 5000, 0, 0, 0, 0, 0);

    // 凯恩
    registerQuestReward(QuestId.A1Q4_CAIN, 0, 0, 0, 0, 0, 0);

    // 忘却之塔
    registerQuestReward(QuestId.A1Q5_COUNTESS, 0, 0, 0, 0, 0, 0);

    // 工具
    registerQuestReward(QuestId.A1Q3_MALUS, 0, 0, 0, 0, 0, 0);

    // 安达利尔
    registerQuestReward(QuestId.A1Q6_ANDARIEL, 20000, 0, 0, 0, 0, 0);
  }

  private void registerAct2Quests() {
    // 地穴之蛇
    registerQuestReward(QuestId.A2Q1_RADAMENT, 10000, 0, 1, 0, 0, 0);

    // 神秘避难所
    registerQuestReward(QuestId.A2Q2_HORADRIC_STAFF, 0, 0, 0, 0, 0, 0);

    // 太阳之符
    registerQuestReward(QuestId.A2Q3_TAINTED_SUN, 0, 0, 0, 0, 0, 0);

    // 奥术避难所
    registerQuestReward(QuestId.A2Q4_HORAZON_TOME, 0, 0, 0, 0, 0, 0);

    // 召唤者
    registerQuestReward(QuestId.A2Q5_SUMMONER, 0, 0, 0, 0, 0, 0);

    // 督瑞尔
    registerQuestReward(QuestId.A2Q6_DURIEL, 50000, 0, 0, 0, 0, 0);
  }

  private void registerAct3Quests() {
    // 金鸟
    registerQuestReward(QuestId.A3Q1_GOLDEN_BIRD, 0, 0, 0, 0, 20, 0);

    // 克蓝之刃
    registerQuestReward(QuestId.A3Q2_BLADE_OF_OLD_RELIGION, 0, 0, 0, 0, 0, 0);

    // 卡立姆
    registerQuestReward(QuestId.A3Q3_KHALIMS_WILL, 0, 0, 0, 0, 0, 0);

    // 兰斯洛的书
    registerQuestReward(QuestId.A3Q4_LAM_ESENS_TOME, 0, 0, 0, 5, 0, 0);

    // 黑之书
    registerQuestReward(QuestId.A3Q5_TRAVINCAL, 0, 0, 0, 0, 0, 0);

    // 墨菲斯托
    registerQuestReward(QuestId.A3Q6_MEPHISTO, 80000, 0, 0, 0, 0, 0);
  }

  private void registerAct4Quests() {
    // 陨落之星
    registerQuestReward(QuestId.A4Q1_IZUAL, 0, 0, 2, 0, 0, 0);

    // 地狱熔炉
    registerQuestReward(QuestId.A4Q3_HELL_FORGE, 0, 0, 0, 0, 0, 0);

    // 恐惧终结
    registerQuestReward(QuestId.A4Q2_DIABLO, 100000, 0, 0, 0, 0, 0);
  }

  private void registerAct5Quests() {
    // 围城
    registerQuestReward(QuestId.A5Q1_SHENK, 0, 0, 0, 0, 0, 0);

    // 囚徒
    registerQuestReward(QuestId.A5Q2_RESCUE_SOLDIERS, 0, 0, 0, 0, 0, 0);

    // 冰封地狱
    registerQuestReward(QuestId.A5Q3_PRISON_OF_ICE, 0, 0, 0, 0, 0, 0);

    // 亚瑞特
    registerQuestReward(QuestId.A5Q4_NIHLATHAK, 0, 0, 0, 0, 0, 0);

    // 古代遗迹
    registerQuestReward(QuestId.A5Q5_ANCIENTS, 0, 0, 0, 0, 0, 0);

    // 巴尔
    registerQuestReward(QuestId.A5Q6_BAAL, 150000, 0, 0, 0, 0, 0);
  }

  private void registerQuestReward(int questId, int exp, int gold, int skillPoints,
      int statPoints, int lifeBonus, int manaBonus) {
    QuestReward reward = new QuestReward();
    reward.experience = exp;
    reward.gold = gold;
    reward.skillPoints = skillPoints;
    reward.statPoints = statPoints;
    reward.lifeBonus = lifeBonus;
    reward.manaBonus = manaBonus;
    questRewards.put(questId, reward);
  }

  //==========================================================================
  // 配置方法
  //==========================================================================

  public void setCallback(QuestCallback callback) {
    this.callback = callback;
  }

  /**
   * 注册怪物击杀触发器
   */
  public void registerMonsterKillTrigger(int monsterId, QuestTrigger trigger) {
    QuestTrigger[] existing = monsterKillTriggers.get(monsterId);
    if (existing == null) {
      monsterKillTriggers.put(monsterId, new QuestTrigger[] { trigger });
    } else {
      QuestTrigger[] newArray = new QuestTrigger[existing.length + 1];
      System.arraycopy(existing, 0, newArray, 0, existing.length);
      newArray[existing.length] = trigger;
      monsterKillTriggers.put(monsterId, newArray);
    }
  }

  /**
   * 注册区域进入触发器
   */
  public void registerAreaEnterTrigger(int areaId, QuestTrigger trigger) {
    QuestTrigger[] existing = areaEnterTriggers.get(areaId);
    if (existing == null) {
      areaEnterTriggers.put(areaId, new QuestTrigger[] { trigger });
    } else {
      QuestTrigger[] newArray = new QuestTrigger[existing.length + 1];
      System.arraycopy(existing, 0, newArray, 0, existing.length);
      newArray[existing.length] = trigger;
      areaEnterTriggers.put(areaId, newArray);
    }
  }

  //==========================================================================
  // 调试方法
  //==========================================================================

  /**
   * 获取玩家任务状态摘要
   */
  public String getPlayerQuestSummary(int playerId) {
    PlayerQuestData data = playerData.get(playerId);
    if (data == null) {
      return "Player not found";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("Player ").append(playerId).append(" Quests:\n");
    sb.append("Act: ").append(data.currentAct).append(", Difficulty: ").append(data.difficulty).append("\n");
    sb.append("Completed: ").append(data.completedQuests.size).append(" quests\n");

    for (IntMap.Entry<QuestData> entry : data.quests) {
      QuestData quest = entry.value;
      if (quest.active || quest.isCompleted()) {
        sb.append("  ").append(QuestId.getName(quest.questId)).append(": ");
        sb.append(QuestState.getName(quest.state)).append("\n");
      }
    }

    return sb.toString();
  }
}
