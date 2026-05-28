package com.riiablo.engine.server.object;

/**
 * 场景对象类型 - 基于 D2MOD Objects.h 移植
 * 
 * <p>定义了游戏中所有可交互对象的类型。
 * 
 * <p>参考：D2MOD/source/D2Game/src/OBJECTS/Objects.h
 * 
 * @author riiablo team
 */
public final class ObjectType {
  private ObjectType() {} // 不可实例化

  //==========================================================================
  // 基础类型分类
  //==========================================================================

  /** 未知类型 */
  public static final int NONE = 0;

  /** 门 */
  public static final int DOOR = 1;

  /** 箱子/容器 */
  public static final int CHEST = 2;

  /** 桶 */
  public static final int BARREL = 3;

  /** 神殿 */
  public static final int SHRINE = 4;

  /** 井 */
  public static final int WELL = 5;

  /** 传送门 */
  public static final int PORTAL = 6;

  /** 传送点 */
  public static final int WAYPOINT = 7;

  /** 火堆 */
  public static final int FIRE = 8;

  /** 火炬 */
  public static final int TORCH = 9;

  /** 陷阱 */
  public static final int TRAP = 10;

  /** 开关/机关 */
  public static final int SWITCH = 11;

  /** 宝石神殿 */
  public static final int GEM_SHRINE = 12;

  /** 书架 */
  public static final int BOOKCASE = 13;

  /** 石棺 */
  public static final int SARCOPHAGUS = 14;

  /** 罐子 */
  public static final int URN = 15;

  /** 尸体 */
  public static final int CORPSE = 16;

  /** 椅子/家具 */
  public static final int FURNITURE = 17;

  /** 石柱 */
  public static final int PILLAR = 18;

  /** 祭坛 */
  public static final int ALTAR = 19;

  /** 赫拉迪克方块 */
  public static final int HORADRIC_CUBE = 20;

  /** 赫拉迪克法杖 */
  public static final int HORADRIC_STAFF = 21;

  /** 红门 */
  public static final int RED_PORTAL = 22;

  //==========================================================================
  // 任务对象
  //==========================================================================

  /** 凯恩之石 */
  public static final int CAIRN_STONE = 50;

  /** 雷加尔之书 */
  public static final int BOOK_OF_SKILL = 51;

  /** 力量之书 */
  public static final int TOME_OF_TOWN_PORTAL = 52;

  /** 鉴定卷轴书 */
  public static final int TOME_OF_IDENTIFY = 53;

  /** 尾巴 */
  public static final int ORIFICE = 54;

  /** 塔拉夏之墓 */
  public static final int TAL_RASHA_TOMB = 55;

  /** 邪恶之力 */
  public static final int EVIL_URN = 56;

  /** 密码祭坛 */
  public static final int GIDBINN = 57;

  /** 破坏者灵魂 */
  public static final int HELLFORGE = 58;

  /** 世界之石 */
  public static final int WORLDSTONE = 59;

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 是否是容器类型
   */
  public static boolean isContainer(int type) {
    return type == CHEST || type == BARREL || type == URN || 
           type == SARCOPHAGUS || type == CORPSE;
  }

  /**
   * 是否可交互
   */
  public static boolean isInteractable(int type) {
    return type == DOOR || type == CHEST || type == SHRINE || 
           type == WELL || type == PORTAL || type == WAYPOINT ||
           type == SWITCH || type == BOOKCASE || type == SARCOPHAGUS ||
           type == URN || type == ALTAR;
  }

  /**
   * 是否是神殿类型
   */
  public static boolean isShrine(int type) {
    return type == SHRINE || type == GEM_SHRINE || type == WELL;
  }

  /**
   * 是否是门类型
   */
  public static boolean isDoor(int type) {
    return type == DOOR;
  }

  /**
   * 是否可以掉落物品
   */
  public static boolean canDropItems(int type) {
    return isContainer(type) || type == BOOKCASE;
  }

  /**
   * 获取类型名称
   */
  public static String getName(int type) {
    switch (type) {
      case NONE: return "None";
      case DOOR: return "Door";
      case CHEST: return "Chest";
      case BARREL: return "Barrel";
      case SHRINE: return "Shrine";
      case WELL: return "Well";
      case PORTAL: return "Portal";
      case WAYPOINT: return "Waypoint";
      case FIRE: return "Fire";
      case TORCH: return "Torch";
      case TRAP: return "Trap";
      case SWITCH: return "Switch";
      case GEM_SHRINE: return "Gem Shrine";
      case BOOKCASE: return "Bookcase";
      case SARCOPHAGUS: return "Sarcophagus";
      case URN: return "Urn";
      case CORPSE: return "Corpse";
      case FURNITURE: return "Furniture";
      case PILLAR: return "Pillar";
      case ALTAR: return "Altar";
      case HORADRIC_CUBE: return "Horadric Cube";
      case HORADRIC_STAFF: return "Horadric Staff";
      case RED_PORTAL: return "Red Portal";
      default: return "Unknown";
    }
  }
}
