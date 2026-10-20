package com.riiablo.engine.server.combat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Literal SUNITDMG/MISSILE expectations, independent of host attribute adapters. */
class NativeOrdinaryWeaponTest {
  @Test void meleeKeepsEndpointFractionsAndExcludesMaximum() {
    NativeAttackData a = weapon(1, 2, 10);
    List<Integer> bounds = new ArrayList<>();
    int damage = NativePhysicalDamageMath.roll(a, new NativeDefenseData(), new NativeCombatRolls(n -> {
      bounds.add(n); return n - 1;
    }));
    assertEquals(List.of(282), bounds); // (512+51)-(256+25)
    assertEquals(562, damage);
  }

  @Test void meleeMinimumAndMaximumEDAreSeparateAndCommonBonusAloneIsCapped() {
    NativeAttackData a = weapon(10, 20, -200);
    a.minimumDamagePercent = -20;
    a.maximumDamagePercent = 30;
    List<Integer> bounds = new ArrayList<>();
    assertEquals(0, NativePhysicalDamageMath.roll(a, new NativeDefenseData(), new NativeCombatRolls(n -> {
      bounds.add(n); return 0;
    })));
    assertEquals(List.of(2304), bounds); // endpoints -256..2048, clamp final only
  }

  @Test void equippedZeroMinimumDoesNotUseWeaponlessFallbackBeforeNormalDamage() {
    NativeAttackData a = weapon(0, 2, 0);
    a.normalDamage = 1;
    assertEquals(256, NativePhysicalDamageMath.roll(a, new NativeDefenseData(), new NativeCombatRolls(n -> 0)));
  }

  @Test void masteryAndPassiveAreSequentialNotSummed() {
    NativeAttackData a = weapon(1, 2, 0);
    a.alwaysHit = true;
    a.masteryCritical = 50;
    a.criticalStrike = 50;
    a.deadlyStrike = 50;
    List<Integer> bounds = new ArrayList<>();
    NativeDamagePacket result = new NativeAttackResolver(new NativeCombatRolls(n -> {
      bounds.add(n); return n == 100 ? 75 : 0;
    }, n -> 99)).resolve(a, new NativeDefenseData());
    assertFalse(result.critical);
    assertFalse(result.deadlyStrike);
    assertEquals(256, result.physicalDamage);
    assertEquals(List.of(256, 100, 100, 100), bounds);
  }

  @Test void meleeMasterySuccessShortCircuitsPassiveAndDeadly() {
    NativeAttackData a = weapon(1, 2, 0);
    a.alwaysHit = true;
    a.masteryCritical = a.criticalStrike = a.deadlyStrike = 100;
    List<Integer> bounds = new ArrayList<>();
    NativeDamagePacket result = new NativeAttackResolver(new NativeCombatRolls(n -> {
      bounds.add(n); return 0;
    }, n -> 99)).resolve(a, new NativeDefenseData());
    assertEquals(512, result.physicalDamage);
    assertEquals(List.of(256, 100), bounds);
  }

  @Test void missileCriticalAlwaysDrawsPassiveThenDeadlyThenMastery() {
    List<Integer> bounds = new ArrayList<>();
    int[] values = {90, 90, 0};
    assertTrue(NativeMissilePhysicalDamage.creationCritical(0, 50, 10, n -> {
      bounds.add(n); return values[bounds.size() - 1];
    }));
    assertEquals(List.of(100, 100, 100), bounds);
  }

  @Test void missileAppliesPercentageAfterDrawNotToEndpoints() {
    NativeMissilePhysicalDamage missile = new NativeMissilePhysicalDamage(128, 256, 10, 0, 0, false);
    assertEquals(210, missile.roll(new NativeDefenseData(), new NativeCombatRolls(n -> 63)));
  }

  private static NativeAttackData weapon(int min, int max, int attribute) {
    NativeAttackData a = new NativeAttackData();
    a.isPlayer = a.fixedPointPhysical = a.ordinaryWeaponPhysical = a.equippedWeapon = true;
    a.minDamage = min; a.maxDamage = max; a.weaponAttributePercent = attribute;
    return a;
  }
}
