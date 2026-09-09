package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import org.junit.jupiter.api.Test;

public class CombatSystemTest extends RiiabloTest {
  private final CombatSystem combat = new CombatSystem();

  @Test
  public void calculatesPvmHitChanceWithLevelFactor() {
    CombatSystem.AttackerData attacker = new CombatSystem.AttackerData();
    attacker.level = 10;
    attacker.attackRating = 100;

    CombatSystem.DefenderData defender = new CombatSystem.DefenderData();
    defender.level = 10;
    defender.defense = 100;

    assertEquals(50, combat.calculateHitChance(attacker, defender));
  }

  @Test
  public void clampsHitChanceToNativeBounds() {
    CombatSystem.AttackerData attacker = new CombatSystem.AttackerData();
    attacker.level = 1;
    attacker.attackRating = 0;

    CombatSystem.DefenderData defender = new CombatSystem.DefenderData();
    defender.level = 99;
    defender.defense = 10000;

    assertEquals(CombatSystem.MIN_TO_HIT_CHANCE, combat.calculateHitChance(attacker, defender));

    attacker.attackRating = Integer.MAX_VALUE;
    defender.defense = 0;
    assertTrue(combat.calculateHitChance(attacker, defender) <= CombatSystem.MAX_TO_HIT_CHANCE);
  }

  @Test
  public void calculatesBlockChanceFromDexterityAndLevel() {
    CombatSystem.DefenderData defender = new CombatSystem.DefenderData();
    defender.level = 10;
    defender.dexterity = 35;
    defender.blockChance = 50;
    defender.canBlock = true;
    defender.isPlayer = true;

    assertEquals(50, combat.calculateBlockChance(defender));

    defender.dexterity = 15;
    assertEquals(0, combat.calculateBlockChance(defender));

    defender.isPlayer = false;
    assertEquals(50, combat.calculateBlockChance(defender));
  }

  @Test
  public void shieldBlockAcceptsPhysicalMissilesButNotElementalOnlyMissiles() {
    CombatSystem deterministicBlock = new CombatSystem() {
      @Override
      protected boolean rollShieldBlock(int blockChance) {
        return blockChance > 0;
      }
    };
    CombatSystem.AttackerData physicalArrow = new CombatSystem.AttackerData();
    physicalArrow.alwaysHit = true;
    physicalArrow.isMissile = true;
    physicalArrow.level = 1;
    physicalArrow.minDamage = 10;
    physicalArrow.maxDamage = 10;

    CombatSystem.DefenderData defender = new CombatSystem.DefenderData();
    defender.level = 1;
    defender.canBlock = true;
    defender.blockChance = 50;
    assertTrue(deterministicBlock.calculateAttack(physicalArrow, defender).blocked);
    assertTrue(CombatSystem.hasBlockablePhysicalDamage(physicalArrow));

    CombatSystem.AttackerData elementalSpell = new CombatSystem.AttackerData();
    elementalSpell.alwaysHit = true;
    elementalSpell.isMissile = true;
    elementalSpell.level = 1;
    elementalSpell.elementalMinDamage[CombatSystem.DAMAGE_FIRE] = 10;
    elementalSpell.elementalMaxDamage[CombatSystem.DAMAGE_FIRE] = 10;
    CombatSystem.CombatResult result = deterministicBlock.calculateAttack(elementalSpell, defender);
    assertTrue(result.hit);
    assertTrue(!result.blocked);
    assertTrue(!CombatSystem.hasBlockablePhysicalDamage(elementalSpell));
  }

  @Test
  public void playerFallbackAttackRatingAvoidsArtificialFivePercentFloor() {
    CombatSystem.AttackerData attacker = new CombatSystem.AttackerData();
    attacker.isPlayer = true;
    attacker.level = 1;
    attacker.dexterity = 25;
    attacker.attackRating = 25 * 5 + 2;

    CombatSystem.DefenderData defender = new CombatSystem.DefenderData();
    defender.level = 1;
    defender.defense = 20;

    assertTrue(combat.calculateHitChance(attacker, defender) > CombatSystem.MIN_TO_HIT_CHANCE);
  }

  @Test
  public void monsterAttackingPlayerDoesNotReceiveAnExtraPvpHitFactor() {
    CombatSystem.AttackerData attacker = new CombatSystem.AttackerData();
    attacker.level = 1;
    attacker.attackRating = 100;

    CombatSystem.DefenderData player = new CombatSystem.DefenderData();
    player.isPlayer = true;
    player.level = 1;
    player.defense = 100;

    assertEquals(50, combat.calculateHitChance(attacker, player));
  }

