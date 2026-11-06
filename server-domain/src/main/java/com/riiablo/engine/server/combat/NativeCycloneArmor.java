package com.riiablo.engine.server.combat;

import static com.riiablo.engine.server.combat.NativeCombatConstants.*;

/** EventFunc25: consume raw post-PvP fire, cold, lightning before resistance/MDR. */
public final class NativeCycloneArmor {
  private static final int[] CHANNEL_ORDER = {DAMAGE_FIRE, DAMAGE_COLD, DAMAGE_LIGHTNING};
  private NativeCycloneArmor() {}

  public static boolean protects(int type) {
    return type == DAMAGE_FIRE || type == DAMAGE_COLD || type == DAMAGE_LIGHTNING;
  }

  /** Returns consumed 8.8 capacity; channel values retain their input scale. */
  public static int absorb(int[] channels, int scale, int capacityFixed) {
    capacityFixed = Math.max(0, capacityFixed);
    if (capacityFixed == 0) return 0;
    int remaining = Math.max(0, capacityFixed);
    for (int type : CHANNEL_ORDER) {
      int damage = Math.max(0, channels[type]);
      long damageFixed = (long) damage * 256 / scale;
      int consumed = (int) Math.min(remaining, damageFixed);
      channels[type] = (int) ((damageFixed - consumed) * scale / 256);
      remaining -= consumed;
    }
    return capacityFixed - remaining;
  }

  public static int absorbedFixed(int type, int rawFixed, int capacityFixed) {
    return protects(type) ? Math.min(Math.max(0, rawFixed), Math.max(0, capacityFixed)) : 0;
  }
}
