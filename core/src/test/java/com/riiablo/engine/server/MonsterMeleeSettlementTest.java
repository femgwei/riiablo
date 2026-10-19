package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.engine.server.combat.CombatSystem;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Host snapshot adapter regression; no MPQs, animation, AI or global RNG.
 * Fixed whole-HP damage here is a compatibility assertion, NOT native parity.
 */
class MonsterMeleeSettlementTest {
  @ParameterizedTest(name = "seed={0}, hit={1}, damage={2}")
  @CsvSource({"3, true, 20", "28, false, 0"})
  void hitThresholdControlsTheWholeDamagePacket(int seed, boolean expectedHit, int expectedDamage) {
    CombatSystem.CombatResult result = new CombatSystem(new NativeRng(seed))
        .calculateAttack(monster(), player());
    // Independent literals: native AR=100, defense=200, levels=10/10 => 33%.
    // Current LCG's first draws for seeds 3 and 28 are 32 and 33 respectively.
    assertEquals(33, result.hitChance);
    assertEquals(expectedHit ? 32 : 33, result.hitRoll);
    assertEquals(expectedHit, result.hit);
    assertFalse(result.blocked);
    assertEquals(expectedDamage, result.physicalDamage);
    assertEquals(expectedDamage, result.totalDamageHp(), 0f);
    assertFalse(result.critical);
    assertFalse(result.deadlyStrike);
    assertEquals(0, result.lifeStolen);
    for (int element : result.elementalDamage) assertEquals(0, element);
  }

  @ParameterizedTest(name = "seed={0}, shield block={1}")
  @CsvSource({"1068, true", "1093, false"})
  void shieldBlockUsesItsOwnRollAfterSuccessfulHit(int seed, boolean expectedBlock) {
    CombatSystem.DefenderData target = player();
    target.canBlock = true;
    target.hasEquippedShield = true;
    target.blockChance = 75;
    target.dexterity = 35; // 75 * (35 - 15) / (2 * 10) = 75
    CombatSystem.CombatResult result = new CombatSystem(new NativeRng(seed))
        .calculateAttack(monster(), target);
    // First rolls 1/2 hit; second rolls 74/75 straddle the 75% block threshold.
    assertTrue(result.hit);
    assertEquals(expectedBlock, result.blocked);
    assertEquals(expectedBlock ? 0 : 20, result.totalDamageHp(), 0f);
  }

  private static CombatSystem.AttackerData monster() {
    CombatSystem.AttackerData attacker = new CombatSystem.AttackerData();
    attacker.level = 10;
    attacker.attackRating = 100;
    attacker.minDamage = attacker.maxDamage = 20;
    return attacker;
  }

  private static CombatSystem.DefenderData player() {
    CombatSystem.DefenderData target = new CombatSystem.DefenderData();
    target.isPlayer = true;
    target.level = 10;
    target.defense = 200;
    target.currentLife = target.maxLife = 1000;
    return target;
  }
}
