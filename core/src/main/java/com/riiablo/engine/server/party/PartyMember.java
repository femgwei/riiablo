package com.riiablo.engine.server.party;

/**
 * 队伍成员数据 - 基于 D2MOD D2PartyNodeStrc 移植
 * 
 * <p>存储单个队伍成员的运行时数据。
 * 
 * <p>参考：D2MOD/source/D2Game/include/UNIT/Party.h
 * 
 * @author riiablo team
 */
public class PartyMember {

  //==========================================================================
  // 成员数据
  //==========================================================================

  /** 玩家实体 ID（GUID） */
  public int entityId;
  
  /** 玩家名称 */
  public String name;
  
  /** 玩家职业 ID */
  public int classId;
  
  /** 玩家等级 */
  public int level;
  
  /** 当前生命值 */
  public int currentHp;
  
  /** 最大生命值 */
  public int maxHp;
  
  /** 当前法力值 */
  public int currentMana;
  
  /** 最大法力值 */
  public int maxMana;
  
  /** 当前所在场景 ID */
  public int levelId;
  
  /** X 坐标 */
  public int x;
  
  /** Y 坐标 */
  public int y;
  
  /** 是否存活 */
  public boolean alive;
  
  /** 是否在线 */
  public boolean online;

  //==========================================================================
  // 构造函数
  //==========================================================================

  /**
   * 创建空的队伍成员
   */
  public PartyMember() {
    reset();
  }

  /**
   * 创建队伍成员
   * 
   * @param entityId 玩家实体 ID
   * @param name 玩家名称
   * @param classId 职业 ID
   * @param level 等级
   */
  public PartyMember(int entityId, String name, int classId, int level) {
    this.entityId = entityId;
    this.name = name;
    this.classId = classId;
    this.level = level;
    this.alive = true;
    this.online = true;
    this.currentHp = 0;
    this.maxHp = 0;
    this.currentMana = 0;
    this.maxMana = 0;
    this.levelId = 0;
    this.x = 0;
    this.y = 0;
  }

  //==========================================================================
  // 方法
  //==========================================================================

  /**
   * 重置成员数据
   */
  public void reset() {
    entityId = -1;
    name = "";
    classId = 0;
    level = 1;
    currentHp = 0;
    maxHp = 0;
    currentMana = 0;
    maxMana = 0;
    levelId = 0;
    x = 0;
    y = 0;
    alive = false;
    online = false;
  }

  /**
   * 更新成员状态
   * 
   * @param hp 当前生命
   * @param maxHp 最大生命
   * @param mana 当前法力
   * @param maxMana 最大法力
   * @param levelId 场景 ID
   * @param x X 坐标
   * @param y Y 坐标
   * @param alive 是否存活
   */
  public void update(int hp, int maxHp, int mana, int maxMana, 
                     int levelId, int x, int y, boolean alive) {
    this.currentHp = hp;
    this.maxHp = maxHp;
    this.currentMana = mana;
    this.maxMana = maxMana;
    this.levelId = levelId;
    this.x = x;
    this.y = y;
    this.alive = alive;
  }

  /**
   * 获取生命百分比
   * 
   * @return 生命百分比 (0-100)
   */
  public int getHpPercent() {
    if (maxHp <= 0) return 0;
    return currentHp * 100 / maxHp;
  }

  /**
   * 获取法力百分比
   * 
   * @return 法力百分比 (0-100)
   */
  public int getManaPercent() {
    if (maxMana <= 0) return 0;
    return currentMana * 100 / maxMana;
  }

  /**
   * 检查成员是否有效
   * 
   * @return true 如果有效
   */
  public boolean isValid() {
    return entityId >= 0 && online;
  }

  /**
   * 检查是否在同一场景
   * 
   * @param otherLevelId 其他玩家的场景 ID
   * @return true 如果在同一场景
   */
  public boolean isInSameLevel(int otherLevelId) {
    return levelId == otherLevelId;
  }

  @Override
  public String toString() {
    return "PartyMember{" +
        "entityId=" + entityId +
        ", name='" + name + '\'' +
        ", level=" + level +
        ", hp=" + currentHp + "/" + maxHp +
        ", alive=" + alive +
        ", levelId=" + levelId +
        '}';
  }
}
