package com.riiablo.engine.server.trade;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;

import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 交易管理器 - 基于 D2MOO PlrTrade.cpp 移植
 * 
 * <p>管理所有交易会话的创建、执行、完成等操作。
 * 
 * <p>参考：D2MOO/source/D2Game/src/PLAYER/PlrTrade.cpp
 * 
 * @author riiablo team
 */
public class TradeManager {
  private static final Logger log = LogManager.getLogger(TradeManager.class);

  //==========================================================================
  // 常量
  //==========================================================================

  /** 最大同时进行的交易数 */
  public static final int MAX_SESSIONS = 64;
  
  /** 交易最大距离（子格） */
  public static final int MAX_TRADE_DISTANCE = 10;

  //==========================================================================
  // 数据
  //==========================================================================

  /** 所有交易会话（按会话 ID 索引） */
  private final IntMap<TradeSession> sessions;
  
  /** 玩家当前交易会话映射（玩家实体 ID -> 会话 ID） */
  private final IntMap<Integer> playerSessionMap;
  
  /** 下一个可用的会话 ID */
  private int nextSessionId;

  //==========================================================================
  // 回调接口
  //==========================================================================

  /** 交易事件回调 */
  public interface TradeCallback {
    /** 交易请求发送 */
    void onTradeRequest(int requesterId, int targetId);
    
    /** 交易开始 */
    void onTradeStart(TradeSession session);
    
    /** 交易内容更新 */
    void onTradeUpdate(TradeSession session, int playerId);
    
    /** 玩家确认状态变化 */
    void onConfirmChanged(TradeSession session, int playerId, boolean confirmed);
    
    /** 交易完成 */
    void onTradeComplete(TradeSession session);
    
    /** 交易取消 */
    void onTradeCancelled(TradeSession session, int cancelledBy);
  }
  
  private TradeCallback callback;

  //==========================================================================
  // 构造函数
  //==========================================================================

  /**
   * 创建交易管理器
   */
  public TradeManager() {
    this.sessions = new IntMap<>(MAX_SESSIONS);
    this.playerSessionMap = new IntMap<>(128);
    this.nextSessionId = 1;
  }

  /**
   * 设置回调
   * 
   * @param callback 回调接口
   */
  public void setCallback(TradeCallback callback) {
    this.callback = callback;
  }

  //==========================================================================
  // 交易发起/响应
  //==========================================================================

  /**
   * 发起交易请求
   * 
   * <p>对应 D2MOO PLRTRADE_TryToTrade
   * 
   * @param requesterId 发起者实体 ID
   * @param targetId 目标实体 ID
   * @return 结果代码
   */
  public int requestTrade(int requesterId, int targetId) {
    // 检查双方是否已在交易中
    if (playerSessionMap.containsKey(requesterId)) {
      log.warn("无法发起交易：玩家已在交易中, requesterId={}", requesterId);
      return TradeState.RESULT_TARGET_BUSY;
    }
    
    if (playerSessionMap.containsKey(targetId)) {
      log.warn("无法发起交易：目标已在交易中, targetId={}", targetId);
      return TradeState.RESULT_TARGET_BUSY;
    }
    
    // 检查会话数量
    if (sessions.size >= MAX_SESSIONS) {
      log.warn("无法发起交易：会话数量已达上限");
      return TradeState.RESULT_ERROR;
    }
    
    // 创建会话
    int sessionId = allocateSessionId();
    TradeSession session = new TradeSession(sessionId, requesterId, targetId);
    sessions.put(sessionId, session);
    
    playerSessionMap.put(requesterId, sessionId);
    playerSessionMap.put(targetId, sessionId);
    
    log.info("发起交易请求: sessionId={}, requesterId={}, targetId={}", 
             sessionId, requesterId, targetId);
    
    if (callback != null) {
      callback.onTradeRequest(requesterId, targetId);
    }
    
    return TradeState.RESULT_SUCCESS;
  }

  /**
   * 分配会话 ID
   * 
   * @return 新的会话 ID
   */
  private int allocateSessionId() {
    int id = nextSessionId;
    while (sessions.containsKey(id)) {
      id++;
    }
    nextSessionId = id + 1;
    return id;
  }

