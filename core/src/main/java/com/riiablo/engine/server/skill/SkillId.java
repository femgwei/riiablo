package com.riiablo.engine.server.skill;

/**
 * 技能 ID 常量 - 基于 D2MOD SkillsIds.h 移植
 * 
 * <p>定义了游戏中所有技能的 ID。
 * 
 * <p>参考：D2MOD/source/D2Common/include/DataTbls/SkillsIds.h
 * 
 * @author riiablo team
 */
public final class SkillId {
  private SkillId() {} // 不可实例化

  //==========================================================================
  // 通用技能
  //==========================================================================
  
  public static final int ATTACK = 0;
  public static final int KICK = 1;
  public static final int THROW = 2;
  public static final int UNSUMMON = 3;
  public static final int LEFT_HAND_THROW = 4;
  public static final int LEFT_HAND_SWING = 5;

  //==========================================================================
  // 亚马逊技能 - 标枪和长矛
  //==========================================================================
  
  public static final int JAB = 10;
  public static final int POWER_STRIKE = 14;
  public static final int POISON_JAVELIN = 15;
  public static final int IMPALE = 19;
  public static final int LIGHTNING_BOLT = 20;
  public static final int CHARGED_STRIKE = 24;
  public static final int PLAGUE_JAVELIN = 25;
  public static final int FEND = 29;
  public static final int LIGHTNING_STRIKE = 30;
  public static final int LIGHTNING_FURY = 35;

  //==========================================================================
  // 亚马逊技能 - 被动和魔法
  //==========================================================================
  
  public static final int INNER_SIGHT = 11;
  public static final int CRITICAL_STRIKE = 12;
  public static final int DODGE = 16;
  public static final int SLOW_MISSILES = 17;
  public static final int AVOID = 21;
  public static final int PENETRATE = 22;
  public static final int DECOY = 26;
  public static final int EVADE = 27;
  public static final int VALKYRIE = 31;
  public static final int PIERCE = 32;

  //==========================================================================
  // 亚马逊技能 - 弓和弩
  //==========================================================================
  
  public static final int MAGIC_ARROW = 6;
  public static final int FIRE_ARROW = 7;
  public static final int COLD_ARROW = 11;
  public static final int MULTIPLE_SHOT = 12;
  public static final int EXPLODING_ARROW = 16;
  public static final int ICE_ARROW = 21;
  public static final int GUIDED_ARROW = 22;
  public static final int STRAFE = 26;
  public static final int IMMOLATION_ARROW = 27;
  public static final int FREEZING_ARROW = 31;

  //==========================================================================
  // 法师技能 - 火焰
  //==========================================================================
  
  public static final int FIRE_BOLT = 36;
  public static final int WARMTH = 37;
  public static final int INFERNO = 41;
  public static final int BLAZE = 46;
  public static final int FIRE_BALL = 47;
  public static final int FIRE_WALL = 51;
  public static final int ENCHANT = 52;
  public static final int METEOR = 56;
  public static final int FIRE_MASTERY = 61;
  public static final int HYDRA = 62;

  //==========================================================================
  // 法师技能 - 闪电
  //==========================================================================
  
  public static final int CHARGED_BOLT = 38;
  public static final int STATIC_FIELD = 42;
  public static final int TELEKINESIS = 43;
  public static final int NOVA = 48;
  public static final int LIGHTNING = 49;
  public static final int CHAIN_LIGHTNING = 53;
  public static final int TELEPORT = 54;
  public static final int THUNDER_STORM = 57;
  public static final int ENERGY_SHIELD = 58;
  public static final int LIGHTNING_MASTERY = 63;

  //==========================================================================
  // 法师技能 - 冰冷
  //==========================================================================
  
  public static final int ICE_BOLT = 39;
  public static final int FROZEN_ARMOR = 40;
  public static final int FROST_NOVA = 44;
  public static final int ICE_BLAST = 45;
  public static final int SHIVER_ARMOR = 50;
  public static final int GLACIAL_SPIKE = 55;
  public static final int BLIZZARD = 59;
  public static final int CHILLING_ARMOR = 60;
  public static final int FROZEN_ORB = 64;
  public static final int COLD_MASTERY = 65;

  //==========================================================================
  // 死灵法师技能 - 诅咒
  //==========================================================================
  
