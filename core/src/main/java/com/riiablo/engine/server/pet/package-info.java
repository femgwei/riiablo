/**
 * 宠物系统 - 基于 D2MOO PlayerPets 模块移植
 * 
 * <p>管理游戏中所有召唤物/宠物的创建、更新和销毁。
 * 
 * <p>主要组件：
 * <ul>
 *   <li>{@link com.riiablo.engine.server.pet.PetManager} - 宠物管理器</li>
 *   <li>{@link com.riiablo.engine.server.pet.PetData} - 宠物数据结构</li>
 *   <li>{@link com.riiablo.engine.server.pet.PetType} - 宠物类型常量</li>
 * </ul>
 * 
 * <p>支持的召唤物类型：
 * <ul>
 *   <li>死灵法师：骷髅战士、骷髅法师、各种石魔、复活怪物</li>
 *   <li>德鲁伊：乌鸦、狼、灰熊、藤蔓、橡木圣灵等</li>
 *   <li>亚马逊：女武神、诱饵</li>
 *   <li>刺客：影子战士、影子大师、各种陷阱</li>
 *   <li>法师：九头蛇</li>
 * </ul>
 * 
 * <p>参考：D2MOO/source/D2Game/src/PLAYER/PlayerPets.cpp
 * 
 * @author riiablo team
 */
package com.riiablo.engine.server.pet;
