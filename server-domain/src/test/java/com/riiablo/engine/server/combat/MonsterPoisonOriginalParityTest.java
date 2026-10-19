package com.riiablo.engine.server.combat;

import static com.riiablo.engine.server.combat.NativeCombatConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Literal, source-derived expectations, NOT captures from the original executable.
 * D2MOO 0c0bbd1: Missile.cpp table HitShift; MissMode.cpp RollDamageValue;
 * SUnitDmg.cpp sgDamageStatTable PoisLen/PoisDamage and ApplyResistancesAndAbsorb.
 */
@Tag("monster-parity-audit")
class MonsterPoisonOriginalParityTest {
  @ParameterizedTest
  @CsvSource({"50,0,0,255,100", "0,50,0,511,50", "50,50,25,383,75",
      "50,50,-25,127,25", "200,200,0,127,25", "-100,-100,0,1022,200"})
  void poisonIsRateAndSeparateLengthNotAnAbsorbableInstantHit(int resistance, int lengthResist,
      int pierce, int expectedRate, int expectedLength) {
    NativeAttackData a = new NativeAttackData();
    a.isMissile = a.alwaysHit = true;
    a.missileElemental = new NativeMissileElementalDamage(DAMAGE_POISON, 256, 512, 100);
    a.elementalPierce[DAMAGE_POISON] = pierce;
    NativeDefenseData d = new NativeDefenseData();
    d.isPlayer = true;
    d.resistances[DAMAGE_POISON] = resistance;
    d.poisonLengthResistance = lengthResist;
    d.magicDamageReduced = 100;
    d.absorbFlat[DAMAGE_POISON] = 100;
    NativeDamagePacket p = new NativeAttackResolver(new NativeCombatRolls(n -> n - 1)).resolve(a, d);
    assertTrue(p.hit);
    assertEquals(expectedRate / 256f, p.poisonDamagePerFrame);
    assertEquals(expectedLength, p.poisonDuration);
    assertEquals(0f, p.totalDamageHp());
    assertEquals(0, p.elementalDamage[DAMAGE_POISON]);
    assertEquals(0, p.absorbedLife);
  }
}
