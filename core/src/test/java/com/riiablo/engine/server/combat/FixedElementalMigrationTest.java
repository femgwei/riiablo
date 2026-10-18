package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.*;
import static com.riiablo.engine.server.combat.CombatSystem.*;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.server.state.StateList;
import org.junit.jupiter.api.Test;

/** Frozen 20c73a1d7 migration oracle, not a new claim of native-mechanics correctness. */
class FixedElementalMigrationTest extends RiiabloTest {
  @Test
  void publicRateAndStaticAdaptersMatchPreviousAcrossTwoThousandSnapshots() {
    CombatSystem host = new CombatSystem();
    java.util.Random random = new java.util.Random(0x110F);
    Attributes attrs = Attributes.obtainStandard();
    short[] resistance = {0, Stat.fireresist, Stat.lightresist, Stat.coldresist, Stat.poisonresist, Stat.magicresist};
    short[] maximum = {0, Stat.maxfireresist, Stat.maxlightresist, Stat.maxcoldresist, Stat.maxpoisonresist, Stat.maxmagicresist};
    short[] absorbPercent = {0, Stat.item_absorbfire_percent, Stat.item_absorblight_percent, Stat.item_absorbcold_percent, 0, Stat.item_absorbmagic_percent};
    short[] absorbFlat = {0, Stat.item_absorbfire, Stat.item_absorblight, Stat.item_absorbcold, 0, Stat.item_absorbmagic};
    for (int i = 0; i < 2000; i++) {
      attrs.base().clear();
      int type = 1 + random.nextInt(5);
      attrs.base().put(resistance[type], random.nextInt(401) - 200);
      attrs.base().put(maximum[type], random.nextInt(50));
      if (absorbPercent[type] != 0) attrs.base().put(absorbPercent[type], random.nextInt(131) - 10);
      if (absorbFlat[type] != 0) attrs.base().put(absorbFlat[type], random.nextInt(10) - 2);
      attrs.base().put(Stat.magic_damage_reduction, random.nextInt(26) - 5);
      attrs.reset();
      attrs.setDifficultyResistanceIncluded(random.nextBoolean());
      com.riiablo.codec.excel.DifficultyLevels.Entry row = new com.riiablo.codec.excel.DifficultyLevels.Entry();
      row.ResistPenalty = random.nextInt(151) - 100;
      NativeCombatContext context = NativeCombatContext.forGameplay(random.nextInt(3), row, random.nextBoolean());
      boolean defenderPlayer = random.nextBoolean();
      boolean attackerPlayer = random.nextBoolean();
      int raw = i % 25 == 0 ? Integer.MAX_VALUE : random.nextInt(100001);
      int rate = new int[] {-1, 0, 41, 1024, 2048}[random.nextInt(5)];
      int pierce = random.nextInt(271) - 20;
      assertResult(previousRate(attrs, defenderPlayer, attackerPlayer, type, raw, pierce, rate, null, context),
          host.calculateFixedElementalRateDamage(attrs, defenderPlayer, attackerPlayer, type, raw, pierce, rate, null, context));
      assertResult(previousStatic(attrs, defenderPlayer, attackerPlayer, type, raw, null, context),
          host.calculateStaticFieldDamage(attrs, defenderPlayer, attackerPlayer, type, raw, null, context));
    }
  }

