package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Literal call ordering in MissMode HandleMissileCollision/FillDamageParams/SetDamageFlags. */
class NativeMissileRandomScheduleTest {
  static NativeAttackData projectile() {
    NativeAttackData a=new NativeAttackData(); a.level=40; a.attackRating=10000;
    a.isMissile=true; a.alwaysHit=true;
    a.missilePhysical=new NativeMissilePhysicalDamage(2560,2816,0,0,0,false).withCreationCritical(false);
    a.missileElemental=new NativeMissileElementalDamage(1,256,512,0);
    return a;
  }
  static NativeDefenseData defender() {
    NativeDefenseData d=new NativeDefenseData(); d.level=40; d.canBlock=true; d.blockChance=75; return d;
  }
  @Test void alwaysHitSkipsOwnerArAndRollsPhysicalFireBeforeDefenderAndOwnerCriticalEvenOnBlock() {
    ArrayList<String> trace=new ArrayList<>();
    NativeAttackResolver resolver=new NativeAttackResolver(new NativeCombatRolls(
        n->{trace.add("damage:"+n);return 0;}, n->{trace.add("defense");return 0;},
        n->{trace.add("AR");return 0;}, ()->{trace.add("crit");return true;}));
    NativeDamagePacket result=resolver.resolve(projectile(),defender());
    assertTrue(result.blocked); assertTrue(result.monsterCritical);
    assertEquals(Arrays.asList("damage:256","damage:256","defense","crit"),trace);
    assertEquals(0,result.totalDamage);
  }
  @Test void arMissConsumesOnlyOwnerHitStream() {
    ArrayList<String> trace=new ArrayList<>(); NativeAttackData a=projectile(); a.alwaysHit=false;
    NativeAttackResolver resolver=new NativeAttackResolver(new NativeCombatRolls(
        n->fail("damage on miss"),n->fail("defense on miss"),n->{trace.add("AR");return 99;},
        ()->fail("crit on miss")));
    assertFalse(resolver.resolve(a,defender()).hit); assertEquals(Arrays.asList("AR"),trace);
  }
  @Test void successfulRatedHitConsumesThreeStreamsInNativeOrder() {
    ArrayList<String> trace=new ArrayList<>(); NativeAttackData a=projectile(); a.alwaysHit=false;
    NativeAttackResolver resolver=new NativeAttackResolver(new NativeCombatRolls(
        n->{trace.add("damage");return 0;},n->{trace.add("defense");return 99;},
        n->{trace.add("AR");return 0;},()->{trace.add("crit");return true;}));
    NativeDamagePacket out=resolver.resolve(a,defender());
    assertTrue(out.hit); assertFalse(out.blocked); assertEquals(5120,out.physicalDamage);
    assertEquals(512,out.elementalDamage[1]);
    assertEquals(Arrays.asList("AR","damage","damage","defense","crit"),trace);
  }
  @Test void runningMeleePlayerSkipsArButStillChecksDefenses() {
    NativeAttackData a=new NativeAttackData(); a.level=40; a.minDamage=a.maxDamage=10;
    NativeDefenseData d=defender(); d.isPlayer=true; d.isRunning=true; d.dexterity=1000;
    int[] counts={0};
    NativeAttackResolver resolver=new NativeAttackResolver(new NativeCombatRolls(
        n->fail("no attacker draws when running target blocks"),n->{counts[0]++;return 0;}));
    NativeDamagePacket out=resolver.resolve(a,d);
    assertTrue(out.hit); assertTrue(out.blocked); assertEquals(-1,out.hitRoll); assertEquals(1,counts[0]);
  }

  @Test void allFiveNativeMissileChannelsPrecedeDefensesInOriginalOrder() {
    ArrayList<String> trace = new ArrayList<>();
    NativeAttackData a = projectile();
    a.missilePhysical = new NativeMissilePhysicalDamage(2560, 2688, 0, 0, 0, false)
        .withCreationCritical(false);
    // Enum order is fire/lightning/cold/poison/magic. Distinct widths expose
    // incorrectly using enum order instead of fire/magic/lightning/cold/poison.
    a.missileElemental = NativeMissileElementalDamage.mixed(0, 0, 0, 0, 128,
        new int[] {0, 1, 1, 1, 1, 1}, new int[] {0, 2, 4, 5, 6, 3}, 10, 20, 0, 0);
    NativeAttackResolver resolver = new NativeAttackResolver(new NativeCombatRolls(
        n -> { trace.add("damage:" + n); return 0; },
        n -> { trace.add("defense"); return 99; },
        n -> fail("unrated missile must not draw owner AR"),
        () -> { trace.add("crit"); return false; }));
    NativeDamagePacket out = resolver.resolve(a, defender());
    assertTrue(out.hit);
    assertFalse(out.blocked);
    assertEquals(Arrays.asList("damage:128", "damage:256", "damage:512",
        "damage:768", "damage:1024", "damage:5", "defense", "crit"), trace);
    assertEquals(2560, out.physicalDamage);
    assertEquals(1 / 256f, out.poisonDamagePerFrame);
  }
}
