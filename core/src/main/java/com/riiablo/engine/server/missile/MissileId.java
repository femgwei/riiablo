package com.riiablo.engine.server.missile;

/**
 * 投射物 ID 常量 - 基于 D2MOO MissilesIds.h 移植
 * 
 * <p>定义了游戏中所有投射物的 ID。
 * 
 * <p>参考：D2MOO/source/D2Common/include/DataTbls/MissilesIds.h
 * 
 * @author riiablo team
 */
public final class MissileId {
  private MissileId() {} // 不可实例化

  //==========================================================================
  // 通用投射物
  //==========================================================================

  public static final int NONE = 0;
  public static final int ARROW = 1;
  public static final int FIRE_ARROW = 2;
  public static final int INNER_SIGHT = 3;
  public static final int COLD_ARROW = 4;
  public static final int MULTIPLE_SHOT = 5;
  public static final int FIRE_BOLT = 6;
  public static final int CHARGED_BOLT = 7;
  public static final int ICE_BOLT = 8;
  public static final int FROZEN_ARMOR = 9;
  public static final int INFERNO = 10;
  public static final int STATIC_FIELD = 11;
  public static final int TELEKINESIS = 12;
  public static final int FROST_NOVA = 13;
  public static final int ICE_BLAST = 14;
  public static final int BLAZE = 15;
  public static final int FIRE_BALL = 16;
  public static final int NOVA = 17;
  public static final int LIGHTNING = 18;
  public static final int SHIVER_ARMOR = 19;
  public static final int FIRE_WALL = 20;
  public static final int ENCHANT = 21;
  public static final int CHAIN_LIGHTNING = 22;
  public static final int TELEPORT = 23;
  public static final int GLACIAL_SPIKE = 24;
  public static final int METEOR = 25;
  public static final int THUNDER_STORM = 26;
  public static final int ENERGY_SHIELD = 27;
  public static final int BLIZZARD = 28;
  public static final int CHILLING_ARMOR = 29;
  public static final int FIRE_MASTERY = 30;
  public static final int HYDRA = 31;
  public static final int LIGHTNING_MASTERY = 32;
  public static final int FROZEN_ORB = 33;
  public static final int COLD_MASTERY = 34;

  //==========================================================================
  // 死灵法师投射物
  //==========================================================================

  public static final int TEETH = 35;
  public static final int BONE_SPEAR = 36;
  public static final int BONE_SPIRIT = 37;
  public static final int POISON_DAGGER = 38;
  public static final int POISON_EXPLOSION = 39;
  public static final int POISON_NOVA = 40;

  //==========================================================================
  // 亚马逊投射物
  //==========================================================================

  public static final int MAGIC_ARROW = 41;
  public static final int EXPLODING_ARROW = 42;
  public static final int ICE_ARROW = 43;
  public static final int GUIDED_ARROW = 44;
  public static final int IMMOLATION_ARROW = 45;
  public static final int STRAFE = 46;
  public static final int FREEZING_ARROW = 47;
  public static final int PLAGUE_JAVELIN = 48;
  public static final int LIGHTNING_FURY = 49;
  public static final int LIGHTNING_BOLT = 50;
  public static final int POWER_STRIKE = 51;
  public static final int CHARGED_STRIKE = 52;
  public static final int LIGHTNING_STRIKE = 53;

  //==========================================================================
  // 圣骑士投射物
  //==========================================================================

  public static final int HOLY_BOLT = 54;
  public static final int BLESSED_HAMMER = 55;
  public static final int FIST_OF_THE_HEAVENS = 56;
  public static final int HOLY_FIRE = 57;
  public static final int HOLY_FREEZE = 58;
  public static final int HOLY_SHOCK = 59;

  //==========================================================================
  // 野蛮人投射物
  //==========================================================================

  public static final int DOUBLE_THROW = 60;
  public static final int HOWL = 61;
  public static final int SHOUT = 62;
  public static final int BATTLE_ORDERS = 63;
  public static final int BATTLE_COMMAND = 64;
  public static final int WAR_CRY = 65;
  public static final int TAUNT = 66;
  public static final int BATTLE_CRY = 67;
  public static final int GRIM_WARD = 68;

  //==========================================================================
  // 德鲁伊投射物
  //==========================================================================

  public static final int FIRESTORM = 69;
  public static final int MOLTEN_BOULDER = 70;
  public static final int ARCTIC_BLAST = 71;
  public static final int FISSURE = 72;
  public static final int CYCLONE_ARMOR = 73;
  public static final int TWISTER = 74;
  public static final int VOLCANO = 75;
  public static final int TORNADO = 76;
  public static final int ARMAGEDDON = 77;
  public static final int HURRICANE = 78;

  //==========================================================================
  // 刺客投射物
  //==========================================================================

  public static final int FIRE_BLAST = 79;
  public static final int SHOCK_WEB = 80;
  public static final int BLADE_SENTINEL = 81;
  public static final int CHARGED_BOLT_SENTRY = 82;
  public static final int WAKE_OF_FIRE = 83;
  public static final int BLADE_FURY = 84;
  public static final int LIGHTNING_SENTRY = 85;
  public static final int WAKE_OF_INFERNO = 86;
  public static final int DEATH_SENTRY = 87;
  public static final int BLADE_SHIELD = 88;

  //==========================================================================
  // 怪物投射物
  //==========================================================================

  public static final int MONSTER_BOLT = 100;
  public static final int MONSTER_ARROW = 101;
  public static final int MONSTER_INFERNO = 102;
  public static final int MONSTER_FIREBALL = 103;
  public static final int MONSTER_COLD_BOLT = 104;
  public static final int MONSTER_LIGHTNING = 105;
  public static final int DIABLO_FIRE = 106;
  public static final int DIABLO_LIGHTNING = 107;
  public static final int BAAL_COLD = 108;
  public static final int BAAL_TENTACLE = 109;

  /** 最大投射物 ID */
  public static final int MAX_MISSILE_ID = 500;
}
