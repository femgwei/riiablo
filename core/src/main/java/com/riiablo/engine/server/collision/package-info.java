/**
 * 碰撞系统模块 - 基于 D2MOD Collision 移植
 * 
 * <p>该模块管理游戏中的碰撞检测，包括：
 * <ul>
 *   <li>碰撞掩码和标志</li>
 *   <li>单位大小和模式</li>
 *   <li>边界框检测</li>
 *   <li>射线追踪</li>
 *   <li>路径碰撞检测</li>
 * </ul>
 * 
 * <h2>核心类</h2>
 * <ul>
 *   <li>{@link com.riiablo.engine.server.collision.CollisionMask} - 碰撞掩码常量</li>
 *   <li>{@link com.riiablo.engine.server.collision.CollisionSize} - 单位碰撞大小</li>
 *   <li>{@link com.riiablo.engine.server.collision.CollisionPattern} - 碰撞模式</li>
 *   <li>{@link com.riiablo.engine.server.collision.BoundingBox} - 边界框</li>
 *   <li>{@link com.riiablo.engine.server.collision.CollisionUtil} - 碰撞工具类</li>
 * </ul>
 * 
 * <h2>碰撞类型</h2>
 * <ul>
 *   <li>墙壁碰撞 - 阻挡所有单位</li>
 *   <li>物体碰撞 - 阻挡行走单位</li>
 *   <li>单位碰撞 - 玩家、怪物、宠物</li>
 *   <li>飞弹碰撞 - 投射物路径</li>
 *   <li>物品碰撞 - 地面物品</li>
 * </ul>
 * 
 * <h2>参考来源</h2>
 * <ul>
 *   <li>D2MOD/source/D2Common/include/D2Collision.h</li>
 *   <li>D2MOD/source/D2Common/src/Collision/Collision.cpp</li>
 * </ul>
 * 
 * @author riiablo team
 */
package com.riiablo.engine.server.collision;
