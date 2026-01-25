package com.riiablo.engine.server.item;

/**
 * 物品类型枚举 - 基于 D2MOO D2C_ItemTypes 移植
 * 
 * <p>定义了游戏中所有物品类型，对应 itemtypes.txt 中的条目。
 * 
 * <p>参考：D2MOO/source/D2Common/include/D2Items.h
 * 
 * @author riiablo team
 */
public final class ItemType {
  private ItemType() {} // 不可实例化

  //==========================================================================
  // 基础类型 (0-20)
  //==========================================================================

  /** 无类型 */
  public static final int NONE_1 = 0;
  public static final int NONE_2 = 1;
  /** 盾牌 */
  public static final int SHIELD = 2;
  /** 护甲 */
  public static final int ARMOR = 3;
  /** 金币 */
  public static final int GOLD = 4;
  /** 弓箭筒 */
  public static final int BOW_QUIVER = 5;
  /** 弩箭筒 */
  public static final int CROSSBOW_QUIVER = 6;
  /** 玩家身体部位 */
  public static final int PLAYER_BODY_PART = 7;
  /** 草药 */
  public static final int HERB = 8;
  /** 药水 */
  public static final int POTION = 9;
  /** 戒指 */
  public static final int RING = 10;
  /** 万能药 */
  public static final int ELIXIR = 11;
  /** 护身符 */
  public static final int AMULET = 12;
  /** 符咒 */
  public static final int CHARM = 13;
  public static final int NONE_3 = 14;
  /** 靴子 */
  public static final int BOOTS = 15;
  /** 手套 */
  public static final int GLOVES = 16;
  public static final int NONE_4 = 17;
  /** 书籍 */
  public static final int BOOK = 18;
  /** 腰带 */
  public static final int BELT = 19;
  /** 宝石 */
  public static final int GEM = 20;

  //==========================================================================
  // 杂项类型 (21-40)
  //==========================================================================

  /** 火把 */
  public static final int TORCH = 21;
  /** 卷轴 */
  public static final int SCROLL = 22;
  public static final int NONE_5 = 23;
  /** 权杖 */
  public static final int SCEPTER = 24;
  /** 法杖 */
  public static final int WAND = 25;
  /** 长杖 */
  public static final int STAFF = 26;
  /** 弓 */
  public static final int BOW = 27;
  /** 斧头 */
  public static final int AXE = 28;
  /** 棍棒 */
  public static final int CLUB = 29;
  /** 剑 */
  public static final int SWORD = 30;
  /** 锤子 */
  public static final int HAMMER = 31;
  /** 匕首 */
  public static final int KNIFE = 32;
  /** 长矛 */
  public static final int SPEAR = 33;
  /** 长柄武器 */
  public static final int POLEARM = 34;
  /** 弩 */
  public static final int CROSSBOW = 35;
  /** 钉锤 */
  public static final int MACE = 36;
  /** 头盔 */
  public static final int HELM = 37;
  /** 投掷药水 */
  public static final int MISSILE_POTION = 38;
  /** 任务物品 */
  public static final int QUEST = 39;
  /** 身体部位（怪物掉落） */
  public static final int BODY_PART = 40;

  //==========================================================================
  // 武器类型 (41-60)
  //==========================================================================

  /** 钥匙 */
  public static final int KEY = 41;
  /** 投掷匕首 */
  public static final int THROWING_KNIFE = 42;
  /** 投掷斧 */
  public static final int THROWING_AXE = 43;
  /** 标枪 */
  public static final int JAVELIN = 44;
  /** 武器（通用） */
  public static final int WEAPON = 45;
  /** 近战武器 */
  public static final int MELEE_WEAPON = 46;
  /** 远程武器 */
  public static final int MISSILE_WEAPON = 47;
  /** 投掷武器 */
  public static final int THROWN_WEAPON = 48;
  /** 组合武器 */
  public static final int COMBO_WEAPON = 49;
  /** 任意护甲 */
  public static final int ANY_ARMOR = 50;
  /** 任意盾牌 */
  public static final int ANY_SHIELD = 51;
  /** 杂项 */
  public static final int MISCELLANEOUS = 52;
  /** 插槽填充物 */
  public static final int SOCKET_FILLER = 53;
  /** 副手物品 */
  public static final int SECOND_HAND = 54;
  /** 法杖和魔棒 */
  public static final int STAVES_AND_RODS = 55;
  /** 飞弹 */
  public static final int MISSILE = 56;
  /** 钝器 */
  public static final int BLUNT = 57;
  /** 珠宝 */
  public static final int JEWEL = 58;

  //==========================================================================
  // 职业专属类型 (59-75)
  //==========================================================================

  /** 职业专属物品 */
  public static final int CLASS_SPECIFIC = 59;
  /** 亚马逊物品 */
  public static final int AMAZON_ITEM = 60;
  /** 野蛮人物品 */
  public static final int BARBARIAN_ITEM = 61;
  /** 死灵法师物品 */
  public static final int NECROMANCER_ITEM = 62;
  /** 圣骑士物品 */
  public static final int PALADIN_ITEM = 63;
  /** 法师物品 */
  public static final int SORCERESS_ITEM = 64;
  /** 刺客物品 */
  public static final int ASSASSIN_ITEM = 65;
  /** 德鲁伊物品 */
  public static final int DRUID_ITEM = 66;
  /** 拳套 */
  public static final int HAND_TO_HAND = 67;
  /** 法球 */
  public static final int ORB = 68;
  /** 干缩头颅 */
  public static final int VOODOO_HEADS = 69;
  /** 圣骑士盾牌 */
  public static final int AURIC_SHIELDS = 70;
  /** 野蛮人头盔 */
  public static final int PRIMAL_HELM = 71;
  /** 德鲁伊头盔 */
  public static final int PELT = 72;
  /** 斗篷 */
  public static final int CLOAK = 73;
  /** 符文 */
  public static final int RUNE = 74;
  /** 头环 */
  public static final int CIRCLET = 75;

