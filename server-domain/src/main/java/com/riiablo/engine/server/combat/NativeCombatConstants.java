package com.riiablo.engine.server.combat;

/** Shared packet encodings; host calculators expose compatibility aliases. */
public final class NativeCombatConstants {
  private NativeCombatConstants() {}

  public static final int DAMAGE_PHYSICAL = 0;
  public static final int DAMAGE_FIRE = 1;
  public static final int DAMAGE_LIGHTNING = 2;
  public static final int DAMAGE_COLD = 3;
  public static final int DAMAGE_POISON = 4;
  public static final int DAMAGE_MAGIC = 5;
  public static final int DAMAGE_TYPE_COUNT = 6;
  public static final int DEFENSE_NONE = 0;
  public static final int DEFENSE_BLOCK = 1;
  public static final int DEFENSE_DODGE = 2;
  public static final int DEFENSE_AVOID = 3;
  public static final int DEFENSE_EVADE = 4;
  public static final int DEFENSE_WEAPON_BLOCK = 5;

  public static final int BASE_TO_HIT_CHANCE = 5;
  public static final int MIN_TO_HIT_CHANCE = 5;
  public static final int MAX_TO_HIT_CHANCE = 95;
  public static final int MIN_RESISTANCE = -100;
  public static final int ABSOLUTE_MAX_RESISTANCE = 95;
  public static final int PVP_HIT_FACTOR = 2;
  public static final int PVP_DAMAGE_PERCENT = 17;
  public static final int MIN_BLOCK_CHANCE = 0;
  public static final int MAX_BLOCK_CHANCE = 75;
  public static final int BLOCK_DEXTERITY_FACTOR = 2;
  public static final int BLOCK_BASE_DEX = 15;
}
