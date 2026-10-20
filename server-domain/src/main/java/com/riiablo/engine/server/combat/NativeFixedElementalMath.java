package com.riiablo.engine.server.combat;

/** Fixed8 direct, Static Field and DamageRate packets share native settlement rows. */
public final class NativeFixedElementalMath {
  private NativeFixedElementalMath() {}
  public static class Result {
    public int damageFixed;
    public int absorbedLifeFixed;
    public boolean immune;
    public void reset() { damageFixed = absorbedLifeFixed = 0; immune = false; }
  }

  public static void rateDamage(Result out, int rawDamageFixed, boolean defenderPlayer,
      boolean attackerPlayer, int resistance, int maximumBonus, int piercePercent,
      int magicReduction, int damageRate, int absorbPercent, int absorbFlat) {
    out.reset();
    if (rawDamageFixed <= 0) return;
    if (!defenderPlayer && resistance >= 100) { out.immune = true; return; }
    resistance = NativeMitigationMath.resistance(defenderPlayer, false, false,
        resistance, 75 + maximumBonus, piercePercent);
    int raw = NativeMitigationMath.pvp(rawDamageFixed, attackerPlayer && defenderPlayer, 256);
    int reduced = NativeMitigationMath.reduce(raw, 256, resistance, magicReduction, damageRate);
    out.absorbedLifeFixed = NativeMitigationMath.absorb(reduced, 256, absorbPercent, absorbFlat);
    out.damageFixed = reduced - out.absorbedLifeFixed;
  }

  /** SkillSor::AuraCallback_StaticField compensates negative resistance BEFORE
   * ExecuteEvents runs the ordinary PvP -> MDR -> resistance -> absorb rows. */
  public static void staticFieldDamage(Result out, int rawDamageFixed, boolean defenderPlayer,
      boolean attackerPlayer, int resistance, int maximumBonus, boolean poison,
      int magicReduction, int absorbPercent, int absorbFlat) {
    staticFieldDamage(out, rawDamageFixed, defenderPlayer, attackerPlayer, resistance,
        maximumBonus, poison, magicReduction, absorbPercent, absorbFlat, 0);
  }

  public static void staticFieldDamage(Result out, int rawDamageFixed, boolean defenderPlayer,
      boolean attackerPlayer, int resistance, int maximumBonus, boolean poison,
      int magicReduction, int absorbPercent, int absorbFlat, int pierce) {
    staticFieldDamage(out, rawDamageFixed, defenderPlayer, attackerPlayer, resistance,
        maximumBonus, poison, magicReduction, absorbPercent, absorbFlat, pierce, resistance);
  }

  public static void staticFieldDamage(Result out, int rawDamageFixed, boolean defenderPlayer,
      boolean attackerPlayer, int resistance, int maximumBonus, boolean poison,
      int magicReduction, int absorbPercent, int absorbFlat, int pierce, int compensationResistance) {
    // Compensation uses the raw stat, not the subsequently clamped resistance.
    if (compensationResistance < 0) rawDamageFixed = NativeMitigationMath.positive(
        NativeMitigationMath.ratio(100, rawDamageFixed,
            (int) Math.min(Integer.MAX_VALUE, 100L - compensationResistance)));
    rateDamage(out, rawDamageFixed, defenderPlayer, attackerPlayer, resistance, maximumBonus, pierce,
        poison ? 0 : magicReduction, 0, poison ? 0 : absorbPercent, poison ? 0 : absorbFlat);
  }

  /** Lightning writes only its own channel; no second PvP scaling by the caller. */
  public static void lightningDamage(NativeDamagePacket out, int rawDamage, boolean attackerPlayer,
      boolean defenderPlayer, boolean monster, boolean immune, int rawResistance,
      int maximumResistance, int pierce, int magicReduction, int absorbPercent, int absorbFlat) {
    if (immune || (monster && rawResistance >= 100)) return;
    Result result = new Result();
    rawDamage = NativeMitigationMath.pvp(rawDamage, attackerPlayer && defenderPlayer, 256);
    rateDamage(result, rawDamage, !monster, false, rawResistance,
        maximumResistance - 75, pierce, magicReduction, 0, absorbPercent, absorbFlat);
    out.elementalDamage[NativeCombatConstants.DAMAGE_LIGHTNING] = result.damageFixed;
    out.absorbedLife = NativeMitigationMath.positive((long) out.absorbedLife + result.absorbedLifeFixed);
  }
}
