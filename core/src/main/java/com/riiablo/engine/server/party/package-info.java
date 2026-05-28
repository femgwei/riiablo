/**
 * 队伍系统模块 - 基于 D2MOD Party 移植
 * 
 * <p>该模块提供多人游戏中的队伍功能：
 * <ul>
 *   <li>{@link com.riiablo.engine.server.party.PartyId} - 队伍 ID 常量</li>
 *   <li>{@link com.riiablo.engine.server.party.PartyRelation} - 队伍关系类型</li>
 *   <li>{@link com.riiablo.engine.server.party.PartyMember} - 队伍成员数据</li>
 *   <li>{@link com.riiablo.engine.server.party.Party} - 队伍实例</li>
 *   <li>{@link com.riiablo.engine.server.party.PartyManager} - 队伍管理器</li>
 * </ul>
 * 
 * <h2>核心功能</h2>
 * <ul>
 *   <li>创建/加入/离开队伍</li>
 *   <li>队伍成员管理</li>
 *   <li>金币共享分配</li>
 *   <li>经验共享计算</li>
 *   <li>敌对/友好关系</li>
 *   <li>队伍邀请系统</li>
 * </ul>
 * 
 * <h2>队伍规则</h2>
 * <ul>
 *   <li>最大队伍人数：8 人</li>
 *   <li>金币共享：同一场景内的存活成员平分</li>
 *   <li>经验共享：同一场景内按等级加权分配</li>
 *   <li>敌对：队伍成员之间无法伤害</li>
 * </ul>
 * 
 * <h2>参考来源</h2>
 * <ul>
 *   <li>D2MOD/source/D2Game/include/UNIT/Party.h</li>
 *   <li>D2MOD/source/D2Game/src/UNIT/Party.cpp</li>
 *   <li>D2MOD/source/D2Game/src/PLAYER/PartyScreen.cpp</li>
 * </ul>
 * 
 * @author riiablo team
 */
package com.riiablo.engine.server.party;