  @Test
  void lightningKernelMatchesPreviousAndPreservesExistingPacketFields() {
    java.util.Random random = new java.util.Random(0xAB50B);
    for (int i = 0; i < 2000; i++) {
      AttackerData a = new AttackerData();
      DefenderData d = new DefenderData();
      a.isPlayer = random.nextBoolean();
      d.isPlayer = random.nextBoolean();
      d.isMonster = random.nextBoolean();
      d.immuneElemental[DAMAGE_LIGHTNING] = random.nextBoolean();
      d.resistances[DAMAGE_LIGHTNING] = random.nextInt(401) - 200;
      d.maxResistances[DAMAGE_LIGHTNING] = random.nextInt(126);
      a.elementalPierce[DAMAGE_LIGHTNING] = random.nextInt(301) - 50;
      d.magicDamageReduced = random.nextInt(26) - 5;
      d.absorbPercent[DAMAGE_LIGHTNING] = random.nextInt(131) - 10;
      d.absorbFlat[DAMAGE_LIGHTNING] = random.nextInt(10) - 2;
      int raw = i % 25 == 0 ? Integer.MAX_VALUE : random.nextInt(100001);
      CombatResult expected = new CombatResult();
      CombatResult actual = new CombatResult();
      expected.absorbedLife = actual.absorbedLife = i % 10 == 0 ? Integer.MAX_VALUE - 1 : i;
      expected.elementalDamage[DAMAGE_LIGHTNING] = actual.elementalDamage[DAMAGE_LIGHTNING] = 17;
      actual.elementalDamage[DAMAGE_FIRE] = 23;
      previousLightning(raw, a, d, expected);
      NativeFixedElementalMath.lightningDamage(actual, raw, a.isPlayer, d.isPlayer, d.isMonster,
          d.immuneElemental[DAMAGE_LIGHTNING], d.resistances[DAMAGE_LIGHTNING],
          d.maxResistances[DAMAGE_LIGHTNING], a.elementalPierce[DAMAGE_LIGHTNING],
          d.magicDamageReduced, d.absorbPercent[DAMAGE_LIGHTNING], d.absorbFlat[DAMAGE_LIGHTNING]);
      assertEquals(expected.elementalDamage[DAMAGE_LIGHTNING], actual.elementalDamage[DAMAGE_LIGHTNING]);
      assertEquals(expected.absorbedLife, actual.absorbedLife);
      assertEquals(23, actual.elementalDamage[DAMAGE_FIRE]);
    }
  }

  @Test
  void publicAdaptersRetainInvalidInputDefaultsAndCompatibilityResultTypes() {
    CombatSystem host = new CombatSystem();
    NativeCombatContext context = NativeCombatContext.normal();
    Attributes empty = Attributes.obtainStandard();
    empty.base().clear();
    empty.reset();
    for (int type : new int[] {-1, 0, 6, 100}) {
      assertResult(previousRate(null, true, true, type, 100, 0, 0, null, context),
          host.calculateFixedElementalRateDamage(null, true, true, type, 100, 0, 0, null, context));
      assertResult(previousStatic(null, true, true, type, 100, null, context),
          host.calculateStaticFieldDamage(null, true, true, type, 100, null, context));
      assertResult(previousRate(empty, true, true, type, 100, 0, 0, null, context),
          host.calculateFixedElementalRateDamage(empty, true, true, type, 100, 0, 0, null, context));
      assertResult(previousStatic(empty, true, true, type, 100, null, context),
          host.calculateStaticFieldDamage(empty, true, true, type, 100, null, context));
    }
    for (int raw : new int[] {-1, 0}) {
      assertResult(previousRate(empty, true, true, DAMAGE_FIRE, raw, 0, 0, null, context),
          host.calculateFixedElementalRateDamage(empty, true, true, DAMAGE_FIRE, raw, 0, 0, null, context));
      assertResult(previousStatic(empty, true, true, DAMAGE_LIGHTNING, raw, null, context),
          host.calculateStaticFieldDamage(empty, true, true, DAMAGE_LIGHTNING, raw, null, context));
    }
    assertTrue(new FixedElementalDamageResult() instanceof NativeFixedElementalMath.Result);
    assertTrue(new StaticFieldDamageResult() instanceof NativeFixedElementalMath.Result);
  }

  private static void assertResult(NativeFixedElementalMath.Result expected, NativeFixedElementalMath.Result actual) {
    assertEquals(expected.damageFixed, actual.damageFixed);
    assertEquals(expected.absorbedLifeFixed, actual.absorbedLifeFixed);
    assertEquals(expected.immune, actual.immune);
  }

