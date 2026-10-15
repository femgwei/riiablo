package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.event.DamageEvent;
import org.junit.jupiter.api.Test;

/** Nova 8.8 lightning packet contracts against SUNITDMG mitigation ordering. */
class NovaFixedPointDamageTest extends RiiabloTest {
  private static final int LIGHTNING = CombatSystem.DAMAGE_LIGHTNING;
  private final CombatSystem combat = new CombatSystem();

  private static CombatSystem.AttackerData attacker(int damage) {
    CombatSystem.AttackerData a = new CombatSystem.AttackerData();
    a.alwaysHit = true;
    a.isMissile = true;
    a.fixedPointElements = true;
    a.elementalMinDamage[LIGHTNING] = damage;
    a.elementalMaxDamage[LIGHTNING] = damage;
    return a;
  }

  @Test void minimumRollRetainsHalfHpAndEventChannel() {
    CombatSystem.DefenderData d = new CombatSystem.DefenderData();
    d.resistances[LIGHTNING] = 50;
    CombatSystem.CombatResult result = combat.calculateAttack(attacker(1), d);
    assertTrue(result.hit);
    assertEquals(128, result.elementalDamage[LIGHTNING]);
    assertEquals(0.5f, result.totalDamageHp());
    DamageEvent event = DamageEvent.obtainMissile(1, 2,
        result.totalDamageHp(), 0, null, result).withElementalDamage(result, 1f);
    assertEquals(0.5f, event.damage);
    assertEquals(0.5f, event.lightningDamage);
  }

  @Test void fractionalAbsorbAndFlatAbsorbUseTheSameUnits() {
    CombatSystem.DefenderData d = new CombatSystem.DefenderData();
    d.resistances[LIGHTNING] = 50;
    d.absorbPercent[LIGHTNING] = 25;
    d.absorbFlat[LIGHTNING] = 1;
    CombatSystem.CombatResult result = combat.calculateAttack(attacker(3), d);
    assertEquals(0.125f, result.totalDamageHp());
    assertEquals(1.375f, result.absorbedLifeHp());
  }

  @Test void flatReductionRemainsInHpAndImmunityRemainsAbsolute() {
    CombatSystem.DefenderData d = new CombatSystem.DefenderData();
    d.resistances[LIGHTNING] = 50;
    d.magicDamageReduced = 1;
    assertEquals(1f, combat.calculateAttack(attacker(3), d).totalDamageHp());
    d.immuneElemental[LIGHTNING] = true;
    assertEquals(0f, combat.calculateAttack(attacker(3), d).totalDamageHp());
  }

  @Test void pvpPreservesFixedPointRemainder() {
    CombatSystem.AttackerData a = attacker(1);
    a.isPlayer = true;
    CombatSystem.DefenderData d = new CombatSystem.DefenderData();
    d.isPlayer = true;
    d.resistances[LIGHTNING] = 50;
    assertEquals(21f / 256f, combat.calculateAttack(a, d).totalDamageHp());
  }

  @Test void optOutRetainsLegacyIntegerContractAndResetClearsScale() {
    CombatSystem.AttackerData a = attacker(1);
    a.fixedPointElements = false;
    CombatSystem.DefenderData d = new CombatSystem.DefenderData();
    d.resistances[LIGHTNING] = 50;
    CombatSystem.CombatResult result = combat.calculateAttack(a, d);
    assertEquals(1, result.elementalScale);
    assertEquals(0f, result.totalDamageHp());
    result = combat.calculateAttack(attacker(1), d);
    result.reset();
    assertEquals(1, result.elementalScale);
    assertEquals(0f, result.totalDamageHp());
    assertEquals(0f, result.absorbedLifeHp());
  }

  @Test void pvpPrecedesMdrResistanceAndAbsorb() {
    CombatSystem.AttackerData a = attacker(10);
    a.isPlayer = true;
    CombatSystem.DefenderData d = new CombatSystem.DefenderData();
    d.isPlayer = true;
    d.resistances[LIGHTNING] = 50;
    d.magicDamageReduced = 1;
    d.absorbPercent[LIGHTNING] = 25;
    CombatSystem.CombatResult result = combat.calculateAttack(a, d);
    // 2560 * 17/100 = 435; -256 = 179; *50/100 = 89; absorb 22.
    assertEquals(67f / 256f, result.totalDamageHp());
    assertEquals(22f / 256f, result.absorbedLifeHp());
  }

  @Test void monsterResistanceIsNotClampedToThePlayerCap() {
    CombatSystem.DefenderData d = new CombatSystem.DefenderData();
    d.isMonster = true;
    d.resistances[LIGHTNING] = 90;
    assertEquals(1f, combat.calculateAttack(attacker(10), d).totalDamageHp());
    d.resistances[LIGHTNING] = 100;
    CombatSystem.AttackerData a = attacker(10);
    a.elementalPierce[LIGHTNING] = 200;
    assertEquals(0f, combat.calculateAttack(a, d).totalDamageHp());
  }

  @Test void absorbPercentCapsAtFortyBeforeFlatAbsorb() {
    CombatSystem.DefenderData d = new CombatSystem.DefenderData();
    d.absorbPercent[LIGHTNING] = 80;
    d.absorbFlat[LIGHTNING] = 1;
    CombatSystem.CombatResult result = combat.calculateAttack(attacker(10), d);
    assertEquals(5f, result.totalDamageHp());
    assertEquals(5f, result.absorbedLifeHp());
  }

  @Test void playerMaxResistanceCapsAtNinetyFiveAndNegativeResistanceAtMinusHundred() {
    CombatSystem.DefenderData d = new CombatSystem.DefenderData();
    d.isPlayer = true;
    d.maxResistances[LIGHTNING] = 125;
    d.resistances[LIGHTNING] = 200;
    assertEquals(0.5f, combat.calculateAttack(attacker(10), d).totalDamageHp());
    d.resistances[LIGHTNING] = -200;
    assertEquals(20f, combat.calculateAttack(attacker(10), d).totalDamageHp());
  }

  @Test void pierceAboveHundredIsNotPrematurelyClamped() {
    CombatSystem.DefenderData d = new CombatSystem.DefenderData();
    d.isMonster = true;
    d.resistances[LIGHTNING] = 90;
    CombatSystem.AttackerData a = attacker(10);
    a.elementalPierce[LIGHTNING] = 150;
    assertEquals(16f, combat.calculateAttack(a, d).totalDamageHp());
  }
}