  //==========================================================================
  // 药水类型 (76-85)
  //==========================================================================

  /** 生命药水 */
  public static final int HEALING_POTION = 76;
  /** 法力药水 */
  public static final int MANA_POTION = 77;
  /** 回复药水 */
  public static final int REJUV_POTION = 78;
  /** 体力药水 */
  public static final int STAMINA_POTION = 79;
  /** 解毒药水 */
  public static final int ANTIDOTE_POTION = 80;
  /** 解冻药水 */
  public static final int THAWING_POTION = 81;
  /** 小符咒 */
  public static final int SMALL_CHARM = 82;
  /** 中符咒 */
  public static final int MEDIUM_CHARM = 83;
  /** 大符咒 */
  public static final int LARGE_CHARM = 84;

  //==========================================================================
  // 亚马逊专属类型 (85-90)
  //==========================================================================

  /** 亚马逊弓 */
  public static final int AMAZON_BOW = 85;
  /** 亚马逊长矛 */
  public static final int AMAZON_SPEAR = 86;
  /** 亚马逊标枪 */
  public static final int AMAZON_JAVELIN = 87;
  /** 拳套2 */
  public static final int HAND_TO_HAND_2 = 88;
  /** 魔法弓箭筒 */
  public static final int MAGIC_BOW_QUIV = 89;

  //==========================================================================
  // 宝石等级类型 (91-100)
  //==========================================================================

  /** 未知类型 */
  public static final int UNK = 90;
  /** 碎裂宝石 */
  public static final int CHIPPED_GEM = 91;
  /** 有瑕疵宝石 */
  public static final int FLAWED_GEM = 92;
  /** 标准宝石 */
  public static final int STANDARD_GEM = 93;
  /** 无瑕疵宝石 */
  public static final int FLAWLESS_GEM = 94;
  /** 完美宝石 */
  public static final int PERFECT_GEM = 95;

  //==========================================================================
  // 宝石种类 (96-102)
  //==========================================================================

  /** 紫宝石 */
  public static final int AMETHYST = 96;
  /** 钻石 */
  public static final int DIAMOND = 97;
  /** 翡翠 */
  public static final int EMERALD = 98;
  /** 红宝石 */
  public static final int RUBY = 99;
  /** 蓝宝石 */
  public static final int SAPPHIRE = 100;
  /** 黄宝石 */
  public static final int TOPAZ = 101;
  /** 头骨 */
  public static final int SKULL = 102;

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 检查是否是武器类型
   * 
   * @param type 物品类型
   * @return true 如果是武器
   */
  public static boolean isWeapon(int type) {
    return type == WEAPON || type == MELEE_WEAPON || type == MISSILE_WEAPON ||
           type == THROWN_WEAPON || type == COMBO_WEAPON ||
           type == SWORD || type == AXE || type == MACE || type == KNIFE ||
           type == SPEAR || type == POLEARM || type == BOW || type == CROSSBOW ||
           type == STAFF || type == WAND || type == SCEPTER ||
           type == THROWING_KNIFE || type == THROWING_AXE || type == JAVELIN;
  }

  /**
   * 检查是否是护甲类型
   * 
   * @param type 物品类型
   * @return true 如果是护甲
   */
  public static boolean isArmor(int type) {
    return type == ARMOR || type == ANY_ARMOR || type == HELM ||
           type == BOOTS || type == GLOVES || type == BELT ||
           type == SHIELD || type == ANY_SHIELD;
  }

  /**
   * 检查是否是饰品类型
   * 
   * @param type 物品类型
   * @return true 如果是饰品
   */
  public static boolean isJewelry(int type) {
    return type == RING || type == AMULET;
  }

  /**
   * 检查是否是药水类型
   * 
   * @param type 物品类型
   * @return true 如果是药水
   */
  public static boolean isPotion(int type) {
    return type == POTION || type == HEALING_POTION || type == MANA_POTION ||
           type == REJUV_POTION || type == STAMINA_POTION ||
           type == ANTIDOTE_POTION || type == THAWING_POTION;
  }

  /**
   * 检查是否是宝石类型
   * 
   * @param type 物品类型
   * @return true 如果是宝石
   */
  public static boolean isGem(int type) {
    return type == GEM || type == CHIPPED_GEM || type == FLAWED_GEM ||
           type == STANDARD_GEM || type == FLAWLESS_GEM || type == PERFECT_GEM ||
           type == AMETHYST || type == DIAMOND || type == EMERALD ||
           type == RUBY || type == SAPPHIRE || type == TOPAZ || type == SKULL;
  }

  /**
   * 检查是否是符咒类型
   * 
   * @param type 物品类型
   * @return true 如果是符咒
   */
  public static boolean isCharm(int type) {
    return type == CHARM || type == SMALL_CHARM || type == MEDIUM_CHARM || type == LARGE_CHARM;
  }

  /**
   * 检查是否是可堆叠类型
   * 
   * @param type 物品类型
   * @return true 如果可堆叠
   */
  public static boolean isStackable(int type) {
    return type == GOLD || type == BOW_QUIVER || type == CROSSBOW_QUIVER ||
           type == THROWING_KNIFE || type == THROWING_AXE || type == JAVELIN ||
           type == KEY || type == SCROLL || type == BOOK || isPotion(type);
  }
}
