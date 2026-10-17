package com.riiablo.engine.server.object;

/**
 * 场景对象数据结构 - 基于 D2MOD D2ObjectStrc 移植
 * 
 * <p>存储单个场景对象的所有运行时数据。
 * 
 * <p>参考：D2MOD/source/D2Game/src/OBJECTS/Objects.h
 * 
 * @author riiablo team
 */
public class ObjectData {

  //==========================================================================
  // 基本属性
  //==========================================================================

  /** 对象唯一 ID */
  public int objectId;

  /** 对象类型 */
  public int objectType;

  /** 对象类型索引（来自 Objects.txt） */
  public int classIndex;

  /** 关联的实体 ID */
  public int entityId;

  /** 所在场景 ID */
  public int areaId;

  //==========================================================================
  // 位置
  //==========================================================================

  /** 位置 X（子格） */
  public int posX;

  /** 位置 Y（子格） */
  public int posY;

  //==========================================================================
  // 状态
  //==========================================================================

  /** 当前模式（开/关/操作中等） */
  public int mode;

  /** 是否可交互 */
  public boolean interactable;

  /** 是否已被使用/开启 */
  public boolean used;

  /** 是否锁定 */
  public boolean locked;

  /** 锁定等级（需要钥匙等级） */
  public int lockLevel;

  /** 是否被困（陷阱） */
  public boolean trapped;

  /** 陷阱伤害 */
  public int trapDamage;

  //==========================================================================
  // 神殿属性
  //==========================================================================

  /** 神殿类型（如果是神殿） */
  public int shrineType;

  /** 神殿效果是否活跃 */
  public boolean shrineActive;

  /** 神殿重置帧数 */
  public int shrineResetFrame;

  //==========================================================================
  // 容器属性
  //==========================================================================

  /** 容器品质（影响掉落） */
  public int containerQuality;

  /** 是否已打开 */
  public boolean opened;

  /** 是否已搜索/已掉落物品 */
  public boolean looted;

  //==========================================================================
  // 门属性
  //==========================================================================

  /** 门是否打开 */
  public boolean doorOpen;

  /** 门打开方向 */
  public int doorDirection;

  //==========================================================================
  // 传送门属性
  //==========================================================================

  /** 传送目标场景 ID */
  public int portalTargetArea;

  /** 传送门所有者（玩家传送门） */
  public int portalOwnerId;

  /** 传送门剩余时间 */
  public int portalDuration;

  //==========================================================================
  // 传送点属性
  //==========================================================================

  /** 传送点是否已激活 */
  public boolean waypointActive;

  //==========================================================================
  // 动画
  //==========================================================================

  /** 当前动画帧 */
  public int animFrame;

  /** 动画是否循环 */
  public boolean animLoop;

  //==========================================================================
  // 构造函数
  //==========================================================================

  public ObjectData() {
    reset();
  }

  /**
   * 重置所有数据
   */
  public void reset() {
    objectId = -1;
    objectType = ObjectType.NONE;
    classIndex = 0;
    entityId = -1;
    areaId = 0;

    posX = 0;
    posY = 0;

    mode = 0;
    interactable = true;
    used = false;
    locked = false;
    lockLevel = 0;
    trapped = false;
    trapDamage = 0;

    shrineType = ShrineType.NONE;
    shrineActive = false;
    shrineResetFrame = 0;

    containerQuality = 0;
    opened = false;
    looted = false;

    doorOpen = false;
    doorDirection = 0;

    portalTargetArea = 0;
    portalOwnerId = -1;
    portalDuration = 0;

    waypointActive = false;

    animFrame = 0;
    animLoop = false;
  }

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 是否是容器
   */
  public boolean isContainer() {
    return ObjectType.isContainer(objectType);
  }

  /**
   * 是否是神殿
   */
  public boolean isShrine() {
    return ObjectType.isShrine(objectType) && shrineType != ShrineType.NONE;
  }

  /**
   * 是否是门
   */
  public boolean isDoor() {
    return ObjectType.isDoor(objectType);
  }

  /**
   * 是否可以交互
   */
  public boolean canInteract() {
    if (!interactable) {
      return false;
    }

    // 容器只能开一次
    if (isContainer() && opened) {
      return false;
    }

    // 神殿需要检查重置
    if (isShrine() && shrineActive) {
      return false;
    }

    return true;
  }

  /**
   * 打开容器
   */
  public void open() {
    if (isContainer()) {
      opened = true;
      used = true;
      mode = 1; // 打开模式
    } else if (isDoor()) {
      doorOpen = true;
      mode = 1;
    }
  }

  /**
   * 关闭门
   */
  public void close() {
    if (isDoor()) {
      doorOpen = false;
      mode = 0;
    }
  }

  /**
   * 激活神殿
   * 
   * @param currentFrame 当前游戏帧
   */
  public void activateShrine(long currentFrame) {
    if (!isShrine()) {
      return;
    }
    shrineActive = true;
    used = true;
    // 神殿在一定时间后可以重新使用（在一些版本中）
    shrineResetFrame = -1; // 永不重置
  }

  /**
   * 检查神殿是否可用
   */
  public boolean canUseShrine(long currentFrame) {
    if (!isShrine()) {
      return false;
    }
    if (!shrineActive) {
      return true;
    }
    if (shrineResetFrame > 0 && currentFrame >= shrineResetFrame) {
      shrineActive = false;
      return true;
    }
    return false;
  }

  @Override
  public String toString() {
    return "ObjectData{type=" + ObjectType.getName(objectType) + 
        ", pos=(" + posX + "," + posY + 
        "), used=" + used + "}";
  }
}
