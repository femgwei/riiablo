package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** D2MOO SUnitDmg.cpp native expectations promoted to the ordinary fast gate.
 * Whole-HP lower endpoints isolate mitigation; fractional roll parity is separate.
 */
class NativeMonsterHitMitigationTest {
  @ParameterizedTest
  @CsvSource({"10,10,95", "10,90,20", "20,10,95", "1,99,5"})
  void zeroRatingAndDefenseStillApplyLevelRatioAndExactHitBoundary(int alvl, int dlvl, int chance) {
    NativeAttackData attacker = new NativeAttackData();
    attacker.level = alvl;
    NativeDefenseData target = new NativeDefenseData();
    target.isPlayer = true;
    target.level = dlvl;
    assertEquals(chance, NativeAttackResolver.calculateHitChance(attacker, target));
    assertTrue(NativeAttackResolver.calculateHit(attacker, target, chance - 1).hit);
    assertFalse(NativeAttackResolver.calculateHit(attacker, target, chance).hit);
  }

  @ParameterizedTest(name = "player={0}, resistance={1}, flat={2}, undead={3}, sanctuary={4}: {5} HP")
  @CsvSource({
      "true,0,0,false,false,100", "true,50,10,false,false,45",
      "true,0,100,false,false,0", "true,0,200,false,false,0",
      "true,75,0,false,false,50", "true,100,0,false,false,50",
      "true,-50,10,false,false,135", "true,-200,10,false,false,180",
      "false,75,0,false,false,25", "false,100,0,false,false,0",
      "false,150,0,false,false,0", "false,50,10,true,true,90",
      "false,100,10,true,true,90", "false,-50,10,true,true,135",
      "false,50,10,false,true,45", "false,50,100,true,true,0"
  })
  void realAttackPacketUsesNativeMitigation(boolean player, int resistance, int flat,
      boolean undead, boolean sanctuary, int expected) {
    NativeAttackData attacker = new NativeAttackData();
    attacker.level = 10;
    attacker.alwaysHit = true;
    attacker.minDamage = attacker.maxDamage = 100;
    attacker.bypassUndeadPhysicalResistance = sanctuary;
    NativeDefenseData target = new NativeDefenseData();
    target.level = 10;
    target.isPlayer = player;
    target.isMonster = !player;
    target.isUndead = undead;
    target.damageReducedPercent = resistance;
    target.immunePhysical = resistance >= 100;
    target.damageReduced = flat;
    NativeDamagePacket packet = new NativeAttackResolver(new NativeCombatRolls(bound -> 0))
        .resolve(attacker, target);
    assertTrue(packet.hit);
    assertFalse(packet.blocked);
    assertEquals(expected, packet.physicalDamage);
    assertEquals(expected, packet.totalDamageHp(), 0f);
  }
}