  /**
   * 接受交易请求
   * 
   * @param playerId 接受者实体 ID
   * @return 结果代码
   */
  public int acceptTrade(int playerId) {
    Integer sessionId = playerSessionMap.get(playerId);
    if (sessionId == null) {
      log.warn("无法接受交易：没有待处理的请求, playerId={}", playerId);
      return TradeState.RESULT_ERROR;
    }
    
    TradeSession session = sessions.get(sessionId);
    if (session == null || session.getState() != TradeState.PENDING) {
      log.warn("无法接受交易：会话无效或状态错误");
      return TradeState.RESULT_ERROR;
    }
    
    // 只有被邀请方可以接受
    if (playerId != session.getPlayer2Id()) {
      log.warn("无法接受交易：不是被邀请方");
      return TradeState.RESULT_ERROR;
    }
    
    session.setState(TradeState.TRADING);
    
    log.info("接受交易: sessionId={}", sessionId);
    
    if (callback != null) {
      callback.onTradeStart(session);
    }
    
    return TradeState.RESULT_SUCCESS;
  }

  /**
   * 拒绝交易请求
   * 
   * @param playerId 拒绝者实体 ID
   * @return 结果代码
   */
  public int declineTrade(int playerId) {
    Integer sessionId = playerSessionMap.get(playerId);
    if (sessionId == null) {
      return TradeState.RESULT_ERROR;
    }
    
    TradeSession session = sessions.get(sessionId);
    if (session == null) {
      return TradeState.RESULT_ERROR;
    }
    
    log.info("拒绝交易: sessionId={}, playerId={}", sessionId, playerId);
    
    cancelSession(session, playerId);
    return TradeState.RESULT_SUCCESS;
  }

  //==========================================================================
  // 交易操作
  //==========================================================================

  /**
   * 添加物品到交易
   * 
   * @param playerId 玩家实体 ID
   * @param slot 交易槽位
   * @return 结果代码
   */
  public int addItem(int playerId, TradeSlot slot) {
    TradeSession session = getPlayerSession(playerId);
    if (session == null) {
      return TradeState.RESULT_ERROR;
    }
    
    if (!slot.isTradable()) {
      return TradeState.RESULT_ITEM_NOT_TRADABLE;
    }
    
    if (!session.addItem(playerId, slot)) {
      return TradeState.RESULT_NO_SPACE;
    }
    
    log.debug("添加交易物品: sessionId={}, playerId={}, item={}", 
              session.getSessionId(), playerId, slot.itemName);
    
    if (callback != null) {
      callback.onTradeUpdate(session, playerId);
    }
    
    return TradeState.RESULT_SUCCESS;
  }

  /**
   * 从交易移除物品
   * 
   * @param playerId 玩家实体 ID
   * @param itemEntityId 物品实体 ID
   * @return 结果代码
   */
  public int removeItem(int playerId, int itemEntityId) {
    TradeSession session = getPlayerSession(playerId);
    if (session == null) {
      return TradeState.RESULT_ERROR;
    }
    
    TradeSlot removed = session.removeItem(playerId, itemEntityId);
    if (removed == null) {
      return TradeState.RESULT_ERROR;
    }
    
    log.debug("移除交易物品: sessionId={}, playerId={}, itemId={}", 
              session.getSessionId(), playerId, itemEntityId);
    
    if (callback != null) {
      callback.onTradeUpdate(session, playerId);
    }
    
    return TradeState.RESULT_SUCCESS;
  }

  /**
   * 设置交易金币
   * 
   * @param playerId 玩家实体 ID
   * @param gold 金币数量
   * @return 结果代码
   */
  public int setGold(int playerId, int gold) {
    TradeSession session = getPlayerSession(playerId);
    if (session == null) {
      return TradeState.RESULT_ERROR;
    }
    
    if (!session.setGold(playerId, gold)) {
      return TradeState.RESULT_ERROR;
    }
    
    log.debug("设置交易金币: sessionId={}, playerId={}, gold={}", 
              session.getSessionId(), playerId, gold);
    
    if (callback != null) {
      callback.onTradeUpdate(session, playerId);
    }
    
    return TradeState.RESULT_SUCCESS;
  }

  //==========================================================================
  // 确认/取消
  //==========================================================================

  /**
   * 确认交易
   * 
   * @param playerId 玩家实体 ID
   * @return 结果代码
   */
  public int confirmTrade(int playerId) {
    TradeSession session = getPlayerSession(playerId);
    if (session == null) {
      return TradeState.RESULT_ERROR;
    }
    
    if (!session.confirm(playerId)) {
      return TradeState.RESULT_ERROR;
    }
    
    log.info("确认交易: sessionId={}, playerId={}", session.getSessionId(), playerId);
    
    if (callback != null) {
      callback.onConfirmChanged(session, playerId, true);
    }
    
    // 检查是否双方都确认
    if (session.isBothConfirmed()) {
      return completeTrade(session);
    }
    
    return TradeState.RESULT_SUCCESS;
  }

