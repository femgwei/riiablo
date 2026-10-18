package com.riiablo.engine.server.combat;

import static com.riiablo.engine.server.combat.CombatSystem.*;
import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.DifficultyLevels;
import com.riiablo.engine.server.NativeRng;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import java.lang.reflect.Field;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SpecialAttackSnapshotMigrationTest extends RiiabloTest {
  private static final short[] MIN = {0, Stat.firemindam, Stat.lightmindam, Stat.coldmindam,
      Stat.poisonmindam, Stat.magicmindam};
  private static final short[] MAX = {0, Stat.firemaxdam, Stat.lightmaxdam, Stat.coldmaxdam,
      Stat.poisonmaxdam, Stat.magicmaxdam};

  private static final class CapturingSystem extends CombatSystem {
    AttackerData attacker;
    DefenderData defender;
    CapturingSystem(NativeRng rng) { super(rng); }
    @Override public CombatResult calculateAttack(AttackerData a, DefenderData d) {
      d.currentFrame = 2000; // same explicit clock as the frozen oracle harness
      attacker = a;
      defender = d;
      return super.calculateAttack(a, d);
    }
  }

  @Test
  void fiveEntryFamiliesMatchFrozenSnapshotsPacketsAndRngAcross3000Cases() throws Exception {
    Random random = new Random(0x32F72110L);
    for (int i = 0; i < 600; i++) {
      Attributes a = attributes(random, true), d = attributes(random, false);
      StateList attackerStates = states(random), defenderStates = states(random);
      DifficultyLevels.Entry row = new DifficultyLevels.Entry();
      row.ResistPenalty = -37;
      row.MonsterColdDivisor = 3;
      NativeCombatContext context = i % 2 == 0
          ? NativeCombatContext.forGameplay(random.nextInt(3), row, random.nextBoolean())
          : NativeCombatContext.ofDifficulty(random.nextInt(3));
      CombatSnapshotFactory.Request request = new CombatSnapshotFactory.Request(
          a, d, random.nextBoolean(), random.nextBoolean(), context);
      request.attackerStates = attackerStates;
      request.defenderStates = defenderStates;
      request.defenderMoving = random.nextBoolean();
      request.attackMinDamageOverride = random.nextInt(150) - 20;
      request.attackMaxDamageOverride = random.nextInt(200) - 20;
      request.attackRatingOverride = random.nextInt(600) - 100;
      request.alwaysHit = random.nextBoolean();
      request.missile = random.nextBoolean();
      request.physicalConversionPercent = random.nextInt(151) - 20;
      request.physicalConversionType = random.nextInt(8) - 1;
      request.coldLengthOverride = random.nextInt(100) - 20;
      request.poisonLengthOverride = random.nextInt(100) - 20;
      request.elementalMinOverride = random.nextBoolean() ? null : range(random);
      request.elementalMaxOverride = random.nextBoolean() ? null : range(random);
      request.fixedPointElements = random.nextBoolean();
      request.magicTargetBonusPercent = random.nextInt(100);
      request.defenderDemon = random.nextBoolean();
      request.defenderUndead = random.nextBoolean();
      request.defenderIgnoreTargetDefenseAllowed = random.nextBoolean();
      request.mastery = new StateList.WeaponMasteryBonus();
      request.mastery.attackRatingPercent = random.nextInt(100);
      request.mastery.damagePercent = random.nextInt(100);
      request.mastery.criticalChance = random.nextInt(40);
      int sourceScale = random.nextInt(257) - 10;
      int skillToHit = random.nextInt(151) - 25;
      int damageBonus = random.nextInt(151) - 25;
      for (int family = 0; family < 5; family++) {
        int seed = random.nextInt(), lastBlockFrame = random.nextInt(200);
        NativeRng oldRng = new NativeRng(seed), newRng = new NativeRng(seed);
        PreviousSpecialAttackSystem old = new PreviousSpecialAttackSystem(oldRng);
        CapturingSystem migrated = new CapturingSystem(newRng);
        d.aggregate().put(Stat.lastblockframe, lastBlockFrame);
        CombatResult expected = invoke(old, family, request, sourceScale, skillToHit, damageBonus);
        int persistedFrame = d.get(Stat.lastblockframe).asInt();
        d.aggregate().put(Stat.lastblockframe, lastBlockFrame);
        CombatResult actual = invoke(migrated, family, request, sourceScale, skillToHit, damageBonus);
        String label = "snapshot " + i + " family " + family;
        compareFields(NativeAttackData.class, old.capturedAttacker, migrated.attacker, label);
        compareFields(NativeDefenseData.class, old.capturedDefender, migrated.defender, label);
        compareFields(NativeDamagePacket.class, expected, actual, label);
        assertEquals(expected.totalDamageHp(), actual.totalDamageHp(), label);
        assertEquals(oldRng.state(), newRng.state(), label + " RNG");
        assertEquals(persistedFrame, d.get(Stat.lastblockframe).asInt(), label + " persisted frame");
        assertSame(d, migrated.defender.combatAttributes);
      }
    }
  }

  @Test
  void absentCombatantsStillReturnEmptyPacketsWithoutRandomConsumption() {
    NativeRng rng = new NativeRng(17);
    CombatSystem host = new CombatSystem(rng);
    int seed = rng.state();
    assertFalse(host.calculateBladeShieldAttackWithContext(null, null, true, false,
        1, 2, 128, 0, true, null, null, false, null).hit);
    assertFalse(host.calculatePrecomputedMeleeElementalAttackWithContext(null, null, true, false,
        1, 2, 10, null, null, 0, 0, 0, DAMAGE_PHYSICAL,
        null, null, false, null).hit);
    assertFalse(host.calculateLightningStrikeAttackWithContext(null, null, true, false,
        1, 2, 10, 0, DAMAGE_PHYSICAL, null, null, false, null, false, false, true).hit);
    assertEquals(seed, rng.state());
  }

  private static CombatResult invoke(CombatSystem system, int family,
      CombatSnapshotFactory.Request r, int scale, int toHit, int damageBonus) {
    switch (family) {
      case 0:
        return system.calculatePrecomputedMeleeElementalAttackWithContext(r.attacker, r.defender,
            r.attackerPlayer, r.defenderPlayer, r.attackMinDamageOverride, r.attackMaxDamageOverride,
            r.attackRatingOverride, r.elementalMinOverride, r.elementalMaxOverride,
            r.coldLengthOverride, r.poisonLengthOverride, r.physicalConversionPercent,
            r.physicalConversionType, r.attackerStates, r.defenderStates, r.defenderMoving, r.context);
      case 1:
        return system.calculateLightningStrikeAttackWithContext(r.attacker, r.defender,
            r.attackerPlayer, r.defenderPlayer, r.attackMinDamageOverride, r.attackMaxDamageOverride,
            damageBonus, r.physicalConversionPercent, r.physicalConversionType, r.attackerStates,
            r.defenderStates, r.defenderMoving, r.context, r.defenderDemon, r.defenderUndead,
            r.defenderIgnoreTargetDefenseAllowed);
      case 2:
        return system.calculateBladeShieldAttackWithContext(r.attacker, r.defender,
            r.attackerPlayer, r.defenderPlayer, r.attackMinDamageOverride, r.attackMaxDamageOverride,
            scale, toHit, r.alwaysHit, r.attackerStates, r.defenderStates, r.defenderMoving, r.context);
      case 3:
        return system.calculatePrecomputedMeleeAttackWithContext(r.attacker, r.defender,
            r.attackerPlayer, r.defenderPlayer, r.attackMinDamageOverride, r.attackMaxDamageOverride,
            r.attackRatingOverride, r.physicalConversionPercent, r.physicalConversionType,
            r.attackerStates, r.defenderStates, r.defenderMoving, r.mastery, r.context);
      default:
        if (system instanceof PreviousSpecialAttackSystem) {
          return ((PreviousSpecialAttackSystem) system).legacyGeneral(r);
        }
        return system.calculateAttackAtDifficulty(r.attacker, r.defender, r.attackerPlayer,
            r.defenderPlayer, r.missile, r.attackMinDamageOverride, r.attackMaxDamageOverride,
            r.attackRatingOverride, r.alwaysHit, r.elementalMinOverride, r.elementalMaxOverride,
            r.coldLengthOverride, r.poisonLengthOverride, r.attackerStates, r.defenderStates,
            r.defenderMoving, r.mastery, r.context, r.magicTargetBonusPercent, r.defenderDemon,
            r.defenderUndead, r.defenderIgnoreTargetDefenseAllowed, r.fixedPointElements);
    }
  }

  private static void compareFields(Class<?> type, Object expected, Object actual, String label)
      throws IllegalAccessException {
    for (Field field : type.getFields()) {
      Object left = field.get(expected), right = field.get(actual);
      if (field.getType() == int[].class) assertArrayEquals((int[]) left, (int[]) right, label + " " + field.getName());
      else if (field.getType() == boolean[].class) assertArrayEquals((boolean[]) left, (boolean[]) right, label + " " + field.getName());
      else assertEquals(left, right, label + " " + field.getName());
    }
  }

  private static int[] range(Random r) {
    int[] values = new int[r.nextInt(DAMAGE_TYPE_COUNT + 1)];
    for (int i = 0; i < values.length; i++) values[i] = r.nextInt(120) - 20;
    return values;
  }

  private static Attributes attributes(Random r, boolean attacker) {
    Attributes attrs = Attributes.obtainLarge();
    attrs.base().clear();
    short[] stats = attacker ? new short[] {Stat.strength, Stat.dexterity, Stat.tohit,
        Stat.item_tohit_percent, Stat.item_tohit_percent_vs_monster, Stat.damagepercent,
        Stat.item_demon_tohit, Stat.item_undead_tohit, Stat.item_demondamage_percent,
        Stat.item_undeaddamage_percent, Stat.item_deadlystrike, Stat.passive_critical_strike,
        Stat.item_crushingblow, Stat.lifedrainmindam, Stat.manadrainmindam, Stat.coldlength,
        Stat.poisonlength, Stat.item_throw_mindamage, Stat.item_throw_maxdamage,
        Stat.passive_cold_pierce, Stat.item_pierce_ltng}
        : new short[] {Stat.dexterity, Stat.armorclass, Stat.armorclass_vs_missile,
        Stat.armorclass_vs_hth, Stat.toblock, Stat.item_fasterblockrate, Stat.passive_dodge,
        Stat.passive_avoid, Stat.passive_evade, Stat.passive_weaponblock, Stat.damageresist,
        Stat.fireresist, Stat.lightresist, Stat.coldresist, Stat.poisonresist, Stat.magicresist,
        Stat.maxfireresist, Stat.maxcoldresist, Stat.maxlightresist, Stat.magic_damage_reduction,
        Stat.item_absorbfire_percent, Stat.item_absorbfire, Stat.item_absorblight_percent};
    for (short stat : stats) if (r.nextInt(4) != 0) attrs.base().put(stat, r.nextInt(161) - 20);
    attrs.base().put(Stat.level, r.nextInt(100) + 1);
    attrs.base().put(Stat.hitpoints, 1000);
    attrs.base().put(Stat.maxhp, 1000);
    if (r.nextBoolean()) attrs.base().put(Stat.mindamage, r.nextInt(100));
    if (r.nextBoolean()) attrs.base().put(Stat.maxdamage, r.nextInt(150));
    for (int type = DAMAGE_FIRE; type < DAMAGE_TYPE_COUNT; type++) {
      if (r.nextBoolean()) attrs.base().put(MIN[type], r.nextInt(100) - 20);
      if (r.nextBoolean()) attrs.base().put(MAX[type], r.nextInt(100) - 20);
    }
    attrs.base().put(Stat.item_ignoretargetac, r.nextBoolean() ? 1 : 0);
    attrs.base().put(Stat.skill_bypass_undead, r.nextBoolean() ? 1 : 0);
    attrs.base().put(Stat.item_cannotbefrozen, r.nextBoolean() ? 1 : 0);
    attrs.base().put(Stat.item_halffreezeduration, r.nextBoolean() ? 1 : 0);
    attrs.reset();
    attrs.setDifficultyResistanceIncluded(r.nextBoolean());
    if (r.nextBoolean()) attrs.setCombatBlockContext(r.nextBoolean(), r.nextInt(40));
    if (r.nextBoolean()) attrs.setCombatRunning(r.nextBoolean());
    return attrs;
  }

  private static StateList states(Random r) {
    if (r.nextInt(3) == 0) return null;
    StateList states = new StateList(1);
    UnitState overlay = states.addState(StateId.CONVICTION, 0);
    short[] stats = {Stat.damagepercent, Stat.item_tohit_percent, Stat.armorclass, Stat.toblock,
        Stat.passive_dodge, Stat.damageresist, Stat.fireresist, Stat.maxcoldresist,
        Stat.item_demondamage_percent, Stat.passive_critical_strike, Stat.lifedrainmindam};
    for (short stat : stats) overlay.setNativeModifier(stat, r.nextInt(41) - 20);
    for (int type = DAMAGE_FIRE; type < DAMAGE_TYPE_COUNT; type++) {
      overlay.setNativeModifier(MIN[type], r.nextInt(21) - 10);
      overlay.setNativeModifier(MAX[type], r.nextInt(21) - 10);
    }
    if (r.nextBoolean()) {
      UnitState venom = states.addState(StateId.VENOMCLAWS, 0);
      venom.poisonMinDamage = r.nextInt(41) - 10;
      venom.poisonMaxDamage = r.nextInt(51) - 10;
      venom.poisonLengthOverride = r.nextInt(30) - 5;
    }
    return states;
  }
}