  @Test
  public void nativeHitFormulaIncludesAttackerAndDefenderLevels() {
    CombatSystem.AttackerData attacker = new CombatSystem.AttackerData();
    attacker.level = 1;
    attacker.attackRating = 100;

    CombatSystem.DefenderData defender = new CombatSystem.DefenderData();
    defender.level = 2;
    defender.defense = 100;

    assertEquals(33, combat.calculateHitChance(attacker, defender));
  }

  @Test
  public void runtimeMightStateIncreasesAuthoritativePhysicalDamage() {
    Attributes attacker = attrs(100, 1, 0, 10, 10, 1000);
    Attributes defender = attrs(100, 1, 0, 1, 1, 1);
    CombatSystem.CombatResult base = combat.calculateAttack(attacker, defender,
        true, false, false, 10, 10, 1000, true, null, null, 0, 0, null, null);

    StateList states = new StateList(1);
    states.addState(StateId.MIGHT, 0);
    states.getState(StateId.MIGHT).damageModifier = 40;
    CombatSystem.CombatResult buffed = combat.calculateAttack(attacker, defender,
        true, false, false, 10, 10, 1000, true, null, null, 0, 0, states, null);

    assertEquals(10, base.totalDamage);
    assertEquals(14, buffed.totalDamage);
    System.out.println("[AURA_COMBAT] aura=MIGHT base=10 buffed=14 status=PASS");
  }

  @Test
  public void runtimeResistFireStateReducesElementalDamage() {
    Attributes attacker = attrs(100, 1, 0, 1, 1, 1000);
    Attributes defender = attrs(100, 1, 0, 1, 1, 1);
    int[] fireMin = new int[CombatSystem.DAMAGE_TYPE_COUNT];
    int[] fireMax = new int[CombatSystem.DAMAGE_TYPE_COUNT];
    fireMin[CombatSystem.DAMAGE_FIRE] = 20;
    fireMax[CombatSystem.DAMAGE_FIRE] = 20;
    CombatSystem.CombatResult base = combat.calculateAttack(attacker, defender,
        true, false, false, 1, 1, 1000, true, fireMin, fireMax, 0, 0, null, null);

    StateList states = new StateList(2);
    states.addState(StateId.RESISTFIRE, 0);
    states.getState(StateId.RESISTFIRE).fireResistModifier = 30;
    CombatSystem.CombatResult resisted = combat.calculateAttack(attacker, defender,
        true, false, false, 1, 1, 1000, true, fireMin, fireMax, 0, 0, null, states);

    assertEquals(20, base.elementalDamage[CombatSystem.DAMAGE_FIRE]);
    assertEquals(14, resisted.elementalDamage[CombatSystem.DAMAGE_FIRE]);
    System.out.println("[AURA_COMBAT] aura=RESIST_FIRE baseFire=20 resistedFire=14 status=PASS");
  }

  @Test
  public void runtimeDefianceStateChangesHitChance() {
    Attributes attacker = attrs(100, 1, 0, 1, 1, 100);
    Attributes defender = attrs(100, 1, 100, 1, 1, 100);
    int base = combat.calculateAttack(attacker, defender, true, false, false,
        1, 1, 100, false, null, null, 0, 0, null, null).hitChance;
    StateList states = new StateList(2);
    states.addState(StateId.DEFIANCE, 0);
    states.getState(StateId.DEFIANCE).defenseModifier = 70;
    int buffed = combat.calculateAttack(attacker, defender, true, false, false,
        1, 1, 100, false, null, null, 0, 0, null, states).hitChance;
    assertTrue(buffed < base);
    System.out.println("[AURA_COMBAT] aura=DEFIANCE hitChance=" + base + "->" + buffed
        + " status=PASS");
  }

  @Test
  public void nativeMaximumResistanceCapIsApplied() {
    Attributes attacker = attrs(100, 1, 0, 1, 1, 1000);
    Attributes defender = attrs(100, 1, 0, 1, 1, 1);
    defender.base().put(Stat.fireresist, 98);
    defender.base().put(Stat.maxfireresist, 20);
    defender.reset();
    int[] fireMin = new int[CombatSystem.DAMAGE_TYPE_COUNT];
    int[] fireMax = new int[CombatSystem.DAMAGE_TYPE_COUNT];
    fireMin[CombatSystem.DAMAGE_FIRE] = 100;
    fireMax[CombatSystem.DAMAGE_FIRE] = 100;

    CombatSystem.CombatResult result = combat.calculateAttack(attacker, defender,
        true, false, false, 1, 1, 1000, true, fireMin, fireMax, 0, 0, null, null);
    assertEquals(5, result.elementalDamage[CombatSystem.DAMAGE_FIRE]);
  }

