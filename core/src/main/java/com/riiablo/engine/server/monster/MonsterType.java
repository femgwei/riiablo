package com.riiablo.engine.server.monster;

/**
 * 怪物类型枚举 - 基于 D2MOD MonsterIds.h 移植
 * 
 * <p>定义了游戏中重要的怪物类型ID，这些ID对应 monstats.txt 中的条目。
 * 
 * <p>参考：D2MOD/source/D2Common/include/DataTbls/MonsterIds.h
 * 
 * @author riiablo team
 */
public final class MonsterType {
  private MonsterType() {} // 不可实例化

  //==========================================================================
  // 特殊单位 (0-10)
  //==========================================================================

  /** 无效怪物ID */
  public static final int INVALID = -1;
  /** 骷髅 */
  public static final int SKELETON1 = 0;
  /** 返还的骷髅 */
  public static final int RETURNED = 1;
  /** 骨骷髅 */
  public static final int BONEWARRIOR = 2;
  /** 燃烧的死者 */
  public static final int BURNTDEAD = 3;
  /** 恐惧 */
  public static final int HORROR = 4;
  /** 僵尸 */
  public static final int ZOMBIE = 5;
  /** 饥饿的死者 */
  public static final int HUNGRYDEAD = 6;
  /** 食尸鬼 */
  public static final int GHOUL = 7;
  /** 溺水者 */
  public static final int DROWNED = 8;
  /** 瘟疫携带者 */
  public static final int PLAGUEBEAR = 9;
  /** 堕落者 */
  public static final int FALLEN = 10;

  //==========================================================================
  // 法师/远程怪物 (11-30)
  //==========================================================================

  /** 堕落萨满 */
  public static final int FALLENSHAMAN = 14;
  /** 刺鼠 */
  public static final int QUILLRAT = 20;
  /** 骷髅弓箭手 */
  public static final int SKELETONBOW = 63;
  /** 骷髅法师 */
  public static final int SKELETONMAGE = 183;

  //==========================================================================
  // BOSS 怪物
  //==========================================================================

  /** 安达利尔 (Act 1 Boss) */
  public static final int ANDARIEL = 156;
  /** 都瑞尔 (Act 2 Boss) */
  public static final int DURIEL = 211;
  /** 墨菲斯托 (Act 3 Boss) */
  public static final int MEPHISTO = 242;
  /** 暗黑破坏神 (Act 4 Boss) */
  public static final int DIABLO = 243;
  /** 巴尔 (Act 5 Boss) */
  public static final int BAALCRAB = 544;

  //==========================================================================
  // 超级 Boss
  //==========================================================================

  /** Uber 安达利尔 */
  public static final int UBERANDARIEL = 707;
  /** Uber 都瑞尔 */
  public static final int UBERDURIEL = 708;
  /** Uber 伊兹尔 */
  public static final int UBERIZUAL = 709;
  /** Uber 墨菲斯托 */
  public static final int UBERMEPHISTO = 704;
  /** Uber 暗黑破坏神 */
  public static final int UBERDIABLO = 705;
  /** Uber 巴尔 */
  public static final int UBERBAAL = 706;

  //==========================================================================
  // 特殊怪物
  //==========================================================================

  /** 毁灭骑士2 */
  public static final int DOOMKNIGHT2 = 400;
  /** 毁灭骑士3 */
  public static final int DOOMKNIGHT3 = 401;
  /** 血鸟 */
  public static final int BLOODRAVEN = 267;
  /** 召唤者 */
  public static final int SUMMONER = 250;
  /** 督军 (议会成员) */
  public static final int COUNCILMEMBER = 345;
  /** 伊兹尔 */
  public static final int IZUAL = 256;

  //==========================================================================
  // 雇佣兵
  //==========================================================================

  /** Act 1 雇佣兵（弓箭手） */
  public static final int HIRELING_ROGUE = 271;
  /** Act 2 雇佣兵（沙漠佣兵） */
  public static final int HIRELING_DESERT = 338;
  /** Act 3 雇佣兵（铁狼） */
  public static final int HIRELING_IRONWOLF = 359;
  /** Act 5 雇佣兵（野蛮人） */
  public static final int HIRELING_BARBARIAN = 560;

  //==========================================================================
  // NPC
  //==========================================================================

  /** 雅克修 (Act 1) */
  public static final int AKARA = 148;
  /** 查西 (Act 1) */
  public static final int KASHYA = 150;
  /** 迪卡·凯恩 */
  public static final int DECKARDCAIN = 146;
  /** 格瑞兹 (Act 1) */
  public static final int GHEED = 147;
  /** 沃瑞夫 (Act 1) */
  public static final int WARRIV = 155;

  //==========================================================================
  // 召唤物
  //==========================================================================

  /** 死灵法师骷髅 */
  public static final int NECROSKELETON = 363;
  /** 死灵法师骷髅法师 */
  public static final int NECROMAGE = 364;
  /** 德鲁伊狼 */
  public static final int DRUIDWOLF = 357;
  /** 德鲁伊熊 */
  public static final int DRUIDBEAR = 358;
  /** 女武神 */
  public static final int VALKYRIE = 365;
  /** 傀儡 */
  public static final int SHADOWWARRIOR = 417;
  /** 影子大师 */
  public static final int SHADOWMASTER = 418;

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 检查是否是 Boss
   * 
   * @param monsterId 怪物ID
   * @return true 如果是 Boss
   */
  public static boolean isBoss(int monsterId) {
    switch (monsterId) {
      case ANDARIEL:
      case DURIEL:
      case MEPHISTO:
      case DIABLO:
      case BAALCRAB:
      case UBERANDARIEL:
      case UBERDURIEL:
      case UBERIZUAL:
      case UBERMEPHISTO:
      case UBERDIABLO:
      case UBERBAAL:
        return true;
      default:
        return false;
    }
  }

  /**
   * 检查是否是超级 Boss
   * 
   * @param monsterId 怪物ID
   * @return true 如果是超级 Boss
   */
  public static boolean isUberBoss(int monsterId) {
    switch (monsterId) {
      case UBERANDARIEL:
      case UBERDURIEL:
      case UBERIZUAL:
      case UBERMEPHISTO:
      case UBERDIABLO:
      case UBERBAAL:
        return true;
      default:
        return false;
    }
  }

  /**
   * 检查是否是雇佣兵
   * 
   * @param monsterId 怪物ID
   * @return true 如果是雇佣兵
   */
  public static boolean isHireling(int monsterId) {
    switch (monsterId) {
      case HIRELING_ROGUE:
      case HIRELING_DESERT:
      case HIRELING_IRONWOLF:
      case HIRELING_BARBARIAN:
        return true;
      default:
        return false;
    }
  }

  /**
   * 检查是否是玩家召唤物
   * 
   * @param monsterId 怪物ID
   * @return true 如果是召唤物
   */
  public static boolean isSummon(int monsterId) {
    switch (monsterId) {
      case NECROSKELETON:
      case NECROMAGE:
      case DRUIDWOLF:
      case DRUIDBEAR:
      case VALKYRIE:
      case SHADOWWARRIOR:
      case SHADOWMASTER:
        return true;
      default:
        return false;
    }
  }
}
