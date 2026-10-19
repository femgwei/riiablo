package com.riiablo.engine.server.combat;

import static com.riiablo.engine.server.combat.NativeCombatConstants.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Independent literals from SUnitDmg.cpp; not executable-captured samples. */
class NativeFixedPhysicalDamageTest {
  @ParameterizedTest
  @CsvSource({
      "10,11,0,0,false,2560,256", "10,11,0,0,true,2815,256",
      "10,10,0,0,true,2815,256", "20,10,0,0,true,5375,256",
      "0,0,0,0,false,256,256", "0,0,0,0,true,511,256",
      "10,11,50,50,false,5120,512", "10,10,50,50,true,5631,512",
      "11,11,50,25,false,4928,448", "11,11,50,25,true,5375,448",
      "10,10,0,-100,false,256,26", "10,10,0,-100,true,281,26",
      "5001,5001,0,50,false,1920356,406"
  })
  void bonusesScaleEndpointsBeforeExclusiveDraw(int min, int max, int strength, int ed,
      boolean upper, int expected, int range) {
    NativeAttackData a = attacker(min, max);
    a.strength = strength;
    a.enhancedDamagePercent = ed;
    List<Integer> bounds = new ArrayList<>();
    NativeDamagePacket result = resolve(a, new NativeDefenseData(), upper, bounds);
    assertEquals(256, result.physicalScale);
    assertEquals(expected, result.physicalDamage);
    assertEquals(expected / 256f, result.physicalDamageHp());
    assertEquals(expected / 256f, result.totalDamageHp());
    assertEquals(expected / 256, result.totalDamage); // explicitly lossy legacy summary
    assertEquals(Arrays.asList(100, range), bounds);
  }

  @Test void normalDamageAndSeparateEndpointPercentagesRemainBeforeTheDraw() {
    NativeAttackData a = attacker(10, 10);
    a.normalDamage = 2;
    assertEquals(3327, resolve(a, new NativeDefenseData(), true, new ArrayList<>()).physicalDamage);
    a.normalDamage = 0;
    a.minimumDamagePercent = 10;
    a.maximumDamagePercent = 20;
    List<Integer> bounds = new ArrayList<>();
    assertEquals(3378, resolve(a, new NativeDefenseData(), true, bounds).physicalDamage);
    assertEquals(Arrays.asList(100, 563), bounds);
    a.minimumDamagePercent = 0;
    a.maximumDamagePercent = -100;
    bounds.clear();
    assertEquals(2560, resolve(a, new NativeDefenseData(), true, bounds).physicalDamage);
    assertEquals(Arrays.asList(100), bounds); // native skips a collapsed/reversed interval
    a.normalDamage = Integer.MAX_VALUE;
    a.enhancedDamagePercent = 100;
    a.maximumDamagePercent = 0;
    assertEquals(Integer.MAX_VALUE,
        resolve(a, new NativeDefenseData(), false, new ArrayList<>()).physicalDamage);
  }

  @Test void largeNativePercentagesKeepEarlyDivisionThroughResistance() {
    NativeAttackData a = attacker(5001, 5001);
    a.enhancedDamagePercent = 50;
    NativeDefenseData d = new NativeDefenseData();
    d.isPlayer = true;
    d.damageReducedPercent = 50;
    // Endpoint: 1280256 + (1280256 / 100)*50 = 1920356.
    // Resistance: (1920356 / 100)*50 = 960150, not 960178.
    assertEquals(960150, resolve(a, d, false, new ArrayList<>()).physicalDamage);
  }

  @ParameterizedTest
  @CsvSource({"0,928,703,92", "100,1983,1407,198"})
  void criticalConversionFlatReductionResistanceAndLeechKeepTheSameUnits(
      int critical, int physical, int fire, int stolen) {
    NativeAttackData a = attacker(10, 10);
    a.criticalStrike = critical;
    a.physicalConversionPercent = 25;
    a.physicalConversionType = DAMAGE_FIRE;
    a.lifeLeech = a.manaLeech = 10;
    NativeDefenseData d = new NativeDefenseData();
    d.isPlayer = true;
    d.damageReduced = 1;
    d.damageReducedPercent = 50;
    NativeDamagePacket result = resolve(a, d, true, new ArrayList<>());
    assertEquals(physical, result.physicalDamage);
    assertEquals(fire, result.elementalDamage[DAMAGE_FIRE]);
    assertEquals(256, result.elementalScale);
    assertEquals((physical + fire) / 256f, result.totalDamageHp());
    assertEquals(stolen / 256f, result.lifeStolenHp());
    assertEquals(stolen / 256f, result.manaStolenHp());
  }

  @Test void mixedWholeHpElementsAbsorbAndPoisonAreNotScaledTwiceOrAppliedImmediately() {
    NativeAttackData a = attacker(10, 10);
    a.physicalConversionPercent = 25;
    a.physicalConversionType = DAMAGE_TYPE_COUNT; // invalid conversion must not alter element units
    a.elementalMinDamage[DAMAGE_FIRE] = a.elementalMaxDamage[DAMAGE_FIRE] = 3;
    a.elementalMinDamage[DAMAGE_POISON] = a.elementalMaxDamage[DAMAGE_POISON] = 5;
    a.poisonLength = 25;
    NativeDefenseData d = new NativeDefenseData();
    d.absorbFlat[DAMAGE_FIRE] = 1;
    NativeDamagePacket result = resolve(a, d, true, new ArrayList<>());
    assertEquals(256, result.physicalScale);
    assertEquals(1, result.elementalScale);
    assertEquals(2f, result.elementalDamageHp(DAMAGE_FIRE));
    assertEquals(1f, result.absorbedLifeHp());
    assertEquals(3327 / 256f, result.totalDamageHp()); // 2815 + 2*256, no poison
    assertEquals(51 / 256f, NativeStatusDurationMath.totalPoisonToPerFrame(5, 25, 1));
    assertEquals(51 / 256f, NativeStatusDurationMath.totalPoisonToPerFrame(1280, 25, 256));
    assertEquals(56 / 256f, NativeStatusDurationMath.totalPoisonToPerFrame(1407, 25, 256));
    result.reset();
    assertEquals(1, result.physicalScale);
    assertEquals(0f, result.physicalDamageHp());
    assertEquals(0f, result.lifeStolenHp());
  }

  @Test void legacyNegativeElementRollStillOffsetsConvertedPhysicalBeforeClamping() {
    NativeAttackData a = attacker(100, 100);
    a.fixedPointPhysical = false;
    a.physicalConversionPercent = 25;
    a.physicalConversionType = DAMAGE_FIRE;
    a.elementalMinDamage[DAMAGE_FIRE] = -10;
    a.elementalMaxDamage[DAMAGE_FIRE] = 1;
    NativeDamagePacket result = resolve(a, new NativeDefenseData(), false, new ArrayList<>());
    assertEquals(75, result.physicalDamage);
    assertEquals(15, result.elementalDamage[DAMAGE_FIRE]);
    assertEquals(90f, result.totalDamageHp());
  }

  private static NativeAttackData attacker(int min, int max) {
    NativeAttackData a = new NativeAttackData();
    a.fixedPointPhysical = true;
    a.alwaysHit = true;
    a.minDamage = min;
    a.maxDamage = max;
    return a;
  }

  private static NativeDamagePacket resolve(NativeAttackData a, NativeDefenseData d,
      boolean upper, List<Integer> bounds) {
    return new NativeAttackResolver(new NativeCombatRolls(bound -> {
      bounds.add(bound);
      return upper && bound != 100 ? bound - 1 : 0;
    })).resolve(a, d);
  }
}
