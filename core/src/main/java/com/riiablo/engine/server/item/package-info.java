/**
 * 物品系统模块 - 基于 D2MOO Items 移植
 * 
 * <p>该模块管理游戏中的物品，包括：
 * <ul>
 *   <li>物品品质和类型</li>
 *   <li>物品标志和模式</li>
 *   <li>物品掉落和生成</li>
 *   <li>物品属性计算</li>
 *   <li>耐久度管理</li>
 * </ul>
 * 
 * <h2>核心类</h2>
 * <ul>
 *   <li>{@link com.riiablo.engine.server.item.ItemQuality} - 物品品质枚举</li>
 *   <li>{@link com.riiablo.engine.server.item.ItemType} - 物品类型枚举</li>
 *   <li>{@link com.riiablo.engine.server.item.ItemMode} - 物品模式枚举</li>
 *   <li>{@link com.riiablo.engine.server.item.ItemFlags} - 物品标志位</li>
 *   <li>{@link com.riiablo.engine.server.item.ItemUtil} - 物品工具类</li>
 * </ul>
 * 
 * <h2>物品品质</h2>
 * <ul>
 *   <li>INFERIOR - 劣质</li>
 *   <li>NORMAL - 普通</li>
 *   <li>SUPERIOR - 超强</li>
 *   <li>MAGIC - 魔法</li>
 *   <li>SET - 套装</li>
 *   <li>RARE - 稀有</li>
 *   <li>UNIQUE - 暗金</li>
 *   <li>CRAFT - 手工</li>
 * </ul>
 * 
 * <h2>参考来源</h2>
 * <ul>
 *   <li>D2MOO/source/D2Common/include/D2Items.h</li>
 *   <li>D2MOO/source/D2Game/src/ITEMS/Items.cpp</li>
 *   <li>D2MOO/source/D2Game/src/ITEMS/ItemsMagic.cpp</li>
 * </ul>
 * 
 * @author riiablo team
 */
package com.riiablo.engine.server.item;