  @Test
  public void nativeElementalPierceReducesTargetResistance() {
    Attributes attacker = attrs(100, 1, 0, 1, 1, 1000);
    attacker.base().put(Stat.item_pierce_fire, 20);
    attacker.reset();
    Attributes defender = attrs(100, 1, 0, 1, 1, 1);
    defender.base().put(Stat.fireresist, 75);
    defender.reset();
    int[] fireMin = new int[CombatSystem.DAMAGE_TYPE_COUNT];
    int[] fireMax = new int[CombatSystem.DAMAGE_TYPE_COUNT];
    fireMin[CombatSystem.DAMAGE_FIRE] = 100;
    fireMax[CombatSystem.DAMAGE_FIRE] = 100;

    CombatSystem.CombatResult result = combat.calculateAttack(attacker, defender,
        true, false, false, 1, 1, 1000, true, fireMin, fireMax, 0, 0, null, null);
    assertEquals(45, result.elementalDamage[CombatSystem.DAMAGE_FIRE]);
  }

  @Test
  public void playerVsPlayerDamageUsesNativeSeventeenPercentScalar() {
    Attributes attacker = attrs(100, 1, 0, 100, 100, 1000);
    Attributes defender = attrs(100, 1, 0, 1, 1, 1);
    CombatSystem.CombatResult result = combat.calculateAttack(attacker, defender,
        true, true, false, 100, 100, 1000, true, null, null, 0, 0, null, null);
    assertEquals(17, result.totalDamage);
  }

  @Test
  public void nativeResistanceIsClampedToNegativeHundred() {
    Attributes attacker = attrs(100, 1, 0, 1, 1, 1000);
    Attributes defender = attrs(100, 1, 0, 1, 1, 1);
    defender.base().put(Stat.fireresist, -150);
    defender.reset();
    int[] fireMin = new int[CombatSystem.DAMAGE_TYPE_COUNT];
    int[] fireMax = new int[CombatSystem.DAMAGE_TYPE_COUNT];
    fireMin[CombatSystem.DAMAGE_FIRE] = 100;
    fireMax[CombatSystem.DAMAGE_FIRE] = 100;

    CombatSystem.CombatResult result = combat.calculateAttack(attacker, defender,
        true, false, false, 1, 1, 1000, true, fireMin, fireMax, 0, 0, null, null);
    assertEquals(200, result.elementalDamage[CombatSystem.DAMAGE_FIRE]);
  }

  @Test
  public void playerDifficultyResistancePenaltyMatchesNativeValues() {
    Attributes attacker = attrs(100, 1, 0, 1, 1, 1000);
    Attributes defender = attrs(100, 1, 0, 1, 1, 1);
    int[] fireMin = new int[CombatSystem.DAMAGE_TYPE_COUNT];
    int[] fireMax = new int[CombatSystem.DAMAGE_TYPE_COUNT];
    fireMin[CombatSystem.DAMAGE_FIRE] = 100;
    fireMax[CombatSystem.DAMAGE_FIRE] = 100;

    CombatSystem.CombatResult normal = combat.calculateAttackAtDifficulty(
        attacker, defender, false, true, false, 1, 1, 1000, true,
        fireMin, fireMax, 0, 0, null, null, false, null, 0);
    CombatSystem.CombatResult nightmare = combat.calculateAttackAtDifficulty(
        attacker, defender, false, true, false, 1, 1, 1000, true,
        fireMin, fireMax, 0, 0, null, null, false, null, 1);
    CombatSystem.CombatResult hell = combat.calculateAttackAtDifficulty(
        attacker, defender, false, true, false, 1, 1, 1000, true,
        fireMin, fireMax, 0, 0, null, null, false, null, 2);

    assertEquals(100, normal.elementalDamage[CombatSystem.DAMAGE_FIRE]);
    assertEquals(140, nightmare.elementalDamage[CombatSystem.DAMAGE_FIRE]);
    assertEquals(200, hell.elementalDamage[CombatSystem.DAMAGE_FIRE]);
  }

  @Test
  public void difficultyResistancePenaltyDoesNotApplyToMonsterTargets() {
    Attributes attacker = attrs(100, 1, 0, 1, 1, 1000);
    Attributes defender = attrs(100, 1, 0, 1, 1, 1);
    int[] fireMin = new int[CombatSystem.DAMAGE_TYPE_COUNT];
    int[] fireMax = new int[CombatSystem.DAMAGE_TYPE_COUNT];
    fireMin[CombatSystem.DAMAGE_FIRE] = 100;
    fireMax[CombatSystem.DAMAGE_FIRE] = 100;

    CombatSystem.CombatResult result = combat.calculateAttackAtDifficulty(
        attacker, defender, false, false, false, 1, 1, 1000, true,
        fireMin, fireMax, 0, 0, null, null, false, null, 2);
    assertEquals(100, result.elementalDamage[CombatSystem.DAMAGE_FIRE]);
  }

