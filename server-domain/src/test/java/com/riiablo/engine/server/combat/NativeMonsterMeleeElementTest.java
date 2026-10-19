package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.*;
import static com.riiablo.engine.server.combat.NativeCombatConstants.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** SUNITDMG_FillDamageValues source-stat policy, isolated from hit scheduling. */
class NativeMonsterMeleeElementTest {
  static NativeAttackData attacker() {
    NativeAttackData a = new NativeAttackData();
    a.nativeMonsterMeleeElements = true; a.alwaysHit = true; a.level = 40;
    return a;
  }
  static NativeDefenseData defender() {
    NativeDefenseData d = new NativeDefenseData(); d.isPlayer = true; d.level = 40;
    for (int i = 1; i < DAMAGE_TYPE_COUNT; i++) d.maxResistances[i] = 75;
    return d;
  }
  static NativeDamagePacket resolve(NativeAttackData a, NativeDefenseData d, List<Integer> bounds) {
    return new NativeAttackResolver(new NativeCombatRolls(n -> {
      bounds.add(n); return n - 1;
    })).resolve(a, d);
  }

  @ParameterizedTest @CsvSource({"1,0,2,511", "1,2,2,512", "1,3,2,768",
      "1,-1,1,255", "1,2,0,0", "2,1,2,511", "3,1,2,511", "5,1,2,511"})
  void nativeMeleeDoesNotUseMissilePositiveMinSwapOrInclusiveEndpoint(int type,
      int min, int max, int expected) {
    NativeAttackData a = attacker(); a.elementalMinDamage[type] = min; a.elementalMaxDamage[type] = max;
    NativeDamagePacket p = resolve(a, defender(), new ArrayList<>());
    assertEquals(256, p.elementalScale); assertEquals(expected, p.elementalDamage[type]);
  }

  @Test void differentChannelsRollInSourceOrderAndEqualEndpointsDoNotDraw() {
    NativeAttackData a = attacker();
    for (int type = 1; type < 6; type++) {
      a.elementalMinDamage[type] = 1; a.elementalMaxDamage[type] = type + 2;
    }
    a.poisonLength = 20;
    List<Integer> bounds = new ArrayList<>(); resolve(a, defender(), bounds);
    assertEquals(java.util.Arrays.asList(100, 512, 768, 1024, 1536, 5), bounds);
    a.elementalMinDamage[1] = a.elementalMaxDamage[1];
    bounds.clear(); resolve(a, defender(), bounds);
    assertEquals(java.util.Arrays.asList(100, 768, 1024, 1536, 5), bounds);
  }

  @Test void sourcePoisonIsRateNotTotalOrInstantDamageAndLengthAveragesBeforeResistance() {
    NativeAttackData a = attacker(); a.elementalMinDamage[4] = a.elementalMaxDamage[4] = 511;
    a.poisonLength = 100; a.poisonCount = 2;
    NativeDefenseData d = defender(); d.resistances[4] = 50; d.poisonLengthResistance = 50;
    NativeDamagePacket p = resolve(a, d, new ArrayList<>());
    assertEquals(255 / 256f, p.poisonDamagePerFrame);
    assertEquals(25, p.poisonDuration); assertEquals(50, p.poisonBaseDuration);
    assertEquals(0, p.elementalDamage[4]); assertEquals(0f, p.totalDamageHp() - p.physicalDamageHp());
  }

  @Test void overrideSuppressesPoisonAverageAndPoisonIgnoresMdrAndAbsorb() {
    NativeAttackData a = attacker(); a.elementalMinDamage[4] = a.elementalMaxDamage[4] = 511;
    a.poisonLength = 100; a.poisonCount = 10; a.poisonOverrideLength = 17;
    NativeDefenseData d = defender(); d.magicDamageReduced = 1000;
    d.absorbPercent[4] = 100; d.absorbFlat[4] = 1000;
    NativeDamagePacket p = resolve(a, d, new ArrayList<>());
    assertEquals(511 / 256f, p.poisonDamagePerFrame); assertEquals(17, p.poisonDuration);
    assertEquals(0, p.absorbedLife);
  }

  @Test void nativeMasteryScalesBeforeExclusiveRollAndUsesOriginalLargeValueTruncation() {
    NativeAttackData a = attacker(); a.elementalMinDamage[1] = a.elementalMaxDamage[1] = 5001;
    a.elementalMastery[1] = 50;
    NativeDamagePacket p = resolve(a, defender(), new ArrayList<>());
    assertEquals(1_920_356, p.elementalDamage[1]); // 1280256 + (1280256/100)*50
  }

  @Test void coldLengthRequiresPositiveUnmitigatedColdButNotPositiveSettledDamage() {
    NativeAttackData a = attacker(); a.coldLength = 100;
    assertEquals(0, resolve(a, defender(), new ArrayList<>()).coldDuration);
    a.elementalMinDamage[3] = a.elementalMaxDamage[3] = 2;
    NativeDefenseData d = defender(); d.magicDamageReduced = 99; d.resistances[3] = 50;
    NativeDamagePacket p = resolve(a, d, new ArrayList<>());
    assertEquals(0, p.elementalDamage[3]); assertEquals(50, p.coldDuration);
  }

  @Test void nativeMdrAbsorbAndZeroLengthPoisonNeverLeakImmediateDamage() {
    NativeAttackData a = attacker(); a.elementalMinDamage[1] = a.elementalMaxDamage[1] = 10;
    a.elementalMinDamage[4] = a.elementalMaxDamage[4] = 511;
    NativeDefenseData d = defender(); d.magicDamageReduced = 1;
    d.resistances[1] = 50; d.absorbPercent[1] = 80; d.absorbFlat[1] = 1;
    NativeDamagePacket p = resolve(a, d, new ArrayList<>());
    assertEquals(436, p.elementalDamage[1]); assertEquals(716, p.absorbedLife);
    assertEquals(0, p.elementalDamage[4]); assertEquals(0, p.poisonDuration);
  }

  @Test void monsterCriticalIsAfterEverySourceRollBeforeMitigationAndDoublesRateNotLength() {
    NativeAttackData a = attacker(); a.fixedPointPhysical = true; a.minDamage = 10; a.maxDamage = 11;
    a.monsterCriticalChance = 100;
    a.elementalMinDamage[1] = 2; a.elementalMaxDamage[1] = 3;
    a.elementalMinDamage[4] = 256; a.elementalMaxDamage[4] = 512; a.poisonLength = 100;
    NativeDefenseData d = defender(); d.damageReduced = 1; d.magicDamageReduced = 1;
    List<Integer> bounds = new ArrayList<>(); NativeDamagePacket p = resolve(a,d,bounds);
    assertTrue(p.monsterCritical); assertFalse(p.critical);
    assertEquals((2815 * 2) - 256,p.physicalDamage);
    assertEquals((767 * 2) - 256,p.elementalDamage[1]);
    assertEquals(1022 / 256f,p.poisonDamagePerFrame); assertEquals(100,p.poisonDuration);
    assertEquals(java.util.Arrays.asList(100,256,256,256,100),bounds);
  }
}
