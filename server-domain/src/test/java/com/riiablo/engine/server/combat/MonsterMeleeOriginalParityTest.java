package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Independent, source-derived expectations, NOT captured 1.10f executable outputs.
 * See docs/monster-combat-parity.md for source revision and hand calculations.
 * Intentionally opt-in: failing native expectations must not become legacy snapshots.
 */
@Tag("monster-parity-audit")
class MonsterMeleeOriginalParityTest {
  @ParameterizedTest(name = "{0}: native chance={5}")
  @CsvSource({
      "equal-level, 100, 200, 10, 10, 33",
      "higher-attacker, 100, 200, 20, 10, 44",
      "upper-cap, 100, 0, 10, 10, 95",
      "lower-cap, 1, 9999, 1, 99, 5",
      "truncate-rating-before-level, 2, 1, 20, 10, 88",
      "level-penalty, 100, 0, 10, 90, 20",
      "zero-divisor-native-factor-100, 0, 0, 10, 10, 95",
      "zero-divisor-still-level-scaled, 0, 0, 10, 90, 20"
  })
  void hitChanceAndExactRollBoundary(String id, int rating, int defense,
      int attackerLevel, int defenderLevel, int expected) {
    // SUNITDMG_IsHitSuccessful: factor defaults to 100 if AR + defense == 0;
    // otherwise truncate 100*AR/(AR+defense), THEN apply the level ratio.
    NativeAttackData attacker = new NativeAttackData();
    attacker.level = attackerLevel;
    attacker.attackRating = rating;
    NativeDefenseData target = new NativeDefenseData();
    target.isPlayer = true;
    target.level = defenderLevel;
    target.defense = defense;
    assertEquals(expected, NativeAttackResolver.calculateHitChance(attacker, target), id);
    assertEquals(true, NativeAttackResolver.calculateHit(attacker, target, expected - 1).hit, id);
    assertEquals(false, NativeAttackResolver.calculateHit(attacker, target, expected).hit, id);
  }

  @ParameterizedTest(name = "{0}: native physical HP={4}")
  @CsvSource({
      "unmitigated, 0, 0, 0, 100",
      "flat-before-percent, 0, 50, 10, 45",
      "fully-absorbed-is-zero, 0, 0, 100, 0",
      "player-physical-resistance-cap, 0, 75, 0, 50",
      "negative-resistance-after-flat, 0, -50, 10, 135",
      "strength-and-enhanced-share-percent, 50, 0, 0, 200"
  })
  void normalMonsterMeleeMitigation(String id, int bonus, int resistance, int flat, int expected) {
    NativeAttackData attacker = monster(100, 100);
    attacker.strength = bonus;
    attacker.enhancedDamagePercent = bonus;
    NativeDefenseData target = player(resistance, flat);
    // Lower endpoint isolates mitigation from the native fractional damage roll.
    NativeDamagePacket actual = new NativeAttackResolver(new NativeCombatRolls(bound -> 0))
        .resolve(attacker, target);
    assertEquals(expected, actual.totalDamageHp(), 0f, id);
  }

  @ParameterizedTest(name = "{0}: native 8.8 endpoint={3}")
  @CsvSource({
      "minimum, 10, 11, 2560, false",
      "maximum-is-exclusive-subhp, 10, 11, 2815, true",
      "equal-ends-expand-by-one-hp, 10, 10, 2815, true"
  })
  void physicalRollPreservesNativeFractionalEndpoint(String id, int min, int max,
      int expectedFixed, boolean upper) {
    NativeAttackData attacker = monster(min, max);
    attacker.alwaysHit = true; // isolates damage RNG from the separate hit draw
    NativeDamagePacket actual = new NativeAttackResolver(
        new NativeCombatRolls(bound -> upper ? bound - 1 : 0)).resolve(attacker, player(0, 0));
    // SUNITDMG_ApplyDamageBonuses: shift by 8, expand max if <= min,
    // then draw [0, max-min). Packet must retain that fraction through life application.
    assertEquals(expectedFixed / 256f, actual.totalDamageHp(), 0f, id);
  }

  private static NativeAttackData monster(int min, int max) {
    NativeAttackData attacker = new NativeAttackData();
    attacker.level = 10;
    attacker.attackRating = 100;
    attacker.minDamage = min;
    attacker.maxDamage = max;
    return attacker;
  }

  private static NativeDefenseData player(int resistance, int flat) {
    NativeDefenseData target = new NativeDefenseData();
    target.isPlayer = true;
    target.level = 10;
    target.currentLife = target.maxLife = 1000;
    target.damageReducedPercent = resistance;
    target.damageReduced = flat;
    return target;
  }
}