  @Test
  public void elementalAbsorbAppliesPercentThenFlatAndRecordsLife() {
    Attributes attacker = attrs(100, 1, 0, 1, 1, 1000);
    Attributes defender = attrs(50, 1, 0, 1, 1, 1);
    defender.base().put(Stat.item_absorbfire_percent, 50);
    defender.base().put(Stat.item_absorbfire, 10);
    defender.reset();
    int[] fireMin = new int[CombatSystem.DAMAGE_TYPE_COUNT];
    int[] fireMax = new int[CombatSystem.DAMAGE_TYPE_COUNT];
    fireMin[CombatSystem.DAMAGE_FIRE] = 100;
    fireMax[CombatSystem.DAMAGE_FIRE] = 100;

    CombatSystem.CombatResult result = combat.calculateAttack(
        attacker, defender, false, false, false, 1, 1, 1000, true,
        fireMin, fireMax, 0, 0, null, null);
    assertEquals(40, result.elementalDamage[CombatSystem.DAMAGE_FIRE]);
    assertEquals(60, result.absorbedLife);
    assertTrue(result.absorbedLife <= 100);
  }

  @Test
  public void elementalAbsorbNeverProducesNegativeDamage() {
    Attributes attacker = attrs(100, 1, 0, 1, 1, 1000);
    Attributes defender = attrs(50, 1, 0, 1, 1, 1);
    defender.base().put(Stat.item_absorbfire_percent, 100);
    defender.base().put(Stat.item_absorbfire, 1000);
    defender.reset();
    int[] fireMin = new int[CombatSystem.DAMAGE_TYPE_COUNT];
    int[] fireMax = new int[CombatSystem.DAMAGE_TYPE_COUNT];
    fireMin[CombatSystem.DAMAGE_FIRE] = 1;
    fireMax[CombatSystem.DAMAGE_FIRE] = 1;

    CombatSystem.CombatResult result = combat.calculateAttack(
        attacker, defender, false, false, false, 1, 1, 1000, true,
        fireMin, fireMax, 0, 0, null, null);
    assertEquals(0, result.elementalDamage[CombatSystem.DAMAGE_FIRE]);
    assertEquals(1, result.absorbedLife);
  }

  @Test
  public void fixedElementalAreaDamageUsesSameAbsorbAndPvpChain() {
    Attributes defender = attrs(100, 1, 0, 1, 1, 1);
    defender.base().put(Stat.fireresist, 50);
    defender.base().put(Stat.item_absorbfire_percent, 20);
    defender.reset();

    CombatSystem.CombatResult result = combat.calculateFixedElementalDamage(
        defender, true, true, CombatSystem.DAMAGE_FIRE, 100, 0, null, 0);
    // 100 -> 50 after resistance -> 40 after absorb; PvP scalar is applied
    // after absorb, matching the regular attack resolver.
    assertEquals(6, result.elementalDamage[CombatSystem.DAMAGE_FIRE]);
    assertEquals(10, result.absorbedLife);
    assertEquals(6, result.totalDamage);
  }

  @Test
  public void fixedPoisonSeparatesRateAndLengthResistance() {
    Attributes attacker = attrs(100, 1, 0, 1, 1, 1);
    Attributes defender = attrs(100, 1, 0, 1, 1, 1);
    defender.base().put(Stat.poisonresist, 50);
    defender.base().put(Stat.item_poisonlengthresist, 25);
    defender.reset();

    CombatSystem.CombatResult result = combat.calculateFixedPoisonDamage(
        attacker, defender, true, false, 256, 100, null, 0);

    assertEquals(0.5f, result.poisonDamagePerFrame, 0.0001f);
    assertEquals(75, result.poisonDuration);
    assertEquals(100, result.poisonBaseDuration);
    assertEquals(0, result.totalDamage);
  }

  @Test
  public void fixedPoisonAppliesHellPenaltyToPlayerDamageAndLength() {
    Attributes attacker = attrs(100, 1, 0, 1, 1, 1);
    Attributes defender = attrs(100, 1, 0, 1, 1, 1);

    CombatSystem.CombatResult result = combat.calculateFixedPoisonDamage(
        attacker, defender, true, false, 256, 100, null, 2);

    assertEquals(2f, result.poisonDamagePerFrame, 0.0001f);
    assertEquals(200, result.poisonDuration);
  }

