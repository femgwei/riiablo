package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class CombatSystemTest {
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

    assertEquals(54, combat.calculateBlockChance(defender));
  }
}
