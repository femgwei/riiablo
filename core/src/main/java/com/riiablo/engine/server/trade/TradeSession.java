package com.riiablo.engine.server.trade;

import com.badlogic.gdx.utils.Array;

/**
 * 交易会话 - 基于 D2MOD 移植
 * 
 * <p>代表一次交易的完整会话，包含双方的交易数据。
 * 
 * <p>参考：D2MOD/source/D2Game/src/PLAYER/PlrTrade.cpp
 * 
 * @author riiablo team
 */
public class TradeSession {

  //==========================================================================
  // 常量
  //==========================================================================

  /** 交易超时时间（毫秒） */
  public static final long TRADE_TIMEOUT = 60000; // 60 秒
  
  /** 请求超时时间（毫秒） */
  public static final long REQUEST_TIMEOUT = 30000; // 30 秒

  //==========================================================================
  // 会话数据
  //==========================================================================

  /** 会话 ID */
  private int sessionId;
  
  /** 玩家 1 实体 ID（发起方） */
  private int player1Id;
  
  /** 玩家 2 实体 ID（接受方） */
  private int player2Id;
  
  /** 玩家 1 名称 */
  private String player1Name;
  
  /** 玩家 2 名称 */
  private String player2Name;
  
  /** 玩家 1 的交易物品 */
  private final Array<TradeSlot> player1Items;
  
  /** 玩家 2 的交易物品 */
  private final Array<TradeSlot> player2Items;
  
  /** 玩家 1 的交易金币 */
  private int player1Gold;
  
  /** 玩家 2 的交易金币 */
  private int player2Gold;
  
  /** 玩家 1 确认状态 */
  private boolean player1Confirmed;
  
  /** 玩家 2 确认状态 */
  private boolean player2Confirmed;
  
  /** 当前交易状态 */
  private int state;
  
  /** 会话创建时间 */
  private long createTime;
  
  /** 最后活动时间 */
  private long lastActivityTime;

  //==========================================================================
  // 构造函数
  //==========================================================================

  /**
   * 创建空会话
   */
  public TradeSession() {
    this.player1Items = new Array<>(TradeSlot.TOTAL_SLOTS);
    this.player2Items = new Array<>(TradeSlot.TOTAL_SLOTS);
    reset();
  }

  /**
   * 创建交易会话
   * 
   * @param sessionId 会话 ID
   * @param player1Id 玩家 1 实体 ID
   * @param player2Id 玩家 2 实体 ID
   */
  public TradeSession(int sessionId, int player1Id, int player2Id) {
    this.sessionId = sessionId;
    this.player1Id = player1Id;
    this.player2Id = player2Id;
    this.player1Items = new Array<>(TradeSlot.TOTAL_SLOTS);
    this.player2Items = new Array<>(TradeSlot.TOTAL_SLOTS);
    this.state = TradeState.PENDING;
    this.createTime = System.currentTimeMillis();
    this.lastActivityTime = createTime;
  }

  //==========================================================================
  // 会话管理
  //==========================================================================

  /**
   * 初始化会话
   * 
   * @param sessionId 会话 ID
   * @param player1Id 玩家 1 实体 ID
   * @param player2Id 玩家 2 实体 ID
   */
  public void init(int sessionId, int player1Id, int player2Id) {
    reset();
    this.sessionId = sessionId;
    this.player1Id = player1Id;
    this.player2Id = player2Id;
    this.state = TradeState.PENDING;
    this.createTime = System.currentTimeMillis();
    this.lastActivityTime = createTime;
  }

  /**
   * 重置会话
   */
  public void reset() {
    this.sessionId = -1;
    this.player1Id = -1;
    this.player2Id = -1;
    this.player1Name = "";
    this.player2Name = "";
    this.player1Items.clear();
    this.player2Items.clear();
    this.player1Gold = 0;
    this.player2Gold = 0;
    this.player1Confirmed = false;
    this.player2Confirmed = false;
    this.state = TradeState.NONE;
    this.createTime = 0;
    this.lastActivityTime = 0;
  }

