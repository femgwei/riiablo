/**
 * 状态系统模块 - 基于 D2MOO D2States 移植
 * 
 * <p>该模块管理游戏中的各种状态效果（buff/debuff），包括：
 * <ul>
 *   <li>增益效果（光环、护盾、强化）</li>
 *   <li>减益效果（诅咒、中毒、减速）</li>
 *   <li>变形效果（狼/熊形态）</li>
 *   <li>持续伤害效果（燃烧、中毒）</li>
 * </ul>
 * 
 * <h2>核心类</h2>
 * <ul>
 *   <li>{@link com.riiablo.engine.server.state.StateId} - 状态ID枚举，对应 states.txt</li>
 *   <li>{@link com.riiablo.engine.server.state.StateMask} - 状态掩码，用于分类状态</li>
 *   <li>{@link com.riiablo.engine.server.state.UnitState} - 单位状态实例</li>
 *   <li>{@link com.riiablo.engine.server.state.StateManager} - 状态管理器</li>
 * </ul>
 * 
 * <h2>状态分类</h2>
 * <ul>
 *   <li>AURA - 光环类状态</li>
 *   <li>CURSE - 诅咒类状态</li>
 *   <li>TRANSFORM - 变形类状态</li>
 *   <li>ACTIVE - 主动技能状态</li>
 *   <li>PASSIVE - 被动技能状态</li>
 * </ul>
 * 
 * <h2>参考来源</h2>
 * <ul>
 *   <li>D2MOO/source/D2Common/include/D2States.h</li>
 *   <li>D2MOO/source/D2Common/src/D2States.cpp</li>
 * </ul>
 * 
 * @author riiablo team
 */
package com.riiablo.engine.server.state;
