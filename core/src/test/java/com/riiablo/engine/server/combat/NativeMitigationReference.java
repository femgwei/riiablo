package com.riiablo.engine.server.combat;

import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.state.StateList;
import static com.riiablo.engine.server.combat.CombatSystem.*;

/** Test-only transcription of D2MOO SUnitDmg's table rows. Intentionally does
 * not call the production mitigation kernels or the frozen migration oracle. */
final class NativeMitigationReference {
  static final short[] RES = {Stat.damageresist, Stat.fireresist, Stat.lightresist,
      Stat.coldresist, Stat.poisonresist, Stat.magicresist};
  static final short[] MAX = {0, Stat.maxfireresist, Stat.maxlightresist,
      Stat.maxcoldresist, Stat.maxpoisonresist, Stat.maxmagicresist};
  static final short[] PCT = {0, Stat.item_absorbfire_percent, Stat.item_absorblight_percent,
      Stat.item_absorbcold_percent, 0, Stat.item_absorbmagic_percent};
  static final short[] FLAT = {0, Stat.item_absorbfire, Stat.item_absorblight,
      Stat.item_absorbcold, 0, Stat.item_absorbmagic};
  static final int[] STATE = {-1, 0, 2, 1, 3, 4};

  static long percent(long value, long percent, int divisor) {
    if (value <= 1048576) {
      if (percent <= 65536) return value * percent / divisor;
      if (divisor <= (percent >> 4)) return value * (percent / divisor);
    } else if (divisor <= (value >> 4)) return percent * (value / divisor);
    return value * percent / divisor;
  }
  static int clamp(long value) { return (int) Math.max(0, Math.min(Integer.MAX_VALUE, value)); }

  static NativeFixedElementalMath.Result resolve(int raw, int scale, boolean player,
      boolean pvp, int type, int resistance, int maximum, int pierce, int reduction,
      int rate, int absorbPercent, int absorbFlat, boolean staticField) {
    NativeFixedElementalMath.Result out = new NativeFixedElementalMath.Result();
    if (raw <= 0) return out;
    long value = raw;
    if (staticField && resistance < 0) value = percent(100, value, 100 - resistance);
    if (!player && resistance >= 100) { out.immune = true; return out; }
    long res = resistance;
    if (type != DAMAGE_PHYSICAL && type != DAMAGE_MAGIC) res -= pierce;
    res = Math.max(-100, Math.min(player && res > 0 ? type == DAMAGE_PHYSICAL ? 50 : Math.min(95, maximum) : 100, res));
    if (pvp) value = scale == 256 ? percent(value, 17, 100) : value * 17 / 100;
    long dr = type == DAMAGE_POISON ? 0 : (long) Math.max(0, reduction) * scale;
    if (rate > 0) dr = percent(dr, rate, 1024);
    value = Math.max(0, value - dr);
    if (res != 0) value = scale == 256 ? percent(value, 100 - res, 100) : value * (100 - res) / 100;
    // The host saturates an out-of-range channel before absorption.
    value = clamp(value);
    long absorbed = 0;
    if (type != DAMAGE_POISON && type != DAMAGE_PHYSICAL) {
      int pct = Math.max(0, Math.min(40, absorbPercent));
      absorbed = scale == 256 ? percent(value, pct, 100) : value * pct / 100;
      absorbed += Math.min(value - absorbed, (long) Math.max(0, absorbFlat) * scale);
    }
    out.damageFixed = clamp(value - absorbed);
    out.absorbedLifeFixed = clamp(absorbed);
    return out;
  }

  static NativeFixedElementalMath.Result attributes(Attributes attrs, boolean player,
      boolean attacker, int type, int raw, int pierce, int scale, int rate,
      boolean staticField, StateList states, NativeCombatContext context) {
    int res = attrs.getValue(RES[type], 0);
    int maximum = type == DAMAGE_PHYSICAL ? 50 : 75 + attrs.getValue(MAX[type], 0);
    if (player && type != DAMAGE_PHYSICAL && type != DAMAGE_MAGIC)
      res += NativeDifficultyRules.remainingResistancePenalty(attrs, context);
    if (states != null) {
      res += type == DAMAGE_PHYSICAL ? states.getTotalPhysicalResistModifier()
          : states.getTotalResistModifier(STATE[type]);
      if (type != DAMAGE_PHYSICAL) maximum += states.getTotalStatContribution(MAX[type]);
    }
    int compensationResistance = res - (player && type != DAMAGE_PHYSICAL && type != DAMAGE_MAGIC
        ? NativeDifficultyRules.playerResistancePenalty(context) : 0);
    if (staticField && compensationResistance < 0)
      raw = clamp(percent(100, raw, 100 - compensationResistance));
    return resolve(raw, scale, player, attacker && player, type, res, maximum,
        type == DAMAGE_MAGIC ? 0 : pierce,
        attrs.getValue(type == DAMAGE_PHYSICAL ? Stat.normal_damage_reduction : Stat.magic_damage_reduction, 0),
        rate, PCT[type] == 0 ? 0 : attrs.getValue(PCT[type], 0),
        FLAT[type] == 0 ? 0 : attrs.getValue(FLAT[type], 0), false);
  }
}