  private static FixedElementalDamageResult previousRate(
      Attributes defender, boolean defenderPlayer, boolean attackerPlayer,
      int damageType, int rawDamageFixed, int piercePercent, int damageRate,
      StateList defenderStates, NativeCombatContext context) {
    FixedElementalDamageResult result = new FixedElementalDamageResult();
    if (defender == null || damageType <= DAMAGE_PHYSICAL
        || damageType >= DAMAGE_TYPE_COUNT || rawDamageFixed <= 0) return result;

    short[] resistanceStats = {0, Stat.fireresist, Stat.lightresist,
        Stat.coldresist, Stat.poisonresist, Stat.magicresist};
    short[] maximumStats = {0, Stat.maxfireresist, Stat.maxlightresist,
        Stat.maxcoldresist, Stat.maxpoisonresist, Stat.maxmagicresist};
    short[] absorbPercentStats = {0, Stat.item_absorbfire_percent,
        Stat.item_absorblight_percent, Stat.item_absorbcold_percent, 0,
        Stat.item_absorbmagic_percent};
    short[] absorbFlatStats = {0, Stat.item_absorbfire, Stat.item_absorblight,
        Stat.item_absorbcold, 0, Stat.item_absorbmagic};
    int[] stateResistanceTypes = {-1, 0, 2, 1, 3, 4};

    int resistance = statInt(defender, resistanceStats[damageType], 0);
    if (defenderStates != null) {
      resistance += defenderStates.getTotalResistModifier(
          stateResistanceTypes[damageType]);
    }
    if (defenderPlayer && damageType != DAMAGE_MAGIC) {
      resistance += NativeDifficultyRules.remainingResistancePenalty(defender, context);
    }
    if (!defenderPlayer && resistance >= 100) {
      result.immune = true;
      return result;
    }
    resistance -= Math.max(0, piercePercent);
    int maximumResistance = Math.min(ABSOLUTE_MAX_RESISTANCE,
        75 + statInt(defender, maximumStats[damageType], 0));
    resistance = Math.max(MIN_RESISTANCE, Math.min(maximumResistance, resistance));

    // Native SUNITDMG_CalculateTotalDamage scales PvP before flat reduction,
    // resistance and absorb. This ordering matters for sub-point fire ticks.
    long reduced = rawDamageFixed;
    if (attackerPlayer && defenderPlayer) {
      reduced = reduced * PVP_DAMAGE_PERCENT / 100L;
    }
    long flatReduction = (long) Math.max(0,
        statInt(defender, Stat.magic_damage_reduction, 0)) << 8;
    if (flatReduction > 0 && damageRate > 0) {
      flatReduction = flatReduction * damageRate / 1024L;
    }
    reduced = Math.max(0L, reduced - flatReduction);
    reduced = reduced * (100L - resistance) / 100L;

    int absorbPercent = absorbPercentStats[damageType] != 0
        ? Math.max(0, Math.min(40,
            statInt(defender, absorbPercentStats[damageType], 0))) : 0;
    long absorbed = reduced * absorbPercent / 100L;
    if (absorbFlatStats[damageType] != 0) {
      long flatFixed = (long) Math.max(0,
          statInt(defender, absorbFlatStats[damageType], 0)) << 8;
      absorbed += Math.min(flatFixed, Math.max(0L, reduced - absorbed));
    }
    absorbed = Math.min(reduced, Math.max(0L, absorbed));
    long damage = Math.max(0L, reduced - absorbed);

    result.damageFixed = saturatingInt(damage);
    result.absorbedLifeFixed = saturatingInt(absorbed);
    return result;
  }