  /**
   * 更新活动时间
   */
  public void touch() {
    this.lastActivityTime = System.currentTimeMillis();
  }

  //==========================================================================
  // 物品管理
  //==========================================================================

  /**
   * 添加物品到交易
   * 
   * @param playerId 玩家实体 ID
   * @param slot 交易槽位
   * @return true 如果成功
   */
  public boolean addItem(int playerId, TradeSlot slot) {
    if (state != TradeState.TRADING) {
      return false;
    }
    
    Array<TradeSlot> items = getPlayerItems(playerId);
    if (items == null) {
      return false;
    }
    
    // 检查槽位是否可用
    if (!canPlaceItem(playerId, slot.x, slot.y, slot.width, slot.height)) {
      return false;
    }
    
    items.add(slot);
    resetConfirmations();
    touch();
    return true;
  }

  /**
   * 从交易移除物品
   * 
   * @param playerId 玩家实体 ID
   * @param itemEntityId 物品实体 ID
   * @return 被移除的槽位，不存在返回 null
   */
  public TradeSlot removeItem(int playerId, int itemEntityId) {
    if (state != TradeState.TRADING) {
      return null;
    }
    
    Array<TradeSlot> items = getPlayerItems(playerId);
    if (items == null) {
      return null;
    }
    
    for (int i = items.size - 1; i >= 0; i--) {
      TradeSlot slot = items.get(i);
      if (slot.itemEntityId == itemEntityId) {
        items.removeIndex(i);
        resetConfirmations();
        touch();
        return slot;
      }
    }
    
    return null;
  }

  /**
   * 检查是否可以放置物品
   * 
   * @param playerId 玩家实体 ID
   * @param x X 坐标
   * @param y Y 坐标
   * @param width 物品宽度
   * @param height 物品高度
   * @return true 如果可以放置
   */
  public boolean canPlaceItem(int playerId, int x, int y, int width, int height) {
    // 检查边界
    if (x < 0 || y < 0 || 
        x + width > TradeSlot.TRADE_WIDTH || 
        y + height > TradeSlot.TRADE_HEIGHT) {
      return false;
    }
    
    Array<TradeSlot> items = getPlayerItems(playerId);
    if (items == null) {
      return false;
    }
    
    // 检查是否与现有物品重叠
    for (TradeSlot slot : items) {
      for (int dx = 0; dx < width; dx++) {
        for (int dy = 0; dy < height; dy++) {
          if (slot.occupies(x + dx, y + dy)) {
            return false;
          }
        }
      }
    }
    
    return true;
  }

  /**
   * 获取玩家的物品列表
   * 
   * @param playerId 玩家实体 ID
   * @return 物品列表，不存在返回 null
   */
  public Array<TradeSlot> getPlayerItems(int playerId) {
    if (playerId == player1Id) {
      return player1Items;
    } else if (playerId == player2Id) {
      return player2Items;
    }
    return null;
  }

  //==========================================================================
  // 金币管理
  //==========================================================================

  /**
   * 设置交易金币
   * 
   * @param playerId 玩家实体 ID
   * @param gold 金币数量
   * @return true 如果成功
   */
  public boolean setGold(int playerId, int gold) {
    if (state != TradeState.TRADING || gold < 0) {
      return false;
    }
    
    if (playerId == player1Id) {
      player1Gold = gold;
    } else if (playerId == player2Id) {
      player2Gold = gold;
    } else {
      return false;
    }
    
    resetConfirmations();
    touch();
    return true;
  }

  /**
   * 获取玩家的交易金币
   * 
   * @param playerId 玩家实体 ID
   * @return 金币数量
   */
  public int getGold(int playerId) {
    if (playerId == player1Id) {
      return player1Gold;
    } else if (playerId == player2Id) {
      return player2Gold;
    }
    return 0;
  }

  //==========================================================================
  // 确认管理
  //==========================================================================

