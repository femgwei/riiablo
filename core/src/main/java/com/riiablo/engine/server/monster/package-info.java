/**
 * 怪物系统模块 - 基于 D2MOO Monster 移植
 * 
 * <p>该模块管理游戏中的怪物，包括：
 * <ul>
 *   <li>怪物初始化和属性计算</li>
 *   <li>怪物类型和标志</li>
 *   <li>玩家数量加成计算</li>
 *   <li>怪物召唤标志</li>
 *   <li>难度调整</li>
 * </ul>
 * 
 * <h2>核心类</h2>
 * <ul>
 *   <li>{@link com.riiablo.engine.server.monster.MonsterType} - 怪物类型枚举</li>
 *   <li>{@link com.riiablo.engine.server.monster.MonsterFlags} - 怪物标志位</li>
 *   <li>{@link com.riiablo.engine.server.monster.MonsterData} - 怪物运行时数据</li>
 *   <li>{@link com.riiablo.engine.server.monster.MonsterUtil} - 怪物工具类</li>
 * </ul>
 * 
 * <h2>难度系统</h2>
 * <ul>
 *   <li>NORMAL - 普通难度</li>
 *   <li>NIGHTMARE - 噩梦难度</li>
 *   <li>HELL - 地狱难度</li>
 * </ul>
 * 
 * <h2>玩家数量加成</h2>
 * <p>怪物的生命值和经验值会根据游戏中的玩家数量进行调整。
 * 
 * <h2>参考来源</h2>
 * <ul>
 *   <li>D2MOO/source/D2Game/include/MONSTER/Monster.h</li>
 *   <li>D2MOO/source/D2Game/src/MONSTER/Monster.cpp</li>
 *   <li>D2MOO/source/D2Game/src/MONSTER/MonsterMode.cpp</li>
 * </ul>
 * 
 * @author riiablo team
 */
package com.riiablo.engine.server.monster;
