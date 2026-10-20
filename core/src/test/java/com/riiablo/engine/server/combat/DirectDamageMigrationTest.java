package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.*;
import static com.riiablo.engine.server.combat.CombatSystem.*;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import org.junit.jupiter.api.Test;

/** Frozen 122f51f9b helpers retained below. Changed production settlement uses
 * an independent D2MOO transcription; unchanged duration adapters retain the frozen oracle. */
class DirectDamageMigrationTest extends RiiabloTest {
  @Test
  void directAndDurationAdaptersMatchPreviousAcrossTwoThousandSnapshots() throws IllegalAccessException {
    CombatSystem host = new CombatSystem();
    java.util.Random random = new java.util.Random(0x110F);
    Attributes attrs = Attributes.obtainStandard();
    short[] resistance = {Stat.damageresist, Stat.fireresist, Stat.lightresist, Stat.coldresist, Stat.poisonresist, Stat.magicresist};
    short[] maximum = {Stat.maxfireresist, Stat.maxlightresist, Stat.maxcoldresist, Stat.maxpoisonresist, Stat.maxmagicresist};
    for (int i = 0; i < 2000; i++) {
      attrs.base().clear();
      for (short stat : resistance) attrs.base().put(stat, random.nextInt(401) - 200);
      for (short stat : maximum) attrs.base().put(stat, random.nextInt(50));
      attrs.base().put(Stat.item_absorbfire_percent, random.nextInt(131) - 10);
      attrs.base().put(Stat.item_absorbfire, random.nextInt(10) - 2);
      attrs.base().put(Stat.item_absorblight_percent, random.nextInt(131) - 10);
      attrs.base().put(Stat.item_absorblight, random.nextInt(10) - 2);
      attrs.base().put(Stat.item_absorbcold_percent, random.nextInt(131) - 10);
      attrs.base().put(Stat.item_absorbcold, random.nextInt(10) - 2);
      attrs.base().put(Stat.item_absorbmagic_percent, random.nextInt(131) - 10);
      attrs.base().put(Stat.item_absorbmagic, random.nextInt(10) - 2);
      attrs.base().put(Stat.normal_damage_reduction, random.nextInt(26) - 5);
      attrs.base().put(Stat.magic_damage_reduction, random.nextInt(26) - 5);
      attrs.base().put(Stat.item_poisonlengthresist, random.nextInt(201) - 50);
      attrs.base().put(Stat.item_cannotbefrozen, i % 23 == 0 ? 1 : 0);
      attrs.base().put(Stat.item_halffreezeduration, random.nextBoolean() ? 1 : 0);
      attrs.reset();
      attrs.setDifficultyResistanceIncluded(random.nextBoolean());
      StateList states = i % 3 == 0 ? null : new StateList(1);
      if (states != null) {
        states.addState(StateId.CONVICTION, 0);
        for (short stat : resistance) states.getState(StateId.CONVICTION).setNativeModifier(stat, random.nextInt(41) - 20);
        states.getState(StateId.CONVICTION).setNativeModifier(Stat.maxcoldresist, random.nextInt(10));
        if (i % 17 == 0) states.addState(StateId.SHRINE_RESIST_POISON, 0);
      }
      com.riiablo.codec.excel.DifficultyLevels.Entry row = new com.riiablo.codec.excel.DifficultyLevels.Entry();
      row.ResistPenalty = random.nextInt(151) - 100;
      NativeCombatContext context = NativeCombatContext.forGameplay(random.nextInt(3), row, random.nextBoolean());
      boolean player = random.nextBoolean();
      boolean attacker = random.nextBoolean();
      int raw = i % 25 == 0 ? Integer.MAX_VALUE : random.nextInt(100001);
      int length = i % 25 == 0 ? Integer.MAX_VALUE : random.nextInt(1001);
      int pierce = random.nextInt(271) - 20;
      int type = 1 + random.nextInt(5);
      NativeFixedElementalMath.Result expected = NativeMitigationReference.attributes(attrs, player,
          attacker, type, raw, pierce, 1, 0, false, states, context);
      CombatResult direct = host.calculateFixedElementalDamage(attrs, player, attacker, type, raw, pierce, states, context);
      assertEquals(expected.damageFixed, direct.totalDamage, "direct snapshot " + i);
      assertEquals(expected.absorbedLifeFixed, direct.absorbedLife);
      assertPacket(direct, host.calculateNativeFixedElementalDamage(attrs, player, attacker, type, raw, pierce, states, context));
      assertPacket(previousNativePhysical(attrs, player, raw, states),
          host.calculateNativeFixedPhysicalDamage(attrs, player, raw, states));
      NativeFixedElementalMath.Result reflected = NativeMitigationReference.attributes(attrs, player,
          attacker, DAMAGE_PHYSICAL, raw, 0, 1, 0, false, states, context);
      assertEquals(reflected.damageFixed, host.calculateFixedPhysicalDamage(attrs, player, attacker, raw, states).totalDamage);
      assertEquals(previousCold(attrs, player, length, pierce, states, context),
          host.resolveColdLengthBeforeState(attrs, player, length, pierce, states, context));
      assertEquals(previousPoison(attrs, player, length, pierce, states, context),
          CombatSystem.resolvePoisonDuration(attrs, player, length, pierce, states, context));
    }
  }

