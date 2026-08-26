package com.riiablo.engine.server.quest;

/**
 * 任务 ID 枚举 - 基于 D2MOD D2Quests 移植
 * 
 * <p>定义了游戏中所有任务的 ID。
 * 
 * <p>参考：D2MOD/source/D2Game/include/QUESTS/Quests.h
 * 
 * @author riiablo team
 */
public final class QuestId {
  private QuestId() {} // 不可实例化

  //==========================================================================
  // 第一幕任务 (Act 1)
  //==========================================================================

  /** 第一幕 NPC 对话（沃瑞夫） */
  public static final int A1Q0_WARRIV_GOSSIP = 0;
  
  /** 邪恶巢穴 - 清除洞穴中的所有怪物 */
  public static final int A1Q1_DEN_OF_EVIL = 1;
  
  /** 血鸟 - 击杀血鸟 */
  public static final int A1Q2_BLOOD_RAVEN = 2;
  
  /** 寻找铁锤 - 找回查西的铁锤 */
  public static final int A1Q3_MALUS = 3;
  
  /** 拯救凯恩 - 从崔斯特瑞姆救出凯恩 */
  public static final int A1Q4_CAIN = 4;
  
  /** 遗忘之塔 - 击杀女伯爵 */
  public static final int A1Q5_COUNTESS = 5;
  
  /** 邪恶姐妹 - 击杀安达利尔 */
  public static final int A1Q6_ANDARIEL = 6;

  //==========================================================================
  // 第二幕任务 (Act 2)
  //==========================================================================

  /** 第二幕 NPC 对话（杰赫恩） */
  public static final int A2Q0_JERHYN_GOSSIP = 7;
  
  /** 拉达曼特的巢穴 - 击杀拉达曼特 */
  public static final int A2Q1_RADAMENT = 8;
  
  /** 赫拉迪克权杖 - 组装赫拉迪克权杖 */
  public static final int A2Q2_HORADRIC_STAFF = 9;
  
  /** 被污染的太阳 - 清除太阳祭坛的污染 */
  public static final int A2Q3_TAINTED_SUN = 10;
  
  /** 奥坛 - 找到赫拉松的日志 */
  public static final int A2Q4_HORAZON_TOME = 11;
  
  /** 召唤者 - 击杀召唤者 */
  public static final int A2Q5_SUMMONER = 12;
  
  /** 七个古墓 - 击杀督瑞尔 */
  public static final int A2Q6_DURIEL = 13;

  //==========================================================================
  // 第三幕任务 (Act 3)
  //==========================================================================

  /** 第三幕 NPC 对话（赫拉特里） */
  public static final int A3Q0_HRATLI_GOSSIP = 14;
  
  /** 黄金鸟 - 找到黄金鸟并获得生命药水 */
  public static final int A3Q1_GOLDEN_BIRD = 15;
  
  /** 剑圣之刃 - 击败议会成员 */
  public static final int A3Q2_BLADE_OF_OLD_RELIGION = 16;
  
  /** 卡利姆的意志 - 找到卡利姆遗物 */
  public static final int A3Q3_KHALIMS_WILL = 17;
  
  /** 拉姆·艾森之书 - 找到拉姆·艾森之书 */
  public static final int A3Q4_LAM_ESENS_TOME = 18;
  
  /** 崔凡克议会 - 击杀议会成员 */
  public static final int A3Q5_TRAVINCAL = 19;
  
  /** 守护者 - 击杀墨菲斯托 */
  public static final int A3Q6_MEPHISTO = 20;

  //==========================================================================
  // 第四幕任务 (Act 4)
  //==========================================================================

  /** 第四幕 NPC 对话（泰瑞尔） */
  public static final int A4Q0_TYRAEL_GOSSIP = 21;
  
  /** 堕落天使 - 击杀伊苏阿尔 */
  public static final int A4Q1_IZUAL = 22;
  
  /** 恐惧之王 - 击杀暗黑破坏神 */
  public static final int A4Q2_DIABLO = 23;
  
  /** 地狱熔炉 - 摧毁墨菲斯托的灵魂石 */
  public static final int A4Q3_HELL_FORGE = 24;

  //==========================================================================
  // 特殊任务
  //==========================================================================

  /** 第一幕导航任务 */
  public static final int A1Q1EX_NAVI = 25;
  
  /** 第二幕未使用任务 7 */
  public static final int A2Q7_UNUSED = 26;
  
  /** 第二幕未使用任务 8 */
  public static final int A2Q8_UNUSED = 27;
  
  /** 黑暗流浪者 */
  public static final int A3Q7_DARK_WANDERER = 28;
  
  /** 恐惧要塞 - 击杀海法斯特 */
  public static final int A4Q4_MALACHAI = 29;

  //==========================================================================
  // 第五幕任务 (Act 5)
  //==========================================================================

  /** 围攻野蛮人 - 击杀沈克 */
  public static final int A5Q1_SHENK = 31;
  
  /** 拯救士兵 - 拯救被俘虏的士兵 */
  public static final int A5Q2_RESCUE_SOLDIERS = 32;
  
  /** 冰封监狱 - 击败尼拉塞克 */
  public static final int A5Q3_PRISON_OF_ICE = 33;
  
  /** 背叛者 - 击杀尼拉塞克 */
  public static final int A5Q4_NIHLATHAK = 34;
  
