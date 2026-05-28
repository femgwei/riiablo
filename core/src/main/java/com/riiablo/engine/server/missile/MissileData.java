package com.riiablo.engine.server.missile;

import com.badlogic.gdx.math.Vector2;

/**
 * 投射物数据结构 - 基于 D2MOD D2MissileStrc 移植
 * 
 * <p>存储单个投射物的所有运行时数据。
 * 
 * <p>参考：D2MOD/source/D2Game/src/MISSILES/Missiles.h
 * 
 * @author riiablo team
 */
public class MissileData {

  //==========================================================================
  // 基本属性
  //==========================================================================

  /** 投射物 ID */
  public int missileId;

  /** 投射物标志 */
  public int flags;

  /** 所属技能 ID */
  public int skillId;

  /** 技能等级 */
  public int skillLevel;

  /** 所有者实体 ID */
  public int ownerId;

  /** 目标实体 ID（追踪投射物使用） */
  public int targetId;

  //==========================================================================
  // 位置和移动
  //==========================================================================

  /** 当前位置 X */
  public float posX;

  /** 当前位置 Y */
  public float posY;

  /** 起始位置 X */
  public float startX;

  /** 起始位置 Y */
  public float startY;

  /** 目标位置 X */
  public float targetX;

  /** 目标位置 Y */
  public float targetY;

  /** 速度 X 分量 */
  public float velocityX;

  /** 速度 Y 分量 */
  public float velocityY;

  /** 基础速度 */
  public float baseSpeed;

  /** 加速度 */
  public float acceleration;

  /** 最大速度 */
  public float maxSpeed;

  /** 当前朝向（弧度） */
  public float direction;

  /** 角速度（追踪投射物转向速度） */
  public float angularVelocity;

  //==========================================================================
  // 生命周期
  //==========================================================================

  /** 已存在帧数 */
  public int frameCount;

  /** 最大存活帧数 */
  public int maxFrames;

  /** 是否已激活 */
  public boolean active;

  /** 是否已命中目标 */
  public boolean hasHit;

  /** 穿透次数剩余 */
  public int pierceRemaining;

  /** 分裂次数剩余 */
  public int splitsRemaining;

  //==========================================================================
  // 伤害数据
  //==========================================================================

  /** 最小物理伤害 */
  public int minPhysicalDamage;

  /** 最大物理伤害 */
  public int maxPhysicalDamage;

  /** 最小火焰伤害 */
  public int minFireDamage;

  /** 最大火焰伤害 */
  public int maxFireDamage;

  /** 最小冰冷伤害 */
  public int minColdDamage;

  /** 最大冰冷伤害 */
  public int maxColdDamage;

  /** 冰冷持续时间（帧） */
  public int coldDuration;

  /** 最小闪电伤害 */
  public int minLightningDamage;

  /** 最大闪电伤害 */
  public int maxLightningDamage;

  /** 毒素伤害 */
  public int poisonDamage;

  /** 毒素持续时间（帧） */
  public int poisonDuration;

  /** 最小魔法伤害 */
  public int minMagicDamage;

  /** 最大魔法伤害 */
  public int maxMagicDamage;

  //==========================================================================
  // 特殊效果
  //==========================================================================

  /** 击退距离 */
  public float knockbackDistance;

  /** 眩晕时间（帧） */
  public int stunDuration;

  /** 爆炸半径 */
  public float explosionRadius;

  /** 子投射物 ID（爆炸或分裂时生成） */
  public int childMissileId;

  /** 子投射物数量 */
  public int childMissileCount;

  //==========================================================================
  // 碰撞
  //==========================================================================

  /** 碰撞半径 */
  public float collisionRadius;

  /** 碰撞高度 */
  public float collisionHeight;

  /** 已命中的目标列表（防止重复命中） */
  private int[] hitTargets;
  private int hitTargetCount;
  private static final int MAX_HIT_TARGETS = 32;

  //==========================================================================
  // 构造函数
  //==========================================================================

  public MissileData() {
    hitTargets = new int[MAX_HIT_TARGETS];
    reset();
  }

  /**
   * 重置所有数据
   */
  public void reset() {
    missileId = MissileId.NONE;
    flags = 0;
    skillId = 0;
    skillLevel = 0;
    ownerId = -1;
    targetId = -1;

    posX = 0;
    posY = 0;
    startX = 0;
    startY = 0;
    targetX = 0;
    targetY = 0;
    velocityX = 0;
    velocityY = 0;
    baseSpeed = 0;
    acceleration = 0;
    maxSpeed = 0;
    direction = 0;
    angularVelocity = 0;

    frameCount = 0;
    maxFrames = 0;
    active = false;
    hasHit = false;
    pierceRemaining = 0;
    splitsRemaining = 0;

    minPhysicalDamage = 0;
    maxPhysicalDamage = 0;
    minFireDamage = 0;
    maxFireDamage = 0;
    minColdDamage = 0;
    maxColdDamage = 0;
    coldDuration = 0;
    minLightningDamage = 0;
    maxLightningDamage = 0;
    poisonDamage = 0;
    poisonDuration = 0;
    minMagicDamage = 0;
    maxMagicDamage = 0;

    knockbackDistance = 0;
    stunDuration = 0;
    explosionRadius = 0;
    childMissileId = MissileId.NONE;
    childMissileCount = 0;

    collisionRadius = 0.5f;
    collisionHeight = 1.0f;

    hitTargetCount = 0;
  }

  //==========================================================================
  // 命中目标管理
  //==========================================================================

  /**
   * 检查目标是否已被命中
   */
  public boolean hasHitTarget(int targetId) {
    for (int i = 0; i < hitTargetCount; i++) {
      if (hitTargets[i] == targetId) {
        return true;
      }
    }
    return false;
  }

  /**
   * 记录命中目标
   */
  public void addHitTarget(int targetId) {
    if (hitTargetCount < MAX_HIT_TARGETS) {
      hitTargets[hitTargetCount++] = targetId;
    }
  }

  /**
   * 清除命中记录
   */
  public void clearHitTargets() {
    hitTargetCount = 0;
  }

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 计算到目标的距离
   */
  public float getDistanceToTarget() {
    float dx = targetX - posX;
    float dy = targetY - posY;
    return (float) Math.sqrt(dx * dx + dy * dy);
  }

  /**
   * 计算已飞行距离
   */
  public float getDistanceTraveled() {
    float dx = posX - startX;
    float dy = posY - startY;
    return (float) Math.sqrt(dx * dx + dy * dy);
  }

  /**
   * 检查是否已超时
   */
  public boolean isExpired() {
    return maxFrames > 0 && frameCount >= maxFrames;
  }

  /**
   * 获取当前速度大小
   */
  public float getCurrentSpeed() {
    return (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY);
  }

  /**
   * 设置速度方向
   */
  public void setVelocityFromDirection(float speed) {
    velocityX = (float) Math.cos(direction) * speed;
    velocityY = (float) Math.sin(direction) * speed;
  }

  /**
   * 计算指向目标的方向
   */
  public float getDirectionToTarget() {
    return (float) Math.atan2(targetY - posY, targetX - posX);
  }

  /**
   * 计算指向目标实体的方向
   */
  public float getDirectionTo(float x, float y) {
    return (float) Math.atan2(y - posY, x - posX);
  }

  @Override
  public String toString() {
    return "MissileData{id=" + missileId + ", pos=(" + posX + "," + posY + 
        "), frame=" + frameCount + "/" + maxFrames + ", active=" + active + "}";
  }
}
