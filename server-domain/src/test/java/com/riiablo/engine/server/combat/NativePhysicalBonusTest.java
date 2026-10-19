package com.riiablo.engine.server.combat;

import static com.riiablo.engine.server.combat.NativeCombatConstants.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Source-derived lower endpoints, not executable-captured damage samples.
 * SUnitDmg.cpp SUNITDMG_ApplyDamageBonuses adds strength to the percentage before
 * clamping to -90 and adding the percentage to the base endpoint. Base 100 keeps
 * these expectations independent of the pending whole-HP/8.8 roll migration.
 */
class NativePhysicalBonusTest {
  @ParameterizedTest(name = "strength={0}, ED={1}, demon={2}, undead={3}: {4} HP")
  @CsvSource({
      "50,50,false,false,200", "50,25,false,false,175",
      "50,50,true,false,225", "50,50,false,true,215",
      "50,50,true,true,240", "0,-50,false,false,50",
      "0,-100,false,false,10", "50,-100,false,false,50",
      "-20,50,false,false,130", "0,-100,true,false,25",
      "0,-200,true,true,10"
  })
  void combinedBonusReachesAttackPacket(int strength, int enhanced, boolean demon,
      boolean undead, int expected) {
    NativeAttackData attacker = attacker(strength, enhanced);
    attacker.isPlayer = true;
    attacker.demonDamagePercent = 25;
    attacker.undeadDamagePercent = 15;
    NativeDefenseData defender = new NativeDefenseData();
    defender.isMonster = true;
    defender.isDemon = demon;
    defender.isUndead = undead;
    NativeDamagePacket packet = new NativeAttackResolver(new NativeCombatRolls(bound -> 0))
        .resolve(attacker, defender);
    assertTrue(packet.hit);
    assertFalse(packet.blocked);
    assertEquals(expected, packet.physicalDamage);
    assertEquals(expected, packet.totalDamageHp(), 0f);
  }

  @ParameterizedTest
  @CsvSource({"0,70,50,7", "100,145,100,14"})
  void correctedBonusFeedsCriticalConversionMitigationAndLeechWithoutExtraDraws(
      int criticalChance, int physical, int magic, int leech) {
    NativeAttackData attacker = attacker(50, 50);
    attacker.criticalStrike = criticalChance;
    attacker.physicalConversionPercent = 25;
    attacker.physicalConversionType = DAMAGE_MAGIC;
    attacker.lifeLeech = 10;
    NativeDefenseData defender = new NativeDefenseData();
    defender.isPlayer = true;
    defender.damageReduced = 10;
    defender.damageReducedPercent = 50;
    List<Integer> bounds = new ArrayList<>();
    NativeDamagePacket packet = new NativeAttackResolver(new NativeCombatRolls(bound -> {
      bounds.add(bound);
      return 0;
    })).resolve(attacker, defender);
    assertEquals(criticalChance > 0, packet.critical);
    assertEquals(physical, packet.physicalDamage);
    assertEquals(magic, packet.elementalDamage[DAMAGE_MAGIC]);
    assertEquals(physical + magic, packet.totalDamageHp(), 0f);
    assertEquals(leech, packet.lifeStolen);
    assertEquals(criticalChance > 0 ? Arrays.asList(100, 1, 100) : Arrays.asList(100, 1), bounds);
  }

  private static NativeAttackData attacker(int strength, int enhanced) {
    NativeAttackData attacker = new NativeAttackData();
    attacker.alwaysHit = true;
    attacker.minDamage = attacker.maxDamage = 100;
    attacker.strength = strength;
    attacker.enhancedDamagePercent = enhanced;
    return attacker;
  }
}
