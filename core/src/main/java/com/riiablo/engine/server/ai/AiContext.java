package com.riiablo.engine.server.ai;

import com.badlogic.gdx.math.Vector2;

import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;

/**
 * AI 上下文信息 - 基于 D2MOO D2AiTickParamStrc 结构移植
 * 
 * <p>该类包含 AI 每次决策所需的上下文信息，包括当前目标、
 * 距离、战斗状态、怪物属性等。
 * 
 * <p>参考：D2MOO/source/D2Game/include/AI/AiGeneral.h - D2AiTickParamStrc
 * 
 * @author riiablo team
 */
public class AiContext {

  //==========================================================================
  // 实体引用
  //==========================================================================

  /** 当前 AI 所属实体 ID */
  public int entityId;

  /** 当前目标实体 ID（-1 表示无目标） */
  public int targetId = -1;

  /** 主人实体 ID（召唤物使用，-1 表示无主人） */
  public int ownerId = -1;

  //==========================================================================
  // 位置信息
  //==========================================================================

  /** 实体当前位置 */
  public final Vector2 position = new Vector2();

  /** 目标位置 */
  public final Vector2 targetPosition = new Vector2();

  /** 初始位置（出生点，用于巡逻等行为） */
  public final Vector2 spawnPosition = new Vector2();

  //==========================================================================
  // 距离信息
  //==========================================================================

  /** 到目标的距离 */
  public float targetDistance;

  /** 到主人的距离（召唤物） */
  public float ownerDistance;

  /** 到出生点的距离 */
  public float spawnDistance;

  //==========================================================================
  // 战斗状态
  //==========================================================================

  /** 是否处于战斗状态 */
  public boolean inCombat;

  /** 是否被攻击 */
  public boolean wasHit;

  /** 最后一次受击时间 */
  public float lastHitTime;

  /** 最后一次攻击时间 */
  public float lastAttackTime;

  //==========================================================================
  // 怪物数据引用
  //==========================================================================

  /** 怪物统计数据 */
  public MonStats.Entry monstats;

  /** 怪物统计数据2（包含视觉效果等） */
  public MonStats2.Entry monstats2;

  //==========================================================================
  // AI 参数（从 MonStats.txt 读取）
  //==========================================================================

  /** AI 参数数组（aip1-aip8） */
  public final int[] params = new int[8];

  /** AI 延迟（每次决策之间的间隔） */
  public float aiDelay;

  //==========================================================================
  // AI 状态
  //==========================================================================

  /** 无特殊状态 */
  public static final int STATE_NONE = 0;

  /** 当前 AI 特殊状态（STATE_* 常量） */
  public int specialState = STATE_NONE;

  /** AI 标志位 */
  public int aiFlags;

  /** 下一次允许行动的时间 */
  public float nextActionTime;

  /** 当前状态持续时间 */
  public float stateTimer;

  //==========================================================================
  // 视野和感知
  //==========================================================================

  /** 视野范围 */
  public float sightRange;

  /** 近战攻击范围 */
  public float meleeRange;

  /** 远程攻击范围 */
  public float rangedRange;

  //==========================================================================
  // 方法
  //==========================================================================

  /**
   * 重置上下文（用于对象池）
   */
  public void reset() {
    entityId = -1;
    targetId = -1;
    ownerId = -1;
    position.setZero();
    targetPosition.setZero();
    spawnPosition.setZero();
    targetDistance = 0;
    ownerDistance = 0;
    spawnDistance = 0;
    inCombat = false;
    wasHit = false;
    lastHitTime = 0;
    lastAttackTime = 0;
    monstats = null;
    monstats2 = null;
    for (int i = 0; i < params.length; i++) {
      params[i] = 0;
    }
    aiDelay = 0;
    specialState = STATE_NONE;
    aiFlags = 0;
    nextActionTime = 0;
    stateTimer = 0;
    sightRange = 0;
    meleeRange = 0;
    rangedRange = 0;
  }

  /**
   * 检查是否有有效目标
   */
  public boolean hasTarget() {
    return targetId >= 0;
  }

  /**
   * 检查是否有主人
   */
  public boolean hasOwner() {
    return ownerId >= 0;
  }

  /**
   * 检查目标是否在近战范围内
   */
  public boolean isTargetInMeleeRange() {
    return hasTarget() && targetDistance <= meleeRange;
  }

  /**
   * 检查目标是否在远程范围内
   */
  public boolean isTargetInRangedRange() {
    return hasTarget() && targetDistance <= rangedRange;
  }

  /**
   * 检查目标是否在视野范围内
   */
  public boolean isTargetInSight() {
    return hasTarget() && targetDistance <= sightRange;
  }

  /**
   * 检查是否可以执行下一个动作
   */
  public boolean canAct(float currentTime) {
    return currentTime >= nextActionTime;
  }

  /**
   * 设置下次行动时间
   */
  public void setNextActionTime(float currentTime, float delay) {
    nextActionTime = currentTime + delay;
  }

  /**
   * 获取指定 AI 参数
   * 
   * @param index 参数索引（0-7）
   * @return 参数值
   */
  public int getParam(int index) {
    if (index < 0 || index >= params.length) return 0;
    return params[index];
  }

  @Override
  public String toString() {
    return "AiContext{" +
        "entityId=" + entityId +
        ", targetId=" + targetId +
        ", targetDistance=" + targetDistance +
        ", inCombat=" + inCombat +
        ", specialState=" + specialState +
        '}';
  }
}
