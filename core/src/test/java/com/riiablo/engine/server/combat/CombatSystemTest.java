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

    assertEquals(54, combat.calculateBlockChance(defender));
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
