package com.riiablo.engine.server.combat;

/**
 * Raw 8.8 HP/frame arithmetic for an active poison state. Source: D2MOO
 * PlrModes::EVENTS_HpRegen and MonsterMode::D2GAME_MONSTER_ApplyStatRegen_6FC63440.
 * This does not model native room checks or event scheduling.
 */
public final class NativePoisonRegenMath {
  private NativePoisonRegenMath() {}

  public static long netRate(int aggregateRegen, int stateRegen, int potionRegen,
      int poisonRate, int baseRegen, boolean suppressBaseRegen) {
    return (long) aggregateRegen + stateRegen + potionRegen - poisonRate
        - (suppressBaseRegen ? baseRegen : 0);
  }

  public static boolean exceedsMaximum(int life, int maximum, long netRate) {
    return life > 0 && netRate != 0 && (long) life + netRate > maximum;
  }

  public static int nextLife(int life, int maximum, long netRate, boolean player) {
    // Never revive a host corpse. Native callers already exclude dead units.
    if (life <= 0 || netRate == 0) return life;
    long next = Math.min((long) maximum, (long) life + netRate);
    next = Math.max(player ? 256L : 0L, next);
    return (int) Math.min(Integer.MAX_VALUE, next);
  }
}
