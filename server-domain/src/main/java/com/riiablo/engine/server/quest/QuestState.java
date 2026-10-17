package com.riiablo.engine.server.quest;

/**
 * 任务状态枚举 - 基于 D2MOD 任务状态系统移植
 * 
 * <p>定义了任务的各种状态。
 * 
 * <p>参考：D2MOD/source/D2Game/include/QUESTS/Quests.h
 * 
 * @author riiablo team
 */
public final class QuestState {
  private QuestState() {} // 不可实例化

  //==========================================================================
  // 任务状态常量
  //==========================================================================

  /** 任务未开始 */
  public static final int NOT_STARTED = 0;
  
  /** 任务已激活（可以开始） */
  public static final int ACTIVE = 1;
  
  /** 任务进行中 */
  public static final int IN_PROGRESS = 2;
  
  /** 任务目标已完成（等待交任务） */
  public static final int COMPLETED_OBJECTIVE = 3;
  
  /** 任务已完成（已领取奖励） */
  public static final int COMPLETED = 4;
  
  /** 任务已完成（在更高难度中） */
  public static final int COMPLETED_IN_HIGHER = 5;

  //==========================================================================
  // 任务状态标志位（D2S 文件中的格式）
  //==========================================================================

  /** 任务完成标志（位 0） */
  public static final int FLAG_COMPLETED = 0x0001;
  
  /** 任务需求已满足（位 1） */
  public static final int FLAG_REQUIREMENTS_MET = 0x0002;
  
  /** 已收到任务（位 2） */
  public static final int FLAG_RECEIVED = 0x0004;
  
  /** 已与 NPC 对话过关于任务（位 3） */
  public static final int FLAG_TALKED_TO_NPC = 0x0008;
  
  /** 任务日志已更新（位 4） */
  public static final int FLAG_LOG_UPDATED = 0x0010;
  
  /** 主要目标已完成（位 5） */
  public static final int FLAG_PRIMARY_GOAL = 0x0020;
  
  /** 任务可关闭（位 6） */
  public static final int FLAG_CAN_CLOSE = 0x0040;
  
  /** 任务已关闭（位 7） */
  public static final int FLAG_CLOSED = 0x0080;

  //==========================================================================
  // 特殊任务状态
  //==========================================================================

  /** 已击杀拉达曼特 */
  public static final int FLAG_RADAMENT_KILLED = 0x0010;
  
  /** 已找到赫拉迪克卷轴 */
  public static final int FLAG_HORADRIC_SCROLL = 0x0004;
  
  /** 已找到赫拉迪克方块 */
  public static final int FLAG_HORADRIC_CUBE = 0x0008;
  
  /** 已找到权杖底座 */
  public static final int FLAG_STAFF_BASE = 0x0010;
  
  /** 已找到权杖宝石 */
  public static final int FLAG_STAFF_JEWEL = 0x0020;

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 检查状态是否有效
   * 
   * @param state 状态值
   * @return true 如果有效
   */
  public static boolean isValid(int state) {
    return state >= NOT_STARTED && state <= COMPLETED_IN_HIGHER;
  }

  /**
   * 获取状态名称
   * 
   * @param state 状态值
   * @return 状态名称
   */
  public static String getName(int state) {
    switch (state) {
      case NOT_STARTED: return "Not Started";
      case ACTIVE: return "Active";
      case IN_PROGRESS: return "In Progress";
      case COMPLETED_OBJECTIVE: return "Completed Objective";
      case COMPLETED: return "Completed";
      case COMPLETED_IN_HIGHER: return "Completed (Higher Difficulty)";
      default: return "Unknown";
    }
  }

  /**
   * 检查任务是否已完成
   * 
   * @param flags 任务标志
   * @return true 如果已完成
   */
  public static boolean isCompleted(int flags) {
    return (flags & FLAG_COMPLETED) != 0;
  }

  /**
   * 检查任务是否已激活
   * 
   * @param flags 任务标志
   * @return true 如果已激活
   */
  public static boolean isActive(int flags) {
    return (flags & FLAG_RECEIVED) != 0 && !isCompleted(flags);
  }

  /**
   * 检查任务是否可以开始
   * 
   * @param flags 任务标志
   * @return true 如果可以开始
   */
  public static boolean canStart(int flags) {
    return (flags & FLAG_REQUIREMENTS_MET) != 0 && !isCompleted(flags);
  }

  /**
   * 设置任务标志
   * 
   * @param flags 原始标志
   * @param flag 要设置的标志
   * @return 新的标志值
   */
  public static int setFlag(int flags, int flag) {
    return flags | flag;
  }

  /**
   * 清除任务标志
   * 
   * @param flags 原始标志
   * @param flag 要清除的标志
   * @return 新的标志值
   */
  public static int clearFlag(int flags, int flag) {
    return flags & ~flag;
  }

  /**
   * 检查是否有指定标志
   * 
   * @param flags 标志集合
   * @param flag 要检查的标志
   * @return true 如果有标志
   */
  public static boolean hasFlag(int flags, int flag) {
    return (flags & flag) != 0;
  }
}
