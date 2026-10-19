package com.riiablo.engine.server.combat;

import static com.riiablo.engine.server.combat.NativeCombatConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NativeMissilePoisonDamageTest {
  @ParameterizedTest
  @CsvSource({"256,512,511,256", "256,256,256,0", "512,256,511,256",
      "0,512,0,0", "256,0,0,0", "-1,512,0,0", "128,129,128,1"})
  void poisonRollsRawRatesNotWholeHitPoints(int min, int max, int rate, int range) {
    List<Integer> bounds = new ArrayList<>();
    NativeDamagePacket p = resolve(min, max, 4, new NativeDefenseData(), 0, bounds);
    assertEquals(rate, p.poisonRawDamageFixed);
    assertEquals(rate / 256f, p.poisonDamagePerFrame);
    assertEquals(4, p.poisonDuration);
    assertEquals(0, p.elementalDamage[DAMAGE_POISON]);
    assertEquals(0f, p.totalDamageHp());
    assertEquals(range > 0 ? Arrays.asList(100, range) : Arrays.asList(100), bounds);
  }

  @ParameterizedTest
  @CsvSource({
      "false,50,0,75,0,255,100", "false,0,50,75,0,511,50",
      "false,50,50,75,25,383,75", "false,50,50,75,-25,127,25",
      "false,200,200,200,0,25,25", "false,100,100,75,50,255,50",
      "false,-200,-200,75,0,1022,200", "true,99,0,75,0,5,100",
      "true,100,0,75,1000,0,200", "true,0,100,75,1000,1022,0",
      "true,100,100,75,1000,0,0", "true,0,99,75,0,511,1"})
  void rateAndLengthHaveIndependentResistanceRows(boolean monster, int resist, int lengthResist,
      int maxResist, int pierce, int expectedRate, int expectedLength) {
    NativeDefenseData d = new NativeDefenseData();
    d.isMonster = monster; d.isPlayer = !monster;
    d.resistances[DAMAGE_POISON] = resist;
    d.maxResistances[DAMAGE_POISON] = maxResist;
    d.poisonLengthResistance = lengthResist;
    // These stats must never reduce or absorb poison.
    d.magicDamageReduced = 999;
    d.absorbPercent[DAMAGE_POISON] = 100;
    d.absorbFlat[DAMAGE_POISON] = 999;
    NativeDamagePacket p = resolve(511, 511, 100, d, pierce, new ArrayList<>());
    assertEquals(expectedRate / 256f, p.poisonDamagePerFrame);
    assertEquals(expectedLength, p.poisonDuration);
    assertEquals(0, p.absorbedLife);
    assertEquals(0, p.totalDamage);
    assertEquals(0f, p.totalDamageHp());
  }

  @Test void largeRateAndLengthUseNativeEarlyDivisionOnlyWhenResistanceIsNonzero() {
    NativeDefenseData d = new NativeDefenseData();
    d.resistances[DAMAGE_POISON] = d.poisonLengthResistance = 50;
    NativeDamagePacket p = resolve(1280256, 1280256, 1280256, d, 0, new ArrayList<>());
    assertEquals(640100 / 256f, p.poisonDamagePerFrame);
    assertEquals(640100, p.poisonDuration);
    d.resistances[DAMAGE_POISON] = d.poisonLengthResistance = 0;
    p = resolve(1280256, 1280256, 1280256, d, 0, new ArrayList<>());
    assertEquals(1280256 / 256f, p.poisonDamagePerFrame);
    assertEquals(1280256, p.poisonDuration);
  }

  @Test void zeroLengthShrineMixedPhysicalAndResetNeverLeakRateIntoImmediateDamage() {
    NativeDefenseData d = new NativeDefenseData();
    d.poisonShrine = true;
    NativeAttackData a = NativeMissileElementalDamageTest.attacker(DAMAGE_POISON, 511, 511);
    a.missileElemental = new NativeMissileElementalDamage(DAMAGE_POISON, 511, 511, 100);
    a.missilePhysical = new NativeMissilePhysicalDamage(128, 128, 0, 0, 0, false);
    NativeDamagePacket p = NativeMissileElementalDamageTest.resolve(a, d, new ArrayList<>());
    assertEquals(0, p.poisonDuration);
    assertEquals(511 / 256f, p.poisonDamagePerFrame);
    assertEquals(0.5f, p.totalDamageHp());
    p.reset();
    assertEquals(0f, p.poisonDamagePerFrame);
    assertEquals(0, p.poisonRawDamageFixed);
    assertEquals(0, p.poisonBaseDuration);
    assertEquals(0, p.poisonPiercePercent);
    d.poisonShrine = false;
    p = resolve(511, 511, 0, d, 0, new ArrayList<>());
    assertEquals(0f, p.totalDamageHp());
  }

  static NativeDamagePacket resolve(int min, int max, int length, NativeDefenseData d,
      int pierce, List<Integer> bounds) {
    NativeAttackData a = NativeMissileElementalDamageTest.attacker(DAMAGE_POISON, min, max);
    a.missileElemental = new NativeMissileElementalDamage(DAMAGE_POISON, min, max, length);
    a.elementalPierce[DAMAGE_POISON] = pierce;
    return NativeMissileElementalDamageTest.resolve(a, d, bounds);
  }
}
