package com.riiablo.engine.server.combat;

/** No-roll direct packet arithmetic. Legacy policy exists only for frozen migration tests. */
public final class NativeDirectDamageMath {
  private NativeDirectDamageMath() {}

  public static int nativePhysicalDamage(int raw, boolean player, int resistance, int flatReduction) {
    return nativePhysicalDamage(raw, player, resistance, flatReduction, 1);
  }

  public static int nativePhysicalDamage(int raw, boolean player, int resistance,
      int flatReduction, int scale) {
    return NativeMitigationMath.reduce(raw, scale,
        NativeMitigationMath.resistance(player, true, false, resistance, 50, 0), flatReduction, 0);
  }

  public static int reflectedPhysicalDamage(int raw, boolean defenderPlayer, boolean attackerPlayer,
      int resistance, int flatReduction) {
    return nativePhysicalDamage(NativeMitigationMath.pvp(raw, attackerPlayer && defenderPlayer, 1),
        defenderPlayer, resistance, flatReduction);
  }

  /** Fills a fresh direct result, not a merge into an existing attack packet. */
  public static void elementalDamage(NativeDamagePacket out, int type, int raw, int pierce,
      boolean defenderPlayer, boolean attackerPlayer, int resistance, int maximum,
      int magicReduction, int absorbPercent, int absorbFlat, boolean nativePacket) {
    out.reset();
    if (type <= NativeCombatConstants.DAMAGE_PHYSICAL
        || type >= NativeCombatConstants.DAMAGE_TYPE_COUNT || raw <= 0) return;
    boolean immune = !defenderPlayer && resistance >= 100;
    int reduced;
    if (nativePacket) {
      int adjusted = NativeMitigationMath.resistance(defenderPlayer, false,
          type == NativeCombatConstants.DAMAGE_MAGIC, resistance, maximum, pierce);
      raw = NativeMitigationMath.pvp(raw, attackerPlayer && defenderPlayer, 1);
      reduced = immune ? 0 : NativeMitigationMath.reduce(raw, 1, adjusted,
          type == NativeCombatConstants.DAMAGE_POISON ? 0 : magicReduction, 0);
      absorbPercent = type == NativeCombatConstants.DAMAGE_POISON ? 0 : Math.min(40, absorbPercent);
      if (type == NativeCombatConstants.DAMAGE_POISON) absorbFlat = 0;
    } else {
      reduced = NativeCombatMath.legacyElementalResistance(raw, immune, resistance,
          maximum > 0 ? maximum : 75, pierce, type != NativeCombatConstants.DAMAGE_POISON,
          magicReduction, 1);
    }
    int absorbed = nativePacket ? NativeMitigationMath.absorb(reduced, 1, absorbPercent, absorbFlat)
        : NativeCombatMath.elementalAbsorb(reduced, absorbPercent, absorbFlat, 1);
    int damage = reduced - absorbed;
    if (!nativePacket && attackerPlayer && defenderPlayer)
      damage = damage * NativeCombatConstants.PVP_DAMAGE_PERCENT / 100;
    out.hit = true;
    out.hitChance = 100;
    out.elementalDamage[type] = Math.max(0, damage);
    out.absorbedLife = absorbed;
    out.totalDamage = out.elementalDamage[type];
  }
}
