package com.riiablo.engine.server.pet;

/**
 * 宠物数据结构 - 基于 D2MOO D2PetStrc 移植
 * 
 * <p>存储单个宠物/召唤物的所有运行时数据。
 * 
 * <p>参考：D2MOO/source/D2Game/src/PLAYER/PlayerPets.h
 * 
 * @author riiablo team
 */
public class PetData {

  //==========================================================================
  // 基本属性
  //==========================================================================

  /** 宠物唯一 ID */
  public int petId;

  /** 宠物类型 */
  public int petType;

  /** 所有者实体 ID */
  public int ownerId;

  /** 关联的技能 ID（召唤技能） */
  public int skillId;

  /** 技能等级 */
  public int skillLevel;

  /** 宠物实体 ID（对应 Artemis 实体） */
  public int entityId;

  //==========================================================================
  // 状态
  //==========================================================================

  /** 是否存活 */
  public boolean alive;

  /** 当前生命值 */
  public int currentHp;

  /** 最大生命值 */
  public int maxHp;

  /** 当前法力值（部分召唤物有法力） */
  public int currentMana;

  /** 最大法力值 */
  public int maxMana;

  //==========================================================================
  // 生命周期
  //==========================================================================

  /** 创建时间（游戏帧） */
  public long createdFrame;

  /** 生存时间限制（帧，0=永久） */
  public int duration;

  /** 剩余生存时间（帧） */
  public int remainingDuration;

  //==========================================================================
  // 战斗属性
  //==========================================================================

  /** 最小伤害 */
  public int minDamage;

  /** 最大伤害 */
  public int maxDamage;

  /** 命中率 */
  public int attackRating;

  /** 防御值 */
  public int defense;

  /** 移动速度 */
  public int moveSpeed;

  /** 攻击速度 */
  public int attackSpeed;

  //==========================================================================
  // 抗性
  //==========================================================================

  /** 火焰抗性 */
  public int fireResist;

  /** 冰冷抗性 */
  public int coldResist;

  /** 闪电抗性 */
  public int lightningResist;

  /** 毒素抗性 */
  public int poisonResist;

  //==========================================================================
  // AI 控制
  //==========================================================================

  /** 当前 AI 状态 */
  public int aiState;

  /** 当前目标实体 ID */
  public int targetId;

  /** AI 模式（跟随、攻击、守护等） */
  public int aiMode;

  //==========================================================================
  // 特殊属性（石魔专用）
  //==========================================================================

  /** 铁石魔来源物品（物品的属性会转移给石魔） */
  public int sourceItemId;

  /** 血石魔生命偷取 */
  public int bloodGolemLifeLeech;

  //==========================================================================
  // 构造函数
  //==========================================================================

  public PetData() {
    reset();
  }

  /**
   * 重置所有数据
   */
  public void reset() {
    petId = -1;
    petType = PetType.NONE;
    ownerId = -1;
    skillId = -1;
    skillLevel = 0;
    entityId = -1;

    alive = false;
    currentHp = 0;
    maxHp = 0;
    currentMana = 0;
    maxMana = 0;

    createdFrame = 0;
    duration = 0;
    remainingDuration = 0;

    minDamage = 0;
    maxDamage = 0;
    attackRating = 0;
    defense = 0;
    moveSpeed = 0;
    attackSpeed = 0;

    fireResist = 0;
    coldResist = 0;
    lightningResist = 0;
    poisonResist = 0;

    aiState = 0;
    targetId = -1;
    aiMode = 0;

    sourceItemId = -1;
    bloodGolemLifeLeech = 0;
  }

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 检查是否过期
   */
  public boolean isExpired() {
    return duration > 0 && remainingDuration <= 0;
  }

  /**
   * 检查是否有时间限制
   */
  public boolean hasTimeLimit() {
    return duration > 0;
  }

  /**
   * 获取生命百分比
   */
  public float getHpPercent() {
    if (maxHp <= 0) return 0;
    return (float) currentHp / maxHp;
  }

  /**
   * 是否满血
   */
  public boolean isFullHp() {
    return currentHp >= maxHp;
  }

  /**
   * 受到伤害
   * 
   * @param damage 伤害值
   * @return 实际造成的伤害
   */
  public int takeDamage(int damage) {
    if (!alive || damage <= 0) {
      return 0;
    }

    int actualDamage = Math.min(damage, currentHp);
    currentHp -= actualDamage;

    if (currentHp <= 0) {
      currentHp = 0;
      alive = false;
    }

    return actualDamage;
  }

  /**
   * 治疗
   * 
   * @param amount 治疗量
   * @return 实际治疗量
   */
  public int heal(int amount) {
    if (!alive || amount <= 0) {
      return 0;
    }

    int actualHeal = Math.min(amount, maxHp - currentHp);
    currentHp += actualHeal;
    return actualHeal;
  }

  @Override
  public String toString() {
    return "PetData{type=" + PetType.getName(petType) + 
        ", owner=" + ownerId + 
        ", hp=" + currentHp + "/" + maxHp + 
        ", alive=" + alive + "}";
  }
}
