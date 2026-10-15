package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.event.DamageEvent;
import org.junit.jupiter.api.Test;

/** Precision contracts; mitigation ordering is still the legacy attack path. */
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
    assertEquals(0.5f, combat.calculateAttack(attacker(3), d).totalDamageHp());
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
}