  /**
   * 确认交易
   * 
   * @param playerId 玩家实体 ID
   * @return true 如果成功
   */
  public boolean confirm(int playerId) {
    if (state != TradeState.TRADING) {
      return false;
    }
    
    if (playerId == player1Id) {
      player1Confirmed = true;
    } else if (playerId == player2Id) {
      player2Confirmed = true;
    } else {
      return false;
    }
    
    touch();
    
    // 检查是否双方都确认
    if (player1Confirmed && player2Confirmed) {
      state = TradeState.CONFIRMED;
    }
    
    return true;
  }

  /**
   * 取消确认
   * 
   * @param playerId 玩家实体 ID
   */
  public void unconfirm(int playerId) {
    if (playerId == player1Id) {
      player1Confirmed = false;
    } else if (playerId == player2Id) {
      player2Confirmed = false;
    }
    
    if (state == TradeState.CONFIRMED) {
      state = TradeState.TRADING;
    }
    
    touch();
  }

  /**
   * 重置双方确认状态
   */
  private void resetConfirmations() {
    player1Confirmed = false;
    player2Confirmed = false;
    if (state == TradeState.CONFIRMED) {
      state = TradeState.TRADING;
    }
  }

  /**
   * 检查是否双方都确认
   * 
   * @return true 如果双方都确认
   */
  public boolean isBothConfirmed() {
    return player1Confirmed && player2Confirmed;
  }

  //==========================================================================
  // 状态检查
  //==========================================================================

  /**
   * 检查会话是否有效
   * 
   * @return true 如果有效
   */
  public boolean isValid() {
    return sessionId >= 0 && player1Id >= 0 && player2Id >= 0;
  }

  /**
   * 检查是否超时
   * 
   * @return true 如果超时
   */
  public boolean isTimedOut() {
    long elapsed = System.currentTimeMillis() - lastActivityTime;
    
    if (state == TradeState.PENDING || state == TradeState.INVITED) {
      return elapsed > REQUEST_TIMEOUT;
    }
    
    return elapsed > TRADE_TIMEOUT;
  }

  /**
   * 检查玩家是否参与此交易
   * 
   * @param playerId 玩家实体 ID
   * @return true 如果参与
   */
  public boolean hasPlayer(int playerId) {
    return playerId == player1Id || playerId == player2Id;
  }

  /**
   * 获取交易对方
   * 
   * @param playerId 玩家实体 ID
   * @return 对方玩家实体 ID
   */
  public int getOtherPlayer(int playerId) {
    if (playerId == player1Id) {
      return player2Id;
    } else if (playerId == player2Id) {
      return player1Id;
    }
    return -1;
  }

  //==========================================================================
  // Getter/Setter
  //==========================================================================

  public int getSessionId() {
    return sessionId;
  }

  public int getPlayer1Id() {
    return player1Id;
  }

  public int getPlayer2Id() {
    return player2Id;
  }

  public String getPlayer1Name() {
    return player1Name;
  }

  public void setPlayer1Name(String name) {
    this.player1Name = name;
  }

  public String getPlayer2Name() {
    return player2Name;
  }

  public void setPlayer2Name(String name) {
    this.player2Name = name;
  }

  public int getState() {
    return state;
  }

  public void setState(int state) {
    this.state = state;
    touch();
  }

  public boolean isPlayer1Confirmed() {
    return player1Confirmed;
  }

  public boolean isPlayer2Confirmed() {
    return player2Confirmed;
  }

  public int getPlayer1Gold() {
    return player1Gold;
  }

  public int getPlayer2Gold() {
    return player2Gold;
  }

  public Array<TradeSlot> getPlayer1Items() {
    return player1Items;
  }

  public Array<TradeSlot> getPlayer2Items() {
    return player2Items;
  }

  //==========================================================================
  // 调试信息
  //==========================================================================

  @Override
  public String toString() {
    return "TradeSession{" +
        "sessionId=" + sessionId +
        ", player1=" + player1Id +
        ", player2=" + player2Id +
        ", state=" + TradeState.getName(state) +
        ", p1Items=" + player1Items.size +
        ", p2Items=" + player2Items.size +
        ", p1Gold=" + player1Gold +
        ", p2Gold=" + player2Gold +
        ", confirmed=" + player1Confirmed + "/" + player2Confirmed +
        '}';
  }
}
