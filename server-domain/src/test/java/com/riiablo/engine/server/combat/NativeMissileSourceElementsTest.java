package com.riiablo.engine.server.combat;

import static com.riiablo.engine.server.combat.NativeCombatConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class NativeMissileSourceElementsTest {
  @ParameterizedTest
  @CsvSource({"64,384,640,30,150,1,150", "128,768,1280,60,150,3,50",
      "192,1152,1920,90,150,4,37", "1,6,10,0,150,0,150"})
  void combinedEndpointsColdLengthAndPoisonCountScaleButPoisonLengthDoesNot(
      int scale, int expectedMin, int expectedMax, int coldLength, int poisonLength,
      int count, int averagedLength) {
    int[] min = new int[6], max = new int[6];
    min[DAMAGE_COLD] = 2; max[DAMAGE_COLD] = 3;
    min[DAMAGE_POISON] = 10; max[DAMAGE_POISON] = 20;
    NativeMissileElementalDamage p = NativeMissileElementalDamage.mixed(
        DAMAGE_COLD, 256, 512, 40, scale, min, max, 20, 150, 3, 0);
    assertEquals(expectedMin, p.minimum(DAMAGE_COLD));
    assertEquals(expectedMax, p.maximum(DAMAGE_COLD));
    assertEquals(coldLength, p.coldLength);
    assertEquals(poisonLength, p.poisonLength);
    assertEquals(count, p.poisonCount);
    assertEquals(averagedLength, p.basePoisonDuration());
    assertEquals(10 * scale / 128, p.minimum(DAMAGE_POISON));
  }

  @Test
  void poisonAddsRawRateAndOverrideLengthSuppressesCountBeforeScaling() {
    int[] min = new int[6], max = new int[6];
    min[DAMAGE_POISON] = 10; max[DAMAGE_POISON] = 20;
    NativeMissileElementalDamage p = NativeMissileElementalDamage.mixed(
        DAMAGE_POISON, 256, 512, 40, 64, min, max, 0, 150, 8, 30);
    assertEquals(133, p.minimum(DAMAGE_POISON));
    assertEquals(266, p.maximum(DAMAGE_POISON));
    assertEquals(70, p.poisonLength);
    assertEquals(0, p.poisonCount);
    assertEquals(70, p.basePoisonDuration());
  }

  @Test
  void capturedValueOwnsNoArraysAndIncludesMultipleElementsWithoutTableElement() {
    int[] min = new int[6], max = new int[6];
    for (int i = 1; i < 6; i++) { min[i] = 2; max[i] = 3; }
    NativeMissileElementalDamage p = NativeMissileElementalDamage.mixed(
        DAMAGE_PHYSICAL, 9999, 9999, 9999, 64, min, max, 20, 100, 2, 0);
    min[DAMAGE_FIRE] = max[DAMAGE_FIRE] = 999;
    for (int i = 1; i < 6; i++) {
      assertEquals(i == DAMAGE_POISON ? 1 : 256, p.minimum(i));
      assertEquals(i == DAMAGE_POISON ? 1 : 384, p.maximum(i));
    }
    assertEquals(10, p.coldLength);
    assertEquals(100, p.basePoisonDuration());
  }

  @Test
  void rollsEachChannelWithExclusiveUpperBoundAndNormalizesOnlyAtRollTime() {
    int[] min = new int[6], max = new int[6];
    min[DAMAGE_FIRE] = 3; max[DAMAGE_FIRE] = 2;
    min[DAMAGE_LIGHTNING] = max[DAMAGE_LIGHTNING] = 1;
    max[DAMAGE_COLD] = 1; // nonpositive endpoint suppresses roll
    NativeMissileElementalDamage p = NativeMissileElementalDamage.mixed(
        DAMAGE_PHYSICAL, 0, 0, 0, 64, min, max, 0, 0, 0, 0);
    NativeCombatRolls rolls = new NativeCombatRolls(n -> n - 1);
    assertEquals(383, p.roll(DAMAGE_FIRE, rolls));
    assertEquals(128, p.roll(DAMAGE_LIGHTNING, rolls));
    assertEquals(0, p.roll(DAMAGE_COLD, rolls));
  }

  @Test
  void fullResolverKeepsPoisonOutOfImmediateDamageAndMitigatesEachInstantElement() {
    int[] min = new int[6], max = new int[6];
    for (int i = 1; i < 6; i++) min[i] = max[i] = i == DAMAGE_POISON ? 256 : 2;
    NativeAttackData a = new NativeAttackData(); a.isMissile = a.alwaysHit = true;
    a.missileElemental = NativeMissileElementalDamage.mixed(
        DAMAGE_FIRE, 256, 256, 0, 64, min, max, 40, 100, 4, 0);
    NativeDefenseData d = new NativeDefenseData(); d.isPlayer = true;
    d.magicDamageReduced = 1;
    d.resistances[DAMAGE_POISON] = 50; d.poisonLengthResistance = 50;
    NativeDamagePacket p = new NativeAttackResolver(new NativeCombatRolls(n -> 0)).resolve(a, d);
    assertEquals(128, p.elementalDamage[DAMAGE_FIRE]);
    assertEquals(0, p.elementalDamage[DAMAGE_LIGHTNING]);
    assertEquals(0, p.elementalDamage[DAMAGE_COLD]);
    assertEquals(0, p.elementalDamage[DAMAGE_MAGIC]);
    assertEquals(0, p.elementalDamage[DAMAGE_POISON]);
    assertEquals(0.25f, p.poisonDamagePerFrame);
    assertEquals(25, p.poisonDuration);
    assertEquals(50, p.poisonBaseDuration);
    assertEquals(20, p.coldDuration);
    assertEquals(0.5f, p.totalDamageHp());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, 256})
  void rejectsUnsupportedSourceScale(int scale) {
    assertThrows(IllegalArgumentException.class, () -> NativeMissileElementalDamage.mixed(
        DAMAGE_PHYSICAL, 0, 0, 0, scale, new int[6], new int[6], 0, 0, 0, 0));
  }

  @Test
  void nativePacketRollsFireMagicLightningColdPoisonWithoutChangingLegacyOrder() {
    int[] min = new int[6], max = new int[6];
    for (int i = 1; i < 6; i++) min[i] = 1;
    max[DAMAGE_FIRE] = 2; max[DAMAGE_MAGIC] = 4;
    max[DAMAGE_LIGHTNING] = 3; max[DAMAGE_COLD] = 5;
    min[DAMAGE_POISON] = 10; max[DAMAGE_POISON] = 15;
    NativeAttackData a = new NativeAttackData(); a.isMissile = a.alwaysHit = true;
    a.missileElemental = NativeMissileElementalDamage.mixed(
        DAMAGE_PHYSICAL, 0, 0, 0, 128, min, max, 0, 100, 0, 0);
    java.util.List<Integer> bounds = new java.util.ArrayList<>();
    NativeAttackResolver resolver = new NativeAttackResolver(new NativeCombatRolls(n -> {
      bounds.add(n); return 0;
    }));
    resolver.resolve(a, new NativeDefenseData());
    assertEquals(java.util.Arrays.asList(100, 256, 768, 512, 1024, 5), bounds);

    a.missileElemental = null;
    for (int i = 1; i < 6; i++) {
      a.elementalMinDamage[i] = min[i]; a.elementalMaxDamage[i] = max[i];
    }
    bounds.clear();
    resolver.resolve(a, new NativeDefenseData());
    assertEquals(java.util.Arrays.asList(100, 1, 2, 4, 5, 3), bounds);
  }
}