  /**
   * 取消确认
   * 
   * @param playerId 玩家实体 ID
   * @return 结果代码
   */
  public int unconfirmTrade(int playerId) {
    TradeSession session = getPlayerSession(playerId);
    if (session == null) {
      return TradeState.RESULT_ERROR;
    }
    
    session.unconfirm(playerId);
    
    log.info("取消确认: sessionId={}, playerId={}", session.getSessionId(), playerId);
    
    if (callback != null) {
      callback.onConfirmChanged(session, playerId, false);
    }
    
    return TradeState.RESULT_SUCCESS;
  }

  /**
   * 取消交易
   * 
   * <p>对应 D2MOO PLRTRADE_StopAllPlayerInteractions
   * 
   * @param playerId 玩家实体 ID
   * @return 结果代码
   */
  public int cancelTrade(int playerId) {
    TradeSession session = getPlayerSession(playerId);
    if (session == null) {
      return TradeState.RESULT_ERROR;
    }
    
    log.info("取消交易: sessionId={}, playerId={}", session.getSessionId(), playerId);
    
    cancelSession(session, playerId);
    return TradeState.RESULT_SUCCESS;
  }

  //==========================================================================
  // 交易完成
  //==========================================================================

  /**
   * 完成交易
   * 
   * @param session 交易会话
   * @return 结果代码
   */
  private int completeTrade(TradeSession session) {
    // 验证交易有效性
    int validationResult = validateTrade(session);
    if (validationResult != TradeState.RESULT_SUCCESS) {
      log.warn("交易验证失败: sessionId={}, result={}", 
               session.getSessionId(), TradeState.getResultName(validationResult));
      return validationResult;
    }
    
    // 标记完成
    session.setState(TradeState.COMPLETED);
    
    log.info("交易完成: sessionId={}", session.getSessionId());
    
    if (callback != null) {
      callback.onTradeComplete(session);
    }
    
    // 清理会话
    cleanupSession(session);
    
    return TradeState.RESULT_SUCCESS;
  }

  /**
   * 验证交易有效性
   * 
   * @param session 交易会话
   * @return 结果代码
   */
  private int validateTrade(TradeSession session) {
    // TODO: 实现详细的验证逻辑
    // - 检查物品是否仍在玩家背包
    // - 检查金币是否足够
    // - 检查背包空间是否足够
    
    return TradeState.RESULT_SUCCESS;
  }

  //==========================================================================
  // 会话管理
  //==========================================================================

  /**
   * 取消会话
   * 
   * @param session 会话
   * @param cancelledBy 取消者实体 ID
   */
  private void cancelSession(TradeSession session, int cancelledBy) {
    session.setState(TradeState.CANCELLED);
    
    if (callback != null) {
      callback.onTradeCancelled(session, cancelledBy);
    }
    
    cleanupSession(session);
  }

  /**
   * 清理会话
   * 
   * @param session 会话
   */
  private void cleanupSession(TradeSession session) {
    playerSessionMap.remove(session.getPlayer1Id());
    playerSessionMap.remove(session.getPlayer2Id());
    sessions.remove(session.getSessionId());
    session.reset();
  }

  /**
   * 获取玩家当前的交易会话
   * 
   * @param playerId 玩家实体 ID
   * @return 交易会话，不存在返回 null
   */
  public TradeSession getPlayerSession(int playerId) {
    Integer sessionId = playerSessionMap.get(playerId);
    if (sessionId == null) {
      return null;
    }
    return sessions.get(sessionId);
  }

  /**
   * 检查玩家是否在交易中
   * 
   * <p>对应 D2MOO D2GAME_PLRTRADE_IsInteractingWithPlayer
   * 
   * @param playerId 玩家实体 ID
   * @return true 如果在交易中
   */
  public boolean isTrading(int playerId) {
    return playerSessionMap.containsKey(playerId);
  }

  //==========================================================================
  // 更新和清理
  //==========================================================================

  /**
   * 更新所有会话（检查超时）
   */
  public void update() {
    Array<TradeSession> timedOut = new Array<>();
    
    for (TradeSession session : sessions.values()) {
      if (session.isTimedOut()) {
        timedOut.add(session);
      }
    }
    
    for (TradeSession session : timedOut) {
      log.info("交易超时: sessionId={}", session.getSessionId());
      cancelSession(session, -1);
    }
  }

  /**
   * 清空所有数据
   */
  public void clear() {
    for (TradeSession session : sessions.values()) {
      session.reset();
    }
    sessions.clear();
    playerSessionMap.clear();
    nextSessionId = 1;
  }

  /**
   * 获取所有会话
   * 
   * @return 会话映射
   */
  public IntMap<TradeSession> getSessions() {
    return sessions;
  }
}
