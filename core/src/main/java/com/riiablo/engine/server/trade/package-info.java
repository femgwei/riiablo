/**
 * 交易系统模块 - 基于 D2MOO PlrTrade 移植
 * 
 * <p>该模块提供玩家之间的交易功能：
 * <ul>
 *   <li>{@link com.riiablo.engine.server.trade.TradeState} - 交易状态常量</li>
 *   <li>{@link com.riiablo.engine.server.trade.TradeSlot} - 交易槽位数据</li>
 *   <li>{@link com.riiablo.engine.server.trade.TradeSession} - 交易会话</li>
 *   <li>{@link com.riiablo.engine.server.trade.TradeManager} - 交易管理器</li>
 * </ul>
 * 
 * <h2>核心功能</h2>
 * <ul>
 *   <li>发起/接受/拒绝交易请求</li>
 *   <li>交易物品放入/取出</li>
 *   <li>交易金币设置</li>
 *   <li>确认/取消交易</li>
 *   <li>交易验证和完成</li>
 * </ul>
 * 
 * <h2>交易规则</h2>
 * <ul>
 *   <li>双方必须在同一场景</li>
 *   <li>双方必须都确认才能完成交易</li>
 *   <li>任一方修改交易内容会重置确认状态</li>
 *   <li>交易完成后物品和金币交换</li>
 * </ul>
 * 
 * <h2>交易槽位</h2>
 * <ul>
 *   <li>每个玩家有 4x4 = 16 个物品槽</li>
 *   <li>金币单独计算，不占用槽位</li>
 * </ul>
 * 
 * <h2>参考来源</h2>
 * <ul>
 *   <li>D2MOO/source/D2Game/include/PLAYER/PlrTrade.h</li>
 *   <li>D2MOO/source/D2Game/src/PLAYER/PlrTrade.cpp</li>
 * </ul>
 * 
 * @author riiablo team
 */
package com.riiablo.engine.server.trade;