  private static StaticFieldDamageResult previousStatic(
      Attributes defender, boolean defenderPlayer, boolean attackerPlayer,
      int damageType, int rawDamageFixed, StateList defenderStates, NativeCombatContext context) {
    StaticFieldDamageResult result = new StaticFieldDamageResult();
    if (defender == null || damageType <= DAMAGE_PHYSICAL
        || damageType >= DAMAGE_TYPE_COUNT || rawDamageFixed <= 0) return result;

    short[] resistanceStats = {0, Stat.fireresist, Stat.lightresist,
        Stat.coldresist, Stat.poisonresist, Stat.magicresist};
    short[] maximumStats = {0, Stat.maxfireresist, Stat.maxlightresist,
        Stat.maxcoldresist, Stat.maxpoisonresist, Stat.maxmagicresist};
    short[] absorbPercentStats = {0, Stat.item_absorbfire_percent,
        Stat.item_absorblight_percent, Stat.item_absorbcold_percent, 0,
        Stat.item_absorbmagic_percent};
    short[] absorbFlatStats = {0, Stat.item_absorbfire, Stat.item_absorblight,
        Stat.item_absorbcold, 0, Stat.item_absorbmagic};
    int[] stateResistanceTypes = {-1, 0, 2, 1, 3, 4};

    int resistance = statInt(defender, resistanceStats[damageType], 0);
    if (defenderStates != null) {
      resistance += defenderStates.getTotalResistModifier(
          stateResistanceTypes[damageType]);
    }
    if (defenderPlayer && damageType != DAMAGE_MAGIC) {
      resistance += NativeDifficultyRules.remainingResistancePenalty(defender, context);
    }
    if (!defenderPlayer && resistance >= 100) {
      result.immune = true;
      return result;
    }
    int maximumResistance = 75 + statInt(defender, maximumStats[damageType], 0);
    maximumResistance = Math.min(ABSOLUTE_MAX_RESISTANCE, maximumResistance);
    resistance = Math.max(MIN_RESISTANCE, Math.min(maximumResistance, resistance));

    long adjusted = rawDamageFixed;
    if (resistance < 0) {
      adjusted = 100L * adjusted / (100L - resistance);
    }
    long reduced = adjusted * (100L - resistance) / 100L;
    // Magic Damage Reduced by X applies to the same non-poison elemental
    // packet as the regular attack path, after resistance and before absorb.
    if (damageType != DAMAGE_POISON) {
      reduced = Math.max(0L, reduced - statInt(defender, Stat.magic_damage_reduction, 0));
    }
    int absorbPercent = absorbPercentStats[damageType] != 0
        ? Math.max(0, Math.min(100,
            statInt(defender, absorbPercentStats[damageType], 0))) : 0;
    long absorbed = reduced * absorbPercent / 100L;
    if (absorbFlatStats[damageType] != 0) {
      long flatFixed = (long) Math.max(0,
          statInt(defender, absorbFlatStats[damageType], 0)) << 8;
      absorbed += Math.min(flatFixed, Math.max(0L, reduced - absorbed));
    }
    absorbed = Math.min(reduced, Math.max(0L, absorbed));
    long damage = Math.max(0L, reduced - absorbed);
    if (attackerPlayer && defenderPlayer) damage = damage * PVP_DAMAGE_PERCENT / 100L;

    result.damageFixed = saturatingInt(damage);
    result.absorbedLifeFixed = saturatingInt(absorbed);
    return result;
  }

  private static void previousLightning(int rawDamage, AttackerData attacker,
      DefenderData defender, CombatResult result) {
    int type = DAMAGE_LIGHTNING;
    long resistance = defender.resistances[type];
    // Monster immunity is checked before passive pierce; player resistance
    // above 100 remains subject to the ordinary maximum resistance cap.
    if (defender.immuneElemental[type] || (defender.isMonster && resistance >= 100)) return;
    resistance -= attacker.elementalPierce[type];
    if (!defender.isMonster) {
      resistance = Math.min(resistance,
          Math.min(ABSOLUTE_MAX_RESISTANCE, defender.maxResistances[type]));
    }
    resistance = Math.max(MIN_RESISTANCE, Math.min(100L, resistance));

    long damage = rawDamage;
    if (attacker.isPlayer && defender.isPlayer) damage = damage * PVP_DAMAGE_PERCENT / 100L;
    damage = Math.max(0L, damage - (long) Math.max(0, defender.magicDamageReduced) * 256);
    damage = damage * (100 - resistance) / 100L;

    int percent = Math.max(0, Math.min(40, defender.absorbPercent[type]));
    long absorbed = damage * percent / 100L;
    absorbed += Math.min(damage - absorbed,
        (long) Math.max(0, defender.absorbFlat[type]) * 256);
    result.elementalDamage[type] = saturatingInt(damage - absorbed);
    result.absorbedLife = saturatingInt((long) result.absorbedLife + absorbed);
  }

  private static int statInt(Attributes attrs, short stat, int defaultValue) {
    if (attrs == null) return defaultValue;
    StatRef ref = attrs.get(stat);
    return ref != null ? ref.asInt() : defaultValue;
  }

  private static int saturatingInt(long value) {
    return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, (int) value);
  }
}
