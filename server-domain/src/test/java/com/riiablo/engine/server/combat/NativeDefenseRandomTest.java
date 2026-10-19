package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.*;
import static com.riiablo.engine.server.combat.NativeCombatConstants.*;
import org.junit.jupiter.api.Test;

/** SUnitDmg.cpp 0x6FCC2560/0x6FCC2610 source ownership and draw boundaries. */
class NativeDefenseRandomTest {
  @Test void shieldConsumesDefenderSeedOnlyAndBlockedHitDoesNotRollDamage() {
    int[] attack={0},defense={0};
    NativeAttackResolver resolver=new NativeAttackResolver(new NativeCombatRolls(
        n->{attack[0]++;return 0;},n->{defense[0]++;return 0;}));
    NativeAttackData a=new NativeAttackData(); a.minDamage=a.maxDamage=10; a.attackRating=10000; a.level=40;
    NativeDefenseData d=new NativeDefenseData(); d.canBlock=true; d.blockChance=75;
    NativeDamagePacket result=resolver.resolve(a,d);
    assertTrue(result.blocked); assertEquals(1,attack[0]); assertEquals(1,defense[0]);
    assertEquals(0,result.totalDamage);
  }
  @Test void nativePassivesAbove75AreNotCappedAndGuaranteedStillDraws() {
    int[] draws={0}; NativeCombatRolls rolls=new NativeCombatRolls(n->fail("attacker"),n->{draws[0]++;return 79;});
    assertEquals(DEFENSE_DODGE,rolls.passiveDefense(ATTACK_MELEE,false,80,0,0,0));
    assertEquals(DEFENSE_AVOID,rolls.passiveDefense(ATTACK_RANGED,false,0,100,0,0));
    assertEquals(DEFENSE_EVADE,rolls.passiveDefense(ATTACK_MELEE,true,100,100,100,100));
    assertEquals(3,draws[0]);
  }
  @Test void zeroDefenseDoesNotDrawAndShieldFailureFallsThroughOnDefenderStream() {
    int[] draws={0}; NativeCombatRolls rolls=new NativeCombatRolls(n->fail("attacker"),n->{draws[0]++;return 50;});
    assertFalse(rolls.defensePercent(0));
    assertEquals(DEFENSE_NONE,rolls.passiveDefense(ATTACK_MELEE,false,0,0,0,0));
    assertEquals(0,draws[0]);
    assertFalse(rolls.defensePercent(25));
    assertEquals(DEFENSE_DODGE,rolls.passiveDefense(ATTACK_MELEE,false,51,0,0,0));
    assertEquals(2,draws[0]);
  }
}