  @Test
  void poisonMasterySnapshotAndConversionsRemainCompatible() {
    CombatSystem host = new CombatSystem();
    Attributes attacker = Attributes.obtainStandard();
    Attributes defender = Attributes.obtainStandard();
    defender.base().clear();
    defender.base().put(Stat.hitpoints, 100);
    defender.reset();
    for (int mastery : new int[] {-200, -100, 0, 50, 200}) {
      attacker.base().clear();
      attacker.base().put(Stat.passive_pois_mastery, mastery);
      attacker.reset();
      for (int raw : new int[] {-1, 0, 11, 256, Integer.MAX_VALUE}) {
        long previous = (long) Math.max(0, raw) * Math.max(0, 100 + statInt(attacker, Stat.passive_pois_mastery, 0)) / 100L;
        CombatResult packet = host.calculateFixedPoisonDamage(attacker, defender, false, true, raw, 100, null, NativeCombatContext.normal());
        assertEquals(previous >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) previous, packet.poisonRawDamageFixed);
        assertEquals(0, packet.totalDamage);
      }
    }
    for (int damage : new int[] {-1, 0, 1, 17, Integer.MAX_VALUE}) {
      for (int frames : new int[] {-1, 0, 1, 75, Integer.MAX_VALUE}) {
        long rate = damage <= 0 || frames <= 0 ? 0 : (long) damage * 256L / frames;
        int expected = rate >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rate;
        assertEquals(expected, CombatSystem.totalPoisonToBitRate(damage, frames));
        assertEquals(expected / 256f, CombatSystem.totalPoisonToPerFrame(damage, frames));
      }
    }
  }

  private static void assertPacket(NativeDamagePacket expected, NativeDamagePacket actual) throws IllegalAccessException {
    for (java.lang.reflect.Field field : NativeDamagePacket.class.getFields()) {
      if (field.getType() == int[].class) assertArrayEquals((int[]) field.get(expected), (int[]) field.get(actual), field.getName());
      else assertEquals(field.get(expected), field.get(actual), field.getName());
    }
  }

  private static CombatResult previousElemental(
      Attributes defender, boolean defenderPlayer, boolean attackerPlayer,
      int damageType, int rawDamage, int piercePercent,
      StateList defenderStates, NativeCombatContext context, boolean nativePacket) {
    CombatResult result = new CombatResult();
    result.reset();
    if (defender == null || damageType <= DAMAGE_PHYSICAL
        || damageType >= DAMAGE_TYPE_COUNT || rawDamage <= 0) return result;

    DefenderData d = new DefenderData();
    d.isPlayer = defenderPlayer;
    d.isMonster = !defenderPlayer;
    d.resistances[DAMAGE_FIRE] = statInt(defender, Stat.fireresist, 0);
    d.resistances[DAMAGE_LIGHTNING] = statInt(defender, Stat.lightresist, 0);
    d.resistances[DAMAGE_COLD] = statInt(defender, Stat.coldresist, 0);
    d.resistances[DAMAGE_POISON] = statInt(defender, Stat.poisonresist, 0);
    d.resistances[DAMAGE_MAGIC] = statInt(defender, Stat.magicresist, 0);
    int penalty = defenderPlayer ? NativeDifficultyRules.remainingResistancePenalty(defender, context) : 0;
    // Native difficulty penalties exclude physical and magic resistance.
    for (int i = DAMAGE_FIRE; i < DAMAGE_TYPE_COUNT; i++) {
      if (i != DAMAGE_MAGIC) d.resistances[i] += penalty;
    }
    d.maxResistances[DAMAGE_FIRE] = 75 + statInt(defender, Stat.maxfireresist, 0);
    d.maxResistances[DAMAGE_LIGHTNING] = 75 + statInt(defender, Stat.maxlightresist, 0);
    d.maxResistances[DAMAGE_COLD] = 75 + statInt(defender, Stat.maxcoldresist, 0);
    d.maxResistances[DAMAGE_POISON] = 75 + statInt(defender, Stat.maxpoisonresist, 0);
    d.maxResistances[DAMAGE_MAGIC] = 75 + statInt(defender, Stat.maxmagicresist, 0);
    d.absorbPercent[DAMAGE_FIRE] = statInt(defender, Stat.item_absorbfire_percent, 0);
    d.absorbPercent[DAMAGE_LIGHTNING] = statInt(defender, Stat.item_absorblight_percent, 0);
    d.absorbPercent[DAMAGE_COLD] = statInt(defender, Stat.item_absorbcold_percent, 0);
    d.absorbPercent[DAMAGE_MAGIC] = statInt(defender, Stat.item_absorbmagic_percent, 0);
    d.absorbFlat[DAMAGE_FIRE] = statInt(defender, Stat.item_absorbfire, 0);
    d.absorbFlat[DAMAGE_LIGHTNING] = statInt(defender, Stat.item_absorblight, 0);
    d.absorbFlat[DAMAGE_COLD] = statInt(defender, Stat.item_absorbcold, 0);
    d.absorbFlat[DAMAGE_MAGIC] = statInt(defender, Stat.item_absorbmagic, 0);
    d.magicDamageReduced = Math.max(0,
        statInt(defender, Stat.magic_damage_reduction, 0));
    if (defenderStates != null) {
      d.resistances[DAMAGE_FIRE] += defenderStates.getTotalResistModifier(0);
      d.resistances[DAMAGE_COLD] += defenderStates.getTotalResistModifier(1);
      d.resistances[DAMAGE_LIGHTNING] += defenderStates.getTotalResistModifier(2);
      d.resistances[DAMAGE_POISON] += defenderStates.getTotalResistModifier(3);
      d.resistances[DAMAGE_MAGIC] += defenderStates.getTotalResistModifier(4);
      d.maxResistances[DAMAGE_FIRE] +=
          defenderStates.getTotalStatContribution(Stat.maxfireresist);
      d.maxResistances[DAMAGE_COLD] +=
          defenderStates.getTotalStatContribution(Stat.maxcoldresist);
      d.maxResistances[DAMAGE_LIGHTNING] +=
          defenderStates.getTotalStatContribution(Stat.maxlightresist);
      d.maxResistances[DAMAGE_POISON] +=
          defenderStates.getTotalStatContribution(Stat.maxpoisonresist);
      d.maxResistances[DAMAGE_MAGIC] +=
          defenderStates.getTotalStatContribution(Stat.maxmagicresist);
    }
    // Native SUNITDMG only preserves >=100 elemental resistance as immunity
    // for monsters. Player resistance is still clamped to the raised
    // 75..95 maximum-resistance cap below.
    d.immuneElemental[damageType] = d.isMonster && d.resistances[damageType] >= 100;
    int reduced;
    if (nativePacket) {
      int resistance = d.resistances[damageType] - Math.max(0, piercePercent);
      if (d.isPlayer) resistance = Math.min(Math.min(ABSOLUTE_MAX_RESISTANCE,
          d.maxResistances[damageType]), resistance);
      resistance = Math.max(MIN_RESISTANCE, Math.min(100, resistance));
      int afterReduction = Math.max(0, rawDamage - (damageType == DAMAGE_POISON ? 0 : d.magicDamageReduced));
      reduced = d.immuneElemental[damageType] ? 0 : (int) Math.min(Integer.MAX_VALUE,
          (long) afterReduction * (100 - resistance) / 100L);
      d.absorbPercent[damageType] = Math.min(40, d.absorbPercent[damageType]);
    } else {
      reduced = applyElementalResistance(rawDamage, d, damageType, piercePercent);
    }
    int absorbed = absorbElementalDamage(reduced, d, damageType);
    int finalDamage = reduced - absorbed;
    if (attackerPlayer && defenderPlayer) finalDamage = finalDamage * PVP_DAMAGE_PERCENT / 100;
    result.hit = true;
    result.hitChance = 100;
    result.elementalDamage[damageType] = Math.max(0, finalDamage);
    result.absorbedLife = absorbed;
    result.totalDamage = result.elementalDamage[damageType];
    return result;
  }

  private static CombatResult previousNativePhysical(
      Attributes defender, boolean defenderPlayer, int rawDamage, StateList defenderStates) {
    CombatResult result = new CombatResult();
    result.reset();
    if (defender == null || rawDamage <= 0) return result;
    int resistance = statInt(defender, Stat.damageresist, 0);
    if (defenderStates != null) resistance += defenderStates.getTotalPhysicalResistModifier();
    resistance = Math.max(MIN_RESISTANCE, Math.min(defenderPlayer ? 50 : 100, resistance));
    int afterReduction = Math.max(0,
        rawDamage - Math.max(0, statInt(defender, Stat.normal_damage_reduction, 0)));
    result.hit = true;
    result.hitChance = 100;
    result.physicalDamage = saturatingInt((long) afterReduction * (100 - resistance) / 100L);
    result.totalDamage = result.physicalDamage;
    return result;
  }

  private static CombatResult previousReflected(
      Attributes defender, boolean defenderPlayer, boolean attackerPlayer,
      int rawDamage, StateList defenderStates) {
    CombatResult result = new CombatResult();
    result.reset();
    if (defender == null || rawDamage <= 0) return result;

    DefenderData d = new DefenderData();
    d.isPlayer = defenderPlayer;
    d.isMonster = !defenderPlayer;
    d.resistances[DAMAGE_PHYSICAL] = statInt(defender, Stat.damageresist, 0);
    if (defenderStates != null) {
      d.resistances[DAMAGE_PHYSICAL] += defenderStates.getTotalPhysicalResistModifier();
    }
    d.damageReducedPercent = Math.max(MIN_RESISTANCE, d.resistances[DAMAGE_PHYSICAL]);
    d.damageReduced = Math.max(0, statInt(defender, Stat.normal_damage_reduction, 0));
    d.immunePhysical = d.resistances[DAMAGE_PHYSICAL] >= 100;
    int damage = applyPhysicalDamageReduction(rawDamage, d);
    if (attackerPlayer && defenderPlayer) damage = damage * PVP_DAMAGE_PERCENT / 100;
    result.hit = true;
    result.hitChance = 100;
    result.physicalDamage = Math.max(0, damage);
    result.totalDamage = result.physicalDamage;
    return result;
  }

  private static int previousCold(Attributes defender, boolean defenderPlayer,
      int baseDuration, int piercePercent, StateList defenderStates, NativeCombatContext context) {
    if (defender == null || baseDuration <= 0) return 0;
    if (statInt(defender, Stat.item_cannotbefrozen, 0) != 0) return 0;
    // SUNITDMG_CalculateTotalDamage halves the packet before its Flen row.
    if (statInt(defender, Stat.item_halffreezeduration, 0) != 0) baseDuration /= 2;
    int resistance = statInt(defender, Stat.coldresist, 0);
    int maximum = 75 + statInt(defender, Stat.maxcoldresist, 0);
    if (defenderPlayer) resistance += NativeDifficultyRules.remainingResistancePenalty(defender, context);
    if (defenderStates != null) {
      resistance += defenderStates.getTotalResistModifier(1);
      maximum += defenderStates.getTotalStatContribution(Stat.maxcoldresist);
    }
    // SUnitDmg.cpp's Flen row has DAMAGE_REDUCTION_NONE: MDR and absorb
    // affect damage, never length. StateUpdater owns ColdDiv/FreezeDiv and
    // player/boss downgrade, so do not pre-divide monster duration here.
    if (!defenderPlayer && resistance >= 100) return 0;
    resistance -= Math.max(0, piercePercent);
    resistance = Math.max(MIN_RESISTANCE, defenderPlayer
        ? Math.min(Math.min(ABSOLUTE_MAX_RESISTANCE, maximum), resistance) : resistance);
    int length = (int) Math.min(Integer.MAX_VALUE, (long) baseDuration * (100 - resistance) / 100L);
    return Math.max(0, length);
  }

  private static int previousPoison(Attributes defender, boolean defenderPlayer,
      int baseDuration, int piercePercent, StateList defenderStates, NativeCombatContext context) {
    if (defender == null || baseDuration <= 0) return 0;
    if (defenderStates != null && defenderStates.hasState(StateId.SHRINE_RESIST_POISON)) {
      return 0;
    }
    int resistance = statInt(defender, Stat.item_poisonlengthresist, 0)
        - Math.max(0, Math.min(100, piercePercent));
    // CharData folds elemental resistance only, not poison length resistance.
    if (defenderPlayer) resistance += NativeDifficultyRules.playerResistancePenalty(context);
    resistance = defenderPlayer
        ? Math.max(-100, Math.min(75, resistance))
        : Math.max(-100, Math.min(100, resistance));
    long resolved = (long) baseDuration * (100 - resistance) / 100L;
    return resolved >= Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, (int) resolved);
  }

  private static int applyElementalResistance(int damage, DefenderData defender, int damageType,
      int piercePercent) {
    return applyElementalResistance(damage, defender, damageType, piercePercent, 1);
  }

  private static int applyElementalResistance(int damage, DefenderData defender, int damageType,
      int piercePercent, int scale) {
    // Preserve the early immunity return and missing-channel default from the host adapter.
    if (defender.immuneElemental[damageType]) return 0;
    int maximum = defender.maxResistances != null
        && damageType < defender.maxResistances.length && defender.maxResistances[damageType] > 0
        ? defender.maxResistances[damageType] : 75;
    return NativeCombatMath.legacyElementalResistance(damage, false,
        defender.resistances[damageType], maximum, piercePercent,
        damageType != DAMAGE_POISON && damageType != DAMAGE_PHYSICAL,
        defender.magicDamageReduced, scale);
  }

  private static int absorbElementalDamage(int damage, DefenderData defender, int damageType) {
    return absorbElementalDamage(damage, defender, damageType, 1);
  }

  private static int absorbElementalDamage(int damage, DefenderData defender, int damageType, int scale) {
    if (damage <= 0 || defender.absorbPercent == null || damageType >= defender.absorbPercent.length) {
      return 0;
    }
    int flat = defender.absorbFlat != null && damageType < defender.absorbFlat.length
        ? defender.absorbFlat[damageType] : 0;
    return NativeCombatMath.elementalAbsorb(damage, defender.absorbPercent[damageType], flat, scale);
  }

  private static int applyPhysicalDamageReduction(int damage, DefenderData defender) {
    return applyPhysicalDamageReduction(damage, defender, false);
  }

  private static int applyPhysicalDamageReduction(
      int damage, DefenderData defender, boolean bypassResistance) {
    return NativeCombatMath.physicalReduction(damage, defender.immunePhysical,
        defender.damageReducedPercent, defender.damageReduced, bypassResistance);
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
