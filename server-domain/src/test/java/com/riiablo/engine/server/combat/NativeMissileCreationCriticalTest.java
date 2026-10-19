package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NativeMissileCreationCriticalTest {
  @ParameterizedTest @CsvSource({"0,0,99,false,1", "0,100,99,true,2",
      "100,100,99,true,1", "50,0,49,true,1", "50,50,50,false,2"})
  void creationAlwaysDrawsPassiveBeforeConditionalDeadly(int passive, int deadly,
      int roll, boolean expected, int draws) {
    List<Integer> bounds = new ArrayList<>();
    assertEquals(expected, NativeMissilePhysicalDamage.creationCritical(passive, deadly,
        n -> { bounds.add(n); return roll; }));
    assertEquals(draws, bounds.size()); for (int n : bounds) assertEquals(100, n);
  }

  @ParameterizedTest @CsvSource({"true,5120", "false,2560"})
  void capturedOutcomeNeverRerollsAtImpactAndOnlyDoublesPhysical(boolean critical, int physical) {
    NativeAttackData a = new NativeAttackData(); a.level = 40; a.alwaysHit = true; a.isMissile = true;
    a.missilePhysical = new NativeMissilePhysicalDamage(2560,2560,0,0,0,false)
        .withCreationCritical(critical);
    a.criticalStrike = a.deadlyStrike = 100;
    a.missileElemental = new NativeMissileElementalDamage(1,256,256,0);
    NativeDefenseData d = new NativeDefenseData(); d.isPlayer = true; d.level = 40; d.maxResistances[1] = 75;
    List<Integer> bounds = new ArrayList<>();
    NativeDamagePacket p = new NativeAttackResolver(new NativeCombatRolls(n -> {
      bounds.add(n); return 0;
    })).resolve(a,d);
    assertEquals(physical,p.physicalDamage); assertEquals(256,p.elementalDamage[1]);
    assertEquals(critical,p.critical); assertFalse(p.deadlyStrike);
    assertEquals(java.util.Arrays.asList(100), bounds);
    NativeMissilePhysicalDamage replaced = a.missilePhysical.withEndpoints(5120,5120);
    assertTrue(replaced.criticalCaptured); assertEquals(critical,replaced.creationCritical);
  }

  @Test void impactOwnerCriticalStacksWithCreationCriticalBeforeFlatReduction() {
    NativeAttackData a = new NativeAttackData(); a.alwaysHit = true; a.isMissile = true;
    a.missilePhysical = new NativeMissilePhysicalDamage(2560,2560,0,0,0,false)
        .withCreationCritical(true).withOwnerCritical(true);
    a.missileElemental = new NativeMissileElementalDamage(1,512,512,0);
    NativeDefenseData d = new NativeDefenseData(); d.isPlayer = true; d.damageReduced = 1;
    d.magicDamageReduced = 1; d.maxResistances[1] = 75;
    List<Integer> bounds = new ArrayList<>();
    NativeDamagePacket p = new NativeAttackResolver(new NativeCombatRolls(n -> {
      bounds.add(n); return 0;
    })).resolve(a,d);
    assertEquals(9984,p.physicalDamage); assertEquals(768,p.elementalDamage[1]);
    assertTrue(p.critical); assertTrue(p.monsterCritical);
    assertEquals(java.util.Arrays.asList(100),bounds);
  }
}
