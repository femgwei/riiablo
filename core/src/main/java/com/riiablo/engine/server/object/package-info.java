/**
 * 场景对象系统 - 基于 D2MOO Objects 模块移植
 * 
 * <p>管理游戏中所有场景对象的创建、交互和效果。
 * 
 * <p>主要组件：
 * <ul>
 *   <li>{@link com.riiablo.engine.server.object.ObjectManager} - 对象管理器</li>
 *   <li>{@link com.riiablo.engine.server.object.ObjectData} - 对象数据结构</li>
 *   <li>{@link com.riiablo.engine.server.object.ObjectType} - 对象类型常量</li>
 *   <li>{@link com.riiablo.engine.server.object.ShrineType} - 神殿类型常量</li>
 * </ul>
 * 
 * <p>支持的对象类型：
 * <ul>
 *   <li>容器：箱子、桶、罐子、石棺</li>
 *   <li>神殿：各种增益神殿、宝石神殿、井</li>
 *   <li>传送：传送门、传送点</li>
 *   <li>门：可开关的门</li>
 *   <li>任务对象：各种任务相关的特殊对象</li>
 * </ul>
 * 
 * <p>参考：D2MOO/source/D2Game/src/OBJECTS/Objects.cpp
 * 
 * @author riiablo team
 */
package com.riiablo.engine.server.object;