  /** 三位远古人 - 通过远古人的考验 */
  public static final int A5Q5_ANCIENTS = 35;
  
  /** 毁灭之王 - 击杀巴尔 */
  public static final int A5Q6_BAAL = 36;

  //==========================================================================
  // 任务状态标志 ID
  //==========================================================================

  /** 最大任务状态数 */
  public static final int MAX_QUEST_STATUS = 41;

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 获取任务所属的幕数
   * 
   * @param questId 任务 ID
   * @return 幕数（1-5）
   */
  public static int getAct(int questId) {
    if (questId <= A1Q6_ANDARIEL || questId == A1Q1EX_NAVI) {
      return 1;
    } else if (questId <= A2Q6_DURIEL || questId == A2Q7_UNUSED || questId == A2Q8_UNUSED) {
      return 2;
    } else if (questId <= A3Q6_MEPHISTO || questId == A3Q7_DARK_WANDERER) {
      return 3;
    } else if (questId <= A4Q3_HELL_FORGE || questId == A4Q4_MALACHAI) {
      return 4;
    } else if (questId >= A5Q1_SHENK && questId <= A5Q6_BAAL) {
      return 5;
    }
    return 0;
  }

  /**
   * 获取任务名称
   * 
   * @param questId 任务 ID
   * @return 任务名称
   */
  public static String getName(int questId) {
    switch (questId) {
      case A1Q1_DEN_OF_EVIL: return "Den of Evil";
      case A1Q2_BLOOD_RAVEN: return "Blood Raven";
      case A1Q3_MALUS: return "Tools of the Trade";
      case A1Q4_CAIN: return "The Search for Cain";
      case A1Q5_COUNTESS: return "The Forgotten Tower";
      case A1Q6_ANDARIEL: return "Sisters to the Slaughter";
      case A2Q1_RADAMENT: return "Radament's Lair";
      case A2Q2_HORADRIC_STAFF: return "The Horadric Staff";
      case A2Q3_TAINTED_SUN: return "Tainted Sun";
      case A2Q4_HORAZON_TOME: return "Arcane Sanctuary";
      case A2Q5_SUMMONER: return "The Summoner";
      case A2Q6_DURIEL: return "The Seven Tombs";
      case A3Q1_GOLDEN_BIRD: return "The Golden Bird";
      case A3Q2_BLADE_OF_OLD_RELIGION: return "Blade of the Old Religion";
      case A3Q3_KHALIMS_WILL: return "Khalim's Will";
      case A3Q4_LAM_ESENS_TOME: return "Lam Esen's Tome";
      case A3Q5_TRAVINCAL: return "The Blackened Temple";
      case A3Q6_MEPHISTO: return "The Guardian";
      case A4Q1_IZUAL: return "The Fallen Angel";
      case A4Q2_DIABLO: return "Terror's End";
      case A4Q3_HELL_FORGE: return "Hell's Forge";
      case A5Q1_SHENK: return "Siege on Harrogath";
      case A5Q2_RESCUE_SOLDIERS: return "Rescue on Mount Arreat";
      case A5Q3_PRISON_OF_ICE: return "Prison of Ice";
      case A5Q4_NIHLATHAK: return "Betrayal of Harrogath";
      case A5Q5_ANCIENTS: return "Rite of Passage";
      case A5Q6_BAAL: return "Eve of Destruction";
      default: return "Unknown";
    }
  }

  /**
   * 检查是否是有效的任务 ID
   * 
   * @param questId 任务 ID
   * @return true 如果有效
   */
  public static boolean isValid(int questId) {
    return questId >= 0 && questId <= A5Q6_BAAL;
  }

  /**
   * 获取所有主线任务 ID 列表
   * 
   * @return 任务 ID 数组
   */
  public static int[] getAllQuestIds() {
    return new int[] {
      A1Q1_DEN_OF_EVIL, A1Q2_BLOOD_RAVEN, A1Q3_MALUS, A1Q4_CAIN, A1Q5_COUNTESS, A1Q6_ANDARIEL,
      A2Q1_RADAMENT, A2Q2_HORADRIC_STAFF, A2Q3_TAINTED_SUN, A2Q4_HORAZON_TOME, A2Q5_SUMMONER, A2Q6_DURIEL,
      A3Q1_GOLDEN_BIRD, A3Q2_BLADE_OF_OLD_RELIGION, A3Q3_KHALIMS_WILL, A3Q4_LAM_ESENS_TOME, A3Q5_TRAVINCAL, A3Q6_MEPHISTO,
      A4Q1_IZUAL, A4Q2_DIABLO, A4Q3_HELL_FORGE,
      A5Q1_SHENK, A5Q2_RESCUE_SOLDIERS, A5Q3_PRISON_OF_ICE, A5Q4_NIHLATHAK, A5Q5_ANCIENTS, A5Q6_BAAL
    };
  }

  /**
   * 检查是否是 Boss 任务
   * 
   * @param questId 任务 ID
   * @return true 如果是 Boss 任务
   */
  public static boolean isBossQuest(int questId) {
    return questId == A1Q6_ANDARIEL ||
           questId == A2Q6_DURIEL ||
           questId == A3Q6_MEPHISTO ||
           questId == A4Q2_DIABLO ||
           questId == A5Q6_BAAL;
  }
}
