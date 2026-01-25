package com.riiablo.engine.server.trade;

/**
 * 交易状态常量 - 基于 D2MOO 移植
 * 
 * <p>定义了交易过程中的各种状态。
 * 
 * <p>参考：D2MOO/source/D2Game/src/PLAYER/PlrTrade.cpp
 * 
 * @author riiablo team
 */
public final class TradeState {
  private TradeState() {} // 不可实例化

  //==========================================================================
  // 交易会话状态
  //==========================================================================

  /** 无交易（空闲状态） */
  public static final int NONE = 0;
  
  /** 等待对方响应（发起方状态） */
  public static final int PENDING = 1;
  
  /** 收到交易请求（接收方状态） */
  public static final int INVITED = 2;
  
  /** 交易进行中 */
  public static final int TRADING = 3;
  
  /** 已确认（等待对方确认） */
  public static final int CONFIRMED = 4;
  
  /** 交易完成 */
  public static final int COMPLETED = 5;
  
  /** 交易取消 */
  public static final int CANCELLED = 6;
  
  /** 交易失败 */
  public static final int FAILED = 7;

  //==========================================================================
  // 交易结果代码
  //==========================================================================

  /** 成功 */
  public static final int RESULT_SUCCESS = 0;
  
  /** 目标玩家忙 */
  public static final int RESULT_TARGET_BUSY = 1;
  
  /** 目标玩家不在附近 */
  public static final int RESULT_TOO_FAR = 2;
  
  /** 背包空间不足 */
  public static final int RESULT_NO_SPACE = 3;
  
  /** 金币不足 */
  public static final int RESULT_NO_GOLD = 4;
  
  /** 对方取消 */
  public static final int RESULT_CANCELLED = 5;
  
  /** 物品不可交易 */
  public static final int RESULT_ITEM_NOT_TRADABLE = 6;
  
  /** 交易超时 */
  public static final int RESULT_TIMEOUT = 7;
  
  /** 未知错误 */
  public static final int RESULT_ERROR = 8;

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
    return state >= NONE && state <= FAILED;
  }

  /**
   * 获取状态名称
   * 
   * @param state 状态值
   * @return 状态名称
   */
  public static String getName(int state) {
    switch (state) {
      case NONE: return "None";
      case PENDING: return "Pending";
      case INVITED: return "Invited";
      case TRADING: return "Trading";
      case CONFIRMED: return "Confirmed";
      case COMPLETED: return "Completed";
      case CANCELLED: return "Cancelled";
      case FAILED: return "Failed";
      default: return "Unknown";
    }
  }

  /**
   * 检查是否处于活跃交易状态
   * 
   * @param state 状态值
   * @return true 如果活跃
   */
  public static boolean isActive(int state) {
    return state == PENDING || state == INVITED || state == TRADING || state == CONFIRMED;
  }

  /**
   * 检查是否可以修改交易内容
   * 
   * @param state 状态值
   * @return true 如果可修改
   */
  public static boolean canModify(int state) {
    return state == TRADING;
  }

  /**
   * 获取结果代码名称
   * 
   * @param result 结果代码
   * @return 结果名称
   */
  public static String getResultName(int result) {
    switch (result) {
      case RESULT_SUCCESS: return "Success";
      case RESULT_TARGET_BUSY: return "Target Busy";
      case RESULT_TOO_FAR: return "Too Far";
      case RESULT_NO_SPACE: return "No Space";
      case RESULT_NO_GOLD: return "No Gold";
      case RESULT_CANCELLED: return "Cancelled";
      case RESULT_ITEM_NOT_TRADABLE: return "Item Not Tradable";
      case RESULT_TIMEOUT: return "Timeout";
      case RESULT_ERROR: return "Error";
      default: return "Unknown";
    }
  }
}
