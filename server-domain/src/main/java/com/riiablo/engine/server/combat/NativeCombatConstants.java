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
}
