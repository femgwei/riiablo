package com.riiablo.engine.server.combat;

import static com.riiablo.engine.server.combat.NativeCombatConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NativeMissileElementalDamageTest {
  @ParameterizedTest
  @CsvSource({"2560,2816,2815,256", "2560,2560,2560,0", "2816,2560,2815,256",
      "0,2816,0,0", "2560,0,0,0", "-1,2816,0,0", "128,129,128,1"})
  void endpointsStayFixedWithExclusiveUpperAndNoInventedDamage(int min, int max, int expected, int range) {
    List<Integer> bounds = new ArrayList<>();
    NativeDamagePacket p = resolve(attacker(DAMAGE_FIRE, min, max), new NativeDefenseData(), bounds);
    assertEquals(expected, p.elementalDamage[DAMAGE_FIRE]);
    assertEquals(256, p.elementalScale);
    assertEquals(expected / 256f, p.totalDamageHp());
    assertEquals(range > 0 ? Arrays.asList(100, range) : Arrays.asList(100), bounds);
  }

  @ParameterizedTest
  @CsvSource({
      "1,2815,10,50,75,0,0,0,127,0", // MDR first, then resist
      "2,2815,10,-50,75,0,0,0,382,0",
      "3,2815,11,0,75,0,40,10,0,0", // fully reduced, never negative life/absorb
      "5,2815,0,50,75,100,0,0,1407,0", // magic has no pierce row
      "1,2815,0,50,75,25,0,0,2111,0",
      "1,2815,0,50,75,-25,0,0,703,0", // signed pierce
      "1,2815,0,-200,75,0,0,0,5630,0",
      "1,2815,0,200,200,0,0,0,140,0", // player max 95
      "1,2815,0,100,75,50,0,0,1407,0", // player is not immune
      "1,2815,0,0,75,0,100,1,1433,1382", // capped percent THEN flat
      "1,2815,0,0,75,0,0,20,0,2815",
      "1,1280256,0,50,75,0,0,0,640100,0", // native early division
      "1,1280256,0,0,75,0,0,0,1280256,0" // no truncation if no resistance
  })
  void independentMitigationSamples(int type, int raw, int mdr, int res, int max,
      int pierce, int absorb, int flat, int expected, int healed) {
    NativeAttackData a = attacker(type, raw, raw);
    a.elementalPierce[type] = pierce;
    NativeDefenseData d = new NativeDefenseData();
    d.isPlayer = true;
    d.magicDamageReduced = mdr;
    d.resistances[type] = res;
    d.maxResistances[type] = max;
    d.absorbPercent[type] = absorb;
    d.absorbFlat[type] = flat;
    NativeDamagePacket p = resolve(a, d, new ArrayList<>());
    assertEquals(expected, p.elementalDamage[type]);
    assertEquals(healed, p.absorbedLife);
    assertEquals(healed / 256f, p.absorbedLifeHp());
  }

  @ParameterizedTest
  @CsvSource({"99,0,28", "99,50,1435", "100,1000,0", "150,1000,0"})
  void monstersAreNotPlayerCappedAndPierceCannotBreakImmunity(int res, int pierce, int expected) {
    NativeAttackData a = attacker(DAMAGE_LIGHTNING, 2815, 2815);
    a.elementalPierce[DAMAGE_LIGHTNING] = pierce;
    NativeDefenseData d = new NativeDefenseData();
    d.isMonster = true;
    d.resistances[DAMAGE_LIGHTNING] = res;
    assertEquals(expected, resolve(a, d, new ArrayList<>()).elementalDamage[DAMAGE_LIGHTNING]);
  }

  @Test void coldLengthUsesResistanceButNeitherMdrNorAbsorption() {
    NativeAttackData a = attacker(DAMAGE_COLD, 2560, 2560);
    a.missileElemental = new NativeMissileElementalDamage(DAMAGE_COLD, 2560, 2560, 100);
    NativeDefenseData d = new NativeDefenseData();
    d.isPlayer = true;
    d.magicDamageReduced = 100;
    d.resistances[DAMAGE_COLD] = 50;
    d.absorbFlat[DAMAGE_COLD] = 100;
    assertEquals(50, resolve(a, d, new ArrayList<>()).coldDuration);
    d.halfFreezeDuration = true;
    assertEquals(25, resolve(a, d, new ArrayList<>()).coldDuration);
    d.cannotBeFrozen = true;
    assertEquals(0, resolve(a, d, new ArrayList<>()).coldDuration);
    d.cannotBeFrozen = d.halfFreezeDuration = false;
    d.isPlayer = false; d.isMonster = true;
    a.difficulty = 2;
    assertEquals(12, resolve(a, d, new ArrayList<>()).coldDuration);
  }

  @Test void mixedPhysicalElementalScalesRemainIndependent() {
    NativeAttackData a = attacker(DAMAGE_FIRE, 2815, 2815);
    a.minDamage = a.maxDamage = 2;
    NativeDamagePacket p = resolve(a, new NativeDefenseData(), new ArrayList<>());
    assertEquals(1, p.physicalScale);
    assertEquals(2 + 2815 / 256f, p.totalDamageHp());
    a.missilePhysical = new NativeMissilePhysicalDamage(128, 128, 0, 0, 0, false);
    p = resolve(a, new NativeDefenseData(), new ArrayList<>());
    assertEquals((128 + 2815) / 256f, p.totalDamageHp());
  }

  static NativeAttackData attacker(int type, int min, int max) {
    NativeAttackData a = new NativeAttackData();
    a.isMissile = a.alwaysHit = true;
    a.missileElemental = new NativeMissileElementalDamage(type, min, max, 0);
    return a;
  }

  static NativeDamagePacket resolve(NativeAttackData a, NativeDefenseData d, List<Integer> bounds) {
    return new NativeAttackResolver(new NativeCombatRolls(bound -> {
      bounds.add(bound);
      return bound == 100 ? 0 : bound - 1;
    })).resolve(a, d);
  }
}