  public static final int AMPLIFY_DAMAGE = 66;
  public static final int DIM_VISION = 71;
  public static final int WEAKEN = 72;
  public static final int IRON_MAIDEN = 76;
  public static final int TERROR = 77;
  public static final int CONFUSE = 81;
  public static final int LIFE_TAP = 82;
  public static final int ATTRACT = 86;
  public static final int DECREPIFY = 87;
  public static final int LOWER_RESIST = 91;

  //==========================================================================
  // 死灵法师技能 - 毒素和骨
  //==========================================================================
  
  public static final int TEETH = 67;
  public static final int BONE_ARMOR = 68;
  public static final int POISON_DAGGER = 73;
  public static final int CORPSE_EXPLOSION = 74;
  public static final int BONE_WALL = 78;
  public static final int POISON_EXPLOSION = 83;
  public static final int BONE_SPEAR = 84;
  public static final int BONE_PRISON = 88;
  public static final int POISON_NOVA = 92;
  public static final int BONE_SPIRIT = 93;

  //==========================================================================
  // 死灵法师技能 - 召唤
  //==========================================================================
  
  public static final int SKELETON_MASTERY = 69;
  public static final int RAISE_SKELETON = 70;
  public static final int CLAY_GOLEM = 75;
  public static final int GOLEM_MASTERY = 79;
  public static final int RAISE_SKELETAL_MAGE = 80;
  public static final int BLOOD_GOLEM = 85;
  public static final int SUMMON_RESIST = 89;
  public static final int IRON_GOLEM = 90;
  public static final int FIRE_GOLEM = 94;
  public static final int REVIVE = 95;

  //==========================================================================
  // 圣骑士技能 - 战斗
  //==========================================================================
  
  public static final int SACRIFICE = 96;
  public static final int SMITE = 97;
  public static final int HOLY_BOLT = 101;
  public static final int ZEAL = 106;
  public static final int CHARGE = 107;
  public static final int VENGEANCE = 111;
  public static final int BLESSED_HAMMER = 112;
  public static final int CONVERSION = 116;
  public static final int HOLY_SHIELD = 117;
  public static final int FIST_OF_THE_HEAVENS = 121;

  //==========================================================================
  // 圣骑士技能 - 攻击光环
  //==========================================================================
  
  public static final int MIGHT = 98;
  public static final int HOLY_FIRE = 102;
  public static final int THORNS = 103;
  public static final int BLESSED_AIM = 108;
  public static final int CONCENTRATION = 113;
  public static final int HOLY_FREEZE = 114;
  public static final int HOLY_SHOCK = 118;
  public static final int SANCTUARY = 119;
  public static final int FANATICISM = 122;
  public static final int CONVICTION = 123;

  //==========================================================================
  // 圣骑士技能 - 防御光环
  //==========================================================================
  
  public static final int PRAYER = 99;
  public static final int RESIST_FIRE = 100;
  public static final int DEFIANCE = 104;
  public static final int RESIST_COLD = 105;
  public static final int CLEANSING = 109;
  public static final int RESIST_LIGHTNING = 110;
  public static final int VIGOR = 115;
  public static final int MEDITATION = 120;
  public static final int REDEMPTION = 124;
  public static final int SALVATION = 125;

  //==========================================================================
  // 野蛮人技能 - 战斗技能
  //==========================================================================
  
  public static final int BASH = 126;
  public static final int LEAP = 132;
  public static final int DOUBLE_SWING = 133;
  public static final int STUN = 139;
  public static final int DOUBLE_THROW = 140;
  public static final int LEAP_ATTACK = 143;
  public static final int CONCENTRATE = 144;
  public static final int FRENZY = 147;
  public static final int WHIRLWIND = 151;
  public static final int BERSERK = 152;

  //==========================================================================
  // 野蛮人技能 - 战吼
  //==========================================================================
  
  public static final int HOWL = 130;
  public static final int FIND_POTION = 131;
  public static final int TAUNT = 137;
  public static final int SHOUT = 138;
  public static final int FIND_ITEM = 142;
  public static final int BATTLE_CRY = 146;
  public static final int BATTLE_ORDERS = 149;
  public static final int GRIM_WARD = 150;
  public static final int WAR_CRY = 154;
  public static final int BATTLE_COMMAND = 155;

  //==========================================================================
  // 野蛮人技能 - 战斗专精
  //==========================================================================
  
  public static final int SWORD_MASTERY = 127;
  public static final int AXE_MASTERY = 128;
  public static final int MACE_MASTERY = 129;
  public static final int POLEARM_MASTERY = 134;
  public static final int THROWING_MASTERY = 135;
  public static final int SPEAR_MASTERY = 136;
  public static final int INCREASED_STAMINA = 141;
  public static final int IRON_SKIN = 145;
  public static final int INCREASED_SPEED = 148;
  public static final int NATURAL_RESISTANCE = 153;

