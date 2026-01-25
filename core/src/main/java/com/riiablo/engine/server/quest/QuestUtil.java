package com.riiablo.engine.server.quest;

import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 任务工具类 - 基于 D2MOO Quests.cpp 移植
 * 
 * <p>提供任务相关的辅助方法。
 * 
 * <p>参考：D2MOO/source/D2Game/src/QUESTS/Quests.cpp
 * 
 * @author riiablo team
 */
public final class QuestUtil {
  private static final Logger log = LogManager.getLogger(QuestUtil.class);

  private QuestUtil() {} // 不可实例化

  //==========================================================================
  // 幕数相关
  //==========================================================================

  /** 每幕的任务数量 */
  private static final int[] QUESTS_PER_ACT = { 6, 6, 6, 3, 6 };
  
  /** 每幕的第一个任务 ID */
  private static final int[] FIRST_QUEST_PER_ACT = {
      QuestId.A1Q1_DEN_OF_EVIL,
      QuestId.A2Q1_RADAMENT,
      QuestId.A3Q1_GOLDEN_BIRD,
      QuestId.A4Q1_IZUAL,
      QuestId.A5Q1_SHENK
  };

  /**
   * 获取指定幕的任务数量
   * 
   * @param act 幕数（1-5）
   * @return 任务数量
   */
  public static int getQuestCount(int act) {
    if (act < 1 || act > 5) {
      return 0;
    }
    return QUESTS_PER_ACT[act - 1];
  }

  /**
   * 获取指定幕的第一个任务 ID
   * 
   * @param act 幕数（1-5）
   * @return 第一个任务 ID，无效返回 -1
   */
  public static int getFirstQuestId(int act) {
    if (act < 1 || act > 5) {
      return -1;
    }
    return FIRST_QUEST_PER_ACT[act - 1];
  }

  //==========================================================================
  // 任务状态检查
  //==========================================================================

  /**
   * 检查指定幕是否完成
   * 
   * @param questFlags 任务标志数组
   * @param act 幕数（1-5）
   * @return true 如果该幕已完成
   */
  public static boolean isActCompleted(int[] questFlags, int act) {
    if (act < 1 || act > 5 || questFlags == null) {
      return false;
    }
    
    // 检查该幕的 Boss 任务
    int bossQuestId;
    switch (act) {
      case 1: bossQuestId = QuestId.A1Q6_ANDARIEL; break;
      case 2: bossQuestId = QuestId.A2Q6_DURIEL; break;
      case 3: bossQuestId = QuestId.A3Q6_MEPHISTO; break;
      case 4: bossQuestId = QuestId.A4Q2_DIABLO; break;
      case 5: bossQuestId = QuestId.A5Q6_BAAL; break;
      default: return false;
    }
    
    if (bossQuestId >= questFlags.length) {
      return false;
    }
    
    return QuestState.isCompleted(questFlags[bossQuestId]);
  }

  /**
   * 检查是否可以进入下一幕
   * 
   * @param questFlags 任务标志数组
   * @param currentAct 当前幕数
   * @return true 如果可以进入下一幕
   */
  public static boolean canProgressToNextAct(int[] questFlags, int currentAct) {
    return isActCompleted(questFlags, currentAct);
  }

  /**
   * 获取玩家当前可进入的最高幕数
   * 
   * @param questFlags 任务标志数组
   * @return 最高幕数（1-5）
   */
  public static int getHighestUnlockedAct(int[] questFlags) {
    for (int act = 4; act >= 1; act--) {
      if (isActCompleted(questFlags, act)) {
        return act + 1;
      }
    }
    return 1;
  }

  //==========================================================================
  // 任务奖励
  //==========================================================================

  /** 每幕完成后的技能点奖励 */
  private static final int[] SKILL_REWARDS_BY_QUEST = new int[37];
  static {
    // 拉达曼特任务奖励 1 点技能
    SKILL_REWARDS_BY_QUEST[QuestId.A2Q1_RADAMENT] = 1;
    // 伊苏阿尔任务奖励 2 点技能
    SKILL_REWARDS_BY_QUEST[QuestId.A4Q1_IZUAL] = 2;
  }

