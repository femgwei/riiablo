package com.riiablo.engine.server.quest;

/**
 * 任务事件枚举 - 基于 D2MOO 任务事件系统移植
 * 
 * <p>定义了可以触发任务回调的事件类型。
 * 
 * <p>参考：D2MOO/source/D2Game/src/QUESTS/Quests.cpp
 * 
 * @author riiablo team
 */
public final class QuestEvent {
  private QuestEvent() {} // 不可实例化

  //==========================================================================
  // 事件类型常量
  //==========================================================================

  /** NPC 激活事件（与 NPC 对话） */
  public static final int NPC_ACTIVATE = 0;
  
  /** NPC 关闭事件（结束对话） */
  public static final int NPC_DEACTIVATE = 1;
  
  /** 进入新区域事件 */
  public static final int ENTER_LEVEL = 2;
  
  /** 离开区域事件 */
  public static final int LEAVE_LEVEL = 3;
  
  /** 击杀怪物事件 */
  public static final int MONSTER_KILLED = 4;
  
  /** 拾取物品事件 */
  public static final int ITEM_PICKED_UP = 5;
  
  /** 丢弃物品事件 */
  public static final int ITEM_DROPPED = 6;
  
  /** 使用物品事件 */
  public static final int ITEM_USED = 7;
  
  /** 操作物体事件 */
  public static final int OBJECT_OPERATED = 8;
  
  /** 玩家升级事件 */
  public static final int PLAYER_LEVEL_UP = 9;
  
  /** 游戏开始事件 */
  public static final int GAME_START = 10;
  
  /** 玩家加入游戏事件 */
  public static final int PLAYER_JOIN = 11;
  
  /** 玩家离开游戏事件 */
  public static final int PLAYER_LEAVE = 12;
  
  /** 任务完成事件 */
  public static final int QUEST_COMPLETE = 13;
  
  /** 传送门使用事件 */
  public static final int PORTAL_USED = 14;
  
  /** 技能使用事件 */
  public static final int SKILL_USED = 15;

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 获取事件名称
   * 
   * @param event 事件类型
   * @return 事件名称
   */
  public static String getName(int event) {
    switch (event) {
      case NPC_ACTIVATE: return "NPC Activate";
      case NPC_DEACTIVATE: return "NPC Deactivate";
      case ENTER_LEVEL: return "Enter Level";
      case LEAVE_LEVEL: return "Leave Level";
      case MONSTER_KILLED: return "Monster Killed";
      case ITEM_PICKED_UP: return "Item Picked Up";
      case ITEM_DROPPED: return "Item Dropped";
      case ITEM_USED: return "Item Used";
      case OBJECT_OPERATED: return "Object Operated";
      case PLAYER_LEVEL_UP: return "Player Level Up";
      case GAME_START: return "Game Start";
      case PLAYER_JOIN: return "Player Join";
      case PLAYER_LEAVE: return "Player Leave";
      case QUEST_COMPLETE: return "Quest Complete";
      case PORTAL_USED: return "Portal Used";
      case SKILL_USED: return "Skill Used";
      default: return "Unknown";
    }
  }

  /**
   * 检查事件是否有效
   * 
   * @param event 事件类型
   * @return true 如果有效
   */
  public static boolean isValid(int event) {
    return event >= NPC_ACTIVATE && event <= SKILL_USED;
  }
}
