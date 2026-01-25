/**
 * AI 系统模块 - 基于 D2MOO 移植
 *
 * <p>本包实现了暗黑破坏神2的怪物人工智能系统，包括：
 *
 * <h2>核心类</h2>
 * <ul>
 *   <li>{@link com.riiablo.engine.server.ai.AI} - AI 基类，findAI 按 MonStats.AI 反射加载具体实现</li>
 *   <li>{@link com.riiablo.engine.server.ai.Idle} - 空闲/默认 AI</li>
 *   <li>{@link com.riiablo.engine.server.ai.Npc} - NPC AI</li>
 *   <li>{@link com.riiablo.engine.server.ai.AiContext} - AI 决策上下文（MonsterData 使用）</li>
 * </ul>
 *
 * <h2>怪物 AI 实现</h2>
 * <p>各怪物类型对应 MonStats.txt 的 AI 列，如 QuillRat、Fallen、Zombie、Skeleton 等，
 * 通过 {@link com.riiablo.engine.server.ai.AI#findAI(int, String)} 动态加载。
 *
 * <h2>参考来源</h2>
 * <p>本模块参考了 D2MOO 项目的以下文件：
 * <ul>
 *   <li>D2Game/src/AI/AiThink.cpp - AI 思考/决策</li>
 *   <li>D2Game/include/AI/*.h - 相关头文件</li>
 * </ul>
 *
 * @author riiablo team
 * @see <a href="https://github.com/AruaSmith/D2MOO">D2MOO Project</a>
 */
package com.riiablo.engine.server.ai;
