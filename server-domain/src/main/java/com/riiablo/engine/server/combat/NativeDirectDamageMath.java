package com.riiablo.engine.server.combat;

/** No-roll direct packet arithmetic. Native and legacy mitigation remain separate policies. */
public final class NativeDirectDamageMath {
  private NativeDirectDamageMath() {}

  public static int nativePhysicalDamage(int raw, boolean player, int resistance, int flatReduction) {
    resistance = Math.max(NativeCombatConstants.MIN_RESISTANCE, Math.min(player ? 50 : 100, resistance));
    int reduced = Math.max(0, raw - Math.max(0, flatReduction));
    long damage = (long) reduced * (100 - resistance) / 100L;
    return damage >= Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, (int) damage);
  }

  public static int reflectedPhysicalDamage(int raw, boolean defenderPlayer, boolean attackerPlayer,
      int resistance, int flatReduction) {
    int damage = NativeCombatMath.physicalReduction(raw, resistance >= 100,
        Math.max(NativeCombatConstants.MIN_RESISTANCE, resistance), Math.max(0, flatReduction), false);
    if (attackerPlayer && defenderPlayer) damage = damage * NativeCombatConstants.PVP_DAMAGE_PERCENT / 100;
    return Math.max(0, damage);
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
      int adjusted = resistance - Math.max(0, pierce);
      if (defenderPlayer) adjusted = Math.min(Math.min(NativeCombatConstants.ABSOLUTE_MAX_RESISTANCE,
          maximum), adjusted);
      adjusted = Math.max(NativeCombatConstants.MIN_RESISTANCE, Math.min(100, adjusted));
      int afterReduction = Math.max(0, raw - (type == NativeCombatConstants.DAMAGE_POISON ? 0 : magicReduction));
      reduced = immune ? 0 : (int) Math.min(Integer.MAX_VALUE,
          (long) afterReduction * (100 - adjusted) / 100L);
      absorbPercent = Math.min(40, absorbPercent);
    } else {
      reduced = NativeCombatMath.legacyElementalResistance(raw, immune, resistance,
          maximum > 0 ? maximum : 75, pierce, type != NativeCombatConstants.DAMAGE_POISON,
          magicReduction, 1);
    }
    int absorbed = NativeCombatMath.elementalAbsorb(reduced, absorbPercent, absorbFlat, 1);
    int damage = reduced - absorbed;
    if (attackerPlayer && defenderPlayer) damage = damage * NativeCombatConstants.PVP_DAMAGE_PERCENT / 100;
    out.hit = true;
    out.hitChance = 100;
    out.elementalDamage[type] = Math.max(0, damage);
    out.absorbedLife = absorbed;
    out.totalDamage = out.elementalDamage[type];
  }
}
