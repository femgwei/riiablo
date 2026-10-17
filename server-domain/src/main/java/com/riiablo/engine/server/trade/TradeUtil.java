package com.riiablo.engine.server.trade;

/**
 * 交易工具类 - 基于 D2MOD PlrTrade.cpp 移植
 * 
 * <p>提供交易相关的辅助计算方法。
 * 
 * <p>参考：D2MOD/source/D2Game/src/PLAYER/PlrTrade.cpp
 * 
 * @author riiablo team
 */
public final class TradeUtil {
  private TradeUtil() {} // 不可实例化

  //==========================================================================
  // 距离检查
  //==========================================================================

  /** 交易最大距离（子格） */
  public static final int MAX_TRADE_DISTANCE = 10;

  /**
   * 检查两个位置是否在交易距离内
   * 
   * @param x1 位置 1 X
   * @param y1 位置 1 Y
   * @param x2 位置 2 X
   * @param y2 位置 2 Y
   * @return true 如果在范围内
   */
  public static boolean isInTradeRange(int x1, int y1, int x2, int y2) {
    int dx = x2 - x1;
    int dy = y2 - y1;
    int distSq = dx * dx + dy * dy;
    return distSq <= MAX_TRADE_DISTANCE * MAX_TRADE_DISTANCE;
  }

  //==========================================================================
  // 物品可交易性检查
  //==========================================================================

  /** 物品标志：任务物品 */
  public static final int ITEM_FLAG_QUEST = 0x0001;
  
  /** 物品标志：灵魂绑定 */
  public static final int ITEM_FLAG_SOULBOUND = 0x0002;
  
  /** 物品标志：不可交易 */
  public static final int ITEM_FLAG_NO_TRADE = 0x0004;

  /**
   * 检查物品是否可交易
   * 
   * @param itemFlags 物品标志
   * @return true 如果可交易
   */
  public static boolean isItemTradable(int itemFlags) {
    // 任务物品、灵魂绑定物品、标记为不可交易的物品都不能交易
    return (itemFlags & (ITEM_FLAG_QUEST | ITEM_FLAG_SOULBOUND | ITEM_FLAG_NO_TRADE)) == 0;
  }

  /**
   * 检查物品是否为任务物品
   * 
   * @param itemFlags 物品标志
   * @return true 如果是任务物品
   */
  public static boolean isQuestItem(int itemFlags) {
    return (itemFlags & ITEM_FLAG_QUEST) != 0;
  }

  //==========================================================================
  // 交易价值计算
  //==========================================================================

  /**
   * 计算物品交易价值
   * 
   * <p>用于显示和参考，不影响实际交易
   * 
   * @param basePrice 基础价格
   * @param quality 物品品质
   * @param identified 是否已鉴定
   * @return 交易价值
   */
  public static int calculateTradeValue(int basePrice, int quality, boolean identified) {
    if (!identified) {
      return basePrice; // 未鉴定物品按基础价格
    }
    
    // 根据品质调整价格
    float multiplier = 1.0f;
    switch (quality) {
      case 1: // 低劣
        multiplier = 0.5f;
        break;
      case 2: // 普通
        multiplier = 1.0f;
        break;
      case 3: // 高级
        multiplier = 1.5f;
        break;
      case 4: // 魔法
        multiplier = 2.0f;
        break;
      case 5: // 套装
        multiplier = 4.0f;
        break;
      case 6: // 稀有
        multiplier = 3.0f;
        break;
      case 7: // 暗金
        multiplier = 5.0f;
        break;
      case 8: // 手工
        multiplier = 3.5f;
        break;
    }
    
    return (int)(basePrice * multiplier);
  }

  //==========================================================================
  // 交易 UI 辅助
  //==========================================================================

  /**
   * 获取交易状态对应的颜色
   * 
   * @param state 交易状态
   * @return 颜色值
   */
  public static int getStateColor(int state) {
    switch (state) {
      case TradeState.PENDING:
      case TradeState.INVITED:
        return 0xFFFF00; // 黄色 - 等待中
      case TradeState.TRADING:
        return 0xFFFFFF; // 白色 - 交易中
      case TradeState.CONFIRMED:
        return 0x00FF00; // 绿色 - 已确认
      case TradeState.COMPLETED:
        return 0x00FF00; // 绿色 - 完成
      case TradeState.CANCELLED:
      case TradeState.FAILED:
        return 0xFF0000; // 红色 - 取消/失败
      default:
        return 0x808080; // 灰色 - 其他
    }
  }

  /**
   * 获取确认状态文本
   * 
   * @param confirmed 是否确认
   * @return 状态文本
   */
  public static String getConfirmText(boolean confirmed) {
    return confirmed ? "Ready" : "Not Ready";
  }

  /**
   * 格式化金币数量
   * 
   * @param gold 金币数量
   * @return 格式化字符串
   */
  public static String formatGold(int gold) {
    if (gold < 1000) {
      return String.valueOf(gold);
    } else if (gold < 1000000) {
      return String.format("%.1fK", gold / 1000.0f);
    } else {
      return String.format("%.1fM", gold / 1000000.0f);
    }
  }

  //==========================================================================
  // 槽位计算
  //==========================================================================

  /**
   * 计算物品需要的槽位数
   * 
   * @param width 物品宽度
   * @param height 物品高度
   * @return 槽位数
   */
  public static int calculateSlotCount(int width, int height) {
    return width * height;
  }

  /**
   * 检查位置是否在交易区域内
   * 
   * @param x X 坐标
   * @param y Y 坐标
   * @return true 如果在区域内
   */
  public static boolean isValidPosition(int x, int y) {
    return x >= 0 && x < TradeSlot.TRADE_WIDTH &&
           y >= 0 && y < TradeSlot.TRADE_HEIGHT;
  }

  /**
   * 检查物品是否可以放置在指定位置
   * 
   * @param x X 坐标
   * @param y Y 坐标
   * @param width 物品宽度
   * @param height 物品高度
   * @return true 如果可以放置
   */
  public static boolean canFitAt(int x, int y, int width, int height) {
    return x >= 0 && y >= 0 &&
           x + width <= TradeSlot.TRADE_WIDTH &&
           y + height <= TradeSlot.TRADE_HEIGHT;
  }

  /**
   * 查找可放置物品的位置
   * 
   * @param occupied 已占用槽位（二维数组）
   * @param width 物品宽度
   * @param height 物品高度
   * @return int[2] {x, y}，未找到返回 {-1, -1}
   */
  public static int[] findFreeSlot(boolean[][] occupied, int width, int height) {
    for (int y = 0; y <= TradeSlot.TRADE_HEIGHT - height; y++) {
      for (int x = 0; x <= TradeSlot.TRADE_WIDTH - width; x++) {
        if (canPlaceAt(occupied, x, y, width, height)) {
          return new int[] { x, y };
        }
      }
    }
    return new int[] { -1, -1 };
  }

  /**
   * 检查是否可以在指定位置放置
   * 
   * @param occupied 已占用槽位
   * @param x X 坐标
   * @param y Y 坐标
   * @param width 物品宽度
   * @param height 物品高度
   * @return true 如果可以放置
   */
  private static boolean canPlaceAt(boolean[][] occupied, int x, int y, int width, int height) {
    for (int dy = 0; dy < height; dy++) {
      for (int dx = 0; dx < width; dx++) {
        if (occupied[y + dy][x + dx]) {
          return false;
        }
      }
    }
    return true;
  }
}