  @Test
  public void fixedPoisonPvpScalesRateButNotDuration() {
    Attributes attacker = attrs(100, 1, 0, 1, 1, 1);
    Attributes defender = attrs(100, 1, 0, 1, 1, 1);

    CombatSystem.CombatResult result = combat.calculateFixedPoisonDamage(
        attacker, defender, true, true, 256, 100, null, 0);

    assertEquals(43f / 256f, result.poisonDamagePerFrame, 0.0001f);
    assertEquals(100, result.poisonDuration);
  }

  @Test
  public void fixedPoisonSnapshotsMasteryAndPierceForSpreadTargets() {
    Attributes attacker = attrs(100, 1, 0, 1, 1, 1);
    attacker.base().put(Stat.passive_pois_mastery, 100);
    attacker.base().put(Stat.item_pierce_pois, 10);
    attacker.base().put(Stat.passive_pois_pierce, 15);
    attacker.reset();
    Attributes first = attrs(100, 1, 0, 1, 1, 1);
    first.base().put(Stat.poisonresist, 50);
    first.base().put(Stat.item_poisonlengthresist, 50);
    first.reset();
    Attributes spread = attrs(100, 1, 0, 1, 1, 1);
    spread.base().put(Stat.poisonresist, 75);
    spread.reset();

    CombatSystem.CombatResult initial = combat.calculateFixedPoisonDamage(
        attacker, first, false, true, 256, 100, null, 0);
    CombatSystem.CombatResult propagated = combat.calculateFixedPoisonDamageSnapshot(
        spread, false, true, initial.poisonRawDamageFixed,
        initial.poisonPiercePercent, 80, null, 0);

    assertEquals(512, initial.poisonRawDamageFixed);
    assertEquals(25, initial.poisonPiercePercent);
    assertEquals(1.5f, initial.poisonDamagePerFrame, 0.0001f);
    assertEquals(75, initial.poisonDuration);
    assertEquals(1f, propagated.poisonDamagePerFrame, 0.0001f);
    // The same 25% pierce is replayed against the new target's zero poison
    // length resistance, extending the remaining 80-frame infection to 100.
    assertEquals(100, propagated.poisonDuration);
  }

  @Test
  public void poisonShrineSuppressesDotWithoutDiscardingInfectionLifetime() {
    Attributes defender = attrs(100, 1, 0, 1, 1, 1);
    StateList states = new StateList(99);
    states.addState(StateId.SHRINE_RESIST_POISON, 100, 1, 99);

    CombatSystem.CombatResult result = combat.calculateFixedPoisonDamageSnapshot(
        defender, false, true, 256, 0, 100, states, 0);

    assertEquals(1f, result.poisonDamagePerFrame, 0.0001f);
    assertEquals(0, result.poisonDuration);
    assertEquals(100, result.poisonBaseDuration);
  }

  @Test
  public void convertsWeaponPoisonFromNativeEightEightRate() {
    assertEquals(0.5f, CombatSystem.fixed8RateToPerFrame(128), 0.0001f);
    assertEquals(1f, CombatSystem.fixed8RateToPerFrame(256), 0.0001f);
    assertEquals(0f, CombatSystem.fixed8RateToPerFrame(-1), 0.0001f);
  }

  @Test
  public void fixedPhysicalDamageAppliesResistanceFlatReductionAndPvp() {
    Attributes defender = attrs(100, 1, 0, 1, 1, 1);
    defender.base().put(Stat.damageresist, 25);
    defender.base().put(Stat.normal_damage_reduction, 5);
    defender.reset();

    CombatSystem.CombatResult pve = combat.calculateFixedPhysicalDamage(
        defender, true, false, 100, null);
    CombatSystem.CombatResult pvp = combat.calculateFixedPhysicalDamage(
        defender, true, true, 100, null);

    assertEquals(70, pve.physicalDamage);
    assertEquals(70 * CombatSystem.PVP_DAMAGE_PERCENT / 100, pvp.physicalDamage);
  }

  private static Attributes attrs(int hp, int level, int defense,
      int minDamage, int maxDamage, int attackRating) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.base().put(Stat.level, level);
    attrs.base().put(Stat.armorclass, defense);
    attrs.base().put(Stat.mindamage, minDamage);
    attrs.base().put(Stat.maxdamage, maxDamage);
    attrs.base().put(Stat.tohit, attackRating);
    attrs.reset();
    return attrs;
  }
}
