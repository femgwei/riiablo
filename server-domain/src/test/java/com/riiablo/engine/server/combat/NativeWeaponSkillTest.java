package com.riiablo.engine.server.combat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Literal D2MOO ApplyDamageBonuses / SrvDo064 expectations. */
class NativeWeaponSkillTest {
  @Test void skillEDAddsToAttributeAndOffWeaponEDBeforeExclusiveFixedDraw() {
    NativeAttackData a = weapon();
    a.weaponAttributePercent = 10;
    a.enhancedDamagePercent = 70 + 25;
    List<Integer> bounds = new ArrayList<>();
    assertEquals(1048, NativePhysicalDamageMath.roll(a, new NativeDefenseData(),
        new NativeCombatRolls(n -> { bounds.add(n); return n - 1; })));
    assertEquals(List.of(525), bounds); // [524,1049), not whole-HP endpoints
  }

  @Test void sourceFractionIsAppliedAfterDrawNotBeforeIt() {
    NativeAttackData a = weapon(); a.weaponSourceDamage = 64;
    a.weaponAttributePercent = 10;
    List<Integer> bounds = new ArrayList<>();
    assertEquals(281, NativePhysicalDamageMath.roll(a, new NativeDefenseData(),
        new NativeCombatRolls(n -> { bounds.add(n); return n - 1; })));
    assertEquals(List.of(282), bounds);
  }

  @Test void sacrificePrefilledFlagSkipsAllThreeCriticalDraws() {
    NativeAttackData a = weapon(); a.alwaysHit = true;
    a.suppressWeaponCritical = true;
    a.masteryCritical = a.criticalStrike = a.deadlyStrike = 100;
    List<Integer> bounds = new ArrayList<>();
    NativeDamagePacket hit = new NativeAttackResolver(new NativeCombatRolls(n -> {
      bounds.add(n); return 0;
    }, n -> 99)).resolve(a, new NativeDefenseData());
    assertFalse(hit.critical); assertFalse(hit.deadlyStrike);
    assertEquals(256, hit.physicalDamage);
    assertEquals(List.of(256), bounds);
  }

  @Test void conversionKeepsSubHpFractionAndDoesNotConvertItemFire() {
    NativeAttackData a = weapon(); a.alwaysHit = true;
    a.weaponAttributePercent = 10;
    a.physicalConversionPercent = 50;
    a.physicalConversionType = NativeCombatConstants.DAMAGE_MAGIC;
    a.nativeMonsterMeleeElements = true;
    a.elementalMinDamage[NativeCombatConstants.DAMAGE_FIRE] = 1;
    a.elementalMaxDamage[NativeCombatConstants.DAMAGE_FIRE] = 1;
    NativeDamagePacket hit = new NativeAttackResolver(new NativeCombatRolls(n -> 0, n -> 99))
        .resolve(a, new NativeDefenseData());
    assertEquals(141, hit.physicalDamage);
    assertEquals(140, hit.elementalDamage[NativeCombatConstants.DAMAGE_MAGIC]);
    assertEquals(256, hit.elementalDamage[NativeCombatConstants.DAMAGE_FIRE]);
  }

  @Test void sacrificeSelfCostUsesMitigatedPhysicalCappedByCurrentLife() {
    assertEquals(56, NativeSacrificeMath.selfDamage(703, 10000, 8));
    assertEquals(20, NativeSacrificeMath.selfDamage(703, 256, 8));
    assertEquals(0, NativeSacrificeMath.selfDamage(0, 256, 8));
    assertEquals(0, NativeSacrificeMath.selfDamage(703, 0, 8));
    assertEquals(0, NativeSacrificeMath.selfDamage(703, 256, -8));
  }

  @Test void sacrificePreservesNativeLargeValueEarlyDivision() {
    assertEquals(83880, NativeSacrificeMath.selfDamage(1048599, Integer.MAX_VALUE, 8)); // (1048599/100)*8
  }

  private static NativeAttackData weapon() {
    NativeAttackData a = new NativeAttackData();
    a.isPlayer = a.fixedPointPhysical = a.ordinaryWeaponPhysical = a.equippedWeapon = true;
    a.minDamage = 1; a.maxDamage = 2;
    return a;
  }
}
