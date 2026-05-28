/**
 * 战斗系统模块 - 基于 D2MOD 移植
 * 
 * <p>本包实现了暗黑破坏神2的战斗伤害计算系统，包括：
 * 
 * <h2>核心类</h2>
 * <ul>
 *   <li>{@link com.riiablo.engine.server.combat.DamageCalculator} - 伤害计算器（单例）</li>
 *   <li>{@link com.riiablo.engine.server.combat.DamageResult} - 伤害计算结果</li>
 * </ul>
 * 
 * <h2>伤害类型</h2>
 * <ul>
 *   <li><b>物理伤害</b> - 武器伤害，受力量/敏捷加成</li>
 *   <li><b>火焰伤害</b> - 火系法术，可造成灼烧</li>
 *   <li><b>闪电伤害</b> - 电系法术</li>
 *   <li><b>冰冷伤害</b> - 冰系法术，可造成减速/冻结</li>
 *   <li><b>魔法伤害</b> - 无属性魔法伤害</li>
 *   <li><b>毒素伤害</b> - 持续伤害（DoT）</li>
 * </ul>
 * 
 * <h2>伤害计算流程</h2>
 * <ol>
 *   <li>命中判定 - 攻击者AR vs 防御者Def</li>
 *   <li>闪避/格挡判定</li>
 *   <li>物理伤害计算 - 基础伤害 + 属性加成</li>
 *   <li>暴击判定 - 致命一击/暴击</li>
 *   <li>元素伤害计算 - 精通加成</li>
 *   <li>抗性减免 - 考虑穿透和吸收</li>
 *   <li>偷取计算 - 生命/法力偷取</li>
 * </ol>
 * 
 * <h2>参考来源</h2>
 * <p>本模块参考了 D2MOD 项目的以下文件：
 * <ul>
 *   <li>D2Game/src/UNIT/SUnitDmg.cpp - 伤害计算核心逻辑</li>
 *   <li>D2Game/include/UNIT/SUnitDmg.h - 数据结构定义</li>
 * </ul>
 * 
 * @author riiablo team
 * @see <a href="https://github.com/AruaSmith/D2MOD">D2MOD Project</a>
 */
package com.riiablo.engine.server.combat;
