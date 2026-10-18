package com.riiablo.engine.server.combat;

/** Fixed-point paths extracted without merging their distinct legacy operation orders. */
public final class NativeFixedElementalMath {
  private NativeFixedElementalMath() {}

  /** Shared output; the core's existing result types remain compatibility subclasses. */
  public static class Result {
    public int damageFixed;
    public int absorbedLifeFixed;
    public boolean immune;

    public void reset() {
      damageFixed = 0;
      absorbedLifeFixed = 0;
      immune = false;
    }
  }

  /** Ground-rate path: PvP, rate-scaled MDR, resistance, then capped absorb. */
  public static void rateDamage(Result out, int rawDamageFixed, boolean defenderPlayer,
      boolean attackerPlayer, int resistance, int maximumBonus, int piercePercent,
      int magicReduction, int damageRate, int absorbPercent, int absorbFlat) {
    out.reset();
    if (rawDamageFixed <= 0) return;
    if (!defenderPlayer && resistance >= 100) {
      out.immune = true;
      return;
    }
    resistance -= Math.max(0, piercePercent);
    int maximum = Math.min(NativeCombatConstants.ABSOLUTE_MAX_RESISTANCE, 75 + maximumBonus);
    resistance = Math.max(NativeCombatConstants.MIN_RESISTANCE, Math.min(maximum, resistance));
    long reduced = rawDamageFixed;
    if (attackerPlayer && defenderPlayer) reduced = reduced * NativeCombatConstants.PVP_DAMAGE_PERCENT / 100L;
    long flatReduction = (long) Math.max(0, magicReduction) << 8;
    if (flatReduction > 0 && damageRate > 0) flatReduction = flatReduction * damageRate / 1024L;
    reduced = Math.max(0L, reduced - flatReduction);
    reduced = reduced * (100L - resistance) / 100L;
    long absorbed = reduced * Math.max(0, Math.min(40, absorbPercent)) / 100L;
    long flatFixed = (long) Math.max(0, absorbFlat) << 8;
    absorbed += Math.min(flatFixed, Math.max(0L, reduced - absorbed));
    absorbed = Math.min(reduced, Math.max(0L, absorbed));
    out.damageFixed = saturatingInt(Math.max(0L, reduced - absorbed));
    out.absorbedLifeFixed = saturatingInt(absorbed);
  }

  /** Static Field: compensate negative resistance, resist, legacy MDR, absorb, then PvP. */
  public static void staticFieldDamage(Result out, int rawDamageFixed, boolean defenderPlayer,
      boolean attackerPlayer, int resistance, int maximumBonus, boolean poison,
      int magicReduction, int absorbPercent, int absorbFlat) {
    out.reset();
    if (rawDamageFixed <= 0) return;
    if (!defenderPlayer && resistance >= 100) {
      out.immune = true;
      return;
    }
    int maximum = Math.min(NativeCombatConstants.ABSOLUTE_MAX_RESISTANCE, 75 + maximumBonus);
    resistance = Math.max(NativeCombatConstants.MIN_RESISTANCE, Math.min(maximum, resistance));
    long adjusted = rawDamageFixed;
    if (resistance < 0) adjusted = 100L * adjusted / (100L - resistance);
    long reduced = adjusted * (100L - resistance) / 100L;
    // Preserve the existing unscaled MDR subtraction; migration does not correct mechanics.
    if (!poison) reduced = Math.max(0L, reduced - magicReduction);
    long absorbed = reduced * Math.max(0, Math.min(100, absorbPercent)) / 100L;
    long flatFixed = (long) Math.max(0, absorbFlat) << 8;
    absorbed += Math.min(flatFixed, Math.max(0L, reduced - absorbed));
    absorbed = Math.min(reduced, Math.max(0L, absorbed));
    long damage = Math.max(0L, reduced - absorbed);
    if (attackerPlayer && defenderPlayer) damage = damage * NativeCombatConstants.PVP_DAMAGE_PERCENT / 100L;
    out.damageFixed = saturatingInt(damage);
    out.absorbedLifeFixed = saturatingInt(absorbed);
  }

  /** Lightning writes one channel and adds absorbed life to the existing attack packet. */
  public static void lightningDamage(NativeDamagePacket out, int rawDamage, boolean attackerPlayer,
      boolean defenderPlayer, boolean monster, boolean immune, int rawResistance,
      int maximumResistance, int pierce, int magicReduction, int absorbPercent, int absorbFlat) {
    int type = NativeCombatConstants.DAMAGE_LIGHTNING;
    long resistance = rawResistance;
    if (immune || (monster && resistance >= 100)) return;
    resistance -= pierce;
    if (!monster) resistance = Math.min(resistance,
        Math.min(NativeCombatConstants.ABSOLUTE_MAX_RESISTANCE, maximumResistance));
    resistance = Math.max(NativeCombatConstants.MIN_RESISTANCE, Math.min(100L, resistance));
    long damage = rawDamage;
    if (attackerPlayer && defenderPlayer) damage = damage * NativeCombatConstants.PVP_DAMAGE_PERCENT / 100L;
    damage = Math.max(0L, damage - (long) Math.max(0, magicReduction) * 256);
    damage = damage * (100 - resistance) / 100L;
    long absorbed = damage * Math.max(0, Math.min(40, absorbPercent)) / 100L;
    absorbed += Math.min(damage - absorbed, (long) Math.max(0, absorbFlat) * 256);
    out.elementalDamage[type] = saturatingInt(damage - absorbed);
    out.absorbedLife = saturatingInt((long) out.absorbedLife + absorbed);
  }

  private static int saturatingInt(long value) {
    return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, (int) value);
  }
}