  //==========================================================================
  // 德鲁伊技能 - 元素
  //==========================================================================
  
  public static final int FIRESTORM = 225;
  public static final int MOLTEN_BOULDER = 226;
  public static final int ARCTIC_BLAST = 227;
  public static final int FISSURE = 228;
  public static final int CYCLONE_ARMOR = 229;
  public static final int TWISTER = 230;
  public static final int VOLCANO = 231;
  public static final int TORNADO = 232;
  public static final int ARMAGEDDON = 233;
  public static final int HURRICANE = 234;

  //==========================================================================
  // 德鲁伊技能 - 变形
  //==========================================================================
  
  public static final int WEREWOLF = 223;
  public static final int LYCANTHROPY = 224;
  public static final int WEREBEAR = 235;
  public static final int FERAL_RAGE = 236;
  public static final int MAUL = 237;
  public static final int RABIES = 238;
  public static final int FIRE_CLAWS = 239;
  public static final int HUNGER = 240;
  public static final int SHOCK_WAVE = 241;
  public static final int FURY = 242;

  //==========================================================================
  // 德鲁伊技能 - 召唤
  //==========================================================================
  
  public static final int RAVEN = 221;
  public static final int POISON_CREEPER = 222;
  public static final int OAK_SAGE = 243;
  public static final int SUMMON_SPIRIT_WOLF = 244;
  public static final int CARRION_VINE = 245;
  public static final int HEART_OF_WOLVERINE = 246;
  public static final int SUMMON_DIRE_WOLF = 247;
  public static final int SOLAR_CREEPER = 248;
  public static final int SPIRIT_OF_BARBS = 249;
  public static final int SUMMON_GRIZZLY = 250;

  //==========================================================================
  // 刺客技能 - 武技
  //==========================================================================
  
  public static final int TIGER_STRIKE = 251;
  public static final int DRAGON_TALON = 252;
  public static final int FISTS_OF_FIRE = 253;
  public static final int DRAGON_CLAW = 254;
  public static final int COBRA_STRIKE = 255;
  public static final int CLAWS_OF_THUNDER = 256;
  public static final int DRAGON_TAIL = 257;
  public static final int BLADES_OF_ICE = 258;
  public static final int DRAGON_FLIGHT = 259;
  public static final int PHOENIX_STRIKE = 260;

  //==========================================================================
  // 刺客技能 - 陷阱
  //==========================================================================
  
  public static final int FIRE_BLAST = 261;
  public static final int SHOCK_WEB = 262;
  public static final int BLADE_SENTINEL = 263;
  public static final int CHARGED_BOLT_SENTRY = 264;
  public static final int WAKE_OF_FIRE = 265;
  public static final int BLADE_FURY = 266;
  public static final int LIGHTNING_SENTRY = 267;
  public static final int WAKE_OF_INFERNO = 268;
  public static final int DEATH_SENTRY = 269;
  public static final int BLADE_SHIELD = 270;

  //==========================================================================
  // 刺客技能 - 暗影
  //==========================================================================
  
  public static final int CLAW_MASTERY = 271;
  public static final int PSYCHIC_HAMMER = 272;
  public static final int BURST_OF_SPEED = 273;
  public static final int WEAPON_BLOCK = 274;
  public static final int CLOAK_OF_SHADOWS = 275;
  public static final int FADE = 276;
  public static final int SHADOW_WARRIOR = 277;
  public static final int MIND_BLAST = 278;
  public static final int VENOM = 279;
  public static final int SHADOW_MASTER = 280;

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 获取技能所属职业
   * 
   * @param skillId 技能 ID
   * @return 职业 ID (0-6)，-1 表示通用
   */
  public static int getCharacterClass(int skillId) {
    if (skillId < 6) return -1; // 通用
    if (skillId <= 35) return 0; // 亚马逊
    if (skillId <= 65) return 1; // 法师
    if (skillId <= 95) return 2; // 死灵法师
    if (skillId <= 125) return 3; // 圣骑士
    if (skillId <= 155) return 4; // 野蛮人
    if (skillId <= 220) return -1; // 其他
    if (skillId <= 250) return 5; // 德鲁伊
    if (skillId <= 280) return 6; // 刺客
    return -1;
  }
}
