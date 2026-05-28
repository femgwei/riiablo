/**
 * 投射物系统 - 基于 D2MOD MISSILES 模块移植
 * 
 * <p>管理游戏中所有投射物的数据结构、ID 和标志位。
 * 
 * <p>注意：投射物的碰撞和伤害处理已迁移到 ECS 系统
 * {@link com.riiablo.engine.server.MissileCollisionSystem}。
 * 
 * <p>主要组件：
 * <ul>
 *   <li>{@link com.riiablo.engine.server.missile.MissileData} - 投射物数据结构</li>
 *   <li>{@link com.riiablo.engine.server.missile.MissileId} - 投射物 ID 常量</li>
 *   <li>{@link com.riiablo.engine.server.missile.MissileFlags} - 投射物标志位</li>
 * </ul>
 * 
 * <p>参考：D2MOD/source/D2Game/src/MISSILES/
 * 
 * @author riiablo team
 */
package com.riiablo.engine.server.missile;
