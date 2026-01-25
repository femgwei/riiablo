package com.riiablo.engine.client.automap;

/**
 * 小地图图标类型常量
 * 定义了游戏中各种特殊物体在小地图上的图标ID
 * 
 * 参考: D2MOO D2C_AutomapCells
 */
public final class AutomapIconType {
  private AutomapIconType() {}

  // ==================== 基础图标 ====================
  
  /** 红十字 - 通常用于标记治疗/神殿 */
  public static final int RED_CROSS = 221;
  
  /** 锤子 - 铁匠 */
  public static final int HAMMER = 302;
  
  /** 凯恩的笼子 - 崔斯特瑞姆 */
  public static final int CAIN_CAGE = 303;
  
  /** 墨菲斯托宝珠 - 崔凡克神殿的强制宝珠 */
  public static final int MEPH_ORB = 305;
  
  /** 暗黑破坏神封印 - 混沌避难所封印 */
  public static final int DIABLO_SEAL = 306;
  
  /** 传送点 */
  public static final int WAYPOINT = 307;
  
  /** 水井 */
  public static final int WELL = 309;
  
  /** 神殿 */
  public static final int SHRINE = 310;
  
  /** 堕落营地旗帜 - 带骷髅的旗帜 */
  public static final int FALLEN_CAMP_FLAG = 312;
  
  /** 伊尼法斯之树 */
  public static final int INI_TREE = 313;
  
  /** 石阵 - 凯恩石 */
  public static final int CAIRN_STONE = 314;
  
  /** 吉德宾匕首 */
  public static final int GIDBINN = 315;
  
  /** 赫拉迪克之锤 - 任务物品 */
  public static final int QUEST_HAMMER = 316;
  
  /** 蓝十字 */
  public static final int BLUE_CROSS = 317;
  
  /** 任务宝箱 - 闪光宝箱 */
  public static final int QUEST_CHEST = 318;
  
  /** 玩家储藏箱 */
  public static final int STASH = 319;
  
  /** 奥法门户 - 秘术师神殿传送门 */
  public static final int ARCANE_PORTAL = 339;
  
  /** 罗格营地篝火 */
  public static final int ROGUE_FIRE = 405;
  
  /** 书籍 - 石地和赫拉迪克卷轴 */
  public static final int BOOK = 427;
  
  /** 未知占位符 */
  public static final int PLACEHOLDER = 1176;
  
  /** 路障塔 - 第五章 */
  public static final int BARRICADE_TOWER = 1258;

  // ==================== 实体类型图标 ====================
  
  /** 玩家标记 */
  public static final int PLAYER = -1;  // 使用自定义绘制
  
  /** 队友标记 */
  public static final int PARTY_MEMBER = -2;
  
  /** 怪物标记 */
  public static final int MONSTER = -3;
  
  /** NPC标记 */
  public static final int NPC = -4;
  
  /** 佣兵标记 */
  public static final int MERCENARY = -5;
  
  /** 召唤物标记 */
  public static final int SUMMON = -6;
  
  /** 传送门标记 */
  public static final int PORTAL = -7;

  // ==================== 出入口图标 ====================
  
  /** 楼梯/入口 */
  public static final int ENTRANCE = -10;
  
  /** 出口 */
  public static final int EXIT = -11;

  /**
   * 根据物体类型获取对应的小地图图标
   * 
   * @param objectId 物体ID
   * @param shrineFunction 神殿功能ID（如果是神殿）
   * @return 对应的图标类型，-1表示无图标
   */
  public static int getIconForObject(int objectId, int shrineFunction) {
    // 传送点
    if (isWaypoint(objectId)) {
      return WAYPOINT;
    }
    
    // 神殿
    if (shrineFunction > 0) {
      return SHRINE;
    }
    
    // 水井
    if (isWell(objectId)) {
      return WELL;
    }
    
    // 储藏箱
    if (isStash(objectId)) {
      return STASH;
    }
    
    return -1;
  }

  /**
   * 判断物体是否是传送点
   */
  public static boolean isWaypoint(int objectId) {
    // 各章节的传送点物体ID
    return objectId == 119   // Act 1
        || objectId == 145   // Act 2
        || objectId == 156   // Act 3
        || objectId == 157   // Act 3 (另一个)
        || objectId == 237   // Act 4
        || objectId == 238   // Act 4 (另一个)
        || objectId == 288   // Act 5
        || objectId == 323   // Act 5 (另一个)
        || objectId == 398;  // 扩展
  }

  /**
   * 判断物体是否是水井
   */
  public static boolean isWell(int objectId) {
    return objectId == 122   // 普通水井
        || objectId == 183   // 沙漠水井
        || objectId == 185;  // 丛林水井
  }

  /**
   * 判断物体是否是储藏箱
   */
  public static boolean isStash(int objectId) {
    return objectId == 267;  // 储藏箱
  }
}
