package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Literal D2MOO MissMode.cpp expectations, independent of the melee helper. */
class NativeMissilePhysicalDamageTest {
  @ParameterizedTest
  @CsvSource({
      "2560,2816,0,false,2560,256", "2560,2816,0,true,2815,256",
      "2560,2560,0,true,2560,0", "2816,2560,0,true,2815,256",
      "0,2560,0,true,0,0", "2560,0,0,true,0,0", "-1,2560,0,true,0,0",
      "2560,2816,75,true,4926,256", "2560,2816,-100,true,282,256",
      "128,128,0,true,128,0", "1,1,50,true,1,0",
      "1280256,1280256,50,false,1920384,0"
  })
  void exclusiveRollPrecedesPercentAndDoesNotInventDamage(int min, int max, int percent,
      boolean upper, int expected, int range) {
    NativeAttackData a = attacker(min, max, percent);
    // Neither melee strength/normaldamage nor its endpoint bonus policy applies.
    a.strength = a.normalDamage = a.enhancedDamagePercent = 100;
    a.minimumDamagePercent = a.maximumDamagePercent = 100;
    List<Integer> bounds = new ArrayList<>();
    NativeDamagePacket result = resolve(a, new NativeDefenseData(), upper, bounds);
    assertEquals(expected, result.physicalDamage);
    assertEquals(256, result.physicalScale);
    assertEquals(expected / 256f, result.totalDamageHp());
    assertEquals(range > 0 ? Arrays.asList(100, range) : Arrays.asList(100), bounds);
  }

  @Test void targetBonusesAreCombinedAfterDrawWithNegativeFloor() {
    NativeAttackData a = attacker(2560, 2816, 25);
    a.missilePhysical = new NativeMissilePhysicalDamage(2560, 2816, 25, 50, -200, false);
    NativeDefenseData d = new NativeDefenseData();
    d.isDemon = true;
    assertEquals(4926, resolve(a, d, true, new ArrayList<>()).physicalDamage);
    d.isUndead = true;
    assertEquals(282, resolve(a, d, true, new ArrayList<>()).physicalDamage);
  }

  @Test void fractionalPhysicalReachesMitigationConversionAndLeech() {
    NativeAttackData a = attacker(2560, 2816, 0);
    NativeDefenseData d = new NativeDefenseData();
    d.isPlayer = true;
    d.damageReduced = 10;
    d.damageReducedPercent = 50;
    a.lifeLeech = 100;
    NativeDamagePacket result = resolve(a, d, true, new ArrayList<>());
    assertEquals(127, result.physicalDamage);
    assertEquals(127 / 256f, result.totalDamageHp());
    assertEquals(127 / 256f, result.lifeStolenHp());
    d.damageReduced = d.damageReducedPercent = 0;
    a.physicalConversionType = NativeCombatConstants.DAMAGE_FIRE;
    a.physicalConversionPercent = 50;
    result = resolve(a, d, true, new ArrayList<>());
    assertEquals(1408, result.physicalDamage);
    assertEquals(1407, result.elementalDamage[NativeCombatConstants.DAMAGE_FIRE]);
    assertEquals(2815 / 256f, result.totalDamageHp());
  }

  @Test void subHpPhysicalIsBlockableButZeroEndpointIsNot() {
    NativeAttackData a = attacker(128, 128, 0);
    assertTrue(NativeAttackResolver.hasBlockablePhysicalDamage(a));
    NativeDefenseData d = new NativeDefenseData();
    d.canBlock = true;
    d.blockChance = 100;
    NativeDamagePacket result = resolve(a, d, false, new ArrayList<>());
    assertTrue(result.blocked);
    assertEquals(0, result.totalDamageHp());
    a.missilePhysical = a.missilePhysical.withEndpoints(0, 128);
    assertFalse(NativeAttackResolver.hasBlockablePhysicalDamage(a));
    assertFalse(resolve(a, d, false, new ArrayList<>()).blocked);
  }

  static NativeAttackData attacker(int min, int max, int percent) {
    NativeAttackData a = new NativeAttackData();
    a.isMissile = a.alwaysHit = true;
    a.missilePhysical = new NativeMissilePhysicalDamage(min, max, percent, 0, 0, false);
    return a;
  }

  static NativeDamagePacket resolve(NativeAttackData a, NativeDefenseData d,
      boolean upper, List<Integer> bounds) {
    return new NativeAttackResolver(new NativeCombatRolls(bound -> {
      bounds.add(bound);
      return upper && bound != 100 ? bound - 1 : 0;
    })).resolve(a, d);
  }
}