  /** 每幕完成后的属性点奖励 */
  private static final int[] STAT_REWARDS_BY_QUEST = new int[37];
  static {
    // 拉姆·艾森之书任务奖励 5 点属性
    STAT_REWARDS_BY_QUEST[QuestId.A3Q4_LAM_ESENS_TOME] = 5;
  }

  /**
   * 获取任务的技能点奖励
   * 
   * @param questId 任务 ID
   * @return 技能点奖励
   */
  public static int getSkillReward(int questId) {
    if (questId < 0 || questId >= SKILL_REWARDS_BY_QUEST.length) {
      return 0;
    }
    return SKILL_REWARDS_BY_QUEST[questId];
  }

  /**
   * 获取任务的属性点奖励
   * 
   * @param questId 任务 ID
   * @return 属性点奖励
   */
  public static int getStatReward(int questId) {
    if (questId < 0 || questId >= STAT_REWARDS_BY_QUEST.length) {
      return 0;
    }
    return STAT_REWARDS_BY_QUEST[questId];
  }

  /**
   * 检查任务是否有技能点奖励
   * 
   * @param questId 任务 ID
   * @return true 如果有技能点奖励
   */
  public static boolean hasSkillReward(int questId) {
    return getSkillReward(questId) > 0;
  }

  /**
   * 检查任务是否有属性点奖励
   * 
   * @param questId 任务 ID
   * @return true 如果有属性点奖励
   */
  public static boolean hasStatReward(int questId) {
    return getStatReward(questId) > 0;
  }

  //==========================================================================
  // 特殊任务检查
  //==========================================================================

  /**
   * 检查是否是序章任务（NPC 对话）
   * 
   * @param questId 任务 ID
   * @return true 如果是序章任务
   */
  public static boolean isIntroQuest(int questId) {
    return questId == QuestId.A1Q0_WARRIV_GOSSIP ||
           questId == QuestId.A2Q0_JERHYN_GOSSIP ||
           questId == QuestId.A3Q0_HRATLI_GOSSIP ||
           questId == QuestId.A4Q0_TYRAEL_GOSSIP;
  }

  /**
   * 检查任务是否需要特定物品
   * 
   * @param questId 任务 ID
   * @return true 如果需要物品
   */
  public static boolean requiresItem(int questId) {
    return questId == QuestId.A1Q3_MALUS ||      // 铁锤
           questId == QuestId.A1Q4_CAIN ||       // 凯恩之石
           questId == QuestId.A2Q2_HORADRIC_STAFF || // 赫拉迪克权杖
           questId == QuestId.A3Q3_KHALIMS_WILL || // 卡利姆遗物
           questId == QuestId.A3Q4_LAM_ESENS_TOME; // 拉姆·艾森之书
  }

  /**
   * 检查任务是否涉及击杀 Boss
   * 
   * @param questId 任务 ID
   * @return true 如果是 Boss 战
   */
  public static boolean involvesBossKill(int questId) {
    return QuestId.isBossQuest(questId) ||
           questId == QuestId.A1Q2_BLOOD_RAVEN ||
           questId == QuestId.A1Q5_COUNTESS ||
           questId == QuestId.A2Q1_RADAMENT ||
           questId == QuestId.A2Q5_SUMMONER ||
           questId == QuestId.A3Q5_TRAVINCAL ||
           questId == QuestId.A4Q1_IZUAL ||
           questId == QuestId.A5Q1_SHENK ||
           questId == QuestId.A5Q4_NIHLATHAK;
  }

  //==========================================================================
  // 难度相关
  //==========================================================================

  /** 难度名称 */
  private static final String[] DIFFICULTY_NAMES = {
      "Normal", "Nightmare", "Hell"
  };

  /**
   * 获取难度名称
   * 
   * @param difficulty 难度（0-2）
   * @return 难度名称
   */
  public static String getDifficultyName(int difficulty) {
    if (difficulty < 0 || difficulty >= DIFFICULTY_NAMES.length) {
      return "Unknown";
    }
    return DIFFICULTY_NAMES[difficulty];
  }

  /**
   * 计算任务标志数组中的索引
   * 
   * @param questId 任务 ID
   * @param difficulty 难度（0-2）
   * @return 数组索引
   */
  public static int getQuestFlagIndex(int questId, int difficulty) {
    // 每个难度有 MAX_QUEST_STATUS 个任务状态
    return questId + difficulty * QuestId.MAX_QUEST_STATUS;
  }
}
